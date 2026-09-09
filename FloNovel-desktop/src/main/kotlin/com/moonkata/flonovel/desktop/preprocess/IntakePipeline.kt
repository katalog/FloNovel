package com.moonkata.flonovel.desktop.preprocess

import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import com.moonkata.flonovel.desktop.library.LibraryScanner
import com.moonkata.flonovel.desktop.library.RelativePath
import com.moonkata.flonovel.desktop.text.TextLoader
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class IntakeFailure(
    val path: Path,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDiskSpaceError: Boolean = false,
)

/**
 * File intake pipeline maintaining the strict contract sequence:
 * Detect -> Wait for write completion -> Preprocess -> Register in books.json
 *
 * Rules:
 * - Order is strictly: Preprocess -> Register. Preprocessing invalidates both relative_path key
 *   and char_offset reading anchor, so books must NEVER be registered before preprocessing.
 * - Books with an existing preprocessedAt timestamp are never preprocessed again.
 * - Single intake worker prevents race conditions on the same file.
 * - Runs completely decoupled from reader/UI threads.
 * - Full reconciliation on start catches changes that occurred while the app was closed.
 */
class IntakePipeline(
    val homeFolder: Path,
    val bookStore: BookStore,
    val backupOriginal: Boolean = true,
    private val checkIntervalMs: Long = 200L,
    private val stableChecksRequired: Int = 2,
) : AutoCloseable {

    private data class IntakeTask(val path: Path, val isModify: Boolean = false)

    private val running = AtomicBoolean(true)
    private val queue = LinkedBlockingQueue<IntakeTask>()
    private val enqueuedPaths = ConcurrentHashMap.newKeySet<Path>()

    private val workerExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Intake-Worker").apply { isDaemon = true }
    }

    private val watcherExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Intake-FolderWatcher").apply { isDaemon = true }
    }

    private var watchService: WatchService? = null
    private val watchKeys = ConcurrentHashMap<WatchKey, Path>()

    val failedFiles = CopyOnWriteArrayList<IntakeFailure>()

    // Optional listener for intake lifecycle events (used for UI notifications and tests)
    var onFileProcessed: ((Path, BookRecord) -> Unit)? = null
    var onFileFailed: ((IntakeFailure) -> Unit)? = null
    var onFileDeleted: ((Path) -> Unit)? = null
    var onDirectoryChanged: (() -> Unit)? = null

    init {
        workerExecutor.submit { runWorkerLoop() }
    }

    /**
     * Enqueues a file to the intake queue if it is a valid, non-hidden .txt file.
     */
    fun enqueue(filePath: Path, isModify: Boolean = false) {
        val abs = filePath.toAbsolutePath().normalize()
        if (!isValidTargetFile(abs)) return

        if (enqueuedPaths.add(abs)) {
            queue.offer(IntakeTask(abs, isModify))
        }
    }

    /**
     * Performs full startup reconciliation:
     * Scans home folder recursively and compares with bookStore:
     * - Found in folder but not in bookStore (or preprocessedAt is null) -> enqueued for intake.
     * - Content modified on disk while app was closed -> enqueued for update.
     * - Discovers files added or deleted while the app was closed.
     */
    fun reconcile(): Int {
        if (!Files.exists(homeFolder)) return 0

        var enqueuedCount = 0
        val scannedFiles = LibraryScanner.scan(homeFolder, bookStore.load())

        for (item in scannedFiles) {
            val record = item.bookRecord
            if (record == null || record.preprocessedAt == null) {
                enqueue(item.file.toPath())
                enqueuedCount++
            } else {
                val currentSize = item.sizeBytes
                val lastMod = item.lastModified
                if (currentSize != record.sizeBytes || lastMod > record.preprocessedAt) {
                    enqueue(item.file.toPath(), isModify = true)
                    enqueuedCount++
                }
            }
        }
        return enqueuedCount
    }

    @Volatile
    var isTreeWatchSupported: Boolean = false
        private set

    /**
     * Starts background directory monitoring on [homeFolder] and all subdirectories.
     * Uses ExtendedWatchEventModifier.FILE_TREE when supported (e.g. on Windows) so that
     * subdirectories are monitored recursively without holding open OS handles on them.
     */
    fun startWatcher() {
        try {
            val ws = FileSystems.getDefault().newWatchService()
            watchService = ws
            val treeRegistered = tryRegisterRecursive(homeFolder, ws)
            isTreeWatchSupported = treeRegistered
            if (!treeRegistered) {
                registerSubtree(homeFolder, ws)
            }

            watcherExecutor.submit {
                runWatcherLoop(ws)
            }
        } catch (_: Exception) {
            // Watch service initialization failure: reconciliation remains as robust fallback
        }
    }

    /**
     * Attempts to register [root] recursively using Windows/extended FILE_TREE modifier.
     * When supported, this watches the entire directory tree under [root] without acquiring
     * individual OS handles on subdirectories, allowing subdirectories to be renamed or deleted
     * freely by the user in Windows Explorer without file sharing violation locks.
     */
    private fun tryRegisterRecursive(root: Path, ws: WatchService): Boolean {
        if (!Files.exists(root)) return false
        return try {
            val clazz = Class.forName("com.sun.nio.file.ExtendedWatchEventModifier")
            val fileTreeModifier = clazz.enumConstants?.firstOrNull { (it as Enum<*>).name == "FILE_TREE" } as? WatchEvent.Modifier
                ?: return false

            val kinds: Array<WatchEvent.Kind<*>> = arrayOf(
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
            val key = root.register(ws, kinds, fileTreeModifier)
            watchKeys[key] = root
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun registerSubtree(start: Path, ws: WatchService) {
        if (!Files.exists(start)) return
        Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val dirName = dir.fileName?.toString() ?: ""
                if (dirName.startsWith(".") && dir != homeFolder) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                try {
                    val key = dir.register(
                        ws,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE,
                    )
                    watchKeys[key] = dir
                } catch (_: IOException) {}
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Enqueues all valid target files within [dir] recursively.
     * Used when a new directory is created or pasted while the app is running.
     */
    fun enqueueSubtree(dir: Path) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return
        try {
            Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(d: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val dName = d.fileName?.toString() ?: ""
                    if (dName.startsWith(".") && d != homeFolder) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (isValidTargetFile(file)) {
                        enqueue(file)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: Exception) {}
    }

    private fun runWatcherLoop(ws: WatchService) {
        while (running.get()) {
            val key: WatchKey = try {
                ws.poll(500, TimeUnit.MILLISECONDS) ?: continue
            } catch (_: InterruptedException) {
                break
            }

            val dir = watchKeys[key]
            if (dir != null) {
                var directoryChanged = false
                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        reconcile()
                        directoryChanged = true
                        continue
                    }

                    val context = event.context() as? Path ?: continue
                    // Skip hidden files or paths with hidden components (e.g. .git, .stfolder)
                    if (context.any { it.toString().startsWith(".") }) continue

                    val resolved = dir.resolve(context)

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(resolved)) {
                        // When recursive FILE_TREE is active, subdirectories are already monitored
                        // without needing individual WatchKeys (preventing Windows folder locks).
                        if (!isTreeWatchSupported) {
                            registerSubtree(resolved, ws)
                        }
                        enqueueSubtree(resolved)
                        directoryChanged = true
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        onFileDeleted?.invoke(resolved)
                        directoryChanged = true
                    } else if (isValidTargetFile(resolved)) {
                        val isModify = (kind == StandardWatchEventKinds.ENTRY_MODIFY)
                        enqueue(resolved, isModify = isModify)
                        directoryChanged = true
                    } else if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        directoryChanged = true
                    }
                }
                if (directoryChanged) {
                    onDirectoryChanged?.invoke()
                }
            }

            val valid = key.reset()
            if (!valid) {
                watchKeys.remove(key)
                if (watchKeys.isEmpty()) {
                    if (Files.exists(homeFolder)) {
                        val treeRegistered = tryRegisterRecursive(homeFolder, ws)
                        isTreeWatchSupported = treeRegistered
                        if (!treeRegistered) {
                            registerSubtree(homeFolder, ws)
                        }
                    } else {
                        break
                    }
                }
            }
        }
    }

    private fun runWorkerLoop() {
        while (running.get()) {
            val task = try {
                queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
            } catch (_: InterruptedException) {
                break
            }

            try {
                processSingleFile(task.path, isModifyEvent = task.isModify)
            } finally {
                enqueuedPaths.remove(task.path)
            }
        }
    }

    /**
     * Executes the intake pipeline sequence for a single file:
     * 1. Check if already preprocessed in bookStore.
     *    - If modified on disk (or isModifyEvent): update size, charCount, preprocessedAt and notify.
     *    - If unchanged: return existing immediately (Contract: protect reading anchors from re-preprocess).
     * 2. Wait for write completion -> ensure file is stable.
     * 3. Preprocess -> clean name, backup original, normalize content, atomic write.
     * 4. Register -> save to bookStore with preprocessedAt timestamp.
     */
    fun processSingleFile(path: Path, isModifyEvent: Boolean = false): BookRecord? {
        val abs = path.toAbsolutePath().normalize()

        // Verify it is a valid target file
        if (!isValidTargetFile(abs)) return null

        // 1. Check if already preprocessed in bookStore
        val initialRel = try {
            homeFolder.relativize(abs).toString().replace('\\', '/')
        } catch (_: Exception) {
            abs.fileName.toString()
        }
        val initialKey = RelativePath.normalize(initialRel)
        val existing = bookStore.findByKey(initialKey)
        if (existing != null && existing.preprocessedAt != null) {
            val currentSize = try { Files.size(abs) } catch (_: Exception) { -1L }
            val isModified = isModifyEvent || (currentSize >= 0 && currentSize != existing.sizeBytes)

            if (isModified) {
                // Wait for write completion (handling file editors saving)
                val ready = waitForWriteComplete(abs)
                if (!ready) return existing

                val loaded = try {
                    TextLoader.load(abs)
                } catch (_: Exception) {
                    null
                }

                if (loaded != null) {
                    val actualSize = try { Files.size(abs) } catch (_: Exception) { existing.sizeBytes }
                    val newCharCount = loaded.text.length
                    val updatedAnchor = existing.anchor.coerceIn(0, newCharCount)
                    val updatedProgress = if (newCharCount > 0) {
                        (updatedAnchor.toDouble() / newCharCount).coerceIn(0.0, 1.0)
                    } else 0.0

                    val now = System.currentTimeMillis()
                    val lastMod = try { Files.getLastModifiedTime(abs).toMillis() } catch (_: Exception) { 0L }
                    val newPreprocessedAt = maxOf(now, lastMod)

                    val updatedRecord = existing.copy(
                        sizeBytes = actualSize,
                        totalCharCount = newCharCount,
                        detectedEncoding = loaded.charset.name(),
                        anchor = updatedAnchor,
                        progress = updatedProgress,
                        preprocessedAt = newPreprocessedAt,
                    )
                    bookStore.addOrUpdate(updatedRecord)
                    bookStore.flush()

                    onFileProcessed?.invoke(abs, updatedRecord)
                    return updatedRecord
                }
            }

            // Already preprocessed book and unchanged: do NOT preprocess again (Contract: prevent corrupting reading anchors)
            return existing
        }

        // 2. Wait for write completion (handling copied / downloading files)
        val ready = waitForWriteComplete(abs)
        if (!ready) {
            // File was deleted or inaccessible during wait
            return null
        }

        // 3. Preprocess file
        val preprocessResult = try {
            TextPreprocessor.preprocessFile(
                filePath = abs,
                homeFolder = homeFolder,
                backupOriginal = backupOriginal,
            )
        } catch (e: Exception) {
            val failure = IntakeFailure(
                path = abs,
                reason = e.message ?: e.toString(),
                isDiskSpaceError = (e is InsufficientDiskSpaceException),
            )
            failedFiles.removeIf { it.path == abs }
            failedFiles.add(failure)
            onFileFailed?.invoke(failure)
            return null
        }

        // Success: remove from failed files if previously failed
        failedFiles.removeIf { it.path == abs || it.path == preprocessResult.finalPath }

        // 4. Register in bookStore
        val finalRel = try {
            homeFolder.relativize(preprocessResult.finalPath).toString().replace('\\', '/')
        } catch (_: Exception) {
            preprocessResult.finalPath.fileName.toString()
        }
        val finalKey = RelativePath.normalize(finalRel)

        val record = BookRecord(
            path = finalRel,
            key = finalKey,
            displayName = preprocessResult.finalPath.fileName.toString().removeSuffix(".txt"),
            sizeBytes = Files.size(preprocessResult.finalPath),
            totalCharCount = preprocessResult.charCountAfter,
            detectedEncoding = "UTF-8",
            anchor = existing?.anchor ?: 0,
            progress = existing?.progress ?: 0.0,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            lastOpenedAt = existing?.lastOpenedAt,
            preprocessedAt = System.currentTimeMillis(),
        )

        bookStore.addOrUpdate(record)
        bookStore.flush()

        onFileProcessed?.invoke(preprocessResult.finalPath, record)
        return record
    }

    /**
     * Waits until the file size remains identical across [stableChecksRequired] consecutive checks
     * and the file is readable without exclusive write locks.
     */
    fun waitForWriteComplete(path: Path): Boolean {
        if (!Files.exists(path)) return false

        val maxWaitMs = 3000L
        val startTime = System.currentTimeMillis()
        var lastSize = -1L
        var stableCount = 0

        while (running.get() && (System.currentTimeMillis() - startTime) < maxWaitMs) {
            if (!Files.exists(path)) return false

            val currentSize = try {
                Files.size(path)
            } catch (_: IOException) {
                -1L
            }

            if (currentSize >= 0 && currentSize == lastSize) {
                stableCount++
            } else {
                stableCount = 0
                lastSize = currentSize
            }

            if (stableCount >= stableChecksRequired) {
                val canRead = try {
                    java.io.FileInputStream(path.toFile()).use { it.read(); true }
                } catch (_: Exception) {
                    false
                }
                if (canRead) {
                    return Files.isRegularFile(path) && Files.isReadable(path)
                }
            }

            try {
                Thread.sleep(checkIntervalMs)
            } catch (_: InterruptedException) {
                return false
            }
        }

        return Files.isRegularFile(path) && Files.isReadable(path)
    }

    /**
     * Retries preprocessing all failed files.
     */
    fun retryAllFailed() {
        val targets = failedFiles.map { it.path }
        failedFiles.clear()
        for (target in targets) {
            enqueue(target)
        }
    }

    /**
     * Retries preprocessing a specific failed file.
     */
    fun retryFailed(path: Path) {
        val abs = path.toAbsolutePath().normalize()
        failedFiles.removeIf { it.path == abs }
        enqueue(abs)
    }

    private fun isValidTargetFile(path: Path): Boolean {
        val fileName = path.fileName?.toString() ?: return false
        if (fileName.startsWith(".")) return false
        if (!fileName.endsWith(".txt", ignoreCase = true)) return false

        // Exclude paths containing hidden folder segments (e.g. .stfolder, .git)
        var current: Path? = path.parent
        while (current != null && current != homeFolder) {
            val segmentName = current.fileName?.toString() ?: ""
            if (segmentName.startsWith(".")) return false
            current = current.parent
        }
        return true
    }

    fun isIdle(): Boolean {
        return queue.isEmpty() && enqueuedPaths.isEmpty()
    }

    override fun close() {
        running.set(false)
        try {
            watchService?.close()
        } catch (_: IOException) {}

        watcherExecutor.shutdownNow()
        workerExecutor.shutdownNow()
    }
}

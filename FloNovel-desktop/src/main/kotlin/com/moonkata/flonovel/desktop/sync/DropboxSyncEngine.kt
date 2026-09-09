package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import com.moonkata.flonovel.desktop.library.CredentialsStore
import com.moonkata.flonovel.desktop.library.RelativePath
import com.moonkata.flonovel.desktop.library.SettingsStore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

data class SyncFileFailure(
    val relativePath: String,
    val error: String,
    val isInsufficientSpace: Boolean = false,
)

data class SyncSummary(
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val failures: List<SyncFileFailure> = emptyList(),
    val deletedCount: Int = 0,
)

data class InitialUploadProgress(
    val totalFiles: Int,
    val processedFiles: Int,
    val totalBytes: Long,
    val processedBytes: Long,
    val currentFileName: String = "",
)

enum class SyncStatus {
    IDLE,
    SYNCING,
    PAUSED,
    INSUFFICIENT_SPACE,
    ERROR,
}

data class RemoteFileInfo(
    val pathDisplay: String,
    val pathLower: String,
    val size: Long,
)

/**
 * Handles file synchronization between local homeFolder and Dropbox App Folder (/books).
 *
 * Rules:
 * - Direction: Desktop uploads to Dropbox (one-way).
 * - Delta sync: list_folder / list_folder/continue with local cursor persistence.
 * - Reset cursor handling: on cursor expiry/reset, restarts from beginning.
 * - Upload decision:
 *     Remote missing -> upload
 *     Size differs -> re-upload
 *     uploadedAt < preprocessedAt -> re-upload
 *   ★ NEVER compare lastModified timestamp (prevents infinite re-download bug).
 * - Exclude "." prefixed files and folders (.stfolder, .git, etc.).
 * - Exclude files not preprocessed (preprocessedAt == null) or failed preprocessing.
 * - First-time full upload (1.3GB) is initiated by explicit user action, with progress & pause/resume.
 * - Individual file failure does not kill batch. Summary aggregates (success, failed, skipped).
 * - Insufficient space is marked as a distinct state and failed files retained.
 * - Never block reader.
 */
class DropboxSyncEngine(
    val homeFolder: Path,
    val bookStore: BookStore,
    val credentialsStore: CredentialsStore,
    val settingsStore: SettingsStore,
    val dropboxClient: DropboxClient,
) {
    @Volatile
    var status: SyncStatus = SyncStatus.IDLE
        private set

    @Volatile
    var lastSummary: SyncSummary? = null
        private set

    @Volatile
    var initialProgress: InitialUploadProgress? = null
        private set

    @Volatile
    var failedFiles: List<SyncFileFailure> = emptyList()
        private set

    private val isPaused = AtomicBoolean(false)
    private val isSyncing = AtomicBoolean(false)

    val isInitialUploadRequired: Boolean
        get() {
            if (!dropboxClient.isLinked) return false
            return settingsStore.load().sync.lastSyncAt == 0L
        }

    val isInsufficientSpace: Boolean
        get() = status == SyncStatus.INSUFFICIENT_SPACE || failedFiles.any { it.isInsufficientSpace }

    fun toRelPath(file: File): String {
        return homeFolder.relativize(file.toPath()).toString().replace('\\', '/')
    }

    /**
     * Pauses ongoing initial upload.
     */
    fun pause() {
        if (isSyncing.get()) {
            isPaused.set(true)
            status = SyncStatus.PAUSED
        }
    }

    /**
     * Resumes paused upload or triggers incremental sync.
     */
    fun resume(): SyncSummary? {
        isPaused.set(false)
        return syncIncremental()
    }

    /**
     * Performs incremental delta synchronization with optional progress reporting.
     */
    fun syncIncremental(
        onProgress: ((InitialUploadProgress) -> Unit)? = null,
    ): SyncSummary? {
        if (!dropboxClient.isLinked || !Files.exists(homeFolder)) return null
        if (!isSyncing.compareAndSet(false, true)) return null

        status = SyncStatus.SYNCING
        isPaused.set(false)

        try {
            val remoteMap = fetchRemoteFiles()
            val candidateFiles = collectLocalEligibleFiles()

            // 1. Reconcile remote deletions: any remote file or directory no longer present locally
            val deletedCount = reconcileDeletions(candidateFiles, remoteMap)

            // 2. Identify files that need upload
            val filesToUpload = candidateFiles.filter { localFile ->
                val relPath = toRelPath(localFile)
                val normKey = RelativePath.normalize(relPath)
                val remoteKey = "/books/${normKey.lowercase()}"
                val remoteFile = remoteMap[remoteKey]

                val record = bookStore.findByPath(relPath) ?: bookStore.findByKey(normKey)
                checkIfUploadNeeded(localFile, remoteFile, record)
            }

            val totalFiles = filesToUpload.size
            val totalBytes = filesToUpload.sumOf { it.length() }
            var processedFiles = 0
            var processedBytes = 0L

            var successCount = 0
            var failedCount = 0
            val skippedCount = candidateFiles.size - totalFiles
            val failures = mutableListOf<SyncFileFailure>()

            for (localFile in filesToUpload) {
                if (isPaused.get()) {
                    status = SyncStatus.PAUSED
                    break
                }

                val relPath = toRelPath(localFile)
                val normKey = RelativePath.normalize(relPath)
                val record = bookStore.findByPath(relPath) ?: bookStore.findByKey(normKey)
                if (record == null || record.preprocessedAt == null) continue

                val progress = InitialUploadProgress(
                    totalFiles = totalFiles,
                    processedFiles = processedFiles,
                    totalBytes = totalBytes,
                    processedBytes = processedBytes,
                    currentFileName = localFile.name,
                )
                initialProgress = progress
                onProgress?.invoke(progress)

                val uploadResult = uploadSingleFile(localFile, relPath)
                when (uploadResult) {
                    is DropboxUploadResult.Success -> {
                        successCount++
                        processedFiles++
                        processedBytes += uploadResult.size
                        bookStore.addOrUpdate(
                            record.copy(
                                uploadedAt = System.currentTimeMillis(),
                                uploadedSize = uploadResult.size,
                            )
                        )
                    }
                    is DropboxUploadResult.InsufficientSpace -> {
                        failedCount++
                        val failure = SyncFileFailure(
                            relativePath = relPath,
                            error = "Dropbox storage full (insufficient space)",
                            isInsufficientSpace = true,
                        )
                        failures.add(failure)
                        status = SyncStatus.INSUFFICIENT_SPACE
                        break
                    }
                    is DropboxUploadResult.Failure -> {
                        failedCount++
                        processedFiles++
                        processedBytes += localFile.length()
                        val failure = SyncFileFailure(
                            relativePath = relPath,
                            error = uploadResult.message,
                            isInsufficientSpace = false,
                        )
                        failures.add(failure)
                    }
                }

                val updatedProgress = InitialUploadProgress(
                    totalFiles = totalFiles,
                    processedFiles = processedFiles,
                    totalBytes = totalBytes,
                    processedBytes = processedBytes,
                    currentFileName = localFile.name,
                )
                initialProgress = updatedProgress
                onProgress?.invoke(updatedProgress)
            }

            if (status != SyncStatus.INSUFFICIENT_SPACE && status != SyncStatus.PAUSED) {
                status = SyncStatus.IDLE
            }

            failedFiles = failures
            val summary = SyncSummary(
                successCount = successCount,
                failedCount = failedCount,
                skippedCount = skippedCount,
                failures = failures,
                deletedCount = deletedCount,
            )
            lastSummary = summary

            if (!isPaused.get() && status != SyncStatus.INSUFFICIENT_SPACE) {
                settingsStore.update {
                    it.copy(sync = it.sync.copy(lastSyncAt = System.currentTimeMillis()))
                }
            }

            return summary
        } catch (_: Exception) {
            status = SyncStatus.ERROR
            return null
        } finally {
            isSyncing.set(false)
        }
    }

    /**
     * Executes initial full upload with progress reporting and pause capability.
     */
    fun startInitialUpload(
        onProgress: ((InitialUploadProgress) -> Unit)? = null,
    ): SyncSummary? {
        if (!dropboxClient.isLinked || !Files.exists(homeFolder)) return null
        if (!isSyncing.compareAndSet(false, true)) return null

        status = SyncStatus.SYNCING
        isPaused.set(false)

        try {
            val remoteMap = fetchRemoteFiles()
            val candidateFiles = collectLocalEligibleFiles()
            val deletedCount = reconcileDeletions(candidateFiles, remoteMap)

            val filesToUpload = candidateFiles.filter { file ->
                val relPath = toRelPath(file)
                val normKey = RelativePath.normalize(relPath)
                val remoteKey = "/books/${normKey.lowercase()}"
                val remoteFile = remoteMap[remoteKey]

                val record = bookStore.findByPath(relPath) ?: bookStore.findByKey(normKey)
                checkIfUploadNeeded(file, remoteFile, record)
            }

            val totalBytes = filesToUpload.sumOf { it.length() }
            val totalFiles = filesToUpload.size
            var processedBytes = 0L
            var processedFiles = 0

            var successCount = 0
            var failedCount = 0
            var skippedCount = 0
            val failures = mutableListOf<SyncFileFailure>()

            for (localFile in filesToUpload) {
                if (isPaused.get()) {
                    status = SyncStatus.PAUSED
                    break
                }

                val relPath = toRelPath(localFile)
                val normKey = RelativePath.normalize(relPath)
                val record = bookStore.findByPath(relPath) ?: bookStore.findByKey(normKey)
                if (record == null || record.preprocessedAt == null) {
                    skippedCount++
                    continue
                }

                val progress = InitialUploadProgress(
                    totalFiles = totalFiles,
                    processedFiles = processedFiles,
                    totalBytes = totalBytes,
                    processedBytes = processedBytes,
                    currentFileName = localFile.name,
                )
                initialProgress = progress
                onProgress?.invoke(progress)

                val uploadResult = uploadSingleFile(localFile, relPath)
                when (uploadResult) {
                    is DropboxUploadResult.Success -> {
                        successCount++
                        processedFiles++
                        processedBytes += uploadResult.size
                        bookStore.addOrUpdate(
                            record.copy(
                                uploadedAt = System.currentTimeMillis(),
                                uploadedSize = uploadResult.size,
                            )
                        )
                    }
                    is DropboxUploadResult.InsufficientSpace -> {
                        failedCount++
                        val failure = SyncFileFailure(
                            relativePath = relPath,
                            error = "Dropbox storage full (insufficient space)",
                            isInsufficientSpace = true,
                        )
                        failures.add(failure)
                        status = SyncStatus.INSUFFICIENT_SPACE
                        break
                    }
                    is DropboxUploadResult.Failure -> {
                        failedCount++
                        processedFiles++
                        processedBytes += localFile.length()
                        val failure = SyncFileFailure(
                            relativePath = relPath,
                            error = uploadResult.message,
                            isInsufficientSpace = false,
                        )
                        failures.add(failure)
                    }
                }

                val updatedProgress = InitialUploadProgress(
                    totalFiles = totalFiles,
                    processedFiles = processedFiles,
                    totalBytes = totalBytes,
                    processedBytes = processedBytes,
                    currentFileName = localFile.name,
                )
                initialProgress = updatedProgress
                onProgress?.invoke(updatedProgress)
            }

            if (status != SyncStatus.INSUFFICIENT_SPACE && status != SyncStatus.PAUSED) {
                status = SyncStatus.IDLE
            }

            failedFiles = failures
            val summary = SyncSummary(
                successCount = successCount,
                failedCount = failedCount,
                skippedCount = skippedCount,
                failures = failures,
                deletedCount = deletedCount,
            )
            lastSummary = summary

            if (!isPaused.get() && status != SyncStatus.INSUFFICIENT_SPACE) {
                settingsStore.update {
                    it.copy(sync = it.sync.copy(lastSyncAt = System.currentTimeMillis()))
                }
            }

            return summary
        } catch (_: Exception) {
            status = SyncStatus.ERROR
            return null
        } finally {
            isSyncing.set(false)
        }
    }

    /**
     * Reconciles local deletions against Dropbox and BookStore:
     * 1. Checks all books in BookStore with `uploadedAt != null` that are missing from [candidateFiles].
     * 2. If an entire directory was removed locally, deletes that directory on Dropbox in one call.
     * 3. For any remaining missing files, deletes each individual file on Dropbox.
     * 4. Updates BookStore records: clears `uploadedAt` and `uploadedSize` while preserving `anchor` and `progress` (05-STATE-FLOW.md §4).
     * 5. Cleans up any files in [remoteMap] that are missing locally.
     */
    internal fun reconcileDeletions(
        candidateFiles: List<File>,
        remoteMap: Map<String, RemoteFileInfo>,
    ): Int {
        val localKeys = candidateFiles.map { RelativePath.normalize(toRelPath(it)) }.toSet()
        val uploadedRecords = bookStore.load().books.filter { it.uploadedAt != null }
        val missingUploadedRecords = uploadedRecords.filter { it.key !in localKeys }

        val deletedDirs = mutableSetOf<String>()
        for (record in missingUploadedRecords) {
            val relKey = record.key
            if (relKey.contains('/')) {
                val segments = relKey.split('/')
                var currentAncestor = ""
                for (i in 0 until segments.size - 1) {
                    currentAncestor = if (currentAncestor.isEmpty()) segments[i] else "$currentAncestor/${segments[i]}"
                    val ancestorPath = homeFolder.resolve(currentAncestor)
                    if (!Files.exists(ancestorPath)) {
                        val topMissing = currentAncestor.substringBefore('/')
                        if (!Files.exists(homeFolder.resolve(topMissing))) {
                            deletedDirs.add(topMissing)
                        } else {
                            deletedDirs.add(currentAncestor)
                        }
                        break
                    }
                }
            }
        }

        var deletedCount = 0

        // 1. Delete missing directories on Dropbox (recursively deletes all remote files in that folder)
        for (deletedDir in deletedDirs) {
            val deleted = dropboxClient.deleteFile("/books/$deletedDir")
            if (deleted) deletedCount++
        }

        // 2. Delete missing individual files not covered by a deleted directory
        for (record in missingUploadedRecords) {
            val isCovered = deletedDirs.any { dir ->
                record.key == dir || record.key.startsWith("$dir/")
            }
            if (!isCovered) {
                val deleted = dropboxClient.deleteFile("/books/${record.key}")
                if (deleted) deletedCount++
            }
            // Update BookStore: clear uploadedAt/uploadedSize, keep anchor intact (05-STATE-FLOW.md §4)
            bookStore.addOrUpdate(
                record.copy(
                    uploadedAt = null,
                    uploadedSize = null,
                )
            )
        }

        // 3. Clean up any remote files in remoteMap that don't exist locally
        for ((remoteKey, remoteInfo) in remoteMap) {
            val rel = remoteKey.removePrefix("/books/").removePrefix("/")
            val normRel = RelativePath.normalize(rel)
            val isCovered = deletedDirs.any { dir -> normRel == dir || normRel.startsWith("$dir/") }
            if (!isCovered && normRel.isNotBlank() && normRel !in localKeys) {
                val deleted = dropboxClient.deleteFile(remoteInfo.pathDisplay)
                if (deleted) deletedCount++
            }
        }

        return deletedCount
    }

    /**
     * Determines whether a local file needs to be uploaded to Dropbox.
     *
     * Rules per 06-SYNC-STRATEGY.md §B4:
     * - uploadedAt == null -> upload (or adopt if remote already has identical size)
     * - uploadedAt < preprocessedAt -> re-upload (content modified locally)
     * - size differs from recorded uploadedSize -> re-upload
     * - remoteFile != null && remoteFile.size != localSize -> re-upload
     * - ★ NEVER compare modification timestamp
     */
    internal fun checkIfUploadNeeded(
        localFile: File,
        remoteFile: RemoteFileInfo?,
        record: BookRecord?,
    ): Boolean {
        if (record == null || record.preprocessedAt == null) return false

        val localSize = try {
            if (Files.exists(localFile.toPath())) Files.size(localFile.toPath()) else -1L
        } catch (_: Exception) {
            -1L
        }
        if (localSize < 0) return false

        // 1. Not uploaded yet
        if (record.uploadedAt == null) {
            if (remoteFile != null && remoteFile.size == localSize) {
                // Already present on remote with matching size -> adopt without re-uploading
                bookStore.addOrUpdate(
                    record.copy(
                        uploadedAt = System.currentTimeMillis(),
                        uploadedSize = localSize,
                    )
                )
                return false
            }
            return true
        }

        // 2. Preprocessed again after upload (content changed locally)
        if (record.uploadedAt < (record.preprocessedAt ?: 0L)) {
            return true
        }

        // 3. File size differs from when it was uploaded
        val recordedSize = record.uploadedSize ?: record.sizeBytes
        if (recordedSize != localSize) {
            return true
        }

        // 4. Remote file exists and its size differs from local size
        if (remoteFile != null && remoteFile.size != localSize) {
            return true
        }

        return false
    }

    private fun uploadSingleFile(file: File, relPath: String): DropboxUploadResult {
        return try {
            if (!file.exists() || !file.canRead()) {
                return DropboxUploadResult.Failure("File inaccessible")
            }
            val bytes = Files.readAllBytes(file.toPath())
            val remotePath = "/books/$relPath"
            dropboxClient.uploadFile(remotePath, bytes, overwrite = true)
        } catch (e: Exception) {
            DropboxUploadResult.Failure(e.message ?: "Read error")
        }
    }

    /**
     * Recursively collects all eligible local .txt files under homeFolder.
     * Excludes any file or directory starting with "." (D5).
     */
    internal fun collectLocalEligibleFiles(): List<File> {
        if (!Files.exists(homeFolder)) return emptyList()
        val results = mutableListOf<File>()

        fun scanDir(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.name.startsWith(".")) {
                    // Exclude .stfolder, .git, etc.
                    continue
                }
                if (child.isDirectory) {
                    scanDir(child)
                } else if (child.isFile && child.name.endsWith(".txt", ignoreCase = true)) {
                    results.add(child)
                }
            }
        }

        scanDir(homeFolder.toFile())
        return results
    }

    /**
     * Fetches remote /books file list using cursor and handles reset.
     */
    internal fun fetchRemoteFiles(): Map<String, RemoteFileInfo> {
        val cursor = credentialsStore.load().dropboxCursor
        val remoteMap = mutableMapOf<String, RemoteFileInfo>()

        var listResult: DropboxListFolderResult = if (!cursor.isNullOrBlank()) {
            val res = dropboxClient.listFolderContinue(cursor)
            if (res is DropboxListFolderResult.Reset) {
                // Cursor expired or reset, restart from full list_folder (D2)
                credentialsStore.update { it.copy(dropboxCursor = null) }
                dropboxClient.listFolder("/books", recursive = true)
            } else res
        } else {
            dropboxClient.listFolder("/books", recursive = true)
        }

        while (true) {
            val current = listResult
            when (current) {
                is DropboxListFolderResult.Success -> {
                    for (entry in current.entries) {
                        when (entry) {
                            is DropboxEntry.FileEntry -> {
                                remoteMap[entry.pathLower] = RemoteFileInfo(
                                    pathDisplay = entry.pathDisplay,
                                    pathLower = entry.pathLower,
                                    size = entry.size,
                                )
                            }
                            is DropboxEntry.DeletedEntry -> {
                                remoteMap.remove(entry.pathLower)
                            }
                            is DropboxEntry.FolderEntry -> {
                                // Subfolders not directly stored in file map
                            }
                        }
                    }

                    credentialsStore.update { it.copy(dropboxCursor = current.cursor) }

                    if (current.hasMore) {
                        listResult = dropboxClient.listFolderContinue(current.cursor)
                    } else {
                        break
                    }
                }
                is DropboxListFolderResult.Reset -> {
                    // Cursor reset during pagination
                    credentialsStore.update { it.copy(dropboxCursor = null) }
                    remoteMap.clear()
                    listResult = dropboxClient.listFolder("/books", recursive = true)
                }
                is DropboxListFolderResult.Failure -> {
                    break
                }
            }
        }

        return remoteMap
    }
}

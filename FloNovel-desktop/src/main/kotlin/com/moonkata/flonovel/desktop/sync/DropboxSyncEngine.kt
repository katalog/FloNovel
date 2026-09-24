package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import com.moonkata.flonovel.desktop.library.RelativePath
import com.moonkata.flonovel.desktop.library.SettingsStore
import com.moonkata.flonovel.desktop.platform.SystemTrash
import com.moonkata.flonovel.desktop.text.ChapterDetector
import com.moonkata.flonovel.desktop.text.TextLoader
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

data class SyncFileFailure(
    val relativePath: String,
    val error: String,
    val isInsufficientSpace: Boolean = false,
)

data class SyncSummary(
    /** Files uploaded or downloaded. */
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val failures: List<SyncFileFailure> = emptyList(),
    /** Local files sent to the recycle bin plus remote files deleted. */
    val deletedCount: Int = 0,
    val conflictCount: Int = 0,
    /** Paths of deletions held back by the mass-deletion guard; non-empty means ask the user. */
    val withheldDeletions: List<String> = emptyList(),
    /** Changes left for a later pass, e.g. because the book is open in the reader. */
    val deferredCount: Int = 0,
    /** Books renamed or moved as a whole, on either side, instead of deleted and transferred. */
    val movedCount: Int = 0,
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

/**
 * Two-way sync between the home folder and Dropbox `/books` (AGENTS.md §1, "동기화 — 파일").
 *
 * Each pass: build the remote view (base overlaid with the listing delta), scan local `.txt`
 * files, run [SyncDecision.decide] per key, then apply the actions one file at a time. A failure
 * affects only its own file. The cursor is saved only when every action landed, so anything left
 * undone is seen again next pass; re-applying is harmless because each decision compares against
 * the base that was updated as actions succeeded.
 *
 * - Only `.txt` files outside dot-folders are synced, on both sides.
 * - Files still waiting for preprocessing are left out of the pass entirely, not treated as
 *   deleted.
 * - Downloads land in `.flonovel/tmp`, are checked against `content_hash`, registered in the book
 *   store, and only then moved into place. Registering first makes the intake watcher see an
 *   already-preprocessed book and leave it alone (no second preprocessing, no backup copy).
 * - Remote deletions go to the recycle bin, never a permanent delete.
 * - Never blocks the reader: the book open in the reader is left alone until it is closed.
 */
class DropboxSyncEngine(
    val homeFolder: Path,
    val bookStore: BookStore,
    val settingsStore: SettingsStore,
    val dropboxClient: DropboxClient,
    val syncStateStore: SyncStateStore,
    private val trash: ((Path) -> Boolean)? = if (SystemTrash.isSupported) SystemTrash::moveToTrash else null,
    private val deviceName: String = "PC",
    private val today: () -> LocalDate = LocalDate::now,
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

    /** The UI-language word for "conflicted copy", used in conflict-copy file names. */
    @Volatile
    var conflictLabel: String = "conflicted copy"

    /**
     * The file open in the reader is not replaced, deleted or renamed until it is closed. Replacing
     * a file by rename can surface as a delete event, and the app closes a book whose file was
     * deleted, so even a plain download would throw the reader out of the book.
     */
    @Volatile
    var isOpenInReader: (Path) -> Boolean = { false }

    /**
     * Called after a book was moved or renamed (either side), with the old and new relative paths,
     * so the app can carry its reading position over to the new path key.
     */
    @Volatile
    var onBookMoved: ((fromRel: String, toRel: String) -> Unit)? = null

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

    fun pause() {
        if (isSyncing.get()) {
            isPaused.set(true)
            status = SyncStatus.PAUSED
        }
    }

    fun resume(): SyncSummary? {
        isPaused.set(false)
        return syncIncremental()
    }

    /** The first, possibly large, sync is started by the user; it is the same pass. */
    fun startInitialUpload(onProgress: ((InitialUploadProgress) -> Unit)? = null): SyncSummary? =
        syncIncremental(onProgress = onProgress)

    /**
     * @param allowMassDeletion true once the user confirmed deletions the guard held back.
     */
    fun syncIncremental(
        allowMassDeletion: Boolean = false,
        onProgress: ((InitialUploadProgress) -> Unit)? = null,
    ): SyncSummary? {
        if (!dropboxClient.isLinked || !Files.exists(homeFolder)) return null
        if (!isSyncing.compareAndSet(false, true)) return null

        status = SyncStatus.SYNCING
        isPaused.set(false)
        return try {
            runPass(onProgress, allowMassDeletion)
        } catch (_: Exception) {
            status = SyncStatus.ERROR
            null
        } finally {
            isSyncing.set(false)
        }
    }

    // ── One pass ──────────────────────────────────────────────────────────

    private data class LocalEntry(val path: Path, val rel: String, val state: LocalFileState)

    private data class LocalScan(
        val files: Map<String, LocalEntry>,
        /** Keys that must sit this pass out: still being preprocessed, or ambiguous. */
        val excludedKeys: Set<String>,
        val failures: List<SyncFileFailure>,
    )

    private data class RemoteSnapshot(
        val files: Map<String, RemoteFileState>,
        val cursor: String,
        val isFullListing: Boolean,
    )

    /**
     * One unit of work. A [move] replaces the two plans it was paired from; if the move cannot be
     * carried out, those two ([fallback]) run instead.
     */
    private data class Plan(
        val key: String,
        val action: SyncAction,
        val move: Move? = null,
        val fallback: List<Plan> = emptyList(),
    )

    private sealed class Outcome {
        data class Done(
            val transferred: Boolean = false,
            val deleted: Boolean = false,
            val conflict: Boolean = false,
            val moved: Boolean = false,
        ) : Outcome()
        object Deferred : Outcome()
        data class Failed(val error: String, val insufficientSpace: Boolean = false) : Outcome()
    }

    private fun runPass(
        onProgress: ((InitialUploadProgress) -> Unit)?,
        allowMassDeletion: Boolean,
    ): SyncSummary? {
        // Before the first two-way pass has saved anything, and only then, the library is coming
        // from one-way sync where the PC was the only writer; see [upgradeOverride].
        val upgradingFromOneWay = !syncStateStore.fileExists && bookStore.load().books.any { it.uploadedAt != null }

        val startState = syncStateStore.load()
        val remote = fetchRemote(startState) ?: run {
            status = SyncStatus.ERROR
            return null
        }
        val local = scanLocal(startState.bases)
        val bases = startState.bases.toMutableMap()

        val keys = (bases.keys + local.files.keys + remote.files.keys) - local.excludedKeys
        val decided = keys.sorted().mapNotNull { key ->
            var action = SyncDecision.decide(bases[key], local.files[key]?.state, remote.files[key])
            if (upgradingFromOneWay) action = upgradeOverride(key, bases[key], action)
            if (action == SyncAction.None) null else Plan(key, action)
        }
        // A rename or move shows up as a deletion plus an addition of the same content; carried
        // out as one it needs no transfer, keeps the reading position and deletes nothing, so it
        // also stays out of the mass-deletion count (a renamed folder is not a mass deletion).
        val byKey = decided.associateBy { it.key }
        val moves = findMoves(byKey.mapValues { it.value.action }, bases, local.files.mapValues { it.value.state.contentHash })
        val movedKeys = moves.flatMap { listOf(it.from, it.to) }.toSet()
        val plans = moves.map { Plan(it.to, SyncAction.None, it, listOf(byKey.getValue(it.from), byKey.getValue(it.to))) } +
            decided.filter { it.key !in movedKeys }

        val deletions = plans.filter { it.action is SyncAction.DeleteLocal || it.action is SyncAction.DeleteRemote }
        val remoteIsEmpty = remote.isFullListing && remote.files.isEmpty()
        val withhold = !allowMassDeletion &&
            SyncDecision.isMassDeletion(deletions.size, bases.size, remoteIsEmpty) &&
            deletions.isNotEmpty()
        val toApply = if (withhold) plans - deletions.toSet() else plans

        val totalBytes = toApply.sumOf { plan ->
            when (val a = plan.action) {
                is SyncAction.Download -> a.remote.size
                is SyncAction.Upload -> local.files[plan.key]?.state?.size ?: 0L
                else -> 0L
            }
        }

        val failures = local.failures.toMutableList()
        var transferred = 0
        var deleted = 0
        var conflicts = 0
        var moved = 0
        var deferred = 0
        var processed = 0
        var processedBytes = 0L
        var stoppedEarly = false

        for (plan in toApply) {
            if (isPaused.get()) {
                status = SyncStatus.PAUSED
                stoppedEarly = true
                break
            }
            val progress = InitialUploadProgress(toApply.size, processed, totalBytes, processedBytes, plan.key.substringAfterLast('/'))
            initialProgress = progress
            onProgress?.invoke(progress)

            val outcome = try {
                apply(plan, local, remote, bases)
            } catch (e: Exception) {
                Outcome.Failed(e.message ?: e.javaClass.simpleName)
            }
            when (outcome) {
                is Outcome.Done -> {
                    if (outcome.transferred) transferred++
                    if (outcome.deleted) deleted++
                    if (outcome.conflict) conflicts++
                    if (outcome.moved) moved++
                }
                Outcome.Deferred -> deferred++
                is Outcome.Failed -> {
                    failures += SyncFileFailure(plan.key, outcome.error, outcome.insufficientSpace)
                    if (outcome.insufficientSpace) {
                        status = SyncStatus.INSUFFICIENT_SPACE
                        stoppedEarly = true
                    }
                }
            }
            processed++
            processedBytes += when (val a = plan.action) {
                is SyncAction.Download -> a.remote.size
                is SyncAction.Upload -> local.files[plan.key]?.state?.size ?: 0L
                else -> 0L
            }
            if (stoppedEarly) break
            // Bases are saved as work lands, with the old cursor, so a crash mid-pass loses at
            // most this batch of bookkeeping and the next pass redoes it harmlessly.
            if (processed % SAVE_EVERY == 0) syncStateStore.save(SyncState(startState.cursor, bases))
        }

        val complete = !stoppedEarly && !withhold && deferred == 0 && failures.isEmpty()
        val cursor = if (complete) remote.cursor.ifBlank { null } else startState.cursor
        syncStateStore.save(SyncState(cursor, bases))

        if (status == SyncStatus.SYNCING) status = SyncStatus.IDLE
        failedFiles = failures
        if (!stoppedEarly) {
            settingsStore.update { it.copy(sync = it.sync.copy(lastSyncAt = System.currentTimeMillis())) }
        }

        val summary = SyncSummary(
            successCount = transferred,
            failedCount = failures.size,
            skippedCount = local.excludedKeys.size,
            failures = failures,
            deletedCount = deleted,
            conflictCount = conflicts,
            withheldDeletions = if (withhold) {
                deletions.map { local.files[it.key]?.rel ?: bases[it.key]?.pathDisplay ?: it.key }
            } else emptyList(),
            deferredCount = deferred,
            movedCount = moved,
        )
        lastSummary = summary
        return summary
    }

    /**
     * The first pass after upgrading from one-way sync. Until then only the PC wrote to Dropbox, so
     * a book whose content differs on both sides with no base is normally a conflict, except when
     * the PC itself knew it had an unsent change (preprocessed again after its last upload): then
     * the PC copy simply replaces the remote one, as one-way sync would have done.
     */
    private fun upgradeOverride(key: String, base: SyncBase?, action: SyncAction): SyncAction {
        if (base != null || action !is SyncAction.Conflict) return action
        val record = bookStore.findByKey(key) ?: return action
        val uploadedAt = record.uploadedAt ?: return action
        val preprocessedAt = record.preprocessedAt ?: return action
        return if (uploadedAt < preprocessedAt) SyncAction.Upload(parentRev = action.remote.rev) else action
    }

    // ── Applying one action ───────────────────────────────────────────────

    private fun apply(
        plan: Plan,
        local: LocalScan,
        remote: RemoteSnapshot,
        bases: MutableMap<String, SyncBase>,
        retried: Boolean = false,
    ): Outcome {
        plan.move?.let { return applyMove(it, plan.fallback, local, remote, bases) }
        val key = plan.key
        val entry = local.files[key]
        return when (val action = plan.action) {
            SyncAction.None -> Outcome.Done()

            SyncAction.ForgetBase -> {
                bases.remove(key)
                Outcome.Done()
            }

            is SyncAction.AdoptBase -> {
                val e = entry ?: return Outcome.Failed("Local file vanished")
                bases[key] = SyncBase(key, action.remote.pathDisplay, action.remote.rev, action.remote.contentHash, e.state.size, e.state.mtime)
                Outcome.Done()
            }

            is SyncAction.Download -> {
                if (entry != null && isOpenInReader(entry.path)) return Outcome.Deferred
                download(key, action.remote, entry, bases)
            }

            SyncAction.DeleteLocal -> {
                val e = entry ?: run {
                    bases.remove(key)
                    return Outcome.Done()
                }
                if (isOpenInReader(e.path)) return Outcome.Deferred
                val moveToTrash = trash ?: return Outcome.Failed("No recycle bin on this system; not deleting permanently")
                if (!moveToTrash(e.path)) return Outcome.Failed("Could not move to the recycle bin")
                bases.remove(key)
                pruneEmptyParents(e.path.parent)
                Outcome.Done(deleted = true)
            }

            is SyncAction.Upload -> {
                val e = entry ?: return Outcome.Failed("Local file vanished")
                when (val result = upload(e, action.parentRev)) {
                    is DropboxUploadResult.Success -> {
                        bases[key] = SyncBase(
                            key, e.rel, result.rev, result.contentHash.ifBlank { e.state.contentHash },
                            e.state.size, e.state.mtime,
                        )
                        Outcome.Done(transferred = true)
                    }
                    is DropboxUploadResult.Conflict ->
                        if (retried) Outcome.Failed(result.message) else redecide(plan, local, remote, bases, e.rel)
                    DropboxUploadResult.InsufficientSpace ->
                        Outcome.Failed("Dropbox storage full (insufficient space)", insufficientSpace = true)
                    is DropboxUploadResult.Failure -> Outcome.Failed(result.message)
                }
            }

            is SyncAction.DeleteRemote -> {
                val base = bases[key] ?: return Outcome.Done()
                when (val result = dropboxClient.deleteFile(remotePathOf(base.pathDisplay), action.parentRev)) {
                    DropboxDeleteResult.Deleted, DropboxDeleteResult.NotFound -> {
                        bases.remove(key)
                        Outcome.Done(deleted = true)
                    }
                    is DropboxDeleteResult.Conflict ->
                        if (retried) Outcome.Failed(result.message) else redecide(plan, local, remote, bases, base.pathDisplay)
                    is DropboxDeleteResult.Failure -> Outcome.Failed(result.message)
                }
            }

            is SyncAction.Conflict -> conflict(key, action.remote, entry, remote, bases)
        }
    }

    private fun applyMove(
        move: Move,
        fallback: List<Plan>,
        local: LocalScan,
        remote: RemoteSnapshot,
        bases: MutableMap<String, SyncBase>,
    ): Outcome {
        val done = when (move.kind) {
            MoveKind.LOCAL -> moveOnDropbox(move, local, bases)
            MoveKind.REMOTE -> moveLocally(move, local, remote, bases)
        }
        if (done != null) return done
        // Could not move (name taken, file gone, ...): do what the two halves said on their own.
        var last: Outcome = Outcome.Done()
        for (plan in fallback) {
            last = apply(plan, local, remote, bases)
            if (last !is Outcome.Done) return last
        }
        return last
    }

    /** Renamed or moved on this PC: move the Dropbox copy too. Null if that was refused. */
    private fun moveOnDropbox(move: Move, local: LocalScan, bases: MutableMap<String, SyncBase>): Outcome? {
        val base = bases[move.from] ?: return null
        val target = local.files[move.to] ?: return null
        val result = dropboxClient.moveFile(remotePathOf(base.pathDisplay), remotePathOf(target.rel))
        if (result !is DropboxMoveResult.Moved) return null
        bases.remove(move.from)
        bases[move.to] = SyncBase(
            move.to, target.rel, result.entry.rev, result.entry.contentHash.ifBlank { target.state.contentHash },
            target.state.size, target.state.mtime,
        )
        // Intake registered the new name as a new book; give it the old one's reading position.
        val from = bookStore.findByKey(move.from)
        val to = bookStore.findByKey(move.to)
        if (from != null && to != null) {
            val anchor = from.anchor.coerceIn(0, maxOf(to.totalCharCount, 0))
            bookStore.addOrUpdate(to.copy(anchor = anchor, progress = from.progress, lastOpenedAt = from.lastOpenedAt))
            bookStore.flush()
        }
        onBookMoved?.invoke(base.pathDisplay, target.rel)
        return Outcome.Done(moved = true)
    }

    /** Renamed or moved on another device: do the same here instead of downloading again. */
    private fun moveLocally(move: Move, local: LocalScan, remote: RemoteSnapshot, bases: MutableMap<String, SyncBase>): Outcome? {
        val source = local.files[move.from] ?: return null
        val destination = remote.files[move.to] ?: return null
        if (isOpenInReader(source.path)) return Outcome.Deferred
        val root = homeFolder.toAbsolutePath().normalize()
        val target = root.resolve(destination.pathDisplay).normalize()
        if (!target.startsWith(root) || target == root || Files.exists(target)) return null

        // Registered before the file appears, so the watcher sees a known, preprocessed book.
        bookStore.findByKey(move.from)?.let { record ->
            bookStore.addOrUpdate(
                record.copy(
                    path = destination.pathDisplay,
                    key = move.to,
                    displayName = destination.pathDisplay.substringAfterLast('/').removeSuffix(".txt"),
                ),
            )
            bookStore.flush()
        }
        try {
            Files.createDirectories(target.parent)
            Files.move(source.path, target)
        } catch (_: IOException) {
            return null
        }
        pruneEmptyParents(source.path.parent)
        bases.remove(move.from)
        bases[move.to] = SyncBase(
            move.to, destination.pathDisplay, destination.rev, destination.contentHash,
            Files.size(target), Files.getLastModifiedTime(target).toMillis(),
        )
        onBookMoved?.invoke(source.rel, destination.pathDisplay)
        return Outcome.Done(moved = true)
    }

    /**
     * A conditional write was refused: another device changed the file since this pass listed it.
     * Re-read that one file and decide again, once.
     */
    private fun redecide(
        plan: Plan,
        local: LocalScan,
        remote: RemoteSnapshot,
        bases: MutableMap<String, SyncBase>,
        rel: String,
    ): Outcome {
        val fresh = when (val meta = dropboxClient.getMetadata(remotePathOf(rel))) {
            is DropboxMetadataResult.Found -> (meta.entry as? DropboxEntry.FileEntry)?.let(::toRemoteState)
            DropboxMetadataResult.NotFound -> null
            is DropboxMetadataResult.Failure -> return Outcome.Failed(meta.message)
        }
        val action = SyncDecision.decide(bases[plan.key], local.files[plan.key]?.state, fresh)
        return apply(Plan(plan.key, action), local, remote, bases, retried = true)
    }

    private fun upload(entry: LocalEntry, parentRev: String?): DropboxUploadResult {
        val bytes = try {
            Files.readAllBytes(entry.path)
        } catch (e: IOException) {
            return DropboxUploadResult.Failure(e.message ?: "Read error")
        }
        return dropboxClient.uploadFile(remotePathOf(entry.rel), bytes, updateRev = parentRev)
    }

    private fun download(
        key: String,
        remote: RemoteFileState,
        existing: LocalEntry?,
        bases: MutableMap<String, SyncBase>,
        targetRel: String = existing?.rel ?: remote.pathDisplay,
    ): Outcome {
        val root = homeFolder.toAbsolutePath().normalize()
        val target = root.resolve(targetRel).normalize()
        // The remote path decides where a file is written; it must stay inside the library.
        if (!target.startsWith(root) || target == root) return Outcome.Failed("Unsafe remote path")
        val bytes = dropboxClient.downloadFile(remotePathOf(remote.pathDisplay)) ?: return Outcome.Failed("Download failed")
        if (remote.contentHash.isNotBlank() && ContentHash.of(bytes) != remote.contentHash) {
            return Outcome.Failed("Downloaded content does not match Dropbox's content_hash")
        }

        val tmpDir = homeFolder.resolve(TMP_DIR)
        Files.createDirectories(tmpDir)
        val tmp = Files.createTempFile(tmpDir, "download_", ".txt")
        try {
            Files.write(tmp, bytes)
            registerDownloaded(key, targetRel, tmp)
            Files.createDirectories(target.parent)
            moveReplacing(tmp, target)
        } finally {
            Files.deleteIfExists(tmp)
        }

        val size = Files.size(target)
        val mtime = Files.getLastModifiedTime(target).toMillis()
        // Reconcile re-reads a book whose mtime is newer than preprocessedAt; coarse file-system
        // timestamps can round the mtime past the registration time.
        bookStore.findByKey(key)?.let { bookStore.addOrUpdate(it.copy(preprocessedAt = maxOf(it.preprocessedAt ?: 0L, mtime))) }
        bookStore.flush()
        bases[key] = SyncBase(key, remote.pathDisplay, remote.rev, remote.contentHash, size, mtime)
        return Outcome.Done(transferred = true)
    }

    /**
     * Records the downloaded book as already preprocessed before it appears in the library.
     * Dropbox holds only preprocessed files (AGENTS.md §1), so running the preprocessor again would
     * only rewrite identical bytes and leave a pointless backup in `.flonovel/original`.
     */
    private fun registerDownloaded(key: String, rel: String, content: Path) {
        val loaded = TextLoader.load(content)
        val charCount = loaded.text.length
        val chapterCount = runCatching { ChapterDetector.detect(loaded.text).size }.getOrDefault(-1)
        val size = Files.size(content)
        val now = System.currentTimeMillis()
        val existing = bookStore.findByKey(key)
        val record = if (existing != null) {
            val anchor = existing.anchor.coerceIn(0, charCount)
            existing.copy(
                path = rel,
                sizeBytes = size,
                totalCharCount = charCount,
                chapterCount = chapterCount,
                detectedEncoding = loaded.charset.name(),
                anchor = anchor,
                progress = if (charCount > 0) anchor.toDouble() / charCount else 0.0,
                preprocessedAt = now,
            )
        } else {
            BookRecord(
                path = rel,
                key = key,
                displayName = rel.substringAfterLast('/').removeSuffix(".txt"),
                sizeBytes = size,
                totalCharCount = charCount,
                chapterCount = chapterCount,
                detectedEncoding = loaded.charset.name(),
                anchor = 0,
                progress = 0.0,
                preprocessedAt = now,
            )
        }
        bookStore.addOrUpdate(record)
        bookStore.flush()
    }

    /**
     * Keep both: the local copy is renamed to a conflict copy and uploaded as a new file, then the
     * remote copy is downloaded under the original name.
     */
    private fun conflict(
        key: String,
        remoteFile: RemoteFileState,
        entry: LocalEntry?,
        remote: RemoteSnapshot,
        bases: MutableMap<String, SyncBase>,
    ): Outcome {
        val e = entry ?: return download(key, remoteFile, null, bases)
        if (isOpenInReader(e.path)) return Outcome.Deferred

        val folderRel = e.rel.substringBeforeLast('/', "")
        val copyName = SyncDecision.conflictCopyName(e.path.fileName.toString(), conflictLabel, deviceName, today()) { name ->
            val rel = if (folderRel.isEmpty()) name else "$folderRel/$name"
            Files.exists(e.path.resolveSibling(name)) || RelativePath.normalize(rel) in remote.files
        }
        val copyRel = if (folderRel.isEmpty()) copyName else "$folderRel/$copyName"
        val copyKey = RelativePath.normalize(copyRel)
        val copyPath = e.path.resolveSibling(copyName)

        // Register the copy first, so the watcher sees a known, preprocessed book under the new name
        // instead of a new file to preprocess (which would also cut the long name to 50 runes).
        // The original record stays for the remote version downloaded below; both keep the
        // reading position.
        bookStore.findByKey(key)?.let { record ->
            bookStore.addOrUpdate(record.copy(path = copyRel, key = copyKey, displayName = copyName.removeSuffix(".txt")))
            bookStore.flush()
        }
        Files.move(e.path, copyPath)

        val copyEntry = LocalEntry(copyPath, copyRel, e.state)
        when (val up = upload(copyEntry, parentRev = null)) {
            is DropboxUploadResult.Success ->
                bases[copyKey] = SyncBase(copyKey, copyRel, up.rev, up.contentHash.ifBlank { e.state.contentHash }, e.state.size, e.state.mtime)
            // Not fatal: with no base the copy is uploaded as new on a later pass.
            else -> Unit
        }

        bases.remove(key)
        return when (val down = download(key, remoteFile, null, bases, targetRel = e.rel)) {
            is Outcome.Done -> Outcome.Done(transferred = true, conflict = true)
            else -> down
        }
    }

    // ── Scanning ─────────────────────────────────────────────────────────

    private fun scanLocal(bases: Map<String, SyncBase>): LocalScan {
        val files = LinkedHashMap<String, LocalEntry>()
        val excluded = mutableSetOf<String>()
        val failures = mutableListOf<SyncFileFailure>()

        for (file in collectLocalEligibleFiles()) {
            val rel = toRelPath(file)
            val key = RelativePath.normalize(rel)
            val record = bookStore.findByKey(key)
            if (record == null || record.preprocessedAt == null) {
                // Still in the intake pipeline. Leaving it out, rather than letting it look absent,
                // keeps a base for it from turning into a remote delete.
                excluded += key
                continue
            }
            if (key in files) {
                // Two names that differ only in case (possible on Linux) map to one key; syncing
                // either would overwrite the other.
                files.remove(key)
                excluded += key
                failures += SyncFileFailure(rel, "Another file differs from this one only in letter case")
                continue
            }
            val path = file.toPath()
            val size = Files.size(path)
            val mtime = Files.getLastModifiedTime(path).toMillis()
            val base = bases[key]
            val hash = if (SyncDecision.needsLocalHash(base, size, mtime)) ContentHash.of(path) else base!!.contentHash
            files[key] = LocalEntry(path, rel, LocalFileState(size, mtime, hash))
        }
        return LocalScan(files, excluded, failures)
    }

    /** Local `.txt` files under the home folder, skipping anything under a dot-prefixed name. */
    internal fun collectLocalEligibleFiles(): List<File> {
        if (!Files.exists(homeFolder)) return emptyList()
        val results = mutableListOf<File>()

        fun scanDir(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.name.startsWith(".")) continue
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
     * The remote view. A delta listing only names what changed, so it is laid over what the bases
     * say the remote held; a full listing (no cursor, or the cursor was reset) replaces it.
     */
    private fun fetchRemote(state: SyncState): RemoteSnapshot? {
        var full = state.cursor == null
        var result = if (full) listBooksRoot() else dropboxClient.listFolderContinue(state.cursor!!)
        if (result is DropboxListFolderResult.Reset) {
            full = true
            result = listBooksRoot()
        }

        val files = LinkedHashMap<String, RemoteFileState>()
        if (!full) {
            for (base in state.bases.values) {
                files[base.key] = RemoteFileState(base.pathDisplay, base.rev, base.contentHash, base.localSize)
            }
        }

        while (true) {
            when (val current = result) {
                is DropboxListFolderResult.Success -> {
                    for (entry in current.entries) applyListingEntry(entry, files)
                    if (!current.hasMore) return RemoteSnapshot(files, current.cursor, full)
                    result = dropboxClient.listFolderContinue(current.cursor)
                }
                DropboxListFolderResult.Reset -> {
                    // A reset mid-way invalidates the pages already read; start over from scratch.
                    full = true
                    files.clear()
                    result = listBooksRoot()
                }
                is DropboxListFolderResult.Failure -> return null
            }
        }
    }

    /** A missing `/books` is an empty remote, not an error: nothing has been uploaded yet. */
    private fun listBooksRoot(): DropboxListFolderResult {
        val result = dropboxClient.listFolder(REMOTE_ROOT, recursive = true)
        if (result is DropboxListFolderResult.Failure && result.statusCode == 409 && result.message.contains("not_found")) {
            return DropboxListFolderResult.Success(emptyList(), cursor = "", hasMore = false)
        }
        return result
    }

    private fun applyListingEntry(entry: DropboxEntry, files: MutableMap<String, RemoteFileState>) {
        when (entry) {
            is DropboxEntry.FileEntry -> {
                val state = toRemoteState(entry) ?: return
                files[RelativePath.normalize(state.pathDisplay)] = state
            }
            is DropboxEntry.DeletedEntry -> {
                val rel = relativeToRoot(entry.pathDisplay.ifBlank { entry.pathLower }) ?: return
                val key = RelativePath.normalize(rel)
                // A deleted folder arrives as one entry for the folder alone.
                files.remove(key)
                files.keys.removeIf { it.startsWith("$key/") }
            }
            is DropboxEntry.FolderEntry -> Unit
        }
    }

    private fun toRemoteState(entry: DropboxEntry.FileEntry): RemoteFileState? {
        val rel = relativeToRoot(entry.pathDisplay) ?: return null
        if (!rel.endsWith(".txt", ignoreCase = true)) return null
        // Mirrors the local scan: anything under a dot-folder is not a book on either side.
        if (rel.split('/').any { it.startsWith(".") }) return null
        return RemoteFileState(rel, entry.rev, entry.contentHash, entry.size)
    }

    private fun relativeToRoot(dropboxPath: String): String? {
        val prefix = "$REMOTE_ROOT/"
        if (!dropboxPath.startsWith(prefix, ignoreCase = true)) return null
        val rel = dropboxPath.substring(prefix.length)
        val segments = rel.split('/')
        if (rel.isBlank() || segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return rel
    }

    private fun remotePathOf(rel: String) = "$REMOTE_ROOT/$rel"

    private fun pruneEmptyParents(start: Path?) {
        val root = homeFolder.toAbsolutePath().normalize()
        var dir = start?.toAbsolutePath()?.normalize()
        while (dir != null && dir != root && dir.startsWith(root)) {
            val empty = runCatching { Files.list(dir).use { !it.findAny().isPresent } }.getOrDefault(false)
            if (!empty || !runCatching { Files.delete(dir) }.isSuccess) break
            dir = dir.parent
        }
    }

    private fun moveReplacing(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val REMOTE_ROOT = "/books"

        /** Inside a dot-folder, so neither the library scan nor the intake watcher sees it. */
        const val TMP_DIR = ".flonovel/tmp"

        private const val SAVE_EVERY = 25
    }
}

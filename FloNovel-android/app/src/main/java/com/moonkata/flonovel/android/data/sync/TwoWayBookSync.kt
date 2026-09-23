package com.moonkata.flonovel.android.data.sync

import com.moonkata.flonovel.android.data.db.SyncBaseDao
import com.moonkata.flonovel.android.data.db.SyncBaseEntity

data class TwoWaySyncResult(
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val deletedLocal: Int = 0,
    val deletedRemote: Int = 0,
    val conflicts: Int = 0,
    val failed: Int = 0,
    /** Paths of deletions held back by the mass-deletion guard; non-empty means ask the user. */
    val withheldDeletions: List<String> = emptyList(),
    /** Uploads and remote deletes skipped because the link predates write access. */
    val waitingForWriteAccess: Int = 0,
) {
    val changed: Int get() = downloaded + uploaded + deletedLocal + deletedRemote + conflicts
}

/**
 * Two-way sync of the library folder with Dropbox `/books` (CLAUDE.md §1, "동기화 — 파일"). The
 * same pass as the Desktop engine, adapted to the phone:
 *
 * - Downloads write in place (the reading position is keyed by the document URI) and mark the
 *   base DOWNLOADING first. SAF has no atomic replace, so an interrupted download leaves a short
 *   file; the mark makes the next pass fetch it again instead of uploading the truncated copy.
 * - A conflict uploads the local content as a conflict copy first, then overwrites the original
 *   in place, so the original keeps its URI and reading position.
 * - There is no recycle bin on SAF: a remote deletion deletes the local file.
 * - Without write access (linked before two-way sync) the pass still downloads and applies remote
 *   deletions, and holds local changes until the user reconnects.
 */
class TwoWayBookSync(
    private val files: LibraryFiles,
    private val client: DropboxClient,
    private val baseDao: SyncBaseDao,
    private val loadCursor: suspend () -> String,
    private val saveCursor: suspend (String) -> Unit,
    private val canWrite: Boolean,
    /** This phone synced with the one-way engine before; see [upgradeOverride]. */
    private val syncedOneWayBefore: Boolean,
    private val conflictLabel: String,
    /** `yyyy-MM-dd`, preformatted: java.time needs API 26 and minSdk is 24. */
    private val today: () -> String,
    /**
     * Preprocesses a book added on this phone before its first upload (Dropbox holds only
     * preprocessed files, CLAUDE.md §1) and returns where it now is; the name can change. Null when
     * it could not be preprocessed, so it is not uploaded raw.
     */
    private val prepareNewBook: suspend (relativePath: String) -> String?,
    private val deviceName: String = "Android",
) {
    private data class LocalEntry(val file: LibraryFile, val state: LocalFileState)
    private data class RemoteSnapshot(val files: Map<String, RemoteFileState>, val cursor: String, val isFullListing: Boolean)
    private data class Plan(val key: String, val action: SyncAction)

    private sealed class Outcome {
        data class Done(
            val downloaded: Boolean = false,
            val uploaded: Boolean = false,
            val deletedLocal: Boolean = false,
            val deletedRemote: Boolean = false,
            val conflict: Boolean = false,
        ) : Outcome()
        object WaitingForWriteAccess : Outcome()
        object Failed : Outcome()
    }

    /** Null when the pass could not start (the remote listing failed). */
    suspend fun sync(
        allowMassDeletion: Boolean = false,
        onProgress: (DropboxSyncProgress) -> Unit = {},
    ): TwoWaySyncResult? {
        val bases = baseDao.getAll().mapNotNull { it.toBase() }.associateBy { it.key }.toMutableMap()
        val upgrading = bases.isEmpty() && syncedOneWayBefore
        val startCursor = loadCursor()
        val remote = fetchRemote(startCursor, bases) ?: return null
        val scan = scanLocal(bases)
        val local = scan.files

        val keys = ((bases.keys + local.keys + remote.files.keys) - scan.excludedKeys).sorted()
        val plans = keys.mapNotNull { key ->
            var action = SyncDecision.decide(bases[key], local[key]?.state, remote.files[key])
            if (upgrading) action = upgradeOverride(bases[key], action)
            if (action == SyncAction.None) null else Plan(key, action)
        }

        val deletions = plans.filter { it.action is SyncAction.DeleteLocal || it.action is SyncAction.DeleteRemote }
        val withhold = !allowMassDeletion && deletions.isNotEmpty() &&
            SyncDecision.isMassDeletion(deletions.size, bases.size, remote.isFullListing && remote.files.isEmpty())
        val toApply = if (withhold) plans - deletions.toSet() else plans

        var result = TwoWaySyncResult()
        var complete = !withhold
        for ((index, plan) in toApply.withIndex()) {
            onProgress(DropboxSyncProgress(index, toApply.size, displayPath(plan.key, local, remote, bases)))
            val outcome = runCatching { apply(plan, local, remote, bases, retried = false) }.getOrDefault(Outcome.Failed)
            result = when (outcome) {
                is Outcome.Done -> result.copy(
                    downloaded = result.downloaded + if (outcome.downloaded) 1 else 0,
                    uploaded = result.uploaded + if (outcome.uploaded) 1 else 0,
                    deletedLocal = result.deletedLocal + if (outcome.deletedLocal) 1 else 0,
                    deletedRemote = result.deletedRemote + if (outcome.deletedRemote) 1 else 0,
                    conflicts = result.conflicts + if (outcome.conflict) 1 else 0,
                )
                Outcome.WaitingForWriteAccess -> {
                    complete = false
                    result.copy(waitingForWriteAccess = result.waitingForWriteAccess + 1)
                }
                Outcome.Failed -> {
                    complete = false
                    result.copy(failed = result.failed + 1)
                }
            }
        }

        // Bases were written as each action landed; the cursor only moves once everything did.
        if (complete) saveCursor(remote.cursor)
        return result.copy(
            withheldDeletions = if (withhold) deletions.map { displayPath(it.key, local, remote, bases) } else emptyList(),
        )
    }

    /**
     * The first pass after the one-way engine. Until now only the PC wrote to Dropbox and the phone
     * only downloaded, so a book present on both sides with different content and no base is a
     * download that never finished, not a local edit: the remote copy wins.
     */
    private fun upgradeOverride(base: SyncBase?, action: SyncAction): SyncAction =
        if (base == null && action is SyncAction.Conflict) SyncAction.Download(action.remote) else action

    // ── Applying one action ───────────────────────────────────────────────

    private suspend fun apply(
        plan: Plan,
        local: Map<String, LocalEntry>,
        remote: RemoteSnapshot,
        bases: MutableMap<String, SyncBase>,
        retried: Boolean,
    ): Outcome {
        val key = plan.key
        val entry = local[key]
        return when (val action = plan.action) {
            SyncAction.None -> Outcome.Done()

            SyncAction.ForgetBase -> {
                forget(key, bases)
                Outcome.Done()
            }

            is SyncAction.AdoptBase -> {
                val e = entry ?: return Outcome.Failed
                record(SyncBase(key, action.remote.pathDisplay, action.remote.rev, action.remote.contentHash, e.file.sizeBytes, e.file.lastModifiedMillis), bases)
                Outcome.Done()
            }

            is SyncAction.Download -> download(key, action.remote, entry?.file?.relativePath ?: action.remote.pathDisplay, bases)

            SyncAction.DeleteLocal -> {
                val e = entry ?: run {
                    forget(key, bases)
                    return Outcome.Done()
                }
                if (!files.delete(e.file.relativePath)) return Outcome.Failed
                forget(key, bases)
                Outcome.Done(deletedLocal = true)
            }

            is SyncAction.Upload -> {
                val e = entry ?: return Outcome.Failed
                if (!canWrite) return Outcome.WaitingForWriteAccess
                if (bases[key] == null && action.parentRev == null) return uploadNewBook(e, bases)
                when (val result = upload(e.file, e.file.relativePath, action.parentRev)) {
                    is DropboxUploadResult.Success -> {
                        record(SyncBase(key, e.file.relativePath, result.rev, result.contentHash.ifBlank { e.state.contentHash }, e.file.sizeBytes, e.file.lastModifiedMillis), bases)
                        Outcome.Done(uploaded = true)
                    }
                    DropboxUploadResult.Conflict ->
                        if (retried) Outcome.Failed else redecide(plan, local, remote, bases, e.file.relativePath)
                    DropboxUploadResult.MissingScope -> Outcome.WaitingForWriteAccess
                    DropboxUploadResult.InsufficientSpace, is DropboxUploadResult.Failure -> Outcome.Failed
                }
            }

            is SyncAction.DeleteRemote -> {
                val base = bases[key] ?: return Outcome.Done()
                if (!canWrite) return Outcome.WaitingForWriteAccess
                when (client.deleteFile(remotePathOf(base.pathDisplay), action.parentRev)) {
                    DropboxDeleteResult.Deleted, DropboxDeleteResult.NotFound -> {
                        forget(key, bases)
                        Outcome.Done(deletedRemote = true)
                    }
                    DropboxDeleteResult.Conflict ->
                        if (retried) Outcome.Failed else redecide(plan, local, remote, bases, base.pathDisplay)
                    DropboxDeleteResult.MissingScope -> Outcome.WaitingForWriteAccess
                    is DropboxDeleteResult.Failure -> Outcome.Failed
                }
            }

            is SyncAction.Conflict -> conflict(key, action.remote, entry, remote, bases)
        }
    }

    /**
     * A book added on this phone: preprocess, then upload under the name it ends up with. A name
     * already taken remotely is refused by `mode=add`; the next pass then sees both copies and
     * adopts or keeps both, like any other first meeting.
     */
    private suspend fun uploadNewBook(entry: LocalEntry, bases: MutableMap<String, SyncBase>): Outcome {
        val rel = prepareNewBook(entry.file.relativePath) ?: return Outcome.Failed
        val file = files.stat(rel) ?: return Outcome.Failed
        val hash = files.openRead(rel)?.use { ContentHash.of(it) } ?: return Outcome.Failed
        return when (val result = upload(file, rel, parentRev = null)) {
            is DropboxUploadResult.Success -> {
                record(SyncBase(syncKeyOf(rel), rel, result.rev, result.contentHash.ifBlank { hash }, file.sizeBytes, file.lastModifiedMillis), bases)
                Outcome.Done(uploaded = true)
            }
            DropboxUploadResult.MissingScope -> Outcome.WaitingForWriteAccess
            else -> Outcome.Failed
        }
    }

    /** A conditional write was refused; re-read that one file and decide again, once. */
    private suspend fun redecide(
        plan: Plan,
        local: Map<String, LocalEntry>,
        remote: RemoteSnapshot,
        bases: MutableMap<String, SyncBase>,
        rel: String,
    ): Outcome {
        val fresh = when (val meta = client.getMetadata(remotePathOf(rel))) {
            is DropboxMetadataResult.Found -> toRemoteState(meta.file)
            DropboxMetadataResult.NotFound -> null
            is DropboxMetadataResult.Failure -> return Outcome.Failed
        }
        val action = SyncDecision.decide(bases[plan.key], local[plan.key]?.state, fresh)
        return apply(Plan(plan.key, action), local, remote, bases, retried = true)
    }

    private suspend fun upload(file: LibraryFile, targetRel: String, parentRev: String?): DropboxUploadResult =
        client.uploadFile(remotePathOf(targetRel), file.sizeBytes, parentRev) { files.openRead(file.relativePath) }

    private suspend fun download(key: String, remote: RemoteFileState, targetRel: String, bases: MutableMap<String, SyncBase>): Outcome {
        val previous = bases[key]
        // Marked before the first byte is written: if this dies halfway, the next pass re-fetches
        // instead of reading the short file as a local edit.
        record(
            (previous ?: SyncBase(key, remote.pathDisplay, remote.rev, remote.contentHash, 0, 0))
                .copy(state = BaseState.DOWNLOADING),
            bases,
        )
        val written = files.write(targetRel) { out ->
            val hashing = HashingOutputStream(out)
            val downloaded = client.downloadFile(remotePathOf(remote.pathDisplay), hashing)
            downloaded is DropboxDownloadResult.Success &&
                (remote.contentHash.isBlank() || hashing.contentHash() == remote.contentHash)
        }
        if (!written) return Outcome.Failed
        val stat = files.stat(targetRel) ?: return Outcome.Failed
        record(SyncBase(key, remote.pathDisplay, remote.rev, remote.contentHash, stat.sizeBytes, stat.lastModifiedMillis), bases)
        return Outcome.Done(downloaded = true)
    }

    /**
     * Keep both. The local content goes up first as a conflict copy (nothing local has changed if
     * that fails), then a local copy file is written, and last the original is overwritten in place
     * with the remote version so it keeps its URI and reading position.
     */
    private suspend fun conflict(
        key: String,
        remoteFile: RemoteFileState,
        entry: LocalEntry?,
        remote: RemoteSnapshot,
        bases: MutableMap<String, SyncBase>,
    ): Outcome {
        val e = entry ?: return download(key, remoteFile, remoteFile.pathDisplay, bases)
        if (!canWrite) return Outcome.WaitingForWriteAccess

        val rel = e.file.relativePath
        val folder = rel.substringBeforeLast('/', "")
        val existingLocal = files.list().map { syncKeyOf(it.relativePath) }.toSet()
        val copyName = SyncDecision.conflictCopyName(rel.substringAfterLast('/'), conflictLabel, deviceName, today()) { name ->
            val candidate = syncKeyOf(if (folder.isEmpty()) name else "$folder/$name")
            candidate in existingLocal || candidate in remote.files
        }
        val copyRel = if (folder.isEmpty()) copyName else "$folder/$copyName"
        val copyKey = syncKeyOf(copyRel)

        val up = upload(e.file, copyRel, parentRev = null)
        if (up !is DropboxUploadResult.Success) {
            return if (up == DropboxUploadResult.MissingScope) Outcome.WaitingForWriteAccess else Outcome.Failed
        }
        val copied = files.write(copyRel) { out ->
            files.openRead(rel)?.use { it.copyTo(out); true } ?: false
        }
        if (copied) {
            files.stat(copyRel)?.let { stat ->
                record(SyncBase(copyKey, copyRel, up.rev, up.contentHash.ifBlank { e.state.contentHash }, stat.sizeBytes, stat.lastModifiedMillis), bases)
            }
        }
        return when (val down = download(key, remoteFile, rel, bases)) {
            is Outcome.Done -> Outcome.Done(downloaded = true, conflict = true)
            else -> down
        }
    }

    private suspend fun record(base: SyncBase, bases: MutableMap<String, SyncBase>) {
        bases[base.key] = base
        baseDao.upsert(SyncBaseEntity.from(base))
    }

    private suspend fun forget(key: String, bases: MutableMap<String, SyncBase>) {
        bases.remove(key)
        baseDao.deleteByKey(key)
    }

    // ── Scanning ─────────────────────────────────────────────────────────

    private class LocalScan(val files: Map<String, LocalEntry>, val excludedKeys: Set<String>)

    private fun scanLocal(bases: Map<String, SyncBase>): LocalScan {
        val out = LinkedHashMap<String, LocalEntry>()
        val excluded = mutableSetOf<String>()
        for (file in files.list()) {
            val key = syncKeyOf(file.relativePath)
            if (key in out) {
                // Two names differing only in case map to one key; syncing either would clobber
                // the other.
                out.remove(key)
                excluded += key
                continue
            }
            val base = bases[key]
            val hash = if (SyncDecision.needsLocalHash(base, file.sizeBytes, file.lastModifiedMillis)) {
                // Unreadable right now: it sits this pass out. Leaving it out of `out` alone would
                // make it look deleted, and a base would turn that into a remote delete.
                runCatching { files.openRead(file.relativePath)?.use { ContentHash.of(it) } }.getOrNull()
                    ?: run {
                        excluded += key
                        null
                    }
                    ?: continue
            } else {
                base!!.contentHash
            }
            out[key] = LocalEntry(file, LocalFileState(file.sizeBytes, file.lastModifiedMillis, hash))
        }
        return LocalScan(out, excluded)
    }

    /**
     * The remote view: bases overlaid with the delta since the cursor, or a full listing when
     * there is no cursor or no base to overlay (a delta alone cannot describe unchanged files).
     */
    private suspend fun fetchRemote(cursor: String, bases: Map<String, SyncBase>): RemoteSnapshot? {
        var full = cursor.isBlank() || bases.isEmpty()
        var result = if (full) listRoot() else client.listFolderContinue(cursor)
        if (result is DropboxListResult.CursorReset) {
            full = true
            result = listRoot()
        }
        val remote = LinkedHashMap<String, RemoteFileState>()
        if (!full) bases.values.forEach { remote[it.key] = RemoteFileState(it.pathDisplay, it.rev, it.contentHash, it.localSize) }

        while (true) {
            when (val current = result) {
                is DropboxListResult.Success -> {
                    for (entry in current.entries) applyListingEntry(entry, remote)
                    if (!current.hasMore) return RemoteSnapshot(remote, current.cursor, full)
                    result = client.listFolderContinue(current.cursor)
                }
                DropboxListResult.CursorReset -> {
                    full = true
                    remote.clear()
                    result = listRoot()
                }
                is DropboxListResult.Failure -> return null
            }
        }
    }

    /** A missing `/books` is an empty remote, not an error: the PC has uploaded nothing yet. */
    private suspend fun listRoot(): DropboxListResult {
        val result = client.listFolder()
        if (result is DropboxListResult.Failure && result.message.contains("not_found")) {
            return DropboxListResult.Success(emptyList(), cursor = "", hasMore = false)
        }
        return result
    }

    private fun applyListingEntry(entry: DropboxEntry, remote: MutableMap<String, RemoteFileState>) {
        when (entry) {
            is DropboxEntry.File -> toRemoteState(entry)?.let { remote[syncKeyOf(it.pathDisplay)] = it }
            is DropboxEntry.Deleted -> {
                val rel = relativeBookPath(entry.pathLower) ?: return
                val key = syncKeyOf(rel)
                // A deleted folder arrives as one entry for the folder alone.
                remote.remove(key)
                remote.keys.removeAll { it.startsWith("$key/") }
            }
        }
    }

    private fun toRemoteState(entry: DropboxEntry.File): RemoteFileState? {
        val rel = relativeBookPath(entry.pathDisplay) ?: return null
        if (!rel.endsWith(".txt", ignoreCase = true)) return null
        if (rel.split('/').any { it.startsWith(".") }) return null
        return RemoteFileState(rel, entry.rev, entry.contentHash, entry.size)
    }

    private fun displayPath(key: String, local: Map<String, LocalEntry>, remote: RemoteSnapshot, bases: Map<String, SyncBase>): String =
        local[key]?.file?.relativePath ?: remote.files[key]?.pathDisplay ?: bases[key]?.pathDisplay ?: key
}

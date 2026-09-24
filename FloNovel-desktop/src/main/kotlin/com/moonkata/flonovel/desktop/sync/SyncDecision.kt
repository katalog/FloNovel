package com.moonkata.flonovel.desktop.sync

import java.time.LocalDate

/**
 * Pure decision rules for two-way file sync (CLAUDE.md §1, "동기화 — 파일", target rules).
 *
 * Every decision compares local against the last agreed state (the base) and remote against the
 * base, separately. Comparing local against remote directly cannot tell "the other device added
 * this" from "I deleted this"; the one-way engine made exactly that inference, and in a two-way
 * world it deletes whatever the other device just added.
 *
 * The Android app carries its own copy of these rules. Both are pinned by the same case table in
 * their tests, so a change here must be made there too.
 */

enum class BaseState {
    SYNCED,

    /**
     * A download was started but not confirmed. The local bytes may be a truncated copy, so they
     * must never be read as a local edit and uploaded.
     */
    DOWNLOADING,
}

/** The last state both sides agreed on for one file. */
data class SyncBase(
    val key: String,
    val pathDisplay: String,
    val rev: String,
    val contentHash: String,
    val localSize: Long,
    val localMtime: Long,
    val state: BaseState = BaseState.SYNCED,
)

/**
 * A local file as seen now. [contentHash] may be the base's hash when [needsLocalHash] said the
 * file is untouched, so large libraries are not re-hashed on every sync.
 */
data class LocalFileState(
    val size: Long,
    val mtime: Long,
    val contentHash: String,
)

data class RemoteFileState(
    val pathDisplay: String,
    val rev: String,
    val contentHash: String,
    val size: Long,
)

sealed class SyncAction {
    data object None : SyncAction()

    /** Local and remote hold the same content; record [remote] and the local stat as the new base. */
    data class AdoptBase(val remote: RemoteFileState) : SyncAction()

    /** Both sides are gone; drop the base. */
    data object ForgetBase : SyncAction()

    data class Download(val remote: RemoteFileState) : SyncAction()

    /** Remote deleted a file this device had not changed. On the PC this goes to the recycle bin. */
    data object DeleteLocal : SyncAction()

    /** [parentRev] null means a new remote file (`mode=add`); otherwise `mode=update(parentRev)`. */
    data class Upload(val parentRev: String?) : SyncAction()

    data class DeleteRemote(val parentRev: String) : SyncAction()

    /**
     * Both sides changed the content differently. Keep both: the remote copy takes the original
     * name, the local copy is renamed with [conflictCopyName] and uploaded as a new file.
     */
    data class Conflict(val remote: RemoteFileState) : SyncAction()
}

object SyncDecision {

    /**
     * Whether the local file must be hashed, or the base's hash can stand in for it.
     * Size and mtime only decide this; they never decide which side is newer.
     */
    fun needsLocalHash(base: SyncBase?, size: Long, mtime: Long): Boolean =
        base == null || base.state != BaseState.SYNCED || base.localSize != size || base.localMtime != mtime

    fun decide(base: SyncBase?, local: LocalFileState?, remote: RemoteFileState?): SyncAction {
        if (base?.state == BaseState.DOWNLOADING) {
            // Whatever is on disk is not trustworthy: finish the download, or clean up after it.
            return when {
                remote != null -> SyncAction.Download(remote)
                local != null -> SyncAction.DeleteLocal
                else -> SyncAction.ForgetBase
            }
        }

        if (base == null) {
            return when {
                local == null && remote == null -> SyncAction.None
                local != null && remote == null -> SyncAction.Upload(parentRev = null)
                local == null && remote != null -> SyncAction.Download(remote)
                local!!.contentHash == remote!!.contentHash -> SyncAction.AdoptBase(remote)
                else -> SyncAction.Conflict(remote)
            }
        }

        val localChange = when {
            local == null -> Change.DELETED
            local.contentHash != base.contentHash -> Change.MODIFIED
            else -> Change.NONE
        }
        val remoteChange = when {
            remote == null -> Change.DELETED
            remote.contentHash != base.contentHash -> Change.MODIFIED
            else -> Change.NONE
        }

        return when (localChange) {
            Change.NONE -> when (remoteChange) {
                Change.NONE -> {
                    // Same content everywhere, but a re-upload of identical bytes bumps the rev and
                    // a touch changes the mtime; refresh the base so the next run stays cheap.
                    val stale = remote!!.rev != base.rev || local!!.size != base.localSize || local.mtime != base.localMtime
                    if (stale) SyncAction.AdoptBase(remote) else SyncAction.None
                }
                Change.MODIFIED -> SyncAction.Download(remote!!)
                Change.DELETED -> SyncAction.DeleteLocal
            }
            Change.MODIFIED -> when (remoteChange) {
                Change.NONE -> SyncAction.Upload(parentRev = remote!!.rev)
                Change.MODIFIED ->
                    if (local!!.contentHash == remote!!.contentHash) SyncAction.AdoptBase(remote)
                    else SyncAction.Conflict(remote)
                // An edit beats a deletion: bring the file back as new.
                Change.DELETED -> SyncAction.Upload(parentRev = null)
            }
            Change.DELETED -> when (remoteChange) {
                Change.NONE -> SyncAction.DeleteRemote(parentRev = remote!!.rev)
                // An edit beats a deletion: take the edited remote copy back.
                Change.MODIFIED -> SyncAction.Download(remote!!)
                Change.DELETED -> SyncAction.ForgetBase
            }
        }
    }

    /**
     * Whether a sync pass is about to delete too much to do it unasked.
     *
     * An empty remote next to a non-empty base is the loudest case: a different Dropbox account or
     * a reset app folder looks exactly like "the user deleted everything". The percentage rule only
     * kicks in from [MIN_COUNT_FOR_RATIO] deletions, or removing one book from a three-book library
     * would stop and ask every time.
     */
    fun isMassDeletion(deletions: Int, trackedFiles: Int, remoteIsEmpty: Boolean): Boolean {
        if (remoteIsEmpty && trackedFiles > 0) return true
        if (deletions >= MASS_DELETE_COUNT) return true
        return deletions >= MIN_COUNT_FOR_RATIO && deletions * 100 >= trackedFiles * MASS_DELETE_PERCENT
    }

    /**
     * `name (label - device - yyyy-MM-dd).ext`, then `..._1.ext`, `..._2.ext` while [exists] says
     * the name is taken. [label] is the UI-language word for "conflicted copy".
     */
    fun conflictCopyName(
        fileName: String,
        label: String,
        device: String,
        date: LocalDate,
        exists: (String) -> Boolean,
    ): String {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        val stem = "$base ($label - $device - $date)"
        val first = "$stem$ext"
        if (!exists(first)) return first
        var n = 1
        while (exists("${stem}_$n$ext")) n++
        return "${stem}_$n$ext"
    }

    const val MASS_DELETE_COUNT = 20
    const val MASS_DELETE_PERCENT = 30
    const val MIN_COUNT_FOR_RATIO = 5

    private enum class Change { NONE, MODIFIED, DELETED }
}

enum class MoveKind {
    /** Moved or renamed on this device: move it on Dropbox instead of re-uploading. */
    LOCAL,

    /** Moved or renamed on another device: move the local file instead of re-downloading. */
    REMOTE,
}

data class Move(val from: String, val to: String, val kind: MoveKind)

/**
 * Pairs a disappearance with an appearance of the same content, so a rename or a move is carried
 * out as one instead of a delete plus a transfer (which would also lose the reading position,
 * keyed by path).
 *
 * Only one-to-one matches count: when two books share the same content, which went where is a
 * guess, and those stay a delete plus a transfer. Keys are sync keys; [localHashes] holds the
 * local content hash of every local file.
 */
fun findMoves(actions: Map<String, SyncAction>, bases: Map<String, SyncBase>, localHashes: Map<String, String>): List<Move> {
    fun pair(sources: Map<String, String>, targets: Map<String, String>, kind: MoveKind): List<Move> {
        val sourcesByHash = sources.entries.groupBy({ it.value }, { it.key })
        val targetsByHash = targets.entries.groupBy({ it.value }, { it.key })
        return sourcesByHash.mapNotNull { (hash, from) ->
            val to = targetsByHash[hash] ?: return@mapNotNull null
            if (hash.isBlank() || from.size != 1 || to.size != 1) null else Move(from.single(), to.single(), kind)
        }
    }

    val localSources = actions.filter { it.value is SyncAction.DeleteRemote }.keys
        .mapNotNull { key -> bases[key]?.let { key to it.contentHash } }.toMap()
    val localTargets = actions.filter { (key, action) -> action == SyncAction.Upload(null) && bases[key] == null }.keys
        .mapNotNull { key -> localHashes[key]?.let { key to it } }.toMap()

    val remoteSources = actions.filter { it.value == SyncAction.DeleteLocal }.keys
        .mapNotNull { key -> bases[key]?.takeIf { it.state == BaseState.SYNCED }?.let { key to it.contentHash } }.toMap()
    val remoteTargets = actions.mapNotNull { (key, action) ->
        if (action is SyncAction.Download && bases[key] == null && key !in localHashes) key to action.remote.contentHash else null
    }.toMap()

    return (pair(localSources, localTargets, MoveKind.LOCAL) + pair(remoteSources, remoteTargets, MoveKind.REMOTE))
        .sortedBy { it.from }
}

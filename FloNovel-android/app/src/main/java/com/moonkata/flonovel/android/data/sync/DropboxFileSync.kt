package com.moonkata.flonovel.android.data.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.moonkata.flonovel.android.data.datastore.ReaderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** "N / total" for the progress row; [currentRelativePath] is what is being written right now. */
data class DropboxSyncProgress(val completed: Int, val total: Int, val currentRelativePath: String)

data class DropboxSyncResult(
    val downloaded: Int,
    val updated: Int,
    val deleted: Int,
    val failed: Int,
    /**
     * Deletions that were withheld because the remote claimed to hold nothing at all — see
     * [isSuspiciousWipe]. Non-zero means the sync deliberately did less than the remote asked for,
     * and the user is told so.
     */
    val withheldDeletions: Int = 0,
) {
    val changed: Int get() = downloaded + updated + deleted
}

/** One file the remote says this device should have; [relativePath] keeps the author's casing. */
data class RemoteBook(val relativePath: String, val sizeBytes: Long)

/**
 * Applies Dropbox's view of `/books` to the local SAF library — download only, in one direction
 * (docs 06-SYNC-STRATEGY B2). The phone never uploads and never deletes remotely, which is why
 * deleting a file on the phone brings it back on the next sync. That is intended, and the settings
 * sheet says so, because it reads like a bug otherwise.
 *
 * Two modes, because Dropbox's two listing calls answer different questions:
 *
 * - **No cursor yet** → `list_folder` gives the complete remote picture, so the local tree is
 *   reconciled against it: anything remote-only is fetched, anything local-only is removed.
 * - **Cursor** → `list_folder/continue` gives only what changed, including explicit `deleted`
 *   entries. Local-only files must NOT be removed here: "absent from this page" means "unchanged",
 *   not "gone". Conflating the two would wipe the library on the first incremental sync.
 *
 * The delete inference the old LAN client had to make ("not in the remote list, so it must have been
 * deleted") is gone — Dropbox states deletions outright (docs 06-SYNC-STRATEGY B3).
 */
class DropboxFileSync(
    private val context: Context,
    private val client: DropboxClient,
    private val settingsRepository: ReaderSettingsRepository,
    private val localScanner: LocalLibraryScanner = LocalLibraryScanner(context),
) {
    /**
     * Returns null when the sync could not start at all — not linked, the remote listing failed, or
     * [treeUri] is no longer a folder this app can read. A single file's failure is counted in
     * [DropboxSyncResult.failed] instead: with a folder rename or a bulk restructure, one SAF-side
     * error must not abandon the other hundred files.
     */
    suspend fun sync(treeUri: Uri, onProgress: (DropboxSyncProgress) -> Unit = {}): DropboxSyncResult? =
        withContext(Dispatchers.IO) {
            if (!client.isLinked()) return@withContext null
            val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return@withContext null

            val cursor = settingsRepository.settingsFlow.first().dropboxCursor
            val listing = fetchListing(cursor) ?: return@withContext null

            val result = if (listing.wasFullListing) {
                applyFullListing(root, treeUri, listing.entries, onProgress)
            } else {
                applyDelta(root, treeUri, listing.entries, onProgress)
            }
            val nextCursor = cursorAfterSync(cursor, listing.cursor, result)
            if (nextCursor != cursor) {
                settingsRepository.updateDropboxSyncState(
                    cursor = nextCursor,
                    lastSyncAtMillis = System.currentTimeMillis(),
                )
            }
            result
        }

    private data class Listing(val entries: List<DropboxEntry>, val cursor: String, val wasFullListing: Boolean)

    /**
     * Pages through the listing until `has_more` clears. The cursor is only committed once, after the
     * whole listing has been applied — persisting each page as it arrives would, if the app died
     * halfway, leave a cursor claiming changes had been applied that never reached disk.
     */
    private suspend fun fetchListing(startCursor: String): Listing? {
        var usedFullListing = startCursor.isBlank()
        var result = if (usedFullListing) client.listFolder() else client.listFolderContinue(startCursor)

        if (result is DropboxListResult.CursorReset) {
            usedFullListing = true
            result = client.listFolder()
        }

        val entries = mutableListOf<DropboxEntry>()
        var cursor = ""
        while (true) {
            when (val current = result) {
                is DropboxListResult.Success -> {
                    entries += current.entries
                    cursor = current.cursor
                    if (!current.hasMore) return Listing(entries, cursor, usedFullListing)
                    result = client.listFolderContinue(current.cursor)
                }
                // A reset midway invalidates the pages already collected, so start over from scratch.
                is DropboxListResult.CursorReset -> {
                    usedFullListing = true
                    entries.clear()
                    result = client.listFolder()
                }
                is DropboxListResult.Failure -> return null
            }
        }
    }

    // ── Full reconciliation ────────────────────────────────────────────────

    private suspend fun applyFullListing(
        root: DocumentFile,
        treeUri: Uri,
        entries: List<DropboxEntry>,
        onProgress: (DropboxSyncProgress) -> Unit,
    ): DropboxSyncResult {
        val remote = remoteBooksByKey(entries)
        val local = localScanner.scanRecursively(treeUri).associateBy { syncKeyOf(it.relativePath) }

        val toWrite = remote.filter { (key, book) ->
            val existing = local[key]
            existing == null || existing.sizeBytes != book.sizeBytes
        }
        val toDelete = local.filterKeys { it !in remote }

        // Sync is one-way and the phone never uploads, so a deletion here is unrecoverable: there is
        // no second copy to restore from. Withhold the whole batch when the remote looks empty.
        if (isSuspiciousWipe(remoteCount = remote.size, localCount = local.size)) {
            return applyChanges(root, toWrite, emptyList(), local, onProgress)
                .copy(withheldDeletions = toDelete.size)
        }

        return applyChanges(root, toWrite, toDelete.values.toList(), local, onProgress)
    }

    // ── Incremental delta ──────────────────────────────────────────────────

    private suspend fun applyDelta(
        root: DocumentFile,
        treeUri: Uri,
        entries: List<DropboxEntry>,
        onProgress: (DropboxSyncProgress) -> Unit,
    ): DropboxSyncResult {
        val local = localScanner.scanRecursively(treeUri).associateBy { syncKeyOf(it.relativePath) }

        val remote = remoteBooksByKey(entries)
        val changed = remote.filter { (key, book) ->
            val existing = local[key]
            existing == null || existing.sizeBytes != book.sizeBytes
        }
        val removed = deletionKeysToApply(entries, remote).mapNotNull { local[it] }

        return applyChanges(root, changed, removed, local, onProgress)
    }

    // ── Shared write/delete pass ───────────────────────────────────────────

    private suspend fun applyChanges(
        root: DocumentFile,
        toWrite: Map<String, RemoteBook>,
        toDelete: List<LocalLibraryFile>,
        local: Map<String, LocalLibraryFile>,
        onProgress: (DropboxSyncProgress) -> Unit,
    ): DropboxSyncResult {
        val total = toWrite.size + toDelete.size
        var completed = 0
        var downloaded = 0
        var updated = 0
        var deleted = 0
        var failed = 0

        for ((key, book) in toWrite) {
            onProgress(DropboxSyncProgress(completed, total, book.relativePath))
            val existing = local[key]
            val success = runCatching {
                if (existing != null) overwriteInPlace(existing.documentUri, book) else createAndWrite(root, book)
            }.getOrDefault(false)
            when {
                !success -> failed++
                existing != null -> updated++
                else -> downloaded++
            }
            completed++
        }

        for (file in toDelete) {
            onProgress(DropboxSyncProgress(completed, total, file.relativePath))
            val success = runCatching {
                DocumentFile.fromSingleUri(context, file.documentUri)?.delete() == true
            }.getOrDefault(false)
            if (success) {
                deleted++
                // A folder deleted wholesale on the PC arrives as N file deletions, leaving an empty
                // shell behind. Failing to prune is harmless, so it never affects the result.
                runCatching { pruneNowEmptyAncestors(root, file.relativePath) }
            } else {
                failed++
            }
            completed++
        }

        return DropboxSyncResult(downloaded, updated, deleted, failed)
    }

    /**
     * Keeps the existing document URI and replaces only the bytes. Deleting and recreating the file
     * would give it a new URI, and `BookEntity` keys the saved reading position off that URI — the
     * position would be orphaned and the book would reopen at the top.
     */
    private suspend fun overwriteInPlace(uri: Uri, book: RemoteBook): Boolean {
        // "wt" truncates first, so a download that dies halfway leaves a short file. That is
        // self-healing: its size no longer matches the remote, so the next sync fetches it again.
        val output = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: return false
        return output.use {
            client.downloadFile(remotePathOf(book.relativePath), it) is DropboxDownloadResult.Success
        }
    }

    private suspend fun createAndWrite(root: DocumentFile, book: RemoteBook): Boolean {
        val segments = book.relativePath.split("/")
        val parent = resolveOrCreateFolder(root, segments.dropLast(1)) ?: return false
        val name = segments.last()
        val mime = if (name.endsWith(".zip", ignoreCase = true)) "application/zip" else "text/plain"
        val file = runCatching { parent.createFile(mime, name) }.getOrNull() ?: return false
        val output = runCatching { context.contentResolver.openOutputStream(file.uri, "wt") }.getOrNull()
        if (output == null) {
            // Leaving a zero-byte file behind would make the next sync see a size mismatch and retry,
            // but the user would see a broken book in the library in the meantime.
            runCatching { file.delete() }
            return false
        }
        val success = output.use {
            client.downloadFile(remotePathOf(book.relativePath), it) is DropboxDownloadResult.Success
        }
        if (!success) runCatching { file.delete() }
        return success
    }

    private fun resolveOrCreateFolder(root: DocumentFile, segments: List<String>): DocumentFile? {
        var current = root
        for (segment in segments) {
            val existing = runCatching { current.findFile(segment) }.getOrNull()
            current = if (existing != null && existing.isDirectory) {
                existing
            } else {
                runCatching { current.createDirectory(segment) }.getOrNull() ?: return null
            }
        }
        return current
    }

    /**
     * Walks up from the folder that held the just-deleted file, removing each one that is now empty,
     * stopping at the first that still holds something (and never touching [root] itself).
     */
    private fun pruneNowEmptyAncestors(root: DocumentFile, deletedRelativePath: String) {
        val folders = deletedRelativePath.split("/").dropLast(1)
        if (folders.isEmpty()) return

        val chain = mutableListOf(root)
        var current = root
        for (segment in folders) {
            current = runCatching { current.findFile(segment) }.getOrNull()?.takeIf { it.isDirectory } ?: return
            chain += current
        }

        for (i in chain.lastIndex downTo 1) {
            val folder = chain[i]
            if (!runCatching { folder.listFiles().isEmpty() }.getOrDefault(false)) break
            if (!runCatching { folder.delete() }.getOrDefault(false)) break
        }
    }
}

/**
 * Whether a full reconciliation is about to delete everything on the strength of a remote listing
 * that holds nothing.
 *
 * The remote saying "no books" is indistinguishable, from here, between three things:
 *
 * - the user really did delete them all on the PC,
 * - they signed in to a different Dropbox account,
 * - the Desktop app has never run against this account, so `/books` exists but was never filled.
 *
 * Only the first is a real instruction. The other two are misconfiguration, and acting on them
 * destroys the library — permanently, because sync is one-way and the phone holds the only copy it
 * will ever get. So an empty remote plus a non-empty local means: download nothing, delete nothing,
 * and say so. If the user truly emptied the PC folder, deleting the books on the phone by hand is a
 * far smaller ask than getting them back.
 *
 * A missing `/books` is already safe without this — Dropbox answers 409 `path/not_found`, which the
 * client reports as a failure and the sync aborts. This covers the folder that exists and is empty.
 */
internal fun isSuspiciousWipe(remoteCount: Int, localCount: Int): Boolean =
    remoteCount == 0 && localCount > 0

/**
 * Whether a remote-supplied relative path is safe to turn into local folder and file names.
 *
 * Every segment of this string is handed to `createDirectory`/`createFile`. Dropbox normalises the
 * paths it returns, so `..` should never arrive — but "the server is well behaved" is not a control,
 * and the cost of checking is four lines. Rejected: empty or blank, an absolute path, an empty
 * segment (`a//b`), and `.` or `..` as a segment.
 *
 * Backslash is left alone on purpose: on Android it is an ordinary character in a file name, and
 * rejecting it would drop real books whose titles contain one.
 */
internal fun isSafeRelativePath(relativePath: String): Boolean {
    if (relativePath.isBlank() || relativePath.startsWith("/")) return false
    return relativePath.split("/").all { it.isNotBlank() && it != "." && it != ".." }
}

/**
 * Which cursor this device should keep after a sync pass.
 *
 * Advancing past a file that failed to land would retire that change **forever**: the next
 * incremental sync asks Dropbox "what changed since this cursor", and a change already covered by
 * the cursor is never mentioned again. So on any failure the previous cursor is kept and the next
 * run sees the same work. When the previous cursor was blank, keeping it means the next run is
 * another full listing, which reconciles by content and self-heals the same way.
 *
 * Pulled out of [DropboxFileSync.sync] during review (T-26/T-24 review): the rule lived inside a
 * suspend function that needs SAF and a network, so nothing tested it and breaking it was silent.
 */
internal fun cursorAfterSync(
    previousCursor: String,
    listingCursor: String,
    result: DropboxSyncResult,
): String = if (result.failed == 0) listingCursor else previousCursor

/**
 * Strips the `/books` prefix off a Dropbox path. Returns null for anything outside it — notably
 * `/.flonovel/secret.json`, which shares the app folder but is not a book.
 */
internal fun relativeBookPath(dropboxPath: String): String? {
    val prefix = "${DropboxConfig.REMOTE_BOOKS_ROOT}/"
    if (!dropboxPath.startsWith(prefix, ignoreCase = true)) return null
    return dropboxPath.substring(prefix.length).takeIf { isSafeRelativePath(it) }
}

internal fun remotePathOf(relativePath: String): String = "${DropboxConfig.REMOTE_BOOKS_ROOT}/$relativePath"

/**
 * The comparison key, deliberately the same [normalizeRelativePath] used for reading-position sync —
 * separators unified, NFC, lowercased. Dropbox already lowercases `path_lower`, but Korean file names
 * still need the NFC pass: a name typed on macOS arrives decomposed and would otherwise never match
 * the same name stored composed on the phone.
 */
internal fun syncKeyOf(relativePath: String): String = normalizeRelativePath(relativePath.split("/"))

/**
 * Which keys a delta page says to delete locally.
 *
 * A file deleted and re-added inside one window arrives as both a `deleted` entry and a `file`
 * entry, and the write pass runs before the delete pass — so without this filter the file would be
 * downloaded and then immediately thrown away. The check is against [remote] (everything the page
 * says exists) rather than against the download list, because a re-added copy whose size matches
 * the local one needs no download but is very much still there.
 */
internal fun deletionKeysToApply(entries: List<DropboxEntry>, remote: Map<String, RemoteBook>): List<String> =
    entries.filterIsInstance<DropboxEntry.Deleted>()
        .mapNotNull { relativeBookPath(it.pathLower)?.let(::syncKeyOf) }
        .filter { it !in remote }
        .distinct()

/**
 * Keeps the last entry per key. Within one listing Dropbox reports changes in order, so a file
 * deleted and re-added in the same window ends up as the add, which is the current truth.
 */
internal fun remoteBooksByKey(entries: List<DropboxEntry>): Map<String, RemoteBook> {
    val books = LinkedHashMap<String, RemoteBook>()
    for (entry in entries) {
        when (entry) {
            is DropboxEntry.File -> {
                // path_display preserves the author's casing; path_lower would rename every new file
                // to lowercase on the phone.
                val relative = relativeBookPath(entry.pathDisplay) ?: continue
                books[syncKeyOf(relative)] = RemoteBook(relative, entry.size)
            }
            is DropboxEntry.Deleted -> relativeBookPath(entry.pathLower)?.let { books.remove(syncKeyOf(it)) }
        }
    }
    return books
}

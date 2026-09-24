package com.moonkata.flonovel.android.data.sync

/** "N / total" for the progress row; [currentRelativePath] is what is being worked on right now. */
data class DropboxSyncProgress(val completed: Int, val total: Int, val currentRelativePath: String)

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

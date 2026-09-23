package com.moonkata.flonovel.android.data.sync

import java.io.InputStream
import java.io.OutputStream

/** One book file in the library; [relativePath] is `/`-separated and rooted at the library root. */
data class LibraryFile(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

/**
 * The library folder as two-way sync sees it.
 *
 * Two implementations: the SAF tree the user picked ([SafLibraryFiles]) and an in-memory one for
 * JVM tests. Sync decisions need real file operations to be tested end to end, and SAF cannot run
 * outside a device.
 */
interface LibraryFiles {
    /** Every `.txt` file, skipping anything under a dot-prefixed name. */
    fun list(): List<LibraryFile>

    fun stat(relativePath: String): LibraryFile?

    fun openRead(relativePath: String): InputStream?

    /**
     * Writes [relativePath] through [block], creating missing folders. An existing file is written
     * in place, keeping its document URI: the saved reading position is keyed by that URI, and a
     * delete-and-recreate would orphan it. Returns what [block] returned, or false when the file
     * could not be opened.
     */
    suspend fun write(relativePath: String, block: suspend (OutputStream) -> Boolean): Boolean

    /** Deletes the file, then any folders above it that are now empty (never the root). */
    fun delete(relativePath: String): Boolean
}

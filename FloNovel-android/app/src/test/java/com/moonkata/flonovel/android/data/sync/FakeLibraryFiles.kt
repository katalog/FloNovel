package com.moonkata.flonovel.android.data.sync

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * An in-memory library folder. Each file carries a [documentId] standing in for its SAF URI, so
 * tests can check that a write kept it (the reading position is keyed by that URI).
 */
class FakeLibraryFiles : LibraryFiles {

    private class Entry(var bytes: ByteArray, var mtime: Long, val documentId: Int)

    private val entries = LinkedHashMap<String, Entry>()
    private var clock = 1_000L
    private var nextId = 1

    /** Writes to these paths stop after [interruptAfterBytes] bytes, like a dropped connection. */
    val interruptWritesOf = mutableSetOf<String>()
    var interruptAfterBytes = 3

    /** openRead() fails for these paths, like a file another app holds. */
    val unreadable = mutableSetOf<String>()

    fun put(rel: String, content: String) {
        val existing = entries[rel]
        if (existing != null) {
            existing.bytes = content.toByteArray()
            existing.mtime = ++clock
        } else {
            entries[rel] = Entry(content.toByteArray(), ++clock, nextId++)
        }
    }

    fun content(rel: String): String? = entries[rel]?.bytes?.toString(Charsets.UTF_8)

    fun documentId(rel: String): Int? = entries[rel]?.documentId

    fun paths(): Set<String> = entries.keys.toSet()

    override fun list(includeHidden: Boolean): List<LibraryFile> = entries
        .filterKeys { rel -> rel.endsWith(".txt", ignoreCase = true) && (includeHidden || rel.split('/').none { it.startsWith(".") }) }
        .map { (rel, e) -> LibraryFile(rel, e.bytes.size.toLong(), e.mtime) }

    override fun stat(relativePath: String): LibraryFile? =
        entries[relativePath]?.let { LibraryFile(relativePath, it.bytes.size.toLong(), it.mtime) }

    override fun openRead(relativePath: String): InputStream? {
        if (relativePath in unreadable) return null
        return entries[relativePath]?.bytes?.inputStream()
    }

    override suspend fun write(relativePath: String, block: suspend (OutputStream) -> Boolean): Boolean {
        val entry = entries.getOrPut(relativePath) { Entry(ByteArray(0), clock, nextId++) }
        val buffer = ByteArrayOutputStream()
        val out = object : OutputStream() {
            override fun write(b: Int) {
                if (relativePath in interruptWritesOf && buffer.size() >= interruptAfterBytes) throw IOException("connection dropped")
                buffer.write(b)
            }
        }
        val ok = try {
            block(out)
        } catch (_: IOException) {
            false
        }
        // Like SAF's "wt": whatever arrived is on disk, even when the transfer failed.
        entry.bytes = buffer.toByteArray()
        entry.mtime = ++clock
        return ok
    }

    override fun delete(relativePath: String): Boolean = entries.remove(relativePath) != null

    override fun rename(relativePath: String, newName: String): Boolean {
        val folder = relativePath.substringBeforeLast('/', "")
        val target = if (folder.isEmpty()) newName else "$folder/$newName"
        if (target in entries) return false
        val entry = entries.remove(relativePath) ?: return false
        entries[target] = entry
        return true
    }
}

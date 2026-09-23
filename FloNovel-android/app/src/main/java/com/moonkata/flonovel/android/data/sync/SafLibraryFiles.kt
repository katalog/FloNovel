package com.moonkata.flonovel.android.data.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.InputStream
import java.io.OutputStream

/**
 * The library folder the user picked, through SAF.
 *
 * Each folder is read with one `DocumentsContract` query for all its children (name, type, size,
 * modification time). `DocumentFile.listFiles()` followed by per-file `length()`/`lastModified()`
 * issues a query per property per file, which is what made the old full-tree scan slow.
 */
class SafLibraryFiles(context: Context, private val treeUri: Uri) : LibraryFiles {

    private val resolver = context.contentResolver

    private data class Child(val documentId: String, val name: String, val isDirectory: Boolean, val size: Long, val mtime: Long)

    private val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    override fun list(includeHidden: Boolean): List<LibraryFile> {
        val out = mutableListOf<LibraryFile>()
        fun walk(folderId: String, prefix: String) {
            for (child in children(folderId)) {
                if (!includeHidden && child.name.startsWith(".")) continue
                val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (child.isDirectory) {
                    walk(child.documentId, rel)
                } else if (child.name.endsWith(".txt", ignoreCase = true)) {
                    out += LibraryFile(rel, child.size, child.mtime)
                }
            }
        }
        walk(rootId, "")
        return out
    }

    override fun stat(relativePath: String): LibraryFile? {
        val child = find(relativePath) ?: return null
        return LibraryFile(relativePath, child.size, child.mtime)
    }

    override fun openRead(relativePath: String): InputStream? {
        val child = find(relativePath) ?: return null
        return runCatching { resolver.openInputStream(documentUri(child.documentId)) }.getOrNull()
    }

    override suspend fun write(relativePath: String, block: suspend (OutputStream) -> Boolean): Boolean {
        val segments = relativePath.split('/')
        val folderId = ensureFolders(segments.dropLast(1)) ?: return false
        val name = segments.last()
        val existing = children(folderId).firstOrNull { it.name == name && !it.isDirectory }
        val fileUri = if (existing != null) {
            documentUri(existing.documentId)
        } else {
            val created = runCatching {
                DocumentsContract.createDocument(resolver, documentUri(folderId), "text/plain", name)
            }.getOrNull() ?: return false
            // Some providers adjust the name they were given (an added extension, a " (1)" suffix).
            // A file under another name would be a different book to sync; do not leave it behind.
            val actualName = children(folderId).firstOrNull { documentUri(it.documentId) == created }?.name
            if (actualName != name) {
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
                return false
            }
            created
        }
        // "wt" truncates in place, keeping the document URI and with it the reading position.
        val output = runCatching { resolver.openOutputStream(fileUri, "wt") }.getOrNull() ?: return false
        return output.use { block(it) }
    }

    override fun delete(relativePath: String): Boolean {
        val child = find(relativePath) ?: return false
        val deleted = runCatching { DocumentsContract.deleteDocument(resolver, documentUri(child.documentId)) }
            .getOrDefault(false)
        if (deleted) pruneEmptyParents(relativePath)
        return deleted
    }

    override fun rename(relativePath: String, newName: String): Boolean {
        val child = find(relativePath) ?: return false
        val renamed = runCatching {
            DocumentsContract.renameDocument(resolver, documentUri(child.documentId), newName)
        }.getOrNull() ?: return false
        val folder = relativePath.substringBeforeLast('/', "")
        return find(if (folder.isEmpty()) newName else "$folder/$newName")?.let { documentUri(it.documentId) } == renamed
    }

    override fun move(from: String, to: String): Boolean {
        val child = find(from) ?: return false
        if (find(to) != null) return false
        val fromFolder = from.substringBeforeLast('/', "")
        val toFolder = to.substringBeforeLast('/', "")
        val toName = to.substringAfterLast('/')
        var uri = documentUri(child.documentId)
        if (fromFolder != toFolder) {
            val sourceParent = if (fromFolder.isEmpty()) rootId else find(fromFolder)?.documentId ?: return false
            val targetParent = ensureFolders(if (toFolder.isEmpty()) emptyList() else toFolder.split('/')) ?: return false
            uri = runCatching {
                DocumentsContract.moveDocument(resolver, uri, documentUri(sourceParent), documentUri(targetParent))
            }.getOrNull() ?: return false
        }
        if (child.name != toName) {
            uri = runCatching { DocumentsContract.renameDocument(resolver, uri, toName) }.getOrNull() ?: return false
        }
        pruneEmptyParents(from)
        return find(to)?.let { documentUri(it.documentId) } == uri
    }

    /** The document URI a book at [relativePath] is stored under in the reading-position table. */
    fun uriOf(relativePath: String): Uri? = find(relativePath)?.let { documentUri(it.documentId) }

    /** Removes now-empty folders above a deleted file, deepest first, never the root. */
    private fun pruneEmptyParents(deletedRelativePath: String) {
        val folders = deletedRelativePath.split('/').dropLast(1)
        for (depth in folders.size downTo 1) {
            val folder = find(folders.take(depth).joinToString("/")) ?: return
            if (children(folder.documentId).isNotEmpty()) return
            if (!runCatching { DocumentsContract.deleteDocument(resolver, documentUri(folder.documentId)) }.getOrDefault(false)) return
        }
    }

    private fun ensureFolders(segments: List<String>): String? {
        var folderId = rootId
        for (segment in segments) {
            val existing = children(folderId).firstOrNull { it.name == segment && it.isDirectory }
            folderId = existing?.documentId ?: runCatching {
                DocumentsContract.createDocument(resolver, documentUri(folderId), Document.MIME_TYPE_DIR, segment)
            }.getOrNull()?.let { DocumentsContract.getDocumentId(it) } ?: return null
        }
        return folderId
    }

    private fun find(relativePath: String): Child? {
        var folderId = rootId
        val segments = relativePath.split('/')
        for ((index, segment) in segments.withIndex()) {
            val child = children(folderId).firstOrNull { it.name == segment } ?: return null
            if (index == segments.lastIndex) return child
            if (!child.isDirectory) return null
            folderId = child.documentId
        }
        return null
    }

    private fun children(folderId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1) ?: continue
                        add(
                            Child(
                                documentId = cursor.getString(0),
                                name = name,
                                isDirectory = cursor.getString(2) == Document.MIME_TYPE_DIR,
                                size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                                mtime = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    private fun documentUri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
}

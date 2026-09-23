package com.moonkata.flonovel.android.data.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the library folder the app already has a persisted grant for, inside a throwaway
 * subfolder it creates and removes. Skipped when no folder has been granted on the device.
 */
@RunWith(AndroidJUnit4::class)
class SafLibraryFilesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun grantedTree(): Uri? =
        context.contentResolver.persistedUriPermissions.firstOrNull { it.isWritePermission }?.uri

    @Test
    fun writeInPlace_list_stat_delete_andPrune() = runBlocking {
        val tree = grantedTree()
        assumeTrue("No library folder granted on this device", tree != null)
        val files = SafLibraryFiles(context, tree!!)
        // Not dot-prefixed, so list() includes it; the test deletes it again at the end.
        val folder = "flonovel-test-${System.nanoTime()}"
        val rel = "$folder/Sub/Book.txt"

        assertTrue(files.write(rel) { it.write("one".toByteArray()); true })
        val firstId = findDocumentId(tree, rel)
        assertTrue(files.write(rel) { it.write("two!".toByteArray()); true })

        assertEquals("the second write kept the document", firstId, findDocumentId(tree, rel))
        assertEquals("two!", files.openRead(rel)!!.bufferedReader().readText())
        assertEquals(4L, files.stat(rel)!!.sizeBytes)
        assertTrue(files.list().any { it.relativePath == rel })

        assertTrue(files.delete(rel))
        assertNull(files.stat(rel))
        assertFalse("emptied folders are pruned", files.list().any { it.relativePath.startsWith(folder) })
        assertNull(files.stat(folder))
    }

    private fun findDocumentId(tree: Uri, rel: String): String? {
        var parent = DocumentsContract.getTreeDocumentId(tree)
        for (segment in rel.split('/')) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val id = context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                var found: String? = null
                while (c.moveToNext()) if (c.getString(1) == segment) found = c.getString(0)
                found
            } ?: return null
            parent = id
        }
        return parent
    }
}

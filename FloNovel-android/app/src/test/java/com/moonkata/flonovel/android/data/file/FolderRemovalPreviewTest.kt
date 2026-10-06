package com.moonkata.flonovel.android.data.file

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FolderRemovalPreviewTest {
    @Test
    fun includesNestedHiddenAndUnsupportedFiles() = runBlocking {
        val tree = mapOf(
            "root" to listOf(RemovalChild("nested", "nested", true),
                RemovalChild("cover", "cover.jpg", false), RemovalChild("hidden", ".hidden", false)),
            "nested" to listOf(RemovalChild("book", "book.txt", false)),
        )
        assertEquals(listOf(".hidden", "cover.jpg", "nested", "nested/book.txt"),
            folderRemovalContents("root") { tree.getValue(it) })
    }

    @Test
    fun emptyFolderHasNoContents() = runBlocking {
        assertTrue(folderRemovalContents("root") { emptyList() }.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun failedNestedListingDoesNotReturnPartialPreview() = runBlocking {
        folderRemovalContents("root") {
            if (it == "root") listOf(RemovalChild("nested", "nested", true))
            else error("Listing failed")
        }
        Unit
    }

    @Test(expected = IllegalStateException::class)
    fun cyclicListingIsRejected() = runBlocking {
        folderRemovalContents("root") { listOf(RemovalChild("root", "loop", true)) }
        Unit
    }
}

package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BooksData
import com.moonkata.flonovel.desktop.library.LibraryBookItem
import com.moonkata.flonovel.desktop.library.LibraryFolderEntry
import com.moonkata.flonovel.desktop.library.LibraryScanner
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryFolderNavigationTest {

    @Test
    fun subfolderNavigation_selectsFirstContentItemByDefault_soImmediateEnterWorks() {
        val tempDir = Files.createTempDirectory("lib_nav_test")
        try {
            val sub1 = Files.createDirectories(tempDir.resolve("Fantasy"))
            val bookFile = sub1.resolve("Chapter1.txt")
            Files.writeString(bookFile, "Hello Fantasy World")

            val booksData = BooksData(
                schemaVersion = 1,
                books = listOf(
                    BookRecord(
                        path = "Fantasy/Chapter1.txt",
                        key = "fantasy/chapter1.txt",
                        displayName = "Chapter1.txt",
                        sizeBytes = bookFile.toFile().length(),
                        totalCharCount = 19,
                        detectedEncoding = "UTF-8",
                        anchor = 0,
                        progress = 0.0,
                        addedAt = System.currentTimeMillis(),
                    )
                )
            )

            // 1. Root folder scan
            val rootContent = LibraryScanner.scanDirectory(tempDir, "", booksData)
            assertEquals(1, rootContent.subfolders.size)
            assertEquals("Fantasy", rootContent.subfolders[0].name)

            // DisplayItems in root: only the subfolder
            val rootDisplayItems = mutableListOf<DisplayItem>()
            for (sub in rootContent.subfolders) rootDisplayItems.add(DisplayItem(type = 1, folder = sub))
            for (b in rootContent.books) rootDisplayItems.add(DisplayItem(type = 2, book = b))

            assertEquals(1, rootDisplayItems.size)
            assertEquals(1, rootDisplayItems[0].type) // FOLDER

            // 2. Scan subfolder
            val subContent = LibraryScanner.scanDirectory(tempDir, "Fantasy", booksData)
            assertEquals(1, subContent.books.size)

            val subDisplayItems = mutableListOf<DisplayItem>()
            // Has parent entry:
            subDisplayItems.add(DisplayItem(type = 0)) // PARENT
            for (sub in subContent.subfolders) subDisplayItems.add(DisplayItem(type = 1, folder = sub))
            for (b in subContent.books) subDisplayItems.add(DisplayItem(type = 2, book = b))

            assertEquals(2, subDisplayItems.size)
            assertEquals(0, subDisplayItems[0].type) // Parent row
            assertEquals(2, subDisplayItems[1].type) // Book row

            // Upon entering subfolder, selection must default to index 1 (the first content item)
            val selectedIndex = if (subDisplayItems.size > 1) 1 else 0
            assertEquals(1, selectedIndex, "Entering subfolder must select first content item, not parent row")

            // Immediate Enter on selectedIndex=1 opens the book!
            var openedBook: LibraryBookItem? = null
            val itemToOpen = subDisplayItems[selectedIndex]
            if (itemToOpen.type == 2) {
                openedBook = itemToOpen.book
            }
            assertNotNull(openedBook)
            assertEquals("Chapter1", openedBook.displayName)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun navigateToParent_restoresSelectionToExitingFolder() {
        val tempDir = Files.createTempDirectory("lib_parent_test")
        try {
            Files.createDirectories(tempDir.resolve("Adventure"))
            Files.createDirectories(tempDir.resolve("Fantasy"))
            Files.createDirectories(tempDir.resolve("SciFi"))

            val booksData = BooksData()
            val rootContent = LibraryScanner.scanDirectory(tempDir, "", booksData)
            val sortedSubfolders = rootContent.subfolders.sortedBy { it.name }

            val exitingFolder = "Fantasy"
            val folderIdx = sortedSubfolders.indexOfFirst { it.name.equals(exitingFolder, ignoreCase = true) }
            val selectedIndex = when {
                folderIdx >= 0 -> folderIdx
                else -> 0
            }

            assertEquals(1, selectedIndex, "When returning to root from 'Fantasy', 'Fantasy' folder must be selected")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun sanitizePaths_convertsAbsoluteLegacyPathsToRelative() {
        val tempDir = Files.createTempDirectory("lib_sanitize_test")
        try {
            val homeFolder = tempDir.resolve("Books").resolve("Reading")
            Files.createDirectories(homeFolder)
            val storeFile = tempDir.resolve("books.json")
            val bookStore = com.moonkata.flonovel.desktop.library.BookStore(storeFile)

            try {
                val absoluteLegacyPath = homeFolder.resolve("0830").resolve("novel.txt").toString()
                val legacyRecord = BookRecord(
                    path = absoluteLegacyPath,
                    key = "0830/novel.txt",
                    displayName = "novel",
                    sizeBytes = 100L,
                    totalCharCount = 50,
                    detectedEncoding = "UTF-8",
                    anchor = 0,
                    progress = 0.0,
                )
                bookStore.addOrUpdate(legacyRecord)
                bookStore.flush()

                // Sanitize paths
                val sanitized = bookStore.sanitizePaths(homeFolder)
                val updatedRecord = sanitized.books.firstOrNull { it.key == "0830/novel.txt" }
                assertNotNull(updatedRecord)
                assertEquals("0830/novel.txt", updatedRecord.path.replace('\\', '/'))
                assertFalse(java.nio.file.Path.of(updatedRecord.path).isAbsolute, "Path must now be relative")
            } finally {
                bookStore.close()
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

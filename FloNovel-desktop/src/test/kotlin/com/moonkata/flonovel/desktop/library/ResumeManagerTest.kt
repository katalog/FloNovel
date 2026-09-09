package com.moonkata.flonovel.desktop.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResumeManagerTest {

    @Test
    fun resume_normalFile_loadsBookAndClampedAnchor() {
        val tempHome = Files.createTempDirectory("resume_home")
        try {
            val bookPath = tempHome.resolve("novel.txt")
            val content = "1234567890".repeat(50) // 500 characters
            Files.writeString(bookPath, content)

            val book = BookRecord(
                path = "novel.txt",
                key = "novel.txt",
                displayName = "novel.txt",
                sizeBytes = content.length.toLong(),
                totalCharCount = content.length,
                detectedEncoding = "UTF-8",
                anchor = 120,
                progress = 120.0 / 500,
                addedAt = 1000L,
            )

            val settings = Settings(lastOpenedBookKey = "novel.txt")
            val booksData = BooksData(books = listOf(book))

            val target = ResumeManager.findResumeTarget(tempHome, settings, booksData)

            assertNotNull(target)
            assertEquals("novel.txt", target.book.key)
            assertEquals(120, target.clampedAnchor)
            assertEquals(500, target.loadedText.text.length)
        } finally {
            tempHome.toFile().deleteRecursively()
        }
    }

    // --- Critical Regression: missing file must NOT crash, returns null gracefully ---

    @Test
    fun resume_missingFile_returnsNullWithoutCrash() {
        val tempHome = Files.createTempDirectory("resume_missing_home")
        try {
            // File does NOT exist on disk!
            val book = BookRecord(
                path = "deleted_novel.txt",
                key = "deleted_novel.txt",
                displayName = "deleted_novel.txt",
                sizeBytes = 5000L,
                totalCharCount = 2000,
                detectedEncoding = "UTF-8",
                anchor = 500,
                progress = 0.25,
                addedAt = 1000L,
            )

            val settings = Settings(lastOpenedBookKey = "deleted_novel.txt")
            val booksData = BooksData(books = listOf(book))

            // Must return null instead of throwing NoSuchFileException / crash
            val target = ResumeManager.findResumeTarget(tempHome, settings, booksData)
            assertNull(target, "Missing file must gracefully return null without crash")
        } finally {
            tempHome.toFile().deleteRecursively()
        }
    }

    // --- Rule 4: anchor clamped to text.length if file was truncated ---

    @Test
    fun resume_truncatedFile_clampsAnchorToTextLength() {
        val tempHome = Files.createTempDirectory("resume_truncated_home")
        try {
            val bookPath = tempHome.resolve("truncated.txt")
            val shortenedContent = "짧아진 내용" // 6 characters
            Files.writeString(bookPath, shortenedContent)

            // Book was recorded with anchor = 500 before file was truncated
            val book = BookRecord(
                path = "truncated.txt",
                key = "truncated.txt",
                displayName = "truncated.txt",
                sizeBytes = 1000L,
                totalCharCount = 500,
                detectedEncoding = "UTF-8",
                anchor = 500,
                progress = 1.0,
                addedAt = 1000L,
            )

            val settings = Settings(lastOpenedBookKey = "truncated.txt")
            val booksData = BooksData(books = listOf(book))

            val target = ResumeManager.findResumeTarget(tempHome, settings, booksData)

            assertNotNull(target)
            // Anchor must be clamped to 6!
            assertEquals(6, target.clampedAnchor)
            assertEquals(shortenedContent, target.loadedText.text)
        } finally {
            tempHome.toFile().deleteRecursively()
        }
    }

    @Test
    fun resume_zeroLengthFile_clampsAnchorToZero() {
        val tempHome = Files.createTempDirectory("resume_zero_home")
        try {
            val bookPath = tempHome.resolve("empty.txt")
            Files.writeString(bookPath, "") // 0 characters

            val book = BookRecord(
                path = "empty.txt",
                key = "empty.txt",
                displayName = "empty.txt",
                sizeBytes = 0L,
                totalCharCount = 0,
                detectedEncoding = "UTF-8",
                anchor = 50,
                progress = 0.0,
                addedAt = 1000L,
            )

            val settings = Settings(lastOpenedBookKey = "empty.txt")
            val booksData = BooksData(books = listOf(book))

            val target = ResumeManager.findResumeTarget(tempHome, settings, booksData)

            assertNotNull(target)
            assertEquals(0, target.clampedAnchor)
        } finally {
            tempHome.toFile().deleteRecursively()
        }
    }

    @Test
    fun resume_fallsBackToLatestOpenedBook_whenLastOpenedKeyIsNull() {
        val tempHome = Files.createTempDirectory("resume_fallback_home")
        try {
            val book1Path = tempHome.resolve("book1.txt")
            val book2Path = tempHome.resolve("book2.txt")
            Files.writeString(book1Path, "첫번째 책 본문")
            Files.writeString(book2Path, "두번째 책 본문")

            val book1 = BookRecord(
                path = "book1.txt",
                key = "book1.txt",
                displayName = "book1.txt",
                sizeBytes = 20L,
                totalCharCount = 8,
                detectedEncoding = "UTF-8",
                anchor = 2,
                progress = 0.25,
                addedAt = 1000L,
                lastOpenedAt = 1500L,
            )
            val book2 = BookRecord(
                path = "book2.txt",
                key = "book2.txt",
                displayName = "book2.txt",
                sizeBytes = 20L,
                totalCharCount = 8,
                detectedEncoding = "UTF-8",
                anchor = 5,
                progress = 0.6,
                addedAt = 1000L,
                lastOpenedAt = 2500L, // More recently opened
            )

            val settings = Settings(lastOpenedBookKey = null)
            val booksData = BooksData(books = listOf(book1, book2))

            val target = ResumeManager.findResumeTarget(tempHome, settings, booksData)

            assertNotNull(target)
            assertEquals("book2.txt", target.book.key, "Should fall back to the book with latest lastOpenedAt")
            assertEquals(5, target.clampedAnchor)
        } finally {
            tempHome.toFile().deleteRecursively()
        }
    }
}

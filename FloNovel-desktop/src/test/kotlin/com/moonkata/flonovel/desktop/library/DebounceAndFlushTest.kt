package com.moonkata.flonovel.desktop.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DebounceAndFlushTest {

    @Test
    fun debounce_defersDiskWrite_andFlushForcesImmediateWrite() {
        val tempDir = Files.createTempDirectory("debounce_test")
        try {
            val booksFile = tempDir.resolve("books.json")
            val store = BookStore(booksFile, debounceMs = 200L)

            val book = BookRecord(
                path = "novel.txt",
                key = "novel.txt",
                displayName = "novel.txt",
                sizeBytes = 1000L,
                totalCharCount = 500,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                addedAt = 1000L,
            )
            store.save(BooksData(books = listOf(book)))

            // Initial on disk: anchor = 0
            assertEquals(0, BooksData.fromJsonString(Files.readString(booksFile)).books[0].anchor)

            // Update position with debounce
            store.updateReadingPosition("novel.txt", anchor = 150, totalCharCount = 500, debounce = true)

            // In-memory cache is immediately 150
            assertEquals(150, store.findByKey("novel.txt")?.anchor)

            // Immediately on disk: still 0 because of debounce!
            assertEquals(0, BooksData.fromJsonString(Files.readString(booksFile)).books[0].anchor)

            // Flush commits immediately
            store.flush()

            // Now on disk: 150!
            assertEquals(150, BooksData.fromJsonString(Files.readString(booksFile)).books[0].anchor)

            store.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun close_automaticallyFlushesPendingDebouncedWrites() {
        val tempDir = Files.createTempDirectory("close_flush_test")
        try {
            val booksFile = tempDir.resolve("books.json")
            val store = BookStore(booksFile, debounceMs = 500L)

            val book = BookRecord(
                path = "novel.txt",
                key = "novel.txt",
                displayName = "novel.txt",
                sizeBytes = 1000L,
                totalCharCount = 500,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                addedAt = 1000L,
            )
            store.save(BooksData(books = listOf(book)))

            // Update position with 500ms debounce
            store.updateReadingPosition("novel.txt", anchor = 300, totalCharCount = 500, debounce = true)

            // Closing store should flush immediately
            store.close()

            // Disk has the flushed position
            val onDisk = BooksData.fromJsonString(Files.readString(booksFile))
            assertEquals(300, onDisk.books[0].anchor)
            assertEquals(0.60, onDisk.books[0].progress, 0.001)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun multipleRapidUpdates_coalesceToLastPosition() {
        val tempDir = Files.createTempDirectory("rapid_updates_test")
        try {
            val booksFile = tempDir.resolve("books.json")
            val store = BookStore(booksFile, debounceMs = 80L)

            val book = BookRecord(
                path = "novel.txt",
                key = "novel.txt",
                displayName = "novel.txt",
                sizeBytes = 1000L,
                totalCharCount = 1000,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                addedAt = 1000L,
            )
            store.save(BooksData(books = listOf(book)))

            // Rapid position updates (like fast page turns)
            for (pos in 10..100 step 10) {
                store.updateReadingPosition("novel.txt", anchor = pos, totalCharCount = 1000, debounce = true)
            }

            // Wait for debounce timer to fire
            Thread.sleep(150L)

            val onDisk = BooksData.fromJsonString(Files.readString(booksFile))
            assertEquals(100, onDisk.books[0].anchor)

            store.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

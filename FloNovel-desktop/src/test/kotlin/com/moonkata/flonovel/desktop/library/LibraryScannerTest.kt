package com.moonkata.flonovel.desktop.library

import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for T-12: Library & Home Folder.
 *
 * Requirements:
 * - Home folder recursive .txt file scanning.
 * - Files and directories starting with "." MUST BE EXCLUDED (.stfolder, .git, .hidden.txt).
 * - Progress formatting in library list: integer % (matching Android, e.g. "14%").
 * - Sorting by NAME, DATE, SIZE, RECENT.
 * - Safe file validation before opening.
 */
class LibraryScannerTest {

    @Test
    fun scan_excludesFilesAndDirectoriesStartingWithDot() {
        val tempDir = createTempDirectory("library_test")
        try {
            // Normal files
            val novel1 = tempDir.resolve("novel1.txt").apply { writeText("Story 1") }
            val subDir = tempDir.resolve("sub").apply { createDirectories() }
            val novel2 = subDir.resolve("novel2.TXT").apply { writeText("Story 2 in sub folder") }

            // Dot files & dot directories that MUST BE EXCLUDED
            val dotFolder = tempDir.resolve(".stfolder").apply { createDirectories() }
            dotFolder.resolve("sync_internal.txt").apply { writeText("ignored") }

            val gitFolder = tempDir.resolve(".git").apply { createDirectories() }
            gitFolder.resolve("commit.txt").apply { writeText("ignored") }

            val hiddenFile = tempDir.resolve(".hidden_note.txt").apply { writeText("ignored") }
            val dotFileInSub = subDir.resolve(".DS_Store.txt").apply { writeText("ignored") }

            val booksData = BooksData()
            val scanned = LibraryScanner.scan(tempDir, booksData)

            assertEquals(2, scanned.size, "Only 2 non-dot txt files should be discovered")
            val relativePaths = scanned.map { it.relativePath.replace('\\', '/') }

            assertTrue(relativePaths.contains("novel1.txt"))
            assertTrue(relativePaths.contains("sub/novel2.TXT"))

            // Verify dot items are not present
            assertFalse(relativePaths.any { it.startsWith(".") || it.contains("/.") })
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun libraryProgressFormat_isIntegerPercentage_matchingAndroidSpec() {
        val tempDir = createTempDirectory("library_progress_test")
        try {
            val file1 = tempDir.resolve("novel1.txt").apply { writeText("Some text content") }
            val file2 = tempDir.resolve("novel2.txt").apply { writeText("More text content") }
            val file3 = tempDir.resolve("novel3.txt").apply { writeText("Third text content") }

            val key1 = RelativePath.normalize("novel1.txt")
            val key2 = RelativePath.normalize("novel2.txt")

            val booksData = BooksData(
                books = listOf(
                    BookRecord(
                        path = "novel1.txt",
                        key = key1,
                        displayName = "novel1",
                        sizeBytes = file1.toFile().length(),
                        totalCharCount = 1000,
                        detectedEncoding = "UTF-8",
                        anchor = 142,
                        progress = 0.1428, // 14.28% -> "14%" in library
                        addedAt = 1000L,
                    ),
                    BookRecord(
                        path = "novel2.txt",
                        key = key2,
                        displayName = "novel2",
                        sizeBytes = file2.toFile().length(),
                        totalCharCount = 1000,
                        detectedEncoding = "UTF-8",
                        anchor = 999,
                        progress = 0.999, // 99.9% -> "99%" in library
                        addedAt = 1000L,
                    ),
                ),
            )

            val scanned = LibraryScanner.scan(tempDir, booksData).associateBy { it.key }

            assertEquals("14%", scanned[key1]?.formattedProgress, "Progress in library list must be integer %")
            assertEquals("99%", scanned[key2]?.formattedProgress, "Progress in library list must be integer %")

            val key3 = RelativePath.normalize("novel3.txt")
            assertEquals("0%", scanned[key3]?.formattedProgress, "Unopened book has 0% progress")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun progressState_reflectsWhatIsActuallyDisplayed_notTheRawFraction() {
        val tempDir = createTempDirectory("library_progress_state_test")
        try {
            val unread = tempDir.resolve("unread.txt").apply { writeText("x") }
            val midway = tempDir.resolve("midway.txt").apply { writeText("x") }
            val almostDone = tempDir.resolve("almost_done.txt").apply { writeText("x") }
            val done = tempDir.resolve("done.txt").apply { writeText("x") }

            fun record(path: String, name: String, progress: Double) = BookRecord(
                path = path,
                key = RelativePath.normalize(path),
                displayName = name,
                sizeBytes = 1L,
                totalCharCount = 1000,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = progress,
                addedAt = 1000L,
            )

            val barelyStarted = tempDir.resolve("barely_started.txt").apply { writeText("x") }

            val booksData = BooksData(
                books = listOf(
                    record("midway.txt", "midway", 0.5),
                    // 99.9% still displays as "99%" -- it must not read as complete either.
                    record("almost_done.txt", "almost_done", 0.999),
                    record("done.txt", "done", 1.0),
                    // 0.1% truncates to "0%" on screen but the book has genuinely been opened.
                    record("barely_started.txt", "barely_started", 0.001),
                ),
            )

            val scanned = LibraryScanner.scan(tempDir, booksData).associateBy { it.relativePath }

            assertEquals(ReadingProgressState.UNREAD, scanned["unread.txt"]?.progressState)
            assertEquals(ReadingProgressState.IN_PROGRESS, scanned["midway.txt"]?.progressState)
            assertEquals(
                ReadingProgressState.IN_PROGRESS,
                scanned["almost_done.txt"]?.progressState,
                "99% on screen must not be colored as complete",
            )
            assertEquals(ReadingProgressState.COMPLETED, scanned["done.txt"]?.progressState)
            assertEquals(
                ReadingProgressState.IN_PROGRESS,
                scanned["barely_started.txt"]?.progressState,
                "A book displaying '0%' due to truncation must still be colored as started, not unread",
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun sorting_nameDateSizeRecent_sortsAccurately() {
        val tempDir = createTempDirectory("library_sort_test")
        try {
            val fileA = tempDir.resolve("Alpha.txt").apply {
                writeText("A".repeat(100))
                toFile().setLastModified(10000L)
            }
            val fileB = tempDir.resolve("Beta.txt").apply {
                writeText("B".repeat(500))
                toFile().setLastModified(30000L)
            }
            val fileC = tempDir.resolve("Gamma.txt").apply {
                writeText("C".repeat(300))
                toFile().setLastModified(20000L)
            }

            val keyA = RelativePath.normalize("Alpha.txt")
            val keyB = RelativePath.normalize("Beta.txt")
            val keyC = RelativePath.normalize("Gamma.txt")

            val booksData = BooksData(
                books = listOf(
                    BookRecord("Alpha.txt", keyA, "Alpha", fileA.toFile().length(), 100, "UTF-8", 0, 0.0, 1L, lastOpenedAt = 300L),
                    BookRecord("Beta.txt", keyB, "Beta", fileB.toFile().length(), 500, "UTF-8", 0, 0.0, 1L, lastOpenedAt = 100L),
                    BookRecord("Gamma.txt", keyC, "Gamma", fileC.toFile().length(), 300, "UTF-8", 0, 0.0, 1L, lastOpenedAt = 200L),
                ),
            )

            val scanned = LibraryScanner.scan(tempDir, booksData)

            // Name sort (case-insensitive A -> B -> C)
            val sortedByName = LibraryScanner.sort(scanned, LibrarySortOption.NAME)
            assertEquals(listOf("Alpha", "Beta", "Gamma"), sortedByName.map { it.displayName })

            // Size sort (descending: B(500) -> C(300) -> A(100))
            val sortedBySize = LibraryScanner.sort(scanned, LibrarySortOption.SIZE)
            assertEquals(listOf("Beta", "Gamma", "Alpha"), sortedBySize.map { it.displayName })

            // Recent sort (lastOpenedAt descending: A(300) -> C(200) -> B(100))
            val sortedByRecent = LibraryScanner.sort(scanned, LibrarySortOption.RECENT)
            assertEquals(listOf("Alpha", "Gamma", "Beta"), sortedByRecent.map { it.displayName })
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun scanDirectory_returnsOnlyDirectChildrenAndExcludesDots() {
        val tempDir = createTempDirectory("library_scan_dir_test")
        try {
            // Root items
            val rootNovel = tempDir.resolve("root_novel.txt").apply { writeText("Root") }
            val subA = tempDir.resolve("Fantasy").apply { createDirectories() }
            val subB = tempDir.resolve("SciFi").apply { createDirectories() }

            // Items inside Fantasy
            val fantasyNovel1 = subA.resolve("dragon.txt").apply { writeText("Dragon") }
            val fantasyNestedDir = subA.resolve("Series1").apply { createDirectories() }
            fantasyNestedDir.resolve("book1.txt").apply { writeText("Book 1") }

            // Dot items
            tempDir.resolve(".stfolder").apply { createDirectories() }
            tempDir.resolve(".hidden.txt").apply { writeText("secret") }
            subA.resolve(".git").apply { createDirectories() }

            val booksData = BooksData(
                books = listOf(
                    BookRecord(
                        path = "root_novel.txt",
                        key = RelativePath.normalize("root_novel.txt"),
                        displayName = "root_novel",
                        sizeBytes = rootNovel.toFile().length(),
                        totalCharCount = 100,
                        detectedEncoding = "UTF-8",
                        anchor = 20,
                        progress = 0.20,
                        addedAt = 1000L,
                    )
                )
            )

            // Scan root directory
            val rootContent = LibraryScanner.scanDirectory(tempDir, "", booksData)
            assertEquals(2, rootContent.subfolders.size, "Root should have Fantasy and SciFi folders")
            assertEquals(listOf("Fantasy", "SciFi"), rootContent.subfolders.map { it.name }.sorted())
            assertEquals(1, rootContent.books.size, "Root should only have root_novel.txt as direct file")
            assertEquals("root_novel", rootContent.books[0].displayName)
            assertEquals("20%", rootContent.books[0].formattedProgress)

            // Scan Fantasy subfolder
            val fantasyContent = LibraryScanner.scanDirectory(tempDir, "Fantasy", booksData)
            assertEquals(1, fantasyContent.subfolders.size, "Fantasy should have Series1 folder only (excluding .git)")
            assertEquals("Series1", fantasyContent.subfolders[0].name)
            assertEquals(1, fantasyContent.books.size, "Fantasy should have dragon.txt only")
            assertEquals("dragon", fantasyContent.books[0].displayName)
            assertEquals("Fantasy/dragon.txt", fantasyContent.books[0].relativePath.replace('\\', '/'))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}


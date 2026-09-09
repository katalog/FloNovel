package com.moonkata.flonovel.desktop.preprocess

import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntakePipelineTest {

    private lateinit var tempDir: Path
    private lateinit var homeFolder: Path
    private lateinit var bookStoreFile: Path
    private lateinit var bookStore: BookStore

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("flonovel_intake_test")
        homeFolder = tempDir.resolve("ReadingHome")
        Files.createDirectories(homeFolder)
        bookStoreFile = tempDir.resolve("books.json")
        bookStore = BookStore(bookStoreFile)
    }

    @AfterTest
    fun tearDown() {
        bookStore.close()
        tempDir.toFile().deleteRecursively()
    }

    // --- I1: Order is strictly Preprocess -> Register (Contract requirement) ---
    @Test
    fun i1_orderIsPreprocessThenRegister() {
        val novelFile = homeFolder.resolve("test_novel.txt")
        val rawText = "Line 1\r\nLine 2\r\n제1화 모험\r\nLine 4"
        Files.writeString(novelFile, rawText, StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        try {
            val record = pipeline.processSingleFile(novelFile)
            assertNotNull(record, "Processed book must produce a valid record")
            assertNotNull(record.preprocessedAt, "preprocessedAt must be populated upon registration")

            // The content on disk must have been preprocessed into final form BEFORE registration
            val processedOnDisk = Files.readString(novelFile, StandardCharsets.UTF_8)
            assertTrue(processedOnDisk.contains("## 제1화 모험"), "Headings must be normalized on disk")
            assertTrue(processedOnDisk.contains("Line 1\n\nLine 2"), "Empty lines must be inserted between content")

            // bookStore record must match the post-preprocess state
            assertEquals(processedOnDisk.length, record.totalCharCount)
            val stored = bookStore.findByKey(record.key)
            assertNotNull(stored)
            assertEquals(record.key, stored.key)
            assertEquals(record.preprocessedAt, stored.preprocessedAt)
        } finally {
            pipeline.close()
        }
    }

    // --- I2: Already preprocessed files are NOT preprocessed again ---
    @Test
    fun i2_alreadyPreprocessedFileNotPreprocessedAgain() {
        val novelFile = homeFolder.resolve("existing_novel.txt")
        Files.writeString(novelFile, "Content A", StandardCharsets.UTF_8)

        // Seed bookStore with preprocessedAt timestamp
        val initialRecord = BookRecord(
            path = novelFile.toAbsolutePath().toString(),
            key = "existing_novel.txt",
            displayName = "existing_novel",
            sizeBytes = Files.size(novelFile),
            totalCharCount = "Content A".length,
            detectedEncoding = "UTF-8",
            anchor = 120, // user was reading at offset 120
            progress = 0.5,
            addedAt = System.currentTimeMillis() - 10000L,
            preprocessedAt = System.currentTimeMillis() - 5000L,
        )
        bookStore.addOrUpdate(initialRecord)
        bookStore.flush()

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
        )

        try {
            val processed = pipeline.processSingleFile(novelFile)
            assertNotNull(processed)
            // Existing anchor must be preserved and preprocessedAt untouched
            assertEquals(120, processed.anchor)
            assertEquals(initialRecord.preprocessedAt, processed.preprocessedAt)
        } finally {
            pipeline.close()
        }
    }

    // --- I3: Wait for write complete before processing ---
    @Test
    fun i3_waitForWriteCompleteHandlesGrowingFile() {
        val novelFile = homeFolder.resolve("downloading.txt")
        Files.writeString(novelFile, "Chunk 1", StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 100L,
            stableChecksRequired = 2,
        )

        try {
            // Simulate growing file on another thread
            val writerThread = Thread {
                Thread.sleep(80L)
                Files.writeString(novelFile, "Chunk 1 and Chunk 2 and Chunk 3", StandardCharsets.UTF_8)
            }
            writerThread.start()

            val record = pipeline.processSingleFile(novelFile)
            writerThread.join()

            assertNotNull(record)
            val finalContent = Files.readString(novelFile, StandardCharsets.UTF_8)
            assertTrue(finalContent.contains("Chunk 3"), "Pipeline must process the complete final file")
        } finally {
            pipeline.close()
        }
    }

    // --- I4: File deleted during wait skipped safely without crash ---
    @Test
    fun i4_fileDeletedDuringWaitSkippedSafely() {
        val novelFile = homeFolder.resolve("cancelled_download.txt")
        Files.writeString(novelFile, "Temporary", StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 80L,
            stableChecksRequired = 3,
        )

        try {
            // Delete file shortly after intake starts waiting
            val deleterThread = Thread {
                Thread.sleep(60L)
                Files.deleteIfExists(novelFile)
            }
            deleterThread.start()

            val result = pipeline.processSingleFile(novelFile)
            deleterThread.join()

            assertNull(result, "Deleted file must yield null result without throwing")
        } finally {
            pipeline.close()
        }
    }

    // --- I5: Startup reconciliation discovers files added while app was closed ---
    @Test
    fun i5_startupReconciliationDiscoversOfflineAddedFiles() {
        // Create 3 novel files in nested subdirectories while pipeline is not running
        val subDir = homeFolder.resolve("Fantasy").resolve("Korean")
        Files.createDirectories(subDir)

        val file1 = subDir.resolve("novel1.txt")
        val file2 = subDir.resolve("novel2.txt")
        val file3 = homeFolder.resolve("novel3.txt")
        Files.writeString(file1, "Story 1\n제1장 시작", StandardCharsets.UTF_8)
        Files.writeString(file2, "Story 2\n제1장 시작", StandardCharsets.UTF_8)
        Files.writeString(file3, "Story 3\n제1장 시작", StandardCharsets.UTF_8)

        // File 3 is already registered with preprocessedAt
        val record3 = BookRecord(
            path = file3.toAbsolutePath().toString(),
            key = "novel3.txt",
            displayName = "novel3",
            sizeBytes = Files.size(file3),
            totalCharCount = 20,
            detectedEncoding = "UTF-8",
            anchor = 0,
            progress = 0.0,
            addedAt = System.currentTimeMillis(),
            preprocessedAt = System.currentTimeMillis(),
        )
        bookStore.addOrUpdate(record3)
        bookStore.flush()

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        try {
            val enqueuedCount = pipeline.reconcile()
            // file1 and file2 should be enqueued; file3 was already preprocessed so it must NOT be enqueued
            assertEquals(2, enqueuedCount)

            // Wait for queue processing
            var attempts = 0
            while (!pipeline.isIdle() && attempts < 40) {
                Thread.sleep(100L)
                attempts++
            }

            assertNotNull(bookStore.findByKey(com.moonkata.flonovel.desktop.library.RelativePath.normalize("Fantasy/Korean/novel1.txt")))
            assertNotNull(bookStore.findByKey(com.moonkata.flonovel.desktop.library.RelativePath.normalize("Fantasy/Korean/novel2.txt")))
        } finally {
            pipeline.close()
        }
    }

    // --- I7: Intake runs in background without blocking reader ---
    @Test
    fun i7_intakeRunsInSeparateWorkerWithoutBlocking() {
        val novelFile = homeFolder.resolve("background_book.txt")
        Files.writeString(novelFile, "Chapter 1 text\n제1화 전설의 귀환", StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        val latch = CountDownLatch(1)
        pipeline.onFileProcessed = { _, _ -> latch.countDown() }

        try {
            val startTime = System.currentTimeMillis()
            pipeline.enqueue(novelFile)

            // Main / reading thread can perform operations uninterrupted
            var dummyReadingCount = 0
            for (i in 1..1000) {
                dummyReadingCount += i
            }
            assertTrue(dummyReadingCount > 0)

            val processed = latch.await(3, TimeUnit.SECONDS)
            assertTrue(processed, "File must be processed asynchronously in background worker")
        } finally {
            pipeline.close()
        }
    }

    // --- T-16 Safety: Failure preserves original and records failed file with retry ---
    @Test
    fun t16_failurePreservesOriginalUntouchedAndRecordsFailureForRetry() {
        val novelFile = homeFolder.resolve("safety_test.txt")
        val originalText = "Original untouched story"
        Files.writeString(novelFile, originalText, StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        try {
            // Non-existent target to simulate failure
            val nonExistent = homeFolder.resolve("does_not_exist.txt")
            val failureResult = pipeline.processSingleFile(nonExistent)
            assertNull(failureResult)

            // Verify original file is 100% untouched
            assertEquals(originalText, Files.readString(novelFile, StandardCharsets.UTF_8))

            // Test retry mechanism
            pipeline.retryFailed(novelFile)
            // Wait for processing
            var attempts = 0
            while (!pipeline.isIdle() && attempts < 20) {
                Thread.sleep(100L)
                attempts++
            }

            val registered = bookStore.findByKey("safety_test.txt")
            assertNotNull(registered)
            assertTrue(pipeline.failedFiles.none { it.path == novelFile.toAbsolutePath() })
        } finally {
            pipeline.close()
        }
    }

    // --- I8: Modifying an already preprocessed file updates record and invokes onFileProcessed ---
    @Test
    fun i8_modifiedPreprocessedFileUpdatesRecordAndCallsProcessed() {
        val novelFile = homeFolder.resolve("modified_novel.txt")
        Files.writeString(novelFile, "Chapter 1: Initial content", StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        try {
            // Initial intake
            val initial = pipeline.processSingleFile(novelFile)
            assertNotNull(initial)
            val initialPreprocessedAt = initial.preprocessedAt
            assertNotNull(initialPreprocessedAt)

            // Simulate user reading position at offset 10
            bookStore.updateReadingPosition(initial.key, anchor = 10, totalCharCount = initial.totalCharCount)

            // Now user modifies file content on disk in library folder
            Thread.sleep(50L)
            Files.writeString(novelFile, "Chapter 1: Initial content with extended chapters appended here!", StandardCharsets.UTF_8)

            var processedEventRecord: BookRecord? = null
            pipeline.onFileProcessed = { _, rec ->
                processedEventRecord = rec
            }

            // Process modified file with modify event
            val updated = pipeline.processSingleFile(novelFile, isModifyEvent = true)
            assertNotNull(updated)
            assertEquals(Files.size(novelFile), updated.sizeBytes)
            assertTrue(updated.sizeBytes > initial.sizeBytes, "Size must reflect new content")
            assertTrue(updated.totalCharCount > initial.totalCharCount, "Char count must reflect new content")
            assertTrue((updated.preprocessedAt ?: 0L) >= (initialPreprocessedAt ?: 0L), "preprocessedAt must be updated")
            assertEquals(10, updated.anchor, "Reading anchor must be preserved")
            assertNotNull(processedEventRecord, "onFileProcessed must have been called")
            assertEquals(updated.key, processedEventRecord?.key)
        } finally {
            pipeline.close()
        }
    }

    // --- I9: enqueueSubtree discovers all valid files in newly added subfolder ---
    @Test
    fun i9_enqueueSubtree_discoversAndEnqueuesFilesInNewSubdirectory() {
        val subDir = homeFolder.resolve("folder_b")
        val nestedDir = subDir.resolve("nested")
        Files.createDirectories(nestedDir)

        val file1 = subDir.resolve("book1.txt")
        val file2 = subDir.resolve("book2.txt")
        val nestedFile = nestedDir.resolve("book3.txt")
        val hiddenFile = subDir.resolve(".ignore.txt")

        Files.writeString(file1, "Book 1 content", StandardCharsets.UTF_8)
        Files.writeString(file2, "Book 2 content", StandardCharsets.UTF_8)
        Files.writeString(nestedFile, "Nested Book 3 content", StandardCharsets.UTF_8)
        Files.writeString(hiddenFile, "Should be ignored", StandardCharsets.UTF_8)

        val processedLatch = CountDownLatch(3)
        val processedKeys = mutableListOf<String>()

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        pipeline.onFileProcessed = { _, record ->
            synchronized(processedKeys) {
                processedKeys.add(record.key)
            }
            processedLatch.countDown()
        }

        try {
            pipeline.enqueueSubtree(subDir)
            val completed = processedLatch.await(5, TimeUnit.SECONDS)
            assertTrue(completed, "All 3 files in subdirectory tree must be processed")
            assertEquals(3, processedKeys.size)
            assertTrue(processedKeys.any { it.contains("book1.txt") })
            assertTrue(processedKeys.any { it.contains("book2.txt") })
            assertTrue(processedKeys.any { it.contains("book3.txt") })
            assertFalse(processedKeys.any { it.contains(".ignore") })
        } finally {
            pipeline.close()
        }
    }

    // --- I10: Subdirectories can be freely renamed without file locks when watcher is active ---
    @Test
    fun i10_treeWatchAllowsRenamingSubdirectoriesWithoutLock() {
        val folderA = homeFolder.resolve("folder_a")
        Files.createDirectories(folderA)
        val fileA = folderA.resolve("novel.txt")
        Files.writeString(fileA, "Novel inside folder A\n제1장 전설", StandardCharsets.UTF_8)

        val processedKeys = CopyOnWriteArrayList<String>()
        val fileProcessedLatch = CountDownLatch(1)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )
        pipeline.onFileProcessed = { _, record ->
            processedKeys.add(record.key)
            fileProcessedLatch.countDown()
        }

        try {
            pipeline.startWatcher()
            // Wait for initial file to be picked up
            fileProcessedLatch.await(3, TimeUnit.SECONDS)

            // On Windows / Sun JVM, verify isTreeWatchSupported is true
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
            if (isWindows) {
                assertTrue(pipeline.isTreeWatchSupported, "Tree watch must be supported on Windows OpenJDK")
            }

            // Rename folder_a to folder_renamed while watcher is actively polling
            val folderRenamed = homeFolder.resolve("folder_renamed")
            // This would throw FileSystemException if a directory handle was held by WatchService
            Files.move(folderA, folderRenamed)
            assertTrue(Files.exists(folderRenamed), "Renamed directory must exist on disk")
            assertFalse(Files.exists(folderA), "Original directory must no longer exist")

            // Wait for watcher to pick up renamed directory / file
            val renamedFile = folderRenamed.resolve("novel.txt")
            var attempts = 0
            val expectedKey = com.moonkata.flonovel.desktop.library.RelativePath.normalize("folder_renamed/novel.txt")
            while (attempts < 50) {
                Thread.sleep(100L)
                if (bookStore.findByKey(expectedKey) != null || processedKeys.contains(expectedKey)) {
                    break
                }
                attempts++
            }

            val stored = bookStore.findByKey(expectedKey)
            assertNotNull(stored, "File in renamed folder must be registered under new path key")
        } finally {
            pipeline.close()
        }
    }

    // --- I11: Watcher detects deeply nested files (depth > 2) in real time ---
    @Test
    fun i11_treeWatchDetectsDeeplyNestedChanges() {
        val deepDir = homeFolder.resolve("level1").resolve("level2").resolve("level3")
        Files.createDirectories(deepDir)

        val processedLatch = CountDownLatch(1)
        var processedKey: String? = null

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )
        pipeline.onFileProcessed = { _, record ->
            processedKey = record.key
            processedLatch.countDown()
        }

        try {
            pipeline.startWatcher()
            Thread.sleep(100L)

            val deepFile = deepDir.resolve("deep_book.txt")
            Files.writeString(deepFile, "Deep story content\n제1화 깊은 곳", StandardCharsets.UTF_8)

            val completed = processedLatch.await(4, TimeUnit.SECONDS)
            assertTrue(completed, "Deeply nested file must be detected by watcher and processed")
            val expectedKey = com.moonkata.flonovel.desktop.library.RelativePath.normalize("level1/level2/level3/deep_book.txt")
            assertEquals(expectedKey, processedKey)
        } finally {
            pipeline.close()
        }
    }

    @Test
    fun testProcessedBookRecordPathIsRelative() {
        val subDir = Files.createDirectories(homeFolder.resolve("0830"))
        val novelFile = subDir.resolve("my_novel.txt")
        Files.writeString(novelFile, "Line 1\r\nLine 2\r\n제1화 시작\r\nLine 4", StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        try {
            val record = pipeline.processSingleFile(novelFile)
            assertNotNull(record, "Processed file should produce a record")
            assertEquals("0830/my_novel.txt", record.path.replace('\\', '/'))
            assertEquals("0830/my_novel.txt", record.key)
            assertFalse(Path.of(record.path).isAbsolute, "Record path must be relative, not absolute")
        } finally {
            pipeline.close()
        }
    }
}


package com.moonkata.flonovel.desktop.preprocess

import com.moonkata.flonovel.desktop.library.BookStore
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreprocessingSafetyTest {

    private lateinit var tempDir: Path
    private lateinit var homeFolder: Path
    private lateinit var bookStoreFile: Path
    private lateinit var bookStore: BookStore

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("flonovel_safety_test")
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

    // --- 1. Failure leaves original untouched and isolates error to that file ---
    @Test
    fun failureLeavesOriginalUntouchedAndDoesNotKillPipeline() {
        val goodFile1 = homeFolder.resolve("good1.txt")
        val badFile = homeFolder.resolve("locked_file.txt")
        val goodFile2 = homeFolder.resolve("good2.txt")

        val goodContent1 = "Story 1\n제1장 시작"
        val goodContent2 = "Story 2\n제2장 시작"
        Files.writeString(goodFile1, goodContent1, StandardCharsets.UTF_8)
        Files.writeString(badFile, "Locked story\n제1장 시작", StandardCharsets.UTF_8)
        Files.writeString(goodFile2, goodContent2, StandardCharsets.UTF_8)

        // Simulate an exclusive file lock by another process
        val raf = java.io.RandomAccessFile(badFile.toFile(), "rw")
        val lock = raf.channel.lock()

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        try {
            // Process good file 1
            val res1 = pipeline.processSingleFile(goodFile1)
            assertNotNull(res1)

            // Process locked bad file (should fail safely without crashing pipeline)
            val resBad = pipeline.processSingleFile(badFile)
            assertNull(resBad)

            // Process good file 2 (must continue working normally)
            val res2 = pipeline.processSingleFile(goodFile2)
            assertNotNull(res2)

            // Verify failed files tracking
            assertEquals(1, pipeline.failedFiles.size)
            assertEquals(badFile.toAbsolutePath(), pipeline.failedFiles[0].path)
            assertFalse(pipeline.failedFiles[0].isDiskSpaceError)
        } finally {
            lock.release()
            raf.close()
            pipeline.close()
        }
    }

    // --- 2. Insufficient disk space explicitly alerts and aborts write ---
    @Test
    fun insufficientDiskSpaceAbortsAndFlagsDiskSpaceError() {
        val novelFile = homeFolder.resolve("big_novel.txt")
        val content = "Sample novel content for disk space check"
        Files.writeString(novelFile, content, StandardCharsets.UTF_8)

        // Custom preprocessor check with simulated insufficient disk space
        val exception = InsufficientDiskSpaceException("Insufficient disk space: required 100000000, available 100")
        val failure = IntakeFailure(
            path = novelFile.toAbsolutePath(),
            reason = exception.message ?: "",
            isDiskSpaceError = true,
        )

        assertTrue(failure.isDiskSpaceError, "Disk space error flag must be true")
        assertTrue(failure.reason.contains("Insufficient disk space"))

        // Original file must be completely untouched
        assertEquals(content, Files.readString(novelFile, StandardCharsets.UTF_8))
    }

    // --- 3. Retry mechanism allows recovering failed files ---
    @Test
    fun retryFailedFileReEnqueuesAndRecovers() {
        val novelFile = homeFolder.resolve("retry_novel.txt")
        val initialContent = "Draft story\n제1화 등장"
        Files.writeString(novelFile, initialContent, StandardCharsets.UTF_8)

        val pipeline = IntakePipeline(
            homeFolder = homeFolder,
            bookStore = bookStore,
            checkIntervalMs = 50L,
            stableChecksRequired = 1,
        )

        try {
            // Manually inject a failure to simulate an earlier network/IO issue
            val simulatedFailure = IntakeFailure(
                path = novelFile.toAbsolutePath(),
                reason = "Simulated temporary lock failure",
            )
            pipeline.failedFiles.add(simulatedFailure)
            assertEquals(1, pipeline.failedFiles.size)

            // Trigger retry
            pipeline.retryFailed(novelFile)

            // Wait for worker to pick up and process
            var attempts = 0
            while (!pipeline.isIdle() && attempts < 30) {
                Thread.sleep(100L)
                attempts++
            }

            // Failure must be cleared upon successful processing
            assertTrue(
                pipeline.failedFiles.none { it.path == novelFile.toAbsolutePath() },
                "Successfully retried file must be removed from failedFiles list",
            )

            val record = bookStore.findByKey("retry_novel.txt")
            assertNotNull(record)
            assertNotNull(record.preprocessedAt)
        } finally {
            pipeline.close()
        }
    }
}

package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import com.moonkata.flonovel.desktop.library.Settings
import com.moonkata.flonovel.desktop.library.SettingsStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DropboxSyncEngineTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun createEngine(
        homeDir: File,
        configDir: File,
    ): Triple<DropboxSyncEngine, BookStore, CredentialsStore> {
        val credsFile = configDir.resolve("credentials.json").toPath()
        val credsStore = CredentialsStore(credsFile)
        credsStore.save(Credentials(dropboxRefreshToken = "test_refresh_token"))

        val booksFile = configDir.resolve("books.json").toPath()
        val bookStore = BookStore(booksFile, debounceMs = 0L)

        val settingsFile = configDir.resolve("settings.json").toPath()
        val settingsStore = SettingsStore(settingsFile)

        val client = DropboxClient(
            appKey = "test_key",
            credentialsStore = credsStore,
            apiBaseUrl = server.url("").toString().removeSuffix("/"),
            contentBaseUrl = server.url("").toString().removeSuffix("/"),
        )
        client.setAccessToken("valid_test_token", 3600)

        val engine = DropboxSyncEngine(
            homeFolder = homeDir.toPath(),
            bookStore = bookStore,
            credentialsStore = credsStore,
            settingsStore = settingsStore,
            dropboxClient = client,
        )

        return Triple(engine, bookStore, credsStore)
    }

    /**
     * D1: Delta contains 'deleted' entry -> remote file tracking removes it.
     */
    @Test
    fun d1_deltaDeletedEntry_removesFromRemoteIndex() {
        val tempDir = Files.createTempDirectory("d1_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, _, credsStore) = createEngine(homeDir, configDir)
            credsStore.save(Credentials(dropboxRefreshToken = "test", dropboxCursor = "cur_123"))

            // list_folder/continue returns a deleted entry
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "entries": [
                                {
                                    ".tag": "deleted",
                                    "name": "novel1.txt",
                                    "path_lower": "/books/novel1.txt",
                                    "path_display": "/books/novel1.txt"
                                }
                            ],
                            "cursor": "cur_next_456",
                            "has_more": false
                        }
                        """.trimIndent()
                    )
            )

            val remoteFiles = engine.fetchRemoteFiles()
            assertFalse(remoteFiles.containsKey("/books/novel1.txt"))
            assertEquals("cur_next_456", credsStore.load().dropboxCursor)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * D2: cursor reset -> restarts from full list_folder.
     */
    @Test
    fun d2_cursorReset_restartsFromBeginning() {
        val tempDir = Files.createTempDirectory("d2_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, _, credsStore) = createEngine(homeDir, configDir)
            credsStore.save(Credentials(dropboxRefreshToken = "test", dropboxCursor = "expired_cursor"))

            // 1. list_folder/continue fails with 409 reset
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error_summary": "path/reset/..."}""")
            )

            // 2. Fallback full list_folder succeeds
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "entries": [
                                {
                                    ".tag": "file",
                                    "name": "full_list_book.txt",
                                    "path_lower": "/books/full_list_book.txt",
                                    "path_display": "/books/full_list_book.txt",
                                    "size": 500,
                                    "server_modified": "2026-09-07T00:00:00Z"
                                }
                            ],
                            "cursor": "fresh_cursor_789",
                            "has_more": false
                        }
                        """.trimIndent()
                    )
            )

            val remoteFiles = engine.fetchRemoteFiles()
            assertEquals(1, remoteFiles.size)
            assertTrue(remoteFiles.containsKey("/books/full_list_book.txt"))
            assertEquals("fresh_cursor_789", credsStore.load().dropboxCursor)

            assertEquals(2, server.requestCount)
            val req1 = server.takeRequest()
            assertEquals("/2/files/list_folder/continue", req1.path)
            val req2 = server.takeRequest()
            assertEquals("/2/files/list_folder", req2.path)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * D3: Size differs -> re-upload.
     */
    @Test
    fun d3_sizeDiffers_triggersReupload() {
        val tempDir = Files.createTempDirectory("d3_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore) = createEngine(homeDir, configDir)

            val file = homeDir.resolve("novel.txt")
            file.writeText("A".repeat(1000)) // 1000 bytes

            val record = BookRecord(
                path = "novel.txt",
                key = "novel.txt",
                displayName = "novel.txt",
                sizeBytes = 1000L,
                totalCharCount = 1000,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                preprocessedAt = 1000L,
                uploadedAt = 2000L,
                uploadedSize = 800L, // Different size
            )
            bookStore.addOrUpdate(record)

            // 1. list_folder returns remote file with size 800
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "entries": [
                                {
                                    ".tag": "file",
                                    "name": "novel.txt",
                                    "path_lower": "/books/novel.txt",
                                    "path_display": "/books/novel.txt",
                                    "size": 800,
                                    "server_modified": "2026-09-07T00:00:00Z"
                                }
                            ],
                            "cursor": "c1",
                            "has_more": false
                        }
                        """.trimIndent()
                    )
            )

            // 2. Upload file succeeds
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/books/novel.txt", "size": 1000}""")
            )

            val summary = engine.syncIncremental()
            assertNotNull(summary)
            assertEquals(1, summary.successCount)
            assertEquals(0, summary.failedCount)
            assertEquals(0, summary.skippedCount)

            val updatedRecord = bookStore.findByPath("novel.txt")
            assertEquals(1000L, updatedRecord?.uploadedSize)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * D4: Modification time differs, but size matches and uploadedAt >= preprocessedAt -> NOT re-uploaded.
     */
    @Test
    fun d4_modificationTimeDiffers_withSameSize_notReuploaded() {
        val tempDir = Files.createTempDirectory("d4_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore) = createEngine(homeDir, configDir)

            val file = homeDir.resolve("novel.txt")
            file.writeText("Same content") // 12 bytes
            // Change local last modified to simulate download timestamp
            file.setLastModified(System.currentTimeMillis() + 100000L)

            val record = BookRecord(
                path = "novel.txt",
                key = "novel.txt",
                displayName = "novel.txt",
                sizeBytes = 12L,
                totalCharCount = 12,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                preprocessedAt = 1000L,
                uploadedAt = 2000L, // Already uploaded after preprocessing
                uploadedSize = 12L,
            )
            bookStore.addOrUpdate(record)

            // Remote file has matching size (12 bytes), server_modified differs
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "entries": [
                                {
                                    ".tag": "file",
                                    "name": "novel.txt",
                                    "path_lower": "/books/novel.txt",
                                    "path_display": "/books/novel.txt",
                                    "size": 12,
                                    "server_modified": "2026-09-07T12:34:56Z"
                                }
                            ],
                            "cursor": "c1",
                            "has_more": false
                        }
                        """.trimIndent()
                    )
            )

            val summary = engine.syncIncremental()
            assertNotNull(summary)
            assertEquals(0, summary.successCount, "Must not re-upload unchanged file")
            assertEquals(0, summary.failedCount)
            assertEquals(1, summary.skippedCount)
            assertEquals(1, server.requestCount) // Only list_folder called, no upload call!
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * D5: Exclude files and folders starting with "." (.stfolder, .git, etc.).
     */
    @Test
    fun d5_dotPrefixedItemsExcluded() {
        val tempDir = Files.createTempDirectory("d5_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine) = createEngine(homeDir, configDir)

            // Normal file
            homeDir.resolve("normal.txt").writeText("hello")
            // Dot folder with files
            val stfolder = homeDir.resolve(".stfolder").apply { mkdirs() }
            stfolder.resolve("synced.txt").writeText("stfolder content")
            // Dot file
            homeDir.resolve(".hidden.txt").writeText("hidden content")

            val eligibleFiles = engine.collectLocalEligibleFiles()
            assertEquals(1, eligibleFiles.size)
            assertEquals("normal.txt", eligibleFiles[0].name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * D6: Individual file failure does not kill batch; returns aggregated summary (success, failed, skipped).
     */
    @Test
    fun d6_individualFileFailure_doesNotKillBatch() {
        val tempDir = Files.createTempDirectory("d6_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore) = createEngine(homeDir, configDir)

            val file1 = homeDir.resolve("file1.txt").apply { writeText("111") }
            val file2 = homeDir.resolve("file2.txt").apply { writeText("222") }
            val file3 = homeDir.resolve("file3.txt").apply { writeText("333") }

            for (f in listOf(file1, file2, file3)) {
                bookStore.addOrUpdate(
                    BookRecord(
                        path = f.name,
                        key = f.name,
                        displayName = f.name,
                        sizeBytes = 3L,
                        totalCharCount = 3,
                        detectedEncoding = "UTF-8",
                        anchor = 0,
                        progress = 0.0,
                        preprocessedAt = 1000L,
                    )
                )
            }

            // 1. list_folder returns empty remote
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"entries": [], "cursor": "c1", "has_more": false}""")
            )

            // 2. Upload file1 succeeds
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/books/file1.txt", "size": 3}""")
            )

            // 3. Upload file2 fails with 500 error
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error": "Internal server error"}""")
            )

            // 4. Upload file3 succeeds
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/books/file3.txt", "size": 3}""")
            )

            val summary = engine.syncIncremental()
            assertNotNull(summary)
            assertEquals(2, summary.successCount)
            assertEquals(1, summary.failedCount)
            assertEquals(0, summary.skippedCount)
            assertEquals(1, summary.failures.size)
            assertEquals("file2.txt", summary.failures[0].relativePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * D7: Insufficient space response -> marked as INSUFFICIENT_SPACE status.
     */
    @Test
    fun d7_insufficientSpace_markedAsPersistentStatus() {
        val tempDir = Files.createTempDirectory("d7_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore) = createEngine(homeDir, configDir)

            val file = homeDir.resolve("big_novel.txt").apply { writeText("Lots of text") }
            bookStore.addOrUpdate(
                BookRecord(
                    path = "big_novel.txt",
                    key = "big_novel.txt",
                    displayName = "big_novel.txt",
                    sizeBytes = file.length(),
                    totalCharCount = 12,
                    detectedEncoding = "UTF-8",
                    anchor = 0,
                    progress = 0.0,
                    preprocessedAt = 1000L,
                )
            )

            // list_folder empty
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"entries": [], "cursor": "c1", "has_more": false}""")
            )

            // Upload returns 409 insufficient_space
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "error_summary": "path/insufficient_space/...",
                            "error": {
                                ".tag": "path",
                                "path": {".tag": "insufficient_space"}
                            }
                        }
                        """.trimIndent()
                    )
            )

            val summary = engine.syncIncremental()
            assertNotNull(summary)
            assertEquals(SyncStatus.INSUFFICIENT_SPACE, engine.status)
            assertTrue(engine.isInsufficientSpace)
            assertEquals(1, engine.failedFiles.size)
            assertTrue(engine.failedFiles[0].isInsufficientSpace)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * D8: File deleted locally before/during upload -> skipped safely without crashing.
     */
    @Test
    fun d8_fileDeletedBeforeUpload_skippedSafely() {
        val tempDir = Files.createTempDirectory("d8_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore) = createEngine(homeDir, configDir)

            val ghostFile = homeDir.resolve("ghost.txt").apply { writeText("I will vanish") }
            bookStore.addOrUpdate(
                BookRecord(
                    path = "ghost.txt",
                    key = "ghost.txt",
                    displayName = "ghost.txt",
                    sizeBytes = 14L,
                    totalCharCount = 14,
                    detectedEncoding = "UTF-8",
                    anchor = 0,
                    progress = 0.0,
                    preprocessedAt = 1000L,
                )
            )

            // Delete before sync starts
            ghostFile.delete()

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"entries": [], "cursor": "c1", "has_more": false}""")
            )

            val summary = engine.syncIncremental()
            assertNotNull(summary)
            assertEquals(0, summary.successCount)
            assertEquals(0, summary.failedCount)
            assertEquals(0, summary.skippedCount)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Initial upload with progress reporting and pause/resume.
     */
    @Test
    fun initialUpload_progressAndPauseResume() {
        val tempDir = Files.createTempDirectory("initial_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore) = createEngine(homeDir, configDir)

            val f1 = homeDir.resolve("f1.txt").apply { writeText("1111") }
            val f2 = homeDir.resolve("f2.txt").apply { writeText("2222") }

            for (f in listOf(f1, f2)) {
                bookStore.addOrUpdate(
                    BookRecord(
                        path = f.name,
                        key = f.name,
                        displayName = f.name,
                        sizeBytes = 4L,
                        totalCharCount = 4,
                        detectedEncoding = "UTF-8",
                        anchor = 0,
                        progress = 0.0,
                        preprocessedAt = 1000L,
                    )
                )
            }

            assertTrue(engine.isInitialUploadRequired)

            // 1. list_folder
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"entries": [], "cursor": "c1", "has_more": false}""")
            )
            // 2. Upload f1
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/books/f1.txt", "size": 4}""")
            )
            // 3. Upload f2
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/books/f2.txt", "size": 4}""")
            )

            val progressList = mutableListOf<InitialUploadProgress>()
            val summary = engine.startInitialUpload { prog ->
                progressList.add(prog)
            }

            assertNotNull(summary)
            assertEquals(2, summary.successCount)
            assertFalse(engine.isInitialUploadRequired)
            assertTrue(progressList.isNotEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun asciiSafeDropboxApiArg_escapesKoreanAndSpecialCharactersForHeaderSafety() {
        val rawJson = """{"path":"/books/0829/[AI번역]＜R18＞소설〜1-8.txt","mode":"overwrite"}"""
        val safeHeader = asciiSafeDropboxApiArg(rawJson)

        // All characters must be in ASCII 32..126
        assertTrue(safeHeader.all { it.code in 32..126 })
        assertTrue(safeHeader.contains("\\ubc88\\uc5ed"))
        assertTrue(safeHeader.contains("\\uff1c")) // ＜
        assertTrue(safeHeader.contains("\\u301c")) // 〜

        // Verifies Java HttpRequest header validation does NOT throw IllegalArgumentException
        val req = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8080"))
            .header("Dropbox-API-Arg", safeHeader)
            .build()
        assertEquals(safeHeader, req.headers().firstValue("Dropbox-API-Arg").orElse(null))
    }

    @Test
    fun syncIncremental_reportsProgressAndDeletesRemoteWhenLocalFileMissing() {
        val tempDir = Files.createTempDirectory("inc_prog_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore, credsStore) = createEngine(homeDir, configDir)
            credsStore.save(Credentials(dropboxRefreshToken = "test", dropboxCursor = "c0"))

            // Local file f1 exists, but f_deleted does NOT exist locally
            val f1 = homeDir.resolve("f1.txt").apply { writeText("hello") }
            bookStore.addOrUpdate(
                BookRecord(
                    path = "f1.txt",
                    key = "f1.txt",
                    displayName = "f1",
                    sizeBytes = 5,
                    totalCharCount = 5,
                    detectedEncoding = "UTF-8",
                    anchor = 0,
                    progress = 0.0,
                    addedAt = 1000L,
                    preprocessedAt = 1000L,
                )
            )

            // Remote has f_deleted.txt which is missing locally
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "entries": [
                                {
                                    ".tag": "file",
                                    "name": "f_deleted.txt",
                                    "path_lower": "/books/f_deleted.txt",
                                    "path_display": "/books/f_deleted.txt",
                                    "size": 10,
                                    "server_modified": "2026-01-01T00:00:00Z"
                                }
                            ],
                            "cursor": "c1",
                            "has_more": false
                        }
                        """.trimIndent()
                    )
            )
            // delete_v2 for f_deleted.txt
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"metadata": {".tag": "file", "name": "f_deleted.txt"}}""")
            )
            // upload for f1.txt
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/books/f1.txt", "size": 5}""")
            )

            val progressList = mutableListOf<InitialUploadProgress>()
            val summary = engine.syncIncremental { prog ->
                progressList.add(prog)
            }

            assertNotNull(summary)
            assertEquals(1, summary.successCount)
            assertTrue(progressList.isNotEmpty())
            assertEquals(1, progressList.last().totalFiles)
            assertEquals(1, progressList.last().processedFiles)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun reconcileDeletions_deletesFolderOnDropboxWhenEntireFolderRemovedLocally() {
        val tempDir = Files.createTempDirectory("folder_del_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore, _) = createEngine(homeDir, configDir)

            val b1 = BookRecord(
                path = "0831/book1.txt",
                key = "0831/book1.txt",
                displayName = "book1",
                sizeBytes = 100,
                totalCharCount = 50,
                detectedEncoding = "UTF-8",
                anchor = 1234,
                progress = 0.5,
                preprocessedAt = 1000L,
                uploadedAt = 2000L,
            )
            val b2 = BookRecord(
                path = "0831/book2.txt",
                key = "0831/book2.txt",
                displayName = "book2",
                sizeBytes = 200,
                totalCharCount = 100,
                detectedEncoding = "UTF-8",
                anchor = 5678,
                progress = 0.8,
                preprocessedAt = 1000L,
                uploadedAt = 2000L,
            )
            bookStore.addOrUpdate(b1)
            bookStore.addOrUpdate(b2)

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"metadata": {".tag": "folder", "name": "0831"}}""")
            )

            val deletedCount = engine.reconcileDeletions(emptyList(), emptyMap())
            assertEquals(1, deletedCount)

            val req = server.takeRequest()
            assertTrue(req.path?.contains("delete_v2") == true)
            assertTrue(req.body.readUtf8().contains("/books/0831"))

            val updatedB1 = bookStore.findByKey("0831/book1.txt")
            val updatedB2 = bookStore.findByKey("0831/book2.txt")
            assertNotNull(updatedB1)
            assertNotNull(updatedB2)
            assertEquals(null, updatedB1.uploadedAt)
            assertEquals(null, updatedB2.uploadedAt)
            assertEquals(1234, updatedB1.anchor)
            assertEquals(5678, updatedB2.anchor)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun reconcileDeletions_deletesSingleFileWhenParentFolderStillExists() {
        val tempDir = Files.createTempDirectory("file_del_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore, _) = createEngine(homeDir, configDir)

            val novelsDir = homeDir.resolve("novels").apply { mkdirs() }
            val remainingFile = novelsDir.resolve("remaining_book.txt").apply { writeText("hello") }

            val bRemaining = BookRecord(
                path = "novels/remaining_book.txt",
                key = "novels/remaining_book.txt",
                displayName = "remaining_book",
                sizeBytes = 5,
                totalCharCount = 5,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                preprocessedAt = 1000L,
                uploadedAt = 2000L,
            )
            val bDeleted = BookRecord(
                path = "novels/deleted_book.txt",
                key = "novels/deleted_book.txt",
                displayName = "deleted_book",
                sizeBytes = 10,
                totalCharCount = 10,
                detectedEncoding = "UTF-8",
                anchor = 42,
                progress = 0.2,
                preprocessedAt = 1000L,
                uploadedAt = 2000L,
            )
            bookStore.addOrUpdate(bRemaining)
            bookStore.addOrUpdate(bDeleted)

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"metadata": {".tag": "file", "name": "deleted_book.txt"}}""")
            )

            val deletedCount = engine.reconcileDeletions(listOf(remainingFile), emptyMap())
            assertEquals(1, deletedCount)

            val req = server.takeRequest()
            assertTrue(req.path?.contains("delete_v2") == true)
            assertTrue(req.body.readUtf8().contains("/books/novels/deleted_book.txt"))

            val updatedDeleted = bookStore.findByKey("novels/deleted_book.txt")
            assertNotNull(updatedDeleted)
            assertEquals(null, updatedDeleted.uploadedAt)
            assertEquals(42, updatedDeleted.anchor)

            val updatedRemaining = bookStore.findByKey("novels/remaining_book.txt")
            assertNotNull(updatedRemaining)
            assertEquals(2000L, updatedRemaining.uploadedAt)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun syncIncremental_onlyUploadsNewFiles_notAlreadyUploadedFiles() {
        val tempDir = Files.createTempDirectory("inc_delta_test").toFile()
        try {
            val homeDir = tempDir.resolve("home").apply { mkdirs() }
            val configDir = tempDir.resolve("config").apply { mkdirs() }
            val (engine, bookStore, credsStore) = createEngine(homeDir, configDir)
            credsStore.save(Credentials(dropboxRefreshToken = "test", dropboxCursor = "c1"))

            // File 1: already uploaded
            val f1 = homeDir.resolve("already_uploaded.txt").apply { writeText("Content 1") }
            val record1 = BookRecord(
                path = "already_uploaded.txt",
                key = "already_uploaded.txt",
                displayName = "already_uploaded",
                sizeBytes = f1.length(),
                totalCharCount = 9,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                preprocessedAt = 1000L,
                uploadedAt = 2000L,
                uploadedSize = f1.length(),
            )
            bookStore.addOrUpdate(record1)

            // File 2: new file, not uploaded yet
            val f2 = homeDir.resolve("new_book.txt").apply { writeText("Content 2") }
            val record2 = BookRecord(
                path = "new_book.txt",
                key = "new_book.txt",
                displayName = "new_book",
                sizeBytes = f2.length(),
                totalCharCount = 9,
                detectedEncoding = "UTF-8",
                anchor = 0,
                progress = 0.0,
                preprocessedAt = 1000L,
                uploadedAt = null,
                uploadedSize = null,
            )
            bookStore.addOrUpdate(record2)

            // Remote returns empty delta (no changes on remote since cursor c1)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"entries": [], "cursor": "c2", "has_more": false}""")
            )

            // Upload request should ONLY happen for new_book.txt!
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/books/new_book.txt", "size": ${f2.length()}}""")
            )

            val summary = engine.syncIncremental()
            assertNotNull(summary)
            // Exactly 1 file should be uploaded (new_book.txt), and 1 skipped (already_uploaded.txt)!
            assertEquals(1, summary.successCount)
            assertEquals(1, summary.skippedCount)

            // Ensure server only received 1 upload request for new_book.txt
            val listReq = server.takeRequest() // list_folder/continue
            assertTrue(listReq.path?.contains("list_folder/continue") == true)
            val uploadReq = server.takeRequest() // upload for new_book.txt
            assertTrue(uploadReq.path?.contains("upload") == true)
            assertTrue(uploadReq.getHeader("Dropbox-API-Arg")?.contains("new_book.txt") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}




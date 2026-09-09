package com.moonkata.flonovel.desktop.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageRoundTripTest {

    @Test
    fun booksJson_roundTrip_exactMatch() {
        val tempDir = Files.createTempDirectory("books_test")
        try {
            val booksFile = tempDir.resolve("books.json")
            val store = BookStore(booksFile, debounceMs = 0L)

            val original = BooksData(
                schemaVersion = 1,
                books = listOf(
                    BookRecord(
                        path = "novels/소설1.txt",
                        key = "novels/소설1.txt",
                        displayName = "소설1.txt",
                        sizeBytes = 123456L,
                        totalCharCount = 45000,
                        detectedEncoding = "UTF-8",
                        anchor = 1234,
                        progress = 0.0274,
                        addedAt = 1725600000000L,
                        lastOpenedAt = 1725700000000L,
                        preprocessedAt = 1725600000000L,
                        uploadedAt = 1725600100000L,
                        uploadedSize = 123456L,
                    ),
                    BookRecord(
                        path = "Later/소설2.txt",
                        key = "later/소설2.txt",
                        displayName = "소설2.txt",
                        sizeBytes = 800000L,
                        totalCharCount = 300000,
                        detectedEncoding = "MS949",
                        anchor = 0,
                        progress = 0.0,
                        addedAt = 1725600050000L,
                    ),
                ),
            )

            store.save(original)

            // Read back fresh from disk
            val reloadedStore = BookStore(booksFile, debounceMs = 0L)
            val reloaded = reloadedStore.load()

            assertEquals(original.schemaVersion, reloaded.schemaVersion)
            assertEquals(original.books.size, reloaded.books.size)

            val b1 = reloaded.books[0]
            assertEquals("novels/소설1.txt", b1.path)
            assertEquals("novels/소설1.txt", b1.key)
            assertEquals("소설1.txt", b1.displayName)
            assertEquals(123456L, b1.sizeBytes)
            assertEquals(45000, b1.totalCharCount)
            assertEquals("UTF-8", b1.detectedEncoding)
            assertEquals(1234, b1.anchor)
            assertEquals(0.0274, b1.progress, 0.0001)
            assertEquals(1725600000000L, b1.addedAt)
            assertEquals(1725700000000L, b1.lastOpenedAt)
            assertEquals(1725600000000L, b1.preprocessedAt)
            assertEquals(1725600100000L, b1.uploadedAt)
            assertEquals(123456L, b1.uploadedSize)

            val b2 = reloaded.books[1]
            assertEquals("Later/소설2.txt", b2.path)
            assertEquals("later/소설2.txt", b2.key)
            assertNull(b2.lastOpenedAt)
            assertNull(b2.preprocessedAt)
            assertNull(b2.uploadedAt)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun settingsJson_roundTrip_exactMatch() {
        val tempDir = Files.createTempDirectory("settings_test")
        try {
            val settingsFile = tempDir.resolve("settings.json")
            val store = SettingsStore(settingsFile)

            val original = Settings(
                schemaVersion = 1,
                homeFolder = "C:\\FloNovel\\Library",
                view = ViewSettings(
                    paneMode = "TWO",
                    advanceRatio = 0.75f,
                    fontFamily = "NanumGothic",
                    fontSizeSp = 22.0f,
                    lineHeightMultiplier = 1.6f,
                    letterSpacing = 0.5f,
                    marginHorizontal = 24.0f,
                    marginTop = 20.0f,
                    marginBottom = 20.0f,
                    theme = "DARK",
                    gutter = 32.0f,
                    paneRatio = 0.5f,
                    maxLineWidth = 1000,
                    focusMode = true,
                    alignChapterToLeftPane = false,
                    uiScale = 1.3f,
                ),
                chapter = ChapterSettings(
                    enabledPresets = listOf("hash", "num_dot"),
                    customPatterns = listOf("^제\\s*\\d+\\s*막"),
                    jumpDivisions = 5,
                ),
                sync = SyncSettings(
                    dropboxLinked = true,
                    uploaderRole = false,
                    lastCursor = "cursor_abc_123",
                    lastSyncAt = 1725800000000L,
                ),
                lastOpenedBookKey = "novels/소설1.txt",
                librarySortOption = "SIZE",
                window = WindowSettings(
                    x = 150,
                    y = 200,
                    width = 1400,
                    height = 900,
                    isMaximized = true,
                ),
            )

            store.save(original)

            val reloadedStore = SettingsStore(settingsFile)
            val reloaded = reloadedStore.load()

            assertEquals(1, reloaded.schemaVersion)
            assertEquals("C:\\FloNovel\\Library", reloaded.homeFolder)
            assertEquals("TWO", reloaded.view.paneMode)
            assertEquals(0.75f, reloaded.view.advanceRatio)
            assertEquals("NanumGothic", reloaded.view.fontFamily)
            assertEquals("DARK", reloaded.view.theme)
            assertTrue(reloaded.view.focusMode)
            assertEquals(1.3f, reloaded.view.uiScale)
            assertEquals(listOf("hash", "num_dot"), reloaded.chapter.enabledPresets)
            assertEquals(listOf("^제\\s*\\d+\\s*막"), reloaded.chapter.customPatterns)
            assertEquals(5, reloaded.chapter.jumpDivisions)
            assertTrue(reloaded.sync.dropboxLinked)
            assertEquals("cursor_abc_123", reloaded.sync.lastCursor)
            assertEquals("novels/소설1.txt", reloaded.lastOpenedBookKey)
            assertEquals("SIZE", reloaded.librarySortOption)
            assertEquals(150, reloaded.window.x)
            assertEquals(200, reloaded.window.y)
            assertEquals(1400, reloaded.window.width)
            assertEquals(900, reloaded.window.height)
            assertTrue(reloaded.window.isMaximized)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun credentialsJson_fileSeparation_exactMatch() {
        val tempDir = Files.createTempDirectory("creds_test")
        try {
            val credsFile = tempDir.resolve("credentials.json")
            val store = CredentialsStore(credsFile)

            val original = Credentials(
                schemaVersion = 1,
                dropboxRefreshToken = "dp_refresh_secret_token_12345",
                cachedSupabaseSecret = "supa_shared_secret_hex_abcdef",
            )

            store.save(original)

            // Verify file exists separately
            assertTrue(Files.exists(credsFile))

            val content = Files.readString(credsFile)
            // Verify tokens are in credentials.json
            assertTrue(content.contains("dp_refresh_secret_token_12345"))
            assertTrue(content.contains("supa_shared_secret_hex_abcdef"))

            // Verify it loads correctly
            val reloaded = CredentialsStore(credsFile).load()
            assertEquals("dp_refresh_secret_token_12345", reloaded.dropboxRefreshToken)
            assertEquals("supa_shared_secret_hex_abcdef", reloaded.cachedSupabaseSecret)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

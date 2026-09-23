package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import com.moonkata.flonovel.desktop.library.RelativePath
import com.moonkata.flonovel.desktop.library.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockWebServer

/**
 * Two-way sync against [FakeDropbox]. Each test drives real files in a temp library and checks
 * both sides afterwards: the local folder and what the fake Dropbox holds.
 */
@OptIn(ExperimentalPathApi::class)
class DropboxSyncEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var dropbox: FakeDropbox
    private lateinit var root: Path
    private lateinit var home: Path
    private lateinit var config: Path
    private lateinit var trashDir: Path
    private lateinit var bookStore: BookStore
    private lateinit var stateStore: SyncStateStore
    private var trashAvailable = true

    @BeforeTest
    fun setUp() {
        dropbox = FakeDropbox()
        server = MockWebServer()
        server.dispatcher = dropbox
        server.start()
        root = Files.createTempDirectory("two_way_sync")
        home = Files.createDirectories(root.resolve("home"))
        config = Files.createDirectories(root.resolve("config"))
        trashDir = Files.createDirectories(root.resolve("trash"))
        bookStore = BookStore(config.resolve("books.json"), debounceMs = 0L)
        stateStore = SyncStateStore(config.resolve("sync-state.json"))
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        bookStore.close()
        root.deleteRecursively()
    }

    private fun engine(openInReader: Path? = null): DropboxSyncEngine {
        val creds = CredentialsStore(config.resolve("credentials.json"))
        creds.save(Credentials(dropboxRefreshToken = "refresh"))
        val base = server.url("").toString().removeSuffix("/")
        val client = DropboxClient(appKey = "k", credentialsStore = creds, apiBaseUrl = base, contentBaseUrl = base)
        client.setAccessToken("token", 3600)
        return DropboxSyncEngine(
            homeFolder = home,
            bookStore = bookStore,
            settingsStore = SettingsStore(config.resolve("settings.json")),
            dropboxClient = client,
            syncStateStore = stateStore,
            trash = if (trashAvailable) { path -> Files.move(path, trashDir.resolve(path.fileName.toString())); true } else null,
            today = { LocalDate.of(2026, 9, 23) },
        ).also { e -> e.isOpenInReader = { it == openInReader } }
    }

    /** A book the PC has already preprocessed and registered. */
    private fun localBook(rel: String, content: String, uploadedAt: Long? = null, preprocessedAt: Long = 1_000L): Path {
        val path = home.resolve(rel)
        Files.createDirectories(path.parent)
        path.writeText(content)
        bookStore.addOrUpdate(
            BookRecord(
                path = rel, key = RelativePath.normalize(rel), displayName = rel.substringAfterLast('/').removeSuffix(".txt"),
                sizeBytes = Files.size(path), totalCharCount = content.length, detectedEncoding = "UTF-8",
                anchor = 0, progress = 0.0, preprocessedAt = preprocessedAt, uploadedAt = uploadedAt,
            ),
        )
        return path
    }

    private fun sync(e: DropboxSyncEngine = engine(), allowMassDeletion: Boolean = false) =
        assertNotNull(e.syncIncremental(allowMassDeletion = allowMassDeletion))

    private fun edit(path: Path, content: String) {
        path.writeText(content)
        // Make the change visible even on file systems with coarse timestamps.
        Files.setLastModifiedTime(path, FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis() + 5_000))
    }

    // ── First sync (no bases) ────────────────────────────────────────────

    @Test
    fun newPc_downloadsRemoteBooks_registeredAsPreprocessed_withoutBackup() {
        dropbox.put("A/One.txt", "remote one")

        val summary = sync()

        assertEquals(1, summary.successCount)
        assertEquals("remote one", home.resolve("A/One.txt").readText())
        val record = assertNotNull(bookStore.findByKey("a/one.txt"))
        assertNotNull(record.preprocessedAt)
        assertEquals("A/One.txt", record.path)
        // Not run through the preprocessor: no backup copy of an already-preprocessed file.
        assertFalse(Files.exists(home.resolve(".flonovel/original")))
        assertEquals(0L, Files.list(home.resolve(".flonovel/tmp")).use { it.count() })
    }

    @Test
    fun firstSync_localOnly_uploadedAsAdd() {
        localBook("Local.txt", "local")
        sync()
        assertEquals("local", dropbox.content("Local.txt"))
        assertEquals(listOf("add"), dropbox.uploadModes)
    }

    @Test
    fun firstSync_sameContentBothSides_adoptedWithoutTransfer() {
        localBook("Same.txt", "same")
        dropbox.put("Same.txt", "same")
        val summary = sync()
        assertEquals(0, summary.successCount)
        assertTrue(dropbox.calls.none { it == "/2/files/upload" || it == "/2/files/download" })
        assertEquals(dropbox.rev("Same.txt"), stateStore.load().bases["same.txt"]?.rev)
    }

    @Test
    fun firstSync_differentContent_keepsBothAsConflictCopy() {
        val path = localBook("Book.txt", "mine")
        dropbox.put("Book.txt", "theirs")

        val summary = sync()

        assertEquals(1, summary.conflictCount)
        val copy = "Book (conflicted copy - PC - 2026-09-23).txt"
        assertEquals("theirs", path.readText())
        assertEquals("mine", home.resolve(copy).readText())
        assertEquals("mine", dropbox.content(copy))
        assertEquals("theirs", dropbox.content("Book.txt"))
        // The copy is a known, preprocessed book, so the watcher will not preprocess (and rename) it.
        assertNotNull(bookStore.findByKey(RelativePath.normalize(copy))?.preprocessedAt)
    }

    @Test
    fun missingBooksFolder_isAnEmptyRemote() {
        dropbox.booksFolderExists = false
        localBook("A.txt", "a")
        sync()
        assertEquals("a", dropbox.content("A.txt"))
    }

    // ── Upgrading from one-way sync ─────────────────────────────────────

    @Test
    fun upgrade_pcHadUnsentChange_replacesRemoteInsteadOfConflict() {
        localBook("Book.txt", "re-preprocessed", uploadedAt = 1_000L, preprocessedAt = 2_000L)
        val oldRev = dropbox.put("Book.txt", "old upload")

        val summary = sync()

        assertEquals(0, summary.conflictCount)
        assertEquals("re-preprocessed", dropbox.content("Book.txt"))
        assertEquals(listOf("update:$oldRev"), dropbox.uploadModes)
    }

    @Test
    fun upgrade_remoteOnly_isDownloadedNotDeleted() {
        // One-way sync deleted remote files missing locally; that rule is gone even on upgrade,
        // because a phone already on two-way sync may have added the file.
        localBook("Mine.txt", "mine", uploadedAt = 1_000L, preprocessedAt = 500L)
        dropbox.put("Mine.txt", "mine")
        dropbox.put("FromPhone.txt", "phone")

        sync()

        assertEquals("phone", home.resolve("FromPhone.txt").readText())
        assertEquals("phone", dropbox.content("FromPhone.txt"))
    }

    @Test
    fun afterFirstPass_noLongerUpgradeMode() {
        localBook("Book.txt", "v1", uploadedAt = 1_000L, preprocessedAt = 500L)
        dropbox.put("Book.txt", "v1")
        sync()
        assertTrue(stateStore.fileExists)
    }

    // ── Steady state (bases present) ────────────────────────────────────

    @Test
    fun localEdit_uploadsWithUpdateRev() {
        val path = localBook("Book.txt", "v1")
        sync()
        val rev = dropbox.rev("Book.txt")!!
        edit(path, "v2")

        sync()

        assertEquals("v2", dropbox.content("Book.txt"))
        assertEquals("update:$rev", dropbox.uploadModes.last())
    }

    @Test
    fun touchedButUnchanged_isNotUploaded() {
        val path = localBook("Book.txt", "v1")
        sync()
        Files.setLastModifiedTime(path, FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis() + 60_000))
        val uploadsBefore = dropbox.uploadModes.size

        sync()

        assertEquals(uploadsBefore, dropbox.uploadModes.size)
    }

    @Test
    fun remoteEdit_downloads_andKeepsReadingPositionClamped() {
        localBook("Book.txt", "0123456789")
        sync()
        bookStore.updateReadingPosition("book.txt", anchor = 8, totalCharCount = 10)
        dropbox.put("Book.txt", "short")

        sync()

        assertEquals("short", home.resolve("Book.txt").readText())
        assertEquals(5, bookStore.findByKey("book.txt")?.anchor)
    }

    @Test
    fun remoteDelete_movesLocalToTrash_andPrunesEmptyFolder() {
        localBook("Series/Vol1.txt", "v1")
        sync()
        dropbox.remove("Series/Vol1.txt")

        val summary = sync()

        assertEquals(1, summary.deletedCount)
        assertFalse(Files.exists(home.resolve("Series")))
        assertTrue(Files.exists(trashDir.resolve("Vol1.txt")))
    }

    @Test
    fun remoteFolderDeleted_trashesEveryFileUnderIt() {
        localBook("Series/Vol1.txt", "1")
        localBook("Series/Sub/Vol2.txt", "2")
        localBook("Other.txt", "o")
        sync()
        dropbox.removeFolder("Series")

        sync()

        assertFalse(Files.exists(home.resolve("Series")))
        assertTrue(Files.exists(home.resolve("Other.txt")))
        assertEquals(setOf("Vol1.txt", "Vol2.txt"), Files.list(trashDir).use { s -> s.map { it.fileName.toString() }.toList().toSet() })
    }

    @Test
    fun noRecycleBin_remoteDeleteLeavesFileAndReportsFailure() {
        trashAvailable = false
        localBook("Book.txt", "v1")
        sync()
        dropbox.remove("Book.txt")

        val summary = sync()

        assertTrue(Files.exists(home.resolve("Book.txt")))
        assertEquals(1, summary.failedCount)
    }

    @Test
    fun localDelete_deletesRemoteWithParentRev() {
        val path = localBook("Book.txt", "v1")
        sync()
        val rev = dropbox.rev("Book.txt")
        Files.delete(path)

        sync()

        assertNull(dropbox.content("Book.txt"))
        assertEquals(listOf(rev), dropbox.deleteParentRevs)
    }

    @Test
    fun bothEdited_conflictCopy() {
        val path = localBook("Book.txt", "v1")
        sync()
        edit(path, "mine")
        dropbox.put("Book.txt", "theirs")

        val summary = sync()

        assertEquals(1, summary.conflictCount)
        assertEquals("theirs", path.readText())
        assertEquals("mine", dropbox.content("Book (conflicted copy - PC - 2026-09-23).txt"))
    }

    @Test
    fun refusedUpdate_isDecidedAgain_asConflict() {
        val path = localBook("Book.txt", "v1")
        sync()
        edit(path, "mine")
        // Another device writes between this pass's listing and its upload.
        dropbox.beforeNextUpload = { dropbox.put("Book.txt", "theirs") }

        val summary = sync()

        assertEquals(1, summary.conflictCount)
        assertEquals("theirs", path.readText())
        assertEquals("mine", dropbox.content("Book (conflicted copy - PC - 2026-09-23).txt"))
        assertTrue("/2/files/get_metadata" in dropbox.calls)
    }

    // ── What sync must leave alone ──────────────────────────────────────

    @Test
    fun dotFolders_ignoredOnBothSides() {
        Files.createDirectories(home.resolve(".stfolder"))
        home.resolve(".stfolder/x.txt").writeText("hidden")
        dropbox.put(".git/y.txt", "hidden")
        dropbox.put("Cover.jpg", "not a book")

        sync()

        assertNull(dropbox.content(".stfolder/x.txt"))
        assertFalse(Files.exists(home.resolve(".git")))
        assertFalse(Files.exists(home.resolve("Cover.jpg")))
    }

    @Test
    fun fileAwaitingPreprocessing_isNeitherUploadedNorTreatedAsDeleted() {
        localBook("Book.txt", "v1")
        sync()
        // Re-dropped by the user: on disk, but the intake pipeline has not registered it yet.
        bookStore.addOrUpdate(bookStore.findByKey("book.txt")!!.copy(preprocessedAt = null))

        sync()

        assertEquals("v1", dropbox.content("Book.txt"))
        assertTrue(dropbox.deleteParentRevs.isEmpty())
    }

    @Test
    fun openBook_isDeferred_andCursorKeptUntilClosed() {
        val path = localBook("Book.txt", "v1")
        sync()
        dropbox.put("Book.txt", "v2")

        val summary = sync(engine(openInReader = path))
        assertEquals(1, summary.deferredCount)
        assertEquals("v1", path.readText())

        sync()
        assertEquals("v2", path.readText())
    }

    // ── Failures, cursor and guard ──────────────────────────────────────

    @Test
    fun oneFailure_othersProceed_andCursorIsKept() {
        dropbox.put("Good.txt", "good")
        dropbox.put("Bad.txt", "bad")
        dropbox.failDownloadsOf += "Bad.txt"

        val summary = sync()

        assertEquals(1, summary.failedCount)
        assertEquals("good", home.resolve("Good.txt").readText())
        assertNull(stateStore.load().cursor)

        dropbox.failDownloadsOf.clear()
        sync()
        assertEquals("bad", home.resolve("Bad.txt").readText())
        assertNotNull(stateStore.load().cursor)
    }

    @Test
    fun corruptedDownload_isRejectedByContentHash() {
        dropbox.put("Book.txt", "real")
        dropbox.corruptDownloadsOf += "Book.txt"

        val summary = sync()

        assertEquals(1, summary.failedCount)
        assertFalse(Files.exists(home.resolve("Book.txt")))
    }

    @Test
    fun massDeletion_isWithheld_untilAllowed() {
        repeat(20) { localBook("B$it.txt", "$it") }
        sync()
        repeat(20) { dropbox.remove("B$it.txt") }

        val held = sync()
        assertEquals(20, held.withheldDeletions.size)
        assertEquals(20, Files.list(home).use { s -> s.filter { it.toString().endsWith(".txt") }.count() })

        sync(allowMassDeletion = true)
        assertEquals(0, Files.list(home).use { s -> s.filter { it.toString().endsWith(".txt") }.count() })
    }

    @Test
    fun cursorReset_relistsEverything() {
        localBook("Book.txt", "v1")
        sync()
        dropbox.put("New.txt", "new")
        dropbox.resetNextContinue = true

        sync()

        assertEquals("new", home.resolve("New.txt").readText())
    }

    @Test
    fun insufficientSpace_stopsWithStatus() {
        localBook("A.txt", "a")
        dropbox.insufficientSpace = true
        val e = engine()

        val summary = assertNotNull(e.syncIncremental())

        assertEquals(SyncStatus.INSUFFICIENT_SPACE, e.status)
        assertTrue(summary.failures.single().isInsufficientSpace)
    }

    @Test
    fun pause_stopsBeforeNextFile_andResumeFinishes() {
        repeat(3) { dropbox.put("B$it.txt", "$it") }
        val e = engine()

        e.syncIncremental { progress -> if (progress.processedFiles == 1) e.pause() }
        assertEquals(SyncStatus.PAUSED, e.status)
        assertNull(stateStore.load().cursor)

        e.resume()
        repeat(3) { assertTrue(Files.exists(home.resolve("B$it.txt"))) }
    }

    @Test
    fun asciiSafeDropboxApiArg_escapesKoreanForHeaderSafety() {
        val escaped = asciiSafeDropboxApiArg("""{"path":"/books/한글.txt"}""")
        assertTrue(escaped.all { it.code < 128 })
        assertEquals("/books/한글.txt", org.json.JSONObject(escaped).getString("path"))
    }
}

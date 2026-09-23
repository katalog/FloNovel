package com.moonkata.flonovel.android.data.sync

import com.moonkata.flonovel.android.data.db.SyncBaseEntity
import com.moonkata.flonovel.android.data.preprocess.LibraryPreprocessor
import com.moonkata.flonovel.android.data.preprocess.TextPreprocessor
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Two-way sync on the phone, against an in-memory library folder and a fake Dropbox. Each test
 * checks both sides afterwards. The cases follow the Desktop engine's suite where the rules are
 * shared, plus what only the phone has: in-place writes, interrupted downloads, read-only links,
 * and books added on the phone, which are preprocessed before their first upload.
 */
class TwoWayBookSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var dropbox: FakeDropbox
    private lateinit var library: FakeLibraryFiles
    private lateinit var dao: FakeSyncBaseDao
    private lateinit var client: DropboxClient
    private var cursor = ""

    @Before
    fun setUp() = runBlocking {
        dropbox = FakeDropbox()
        server = MockWebServer()
        server.dispatcher = dropbox
        server.start()
        library = FakeLibraryFiles()
        dao = FakeSyncBaseDao()
        val base = server.url("").toString().removeSuffix("/")
        client = DropboxClient(
            loadRefreshToken = { "refresh" },
            saveRotatedRefreshToken = {},
            appKey = "k",
            apiBase = base,
            contentBase = base,
        )
        client.cacheAccessToken("token", 3600)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private var openBook: String? = null

    private fun engine(canWrite: Boolean = true, syncedOneWayBefore: Boolean = false) = TwoWayBookSync(
        files = library,
        client = client,
        baseDao = dao,
        loadCursor = { cursor },
        saveCursor = { cursor = it },
        canWrite = canWrite,
        syncedOneWayBefore = syncedOneWayBefore,
        conflictLabel = "conflicted copy",
        today = { "2026-09-23" },
        prepareNewBook = { rel ->
            when (val r = LibraryPreprocessor(library).preprocess(rel)) {
                is LibraryPreprocessor.Result.Unchanged -> r.relativePath
                is LibraryPreprocessor.Result.Processed -> r.to
                is LibraryPreprocessor.Result.Failed -> null
            }
        },
        isInUse = { rel -> rel == openBook },
    )

    private fun sync(
        canWrite: Boolean = true,
        syncedOneWayBefore: Boolean = false,
        allowMassDeletion: Boolean = false,
    ): TwoWaySyncResult = runBlocking {
        checkNotNull(engine(canWrite, syncedOneWayBefore).sync(allowMassDeletion)) { "sync could not start" }
    }

    private val copyName = "Book (conflicted copy - Android - 2026-09-23).txt"

    // ── First sync ──────────────────────────────────────────────────────

    @Test
    fun remoteBooks_areDownloaded() {
        dropbox.put("A/One.txt", "one")
        val result = sync()
        assertEquals(1, result.downloaded)
        assertEquals("one", library.content("A/One.txt"))
        assertEquals("SYNCED", dao.rows["a/one.txt"]?.state)
    }

    @Test
    fun bookAddedOnPhone_isPreprocessedThenUploadedUnderItsCleanedName() {
        val raw = "  제1화 시작\n본문\n"
        library.put("Series/삼국지 三國志.txt", raw)

        val result = sync()

        val processed = TextPreprocessor.normalizeContent(raw)
        assertEquals(1, result.uploaded)
        assertEquals(processed, library.content("Series/삼국지.txt"))
        assertEquals(processed, dropbox.content("Series/삼국지.txt"))
        assertNull(dropbox.content("Series/삼국지 三國志.txt"))
        assertEquals("add", dropbox.uploadModes.single())
        assertEquals("SYNCED", dao.rows["series/삼국지.txt"]?.state)
        // The next pass has nothing to do.
        assertEquals(0, sync().changed)
    }

    @Test
    fun bookAddedOnPhone_thatCannotBePreprocessed_isNotUploadedRaw() {
        library.put("Mine.txt", "raw")
        library.unreadable += "Mine.txt"

        sync()

        assertNull(dropbox.content("Mine.txt"))
    }

    @Test
    fun sameContentBothSides_adoptedWithoutTransfer() {
        library.put("Book.txt", "same")
        dropbox.put("Book.txt", "same")
        val result = sync()
        assertEquals(0, result.changed)
        assertTrue(dropbox.calls.none { it == "/2/files/download" || it == "/2/files/upload" })
    }

    @Test
    fun differentContentWithoutBase_isAConflict_keepingBoth() {
        library.put("Book.txt", "mine")
        dropbox.put("Book.txt", "theirs")
        val id = library.documentId("Book.txt")

        val result = sync()

        assertEquals(1, result.conflicts)
        assertEquals("theirs", library.content("Book.txt"))
        assertEquals("mine", library.content(copyName))
        assertEquals("mine", dropbox.content(copyName))
        assertEquals("the original keeps its URI", id, library.documentId("Book.txt"))
    }

    @Test
    fun upgradeFromOneWay_differentContent_remoteWins() {
        // Under one-way sync the phone never wrote; a mismatch is a download that never finished.
        library.put("Book.txt", "trunc")
        dropbox.put("Book.txt", "complete")

        val result = sync(syncedOneWayBefore = true)

        assertEquals(0, result.conflicts)
        assertEquals("complete", library.content("Book.txt"))
        assertFalse(copyName in library.paths())
    }

    @Test
    fun missingBooksFolder_isAnEmptyRemote() {
        dropbox.booksFolderExists = false
        val result = sync()
        assertEquals(0, result.failed)
    }

    // ── Steady state ────────────────────────────────────────────────────

    private fun syncedBook(rel: String = "Book.txt", content: String = "v1") {
        dropbox.put(rel, content)
        sync()
    }

    @Test
    fun localEdit_uploadsWithUpdateRev() {
        syncedBook()
        val rev = dropbox.rev("Book.txt")
        library.put("Book.txt", "v2")

        val result = sync()

        assertEquals(1, result.uploaded)
        assertEquals("v2", dropbox.content("Book.txt"))
        assertEquals("update:$rev", dropbox.uploadModes.last())
    }

    @Test
    fun remoteEdit_downloadsInPlace_keepingTheUri() {
        syncedBook()
        val id = library.documentId("Book.txt")
        dropbox.put("Book.txt", "v2")

        sync()

        assertEquals("v2", library.content("Book.txt"))
        assertEquals(id, library.documentId("Book.txt"))
    }

    @Test
    fun remoteDelete_deletesLocal() {
        syncedBook()
        dropbox.remove("Book.txt")
        val result = sync()
        assertEquals(1, result.deletedLocal)
        assertNull(library.content("Book.txt"))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun remoteFolderDelete_deletesEverythingUnderIt() {
        dropbox.put("Series/One.txt", "1")
        dropbox.put("Series/Sub/Two.txt", "2")
        dropbox.put("Other.txt", "o")
        sync()
        dropbox.removeFolder("Series")

        sync()

        assertEquals(setOf("Other.txt"), library.paths())
    }

    @Test
    fun localDelete_deletesRemoteAtBaseRev() {
        syncedBook()
        val rev = dropbox.rev("Book.txt")
        library.delete("Book.txt")

        val result = sync()

        assertEquals(1, result.deletedRemote)
        assertNull(dropbox.content("Book.txt"))
        assertEquals(listOf(rev), dropbox.deleteParentRevs)
    }

    @Test
    fun bothEdited_conflictCopy_andOriginalKeepsItsUri() {
        syncedBook()
        val id = library.documentId("Book.txt")
        library.put("Book.txt", "mine")
        dropbox.put("Book.txt", "theirs")

        val result = sync()

        assertEquals(1, result.conflicts)
        assertEquals("theirs", library.content("Book.txt"))
        assertEquals(id, library.documentId("Book.txt"))
        assertEquals("mine", library.content(copyName))
        assertEquals("mine", dropbox.content(copyName))
    }

    @Test
    fun refusedUpdate_isDecidedAgain_asConflict() {
        syncedBook()
        library.put("Book.txt", "mine")
        dropbox.beforeNextUpload = { dropbox.put("Book.txt", "theirs") }

        val result = sync()

        assertEquals(1, result.conflicts)
        assertEquals("theirs", library.content("Book.txt"))
        assertEquals("mine", dropbox.content(copyName))
    }

    // ── Interrupted and bad downloads ───────────────────────────────────

    @Test
    fun interruptedDownload_isRefetched_andTheTruncatedFileNeverUploaded() {
        syncedBook(content = "v1")
        dropbox.put("Book.txt", "version two")
        library.interruptWritesOf += "Book.txt"

        val first = sync()
        assertEquals(1, first.failed)
        assertEquals("DOWNLOADING", dao.rows["book.txt"]?.state)
        // The short file on disk now differs from the base; it must not be mistaken for an edit.
        library.interruptWritesOf.clear()
        val uploadsBefore = dropbox.uploadModes.size

        sync()

        assertEquals(uploadsBefore, dropbox.uploadModes.size)
        assertEquals("version two", library.content("Book.txt"))
        assertEquals("version two", dropbox.content("Book.txt"))
        assertEquals("SYNCED", dao.rows["book.txt"]?.state)
    }

    @Test
    fun corruptedDownload_failsTheHashCheck_andStaysMarked() {
        dropbox.put("Book.txt", "real")
        dropbox.corruptDownloadsOf += "Book.txt"

        val result = sync()

        assertEquals(1, result.failed)
        assertEquals("DOWNLOADING", dao.rows["book.txt"]?.state)
        assertTrue(cursor.isBlank())
    }

    // ── The book open in the reader ─────────────────────────────────────

    @Test
    fun openBook_isLeftAlone_untilClosed() {
        syncedBook("Open.txt", "v1")
        syncedBook("Other.txt", "o1")
        dropbox.put("Open.txt", "v2")
        dropbox.put("Other.txt", "o2")
        openBook = "Open.txt"

        val held = sync()
        assertEquals(1, held.deferred)
        assertEquals("v1", library.content("Open.txt"))
        assertEquals("o2", library.content("Other.txt"))

        openBook = null
        sync()
        assertEquals("v2", library.content("Open.txt"))
    }

    @Test
    fun openBook_remoteDeletion_waitsToo() {
        syncedBook()
        dropbox.remove("Book.txt")
        openBook = "Book.txt"

        assertEquals(1, sync().deferred)
        assertEquals("v1", library.content("Book.txt"))
    }

    @Test
    fun failedPaths_nameTheBooks() {
        dropbox.put("Bad.txt", "x")
        dropbox.failDownloadsOf += "Bad.txt"
        assertEquals(listOf("Bad.txt"), sync().failedPaths)
    }

    // ── Write access ────────────────────────────────────────────────────

    @Test
    fun readOnlyLink_stillDownloads_butHoldsLocalChanges() {
        syncedBook("Edited.txt", "v1")
        syncedBook("Gone.txt", "g")
        library.put("Edited.txt", "v2")
        library.delete("Gone.txt")
        dropbox.put("New.txt", "new")

        val result = sync(canWrite = false)

        assertEquals("new", library.content("New.txt"))
        assertEquals(2, result.waitingForWriteAccess)
        assertEquals("v1", dropbox.content("Edited.txt"))
        assertEquals("g", dropbox.content("Gone.txt"))
        // The deleted book is not downloaded again while waiting.
        assertNull(library.content("Gone.txt"))
    }

    @Test
    fun serverSaysMissingScope_isWaitingForWriteAccess_notFailure() {
        // Exercised through delete: its request body is not streamed, so the JVM's
        // HttpURLConnection returns the 401 body instead of throwing as it does for uploads.
        syncedBook()
        library.delete("Book.txt")
        dropbox.tokenLacksWriteScope = true

        val result = sync()

        assertEquals(1, result.waitingForWriteAccess)
        assertEquals(0, result.failed)
    }

    // ── Guard, cursor, filters ──────────────────────────────────────────

    @Test
    fun massDeletion_isWithheld_untilAllowed() {
        repeat(20) { dropbox.put("B$it.txt", "$it") }
        sync()
        repeat(20) { dropbox.remove("B$it.txt") }

        val held = sync()
        assertEquals(20, held.withheldDeletions.size)
        assertEquals(20, library.paths().size)

        sync(allowMassDeletion = true)
        assertTrue(library.paths().isEmpty())
    }

    @Test
    fun deletedAndReAddedInOneWindow_isKept() {
        // One delta carries both the deletion and the re-add; the re-add is the current truth.
        syncedBook(content = "v1")
        dropbox.remove("Book.txt")
        dropbox.put("Book.txt", "v2")

        val result = sync()

        assertEquals(0, result.deletedLocal)
        assertEquals("v2", library.content("Book.txt"))
    }

    @Test
    fun emptiedRemoteSeenByFullRelisting_isWithheld() {
        // Wrong account or a reset app folder looks like "everything was deleted".
        syncedBook("A.txt", "a")
        syncedBook("B.txt", "b")
        dropbox.remove("A.txt")
        dropbox.remove("B.txt")
        dropbox.resetNextContinue = true

        val result = sync()

        assertEquals(2, result.withheldDeletions.size)
        assertEquals(setOf("A.txt", "B.txt"), library.paths())
    }

    @Test
    fun cursorReset_relistsEverything() {
        syncedBook()
        dropbox.put("New.txt", "new")
        dropbox.resetNextContinue = true
        sync()
        assertEquals("new", library.content("New.txt"))
    }

    @Test
    fun dotFoldersAndNonBooks_ignoredOnBothSides() {
        dropbox.put(".git/x.txt", "hidden")
        dropbox.put("Cover.jpg", "image")
        library.put(".stfolder/y.txt", "hidden")

        sync()

        assertFalse(".git/x.txt" in library.paths())
        assertFalse("Cover.jpg" in library.paths())
        assertNull(dropbox.content(".stfolder/y.txt"))
    }

    @Test
    fun unreadableLocalFile_isNotTreatedAsDeleted() {
        syncedBook()
        library.put("Book.txt", "v2")
        library.unreadable += "Book.txt"

        val result = sync()

        assertEquals(0, result.deletedRemote)
        assertEquals("v1", dropbox.content("Book.txt"))
    }

    @Test
    fun unknownStoredState_isTreatedAsNoBase() = runBlocking {
        library.put("Book.txt", "same")
        dropbox.put("Book.txt", "same")
        dao.upsert(SyncBaseEntity("book.txt", "Book.txt", "old", "old", 1, 1, "FROM_THE_FUTURE"))

        val result = sync()

        assertEquals(0, result.changed)
        assertEquals("SYNCED", dao.rows["book.txt"]?.state)
    }
}

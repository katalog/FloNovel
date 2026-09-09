package com.moonkata.flonovel.desktop

import com.moonkata.flonovel.desktop.library.BookRecord
import com.moonkata.flonovel.desktop.library.BookStore
import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import com.moonkata.flonovel.desktop.library.ResumeManager
import com.moonkata.flonovel.desktop.library.Settings
import com.moonkata.flonovel.desktop.library.SettingsStore
import com.moonkata.flonovel.desktop.library.SyncSettings
import com.moonkata.flonovel.desktop.preprocess.IntakePipeline
import com.moonkata.flonovel.desktop.reader.FakeTextFitter
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import com.moonkata.flonovel.desktop.sync.ReadingPositionSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-27 — the user scenarios from `docs/09-TEST-STRATEGY.md` §8, exercised as whole flows rather
 * than as component slices.
 *
 * Most of U1–U14 were already covered while their features were built. This file adds the six that
 * had nothing end-to-end behind them, and records where the rest are proven so the map is not
 * scattered across a dozen files:
 *
 * | # | Scenario | Where it is proven |
 * |---|---|---|
 * | U1 | Launch -> resume the last book at the exact character | **here** (disk round trip included) · `ResumeManagerTest` |
 * | U2 | Resume target deleted -> no crash | `ResumeManagerTest.resume_missingFile_returnsNullWithoutCrash` |
 * | U3 | 1-pane advances by half | `OnePaneNavigationTest.u3_…` |
 * | U4 | 2-pane right becomes left | `TwoPaneNavigationTest.m3_twoPane_advanceOnce_newLeftEqualsPreviousRight` |
 * | U5 | 1 <-> 2 pane switch -> same character | `TwoPaneNavigationTest.u5_…` |
 * | U6 | Settings change -> same character | `SettingsInvarianceTest` (6 cases) |
 * | U7 | Chapter jump · zero chapters is fine | `TocTest` |
 * | U8 | Search runs only on submit · no result cap | `KeyboardNavigationTest.u8_…` |
 * | U9 | New file arrives mid-read -> no interruption | **here** · `IntakePipelineTest` |
 * | U10 | One Dropbox link enables both syncs | **here** |
 * | U11 | Phone read further -> notice -> accept -> jump | **here** |
 * | U12 | No network -> read, save and resume still work | **here** |
 * | U13 | Attempt to edit the text -> file unchanged | **here** · `PreprocessingSafetyTest` |
 * | U14 | Whole flow without a mouse | `KeyboardNavigationTest` |
 *
 * What these cannot show: anything that only exists once a window is on screen. Rendering, focus,
 * and the actual key-event plumbing are excluded by construction — see `docs/03-ARCHITECTURE.md`
 * A9-1, written after a green suite sat next to an app whose keyboard did nothing at all.
 */
class ScenarioTest {

    private lateinit var tempDir: Path
    private lateinit var homeFolder: Path
    private lateinit var bookStore: BookStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var credentialsStore: CredentialsStore

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("flonovel_scenario")
        homeFolder = tempDir.resolve("ReadingHome")
        Files.createDirectories(homeFolder)
        bookStore = BookStore(tempDir.resolve("books.json"))
        settingsStore = SettingsStore(tempDir.resolve("settings.json"))
        credentialsStore = CredentialsStore(tempDir.resolve("credentials.json"))
    }

    @AfterTest
    fun tearDown() {
        bookStore.close()
        System.clearProperty("flonovel.supabase.url")
        System.clearProperty("flonovel.supabase.publishable_key")
        tempDir.toFile().deleteRecursively()
    }

    private fun writeBook(relativePath: String, text: String): Path {
        val file = homeFolder.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file
    }

    private fun sampleText(paragraphs: Int = 400): String =
        (1..paragraphs).joinToString("\n") { "$it 번째 문단입니다. 여기에 본문이 이어집니다." }

    private fun sha256(path: Path): String =
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }

    // ── U1 — launch resumes the last book at the exact character ───────────

    /**
     * The whole point of the app: close it mid-sentence, open it, and be back on that sentence.
     *
     * Unlike `ResumeManagerTest`, this goes through the stores on disk, because that is what
     * actually happens at launch — the in-memory objects are gone by then. A field that fails to
     * serialize would pass the unit test and lose the position in real use.
     */
    @Test
    fun u1_relaunchResumesTheLastBookAtTheExactCharacter() {
        val text = sampleText()
        writeBook("novel/book.txt", text)
        val anchor = 5_000
        assertTrue(anchor < text.length, "Fixture must be long enough for the anchor to be interesting")

        // Session 1: the reader saves where it stopped.
        bookStore.addOrUpdate(
            BookRecord(
                path = "novel/book.txt",
                key = "novel/book.txt",
                displayName = "book.txt",
                sizeBytes = Files.size(homeFolder.resolve("novel/book.txt")),
                totalCharCount = text.length,
                detectedEncoding = "UTF-8",
                anchor = anchor,
                progress = anchor.toDouble() / text.length,
                lastOpenedAt = 1_000L,
                preprocessedAt = 900L,
            ),
        )
        bookStore.flush()
        settingsStore.save(Settings(homeFolder = homeFolder.toString(), lastOpenedBookKey = "novel/book.txt"))

        // Session 2: nothing carried over in memory — everything is read back from disk.
        val reloadedBooks = BookStore(tempDir.resolve("books.json")).use { it.load() }
        val reloadedSettings = SettingsStore(tempDir.resolve("settings.json")).load()

        val target = ResumeManager.findResumeTarget(homeFolder, reloadedSettings, reloadedBooks)

        assertNotNull(target, "The last book must be offered again")
        assertEquals("novel/book.txt", target.book.key)
        assertEquals(anchor, target.clampedAnchor, "Resume must land on the exact character, not near it")
        assertEquals(text.length, target.loadedText.text.length)
    }

    /**
     * And the reader must then actually render from that character. A resume that reports the right
     * offset but starts the viewport somewhere else is the same bug to the reader.
     */
    @Test
    fun u1_theReopenedViewportStartsAtTheResumedCharacter() {
        val text = sampleText()
        val anchor = 5_000
        val spec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(
            totalLength = text.length,
            textFitter = FakeTextFitter(text),
            initialSpec = spec,
            initialAnchor = anchor,
        )

        val layout = navigator.layoutFor(navigator.anchor, spec)

        assertEquals(anchor, navigator.anchor)
        assertEquals(anchor, layout.primaryPane.startOffset, "The first visible character must be the resumed one")
        assertTrue(layout.primaryPane.endOffset > anchor, "…and something must actually be rendered from there")
    }

    // ── U9 — a new file arriving must not disturb the open book ────────────

    /**
     * Files land in the home folder while the user is reading — Dropbox drops one in, or they copy
     * one over. Intake preprocesses and registers it, and the book already open must not move by a
     * character.
     *
     * The anchor is checked against the *reader's* state rather than the store, because the store is
     * exactly where intake writes: asserting there would prove nothing about what is on screen.
     */
    @Test
    fun u9_aFileArrivingMidReadLeavesTheOpenBookUntouched() {
        val text = sampleText()
        writeBook("open.txt", text)
        val navigator = ReaderNavigator(
            totalLength = text.length,
            textFitter = FakeTextFitter(text),
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 3_000,
        )
        val anchorBefore = navigator.anchor
        val historyBefore = navigator.historyStack.toList()

        val arrived = writeBook("arrived.txt", "새로 도착한 파일\r\n## 1장\r\n본문입니다.\r\n")
        val registered = IntakePipeline(homeFolder, bookStore).use { it.processSingleFile(arrived) }

        assertNotNull(registered, "The arrived file must be preprocessed and registered")
        assertNotNull(registered.preprocessedAt)
        assertNotNull(bookStore.findByKey("arrived.txt"), "It must show up in the library")

        assertEquals(anchorBefore, navigator.anchor, "Intake must not move the open book")
        assertEquals(historyBefore, navigator.historyStack, "…nor disturb its back history")
    }

    // ── U10 — one sign-in turns on both kinds of sync ──────────────────────

    /**
     * The whole reason Dropbox replaced the LAN server and the QR pairing: there is exactly one
     * thing to authenticate. Signing in must leave file sync *and* position sync both live, without
     * a second dialog, a scanned code, or a typed secret.
     *
     * Both are asserted off the same `CredentialsStore`, which is the point — one credential set,
     * two features.
     */
    @Test
    fun u10_asingleDropboxSignInEnablesBothFileAndPositionSync() {
        System.setProperty("flonovel.supabase.url", "https://example.supabase.co")
        System.setProperty("flonovel.supabase.publishable_key", "test-key")

        // Before: nothing is linked, so neither feature may claim to be on.
        val coordinator = ReadingPositionSyncCoordinator(
            credentialsStore = credentialsStore,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )
        assertFalse(coordinator.isSyncEnabled, "Position sync must be off before sign-in")
        assertFalse(settingsStore.load().sync.dropboxLinked, "File sync must be off before sign-in")

        // Sign-in: the refresh token arrives from OAuth, and the secret is read from the app folder
        // that same token unlocked (docs 06-SYNC-STRATEGY Part C). No second prompt in between.
        credentialsStore.save(
            Credentials(
                dropboxRefreshToken = "refresh-token",
                cachedSupabaseSecret = "secret-from-app-folder",
                verifiedSupabaseSecret = "secret-from-app-folder",
            ),
        )
        settingsStore.update { it.copy(sync = SyncSettings(dropboxLinked = true)) }

        assertTrue(coordinator.isSyncEnabled, "Position sync must come on with the same sign-in")
        assertTrue(settingsStore.load().sync.dropboxLinked, "File sync must come on with the same sign-in")
    }

    /**
     * A secret that has not passed a Supabase round trip must leave position sync off. It is
     * machine-generated, so it always *looks* right; without the check the app would report
     * "connected" while writing into a partition nobody reads.
     */
    @Test
    fun u10_anUnverifiedSecretLeavesPositionSyncOff() {
        System.setProperty("flonovel.supabase.url", "https://example.supabase.co")
        System.setProperty("flonovel.supabase.publishable_key", "test-key")
        credentialsStore.save(
            Credentials(
                dropboxRefreshToken = "refresh-token",
                cachedSupabaseSecret = "secret-from-app-folder",
                verifiedSupabaseSecret = null,
            ),
        )

        val coordinator = ReadingPositionSyncCoordinator(
            credentialsStore = credentialsStore,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )

        assertNull(coordinator.getClientOrNull())
        assertFalse(coordinator.isSyncEnabled)
    }

    // ── U11 — the phone read further, and the desktop offers to follow ─────

    /**
     * Read on the phone over lunch, come back to the PC: the desktop notices the remote position is
     * ahead, says so, and jumps only when told to.
     *
     * It must never jump on its own. A silent jump would throw away wherever the user actually was,
     * and there is no undo for that.
     */
    @Test
    fun u11_aFartherRemotePositionRaisesANoticeAndOnlyJumpsWhenAccepted() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            System.setProperty("flonovel.supabase.url", server.url("").toString())
            System.setProperty("flonovel.supabase.publishable_key", "test-key")
            credentialsStore.save(
                Credentials(cachedSupabaseSecret = "secret", verifiedSupabaseSecret = "secret"),
            )

            val text = sampleText()
            val localAnchor = 1_000
            val remoteOffset = 9_000
            val spec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
            val navigator = ReaderNavigator(
                totalLength = text.length,
                textFitter = FakeTextFitter(text),
                initialSpec = spec,
                initialAnchor = localAnchor,
            )

            // Captured through a holder: a plain `var` written from the callback cannot be
            // smart-cast afterwards, and the assertions below need the non-null value.
            val notices = mutableListOf<com.moonkata.flonovel.desktop.sync.RemotePositionNotice?>()
            val coordinator = ReadingPositionSyncCoordinator(
                credentialsStore = credentialsStore,
                coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            ).apply { onNoticeChanged = { notices += it } }

            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{"char_offset":$remoteOffset,"source":"android","encoding":"UTF-8"}]""",
                ),
            )
            // `onBookOpened` fires exactly this fetch, but returns before it lands and hands back no
            // handle to wait on. Driving it directly is what keeps this deterministic instead of
            // polling; that `onBookOpened` triggers it is covered by
            // `ReadingPositionSyncCoordinatorTest`.
            coordinator.currentBookKey = "novel/book.txt"
            coordinator.currentAnchor = localAnchor
            coordinator.triggerFetch(force = true)?.join()

            val notice = notices.lastOrNull()
            assertNotNull(notice, "A remote position this far ahead must be surfaced")
            assertEquals(remoteOffset, notice.charOffset)
            assertEquals("android", notice.source)
            assertEquals(remoteOffset - localAnchor, notice.delta)
            assertEquals(localAnchor, navigator.anchor, "Nothing may move before the user accepts")

            // Accepting — in the app this is the keyboard confirm on the banner.
            navigator.jumpTo(notice.charOffset)
            coordinator.onAnchorChanged(navigator.anchor)
            coordinator.dismissNotice()

            assertEquals(remoteOffset, navigator.anchor, "Accepting must land on the exact remote character")
            assertEquals(remoteOffset, navigator.layoutFor(navigator.anchor, spec).primaryPane.startOffset)
            assertNull(coordinator.activeNotice, "The banner must clear once acted on")
        } finally {
            server.shutdown()
        }
    }

    /**
     * A remote position only slightly ahead — the phone caught the same paragraph — must stay quiet.
     * A banner for every few characters would train the user to dismiss it without reading.
     */
    @Test
    fun u11_aRemotePositionWithinTheThresholdRaisesNoNotice() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            System.setProperty("flonovel.supabase.url", server.url("").toString())
            System.setProperty("flonovel.supabase.publishable_key", "test-key")
            credentialsStore.save(
                Credentials(cachedSupabaseSecret = "secret", verifiedSupabaseSecret = "secret"),
            )

            val localAnchor = 1_000
            val justUnder = localAnchor + ReadingPositionSyncCoordinator.NOTIFICATION_THRESHOLD
            val coordinator = ReadingPositionSyncCoordinator(
                credentialsStore = credentialsStore,
                coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            )

            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{"char_offset":$justUnder,"source":"android","encoding":"UTF-8"}]""",
                ),
            )
            coordinator.currentBookKey = "novel/book.txt"
            coordinator.currentAnchor = localAnchor
            coordinator.triggerFetch(force = true)?.join()

            assertNull(coordinator.activeNotice, "A delta at exactly the threshold must not notify")
        } finally {
            server.shutdown()
        }
    }

    // ── U12 — offline is the normal case, not a degraded one ───────────────

    /**
     * The reader is offline-first. With no network configured at all, reading, saving the position,
     * and resuming must be untouched — sync is the only thing that goes quiet.
     *
     * "No network" is modelled as no Supabase configuration, which is what an unconfigured or
     * disconnected client actually looks like to the coordinator: `getClientOrNull()` returns null
     * and nothing is attempted.
     */
    @Test
    fun u12_withNoNetworkReadingSavingAndResumingAllStillWork() {
        System.clearProperty("flonovel.supabase.url")
        System.clearProperty("flonovel.supabase.publishable_key")

        val text = sampleText()
        writeBook("offline.txt", text)
        val coordinator = ReadingPositionSyncCoordinator(
            credentialsStore = credentialsStore,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )
        assertFalse(coordinator.isSyncEnabled, "Sync is expected to be off — that is the premise")

        // Read: navigation is pure local computation and must not care.
        val navigator = ReaderNavigator(
            totalLength = text.length,
            textFitter = FakeTextFitter(text),
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 0,
        )
        navigator.advance(ratio = 0.5f)
        navigator.advance(ratio = 0.5f)
        val anchorAfterReading = navigator.anchor
        assertTrue(anchorAfterReading > 0, "Reading must actually move while offline")

        // Save: the position goes to local disk, with no network in the path.
        bookStore.addOrUpdate(
            BookRecord(
                path = "offline.txt",
                key = "offline.txt",
                displayName = "offline.txt",
                sizeBytes = Files.size(homeFolder.resolve("offline.txt")),
                totalCharCount = text.length,
                detectedEncoding = "UTF-8",
                anchor = anchorAfterReading,
                progress = anchorAfterReading.toDouble() / text.length,
                lastOpenedAt = 1_000L,
                preprocessedAt = 900L,
            ),
        )
        bookStore.flush()
        settingsStore.save(Settings(homeFolder = homeFolder.toString(), lastOpenedBookKey = "offline.txt"))

        // Resume: still exact.
        val target = ResumeManager.findResumeTarget(
            homeFolder,
            SettingsStore(tempDir.resolve("settings.json")).load(),
            BookStore(tempDir.resolve("books.json")).use { it.load() },
        )
        assertNotNull(target)
        assertEquals(anchorAfterReading, target.clampedAnchor, "Offline resume must be exact too")

        // And the sync calls that the reader makes anyway must be no-ops, not crashes.
        coordinator.onBookOpened("offline.txt", anchorAfterReading, "UTF-8")
        coordinator.onAnchorChanged(anchorAfterReading + 10)
        coordinator.onWindowFocusLost()
        coordinator.onWindowFocusGained()
        coordinator.onBookClosed()
        assertNull(coordinator.activeNotice)
    }

    // ── U13 — the reader never writes to the book ──────────────────────────

    /**
     * The reader is a reader. Opening a book, moving through it, jumping, and closing it must leave
     * the file byte-for-byte identical — these are the user's novels, and there is no undo.
     *
     * Bytes are compared, not text: a re-encode that preserved every character but changed the
     * encoding or the line endings would still be a modification, and the phone would then see a
     * different size and re-download the whole file.
     */
    @Test
    fun u13_readingABookNeverModifiesTheFile() {
        val text = sampleText()
        val file = writeBook("untouched.txt", text)
        val digestBefore = sha256(file)
        val sizeBefore = Files.size(file)
        val modifiedBefore = Files.getLastModifiedTime(file)

        val navigator = ReaderNavigator(
            totalLength = text.length,
            textFitter = FakeTextFitter(text),
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 0,
        )
        repeat(5) { navigator.advance(ratio = 0.5f) }
        navigator.retreat()
        navigator.jumpTo(text.length / 2)
        navigator.onLayoutKeyChanged(ViewportSpec(widthPx = 400, heightPx = 900, paneMode = PaneMode.TWO))
        navigator.advance(ratio = 1.0f)

        assertEquals(digestBefore, sha256(file), "Reading must not change a single byte")
        assertEquals(sizeBefore, Files.size(file))
        assertEquals(modifiedBefore, Files.getLastModifiedTime(file), "…nor even touch the mtime")
    }

    /**
     * The position is recorded next to the library, never inside the book. Writing progress into the
     * file itself would corrupt the novel and make its size drift, which the file sync reads as
     * "changed on the PC".
     */
    @Test
    fun u13_savingThePositionWritesToTheLibraryNotTheBook() {
        val text = sampleText()
        val file = writeBook("untouched.txt", text)
        val digestBefore = sha256(file)

        bookStore.addOrUpdate(
            BookRecord(
                path = "untouched.txt",
                key = "untouched.txt",
                displayName = "untouched.txt",
                sizeBytes = Files.size(file),
                totalCharCount = text.length,
                detectedEncoding = "UTF-8",
                anchor = 1_234,
                progress = 0.1,
                lastOpenedAt = 1_000L,
                preprocessedAt = 900L,
            ),
        )
        bookStore.flush()

        assertEquals(digestBefore, sha256(file))
        assertEquals(1_234, bookStore.findByKey("untouched.txt")?.anchor)
    }
}

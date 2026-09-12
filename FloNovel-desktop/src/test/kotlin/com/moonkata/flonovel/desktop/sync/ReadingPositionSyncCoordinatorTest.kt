package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReadingPositionSyncCoordinatorTest {

    private lateinit var server: MockWebServer
    private var simulatedTime = 1_000_000L
    private val tempDir = Files.createTempDirectory("coord_test")
    private val credFile = tempDir.resolve("credentials.json")
    private lateinit var credentialsStore: CredentialsStore

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()

        System.setProperty("flonovel.supabase.url", server.url("").toString())
        System.setProperty("flonovel.supabase.publishable_key", "test_key_abc")

        credentialsStore = CredentialsStore(credFile)
        credentialsStore.save(
            Credentials(
                cachedSupabaseSecret = "secret_valid_123",
                verifiedSupabaseSecret = "secret_valid_123", // verified!
            )
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        Files.deleteIfExists(credFile)
        Files.deleteIfExists(tempDir)
        System.clearProperty("flonovel.supabase.url")
        System.clearProperty("flonovel.supabase.publishable_key")
    }

    private fun createCoordinator(): ReadingPositionSyncCoordinator {
        return ReadingPositionSyncCoordinator(
            credentialsStore = credentialsStore,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            clock = { simulatedTime },
        )
    }

    @Test
    fun a1_notification_triggers_when_remote_minus_local_greater_than_500() = runBlocking {
        // remote is 1600, local is 1000 -> diff is 600 (> 500)
        val responseBody = """[{"char_offset": 1600, "source": "android", "encoding": "UTF-8"}]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val coordinator = createCoordinator()
        coordinator.currentAnchor = 1000
        coordinator.currentBookKey = "novel.txt"
        coordinator.triggerFetch(force = true)?.join()

        val notice = coordinator.activeNotice
        assertNotNull(notice, "A1: Notification must be shown when remote - local > 500")
        assertEquals(1600, notice.charOffset)
        assertEquals(600, notice.delta)
        assertEquals("android", notice.source)
    }

    @Test
    fun a2_no_notification_when_remote_minus_local_equals_500() = runBlocking {
        // remote is 1500, local is 1000 -> diff is 500 (not > 500)
        val responseBody = """[{"char_offset": 1500, "source": "android", "encoding": "UTF-8"}]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val coordinator = createCoordinator()
        coordinator.currentAnchor = 1000
        coordinator.currentBookKey = "novel.txt"
        coordinator.triggerFetch(force = true)?.join()

        assertNull(coordinator.activeNotice, "A2: No notification when remote - local == 500 (strict inequality)")
    }

    @Test
    fun a3_no_notification_when_remote_is_less_than_local() = runBlocking {
        // remote is 800, local is 1000 -> remote lags behind local
        val responseBody = """[{"char_offset": 800, "source": "android", "encoding": "UTF-8"}]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val coordinator = createCoordinator()
        coordinator.currentAnchor = 1000
        coordinator.currentBookKey = "novel.txt"
        coordinator.triggerFetch(force = true)?.join()

        assertNull(coordinator.activeNotice, "A3: Do nothing when remote is behind local (remote < local)")
    }

    @Test
    fun a4_fetch_cooldown_prevents_requests_within_30_seconds() = runBlocking {
        val responseBody = """[{"char_offset": 1200, "source": "android", "encoding": "UTF-8"}]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val coordinator = createCoordinator()
        coordinator.currentAnchor = 1000
        coordinator.currentBookKey = "novel.txt"

        // First fetch at t = 1000000
        coordinator.triggerFetch(force = false)?.join()
        assertEquals(1, server.requestCount, "First fetch should send request")

        // Second fetch within 20 seconds (t = 1020000 < 1030000)
        simulatedTime += 20_000L
        val suppressedJob = coordinator.triggerFetch(force = false)
        assertNull(suppressedJob, "Should return null when fetch is suppressed by cooldown")
        assertEquals(1, server.requestCount, "A4: No request should be sent within 30s cooldown")

        // Third fetch after 35 seconds total elapsed (simulatedTime = 1035000)
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        simulatedTime += 15_000L
        coordinator.triggerFetch(force = false)?.join()
        assertEquals(2, server.requestCount, "A4: Request should be sent after cooldown has passed")
    }

    @Test
    fun f1_forcePush_deletes_then_inserts_to_bypass_max_wins_clamp() = runBlocking {
        // DELETE succeeds, then upsert (POST) succeeds
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val coordinator = createCoordinator()
        coordinator.currentAnchor = 250
        coordinator.currentBookKey = "novel.txt"
        coordinator.currentEncoding = "UTF-8"

        val outcome = coordinator.forcePush()

        assertEquals(2, server.requestCount, "F1: forcePush must send exactly DELETE then POST")
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        val insertRequest = server.takeRequest()
        assertEquals("POST", insertRequest.method)

        assertEquals(ForcePushOutcome.Success(250), outcome)
        assertEquals(250, coordinator.lastPushedOffset, "F1: lastPushedOffset must reflect the force-pushed value")
    }

    @Test
    fun f2_forcePush_returns_noBookOpen_when_no_book_is_open() = runBlocking {
        val coordinator = createCoordinator()

        val outcome = coordinator.forcePush()

        assertEquals(ForcePushOutcome.NoBookOpen, outcome)
        assertEquals(0, server.requestCount, "F2: No request should be sent when no book is open")
    }

    @Test
    fun f3_forcePush_returns_syncNotAvailable_when_secret_unverified() = runBlocking {
        credentialsStore.save(
            Credentials(
                cachedSupabaseSecret = "secret_valid_123",
                verifiedSupabaseSecret = null, // unverified
            )
        )
        val coordinator = createCoordinator()
        coordinator.currentBookKey = "novel.txt"
        coordinator.currentAnchor = 100

        val outcome = coordinator.forcePush()

        assertEquals(ForcePushOutcome.SyncNotAvailable, outcome)
        assertEquals(0, server.requestCount, "F3: No request should be sent when secret is unverified")
    }

    @Test
    fun f4_forcePush_reports_failure_when_delete_fails() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val coordinator = createCoordinator()
        coordinator.currentAnchor = 100
        coordinator.currentBookKey = "novel.txt"

        val outcome = coordinator.forcePush()

        assertEquals(1, server.requestCount, "F4: upsert must not be attempted when delete fails")
        assert(outcome is ForcePushOutcome.Failure) { "F4: Failure must be surfaced when delete fails" }
        assertNull(coordinator.lastPushedOffset, "F4: lastPushedOffset must not be updated on failure")
    }
}

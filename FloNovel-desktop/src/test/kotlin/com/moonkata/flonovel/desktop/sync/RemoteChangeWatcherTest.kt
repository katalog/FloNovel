package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class RemoteChangeWatcherTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DropboxClient
    private var changes = 0

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        val creds = CredentialsStore(Files.createTempDirectory("longpoll").resolve("credentials.json"))
        creds.save(Credentials(dropboxRefreshToken = "t"))
        val base = server.url("").toString().removeSuffix("/")
        client = DropboxClient(appKey = "k", credentialsStore = creds, apiBaseUrl = base, contentBaseUrl = base, notifyBaseUrl = base)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun watcher(cursor: String? = "c1") = RemoteChangeWatcher(client, { cursor }, { changes++ })

    private fun respond(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test
    fun change_isReported_andRequestCarriesCursorWithoutAuthorization() {
        respond("""{"changes": true}""")

        val wait = watcher().pollOnce()

        assertEquals(1, changes)
        assertEquals(RemoteChangeWatcher.AFTER_CHANGE_WAIT_MS, wait)
        val request = server.takeRequest()
        assertEquals("/2/files/list_folder/longpoll", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("c1", org.json.JSONObject(request.body.readUtf8()).getString("cursor"))
    }

    @Test
    fun noChange_honoursBackoff() {
        respond("""{"changes": false, "backoff": 7}""")
        assertEquals(7_000L, watcher().pollOnce())
        assertEquals(0, changes)
    }

    @Test
    fun invalidCursor_isReportedAsChange_soSyncRelists() {
        respond("""{"error_summary": "reset/", "error": {".tag": "reset"}}""", code = 409)
        watcher().pollOnce()
        assertEquals(1, changes)
    }

    @Test
    fun noCursorYet_doesNotPoll() {
        assertEquals(RemoteChangeWatcher.NO_CURSOR_WAIT_MS, watcher(cursor = null).pollOnce())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun failure_waitsBeforeRetrying() {
        respond("oops", code = 500)
        assertEquals(RemoteChangeWatcher.FAILURE_WAIT_MS, watcher().pollOnce())
    }

    @Test
    fun sameCursorReportedAgain_waitsLongerEachTime() {
        // The sync after a change could not finish (e.g. the book is open), so the cursor did not move.
        val w = watcher()
        repeat(6) { respond("""{"changes": true}""") }

        val waits = List(6) { w.pollOnce() }

        assertEquals(RemoteChangeWatcher.AFTER_CHANGE_WAIT_MS, waits[0])
        assertEquals(RemoteChangeWatcher.REPEAT_FIRST_WAIT_MS, waits[1])
        assertEquals(RemoteChangeWatcher.REPEAT_FIRST_WAIT_MS * 2, waits[2])
        assertEquals(RemoteChangeWatcher.REPEAT_MAX_WAIT_MS, waits[5])
    }
}

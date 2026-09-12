package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingPositionSyncClientTest {

    private lateinit var server: MockWebServer
    private val apiKey = "test_supabase_pubkey_xyz"
    private val secret = "test_secret_48_hex_characters_long_1234567890abcdef"

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun createClient(): ReadingPositionSyncClient {
        val baseUrl = server.url("").toString()
        return ReadingPositionSyncClient(
            baseUrl = baseUrl,
            publishableKey = apiKey,
            sharedSecret = secret,
        )
    }

    @Test
    fun s1_s2_s3_s4_s5_upsert_request_shape_is_strictly_compliant() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val client = createClient()
        client.upsert(
            relativePath = "Fantasy/Novel1.txt",
            charOffset = 12345,
            encoding = "UTF-8",
        )

        val request = server.takeRequest()

        // S1: Headers contain apikey and x-flonovel-secret
        assertEquals(apiKey, request.getHeader("apikey"), "S1: Must contain apikey header")
        assertEquals(secret, request.getHeader("x-flonovel-secret"), "S1: Must contain x-flonovel-secret header")

        // S2: Authorization header MUST NOT be present
        assertNull(request.getHeader("Authorization"), "S2: Authorization header must NEVER be set (causes JWT failure)")

        // S3: Prefer header must specify resolution=merge-duplicates
        assertEquals("resolution=merge-duplicates", request.getHeader("Prefer"), "S3: Must include Prefer: resolution=merge-duplicates")

        // S4 & S5: Request body JSON verification
        val bodyJson = JSONObject(request.body.readUtf8())
        assertFalse(bodyJson.has("user_key"), "S4: Body must NEVER contain user_key (calculated by server trigger)")
        assertEquals("desktop", bodyJson.getString("source"), "S5: source must be 'desktop'")
        assertEquals("Fantasy/Novel1.txt", bodyJson.getString("relative_path"))
        assertEquals(12345, bodyJson.getInt("char_offset"))
        assertEquals("UTF-8", bodyJson.getString("encoding"))

        // Path should target flonovel_sync
        assertEquals("/rest/v1/flonovel_sync", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun s6_testConnection_posts_to_connection_test_path() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val client = createClient()
        val result = client.testConnection()

        assertTrue(result, "200 response should mean successful connection")
        val request = server.takeRequest()

        assertEquals("POST", request.method, "S6: Connection test must be POST (write), not GET")
        val bodyJson = JSONObject(request.body.readUtf8())
        assertEquals("__connection_test__", bodyJson.getString("relative_path"), "S6: relative_path must be __connection_test__")
        assertEquals(0, bodyJson.getInt("char_offset"))
        assertEquals("desktop", bodyJson.getString("source"))
    }

    @Test
    fun s7_push_does_not_perform_get_before_pushing() = runBlocking {
        // S7 is critical: Verify that client unconditionally upserts without querying remote first.
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val client = createClient()
        client.upsert("novels/test.txt", 500, "UTF-8")

        assertEquals(1, server.requestCount, "Exactly 1 request must be sent during upsert")
        val request = server.takeRequest()
        assertEquals("POST", request.method, "S7: Only POST was sent, no prior GET")
    }

    @Test
    fun s8_network_failures_do_not_throw_exceptions() = runBlocking {
        // Shut down server to simulate total network failure
        server.shutdown()

        val client = createClient()

        // Neither fetch nor upsert should throw an exception
        val fetched = client.fetch("novels/missing.txt")
        assertNull(fetched, "Fetch should return null on network error without throwing")
        assertNotNull(client.lastSyncError, "Sync error should be recorded")

        // Upsert should not throw
        client.upsert("novels/novel.txt", 100, "UTF-8")
        assertNotNull(client.lastSyncError)
    }

    @Test
    fun s10_delete_sends_delete_request_scoped_to_relative_path() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val client = createClient()
        val result = client.delete("Fantasy/Novel1.txt")

        assertTrue(result, "204 response should mean successful delete")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(apiKey, request.getHeader("apikey"))
        assertEquals(secret, request.getHeader("x-flonovel-secret"))
        assertNull(request.getHeader("Authorization"), "Authorization header must NEVER be set")
        assertTrue(request.path?.contains("relative_path=eq.Fantasy%2FNovel1.txt") == true, "delete must scope to the relative_path")
    }

    @Test
    fun s11_delete_failure_does_not_throw_and_records_error() = runBlocking {
        // Use an isolated server so shutting it down here doesn't collide with
        // the shared `server`'s own shutdown in tearDown() (double-shutdown of
        // the same MockWebServer crashes the test worker silently).
        val deadServer = MockWebServer()
        deadServer.start()
        val client = ReadingPositionSyncClient(
            baseUrl = deadServer.url("").toString(),
            publishableKey = apiKey,
            sharedSecret = secret,
        )
        deadServer.shutdown()

        val result = client.delete("novels/novel.txt")

        assertFalse(result, "Delete should return false on network error without throwing")
        assertNotNull(client.lastSyncError, "Sync error should be recorded")
        Unit
    }

    @Test
    fun s9_unverified_secret_prevents_requests_from_being_sent() = runBlocking {
        val tempFile = Files.createTempFile("test_credentials", ".json")
        try {
            val credentialsStore = CredentialsStore(tempFile)
            // Cached secret exists, but verifiedSupabaseSecret is null or different
            credentialsStore.save(
                Credentials(
                    cachedSupabaseSecret = "secret_abc",
                    verifiedSupabaseSecret = null, // unverified!
                )
            )

            var clientCreated = false
            val coordinator = ReadingPositionSyncCoordinator(
                credentialsStore = credentialsStore,
                coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                clientFactory = { _, _, _ ->
                    clientCreated = true
                    createClient()
                },
            )

            assertFalse(coordinator.isSyncEnabled, "Sync should be disabled when secret is unverified")
            assertNull(coordinator.getClientOrNull(), "getClientOrNull must return null when unverified")

            coordinator.onBookOpened("test.txt", 100, "UTF-8")
            coordinator.triggerPush()

            // Verify no client was created and 0 requests were sent to the server
            assertFalse(clientCreated, "Client should never be created when secret is unverified")
            assertEquals(0, server.requestCount, "S9: 0 requests sent when secret is unverified")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}

package com.moonkata.flonovel.android.data.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the actual shape of the requests `ReadingPositionSyncClient` sends to Supabase
 * PostgREST (path/headers/body) and its response parsing, using a local fake server
 * (MockWebServer) — checks only the protocol contract, without a real Supabase project.
 *
 * A plain JVM test on purpose. It used to sit in androidTest, which meant the contract it enforces
 * (never send `user_key`, never send `Authorization`) was only checked on a device that had never
 * been run — so `./gradlew testDebugUnitTest` proved nothing about it. Found by review, T-26.
 */
class ReadingPositionSyncClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ReadingPositionSyncClient

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        client = ReadingPositionSyncClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "test-publishable-key",
            sharedSecret = "test-shared-secret",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetch_parsesTheFirstRowOfANonEmptyArray() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"char_offset":1234,"source":"vscode","encoding":"UTF-8"}]"""
            )
        )

        val result = runBlocking { client.fetch("folder/book.txt") }

        assertEquals(1234, result?.charOffset)
        assertEquals("vscode", result?.source)
        assertEquals("UTF-8", result?.encoding)
    }

    @Test
    fun fetch_treatsNullJsonEncodingAsNullEncoding() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"char_offset":0,"source":"android","encoding":null}]"""
            )
        )

        val result = runBlocking { client.fetch("book.txt") }

        assertNull(result?.encoding)
    }

    @Test
    fun fetch_returnsNullWhenNoRowMatches() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val result = runBlocking { client.fetch("book.txt") }

        assertNull(result)
    }

    @Test
    fun fetch_returnsNullOnServerError_insteadOfThrowing() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = runBlocking { client.fetch("book.txt") }

        assertNull(result)
    }

    @Test
    fun fetch_sendsApikeyAndSecretHeaders_andUrlEncodesTheRelativePath() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        runBlocking { client.fetch("폴더/책 1.txt") }

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("test-publishable-key", request.getHeader("apikey"))
        assertEquals("test-shared-secret", request.getHeader("x-flonovel-secret"))
        assertTrue(
            "The encoded relative path must be included in the query: ${request.path}",
            request.path?.contains("relative_path=eq.") == true && request.path?.contains(" ") != true,
        )
    }

    @Test
    fun upsert_sendsRelativePathOffsetSourceAndEncodingAsJson_withMergeDuplicatesPreferHeader() {
        server.enqueue(MockResponse().setResponseCode(201))

        runBlocking { client.upsert("folder/book.txt", 4321, "EUC-KR") }

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("resolution=merge-duplicates", request.getHeader("Prefer"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("folder/book.txt", body.getString("relative_path"))
        assertEquals(4321, body.getInt("char_offset"))
        assertEquals("android", body.getString("source"))
        assertEquals("EUC-KR", body.getString("encoding"))
    }

    @Test
    fun upsert_sendsJsonNull_whenEncodingIsUnknown() {
        server.enqueue(MockResponse().setResponseCode(201))

        runBlocking { client.upsert("book.txt", 0, null) }

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.isNull("encoding"))
    }

    @Test
    fun testConnection_returnsTrueForA2xxResponse() {
        server.enqueue(MockResponse().setResponseCode(201))

        assertTrue(runBlocking { client.testConnection() })
    }

    @Test
    fun testConnection_returnsFalseWhenTheSecretIsRejected() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertFalse(runBlocking { client.testConnection() })
    }

    /**
     * In real usage, the GitHub secret SUPABASE_URL was once registered incorrectly as
     * "https://xxx.supabase.co/rest/v1" (including the trailing /rest/v1) — the client then
     * appended the table path again, duplicating it (".../rest/v1/rest/v1/..."), which Supabase
     * rejected with "PGRST125: invalid path". Whitespace or a newline pasted onto the end of
     * baseUrl breaks the request line just as thoroughly. Either way the path must come out as
     * exactly one "/rest/v1/flonovel_sync".
     */
    @Test
    fun restBase_stripsDuplicateRestV1SuffixAndWhitespace_fromBaseUrl() {
        val messyClient = ReadingPositionSyncClient(
            baseUrl = server.url("/").toString().trimEnd('/') + "/rest/v1 \n",
            publishableKey = "test-publishable-key",
            sharedSecret = "test-shared-secret",
        )
        server.enqueue(MockResponse().setResponseCode(201))

        assertTrue(runBlocking { messyClient.testConnection() })

        val request = server.takeRequest()
        assertTrue(
            "The path must be exactly /rest/v1/$SUPABASE_TABLE once (no duplication/whitespace): ${request.path}",
            request.path?.startsWith("/rest/v1/$SUPABASE_TABLE") == true,
        )
        assertFalse("'/rest/v1' must not remain duplicated: ${request.path}", request.path?.contains("v1/rest") == true)
    }

    // ── T-26: the table, and the contract shared with the Desktop client ───

    /**
     * The rename from `reading_positions`. Desktop already writes to `flonovel_sync`; if the
     * two ever disagree, both apps keep working and simply never see each other's positions — a
     * failure with no error message anywhere.
     */
    @Test
    fun upsertTargetsTheFloNovelSyncTable() {
        server.enqueue(MockResponse().setResponseCode(201))

        runBlocking { client.upsert("folder/book.txt", 10, null) }

        val path = server.takeRequest().path
        assertEquals("/rest/v1/flonovel_sync", path)
    }

    @Test
    fun fetchTargetsTheFloNovelSyncTable() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        runBlocking { client.fetch("folder/book.txt") }

        val path = server.takeRequest().path
        assertTrue("fetch path was $path", path?.startsWith("/rest/v1/flonovel_sync?") == true)
    }

    /**
     * Supabase parses `Authorization` as a JWT and rejects the whole request when it is not one. The
     * publishable key belongs in `apikey` alone.
     */
    @Test
    fun noAuthorizationHeaderIsEverSent() {
        server.enqueue(MockResponse().setResponseCode(201))

        runBlocking { client.upsert("folder/book.txt", 10, null) }

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    /**
     * `user_key` is derived server-side as `hex(sha256(x-flonovel-secret))` and is what separates
     * one user's rows from another's. A client that sent its own value would be choosing its own
     * partition — and could land in someone else's.
     */
    @Test
    fun theClientNeverSendsUserKey() {
        server.enqueue(MockResponse().setResponseCode(201))

        runBlocking { client.upsert("folder/book.txt", 10, "UTF-8") }

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse("user_key must be left to the server trigger", body.has("user_key"))
        assertEquals(
            "Only these four fields belong in the body",
            setOf("relative_path", "char_offset", "source", "encoding"),
            body.keys().asSequence().toSet(),
        )
    }

    /**
     * With a blank secret the server hashes the empty string into a perfectly valid-looking
     * user_key, so the write succeeds and lands on a partition shared with every other
     * misconfigured client. Failing before the request is the only way to see that.
     */
    @Test
    fun aBlankSecretFailsWithoutSendingAnything() {
        val secretless = ReadingPositionSyncClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "test-publishable-key",
            sharedSecret = "",
        )

        assertFalse(runBlocking { secretless.testConnection() })
        assertEquals("No request may be sent", 0, server.requestCount)
        assertEquals("Secret is empty", secretless.lastTestConnectionError)
    }
}

package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretManagerTest {

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

    private fun createClient(store: CredentialsStore): DropboxClient {
        val client = DropboxClient(
            appKey = "test_key",
            credentialsStore = store,
            apiBaseUrl = server.url("").toString().removeSuffix("/"),
            contentBaseUrl = server.url("").toString().removeSuffix("/"),
        )
        client.setAccessToken("test_access_token", 3600)
        return client
    }

    @Test
    fun existingSecretOnDropbox_downloadsAndCachesLocally() {
        val tempDir = Files.createTempDirectory("secret_test_1")
        try {
            val credsFile = tempDir.resolve("credentials.json")
            val store = CredentialsStore(credsFile)
            store.save(Credentials(dropboxRefreshToken = "mock_refresh_token"))

            val client = createClient(store)

            val remoteSecret = "0123456789abcdef0123456789abcdef0123456789abcdef"
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody("""{"secret": "$remoteSecret", "createdAt": 1725600000000}""")
            )

            val manager = SecretManager(client, store, "secret.json")
            val secret = manager.fetchOrInitializeSecret()

            assertEquals(remoteSecret, secret)
            assertEquals(remoteSecret, store.load().cachedSupabaseSecret)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/2/files/download", recorded.path)
            assertTrue(recorded.getHeader("Dropbox-API-Arg")?.contains("/.flonovel/secret.json") == true)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingSecretOnDropbox_generates24BytesAndUploads() {
        val tempDir = Files.createTempDirectory("secret_test_2")
        try {
            val credsFile = tempDir.resolve("credentials.json")
            val store = CredentialsStore(credsFile)
            store.save(Credentials(dropboxRefreshToken = "mock_refresh_token"))

            val client = createClient(store)

            // 1. Download returns 409 path not found
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error_summary": "path/not_found/..."}""")
            )

            // 2. Upload returns 200 success
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/.flonovel/secret.json", "size": 90}""")
            )

            val manager = SecretManager(client, store, "secret.json")
            val secret = manager.fetchOrInitializeSecret()

            assertNotNull(secret)
            // 24 bytes = 48 hex characters
            assertEquals(48, secret.length)
            assertTrue(secret.all { it in "0123456789abcdef" })
            assertEquals(secret, store.load().cachedSupabaseSecret)

            assertEquals(2, server.requestCount)
            val req1 = server.takeRequest() // download
            assertEquals("/2/files/download", req1.path)
            val req2 = server.takeRequest() // upload
            assertEquals("/2/files/upload", req2.path)
            assertTrue(req2.getHeader("Dropbox-API-Arg")?.contains("/.flonovel/secret.json") == true)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun devMode_usesSecretDevJson() {
        val tempDir = Files.createTempDirectory("secret_test_dev")
        try {
            val credsFile = tempDir.resolve("credentials.json")
            val store = CredentialsStore(credsFile)
            store.save(Credentials(dropboxRefreshToken = "mock_refresh_token"))

            val client = createClient(store)

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody("""{"secret": "dev_secret_hex_value_1234", "createdAt": 1725600000000}""")
            )

            val manager = SecretManager(client, store, secretFileName = "secret-dev.json")
            val secret = manager.fetchOrInitializeSecret()

            assertEquals("dev_secret_hex_value_1234", secret)
            val recorded = server.takeRequest()
            assertTrue(recorded.getHeader("Dropbox-API-Arg")?.contains("/.flonovel/secret-dev.json") == true)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun regenerateSecret_explicitUserAction_createsNewSecret() {
        val tempDir = Files.createTempDirectory("secret_test_regen")
        try {
            val credsFile = tempDir.resolve("credentials.json")
            val store = CredentialsStore(credsFile)
            val oldSecret = "old_secret_0123456789abcdef0123456789abcdef0123"
            store.save(Credentials(dropboxRefreshToken = "mock_refresh_token", cachedSupabaseSecret = oldSecret))

            val client = createClient(store)

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"path_display": "/.flonovel/secret.json", "size": 90}""")
            )

            val manager = SecretManager(client, store, "secret.json")
            val newSecret = manager.regenerateSecret()

            assertNotNull(newSecret)
            assertNotEquals(oldSecret, newSecret)
            assertEquals(48, newSecret.length)
            assertEquals(newSecret, store.load().cachedSupabaseSecret)

            val recorded = server.takeRequest()
            assertEquals("/2/files/upload", recorded.path)
            assertTrue(recorded.getHeader("Dropbox-API-Arg")?.contains("\"mode\":\"overwrite\"") == true)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

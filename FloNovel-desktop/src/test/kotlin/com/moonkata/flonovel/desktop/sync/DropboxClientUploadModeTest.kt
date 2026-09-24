package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.Credentials
import com.moonkata.flonovel.desktop.library.CredentialsStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `overwrite` used to be the default upload mode, so a caller that forgot to pass a mode would
 * silently discard another device's change (AGENTS.md §1). These pin down which mode each call
 * shape actually sends.
 */
class DropboxClientUploadModeTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = Files.createTempDirectory("dropbox_upload_mode")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        tempDir.toFile().deleteRecursively()
    }

    private fun uploadAndCaptureArg(upload: (DropboxClient) -> Unit): String {
        val baseUrl = server.url("").toString().removeSuffix("/")
        // Without a stored refresh token the client counts as unlinked and sends nothing.
        val store = CredentialsStore(tempDir.resolve("credentials.json"))
        store.save(Credentials(dropboxRefreshToken = "mock_refresh_token"))
        val client = DropboxClient(
            appKey = "test_key",
            credentialsStore = store,
            apiBaseUrl = baseUrl,
            contentBaseUrl = baseUrl,
        )
        client.setAccessToken("test_access_token", 3600)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"path_display":"/books/a.txt","rev":"r1"}"""))
        upload(client)
        val request = assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "upload request was never sent")
        return request.getHeader("Dropbox-API-Arg").orEmpty()
    }

    @Test
    fun noModeGiven_uploadsAsAdd_neverOverwrite() {
        val arg = uploadAndCaptureArg { it.uploadFile("/books/a.txt", "x".toByteArray()) }
        assertTrue(arg.contains("\"mode\":\"add\""), arg)
    }

    @Test
    fun updateRevGiven_uploadsAsUpdateAgainstThatRev() {
        val arg = uploadAndCaptureArg { it.uploadFile("/books/a.txt", "x".toByteArray(), updateRev = "r0") }
        assertTrue(arg.contains("\"update\":\"r0\""), arg)
        assertTrue(!arg.contains("overwrite"), arg)
    }

    @Test
    fun overwriteOnlyWhenAskedForByName() {
        val arg = uploadAndCaptureArg { it.uploadFile("/.flonovel/secret.json", "x".toByteArray(), overwrite = true) }
        assertTrue(arg.contains("\"mode\":\"overwrite\""), arg)
    }
}

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DropboxOAuthTest {

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

    @Test
    fun pkce_verifierAndChallenge_correctFormat() {
        val verifier = DropboxOAuth.generateCodeVerifier()
        assertTrue(verifier.length >= 43, "Verifier should be at least 43 characters")
        assertFalse(verifier.contains("="), "URL-safe Base64 verifier must not have padding")
        assertFalse(verifier.contains("+"), "Must not contain +")
        assertFalse(verifier.contains("/"), "Must not contain /")

        val challenge = DropboxOAuth.generateCodeChallenge(verifier)
        assertTrue(challenge.isNotBlank())
        assertFalse(challenge.contains("="), "Challenge must not have padding")
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))

        // Distinct verifiers on multiple calls
        val verifier2 = DropboxOAuth.generateCodeVerifier()
        assertFalse(verifier == verifier2)
    }

    @Test
    fun buildAuthorizeUrl_containsAllRequiredParameters() {
        val appKey = "test_app_key_123"
        val challenge = "sample_challenge_abc"
        val redirectUri = "http://localhost:52475/oauth/callback"

        val url = DropboxOAuth.buildAuthorizeUrl(appKey, challenge, redirectUri)

        assertTrue(url.startsWith("https://www.dropbox.com/oauth2/authorize?"))
        assertTrue(url.contains("client_id=test_app_key_123"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge=sample_challenge_abc"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A52475%2Foauth%2Fcallback"))
        assertTrue(url.contains("token_access_type=offline"))
    }

    @Test
    fun exchangeCodeForTokens_successfulExchange() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "access_token": "mock_access_token_xyz",
                        "token_type": "bearer",
                        "expires_in": 14400,
                        "refresh_token": "mock_refresh_token_abc",
                        "account_id": "dbid:12345"
                    }
                    """.trimIndent()
                )
        )

        val tokens = DropboxOAuth.exchangeCodeForTokens(
            code = "auth_code_999",
            codeVerifier = "verifier_111",
            appKey = "app_key_222",
            tokenEndpoint = server.url("/oauth2/token").toString(),
        )

        assertEquals("mock_access_token_xyz", tokens.accessToken)
        assertEquals("mock_refresh_token_abc", tokens.refreshToken)
        assertEquals("dbid:12345", tokens.accountId)
        assertEquals(14400L, tokens.expiresInSeconds)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("code=auth_code_999"))
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("client_id=app_key_222"))
        assertTrue(body.contains("code_verifier=verifier_111"))
    }

    @Test
    fun refreshAccessToken_successfulRefresh() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "access_token": "new_access_token_555",
                        "token_type": "bearer",
                        "expires_in": 14400
                    }
                    """.trimIndent()
                )
        )

        val tokens = DropboxOAuth.refreshAccessToken(
            refreshToken = "stored_refresh_token_000",
            appKey = "app_key_222",
            tokenEndpoint = server.url("/oauth2/token").toString(),
        )

        assertEquals("new_access_token_555", tokens.accessToken)
        assertEquals("stored_refresh_token_000", tokens.refreshToken)
        assertEquals(14400L, tokens.expiresInSeconds)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("refresh_token=stored_refresh_token_000"))
        assertTrue(body.contains("client_id=app_key_222"))
    }

    @Test
    fun errorHandling_neverLeaksTokensInExceptionMessage() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "error": "invalid_grant",
                        "error_description": "The authorization code has expired."
                    }
                    """.trimIndent()
                )
        )

        val secretToken = "super_secret_token_12345678"
        try {
            DropboxOAuth.exchangeCodeForTokens(
                code = secretToken,
                codeVerifier = "some_verifier",
                appKey = "some_key",
                tokenEndpoint = server.url("/oauth2/token").toString(),
            )
            kotlin.test.fail("Should have thrown exception")
        } catch (e: DropboxOAuthException) {
            // Assert error description is present but secret token is NOT leaked
            assertTrue(e.message?.contains("expired") == true)
            assertFalse(e.message?.contains(secretToken) == true)
        }
    }

    @Test
    fun dropboxClient_automaticTokenRefreshAndCaching() {
        val tempDir = Files.createTempDirectory("dropbox_client_test")
        try {
            val credsFile = tempDir.resolve("credentials.json")
            val store = CredentialsStore(credsFile)
            store.save(Credentials(dropboxRefreshToken = "test_refresh_token"))

            // Token refresh endpoint returns new access token
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"access_token": "token_1", "expires_in": 14400}""")
            )

            // Account info endpoint returns user info
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"account_id": "dbid:test", "name": {"display_name": "FloNovel User"}, "email": "test@flonovel.com"}""")
            )

            val client = DropboxClient(
                appKey = "test_key",
                credentialsStore = store,
                apiBaseUrl = server.url("").toString().removeSuffix("/"),
                contentBaseUrl = server.url("").toString().removeSuffix("/"),
            )

            val account = client.getCurrentAccount()
            assertNotNull(account)
            assertEquals("dbid:test", account.accountId)
            assertEquals("FloNovel User", account.displayName)
            assertEquals("test@flonovel.com", account.email)

            // Verify access token is cached and reused (no second token refresh request)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"account_id": "dbid:test", "name": {"display_name": "FloNovel User"}, "email": "test@flonovel.com"}""")
            )
            val account2 = client.getCurrentAccount()
            assertNotNull(account2)

            // Should have made 3 requests total: 1 refresh + 1 get_account + 1 get_account
            assertEquals(3, server.requestCount)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun unconfiguredOrDisconnectedClient_quietlyDisabled() {
        val tempDir = Files.createTempDirectory("dropbox_client_unlinked")
        try {
            val credsFile = tempDir.resolve("credentials.json")
            val store = CredentialsStore(credsFile)
            // No refresh token stored
            val client = DropboxClient(
                appKey = "",
                credentialsStore = store,
            )

            assertFalse(client.isConfigured)
            assertFalse(client.isLinked)
            assertNull(client.getOrRefreshAccessToken())
            assertNull(client.getCurrentAccount())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

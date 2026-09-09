package com.moonkata.flonovel.desktop.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.moonkata.flonovel.desktop.i18n.Strings

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val accountId: String?,
    val expiresInSeconds: Long,
    val issuedAtMillis: Long = System.currentTimeMillis(),
)

class DropboxOAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object DropboxOAuth {

    private val defaultHttpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    /**
     * Generates a PKCE code verifier (cryptographically random 32 bytes, URL-safe Base64 without padding).
     */
    fun generateCodeVerifier(): String {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    /**
     * Generates the SHA-256 PKCE code challenge from a code verifier.
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Constructs the Dropbox OAuth authorization URL for PKCE public client flow.
     */
    fun buildAuthorizeUrl(
        appKey: String,
        codeChallenge: String,
        redirectUri: String = DropboxConfig.REDIRECT_URI,
    ): String {
        val encodedKey = URLEncoder.encode(appKey, StandardCharsets.UTF_8)
        val encodedChallenge = URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
        val encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        return "https://www.dropbox.com/oauth2/authorize" +
            "?client_id=$encodedKey" +
            "&response_type=code" +
            "&code_challenge=$encodedChallenge" +
            "&code_challenge_method=S256" +
            "&redirect_uri=$encodedRedirect" +
            "&token_access_type=offline"
    }

    @Volatile
    private var activeServer: HttpServer? = null

    fun stopActiveServer() {
        activeServer?.let { s ->
            try {
                s.stop(0)
            } catch (e: Throwable) {
                System.err.println("Failed to stop previous HttpServer: ${e.message}")
            }
            activeServer = null
        }
    }

    /**
     * Attempts to open [url] in the system default browser.
     * Uses AWT Desktop.browse() first, followed by Windows-specific fallback processes.
     * Returns Pair(success: Boolean, errorReason: String?).
     */
    fun openBrowser(url: String): Pair<Boolean, String?> {
        val uri = try {
            URI.create(url)
        } catch (e: Throwable) {
            return Pair(false, Strings.get("dropbox_oauth_invalid_url", e.message ?: ""))
        }

        var desktopError: String? = null
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                return Pair(true, null)
            } else {
                desktopError = Strings.get("dropbox_oauth_browse_not_supported")
            }
        } catch (e: Throwable) {
            desktopError = Strings.get("dropbox_oauth_browse_exception", e.javaClass.simpleName, e.message ?: "")
            System.err.println("DropboxOAuth: $desktopError")
        }

        // Windows fallback strategies
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) {
            try {
                val process = ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
                val exited = process.waitFor(3, TimeUnit.SECONDS)
                if (!exited || process.exitValue() == 0) {
                    return Pair(true, null)
                }
            } catch (e: Throwable) {
                System.err.println("DropboxOAuth rundll32 fallback failed: ${e.message}")
            }

            try {
                val process = ProcessBuilder("cmd", "/c", "start", "", url).start()
                val exited = process.waitFor(3, TimeUnit.SECONDS)
                if (!exited || process.exitValue() == 0) {
                    return Pair(true, null)
                }
            } catch (e: Throwable) {
                System.err.println("DropboxOAuth cmd start fallback failed: ${e.message}")
            }
        }

        return Pair(false, desktopError ?: Strings.get("dropbox_oauth_cannot_open_browser"))
    }

    /**
     * Starts a loopback HTTP server on [port], opens the system browser to [authUrl],
     * and awaits the authorization callback code.
     */
    fun startLoopbackAuth(
        appKey: String,
        codeChallenge: String,
        port: Int = DropboxConfig.REDIRECT_PORT,
        redirectUri: String = DropboxConfig.REDIRECT_URI,
        timeoutSeconds: Long = 180,
        openBrowser: Boolean = true,
        onAuthUrlReady: ((authUrl: String, browserOpened: Boolean, failureReason: String?) -> Unit)? = null,
    ): CompletableFuture<String> {
        stopActiveServer()

        val future = CompletableFuture<String>()
        val executor = Executors.newSingleThreadExecutor()
        val server = try {
            HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        } catch (e: Throwable) {
            System.err.println(Strings.get("dropbox_oauth_port_bind_failed", port, e.message ?: ""))
            throw DropboxOAuthException(Strings.get("dropbox_oauth_loopback_bind_failed", port, e.message ?: ""), e)
        }
        activeServer = server

        server.createContext("/oauth/callback", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                try {
                    val query = exchange.requestURI.query ?: ""
                    val params = parseQuery(query)
                    val code = params["code"]
                    val error = params["error"]
                    val errorDesc = params["error_description"]

                    val responseHtml = if (code != null) {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="utf-8"><title>FloNovel</title></head>
                        <body style="font-family: sans-serif; text-align: center; padding: 60px; background: #141416; color: #ffffff;">
                          <h2 style="color: #60A5FA;">${Strings.get("dropbox_oauth_html_success_title")}</h2>
                          <p style="color: #9CA3AF; margin-top: 16px;">${Strings.get("dropbox_oauth_html_success_desc")}</p>
                        </body>
                        </html>
                        """.trimIndent()
                    } else {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="utf-8"><title>FloNovel</title></head>
                        <body style="font-family: sans-serif; text-align: center; padding: 60px; background: #141416; color: #ffffff;">
                          <h2 style="color: #EF4444;">${Strings.get("dropbox_oauth_html_failure_title")}</h2>
                          <p style="color: #9CA3AF; margin-top: 16px;">${Strings.get("dropbox_oauth_html_failure_desc")}</p>
                        </body>
                        </html>
                        """.trimIndent()
                    }

                    val responseBytes = responseHtml.toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                    exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                    exchange.responseBody.use { it.write(responseBytes) }

                    if (code != null) {
                        future.complete(code)
                    } else {
                        future.completeExceptionally(DropboxOAuthException("OAuth failed: ${error ?: "unknown"} (${errorDesc ?: ""})"))
                    }
                } finally {
                    executor.submit {
                        Thread.sleep(500)
                        server.stop(1)
                        activeServer = null
                        executor.shutdown()
                    }
                }
            }
        })

        server.executor = executor
        server.start()

        val authUrl = buildAuthorizeUrl(appKey, codeChallenge, redirectUri)
        val (browserOpened, failureReason) = if (openBrowser) {
            openBrowser(authUrl)
        } else {
            Pair(false, null)
        }
        onAuthUrlReady?.invoke(authUrl, browserOpened, failureReason)

        // Schedule timeout
        CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS).execute {
            if (!future.isDone) {
                future.completeExceptionally(DropboxOAuthException("OAuth authorization timed out after ${timeoutSeconds}s"))
                server.stop(0)
                activeServer = null
                executor.shutdown()
            }
        }

        return future
    }

    /**
     * Exchanges the authorization code and PKCE code_verifier for OAuth tokens.
     */
    fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        appKey: String,
        redirectUri: String = DropboxConfig.REDIRECT_URI,
        tokenEndpoint: String = "https://api.dropboxapi.com/oauth2/token",
        httpClient: HttpClient = defaultHttpClient,
    ): OAuthTokens {
        val formParams = listOf(
            "code" to code,
            "grant_type" to "authorization_code",
            "client_id" to appKey,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier,
        )
        val requestBody = formParams.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val errorSummary = runCatching {
                val json = JSONObject(response.body())
                json.optString("error_description", json.optString("error", "Unknown error"))
            }.getOrDefault("HTTP status ${response.statusCode()}")
            // NEVER include tokens or sensitive code in exception message
            throw DropboxOAuthException("Token exchange failed: $errorSummary")
        }

        val json = JSONObject(response.body())
        return OAuthTokens(
            accessToken = json.getString("access_token"),
            refreshToken = if (json.has("refresh_token") && !json.isNull("refresh_token")) json.getString("refresh_token") else null,
            accountId = if (json.has("account_id") && !json.isNull("account_id")) json.getString("account_id") else null,
            expiresInSeconds = json.optLong("expires_in", 14400L),
        )
    }

    /**
     * Refreshes an expired access token using the stored refresh token.
     */
    fun refreshAccessToken(
        refreshToken: String,
        appKey: String,
        tokenEndpoint: String = "https://api.dropboxapi.com/oauth2/token",
        httpClient: HttpClient = defaultHttpClient,
    ): OAuthTokens {
        val formParams = listOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to appKey,
        )
        val requestBody = formParams.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val errorSummary = runCatching {
                val json = JSONObject(response.body())
                json.optString("error_description", json.optString("error", "Unknown error"))
            }.getOrDefault("HTTP status ${response.statusCode()}")
            // NEVER include tokens in exception message
            throw DropboxOAuthException("Token refresh failed: $errorSummary")
        }

        val json = JSONObject(response.body())
        return OAuthTokens(
            accessToken = json.getString("access_token"),
            refreshToken = refreshToken, // Dropbox refresh doesn't return a new refresh token unless rotated
            accountId = null,
            expiresInSeconds = json.optLong("expires_in", 14400L),
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                key to java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)
            } else null
        }.toMap()
    }
}

package com.moonkata.flonovel.android.data.sync

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class OAuthTokens(
    val accessToken: String,
    /** Null when Dropbox did not rotate it — the caller keeps whichever one it already had. */
    val refreshToken: String?,
    val accountId: String?,
    val expiresInSeconds: Long,
)

/**
 * Dropbox OAuth 2 for a public client, PKCE only (docs 06-SYNC-STRATEGY B1).
 *
 * The Desktop app runs the same flow against a loopback redirect; Android uses a custom scheme
 * ([DropboxConfig.redirectUri]) so the browser hands control straight back to the app.
 *
 * Nothing here ever logs a token, a code, or a verifier. Failures carry Dropbox's `error_description`
 * at most, so a stack trace in logcat can never be replayed into an account.
 */
object DropboxOAuth {

    private const val TAG = "DropboxOAuth"
    private const val AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize"
    private const val TOKEN_ENDPOINT = "https://api.dropboxapi.com/oauth2/token"

    private const val BASE64_URL_SAFE = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    /** 32 random bytes, URL-safe Base64 without padding — RFC 7636 §4.1 (43 characters). */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, BASE64_URL_SAFE)
    }

    /** S256 challenge — RFC 7636 §4.2. */
    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, BASE64_URL_SAFE)
    }

    /**
     * `token_access_type=offline` is what makes Dropbox return a refresh token. Without it the app
     * would silently stop syncing a few hours after every login, which is exactly the kind of failure
     * a user reports as "it worked yesterday".
     */
    fun buildAuthorizeUrl(
        appKey: String,
        codeChallenge: String,
        redirectUri: String,
        scopes: String = DropboxConfig.SCOPES,
    ): String = buildString {
        append(AUTHORIZE_URL)
        append("?client_id=").append(enc(appKey))
        append("&response_type=code")
        append("&code_challenge=").append(enc(codeChallenge))
        append("&code_challenge_method=S256")
        append("&redirect_uri=").append(enc(redirectUri))
        append("&token_access_type=offline")
        append("&scope=").append(enc(scopes))
    }

    /**
     * Pulls the authorization code out of the redirect the browser sent back. Returns null when the
     * user denied consent (`?error=access_denied`) or the URI is not ours, which the caller reports
     * as "cancelled" rather than as a failure.
     */
    fun extractAuthorizationCode(redirectUri: String, expectedRedirectUri: String): String? {
        if (!redirectUri.startsWith(expectedRedirectUri)) return null
        val query = redirectUri.substringAfter('?', "")
        if (query.isEmpty()) return null
        return parseQuery(query)["code"]?.takeIf { it.isNotBlank() }
    }

    suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        appKey: String,
        redirectUri: String,
    ): OAuthTokens? = postForm(
        listOf(
            "code" to code,
            "grant_type" to "authorization_code",
            "client_id" to appKey,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier,
        ),
        what = "Token exchange",
    )?.let { json ->
        OAuthTokens(
            accessToken = json.getString("access_token"),
            refreshToken = json.optStringOrNull("refresh_token"),
            accountId = json.optStringOrNull("account_id"),
            expiresInSeconds = json.optLong("expires_in", DEFAULT_EXPIRY_SECONDS),
        )
    }

    /**
     * Dropbox normally does not return a new refresh token here, so [OAuthTokens.refreshToken] comes
     * back null and the caller keeps the stored one. It *can* rotate, so the field is passed through
     * rather than assumed absent.
     */
    suspend fun refreshAccessToken(refreshToken: String, appKey: String): OAuthTokens? = postForm(
        listOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to appKey,
        ),
        what = "Token refresh",
    )?.let { json ->
        OAuthTokens(
            accessToken = json.getString("access_token"),
            refreshToken = json.optStringOrNull("refresh_token"),
            accountId = null,
            expiresInSeconds = json.optLong("expires_in", DEFAULT_EXPIRY_SECONDS),
        )
    }

    private const val DEFAULT_EXPIRY_SECONDS = 14_400L

    private suspend fun postForm(params: List<Pair<String, String>>, what: String): JSONObject? =
        withContext(Dispatchers.IO) {
            val body = params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
            runCatching {
                val connection = (URL(TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                connection.use {
                    OutputStreamWriter(it.outputStream, Charsets.UTF_8).use { writer -> writer.write(body) }
                    if (it.responseCode !in 200..299) {
                        Log.w(TAG, "$what failed: ${describeError(it)}")
                        return@runCatching null
                    }
                    JSONObject(it.inputStream.bufferedReader().readText())
                }
            }.onFailure { Log.w(TAG, "$what failed", it) }.getOrNull()
        }

    /** Dropbox's own wording, never our request contents. */
    private fun describeError(connection: HttpURLConnection): String {
        val raw = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
            ?: return "HTTP ${connection.responseCode}"
        return runCatching {
            val json = JSONObject(raw)
            json.optString("error_description", json.optString("error", "HTTP ${connection.responseCode}"))
        }.getOrDefault("HTTP ${connection.responseCode}")
    }

    private fun parseQuery(query: String): Map<String, String> = query.split("&").mapNotNull { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        val key = pair.substring(0, separator)
        val value = runCatching {
            java.net.URLDecoder.decode(pair.substring(separator + 1), "UTF-8")
        }.getOrDefault("")
        key to value
    }.toMap()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/** `optString` returns "" for a missing key, which is indistinguishable from an empty value. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

internal inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
    try {
        block(this)
    } finally {
        disconnect()
    }

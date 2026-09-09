package com.moonkata.flonovel.android.data.sync

import android.util.Log
import com.moonkata.flonovel.android.data.datastore.ReaderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One entry from `list_folder`. Folders are dropped during parsing — nothing here needs them. */
sealed class DropboxEntry {
    abstract val pathLower: String

    /** [pathDisplay] keeps the author's original casing; [pathLower] is the matching key. */
    data class File(
        override val pathLower: String,
        val pathDisplay: String,
        val size: Long,
    ) : DropboxEntry()

    data class Deleted(override val pathLower: String) : DropboxEntry()
}

/**
 * [NotFound] is kept apart from [Failure] because the two mean opposite things to the caller: a
 * missing `/.flonovel/secret.json` means "run the Desktop app once", while a failure means "try
 * again later". Collapsing them would put the wrong instruction on screen.
 */
sealed class DropboxDownloadResult {
    object Success : DropboxDownloadResult()
    object NotFound : DropboxDownloadResult()
    data class Failure(val statusCode: Int) : DropboxDownloadResult()
}

sealed class DropboxListResult {
    data class Success(val entries: List<DropboxEntry>, val cursor: String, val hasMore: Boolean) : DropboxListResult()

    /** The cursor is too old or otherwise invalid — the caller must restart from a full listing. */
    object CursorReset : DropboxListResult()

    data class Failure(val message: String) : DropboxListResult()
}

/**
 * The Dropbox HTTP API, limited to what the phone actually does: read metadata, read content
 * (docs 06-SYNC-STRATEGY B2 — Android never uploads and never deletes remotely, so there is no
 * upload/delete here to be called by mistake).
 *
 * Hand-written against `HttpURLConnection` + `org.json` to match [ReadingPositionSyncClient] and the
 * Desktop client, rather than pulling in the Dropbox SDK for four endpoints.
 *
 * The access token lives in memory only. The refresh token is the one persisted thing, and it is
 * excluded from cloud backup (see `backup_rules.xml`).
 */
class DropboxClient(
    private val settingsRepository: ReaderSettingsRepository,
    private val appKey: String = DropboxConfig.appKey,
) {
    private val tokenMutex = Mutex()
    private var cachedAccessToken: String? = null
    private var tokenExpiresAtMillis: Long = 0L

    suspend fun isLinked(): Boolean = settingsRepository.settingsFlow.first().dropboxRefreshToken.isNotBlank()

    /**
     * Returns a usable access token, refreshing it when it is missing or close to expiry. Null means
     * the account is not linked, or the refresh token was revoked — the caller surfaces that as
     * "reconnect", not as a transient network error.
     */
    suspend fun accessToken(): String? = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        // A minute of headroom: a token that expires mid-request would fail the call it was fetched for.
        cachedAccessToken?.let { if (now < tokenExpiresAtMillis - 60_000L) return@withLock it }

        val refreshToken = settingsRepository.settingsFlow.first().dropboxRefreshToken
        if (refreshToken.isBlank()) return@withLock null

        val tokens = DropboxOAuth.refreshAccessToken(refreshToken, appKey) ?: return@withLock null
        cachedAccessToken = tokens.accessToken
        tokenExpiresAtMillis = now + tokens.expiresInSeconds * 1000L
        // Dropbox rarely rotates the refresh token, but when it does, dropping the new one would
        // silently break every sync after the old one expires.
        tokens.refreshToken?.takeIf { it != refreshToken }?.let { settingsRepository.updateDropboxRefreshToken(it) }
        tokens.accessToken
    }

    /** Called after a successful sign-in so the first request does not have to refresh immediately. */
    suspend fun cacheAccessToken(accessToken: String, expiresInSeconds: Long) = tokenMutex.withLock {
        cachedAccessToken = accessToken
        tokenExpiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000L
    }

    suspend fun forgetAccessToken() = tokenMutex.withLock {
        cachedAccessToken = null
        tokenExpiresAtMillis = 0L
    }

    /** The signed-in account's email, shown in settings so it is obvious *which* account is linked. */
    suspend fun accountEmail(): String? = withContext(Dispatchers.IO) {
        val body = rpc("$API_BASE/2/users/get_current_account", "null") ?: return@withContext null
        runCatching { JSONObject(body).optStringOrNull("email") }.getOrNull()
    }

    /** Full recursive listing of [path]; the returned cursor feeds [listFolderContinue]. */
    suspend fun listFolder(path: String = DropboxConfig.REMOTE_BOOKS_ROOT): DropboxListResult =
        withContext(Dispatchers.IO) {
            val request = JSONObject().put("path", path).put("recursive", true).toString()
            rpcWithStatus("$API_BASE/2/files/list_folder", request).toListResult()
        }

    /** Only what changed since [cursor] — including explicit `deleted` entries. */
    suspend fun listFolderContinue(cursor: String): DropboxListResult = withContext(Dispatchers.IO) {
        val request = JSONObject().put("cursor", cursor).toString()
        rpcWithStatus("$API_BASE/2/files/list_folder/continue", request).toListResult()
    }

    /**
     * Streams [path] into [output] without buffering the whole file — the real library holds a 48.6MB
     * novel (docs 06-SYNC-STRATEGY B6), which is well past what should sit in memory on a phone.
     * [output] is not closed here; the caller owns it.
     */
    suspend fun downloadFile(path: String, output: OutputStream): DropboxDownloadResult =
        withContext(Dispatchers.IO) {
            val first = downloadOnce(path, output)
            // Same reason as the RPC retry: a token expiring mid-sync would otherwise fail every
            // remaining file in the batch. Nothing has been written to [output] at this point --
            // the status is checked before the body is copied -- so the retry starts clean.
            if (first is DropboxDownloadResult.Failure && first.statusCode == 401) {
                forgetAccessToken()
                downloadOnce(path, output)
            } else {
                first
            }
        }

    private suspend fun downloadOnce(path: String, output: OutputStream): DropboxDownloadResult =
        withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext DropboxDownloadResult.Failure(-1)
        runCatching {
            val connection = (URL("$CONTENT_BASE/2/files/download").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                // Generous: a large novel over a slow connection must not be cut off mid-transfer.
                readTimeout = 120_000
                setRequestProperty("Authorization", "Bearer $token")
                // Dropbox takes the argument in a header for content endpoints, not in the body.
                setRequestProperty("Dropbox-API-Arg", asciiSafeDropboxApiArg(JSONObject().put("path", path).toString()))
            }
            connection.use {
                val status = it.responseCode
                if (status !in 200..299) {
                    // Dropbox reports a missing path as 409 with a "not_found" tag in the body,
                    // not as a 404.
                    val body = runCatching { it.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                    if (status == 409 && body.contains("not_found")) {
                        return@runCatching DropboxDownloadResult.NotFound
                    }
                    Log.w(TAG, "Download failed for $path: HTTP $status")
                    return@runCatching DropboxDownloadResult.Failure(status)
                }
                it.inputStream.use { input -> input.copyTo(output) }
                DropboxDownloadResult.Success
            }
        }.onFailure { Log.w(TAG, "Download failed for $path", it) }
            .getOrDefault(DropboxDownloadResult.Failure(-1))
    }

    // ── HTTP plumbing ──────────────────────────────────────────────────────

    private data class Response(val status: Int, val body: String?)

    private suspend fun rpc(url: String, jsonBody: String): String? = rpcWithStatus(url, jsonBody).body

    /**
     * A 401 means the cached token expired earlier than advertised (or was revoked server-side).
     * Dropping it and retrying once turns that into a normal request instead of a visible failure;
     * a second 401 is reported as-is so the caller can prompt for a reconnect.
     */
    private suspend fun rpcWithStatus(url: String, jsonBody: String): Response {
        val first = rpcOnce(url, jsonBody) ?: return Response(-1, null)
        if (first.status != 401) return first
        forgetAccessToken()
        return rpcOnce(url, jsonBody) ?: Response(-1, null)
    }

    private suspend fun rpcOnce(url: String, jsonBody: String): Response? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.use {
                it.outputStream.use { out -> out.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                val stream = if (it.responseCode in 200..299) it.inputStream else it.errorStream
                Response(it.responseCode, stream?.bufferedReader()?.readText())
            }
        }.onFailure { Log.w(TAG, "Request to $url failed", it) }.getOrNull()
    }

    private fun Response.toListResult(): DropboxListResult {
        // Bound to a local because Kotlin will not smart-cast a member property inside the lambda below.
        val payload = body ?: return DropboxListResult.Failure("Network or authentication error")
        if (status in 200..299) return parseListFolderBody(payload)
        // Dropbox reports an unusable cursor as 409 with a "reset" tag rather than a distinct status.
        if (status == 409 && payload.contains("reset")) return DropboxListResult.CursorReset
        // Dropbox's own error_summary names the actual problem ("path/not_found/..."); the bare
        // status is the fallback for a body that is not the error shape we expect.
        val summary = runCatching { JSONObject(payload).optStringOrNull("error_summary") }.getOrNull()
        return DropboxListResult.Failure(summary ?: "HTTP $status")
    }

    companion object {
        private const val TAG = "DropboxClient"
        private const val API_BASE = "https://api.dropboxapi.com"
        private const val CONTENT_BASE = "https://content.dropboxapi.com"
    }
}

/**
 * Pure parsing, split out so it can be unit-tested against recorded response bodies without a device
 * or a network. Folder entries are dropped: the sync only ever acts on files, and folders are created
 * on demand as a side effect of writing one.
 */
internal fun parseListFolderBody(body: String): DropboxListResult = runCatching {
    val json = JSONObject(body)
    val array = json.optJSONArray("entries")
    val entries = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val item = array!!.getJSONObject(i)
            val pathLower = item.optString("path_lower", "")
            if (pathLower.isBlank()) continue
            when (item.optString(".tag")) {
                "file" -> add(
                    DropboxEntry.File(
                        pathLower = pathLower,
                        pathDisplay = item.optString("path_display", pathLower),
                        size = item.optLong("size", 0L),
                    ),
                )
                "deleted" -> add(DropboxEntry.Deleted(pathLower))
            }
        }
    }
    DropboxListResult.Success(
        entries = entries,
        cursor = json.optString("cursor", ""),
        hasMore = json.optBoolean("has_more", false),
    )
}.getOrElse { DropboxListResult.Failure("Malformed response") }

/**
 * Escapes non-ASCII characters to standard JSON \uXXXX format for HTTP header safety.
 * Dropbox API requires `Dropbox-API-Arg` to be ASCII-safe.
 */
internal fun asciiSafeDropboxApiArg(json: String): String {
    val sb = StringBuilder(json.length)
    for (ch in json) {
        val code = ch.code
        if (code in 32..126) {
            sb.append(ch)
        } else {
            sb.append(String.format("\\u%04x", code))
        }
    }
    return sb.toString()
}

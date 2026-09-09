package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.CredentialsStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

sealed class DropboxEntry {
    abstract val name: String
    abstract val pathDisplay: String
    abstract val pathLower: String

    data class FileEntry(
        override val name: String,
        override val pathDisplay: String,
        override val pathLower: String,
        val size: Long,
        val serverModified: String,
    ) : DropboxEntry()

    data class FolderEntry(
        override val name: String,
        override val pathDisplay: String,
        override val pathLower: String,
    ) : DropboxEntry()

    data class DeletedEntry(
        override val name: String,
        override val pathDisplay: String,
        override val pathLower: String,
    ) : DropboxEntry()
}

sealed class DropboxUploadResult {
    data class Success(val path: String, val size: Long, val serverModified: String) : DropboxUploadResult()
    object InsufficientSpace : DropboxUploadResult()
    data class Failure(val message: String, val statusCode: Int = -1) : DropboxUploadResult()
}

sealed class DropboxListFolderResult {
    data class Success(
        val entries: List<DropboxEntry>,
        val cursor: String,
        val hasMore: Boolean,
    ) : DropboxListFolderResult()

    object Reset : DropboxListFolderResult()
    data class Failure(val message: String, val statusCode: Int = -1) : DropboxListFolderResult()
}

data class AccountInfo(
    val accountId: String,
    val displayName: String,
    val email: String,
)

class DropboxClient(
    val appKey: String = DropboxConfig.appKey,
    val credentialsStore: CredentialsStore,
    val apiBaseUrl: String = "https://api.dropboxapi.com",
    val contentBaseUrl: String = "https://content.dropboxapi.com",
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build(),
) {
    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiresAtMillis: Long = 0L

    val isConfigured: Boolean
        get() = appKey.isNotBlank()

    val isLinked: Boolean
        get() = !credentialsStore.load().dropboxRefreshToken.isNullOrBlank()

    /**
     * Ensures a valid access token is available. Refreshes if expired or missing.
     * Returns null if no refresh token is stored or if refresh fails.
     */
    @Synchronized
    fun getOrRefreshAccessToken(): String? {
        val refreshToken = credentialsStore.load().dropboxRefreshToken
        if (refreshToken.isNullOrBlank()) return null

        val now = System.currentTimeMillis()
        if (cachedAccessToken != null && now < tokenExpiresAtMillis - 60_000L) {
            return cachedAccessToken
        }

        return try {
            val tokens = DropboxOAuth.refreshAccessToken(
                refreshToken = refreshToken,
                appKey = appKey,
                tokenEndpoint = "$apiBaseUrl/oauth2/token",
                httpClient = httpClient,
            )
            cachedAccessToken = tokens.accessToken
            tokenExpiresAtMillis = now + (tokens.expiresInSeconds * 1000L)
            tokens.accessToken
        } catch (_: Exception) {
            // Never leak tokens in error logging
            null
        }
    }

    /**
     * Sets in-memory access token (e.g. immediately after initial OAuth flow).
     */
    @Synchronized
    fun setAccessToken(accessToken: String, expiresInSeconds: Long) {
        cachedAccessToken = accessToken
        tokenExpiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L)
    }

    /**
     * Clears in-memory token to force refresh on next call.
     */
    @Synchronized
    fun invalidateAccessToken() {
        cachedAccessToken = null
        tokenExpiresAtMillis = 0L
    }

    /**
     * Fetches current account profile.
     */
    fun getCurrentAccount(): AccountInfo? {
        val response = executeStringRequest { token ->
            HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/2/users/get_current_account"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("null"))
                .build()
        } ?: return null

        if (response.statusCode() !in 200..299) return null

        return runCatching {
            val json = JSONObject(response.body())
            val accountId = json.getString("account_id")
            val nameObj = json.optJSONObject("name")
            val displayName = nameObj?.optString("display_name", "User") ?: "User"
            val email = json.optString("email", "")
            AccountInfo(accountId, displayName, email)
        }.getOrNull()
    }

    /**
     * Uploads file content to Dropbox.
     */
    fun uploadFile(
        path: String,
        content: ByteArray,
        overwrite: Boolean = true,
    ): DropboxUploadResult {
        val normPath = if (path.startsWith("/")) path else "/$path"
        val argJson = JSONObject().apply {
            put("path", normPath)
            put("mode", if (overwrite) "overwrite" else "add")
            put("autorename", false)
            put("mute", true)
        }.toString()

        val response = executeStringRequest { token ->
            HttpRequest.newBuilder()
                .uri(URI.create("$contentBaseUrl/2/files/upload"))
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", asciiSafeDropboxApiArg(argJson))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build()
        } ?: return DropboxUploadResult.Failure("Network or auth error")

        if (response.statusCode() in 200..299) {
            val json = JSONObject(response.body())
            return DropboxUploadResult.Success(
                path = json.optString("path_display", normPath),
                size = json.optLong("size", content.size.toLong()),
                serverModified = json.optString("server_modified", ""),
            )
        }

        val bodyStr = response.body()
        if (response.statusCode() == 409 && bodyStr.contains("insufficient_space")) {
            return DropboxUploadResult.InsufficientSpace
        }

        val errorSummary = runCatching {
            val json = JSONObject(bodyStr)
            json.optString("error_summary", "Upload failed with HTTP ${response.statusCode()}")
        }.getOrDefault("HTTP ${response.statusCode()}")

        return DropboxUploadResult.Failure(errorSummary, response.statusCode())
    }

    /**
     * Downloads file content from Dropbox. Returns null if not found or on error.
     */
    fun downloadFile(path: String): ByteArray? {
        val normPath = if (path.startsWith("/")) path else "/$path"
        val argJson = JSONObject().apply {
            put("path", normPath)
        }.toString()

        val response = executeBytesRequest { token ->
            HttpRequest.newBuilder()
                .uri(URI.create("$contentBaseUrl/2/files/download"))
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", asciiSafeDropboxApiArg(argJson))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        } ?: return null

        if (response.statusCode() in 200..299) {
            return response.body()
        }

        // 409 path/not_found
        return null
    }

    /**
     * Deletes a file or folder in Dropbox.
     */
    fun deleteFile(path: String): Boolean {
        val normPath = if (path.startsWith("/")) path else "/$path"
        val bodyJson = JSONObject().apply {
            put("path", normPath)
        }.toString()

        val response = executeStringRequest { token ->
            HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/2/files/delete_v2"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build()
        } ?: return false

        return response.statusCode() in 200..299 ||
            (response.statusCode() == 409 && response.body().contains("not_found"))
    }

    /**
     * Lists entries in [path] (e.g. "/books").
     */
    fun listFolder(path: String, recursive: Boolean = true): DropboxListFolderResult {
        val normPath = if (path.startsWith("/")) path else "/$path"
        val bodyJson = JSONObject().apply {
            put("path", normPath)
            put("recursive", recursive)
        }.toString()

        val response = executeStringRequest { token ->
            HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/2/files/list_folder"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build()
        } ?: return DropboxListFolderResult.Failure("Network or auth error")

        return parseListFolderResponse(response)
    }

    /**
     * Continues listing folder changes with [cursor].
     */
    fun listFolderContinue(cursor: String): DropboxListFolderResult {
        val bodyJson = JSONObject().apply {
            put("cursor", cursor)
        }.toString()

        val response = executeStringRequest { token ->
            HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/2/files/list_folder/continue"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build()
        } ?: return DropboxListFolderResult.Failure("Network or auth error")

        return parseListFolderResponse(response)
    }

    private fun parseListFolderResponse(response: HttpResponse<String>): DropboxListFolderResult {
        if (response.statusCode() in 200..299) {
            val json = JSONObject(response.body())
            val entriesArray = json.optJSONArray("entries") ?: JSONArray()
            val entries = mutableListOf<DropboxEntry>()
            for (i in 0 until entriesArray.length()) {
                val item = entriesArray.getJSONObject(i)
                val tag = item.optString(".tag")
                val name = item.optString("name", "")
                val pathDisplay = item.optString("path_display", "")
                val pathLower = item.optString("path_lower", "")
                when (tag) {
                    "file" -> entries.add(
                        DropboxEntry.FileEntry(
                            name = name,
                            pathDisplay = pathDisplay,
                            pathLower = pathLower,
                            size = item.optLong("size", 0L),
                            serverModified = item.optString("server_modified", ""),
                        )
                    )
                    "folder" -> entries.add(
                        DropboxEntry.FolderEntry(
                            name = name,
                            pathDisplay = pathDisplay,
                            pathLower = pathLower,
                        )
                    )
                    "deleted" -> entries.add(
                        DropboxEntry.DeletedEntry(
                            name = name,
                            pathDisplay = pathDisplay,
                            pathLower = pathLower,
                        )
                    )
                }
            }
            val cursor = json.optString("cursor", "")
            val hasMore = json.optBoolean("has_more", false)
            return DropboxListFolderResult.Success(entries, cursor, hasMore)
        }

        val body = response.body()
        if (response.statusCode() == 409 && (body.contains("\"reset\"") || body.contains("path/reset"))) {
            return DropboxListFolderResult.Reset
        }

        val errorSummary = runCatching {
            JSONObject(body).optString("error_summary", "HTTP ${response.statusCode()}")
        }.getOrDefault("HTTP ${response.statusCode()}")

        return DropboxListFolderResult.Failure(errorSummary, response.statusCode())
    }

    private fun executeStringRequest(
        requestBuilder: (token: String) -> HttpRequest,
    ): HttpResponse<String>? {
        val token = getOrRefreshAccessToken() ?: return null
        return try {
            val request = requestBuilder(token)
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() == 401) {
                invalidateAccessToken()
                val refreshedToken = getOrRefreshAccessToken() ?: return response
                val retryRequest = requestBuilder(refreshedToken)
                httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            } else {
                response
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun executeBytesRequest(
        requestBuilder: (token: String) -> HttpRequest,
    ): HttpResponse<ByteArray>? {
        val token = getOrRefreshAccessToken() ?: return null
        return try {
            val request = requestBuilder(token)
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() == 401) {
                invalidateAccessToken()
                val refreshedToken = getOrRefreshAccessToken() ?: return response
                val retryRequest = requestBuilder(refreshedToken)
                httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofByteArray())
            } else {
                response
            }
        } catch (_: Exception) {
            null
        }
    }
}

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

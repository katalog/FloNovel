package com.moonkata.flonovel.desktop.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RemoteReadingPosition(
    val charOffset: Int,
    val source: String,
    val encoding: String?,
)

/**
 * Direct HTTP client for Supabase PostgREST reading position sync.
 * Table: flonovel_sync (06-SYNC-STRATEGY.md PART A)
 *
 * Strict contract:
 * - Table name: flonovel_sync
 * - Headers: apikey, x-flonovel-secret
 * - NEVER add Authorization header (Supabase rejects it as invalid JWT)
 * - Upsert header: Prefer: resolution=merge-duplicates
 * - NEVER send user_key (server trigger computes it via sha256 of x-flonovel-secret)
 * - source is "desktop"
 * - Connection test is an upsert to relative_path = "__connection_test__"
 * - NEVER do conflict resolution on client (server trigger enforces max-wins)
 * - NEVER do GET before push
 * - All failures are safely caught and never disrupt reader screen
 */
class ReadingPositionSyncClient(
    private val baseUrl: String,
    private val publishableKey: String,
    private val sharedSecret: String,
) {
    private val restBase: String
        get() = "${baseUrl.trim().trimEnd('/').removeSuffix("/rest/v1")}/rest/v1/flonovel_sync"

    var lastTestConnectionError: String? = null
        private set

    var lastSyncError: String? = null
        private set

    suspend fun fetch(relativePath: String): RemoteReadingPosition? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(relativePath, "UTF-8")
            val url = java.net.URI.create("$restBase?select=char_offset,source,encoding&relative_path=eq.$encoded").toURL()
            val connection = openConnection(url, "GET")
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText()
                lastSyncError = "HTTP $code" + (if (!err.isNullOrBlank()) ": $err" else "")
                return@runCatching null
            }
            connection.inputStream.use { input ->
                val array = JSONArray(input.bufferedReader().readText())
                if (array.length() == 0) return@runCatching null
                val obj = array.getJSONObject(0)
                RemoteReadingPosition(
                    charOffset = obj.getInt("char_offset"),
                    source = obj.getString("source"),
                    encoding = if (obj.isNull("encoding")) null else obj.getString("encoding"),
                )
            }
        }.onFailure {
            lastSyncError = "${it.javaClass.simpleName}: ${it.message}"
        }.getOrNull()
    }

    suspend fun upsert(relativePath: String, charOffset: Int, encoding: String?) = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = java.net.URI.create(restBase).toURL()
            val connection = openConnection(targetUrl, "POST").apply {
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates")
                doOutput = true
            }
            val body = JSONObject().apply {
                put("relative_path", relativePath)
                put("char_offset", charOffset)
                put("source", "desktop")
                put("encoding", encoding ?: JSONObject.NULL)
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText()
                lastSyncError = "HTTP $code" + (if (!err.isNullOrBlank()) ": $err" else "")
            } else {
                connection.inputStream.use { it.readBytes() }
            }
        }.onFailure {
            lastSyncError = "${it.javaClass.simpleName}: ${it.message}"
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        lastTestConnectionError = if (baseUrl.isBlank() || publishableKey.isBlank()) {
            "SUPABASE_URL / PUBLISHABLE_KEY is empty"
        } else if (sharedSecret.isBlank()) {
            "Secret is empty"
        } else {
            null
        }
        if (lastTestConnectionError != null) return@withContext false

        runCatching {
            val targetUrl = java.net.URI.create(restBase).toURL()
            val connection = openConnection(targetUrl, "POST").apply {
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates")
                doOutput = true
            }
            val body = JSONObject().apply {
                put("relative_path", CONNECTION_TEST_PATH)
                put("char_offset", 0)
                put("source", "desktop")
                put("encoding", JSONObject.NULL)
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
                lastTestConnectionError = "HTTP $code" + (if (!errorBody.isNullOrBlank()) ": $errorBody" else "")
            }
            code in 200..299
        }.onFailure {
            lastTestConnectionError = "${it.javaClass.simpleName}: ${it.message}"
        }.getOrDefault(false)
    }

    private fun openConnection(url: URL, method: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("x-flonovel-secret", sharedSecret)
            // NEVER add Authorization header
        }

    companion object {
        const val CONNECTION_TEST_PATH = "__connection_test__"
    }
}

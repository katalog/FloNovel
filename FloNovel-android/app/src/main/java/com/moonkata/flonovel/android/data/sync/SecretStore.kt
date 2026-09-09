package com.moonkata.flonovel.android.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Outcome of reading the shared secret. Each case maps to a different thing to tell the user. */
sealed class SecretResult {
    data class Success(val secret: String) : SecretResult()

    /** No Dropbox account linked yet — connect first. */
    object NotLinked : SecretResult()

    /** Linked, but the Desktop app has not written the file yet. Not an error; run Desktop once. */
    object NotFound : SecretResult()

    /** Network, auth, or a malformed file. Retrying later is the right move. */
    object Failed : SecretResult()
}

/**
 * Reads the Supabase shared secret out of the Dropbox app folder.
 *
 * **Read-only, on purpose.** Desktop generates the secret (24 random bytes, hex) and writes
 * `/.flonovel/secret.json`; the phone only ever consumes it. Two reasons, and both matter:
 *
 * 1. Android requests no Dropbox write scope (docs 06-SYNC-STRATEGY B2), so it *cannot* upload.
 * 2. If both ends could create the file, a phone that ran first would mint a secret the Desktop
 *    never saw, and each end would then write reading positions into a different Supabase
 *    partition — the sync would look connected and silently share nothing.
 *
 * The file name differs per build (`secret.json` vs `secret-dev.json`, see [DropboxConfig.secretPath])
 * so a dev build can never write into the real partition. The server forbids offset regression, so a
 * stray dev value there could never be undone (docs G21).
 *
 * The secret is never logged — failures report the *kind* of failure only.
 */
class SecretStore(private val client: DropboxClient) {

    suspend fun fetch(): SecretResult = withContext(Dispatchers.IO) {
        if (!client.isLinked()) return@withContext SecretResult.NotLinked

        // The file is a few dozen bytes, so buffering it is fine — unlike a book, which streams.
        val buffer = ByteArrayOutputStream()
        when (client.downloadFile(DropboxConfig.secretPath, buffer)) {
            is DropboxDownloadResult.NotFound -> return@withContext SecretResult.NotFound
            is DropboxDownloadResult.Failure -> return@withContext SecretResult.Failed
            is DropboxDownloadResult.Success -> Unit
        }

        val secret = parseSecret(buffer.toByteArray())
        if (secret == null) {
            Log.w(TAG, "Secret file is present but unreadable")
            return@withContext SecretResult.Failed
        }
        SecretResult.Success(secret)
    }

    companion object {
        private const val TAG = "SecretStore"
    }
}

/**
 * Parses `{"secret": "...", "createdAt": 1234}` as written by the Desktop app's `SecretManager`.
 * Returns null for anything else, which the caller reports rather than treating "" as a secret —
 * an empty secret would hash to a valid-looking user key and quietly land on a shared partition.
 */
internal fun parseSecret(content: ByteArray): String? = runCatching {
    JSONObject(String(content, Charsets.UTF_8)).optStringOrNull("secret")
}.getOrNull()

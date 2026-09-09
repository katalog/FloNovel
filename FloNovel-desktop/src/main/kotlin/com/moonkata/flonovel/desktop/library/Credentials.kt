package com.moonkata.flonovel.desktop.library

import org.json.JSONObject

/**
 * Stores authentication tokens and secrets.
 *
 * Stored strictly in a separate file (credentials.json) so that settings and books
 * can be backed up or shared without leaking credentials.
 */
data class Credentials(
    val schemaVersion: Int = 1,
    val dropboxRefreshToken: String? = null,
    val cachedSupabaseSecret: String? = null,
    val dropboxAccountId: String? = null,
    val dropboxCursor: String? = null,
    val verifiedSupabaseSecret: String? = null,
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("schemaVersion", schemaVersion)
        if (dropboxRefreshToken != null) obj.put("dropboxRefreshToken", dropboxRefreshToken) else obj.put("dropboxRefreshToken", JSONObject.NULL)
        if (cachedSupabaseSecret != null) obj.put("cachedSupabaseSecret", cachedSupabaseSecret) else obj.put("cachedSupabaseSecret", JSONObject.NULL)
        if (dropboxAccountId != null) obj.put("dropboxAccountId", dropboxAccountId) else obj.put("dropboxAccountId", JSONObject.NULL)
        if (dropboxCursor != null) obj.put("dropboxCursor", dropboxCursor) else obj.put("dropboxCursor", JSONObject.NULL)
        if (verifiedSupabaseSecret != null) obj.put("verifiedSupabaseSecret", verifiedSupabaseSecret) else obj.put("verifiedSupabaseSecret", JSONObject.NULL)
        return obj.toString(2)
    }

    companion object {
        fun fromJsonString(jsonString: String): Credentials {
            val obj = JSONObject(jsonString)
            return Credentials(
                schemaVersion = obj.optInt("schemaVersion", 1),
                dropboxRefreshToken = if (obj.has("dropboxRefreshToken") && !obj.isNull("dropboxRefreshToken")) obj.getString("dropboxRefreshToken") else null,
                cachedSupabaseSecret = if (obj.has("cachedSupabaseSecret") && !obj.isNull("cachedSupabaseSecret")) obj.getString("cachedSupabaseSecret") else null,
                dropboxAccountId = if (obj.has("dropboxAccountId") && !obj.isNull("dropboxAccountId")) obj.getString("dropboxAccountId") else null,
                dropboxCursor = if (obj.has("dropboxCursor") && !obj.isNull("dropboxCursor")) obj.getString("dropboxCursor") else null,
                verifiedSupabaseSecret = if (obj.has("verifiedSupabaseSecret") && !obj.isNull("verifiedSupabaseSecret")) obj.getString("verifiedSupabaseSecret") else null,
            )
        }
    }
}

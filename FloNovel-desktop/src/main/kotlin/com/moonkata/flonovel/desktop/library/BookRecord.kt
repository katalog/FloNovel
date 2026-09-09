package com.moonkata.flonovel.desktop.library

import org.json.JSONArray
import org.json.JSONObject

data class BookRecord(
    val path: String,
    val key: String,
    val displayName: String,
    val sizeBytes: Long,
    val totalCharCount: Int,
    val detectedEncoding: String,
    val anchor: Int,
    val progress: Double,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    val preprocessedAt: Long? = null,
    val uploadedAt: Long? = null,
    val uploadedSize: Long? = null,
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("path", path)
        obj.put("key", key)
        obj.put("displayName", displayName)
        obj.put("sizeBytes", sizeBytes)
        obj.put("totalCharCount", totalCharCount)
        obj.put("detectedEncoding", detectedEncoding)
        obj.put("anchor", anchor)
        obj.put("progress", progress)
        obj.put("addedAt", addedAt)
        if (lastOpenedAt != null) obj.put("lastOpenedAt", lastOpenedAt)
        if (preprocessedAt != null) obj.put("preprocessedAt", preprocessedAt)
        if (uploadedAt != null) obj.put("uploadedAt", uploadedAt)
        if (uploadedSize != null) obj.put("uploadedSize", uploadedSize)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): BookRecord {
            val pathStr = obj.getString("path")
            return BookRecord(
                path = pathStr,
                key = if (obj.has("key") && !obj.isNull("key")) obj.getString("key") else RelativePath.normalize(pathStr),
                displayName = if (obj.has("displayName") && !obj.isNull("displayName")) obj.getString("displayName") else pathStr.substringAfterLast('/'),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                totalCharCount = obj.optInt("totalCharCount", 0),
                detectedEncoding = obj.optString("detectedEncoding", "UTF-8"),
                anchor = obj.optInt("anchor", 0),
                progress = obj.optDouble("progress", 0.0),
                addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                lastOpenedAt = if (obj.has("lastOpenedAt") && !obj.isNull("lastOpenedAt")) obj.getLong("lastOpenedAt") else null,
                preprocessedAt = if (obj.has("preprocessedAt") && !obj.isNull("preprocessedAt")) obj.getLong("preprocessedAt") else null,
                uploadedAt = if (obj.has("uploadedAt") && !obj.isNull("uploadedAt")) obj.getLong("uploadedAt") else null,
                uploadedSize = if (obj.has("uploadedSize") && !obj.isNull("uploadedSize")) obj.getLong("uploadedSize") else null,
            )
        }
    }
}

data class BooksData(
    val schemaVersion: Int = 1,
    val books: List<BookRecord> = emptyList(),
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)
        val array = JSONArray()
        for (b in books) {
            array.put(b.toJsonObject())
        }
        root.put("books", array)
        return root.toString(2)
    }

    companion object {
        fun fromJsonString(jsonString: String): BooksData {
            val root = JSONObject(jsonString)
            val version = root.optInt("schemaVersion", 1)
            val array = root.optJSONArray("books") ?: JSONArray()
            val list = mutableListOf<BookRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list += BookRecord.fromJsonObject(obj)
            }
            return BooksData(schemaVersion = version, books = list)
        }
    }
}

package com.moonkata.flonovel.desktop.library

import org.json.JSONArray
import org.json.JSONObject

data class ViewSettings(
    val paneMode: String = "ONE",
    val advanceRatio: Float = 0.5f,
    val fontFamily: String = "system",
    val fontSizeSp: Float = 20.0f,
    val lineHeightMultiplier: Float = 1.5f,
    val letterSpacing: Float = 0.0f,
    val marginHorizontal: Float = 16.0f,
    val marginTop: Float = 16.0f,
    val marginBottom: Float = 16.0f,
    val theme: String = "LIGHT",
    val gutter: Float = 24.0f,
    val paneRatio: Float = 0.5f,
    val maxLineWidth: Int = 900,
    val focusMode: Boolean = false,
    val alignChapterToLeftPane: Boolean = false,
    val uiScale: Float = 1.0f,
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("paneMode", paneMode)
        obj.put("advanceRatio", advanceRatio)
        obj.put("fontFamily", fontFamily)
        obj.put("fontSizeSp", fontSizeSp)
        obj.put("lineHeightMultiplier", lineHeightMultiplier)
        obj.put("letterSpacing", letterSpacing)
        obj.put("marginHorizontal", marginHorizontal)
        obj.put("marginTop", marginTop)
        obj.put("marginBottom", marginBottom)
        obj.put("theme", theme)
        obj.put("gutter", gutter)
        obj.put("paneRatio", paneRatio)
        obj.put("maxLineWidth", maxLineWidth)
        obj.put("focusMode", focusMode)
        obj.put("alignChapterToLeftPane", alignChapterToLeftPane)
        obj.put("uiScale", uiScale)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): ViewSettings {
            if (obj == null) return ViewSettings()
            return ViewSettings(
                paneMode = obj.optString("paneMode", "ONE"),
                advanceRatio = obj.optDouble("advanceRatio", 0.5).toFloat(),
                fontFamily = obj.optString("fontFamily", "system"),
                fontSizeSp = obj.optDouble("fontSizeSp", 20.0).toFloat(),
                lineHeightMultiplier = obj.optDouble("lineHeightMultiplier", 1.5).toFloat(),
                letterSpacing = obj.optDouble("letterSpacing", 0.0).toFloat(),
                marginHorizontal = obj.optDouble("marginHorizontal", 16.0).toFloat(),
                marginTop = obj.optDouble("marginTop", 16.0).toFloat(),
                marginBottom = obj.optDouble("marginBottom", 16.0).toFloat(),
                theme = obj.optString("theme", "LIGHT"),
                gutter = obj.optDouble("gutter", 24.0).toFloat(),
                paneRatio = obj.optDouble("paneRatio", 0.5).toFloat(),
                maxLineWidth = obj.optInt("maxLineWidth", 900),
                focusMode = obj.optBoolean("focusMode", false),
                alignChapterToLeftPane = obj.optBoolean("alignChapterToLeftPane", false),
                uiScale = obj.optDouble("uiScale", 1.0).toFloat(),
            )
        }
    }
}

data class ChapterSettings(
    val enabledPresets: List<String> = listOf("hash"),
    val customPatterns: List<String> = emptyList(),
    val jumpDivisions: Int = 4,
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        val presetsArray = JSONArray()
        enabledPresets.forEach { presetsArray.put(it) }
        obj.put("enabledPresets", presetsArray)

        val customArray = JSONArray()
        customPatterns.forEach { customArray.put(it) }
        obj.put("customPatterns", customArray)

        obj.put("jumpDivisions", jumpDivisions)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): ChapterSettings {
            if (obj == null) return ChapterSettings()
            val presets = mutableListOf<String>()
            val pArray = obj.optJSONArray("enabledPresets")
            if (pArray != null) {
                for (i in 0 until pArray.length()) presets += pArray.getString(i)
            } else {
                presets += "hash"
            }

            val custom = mutableListOf<String>()
            val cArray = obj.optJSONArray("customPatterns")
            if (cArray != null) {
                for (i in 0 until cArray.length()) custom += cArray.getString(i)
            }

            return ChapterSettings(
                enabledPresets = presets,
                customPatterns = custom,
                jumpDivisions = obj.optInt("jumpDivisions", 4),
            )
        }
    }
}

data class SyncSettings(
    val dropboxLinked: Boolean = false,
    val uploaderRole: Boolean = true,
    val lastCursor: String? = null,
    val lastSyncAt: Long = 0L,
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("dropboxLinked", dropboxLinked)
        obj.put("uploaderRole", uploaderRole)
        if (lastCursor != null) obj.put("lastCursor", lastCursor) else obj.put("lastCursor", JSONObject.NULL)
        obj.put("lastSyncAt", lastSyncAt)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): SyncSettings {
            if (obj == null) return SyncSettings()
            return SyncSettings(
                dropboxLinked = obj.optBoolean("dropboxLinked", false),
                uploaderRole = obj.optBoolean("uploaderRole", true),
                lastCursor = if (obj.has("lastCursor") && !obj.isNull("lastCursor")) obj.getString("lastCursor") else null,
                lastSyncAt = obj.optLong("lastSyncAt", 0L),
            )
        }
    }
}

data class WindowSettings(
    val x: Int? = null,
    val y: Int? = null,
    val width: Int = 1200,
    val height: Int = 800,
    val isMaximized: Boolean = false,
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        if (x != null) obj.put("x", x)
        if (y != null) obj.put("y", y)
        obj.put("width", width)
        obj.put("height", height)
        obj.put("isMaximized", isMaximized)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): WindowSettings {
            if (obj == null) return WindowSettings()
            val x = if (obj.has("x") && !obj.isNull("x")) obj.getInt("x") else null
            val y = if (obj.has("y") && !obj.isNull("y")) obj.getInt("y") else null
            return WindowSettings(
                x = x,
                y = y,
                width = obj.optInt("width", 1200).coerceAtLeast(400),
                height = obj.optInt("height", 800).coerceAtLeast(300),
                isMaximized = obj.optBoolean("isMaximized", false),
            )
        }
    }
}

data class KeymapSettings(
    val nextPage: String = "PERIOD",
    val prevPage: String = "COMMA",
    val nextChapter: String = "PAGE_DOWN",
    val prevChapter: String = "PAGE_UP",
    val back: String = "ESCAPE",
    val home: String = "F1",
    val search: String = "F2",
    val toc: String = "F3",
    val settings: String = "F4",
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("nextPage", nextPage)
        obj.put("prevPage", prevPage)
        obj.put("nextChapter", nextChapter)
        obj.put("prevChapter", prevChapter)
        obj.put("back", back)
        obj.put("home", home)
        obj.put("search", search)
        obj.put("toc", toc)
        obj.put("settings", settings)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): KeymapSettings {
            if (obj == null) return KeymapSettings()
            return KeymapSettings(
                nextPage = obj.optString("nextPage", "PERIOD"),
                prevPage = obj.optString("prevPage", "COMMA"),
                nextChapter = obj.optString("nextChapter", "PAGE_DOWN"),
                prevChapter = obj.optString("prevChapter", "PAGE_UP"),
                back = obj.optString("back", "ESCAPE"),
                home = obj.optString("home", "F1"),
                search = obj.optString("search", "F2"),
                toc = obj.optString("toc", "F3"),
                settings = obj.optString("settings", "F4"),
            )
        }
    }
}

data class Settings(
    val schemaVersion: Int = 1,
    val homeFolder: String = "",
    val view: ViewSettings = ViewSettings(),
    val chapter: ChapterSettings = ChapterSettings(),
    val sync: SyncSettings = SyncSettings(),
    val window: WindowSettings = WindowSettings(),
    val keymap: KeymapSettings = KeymapSettings(),
    val lastOpenedBookKey: String? = null,
    val librarySortOption: String = "RECENT",
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("schemaVersion", schemaVersion)
        obj.put("homeFolder", homeFolder)
        obj.put("view", view.toJsonObject())
        obj.put("chapter", chapter.toJsonObject())
        obj.put("sync", sync.toJsonObject())
        obj.put("window", window.toJsonObject())
        obj.put("keymap", keymap.toJsonObject())
        if (lastOpenedBookKey != null) obj.put("lastOpenedBookKey", lastOpenedBookKey)
        obj.put("librarySortOption", librarySortOption)
        return obj.toString(2)
    }

    companion object {
        fun fromJsonString(jsonString: String): Settings {
            val obj = JSONObject(jsonString)
            return Settings(
                schemaVersion = obj.optInt("schemaVersion", 1),
                homeFolder = obj.optString("homeFolder", ""),
                view = ViewSettings.fromJsonObject(obj.optJSONObject("view")),
                chapter = ChapterSettings.fromJsonObject(obj.optJSONObject("chapter")),
                sync = SyncSettings.fromJsonObject(obj.optJSONObject("sync")),
                window = WindowSettings.fromJsonObject(obj.optJSONObject("window")),
                keymap = KeymapSettings.fromJsonObject(obj.optJSONObject("keymap")),
                lastOpenedBookKey = if (obj.has("lastOpenedBookKey") && !obj.isNull("lastOpenedBookKey")) obj.getString("lastOpenedBookKey") else null,
                librarySortOption = obj.optString("librarySortOption", "RECENT"),
            )
        }
    }
}

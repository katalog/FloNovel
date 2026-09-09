package com.moonkata.flonovel.desktop.library

import androidx.compose.ui.input.key.Key
import com.moonkata.flonovel.desktop.ui.KeymapHelper
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class KeymapSettingsTest {

    @Test
    fun defaultKeymapSettings_hasExpectedDefaults() {
        val keymap = KeymapSettings()
        assertEquals("PERIOD", keymap.nextPage)
        assertEquals("COMMA", keymap.prevPage)
        assertEquals("PAGE_DOWN", keymap.nextChapter)
        assertEquals("PAGE_UP", keymap.prevChapter)
        assertEquals("ESCAPE", keymap.back)
        assertEquals("F1", keymap.home)
        assertEquals("F2", keymap.search)
        assertEquals("F3", keymap.toc)
        assertEquals("F4", keymap.settings)
    }

    @Test
    fun keymapSettings_roundTrip_jsonPersistence() {
        val tempDir = Files.createTempDirectory("keymap_test")
        try {
            val settingsFile = tempDir.resolve("settings.json")
            val store = SettingsStore(settingsFile)

            val customKeymap = KeymapSettings(
                nextPage = "DIRECTION_RIGHT",
                prevPage = "DIRECTION_LEFT",
                nextChapter = "RIGHT_BRACKET",
                prevChapter = "LEFT_BRACKET",
                back = "BACKSPACE",
                home = "H",
                search = "SLASH",
                toc = "T",
                settings = "S",
            )
            val settings = Settings(keymap = customKeymap)
            store.save(settings)

            val loaded = store.load()
            assertEquals("DIRECTION_RIGHT", loaded.keymap.nextPage)
            assertEquals("DIRECTION_LEFT", loaded.keymap.prevPage)
            assertEquals("RIGHT_BRACKET", loaded.keymap.nextChapter)
            assertEquals("LEFT_BRACKET", loaded.keymap.prevChapter)
            assertEquals("BACKSPACE", loaded.keymap.back)
            assertEquals("H", loaded.keymap.home)
            assertEquals("SLASH", loaded.keymap.search)
            assertEquals("T", loaded.keymap.toc)
            assertEquals("S", loaded.keymap.settings)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun keymapHelper_toDisplayName() {
        assertEquals(">  (.)", KeymapHelper.toDisplayName("PERIOD"))
        assertEquals("<  (,)", KeymapHelper.toDisplayName("COMMA"))
        assertEquals("PgDn", KeymapHelper.toDisplayName("PAGE_DOWN"))
        assertEquals("PgUp", KeymapHelper.toDisplayName("PAGE_UP"))
        assertEquals("ESC", KeymapHelper.toDisplayName("ESCAPE"))
        assertEquals("F1", KeymapHelper.toDisplayName("F1"))
        assertEquals("F2", KeymapHelper.toDisplayName("F2"))
        assertEquals("F3", KeymapHelper.toDisplayName("F3"))
        assertEquals("F4", KeymapHelper.toDisplayName("F4"))
        assertEquals("Space", KeymapHelper.toDisplayName("SPACEBAR"))
        assertEquals("→", KeymapHelper.toDisplayName("DIRECTION_RIGHT"))
        assertEquals("←", KeymapHelper.toDisplayName("DIRECTION_LEFT"))
    }

    @Test
    fun keymapHelper_matchesKeyAndCodePoint() {
        // Test default settings key matches
        assertTrue(KeymapHelper.matches("PERIOD", Key.Period))
        assertTrue(KeymapHelper.matches("PERIOD", Key.Period, '>'.code))
        assertTrue(KeymapHelper.matches("COMMA", Key.Comma))
        assertTrue(KeymapHelper.matches("COMMA", Key.Comma, '<'.code))
        assertTrue(KeymapHelper.matches("PAGE_DOWN", Key.PageDown))
        assertTrue(KeymapHelper.matches("PAGE_UP", Key.PageUp))
        assertTrue(KeymapHelper.matches("ESCAPE", Key.Escape))
        assertTrue(KeymapHelper.matches("F1", Key.F1))
        assertTrue(KeymapHelper.matches("F2", Key.F2))
        assertTrue(KeymapHelper.matches("F3", Key.F3))
        assertTrue(KeymapHelper.matches("F4", Key.F4))

        // Negative match
        assertFalse(KeymapHelper.matches("F4", Key.F2))
        assertFalse(KeymapHelper.matches("PAGE_DOWN", Key.PageUp))
    }
}

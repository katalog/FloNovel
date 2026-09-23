package com.moonkata.flonovel.desktop.library

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeleteSettingsTest {

    @Test
    fun defaults_trashAndDeleteKey() {
        val settings = Settings()
        assertEquals(DeleteAction.TRASH, settings.delete.action)
        assertEquals("", settings.delete.moveFolder)
        assertEquals("DELETE", settings.keymap.deleteFile)
    }

    @Test
    fun roundTrip_throughSettingsStore() {
        val tempDir = Files.createTempDirectory("delete_settings_test")
        try {
            val store = SettingsStore(tempDir.resolve("settings.json"))
            store.save(
                Settings(
                    delete = DeleteSettings(DeleteAction.MOVE, "D:\\Read"),
                    keymap = KeymapSettings(deleteFile = "X"),
                ),
            )
            val loaded = store.load()
            assertEquals(DeleteSettings(DeleteAction.MOVE, "D:\\Read"), loaded.delete)
            assertEquals("X", loaded.keymap.deleteFile)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun settingsFromBeforeThisFeature_loadWithDefaults() {
        val loaded = Settings.fromJsonString("""{"homeFolder":"C:\\Books","keymap":{"nextPage":"PERIOD"}}""")
        assertEquals(DeleteSettings(), loaded.delete)
        assertEquals("DELETE", loaded.keymap.deleteFile)
    }

    @Test
    fun unknownAction_fallsBackToTrash() {
        val loaded = Settings.fromJsonString("""{"delete":{"action":"SHRED","moveFolder":"x"}}""")
        assertEquals(DeleteAction.TRASH, loaded.delete.action)
        assertEquals("x", loaded.delete.moveFolder)
    }

    // ── Where the library cursor lands after the open book is removed ──

    private fun item(key: String) = LibraryBookItem(
        file = File(key),
        relativePath = key,
        key = key,
        displayName = key,
        sizeBytes = 0,
        lastModified = 0,
        bookRecord = null,
    )

    @Test
    fun neighbour_isNextRow_orPreviousWhenLast() {
        val list = listOf(item("a"), item("b"), item("c"))
        assertEquals("b", LibraryScanner.neighbourAfterRemoval(list, "a")?.key)
        assertEquals("c", LibraryScanner.neighbourAfterRemoval(list, "b")?.key)
        assertEquals("b", LibraryScanner.neighbourAfterRemoval(list, "c")?.key)
    }

    @Test
    fun neighbour_noneWhenOnlyBookOrUnknown() {
        assertNull(LibraryScanner.neighbourAfterRemoval(listOf(item("a")), "a"))
        assertNull(LibraryScanner.neighbourAfterRemoval(listOf(item("a")), "zzz"))
    }
}

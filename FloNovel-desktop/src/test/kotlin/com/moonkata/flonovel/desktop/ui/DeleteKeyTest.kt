package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import com.moonkata.flonovel.desktop.library.KeymapSettings
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteKeyTest {

    private fun navigator() = ReaderNavigator(
        totalLength = 5000,
        textFitter = { from, _, _ -> from + 200 },
        initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
        initialAnchor = 1200,
    )

    @Test
    fun deleteKey_resolvesAndDisplays() {
        assertEquals("DELETE", KeymapHelper.resolveKeyIdentifier(Key.Delete))
        assertEquals("Del", KeymapHelper.toDisplayName("DELETE"))
    }

    @Test
    fun reader_deleteKey_requestsRemovalWithoutMoving() {
        val nav = navigator()
        var requested = 0
        val handled = handleKeyAction(
            key = Key.Delete,
            navigator = nav,
            advanceRatio = 0.5f,
            onDeleteFile = { requested++ },
        )
        assertTrue(handled)
        assertEquals(1, requested)
        assertEquals(1200, nav.anchor)
    }

    @Test
    fun reader_rebindingDeleteFile_movesTheAction() {
        var requested = 0
        val keymap = KeymapSettings(deleteFile = "X")

        handleKeyAction(key = Key.Delete, keymap = keymap, navigator = navigator(), advanceRatio = 0.5f, onDeleteFile = { requested++ })
        assertEquals(0, requested)

        handleKeyAction(key = Key.X, keymap = keymap, navigator = navigator(), advanceRatio = 0.5f, onDeleteFile = { requested++ })
        assertEquals(1, requested)
    }

    @Test
    fun reader_unboundDeleteFile_doesNothing() {
        var requested = 0
        val handled = handleKeyAction(
            key = Key.Delete,
            keymap = KeymapSettings(deleteFile = ""),
            navigator = navigator(),
            advanceRatio = 0.5f,
            onDeleteFile = { requested++ },
        )
        assertFalse(handled)
        assertEquals(0, requested)
    }
}

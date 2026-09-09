package com.moonkata.flonovel.desktop.platform

import androidx.compose.ui.graphics.Color
import com.moonkata.flonovel.desktop.ui.ReaderColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindowsTitleBarTest {

    @Test
    fun toColorRef_calculatesCorrectBgrFormat() {
        // Pure colors
        assertEquals(0x0000FF, WindowsTitleBar.toColorRef(Color(255, 0, 0)))
        assertEquals(0x00FF00, WindowsTitleBar.toColorRef(Color(0, 255, 0)))
        assertEquals(0xFF0000, WindowsTitleBar.toColorRef(Color(0, 0, 255)))

        // Warm Ivory: RGB(250, 247, 239) -> BGR: (239 shl 16) | (247 shl 8) | 250
        val expectedIvory = (239 shl 16) or (247 shl 8) or 250
        assertEquals(expectedIvory, WindowsTitleBar.toColorRef(ReaderColors.WarmIvory.background))

        // Dark Navy: RGB(28, 32, 40) -> BGR: (40 shl 16) | (32 shl 8) | 28
        val expectedDarkNavy = (40 shl 16) or (32 shl 8) or 28
        assertEquals(expectedDarkNavy, WindowsTitleBar.toColorRef(ReaderColors.DarkNavy.background))
    }

    @Test
    fun isDarkColor_classifiesLightAndDarkThemesCorrectly() {
        // Dark themes
        assertTrue(WindowsTitleBar.isDarkColor(ReaderColors.DarkNavy.background))
        assertTrue(WindowsTitleBar.isDarkColor(ReaderColors.SoftDarkBrown.background))

        // Light themes
        assertFalse(WindowsTitleBar.isDarkColor(ReaderColors.WarmIvory.background))
        assertFalse(WindowsTitleBar.isDarkColor(ReaderColors.SepiaCream.background))
        assertFalse(WindowsTitleBar.isDarkColor(ReaderColors.SoftGray.background))
        assertFalse(WindowsTitleBar.isDarkColor(ReaderColors.CoolLight.background))
    }

    @Test
    fun updateTitleBarColor_safelyHandlesNullOrUnopenedWindow() {
        // Must never throw an exception when window is null
        WindowsTitleBar.updateTitleBarColor(
            window = null,
            backgroundColor = ReaderColors.WarmIvory.background,
            textColor = ReaderColors.WarmIvory.text,
        )
    }
}

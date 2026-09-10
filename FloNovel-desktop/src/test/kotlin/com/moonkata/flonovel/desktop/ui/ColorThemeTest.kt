package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color
import com.moonkata.flonovel.desktop.library.ViewSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ColorThemeTest {

    private fun assertRgb(expectedR: Int, expectedG: Int, expectedB: Int, color: Color, label: String) {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        assertEquals(expectedR, r, "$label red mismatch")
        assertEquals(expectedG, g, "$label green mismatch")
        assertEquals(expectedB, b, "$label blue mismatch")
    }

    @Test
    fun sixThemes_definedInCorrectOrder() {
        val expectedCodes = listOf(
            "WARM_IVORY",
            "SEPIA_CREAM",
            "DARK_NAVY",
            "SOFT_GRAY",
            "COOL_LIGHT",
            "SOFT_DARK_BROWN",
        )
        assertEquals(6, ReaderColors.AllThemes.size)
        assertEquals(expectedCodes, ReaderColors.AllThemes.map { it.code })
    }

    @Test
    fun themeColors_matchSpecificationRgbValues() {
        // 1. Warm Ivory
        assertRgb(45, 45, 42, ReaderColors.WarmIvory.text, "WarmIvory text")
        assertRgb(250, 247, 239, ReaderColors.WarmIvory.background, "WarmIvory background")
        assertRgb(238, 222, 196, ReaderColors.WarmIvory.chapterHighlight, "WarmIvory chapterHighlight")

        // 2. Sepia Cream
        assertRgb(55, 48, 40, ReaderColors.SepiaCream.text, "SepiaCream text")
        assertRgb(245, 239, 224, ReaderColors.SepiaCream.background, "SepiaCream background")
        assertRgb(226, 208, 172, ReaderColors.SepiaCream.chapterHighlight, "SepiaCream chapterHighlight")

        // 3. Dark Navy
        assertRgb(220, 224, 230, ReaderColors.DarkNavy.text, "DarkNavy text")
        assertRgb(28, 32, 40, ReaderColors.DarkNavy.background, "DarkNavy background")
        assertRgb(44, 62, 92, ReaderColors.DarkNavy.chapterHighlight, "DarkNavy chapterHighlight")

        // 4. Soft Gray
        assertRgb(50, 52, 54, ReaderColors.SoftGray.text, "SoftGray text")
        assertRgb(242, 243, 245, ReaderColors.SoftGray.background, "SoftGray background")
        assertRgb(212, 220, 234, ReaderColors.SoftGray.chapterHighlight, "SoftGray chapterHighlight")

        // 5. Cool Light
        assertRgb(45, 48, 52, ReaderColors.CoolLight.text, "CoolLight text")
        assertRgb(235, 238, 242, ReaderColors.CoolLight.background, "CoolLight background")
        assertRgb(202, 218, 238, ReaderColors.CoolLight.chapterHighlight, "CoolLight chapterHighlight")

        // 6. Soft Dark Brown
        assertRgb(218, 211, 198, ReaderColors.SoftDarkBrown.text, "SoftDarkBrown text")
        assertRgb(38, 35, 32, ReaderColors.SoftDarkBrown.background, "SoftDarkBrown background")
        assertRgb(72, 60, 50, ReaderColors.SoftDarkBrown.chapterHighlight, "SoftDarkBrown chapterHighlight")
    }

    @Test
    fun canonicalCode_handlesLegacyAndNewCodes() {
        assertEquals("WARM_IVORY", ReaderColors.canonicalCode("LIGHT"))
        assertEquals("WARM_IVORY", ReaderColors.canonicalCode("light"))
        assertEquals("DARK_NAVY", ReaderColors.canonicalCode("DARK"))
        assertEquals("DARK_NAVY", ReaderColors.canonicalCode("dark"))
        assertEquals("SEPIA_CREAM", ReaderColors.canonicalCode("SEPIA"))
        assertEquals("SEPIA_CREAM", ReaderColors.canonicalCode("sepia"))

        assertEquals("WARM_IVORY", ReaderColors.canonicalCode("WARM_IVORY"))
        assertEquals("SEPIA_CREAM", ReaderColors.canonicalCode("sepia_cream"))
        assertEquals("DARK_NAVY", ReaderColors.canonicalCode("Dark_Navy"))
        assertEquals("SOFT_GRAY", ReaderColors.canonicalCode("soft_gray"))
        assertEquals("COOL_LIGHT", ReaderColors.canonicalCode("cool_light"))
        assertEquals("SOFT_DARK_BROWN", ReaderColors.canonicalCode("soft_dark_brown"))

        assertEquals("WARM_IVORY", ReaderColors.canonicalCode("UNKNOWN_THEME"))
        assertEquals("WARM_IVORY", ReaderColors.canonicalCode(""))
    }

    @Test
    fun forName_returnsMatchingThemeColors() {
        assertEquals(ReaderColors.WarmIvory, ReaderColors.forName("WARM_IVORY"))
        assertEquals(ReaderColors.SepiaCream, ReaderColors.forName("SEPIA_CREAM"))
        assertEquals(ReaderColors.DarkNavy, ReaderColors.forName("DARK_NAVY"))
        assertEquals(ReaderColors.SoftGray, ReaderColors.forName("SOFT_GRAY"))
        assertEquals(ReaderColors.CoolLight, ReaderColors.forName("COOL_LIGHT"))
        assertEquals(ReaderColors.SoftDarkBrown, ReaderColors.forName("SOFT_DARK_BROWN"))

        // Legacy compatibility
        assertEquals(ReaderColors.WarmIvory, ReaderColors.forName("LIGHT"))
        assertEquals(ReaderColors.DarkNavy, ReaderColors.forName("DARK"))
        assertEquals(ReaderColors.SepiaCream, ReaderColors.forName("SEPIA"))
    }

    @Test
    fun defaultTheme_isWarmIvory() {
        val settings = ViewSettings()
        assertEquals("WARM_IVORY", settings.theme)
    }
}

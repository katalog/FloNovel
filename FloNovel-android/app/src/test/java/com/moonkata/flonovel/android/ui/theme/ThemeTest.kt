package com.moonkata.flonovel.android.ui.theme

import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `deriveColorScheme` is what makes the library/TOC/search/settings screens follow the same
 * reading theme (Light/Dark/Sepia/Custom) as the reader viewer itself, instead of silently
 * following the phone's system dark mode — this covers that every preset's background/text ends
 * up matching `ReaderThemePresets` (the source of truth the viewer itself uses), and that a
 * Custom theme's dark/light base is picked correctly from its background's luminance.
 */
class ThemeTest {

    @Test
    fun sixPresets_useReaderThemePresetsColorsAndCorrectBasePalette() {
        // Light-based themes
        listOf(
            ThemePreset.WARM_IVORY to ReaderThemePresets.WARM_IVORY,
            ThemePreset.SEPIA_CREAM to ReaderThemePresets.SEPIA_CREAM,
            ThemePreset.SOFT_GRAY to ReaderThemePresets.SOFT_GRAY,
            ThemePreset.COOL_LIGHT to ReaderThemePresets.COOL_LIGHT,
        ).forEach { (preset, expectedColors) ->
            val scheme = deriveColorScheme(ReaderSettings(themePreset = preset))
            assertEquals(expectedColors.background, scheme.background)
            assertEquals(expectedColors.text, scheme.onBackground)
            assertEquals(scheme.background, scheme.surface)
            assertEquals(scheme.onBackground, scheme.onSurface)
            assertEquals(Purple40, scheme.primary)
        }

        // Dark-based themes
        listOf(
            ThemePreset.DARK_NAVY to ReaderThemePresets.DARK_NAVY,
            ThemePreset.SOFT_DARK_BROWN to ReaderThemePresets.SOFT_DARK_BROWN,
        ).forEach { (preset, expectedColors) ->
            val scheme = deriveColorScheme(ReaderSettings(themePreset = preset))
            assertEquals(expectedColors.background, scheme.background)
            assertEquals(expectedColors.text, scheme.onBackground)
            assertEquals(scheme.background, scheme.surface)
            assertEquals(scheme.onBackground, scheme.onSurface)
            assertEquals(Purple80, scheme.primary)
        }
    }

    @Test
    fun sixPresets_matchExactRgbSpecifications() {
        fun assertRgb(expectedR: Int, expectedG: Int, expectedB: Int, color: androidx.compose.ui.graphics.Color, label: String) {
            val r = (color.red * 255).toInt()
            val g = (color.green * 255).toInt()
            val b = (color.blue * 255).toInt()
            assertEquals("$label R", expectedR, r)
            assertEquals("$label G", expectedG, g)
            assertEquals("$label B", expectedB, b)
        }

        // 1. Warm Ivory
        assertRgb(45, 45, 42, ReaderThemePresets.WARM_IVORY.text, "WarmIvory text")
        assertRgb(250, 247, 239, ReaderThemePresets.WARM_IVORY.background, "WarmIvory bg")

        // 2. Sepia Cream
        assertRgb(55, 48, 40, ReaderThemePresets.SEPIA_CREAM.text, "SepiaCream text")
        assertRgb(245, 239, 224, ReaderThemePresets.SEPIA_CREAM.background, "SepiaCream bg")

        // 3. Dark Navy
        assertRgb(220, 224, 230, ReaderThemePresets.DARK_NAVY.text, "DarkNavy text")
        assertRgb(28, 32, 40, ReaderThemePresets.DARK_NAVY.background, "DarkNavy bg")

        // 4. Soft Gray
        assertRgb(50, 52, 54, ReaderThemePresets.SOFT_GRAY.text, "SoftGray text")
        assertRgb(242, 243, 245, ReaderThemePresets.SOFT_GRAY.background, "SoftGray bg")

        // 5. Cool Light
        assertRgb(45, 48, 52, ReaderThemePresets.COOL_LIGHT.text, "CoolLight text")
        assertRgb(235, 238, 242, ReaderThemePresets.COOL_LIGHT.background, "CoolLight bg")

        // 6. Soft Dark Brown
        assertRgb(218, 211, 198, ReaderThemePresets.SOFT_DARK_BROWN.text, "SoftDarkBrown text")
        assertRgb(38, 35, 32, ReaderThemePresets.SOFT_DARK_BROWN.background, "SoftDarkBrown bg")
    }

    @Test
    fun defaultPreset_isWarmIvory() {
        val settings = ReaderSettings()
        assertEquals(ThemePreset.WARM_IVORY, settings.themePreset)
    }

    @Test
    fun customPreset_withDarkBackground_usesCustomColorsAndTheDarkBasePalette() {
        val settings = ReaderSettings(
            themePreset = ThemePreset.CUSTOM,
            customBackgroundColorArgb = 0xFF000000.toInt(),
            customTextColorArgb = 0xFFFFFFFF.toInt(),
        )
        val scheme = deriveColorScheme(settings)
        assertEquals(ReaderThemePresets.forSettings(settings).background, scheme.background)
        assertEquals(ReaderThemePresets.forSettings(settings).text, scheme.onBackground)
        assertEquals(Purple80, scheme.primary)
    }

    @Test
    fun customPreset_withLightBackground_usesCustomColorsAndTheLightBasePalette() {
        val settings = ReaderSettings(
            themePreset = ThemePreset.CUSTOM,
            customBackgroundColorArgb = 0xFFFFFFFF.toInt(),
            customTextColorArgb = 0xFF000000.toInt(),
        )
        val scheme = deriveColorScheme(settings)
        assertEquals(ReaderThemePresets.forSettings(settings).background, scheme.background)
        assertEquals(ReaderThemePresets.forSettings(settings).text, scheme.onBackground)
        assertEquals(Purple40, scheme.primary)
    }
}

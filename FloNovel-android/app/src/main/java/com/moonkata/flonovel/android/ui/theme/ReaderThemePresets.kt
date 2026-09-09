package com.moonkata.flonovel.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.ThemePreset

data class ReaderColors(val background: Color, val text: Color)

object ReaderThemePresets {
    // 1. Warm Ivory
    val WARM_IVORY = ReaderColors(Color(250, 247, 239), Color(45, 45, 42))
    // 2. Sepia Cream
    val SEPIA_CREAM = ReaderColors(Color(245, 239, 224), Color(55, 48, 40))
    // 3. Dark Navy
    val DARK_NAVY = ReaderColors(Color(28, 32, 40), Color(220, 224, 230))
    // 4. Soft Gray
    val SOFT_GRAY = ReaderColors(Color(242, 243, 245), Color(50, 52, 54))
    // 5. Cool Light
    val COOL_LIGHT = ReaderColors(Color(235, 238, 242), Color(45, 48, 52))
    // 6. Soft Dark Brown
    val SOFT_DARK_BROWN = ReaderColors(Color(38, 35, 32), Color(218, 211, 198))

    fun forSettings(settings: ReaderSettings): ReaderColors = when (settings.themePreset) {
        ThemePreset.WARM_IVORY -> WARM_IVORY
        ThemePreset.SEPIA_CREAM -> SEPIA_CREAM
        ThemePreset.DARK_NAVY -> DARK_NAVY
        ThemePreset.SOFT_GRAY -> SOFT_GRAY
        ThemePreset.COOL_LIGHT -> COOL_LIGHT
        ThemePreset.SOFT_DARK_BROWN -> SOFT_DARK_BROWN
        ThemePreset.CUSTOM -> ReaderColors(
            Color(settings.customBackgroundColorArgb),
            Color(settings.customTextColorArgb),
        )
    }
}

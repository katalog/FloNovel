package com.moonkata.flonovel.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.ThemePreset

data class ReaderColors(
    val background: Color,
    val text: Color,
    val chapterHighlight: Color = Color(238, 222, 196),
)

object ReaderThemePresets {
    // 1. Warm Ivory
    val WARM_IVORY = ReaderColors(
        background = Color(250, 247, 239),
        text = Color(45, 45, 42),
        chapterHighlight = Color(238, 222, 196),
    )
    // 2. Sepia Cream
    val SEPIA_CREAM = ReaderColors(
        background = Color(245, 239, 224),
        text = Color(55, 48, 40),
        chapterHighlight = Color(226, 208, 172),
    )
    // 3. Dark Navy
    val DARK_NAVY = ReaderColors(
        background = Color(28, 32, 40),
        text = Color(220, 224, 230),
        chapterHighlight = Color(44, 62, 92),
    )
    // 4. Soft Gray
    val SOFT_GRAY = ReaderColors(
        background = Color(242, 243, 245),
        text = Color(50, 52, 54),
        chapterHighlight = Color(212, 220, 234),
    )
    // 5. Cool Light
    val COOL_LIGHT = ReaderColors(
        background = Color(235, 238, 242),
        text = Color(45, 48, 52),
        chapterHighlight = Color(202, 218, 238),
    )
    // 6. Soft Dark Brown
    val SOFT_DARK_BROWN = ReaderColors(
        background = Color(38, 35, 32),
        text = Color(218, 211, 198),
        chapterHighlight = Color(72, 60, 50),
    )

    fun forSettings(settings: ReaderSettings): ReaderColors = when (settings.themePreset) {
        ThemePreset.WARM_IVORY -> WARM_IVORY
        ThemePreset.SEPIA_CREAM -> SEPIA_CREAM
        ThemePreset.DARK_NAVY -> DARK_NAVY
        ThemePreset.SOFT_GRAY -> SOFT_GRAY
        ThemePreset.COOL_LIGHT -> COOL_LIGHT
        ThemePreset.SOFT_DARK_BROWN -> SOFT_DARK_BROWN
        ThemePreset.CUSTOM -> {
            val bg = Color(settings.customBackgroundColorArgb)
            val fg = Color(settings.customTextColorArgb)
            val highlight = Color(
                red = bg.red * 0.85f + fg.red * 0.15f,
                green = bg.green * 0.85f + fg.green * 0.15f,
                blue = bg.blue * 0.85f + fg.blue * 0.15f,
                alpha = 1f,
            )
            ReaderColors(bg, fg, highlight)
        }
    }
}

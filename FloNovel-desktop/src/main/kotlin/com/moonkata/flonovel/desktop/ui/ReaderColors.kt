package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color

data class ThemeColors(
    val background: Color,
    val text: Color,
    val chapterHighlight: Color,
    val progressText: Color,
)

data class ThemeSpec(
    val code: String,
    val stringResKey: String,
    val colors: ThemeColors,
)

object ReaderColors {
    // 1. Warm Ivory: Paper-like feeling, most comfortable for extended reading
    // Text: RGB(45, 45, 42), Background: RGB(250, 247, 239)
    val WarmIvory = ThemeColors(
        background = Color(250, 247, 239),
        text = Color(45, 45, 42),
        chapterHighlight = Color(238, 222, 196),
        progressText = Color(140, 134, 124),
    )

    // 2. Sepia Cream: Warm and soft, particularly suited for novels
    // Text: RGB(55, 48, 40), Background: RGB(245, 239, 224)
    val SepiaCream = ThemeColors(
        background = Color(245, 239, 224),
        text = Color(55, 48, 40),
        chapterHighlight = Color(226, 208, 172),
        progressText = Color(148, 133, 114),
    )

    // 3. Dark Navy: Dark mode, optimal in dim surroundings
    // Text: RGB(220, 224, 230), Background: RGB(28, 32, 40)
    val DarkNavy = ThemeColors(
        background = Color(28, 32, 40),
        text = Color(220, 224, 230),
        chapterHighlight = Color(44, 62, 92),
        progressText = Color(131, 140, 158),
    )

    // 4. Soft Gray: Modern and clean, reduced eye strain / glare
    // Text: RGB(50, 52, 54), Background: RGB(242, 243, 245)
    val SoftGray = ThemeColors(
        background = Color(242, 243, 245),
        text = Color(50, 52, 54),
        chapterHighlight = Color(212, 220, 234),
        progressText = Color(133, 136, 142),
    )

    // 5. Cool Light: Calmer than pure white, comfortable for long passages
    // Text: RGB(45, 48, 52), Background: RGB(235, 238, 242)
    val CoolLight = ThemeColors(
        background = Color(235, 238, 242),
        text = Color(45, 48, 52),
        chapterHighlight = Color(202, 218, 238),
        progressText = Color(126, 134, 145),
    )

    // 6. Soft Dark Brown: Softer dark mode than pitch black
    // Text: RGB(218, 211, 198), Background: RGB(38, 35, 32)
    val SoftDarkBrown = ThemeColors(
        background = Color(38, 35, 32),
        text = Color(218, 211, 198),
        chapterHighlight = Color(72, 60, 50),
        progressText = Color(150, 143, 132),
    )

    // Legacy aliases for backward compatibility
    val Light: ThemeColors get() = WarmIvory
    val Dark: ThemeColors get() = DarkNavy
    val Sepia: ThemeColors get() = SepiaCream

    val AllThemes: List<ThemeSpec> = listOf(
        ThemeSpec("WARM_IVORY", "settings_theme_warm_ivory", WarmIvory),
        ThemeSpec("SEPIA_CREAM", "settings_theme_sepia_cream", SepiaCream),
        ThemeSpec("DARK_NAVY", "settings_theme_dark_navy", DarkNavy),
        ThemeSpec("SOFT_GRAY", "settings_theme_soft_gray", SoftGray),
        ThemeSpec("COOL_LIGHT", "settings_theme_cool_light", CoolLight),
        ThemeSpec("SOFT_DARK_BROWN", "settings_theme_soft_dark_brown", SoftDarkBrown),
    )

    fun canonicalCode(name: String): String {
        return when (name.uppercase()) {
            "LIGHT" -> "WARM_IVORY"
            "SEPIA" -> "SEPIA_CREAM"
            "DARK" -> "DARK_NAVY"
            "WARM_IVORY", "SEPIA_CREAM", "DARK_NAVY", "SOFT_GRAY", "COOL_LIGHT", "SOFT_DARK_BROWN" -> name.uppercase()
            else -> "WARM_IVORY"
        }
    }

    fun forName(name: String): ThemeColors {
        return when (canonicalCode(name)) {
            "WARM_IVORY" -> WarmIvory
            "SEPIA_CREAM" -> SepiaCream
            "DARK_NAVY" -> DarkNavy
            "SOFT_GRAY" -> SoftGray
            "COOL_LIGHT" -> CoolLight
            "SOFT_DARK_BROWN" -> SoftDarkBrown
            else -> WarmIvory
        }
    }
}

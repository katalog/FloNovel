package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color

data class ThemeColors(
    val background: Color,
    val text: Color,
    val chapterHighlight: Color,
    val progressText: Color,
)

object ReaderColors {
    val Light = ThemeColors(
        background = Color(0xFFFBFBFB),
        text = Color(0xFF1A1A1A),
        chapterHighlight = Color(0xFFE8EEF5),
        progressText = Color(0xFF888888),
    )

    val Dark = ThemeColors(
        background = Color(0xFF1E1E1E),
        text = Color(0xFFD4D4D4),
        chapterHighlight = Color(0xFF2D3748),
        progressText = Color(0xFF888888),
    )

    val Sepia = ThemeColors(
        background = Color(0xFFF4ECD8),
        text = Color(0xFF5B4636),
        chapterHighlight = Color(0xFFEAE0C8),
        progressText = Color(0xFF9E8C76),
    )

    fun forName(name: String): ThemeColors {
        return when (name.uppercase()) {
            "DARK" -> Dark
            "SEPIA" -> Sepia
            else -> Light
        }
    }
}

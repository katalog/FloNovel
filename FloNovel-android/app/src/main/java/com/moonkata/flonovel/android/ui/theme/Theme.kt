package com.moonkata.flonovel.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.ThemePreset

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

/**
 * Derives the app-wide Material color scheme from the same reading theme
 * (Light/Dark/Sepia/Custom) that governs the reader viewer's own background/text colors
 * (`ReaderThemePresets`), so the library screen, TOC, search, and settings sheets share one
 * consistent look instead of silently following the phone's system dark mode. Only the
 * background/surface/text roles are overridden — primary/secondary/tertiary/error stay on the
 * existing light/dark palette, since re-theming button accents per reading theme is out of scope.
 * A pure function (no @Composable dependency) so it's directly unit-testable.
 */
fun deriveColorScheme(settings: ReaderSettings): ColorScheme {
    val readerColors = ReaderThemePresets.forSettings(settings)
    val isDark = when (settings.themePreset) {
        ThemePreset.DARK_NAVY, ThemePreset.SOFT_DARK_BROWN -> true
        ThemePreset.WARM_IVORY, ThemePreset.SEPIA_CREAM, ThemePreset.SOFT_GRAY, ThemePreset.COOL_LIGHT -> false
        ThemePreset.CUSTOM -> readerColors.background.luminance() < 0.5f
    }
    val base = if (isDark) DarkColorScheme else LightColorScheme
    return base.copy(
        background = readerColors.background,
        onBackground = readerColors.text,
        surface = readerColors.background,
        onSurface = readerColors.text,
        surfaceVariant = readerColors.background,
        onSurfaceVariant = readerColors.text.copy(alpha = 0.7f),
    )
}

@Composable
fun FloNovelTheme(settings: ReaderSettings, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = deriveColorScheme(settings),
        typography = Typography,
        content = content,
    )
}

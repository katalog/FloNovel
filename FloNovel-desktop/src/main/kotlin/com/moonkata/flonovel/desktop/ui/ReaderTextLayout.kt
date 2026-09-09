package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.library.ViewSettings

/**
 * Shared text layout and styling logic for reader measurement ([ComposeTextFitter])
 * and display ([ReaderView]).
 *
 * CRITICAL RULE: Measurement and rendering MUST use the exact same AnnotatedString
 * and TextStyle to prevent text truncation, line wrapping discrepancies, or layout jitter.
 */
object ReaderTextLayout {

    /**
     * Resolves the base [TextStyle] for reader measurement and rendering.
     *
     * When [emptyLineSpacingRatio] == 1.0f (default 100%), strictly preserves the original
     * lineHeightMultiplier behavior for 100% regression invariance.
     * When [emptyLineSpacingRatio] < 1.0f, sets lineHeight to [TextUnit.Unspecified] so that
     * empty lines can be scaled down via [SpanStyle] font-size without being clamped
     * to the global line height by Skia.
     */
    fun resolveTextStyle(
        viewSettings: ViewSettings,
        fontFamily: FontFamily,
        textColor: Color = Color.Unspecified,
    ): TextStyle {
        val fontSize = viewSettings.fontSizeSp.sp
        val letterSpacing = viewSettings.letterSpacing.sp
        val lineHeight = if (viewSettings.emptyLineSpacingRatio >= 1.0f) {
            (viewSettings.fontSizeSp * viewSettings.lineHeightMultiplier).sp
        } else {
            TextUnit.Unspecified
        }

        return TextStyle(
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            fontFamily = fontFamily,
        )
    }

    /**
     * Builds an [AnnotatedString] that applies chapter highlighting and empty line scaling.
     *
     * Used by BOTH [ComposeTextFitter] (measurement) and [ReaderView] (rendering) to guarantee
     * 100% pixel-perfect matching.
     */
    fun buildAnnotatedText(
        rawText: String,
        baseOffset: Int = 0,
        chapterOffsets: Set<Int> = emptySet(),
        chapterHighlightColor: Color = Color.Transparent,
        fontSizeSp: Float = 20.0f,
        emptyLineSpacingRatio: Float = 1.0f,
    ): AnnotatedString {
        if (rawText.isEmpty()) return AnnotatedString("")

        val hasChapters = chapterOffsets.isNotEmpty() && chapterHighlightColor != Color.Transparent
        val hasEmptyLineScaling = emptyLineSpacingRatio < 0.999f

        if (!hasChapters && !hasEmptyLineScaling) {
            return AnnotatedString(rawText)
        }

        val emptyFontSize = (fontSizeSp * emptyLineSpacingRatio).coerceAtLeast(3f).sp

        return buildAnnotatedString {
            append(rawText)

            // 1. Chapter highlighting (background color ONLY, no bold to prevent glyph width shifts)
            if (hasChapters) {
                for (offset in chapterOffsets) {
                    val local = offset - baseOffset
                    if (local < 0 || local >= rawText.length) continue
                    val end = rawText.indexOf('\n', local).let { if (it == -1) rawText.length else it }
                    addStyle(SpanStyle(background = chapterHighlightColor), local, end)
                }
            }

            // 2. Empty line scaling
            if (hasEmptyLineScaling) {
                var idx = 0
                while (idx < rawText.length) {
                    val nl = rawText.indexOf('\n', idx)
                    if (nl == -1) break

                    var nextNl = nl + 1
                    while (nextNl < rawText.length && (rawText[nextNl] == ' ' || rawText[nextNl] == '\t' || rawText[nextNl] == '\r')) {
                        nextNl++
                    }

                    if (nextNl < rawText.length && rawText[nextNl] == '\n') {
                        // Empty line found between nl+1 and nextNl+1 inclusive
                        addStyle(SpanStyle(fontSize = emptyFontSize), nl + 1, nextNl + 1)
                        idx = nextNl + 1
                    } else {
                        idx = nl + 1
                    }
                }
            }
        }
    }
}

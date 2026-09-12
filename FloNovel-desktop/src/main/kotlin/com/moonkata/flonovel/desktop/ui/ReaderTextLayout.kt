package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
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
     * Line break policy with phrase-based word breaking ([LineBreak.WordBreak.Phrase]).
     * Prevents mid-word line breaking in Korean (e.g. keeps "테크놀로지" and "빛줄기" intact).
     */
    val READER_LINE_BREAK = LineBreak.Paragraph

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
            fontWeight = FontWeight(viewSettings.fontWeight),
            lineBreak = READER_LINE_BREAK,
        )
    }

    /**
     * Inserts Unicode Word Joiners (U+2060) between adjacent Korean syllables in the same word.
     * This instructs the desktop layout engine (Skia) to keep words together at line ends
     * while preserving clean word-boundary breaking on spaces.
     */
    fun addWordJoiners(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length + (text.length / 3))
        for (i in text.indices) {
            sb.append(text[i])
            if (i + 1 < text.length) {
                val c1 = text[i]
                val c2 = text[i + 1]
                if (!c1.isWhitespace() && !c2.isWhitespace() &&
                    c1 in '\uAC00'..'\uD7A3' && c2 in '\uAC00'..'\uD7A3'
                ) {
                    sb.append('\u2060')
                }
            }
        }
        return sb.toString()
    }

    /**
     * Maps an offset in a Word-Joiner-annotated string back to the corresponding offset in rawText.
     */
    fun mapAnnotatedOffsetToRaw(annotated: String, annotatedOffset: Int): Int {
        var rawCount = 0
        val limit = annotatedOffset.coerceIn(0, annotated.length)
        for (i in 0 until limit) {
            if (annotated[i] != '\u2060') {
                rawCount++
            }
        }
        return rawCount
    }

    /**
     * Maps an offset in rawText to the corresponding offset in the Word-Joiner-annotated string.
     */
    fun mapRawOffsetToProcessed(rawText: String, rawOffset: Int): Int {
        var wjCount = 0
        val limit = rawOffset.coerceIn(0, rawText.length)
        for (i in 0 until limit) {
            if (i + 1 < rawText.length) {
                val c1 = rawText[i]
                val c2 = rawText[i + 1]
                if (!c1.isWhitespace() && !c2.isWhitespace() &&
                    c1 in '\uAC00'..'\uD7A3' && c2 in '\uAC00'..'\uD7A3'
                ) {
                    wjCount++
                }
            }
        }
        return limit + wjCount
    }

    /**
     * Builds an [AnnotatedString] that applies word-joiner line breaking, chapter highlighting,
     * and empty line scaling.
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

        val processedText = addWordJoiners(rawText)

        if (!hasChapters && !hasEmptyLineScaling) {
            return AnnotatedString(processedText)
        }

        val emptyFontSize = (fontSizeSp * emptyLineSpacingRatio).coerceAtLeast(3f).sp

        return buildAnnotatedString {
            append(processedText)

            // 1. Chapter highlighting (background color ONLY, no bold to prevent glyph width shifts)
            if (hasChapters) {
                for (offset in chapterOffsets) {
                    val rawLocal = offset - baseOffset
                    if (rawLocal < 0 || rawLocal >= rawText.length) continue
                    val processedLocal = mapRawOffsetToProcessed(rawText, rawLocal)
                    val end = processedText.indexOf('\n', processedLocal).let { if (it == -1) processedText.length else it }
                    addStyle(SpanStyle(background = chapterHighlightColor), processedLocal, end)
                }
            }

            // 2. Empty line scaling
            if (hasEmptyLineScaling) {
                var idx = 0
                while (idx < processedText.length) {
                    val nl = processedText.indexOf('\n', idx)
                    if (nl == -1) break

                    var nextNl = nl + 1
                    while (nextNl < processedText.length && (processedText[nextNl] == ' ' || processedText[nextNl] == '\t' || processedText[nextNl] == '\r')) {
                        nextNl++
                    }

                    if (nextNl < processedText.length && processedText[nextNl] == '\n') {
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

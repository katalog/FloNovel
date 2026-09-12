package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
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
     * Line height is explicitly computed as (fontSizeSp * lineHeightMultiplier).sp. A separate
     * "empty line spacing" ratio was tried here and rolled back: shrinking only blank lines
     * requires this lineHeight to be Unspecified (Compose clamps every line, blank or not, to
     * whatever height is set), which cancels lineHeightMultiplier for the whole document. The two
     * settings could not coexist under this API, so only the one that controls every line stayed.
     */
    fun resolveTextStyle(
        viewSettings: ViewSettings,
        fontFamily: FontFamily,
        textColor: Color = Color.Unspecified,
    ): TextStyle {
        val fontSize = viewSettings.fontSizeSp.sp
        val letterSpacing = viewSettings.letterSpacing.sp
        val lineHeight = (viewSettings.fontSizeSp * viewSettings.lineHeightMultiplier).sp

        return TextStyle(
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            fontFamily = fontFamily,
            fontWeight = FontWeight(viewSettings.fontWeight),
            lineBreak = READER_LINE_BREAK,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
            ),
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
     * Builds an [AnnotatedString] that applies word-joiner line breaking and chapter highlighting.
     *
     * Used by BOTH [ComposeTextFitter] (measurement) and [ReaderView] (rendering) to guarantee
     * 100% pixel-perfect matching.
     */
    fun buildAnnotatedText(
        rawText: String,
        baseOffset: Int = 0,
        chapterOffsets: Set<Int> = emptySet(),
        chapterHighlightColor: Color = Color.Transparent,
    ): AnnotatedString {
        if (rawText.isEmpty()) return AnnotatedString("")

        val hasChapters = chapterOffsets.isNotEmpty() && chapterHighlightColor != Color.Transparent

        val processedText = addWordJoiners(rawText)

        if (!hasChapters) {
            return AnnotatedString(processedText)
        }

        return buildAnnotatedString {
            append(processedText)

            // Chapter highlighting (background color ONLY, no bold to prevent glyph width shifts)
            for (offset in chapterOffsets) {
                val rawLocal = offset - baseOffset
                if (rawLocal < 0 || rawLocal >= rawText.length) continue
                val processedLocal = mapRawOffsetToProcessed(rawText, rawLocal)
                val end = processedText.indexOf('\n', processedLocal).let { if (it == -1) processedText.length else it }
                addStyle(SpanStyle(background = chapterHighlightColor), processedLocal, end)
            }
        }
    }
}

package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.moonkata.flonovel.desktop.reader.TextFitter

/**
 * Real [TextFitter] implementation using Compose [TextMeasurer].
 *
 * CRITICAL RULE: Measures the candidate page span as a single whole block,
 * exactly matching how Compose renders text.
 * NEVER sum per-paragraph heights — line spacing rounding differences would push
 * the last line off the screen.
 */
class ComposeTextFitter(
    val fullText: String,
    val textMeasurer: TextMeasurer,
    val style: TextStyle,
) : TextFitter {

    override fun fitForward(from: Int, widthPx: Int, heightPx: Int): Int {
        if (from >= fullText.length) return fullText.length
        if (widthPx <= 0 || heightPx <= 0) return from

        val constraints = Constraints(maxWidth = widthPx)

        // Estimated initial chunk to avoid measuring entire novel texts at once
        val fontSizeVal = if (style.fontSize.value > 0f) style.fontSize.value else 16f
        val lineHeightVal = if (style.lineHeight.value > 0f) style.lineHeight.value else (fontSizeVal * 1.5f)
        val approxCharsPerLine = (widthPx / (fontSizeVal * 0.6f)).toInt().coerceAtLeast(10)
        val approxLines = (heightPx / lineHeightVal).toInt().coerceAtLeast(1)
        var chunk = (approxCharsPerLine * approxLines * 2).coerceAtLeast(500)

        var candidateEnd = minOf(from + chunk, fullText.length)

        while (true) {
            val candidateText = fullText.substring(from, candidateEnd)
            val layout = textMeasurer.measure(
                text = AnnotatedString(candidateText),
                style = style,
                constraints = constraints,
            )

            if (layout.size.height <= heightPx) {
                // Entire candidateText fits
                if (candidateEnd == fullText.length) {
                    return fullText.length
                }
                chunk *= 2
                candidateEnd = minOf(from + chunk, fullText.length)
            } else {
                // Overflows heightPx: find the last line whose bottom coordinate fits within heightPx
                var fitLines = 0
                for (lineIndex in 0 until layout.lineCount) {
                    if (layout.getLineBottom(lineIndex) > heightPx) break
                    fitLines = lineIndex + 1
                }
                if (fitLines == 0) fitLines = 1 // Safety net: at least 1 line
                val splitAt = if (fitLines < layout.lineCount) {
                    layout.getLineStart(fitLines)
                } else {
                    layout.getLineEnd(fitLines - 1, visibleEnd = false)
                }.coerceAtLeast(1)
                return minOf(from + splitAt, fullText.length)
            }
        }
    }
}


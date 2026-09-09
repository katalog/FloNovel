package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

object ChapterHighlighter {
    /**
     * Highlights chapter header lines within [pageText] starting at [baseOffset].
     *
     * CRITICAL RULE: ONLY the background color is changed. Font weight (bold) is strictly
     * prohibited because ComposeTextFitter measures with regular weight; applying bold here
     * would widen glyphs and cause the last line on the page to be clipped.
     */
    fun highlightChapters(
        pageText: String,
        baseOffset: Int,
        chapterOffsets: Set<Int>,
        highlightColor: Color,
    ): AnnotatedString {
        if (chapterOffsets.isEmpty() || pageText.isEmpty()) {
            return AnnotatedString(pageText)
        }

        return buildAnnotatedString {
            append(pageText)
            for (offset in chapterOffsets) {
                val local = offset - baseOffset
                if (local < 0 || local >= pageText.length) continue
                val end = pageText.indexOf('\n', local).let { if (it == -1) pageText.length else it }
                // Background color ONLY — NO FontWeight.Bold!
                addStyle(SpanStyle(background = highlightColor), local, end)
            }
        }
    }
}

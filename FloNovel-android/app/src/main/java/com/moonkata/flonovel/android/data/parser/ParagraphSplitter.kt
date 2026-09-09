package com.moonkata.flonovel.android.data.parser

import com.moonkata.flonovel.android.model.Paragraph

/**
 * Splits decoded text into the paragraphs the reader lays out, one per source line.
 *
 * Character offsets are indices into the original decoded text, which is what makes a saved reading
 * position survive every layout change — see docs `04-DATA-MODEL.md` O-b.
 *
 * This was `TextReflower`, which also offered a REFLOW mode that joined single line breaks inside a
 * paragraph. That mode was removed in T-33: across the real library of 118 files it would have
 * changed **32 places out of 5.5 million paragraph breaks**, because the novels separate paragraphs
 * with blank lines and the preprocessor collapses `\n{3,}` to `\n\n` anyway. A settings toggle that
 * changes nothing is worse than no toggle.
 */
object ParagraphSplitter {

    fun split(text: String): List<Paragraph> {
        val paragraphs = mutableListOf<Paragraph>()
        val n = text.length
        var start = 0
        var i = 0
        while (i <= n) {
            if (i == n || text[i] == '\n') {
                var end = i
                // A CRLF file must not leave the '\r' hanging on the end of every paragraph.
                if (end > start && text[end - 1] == '\r') end -= 1
                paragraphs += Paragraph(text.substring(start, end), start, end)
                start = i + 1
            }
            i++
        }
        return paragraphs
    }
}

package com.moonkata.flonovel.android.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Paragraph splitting, which pagination consumes as-is — a bug here throws off every page boundary
 * and therefore the saved reading position.
 *
 * Carried over from `TextReflowerTest`'s PRESERVE half. The REFLOW half went with the mode itself in
 * T-33: on the real library it would have changed 32 places out of 5.5 million paragraph breaks.
 */
class ParagraphSplitterTest {

    @Test
    fun basicMultilineSplit() {
        val text = "첫줄\n둘째줄\n셋째줄"

        val paragraphs = ParagraphSplitter.split(text)

        assertEquals(listOf("첫줄", "둘째줄", "셋째줄"), paragraphs.map { it.text })
        assertEquals(listOf(0 to 2, 3 to 6, 7 to 10), paragraphs.map { it.startOffset to it.endOffset })
    }

    @Test
    fun crlfLineEndings_stripTrailingCr() {
        val text = "첫줄\r\n둘째줄\r\n"

        val paragraphs = ParagraphSplitter.split(text)

        // One extra trailing newline produces one extra empty paragraph, because the i == n position
        // is also a paragraph boundary. Intentional: it keeps offsets a strict 1:1 with the source.
        assertEquals(listOf("첫줄", "둘째줄", ""), paragraphs.map { it.text })
        assertEquals(listOf(0 to 2, 4 to 7, 9 to 9), paragraphs.map { it.startOffset to it.endOffset })
    }

    @Test
    fun blankLineBecomesItsOwnEmptyParagraph() {
        val text = "첫줄\n\n셋째줄\n"

        val paragraphs = ParagraphSplitter.split(text)

        assertEquals(listOf("첫줄", "", "셋째줄", ""), paragraphs.map { it.text })
    }

    @Test
    fun emptyInput_producesOneEmptyParagraph() {
        val paragraphs = ParagraphSplitter.split("")

        assertEquals(listOf(""), paragraphs.map { it.text })
        assertEquals(0, paragraphs[0].startOffset)
        assertEquals(0, paragraphs[0].endOffset)
    }

    /**
     * The whole point of keeping offsets: they index the original decoded text, so a saved position
     * still means the same character no matter how the text is laid out (docs 04-DATA-MODEL O-b).
     */
    @Test
    fun offsetsIndexTheOriginalText() {
        val text = "가나다\n라마바\n사아자"

        ParagraphSplitter.split(text).forEach { paragraph ->
            assertEquals(paragraph.text, text.substring(paragraph.startOffset, paragraph.endOffset))
        }
    }
}

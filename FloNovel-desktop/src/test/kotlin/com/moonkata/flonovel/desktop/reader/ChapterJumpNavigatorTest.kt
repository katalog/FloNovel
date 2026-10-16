package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChapterJumpNavigatorTest {

    @Test
    fun rewindLinesBefore_exactLinesCount() {
        // Construct 10 lines of text
        val lines = (1..10).map { "Line $it: Some prose content" }
        val text = lines.joinToString("\n") + "\n[Chapter 2: The Adventure]"
        val chapterOffset = text.indexOf("[Chapter 2: The Adventure]")
        assertTrue(chapterOffset > 0)

        // Rewind 6 lines before chapterOffset
        // Expected: should start at "Line 5: Some prose content"
        val expectedStartIndex = text.indexOf("Line 5: Some prose content")
        val actualStartIndex = ChapterJumpNavigator.rewindLinesBefore(text, chapterOffset, lines = 6)

        assertEquals(expectedStartIndex, actualStartIndex)
    }

    @Test
    fun rewindLinesBefore_fewerLinesThanRequested_clampsToStart() {
        val text = "Line 1\nLine 2\nLine 3\n[Chapter 1]"
        val chapterOffset = text.indexOf("[Chapter 1]")

        // Request 6 lines when only 3 exist before chapter
        val actualStartIndex = ChapterJumpNavigator.rewindLinesBefore(text, chapterOffset, lines = 6)
        assertEquals(0, actualStartIndex)
    }

    @Test
    fun rewindLinesBefore_zeroOrNegativeInputs() {
        val text = "Line 1\nLine 2\n[Chapter 1]"
        assertEquals(0, ChapterJumpNavigator.rewindLinesBefore(text, 0, lines = 6))
        assertEquals(0, ChapterJumpNavigator.rewindLinesBefore(text, 10, lines = 0))
        assertEquals(0, ChapterJumpNavigator.rewindLinesBefore(text, -5, lines = 6))
    }

    @Test
    fun rewindLinesBefore_longParagraphExceedingMaxChars_clampsToMaxCharsBefore() {
        // A single very long paragraph with no newlines
        val longParagraph = "A".repeat(500)
        val text = "$longParagraph\n[Chapter 2]"
        val chapterOffset = text.indexOf("[Chapter 2]")

        val actualStartIndex = ChapterJumpNavigator.rewindLinesBefore(
            text = text,
            targetOffset = chapterOffset,
            lines = 6,
            maxCharsBefore = 200,
        )

        // Must be clamped to within 200 chars before chapterOffset
        assertTrue(chapterOffset - actualStartIndex <= 200)
        assertTrue(actualStartIndex >= chapterOffset - 200)
    }

    @Test
    fun readerNavigator_jumpToWithLeadingAnchor_setsAnchorAndPreservesTargetInForwardStack() {
        val totalLength = 1000
        val fitter = FakeTextFitter(totalLength = totalLength)
        val spec = ViewportSpec(widthPx = 500, heightPx = 400, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(totalLength, fitter, spec, initialAnchor = 0)

        val targetChapter = 600
        val leadingAnchor = 520

        navigator.jumpTo(offset = targetChapter, leadingAnchor = leadingAnchor)

        // Current anchor is the leading anchor (showing 6 lines before chapter)
        assertEquals(leadingAnchor, navigator.anchor)

        // Forward stack must hold targetChapter
        assertEquals(listOf(targetChapter), navigator.forwardHistoryStack)

        // Next regular advance() pops from forward stack directly to targetChapter
        val nextState = navigator.advance()
        assertEquals(targetChapter, nextState.anchor)
    }
}

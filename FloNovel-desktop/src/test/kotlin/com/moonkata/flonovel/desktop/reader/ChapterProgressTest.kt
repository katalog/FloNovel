package com.moonkata.flonovel.desktop.reader

import com.moonkata.flonovel.desktop.text.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChapterProgressTest {
    private val chapters = listOf(Chapter("First", 20), Chapter("Second", 100))

    @Test
    fun shortTitleStaysUnchanged() {
        assertEquals("## 제목", compactChapterTitle("## 제목"))
    }

    @Test
    fun twentyCharacterTitleStaysUnchanged() {
        val title = "12345678901234567890"
        assertEquals(title, compactChapterTitle(title))
    }

    @Test
    fun longTitleIncludesEllipsisWithinTwentyCharacters() {
        val title = "123456789012345678901"
        assertEquals("1234567890123456789…", compactChapterTitle(title))
    }

    @Test
    fun markerDoesNotConsumeTitleBudget() {
        val title = "## 12345678901234567890"
        assertEquals(title, compactChapterTitle(title))
    }

    @Test
    fun markedLongTitleUsesTwentyCharactersAfterMarker() {
        val title = "## 123456789012345678901"
        assertEquals("## 1234567890123456789…", compactChapterTitle(title))
    }

    @Test
    fun markerWhitespaceDoesNotConsumeTitleBudget() {
        assertEquals("## 12345678901234567890", compactChapterTitle("  ##   12345678901234567890  "))
    }

    @Test
    fun titleDoesNotSplitSupplementaryCharacters() {
        val title = "😀12345678901234567890" // 1 emoji + 20 digits = 21 codepoints
        assertEquals("😀123456789012345678…", compactChapterTitle(title))
    }

    @Test
    fun chapterStart() = assertProgress(20, "First", 0f)

    @Test
    fun chapterMidpoint() = assertProgress(60, "First", 0.5f)

    @Test
    fun nextBoundaryStartsNewChapter() = assertProgress(100, "Second", 0f)

    @Test
    fun lastChapterUsesBookEnd() = assertProgress(150, "Second", 0.5f)

    @Test
    fun bookEnd() = assertProgress(200, "Second", 1f)

    @Test
    fun introduction() = assertProgress(10, null, 0.5f)

    @Test
    fun negativeOffsetClampsToStart() = assertProgress(-10, null, 0f)

    @Test
    fun offsetPastEndClampsToEnd() = assertProgress(250, "Second", 1f)

    @Test
    fun noChapters() {
        assertNull(chapterProgress(emptyList(), 50, 200))
    }

    @Test
    fun emptyText() {
        assertNull(chapterProgress(chapters, 0, 0))
    }

    @Test
    fun headingAtEndAvoidsDivisionByZero() {
        val result = chapterProgress(listOf(Chapter("End", 200)), 200, 200)!!
        assertEquals("End", result.title)
        assertEquals(0f, result.fraction)
    }

    private fun assertProgress(offset: Int, title: String?, fraction: Float) {
        val result = chapterProgress(chapters, offset, 200)!!
        assertEquals(title, result.title)
        assertEquals(fraction, result.fraction, 0.00001f)
    }
}

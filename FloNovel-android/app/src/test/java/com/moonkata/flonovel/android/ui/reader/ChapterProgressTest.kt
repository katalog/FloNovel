package com.moonkata.flonovel.android.ui.reader

import com.moonkata.flonovel.android.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterProgressTest {
    private val chapters = listOf(Chapter("First", 20), Chapter("Second", 100))

    @Test fun shortTitleStaysUnchanged() { assertEquals("## 제목", compactChapterTitle("## 제목")) }
    @Test fun sevenCharacterTitleStaysUnchanged() { assertEquals("1234567", compactChapterTitle("1234567")) }
    @Test fun longTitleIncludesEllipsisWithinSevenCharacters() {
        assertEquals("123456…", compactChapterTitle("12345678"))
    }
    @Test fun markerDoesNotConsumeTitleBudget() {
        assertEquals("## 긴제목입니다", compactChapterTitle("## 긴제목입니다"))
    }
    @Test fun markedLongTitleUsesSevenCharactersAfterMarker() {
        assertEquals("## 아주긴챕터제…", compactChapterTitle("## 아주긴챕터제목입니다"))
    }
    @Test fun markerWhitespaceDoesNotConsumeTitleBudget() {
        assertEquals("## 긴제목입니다", compactChapterTitle("  ##   긴제목입니다  "))
    }
    @Test fun titleDoesNotSplitSupplementaryCharacters() {
        assertEquals("😀12345…", compactChapterTitle("😀1234567"))
    }

    @Test fun chapterStart() = assertProgress(20, "First", 0f)
    @Test fun chapterMidpoint() = assertProgress(60, "First", 0.5f)
    @Test fun nextBoundaryStartsNewChapter() = assertProgress(100, "Second", 0f)
    @Test fun lastChapterUsesBookEnd() = assertProgress(150, "Second", 0.5f)
    @Test fun bookEnd() = assertProgress(200, "Second", 1f)
    @Test fun introduction() = assertProgress(10, null, 0.5f)
    @Test fun negativeOffsetClampsToStart() = assertProgress(-10, null, 0f)
    @Test fun offsetPastEndClampsToEnd() = assertProgress(250, "Second", 1f)
    @Test fun noChapters() { assertNull(chapterProgress(emptyList(), 50, 200)) }
    @Test fun emptyText() { assertNull(chapterProgress(chapters, 0, 0)) }
    @Test fun headingAtEndAvoidsDivisionByZero() {
        val result = chapterProgress(listOf(Chapter("End", 200)), 200, 200)!!
        assertEquals("End", result.title)
        assertEquals(0f, result.fraction, 0f)
    }

    private fun assertProgress(offset: Int, title: String?, fraction: Float) {
        val result = chapterProgress(chapters, offset, 200)!!
        assertEquals(title, result.title)
        assertEquals(fraction, result.fraction, 0.00001f)
    }
}

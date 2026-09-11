package com.moonkata.flonovel.desktop.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTest {

    @Test
    fun caseInsensitiveSubstringSearch() {
        val text = "Apple apple APPLE aPple banana"
        val results = Search.search(text, "apple")

        assertEquals(4, results.size)
        assertEquals(listOf(0, 6, 12, 18), results.map { it.charOffset })
    }

    @Test
    fun noUpperLimitOnResultCount() {
        // Build text with 1,000 matches
        val chunk = "이 문장 안에 검색단어가 들어있습니다. "
        val text = chunk.repeat(1000)

        val results = Search.search(text, "검색단어")

        assertEquals(1000, results.size, "Search must return all occurrences without artificial capping")
    }

    @Test
    fun emptyOrBlankQuery_returnsEmptyList() {
        val text = "어떤 텍스트 내용입니다."

        assertTrue(Search.search(text, "").isEmpty())
        assertTrue(Search.search(text, "   ").isEmpty())
        assertTrue(Search.search("", "단어").isEmpty())
    }

    @Test
    fun snippetCollapsesNewlinesIntoSingleSpace() {
        val text = "앞쪽 문장입니다.\n\n\n\n타깃단어\n\n\n뒤쪽 문장입니다."
        val results = Search.search(text, "타깃단어")

        assertEquals(1, results.size)
        val snippet = results[0].snippet
        assertFalse(snippet.contains('\n'), "Snippet must collapse newlines into spaces: $snippet")
        assertFalse(snippet.contains('\r'), "Snippet must not contain carriage return: $snippet")
        assertTrue(snippet.contains("타깃단어"))
    }

    @Test
    fun snippetBoundaryClampingNearStartAndEnd() {
        val text = "시작단어 그리고 끝단어"
        val startResults = Search.search(text, "시작단어", snippetRadius = 20)
        assertEquals(1, startResults.size)
        assertEquals(0, startResults[0].charOffset)

        val endResults = Search.search(text, "끝단어", snippetRadius = 20)
        assertEquals(1, endResults.size)
        assertEquals(text.indexOf("끝단어"), endResults[0].charOffset)
    }

    @Test
    fun findParagraphStart_firstParagraph_returnsZero() {
        val text = "첫 번째 문단의 내용입니다. 여기에 검색어가 있습니다."
        val keywordOffset = text.indexOf("검색어")
        assertEquals(0, Search.findParagraphStart(text, keywordOffset))
        assertEquals(0, Search.findParagraphStart(text, 0))
    }

    @Test
    fun findParagraphStart_subsequentParagraph_returnsOffsetAfterPrecedingNewline() {
        val line1 = "첫 번째 문단입니다."
        val line2 = "두 번째 문단에서 타깃 키워드가 등장합니다."
        val text = "$line1\n$line2"

        val keywordOffset = text.indexOf("타깃")
        val expectedStart = line1.length + 1 // exactly at the start of line2

        assertEquals(expectedStart, Search.findParagraphStart(text, keywordOffset))
    }

    @Test
    fun findParagraphStart_consecutiveNewlines_returnsOffsetAfterImmediatePrecedingNewline() {
        val text = "문단 1\n\n\n문단 2 검색어 포함"
        val keywordOffset = text.indexOf("검색어")
        val expectedStart = text.indexOf("문단 2")

        assertEquals(expectedStart, Search.findParagraphStart(text, keywordOffset))
    }

    @Test
    fun findParagraphStart_boundaryConditions_safeClamping() {
        val text = "단순한 텍스트"
        assertEquals(0, Search.findParagraphStart(text, -5))
        assertEquals(0, Search.findParagraphStart("", 10))
        assertEquals(0, Search.findParagraphStart(text, 500))
    }
}


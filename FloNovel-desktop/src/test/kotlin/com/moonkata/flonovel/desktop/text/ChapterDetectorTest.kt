package com.moonkata.flonovel.desktop.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChapterDetectorTest {

    // --- C1: "## " prefix with length > 60 chars MUST be recognized (regression test) ---

    @Test
    fun c1_hashPreset_recognizesLineExceeding60Chars() {
        val longTitle = ("## 123화 " + "아주 긴 챕터 제목입니다 ".repeat(5)).trimEnd() // > 60 chars
        assertTrue(longTitle.length > 60, "Test prerequisite: title length (${longTitle.length}) must exceed 60 chars")

        val text = "프롤로그 본문\n$longTitle\n123화 본문입니다.\n"
        val chapters = ChapterDetector.detect(text, ChapterPatterns.buildRules(setOf("hash")))

        assertEquals(1, chapters.size)
        assertEquals(longTitle, chapters[0].title)
        assertEquals(text.indexOf(longTitle), chapters[0].charOffset)
    }

    // --- C2: Custom regex with length > 60 chars MUST be excluded (safety net preserved) ---

    @Test
    fun c2_customRegex_excludesLineExceeding60Chars_butIncludesUnder60() {
        val customPattern = """^제\s*\d+\s*화.*$"""
        val rules = ChapterPatterns.buildRules(
            enabledPresetIds = emptySet(),
            customPatterns = listOf(customPattern),
        )

        val shortTitle = "제 1 화 짧은 제목" // <= 60 chars
        val longTitle = "제 2 화 " + "길이가 60자를 훌쩍 넘겨버린 아주 긴 줄입니다 ".repeat(3) // > 60 chars
        assertTrue(shortTitle.length <= 60)
        assertTrue(longTitle.length > 60)

        val text = "$shortTitle\n본문 1\n$longTitle\n본문 2\n"
        val chapters = ChapterDetector.detect(text, rules)

        assertEquals(1, chapters.size, "Only short custom chapter should be recognized")
        assertEquals(shortTitle, chapters[0].title)
        assertEquals(0, chapters[0].charOffset)
    }

    // --- C3: Zero matches is a normal valid state, not an error ---

    @Test
    fun c3_zeroMatches_returnsEmptyListWithoutError() {
        val text = "목차가 전혀 없는 일반 소설 본문입니다.\n줄바꿈만 있습니다.\n"
        val chapters = ChapterDetector.detect(text, ChapterPatterns.buildRules())

        assertTrue(chapters.isEmpty(), "Zero matches must return emptyList()")
    }

    @Test
    fun c3_emptyInput_returnsEmptyListWithoutError() {
        val chapters = ChapterDetector.detect("", ChapterPatterns.buildRules())
        assertTrue(chapters.isEmpty())
    }

    // --- C4: Malformed custom regex is quietly excluded while valid patterns work ---

    @Test
    fun c4_malformedCustomRegex_isQuietlyExcluded_whileValidPatternsWork() {
        val malformedPattern = "[unclosed-bracket-regex("
        val validPattern = """^Chapter\s+\d+$"""

        val rules = ChapterPatterns.buildRules(
            enabledPresetIds = emptySet(),
            customPatterns = listOf(malformedPattern, validPattern),
        )

        assertEquals(1, rules.size, "Malformed pattern should be excluded, leaving 1 valid rule")

        val text = "Chapter 1\n본문입니다.\nChapter 2\n다음 본문입니다.\n"
        val chapters = ChapterDetector.detect(text, rules)

        assertEquals(2, chapters.size)
        assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it.title })
    }

    // --- C5: Very long title (10,000+ chars) is detected and displayTitle is truncated ---

    @Test
    fun c5_extremelyLongTitle_isDetectedAndDisplayTitleIsTruncated() {
        val hugeTitle = ("## " + "엄청나게 긴 챕터 제목 ".repeat(1000)).trimEnd() // ~11,000 chars
        assertTrue(hugeTitle.length > 10_000)

        val text = "시작\n$hugeTitle\n본문 끝\n"
        val chapters = ChapterDetector.detect(text, ChapterPatterns.buildRules(setOf("hash")))

        assertEquals(1, chapters.size)
        assertEquals(hugeTitle, chapters[0].title)
        assertEquals(text.indexOf(hugeTitle), chapters[0].charOffset)

        // Verification of display title truncation for UI
        assertTrue(chapters[0].displayTitle.length <= 83)
        assertTrue(chapters[0].displayTitle.endsWith("..."))
    }

    @Test
    fun fullMatchRequired_doesNotMatchSubstringInMiddleOfLine() {
        val rules = ChapterPatterns.buildRules(
            enabledPresetIds = emptySet(),
            customPatterns = listOf("""^제\s*\d+\s*장$"""),
        )
        // Line has extra text after "제 1 장", so full match ^제\s*\d+\s*장$ should NOT match
        val text = "그는 제 1 장을 읽고 있었다.\n제 1 장\n본문\n"
        val chapters = ChapterDetector.detect(text, rules)

        assertEquals(1, chapters.size)
        assertEquals("제 1 장", chapters[0].title)
    }
}

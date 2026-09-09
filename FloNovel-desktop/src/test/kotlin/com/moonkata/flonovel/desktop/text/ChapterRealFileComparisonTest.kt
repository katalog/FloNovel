package com.moonkata.flonovel.desktop.text

import com.moonkata.flonovel.desktop.test.TestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChapterRealFileComparisonTest {

    @Test
    fun compareChapterDetectionWithAndWithoutLineLengthLimit() {
        val text = TestFixtures.loadFixture("sample_novel_chapters.txt")
        assertTrue(text.isNotEmpty(), "Fixture text must not be empty")

        val unlimitedRules = ChapterPatterns.buildRules(setOf(ChapterPatterns.PRESET_HASH))
        val limitedRules = listOf(
            ChapterPatternRule(
                id = "hash_limited",
                regex = ChapterPatterns.presetHashRegex,
                maxLineLength = 60,
            )
        )

        val unlimitedChapters = ChapterDetector.detect(text, unlimitedRules)
        val limitedChapters = ChapterDetector.detect(text, limitedRules)

        val total = unlimitedChapters.size
        val limited = limitedChapters.size
        val dropped = total - limited
        val dropRate = if (total > 0) (dropped.toDouble() / total) * 100.0 else 0.0

        println("=========================================================================================")
        println("CHAPTER DETECTION COMPARISON (No Limit vs 60-char Limit)")
        println("=========================================================================================")
        println("Fixture: sample_novel_chapters.txt")
        println("  - Without length limit (current): $total chapters")
        println("  - With 60-char limit (old Android): $limited chapters")
        println("  - Dropped chapters: $dropped (${String.format("%.1f", dropRate)}%)")

        val droppedChapters = unlimitedChapters.filter { it.title.length > 60 }
        for (ch in droppedChapters) {
            println("  - Dropped title (${ch.title.length} chars): ${ch.title}")
        }
        println("=========================================================================================")

        // Verification of §2-(4) contract:
        // The preset hash rule must NOT enforce a 60-character limit,
        // and fixture titles exceeding 60 characters must be detected.
        assertTrue(total > limited, "Unlimited rules must capture long chapter titles dropped by 60-char limit")
        assertTrue(dropped >= 2, "Fixture contains at least 2 titles > 60 chars that must be dropped by limited rules")
        assertTrue(limitedChapters.all { it.title.length <= 60 }, "All chapters in limited rules must be <= 60 chars")
    }
}

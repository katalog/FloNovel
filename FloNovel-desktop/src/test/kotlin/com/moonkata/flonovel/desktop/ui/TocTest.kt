package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import com.moonkata.flonovel.desktop.text.Chapter
import com.moonkata.flonovel.desktop.text.ChapterDetector
import com.moonkata.flonovel.desktop.text.ChapterPatterns
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Tests for T-10: Table of Contents.
 *
 * Requirements:
 * - Chapter list display, jumping to chapter position on selection.
 * - Preset '##' chapters without length limit (titles exceeding 60 characters are included).
 * - Ultra-long titles (even 10,000+ characters) are retained in detection and cleanly truncated in display.
 * - 0 chapters detected is a valid normal state: displays clean empty content without error or warning.
 * - Chapter detection must not block initial screen rendering (runs in background on Dispatchers.Default).
 * - Currently active chapter is identified and highlighted based on anchor.
 */
class TocTest {

    // --- 1. '##' preset chapters exceeding 60 characters are included ---

    @Test
    fun presetChaptersExceeding60CharsAreIncludedInList() {
        val longTitle1 = "## 제 1 장: " + "이것은 60자를 훨씬 넘어서는 아주 긴 한국어 웹소설의 챕터 제목입니다.".repeat(2)
        val longTitle2 = "## 제 2 장: " + "A".repeat(120)
        val shortTitle = "## 제 3 장: 짧은 제목"

        assertTrue(longTitle1.length > 60, "longTitle1 should exceed 60 chars")
        assertTrue(longTitle2.length > 60, "longTitle2 should exceed 60 chars")

        val text = """
            $longTitle1
            본문 1
            $longTitle2
            본문 2
            $shortTitle
            본문 3
        """.trimIndent()

        val chapters = ChapterDetector.detect(text)

        assertEquals(3, chapters.size, "All 3 chapters must be detected without dropping >60 char titles")
        assertEquals(longTitle1, chapters[0].title)
        assertEquals(longTitle2, chapters[1].title)
        assertEquals(shortTitle, chapters[2].title)
    }

    // --- 2. Ultra-long titles (10,000+ characters) are retained and truncated on display ---

    @Test
    fun ultraLongTitlesAreRetainedAndTruncatedOnDisplay() {
        val ultraLongTitle = "## Chapter Ultra Long: " + "x".repeat(12_000)
        val text = "$ultraLongTitle\nStory begins here after the massive chapter heading.\n"

        val chapters = ChapterDetector.detect(text)

        assertEquals(1, chapters.size, "Ultra long title must not be dropped by ChapterDetector")
        val chapter = chapters[0]

        // Full title is preserved for data integrity
        assertEquals(ultraLongTitle, chapter.title)
        assertEquals(ultraLongTitle.length, chapter.title.length)

        // Display title is cleanly truncated with ellipsis
        assertTrue(chapter.displayTitle.length <= 83, "Display title should be capped around 80 chars + '...'")
        assertTrue(chapter.displayTitle.endsWith("..."), "Display title must end with ellipsis")
        assertEquals(ultraLongTitle.take(80) + "...", chapter.displayTitle)
    }

    // --- 3. Zero chapters is a valid normal state ---

    @Test
    fun zeroChaptersIsAValidNormalStateWithoutErrorOrWarning() {
        val text = """
            Once upon a time in a faraway land.
            There were no chapter headings anywhere in this file.
            Just paragraph after paragraph of plain narrative text.
        """.trimIndent()

        val chapters = ChapterDetector.detect(text)

        assertTrue(chapters.isEmpty(), "Zero chapters is expected and valid")
        val currentChapter = findCurrentChapter(chapters, anchor = 50)
        assertNull(currentChapter, "Current chapter is null when chapters list is empty")
    }

    // --- 4. Current chapter identification from anchor ---

    @Test
    fun findCurrentChapter_identifiesActiveChapterCorrectly() {
        val chapters = listOf(
            Chapter("## 1장", charOffset = 0),
            Chapter("## 2장", charOffset = 500),
            Chapter("## 3장", charOffset = 1200),
            Chapter("## 4장", charOffset = 2500),
        )

        // Before first chapter offset (if anchor was negative, clamped)
        assertNull(findCurrentChapter(chapters, -10))

        // Exactly at first chapter
        assertEquals("## 1장", findCurrentChapter(chapters, 0)?.title)

        // Inside first chapter
        assertEquals("## 1장", findCurrentChapter(chapters, 250)?.title)
        assertEquals("## 1장", findCurrentChapter(chapters, 499)?.title)

        // Exactly at second chapter
        assertEquals("## 2장", findCurrentChapter(chapters, 500)?.title)

        // Inside third chapter
        assertEquals("## 3장", findCurrentChapter(chapters, 1500)?.title)

        // Past the last chapter
        assertEquals("## 4장", findCurrentChapter(chapters, 2500)?.title)
        assertEquals("## 4장", findCurrentChapter(chapters, 99999)?.title)
    }

    // --- 5. Chapter selection jumpTo clears history and updates anchor ---

    @Test
    fun selectingChapter_jumpToClearsHistoryAndUpdatesAnchor() {
        val text = (1..200).joinToString("\n") { "Line $it: Some text content here." }
        val spec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(
            totalLength = text.length,
            textFitter = { from, _, _ -> (from + 200).coerceAtMost(text.length) },
            initialSpec = spec,
            initialAnchor = 0,
        )

        // Advance a few times to build visit history
        navigator.advance(1.0f)
        navigator.advance(1.0f)
        assertEquals(2, navigator.historyStack.size)
        val prevAnchor = navigator.anchor
        assertTrue(prevAnchor > 0)

        // Jump to chapter at offset 800
        val targetOffset = 800
        val newState = navigator.jumpTo(targetOffset)

        assertEquals(targetOffset, navigator.anchor, "Navigator anchor must be target offset")
        assertEquals(targetOffset, newState.anchor)
        assertTrue(navigator.historyStack.isEmpty(), "History must be cleared on jumpTo")

        // Next layout begins from the new chapter anchor
        val layout = navigator.layoutFor(navigator.anchor, spec)
        assertEquals(targetOffset, layout.primaryPane.startOffset)
    }

    // --- 6. Background chapter detection does not block initial render ---

    @Test
    fun backgroundChapterDetection_doesNotBlockInitialRender() = runBlocking {
        val text = StringBuilder().apply {
            for (i in 1..1000) {
                append("## 제 ${i} 화\n")
                append("본문 내용입니다. 반복해서 텍스트를 구성합니다.\n".repeat(10))
            }
        }.toString()

        val spec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(
            totalLength = text.length,
            textFitter = { from, _, _ -> (from + 300).coerceAtMost(text.length) },
            initialSpec = spec,
            initialAnchor = 0,
        )

        // Initial layout calculation is INSTANT (0ms), completely independent of chapter detection
        val initialRenderTimeMs = measureTimeMillis {
            val initialLayout = navigator.layoutFor(navigator.anchor, spec)
            assertEquals(0, initialLayout.primaryPane.startOffset)
        }
        assertTrue(initialRenderTimeMs < 50, "Initial layout calculation must be instant (< 50ms), got ${initialRenderTimeMs}ms")

        // Chapter detection runs asynchronously in background without blocking
        val detectedChapters = withContext(Dispatchers.Default) {
            ChapterDetector.detect(text)
        }

        assertEquals(1000, detectedChapters.size)
        assertEquals("## 제 1 화", detectedChapters[0].title)
        assertEquals("## 제 1000 화", detectedChapters[999].title)
    }

    // --- 7. Key bindings for TOC (F3, T) ---

    @Test
    fun tocKeyBindings_F3_and_T_invokeOpenToc() {
        val navigator = ReaderNavigator(
            totalLength = 1000,
            textFitter = { from, _, _ -> from + 100 },
            initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE),
            initialAnchor = 0,
        )

        var tocOpened = false

        // F3 key opens TOC
        val handledF3 = handleKeyAction(
            key = Key.F3,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
            onOpenToc = { tocOpened = true },
        )
        assertTrue(handledF3)
        assertTrue(tocOpened)

        // T key opens TOC
        tocOpened = false
        val handledT = handleKeyAction(
            key = Key.T,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
            onOpenToc = { tocOpened = true },
        )
        assertTrue(handledT)
        assertTrue(tocOpened)
    }

    // --- 8. Large file chapter detection safety check ---

    @Test
    fun largeFileDetection_completesSafelyWithoutBlockingOrCrashing() {
        val text = com.moonkata.flonovel.desktop.test.TestFixtures.generateSyntheticNovelText(
            numChapters = 1000,
            paragraphsPerChapter = 4,
        )

        var detectedCount = 0
        val elapsedMs = measureTimeMillis {
            val chapters = ChapterDetector.detect(text)
            detectedCount = chapters.size
            assertTrue(chapters.isNotEmpty(), "Chapters should be detected in large file")
        }
        println("Synthetic large file (1000 chapters) detection: $detectedCount chapters detected in ${elapsedMs}ms")
        assertTrue(detectedCount >= 1000, "Must detect all 1000 synthetic chapters")
    }
}

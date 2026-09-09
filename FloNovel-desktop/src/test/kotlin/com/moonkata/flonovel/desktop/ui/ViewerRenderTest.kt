package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import com.moonkata.flonovel.desktop.text.TextLoader
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewerRenderTest {

    private fun createTextMeasurer(): TextMeasurer {
        return TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // --- Test 1: the source file's hash never changes after any key input or navigation (read-only contract) ---

    @Test
    fun readOnlyContract_fileHashNeverChanges() {
        val tempDir = Files.createTempDirectory("read_only_test")
        try {
            val bookPath = tempDir.resolve("sample_novel.txt")
            val originalText = "제 1 장: 모험의 시작\n그는 길을 떠났다.\n햇살이 눈부시게 내리쬐고 있었다.\n" +
                    "여러 줄의 텍스트가 이어집니다.\n".repeat(50)
            Files.writeString(bookPath, originalText)

            val originalBytes = Files.readAllBytes(bookPath)
            val originalHash = sha256(originalBytes)

            // Load book and navigate through it
            val loaded = TextLoader.load(bookPath)
            val measurer = createTextMeasurer()
            val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
            val fitter = ComposeTextFitter(loaded.text, measurer, style)

            val spec = ViewportSpec(widthPx = 500, heightPx = 400, paneMode = PaneMode.ONE)
            val navigator = ReaderNavigator(loaded.text.length, fitter, spec, initialAnchor = 0)

            // Simulate navigation operations
            navigator.advance(1.0f)
            navigator.advance(0.5f)
            navigator.retreat()
            navigator.jumpTo(200)
            navigator.retreat()

            // Verify file on disk is strictly untouched: hash and byte content are 100% identical!
            val currentBytes = Files.readAllBytes(bookPath)
            val currentHash = sha256(currentBytes)

            assertEquals(originalHash, currentHash, "Original file hash must NEVER change (read-only)")
            assertEquals(originalBytes.size, currentBytes.size)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // --- Test 2: the last line on screen is never clipped (measurement agrees with rendering) ---

    @Test
    fun lastLine_isNeverClipped_measurementMatchesRender() {
        val text = "첫 번째 문단입니다. 소설의 시작을 알리는 문장입니다.\n\n" +
                "두 번째 문단은 조금 더 긴 내용으로 이루어져 있으며, 여러 줄로 나뉘어 표시됩니다. " +
                "글자가 줄바꿈되면서 화면 높이를 채워나갑니다. ".repeat(10) +
                "\n\n세 번째 문단입니다. 끝부분입니다."

        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val viewportWidth = 400
        val viewportHeight = 300

        // Fit page forward from offset 0
        val pageEnd = fitter.fitForward(from = 0, widthPx = viewportWidth, heightPx = viewportHeight)
        assertTrue(pageEnd > 0, "Page must contain characters")

        val pageSlice = text.substring(0, pageEnd)

        // Measure the exact same slice with the exact same measurer and style as RenderView
        val layout = measurer.measure(
            text = AnnotatedString(pageSlice),
            style = style,
            constraints = Constraints(maxWidth = viewportWidth),
        )

        // CRITICAL CHECK:
        // 1. Total rendered height must not exceed viewport height
        assertTrue(
            layout.size.height <= viewportHeight,
            "Rendered height (${layout.size.height}) must not exceed viewport height ($viewportHeight)",
        )

        // 2. The bottom of the last line must be within viewport height (no clipping)
        val lastLineIndex = layout.lineCount - 1
        val lastLineBottom = layout.getLineBottom(lastLineIndex)
        assertTrue(
            lastLineBottom <= viewportHeight,
            "Last line bottom ($lastLineBottom) must be <= viewportHeight ($viewportHeight), no clipping!",
        )
    }

    // --- Test 3: text measurements stay identical with the chapter highlight background applied ---

    @Test
    fun chapterHighlight_backgroundOnly_doesNotAffectTextLayoutGeometry() {
        val pageText = "## 제 1 장\n본문 첫 번째 줄입니다.\n본문 두 번째 줄입니다.\n"
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val constraints = Constraints(maxWidth = 400)

        // 1. Plain text layout
        val plainLayout = measurer.measure(AnnotatedString(pageText), style, constraints = constraints)

        // 2. Highlighted text layout (background tint only)
        val highlighted = ChapterHighlighter.highlightChapters(
            pageText = pageText,
            baseOffset = 0,
            chapterOffsets = setOf(0),
            highlightColor = Color.Yellow,
        )
        val highlightedLayout = measurer.measure(highlighted, style, constraints = constraints)

        // Layout dimensions and line count must be identical because only background was styled (no bold)
        assertEquals(plainLayout.lineCount, highlightedLayout.lineCount)
        assertEquals(plainLayout.size.height, highlightedLayout.size.height)
        for (i in 0 until plainLayout.lineCount) {
            assertEquals(plainLayout.getLineBottom(i), highlightedLayout.getLineBottom(i))
            assertEquals(plainLayout.getLineEnd(i), highlightedLayout.getLineEnd(i))
        }
    }

    // --- Test 4: progress formatted as a percentage with one decimal (matching Android) ---

    @Test
    fun progressFormatting_matchesAndroidFormatWithOneDecimalPlace() {
        assertEquals("0.0%", ProgressFormatter.format(0.0))
        assertEquals("11.8%", ProgressFormatter.format(0.1184))
        assertEquals("50.0%", ProgressFormatter.format(0.50))
        assertEquals("99.9%", ProgressFormatter.format(0.9994))
        assertEquals("100.0%", ProgressFormatter.format(1.0))
    }

    @Test
    fun lineSplit_startsCleanlyAtNextLineStart_withoutLeavingDanglingNewline() {
        val lines = (1..20).map { "Line %02d: content of this line".format(it) }
        val text = lines.joinToString("\n") + "\n"
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        // 100px fits 4 lines (lines 1..4 = 120 chars including newlines)
        val nextStart = fitter.fitForward(0, 400, 100)
        assertEquals(120, nextStart)
        assertEquals('L', text[nextStart])
        assertTrue(text.substring(0, nextStart).endsWith("Line 04: content of this line\n"))
    }
}






package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.library.ViewSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordBreakLineLayoutTest {

    @Test
    fun testWordJoinerPaginationConsistency() {
        val sampleText = "이어서 눈앞의 풍경이 변하기 시작했다. 어둠 속에서 먼저 나타난 것은 약간의 테크놀로지 감성이 느껴지는 빛의 흐름이었고, 곧이어 중심에서 순백의 빛이 터져 나와 모든 빛줄기를 삼키더니 그의 시야 전체를 덮어버렸다."

        fun addWordJoiners(text: String): String {
            val sb = StringBuilder(text.length * 2)
            for (i in text.indices) {
                sb.append(text[i])
                if (i + 1 < text.length) {
                    val c1 = text[i]
                    val c2 = text[i + 1]
                    // Insert WJ between adjacent non-whitespace Korean/letter characters
                    if (!c1.isWhitespace() && !c2.isWhitespace() && (c1.isLetterOrDigit() || c1 in '\uAC00'..'\uD7A3') && (c2.isLetterOrDigit() || c2 in '\uAC00'..'\uD7A3')) {
                        sb.append('\u2060')
                    }
                }
            }
            return sb.toString()
        }

        fun mapAnnotatedToRaw(annotated: String, annotatedOffset: Int): Int {
            var rawCount = 0
            for (i in 0 until annotatedOffset.coerceAtMost(annotated.length)) {
                if (annotated[i] != '\u2060') {
                    rawCount++
                }
            }
            return rawCount
        }

        val resolver = createFontFamilyResolver()
        val density = Density(1f)
        val textMeasurer = androidx.compose.ui.text.TextMeasurer(resolver, density, androidx.compose.ui.unit.LayoutDirection.Ltr)
        val style = ReaderTextLayout.resolveTextStyle(ViewSettings(fontSizeSp = 20f, lineHeightMultiplier = 1.5f), FontFamily.Default)
        val constraints = Constraints(maxWidth = 300)

        var from = 0
        var pageNum = 1
        val pages = mutableListOf<String>()

        while (from < sampleText.length) {
            val candidateRaw = sampleText.substring(from)
            val candidateAnnotated = addWordJoiners(candidateRaw)
            val layout = textMeasurer.measure(
                text = AnnotatedString(candidateAnnotated),
                style = style,
                constraints = constraints,
            )

            // Height for 2 lines
            val targetHeight = layout.getLineBottom(1.coerceAtMost(layout.lineCount - 1)).toInt()
            var fitLines = 0
            for (lineIdx in 0 until layout.lineCount) {
                if (layout.getLineBottom(lineIdx) > targetHeight) break
                fitLines = lineIdx + 1
            }
            val splitAnnotated = if (fitLines < layout.lineCount) {
                layout.getLineStart(fitLines)
            } else {
                candidateAnnotated.length
            }

            val splitRaw = mapAnnotatedToRaw(candidateAnnotated, splitAnnotated)
            val pageContent = sampleText.substring(from, from + splitRaw)
            pages.add(pageContent)
            println("Page $pageNum (len ${pageContent.length}): '$pageContent'")
            from += splitRaw
            pageNum++
        }

        // Verify: concatenated pages MUST exactly equal the original sampleText!
        val reconstructed = pages.joinToString("")
        assertEquals(sampleText, reconstructed)
    }

    @Test
    fun testReaderTextLayoutWordJoinerHelpers() {
        val raw = "약간의 테크놀로지 감성"
        val joined = ReaderTextLayout.addWordJoiners(raw)
        // Check that WJ \u2060 was inserted between Korean characters
        assertTrue(joined.contains("\u2060"))
        assertTrue(joined.contains("테\u2060크\u2060놀\u2060로\u2060지"))
        assertTrue(joined.contains(" "))

        // Mapping test: raw length mapped back
        val rawLen = ReaderTextLayout.mapAnnotatedOffsetToRaw(joined, joined.length)
        assertEquals(raw.length, rawLen)

        // Mapping raw offset to processed offset and back for every position
        for (i in 0..raw.length) {
            val processedOffset = ReaderTextLayout.mapRawOffsetToProcessed(raw, i)
            val mappedBack = ReaderTextLayout.mapAnnotatedOffsetToRaw(joined, processedOffset)
            assertEquals(i, mappedBack)
        }
    }
}

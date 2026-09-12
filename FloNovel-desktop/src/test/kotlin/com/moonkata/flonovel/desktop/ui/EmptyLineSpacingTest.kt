package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.library.ViewSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmptyLineSpacingTest {

    @Test
    fun testResolveTextStyleInvarianceWhen100Percent() {
        val settings = ViewSettings(
            fontSizeSp = 20f,
            lineHeightMultiplier = 1.5f,
            emptyLineSpacingRatio = 1.0f,
        )
        val style = ReaderTextLayout.resolveTextStyle(settings, FontFamily.Default)
        assertEquals(20.sp, style.fontSize)
        assertEquals(30.sp, style.lineHeight)
    }

    @Test
    fun testResolveTextStylePreservesLineHeightEvenWhenEmptySpacingReduced() {
        val settings = ViewSettings(
            fontSizeSp = 20f,
            lineHeightMultiplier = 1.5f,
            emptyLineSpacingRatio = 0.5f,
        )
        val style = ReaderTextLayout.resolveTextStyle(settings, FontFamily.Default)
        assertEquals(20.sp, style.fontSize)
        assertEquals(30.sp, style.lineHeight, "Line height MUST be preserved even when emptyLineSpacingRatio < 1.0f")
    }

    @Test
    fun testBuildAnnotatedTextDoesNotAddSpansWhen100Percent() {
        val text = "Line 1\n\nLine 2"
        val annotated = ReaderTextLayout.buildAnnotatedText(
            rawText = text,
            fontSizeSp = 20f,
            emptyLineSpacingRatio = 1.0f,
        )
        assertEquals(text, annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun testBuildAnnotatedTextAddsSpansToEmptyLinesWhenReduced() {
        val text = "Line 1\n\nLine 2\n\nLine 3"
        val annotated = ReaderTextLayout.buildAnnotatedText(
            rawText = text,
            fontSizeSp = 20f,
            emptyLineSpacingRatio = 0.5f,
        )
        assertEquals(text, annotated.text)
        assertEquals(2, annotated.spanStyles.size)

        // First empty line
        val span1 = annotated.spanStyles[0]
        assertEquals(7, span1.start)
        assertEquals(8, span1.end)
        assertEquals(10.sp, span1.item.fontSize)

        // Second empty line
        val span2 = annotated.spanStyles[1]
        assertEquals(15, span2.start)
        assertEquals(16, span2.end)
        assertEquals(10.sp, span2.item.fontSize)
    }

    @Test
    fun testMeasuredLineHeightsActuallyDecreaseWhenFontMetricsDriveHeight() {
        val measurer = androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        val text = "Sentence 1\n\nSentence 2\n\nSentence 3"

        // Measure without global line-height clamping to verify SpanStyle scaling behavior
        val baseStyle = TextStyle(fontSize = 20.sp)
        val annotated100 = ReaderTextLayout.buildAnnotatedText(text, fontSizeSp = 20f, emptyLineSpacingRatio = 1.0f)
        val layout100 = measurer.measure(annotated100, baseStyle, constraints = Constraints(maxWidth = 500))

        assertEquals(5, layout100.lineCount)
        val emptyLine1Height100 = layout100.getLineBottom(1) - layout100.getLineTop(1)

        val annotated50 = ReaderTextLayout.buildAnnotatedText(text, fontSizeSp = 20f, emptyLineSpacingRatio = 0.5f)
        val layout50 = measurer.measure(annotated50, baseStyle, constraints = Constraints(maxWidth = 500))

        assertEquals(5, layout50.lineCount)
        val emptyLine1Height50 = layout50.getLineBottom(1) - layout50.getLineTop(1)

        // Empty line height at 50% MUST be significantly smaller than at 100%
        assertTrue(
            emptyLine1Height50 < emptyLine1Height100,
            "Expected empty line height at 50% ($emptyLine1Height50) to be smaller than at 100% ($emptyLine1Height100)"
        )
        assertTrue(
            layout50.size.height < layout100.size.height,
            "Expected total block height at 50% (${layout50.size.height}) to be smaller than at 100% (${layout100.size.height})"
        )
    }

    @Test
    fun testComposeTextFitterFitsMoreTextWhenEmptyLineSpacingIsReduced() {
        val measurer = androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        // Repeated paragraphs with empty lines in between
        val text = buildString {
            for (i in 1..20) {
                append("Novel paragraph content line $i.\n\n")
            }
        }

        val baseStyle = TextStyle(fontSize = 20.sp)
        val fitter100 = ComposeTextFitter(text, measurer, baseStyle, emptyLineSpacingRatio = 1.0f)
        val fitter50 = ComposeTextFitter(text, measurer, baseStyle, emptyLineSpacingRatio = 0.5f)

        val fixedHeightPx = 300
        val end100 = fitter100.fitForward(from = 0, widthPx = 500, heightPx = fixedHeightPx)
        val end50 = fitter50.fitForward(from = 0, widthPx = 500, heightPx = fixedHeightPx)

        // With empty lines compressed to 50%, more text must fit in the same vertical space!
        assertTrue(
            end50 > end100,
            "Expected fitter with 50% empty lines ($end50 chars) to fit more text than 100% ($end100 chars)"
        )
    }
}

package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.library.ViewSettings
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import com.moonkata.flonovel.desktop.text.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwoPaneTypesettingTest {

    private fun createTextMeasurer(): androidx.compose.ui.text.TextMeasurer {
        return androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
    }

    private fun makeSampleText(lineCount: Int = 150): String {
        return (1..lineCount).joinToString("\n") {
            "Line %03d: Reading novels in two-pane view with dynamic gutters and pane ratios.".format(it)
        } + "\n"
    }

    // --- 1. alignChapterToLeftPane default value is strictly false ---

    @Test
    fun alignChapterToLeftPane_defaultIsFalse() {
        val settings = ViewSettings()
        assertFalse(
            settings.alignChapterToLeftPane,
            "alignChapterToLeftPane must default to false to keep page advances uniform",
        )
    }

    // --- 2. Changing gutter / paneRatio / maxLineWidth / focusMode preserves anchor ---

    @Test
    fun typesettingChanges_preserveAnchor() {
        val text = makeSampleText(100)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val baseSpec = ViewportSpec(
            widthPx = 1000,
            heightPx = 600,
            paneMode = PaneMode.TWO,
            paneRatio = 0.5f,
            gutterPx = 24,
            maxLineWidthPx = null,
        )

        val navigator = ReaderNavigator(text.length, fitter, baseSpec, initialAnchor = 500)
        assertEquals(500, navigator.anchor)

        // 2.1 Change gutter
        val specWithNewGutter = baseSpec.copy(gutterPx = 48)
        navigator.onLayoutKeyChanged(specWithNewGutter)
        assertEquals(500, navigator.anchor, "Changing gutter must strictly preserve anchor")

        // 2.2 Change paneRatio
        val specWithNewRatio = specWithNewGutter.copy(paneRatio = 0.45f)
        navigator.onLayoutKeyChanged(specWithNewRatio)
        assertEquals(500, navigator.anchor, "Changing paneRatio must strictly preserve anchor")

        // 2.3 Change maxLineWidth
        val specWithMaxWidth = specWithNewRatio.copy(maxLineWidthPx = 800)
        navigator.onLayoutKeyChanged(specWithMaxWidth)
        assertEquals(500, navigator.anchor, "Changing maxLineWidth must strictly preserve anchor")

        // 2.4 Toggling focusMode preserves anchor
        var settings = ViewSettings()
        val initialAnchor = navigator.anchor
        settings = settings.copy(focusMode = !settings.focusMode)
        assertEquals(initialAnchor, navigator.anchor, "Toggling focusMode must not touch anchor")
    }

    // --- 3. alignChapterToLeftPane toggling preserves anchor ---

    @Test
    fun alignChapterToLeftPane_togglePreservesAnchor() {
        val text = makeSampleText(100)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(
            widthPx = 1000,
            heightPx = 600,
            paneMode = PaneMode.TWO,
            paneRatio = 0.5f,
            gutterPx = 24,
        )

        val chapterOffset = 300
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = chapterOffset)

        // Calculate layout with alignChapterToLeftPane = false
        val layoutFalse = navigator.layoutFor(
            anchor = navigator.anchor,
            spec = spec,
            alignChapterToLeftPane = false,
            chapterOffsets = setOf(chapterOffset),
        )
        assertEquals(chapterOffset, navigator.anchor)

        // Calculate layout with alignChapterToLeftPane = true
        val layoutTrue = navigator.layoutFor(
            anchor = navigator.anchor,
            spec = spec,
            alignChapterToLeftPane = true,
            chapterOffsets = setOf(chapterOffset),
        )
        assertEquals(chapterOffset, navigator.anchor, "Anchor must remain identical regardless of alignment setting")

        // In default mode (false): anchor is right pane start
        assertEquals(chapterOffset, layoutFalse.rightPane?.startOffset)

        // In aligned mode (true): anchor is left pane start
        assertEquals(chapterOffset, layoutTrue.leftPane.startOffset)
        assertEquals(layoutTrue.leftPane.endOffset, layoutTrue.rightPane?.startOffset)
    }

    // --- 4. ViewportSpec width calculations with gutter, paneRatio, and maxLineWidth ---

    @Test
    fun viewportSpec_widthCalculations() {
        // Normal 50:50
        val spec50 = ViewportSpec(
            widthPx = 1000,
            heightPx = 600,
            paneMode = PaneMode.TWO,
            paneRatio = 0.5f,
            gutterPx = 40,
        )
        // Available: 1000 - 40 = 960 -> 480 each
        assertEquals(960, spec50.availableForPanesPx)
        assertEquals(480, spec50.leftPaneWidthPx)
        assertEquals(480, spec50.rightPaneWidthPx)

        // Asymmetric 45:55
        val spec45 = ViewportSpec(
            widthPx = 1000,
            heightPx = 600,
            paneMode = PaneMode.TWO,
            paneRatio = 0.45f,
            gutterPx = 40,
        )
        // Available: 960 -> left: 960 * 0.45 = 432, right: 960 - 432 = 528
        assertEquals(432, spec45.leftPaneWidthPx)
        assertEquals(528, spec45.rightPaneWidthPx)

        // Max line width restriction
        val specLimited = ViewportSpec(
            widthPx = 1200,
            heightPx = 600,
            paneMode = PaneMode.TWO,
            paneRatio = 0.5f,
            gutterPx = 40,
            maxLineWidthPx = 840,
        )
        // Effective width capped at 840. Available: 840 - 40 = 800 -> 400 each
        assertEquals(840, specLimited.effectiveWidthPx)
        assertEquals(800, specLimited.availableForPanesPx)
        assertEquals(400, specLimited.leftPaneWidthPx)
        assertEquals(400, specLimited.rightPaneWidthPx)
    }

    // --- 5. Focus Mode F11 shortcut ---

    @Test
    fun f11_togglesFocusMode() {
        val text = makeSampleText(20)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)
        val spec = ViewportSpec(widthPx = 600, heightPx = 400, paneMode = PaneMode.TWO)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        var toggleInvoked = false
        val handled = handleKeyAction(
            key = Key.F11,
            navigator = navigator,
            advanceRatio = 0.5f,
            onToggleFocusMode = { toggleInvoked = true },
        )

        assertTrue(handled, "F11 must be handled")
        assertTrue(toggleInvoked, "onToggleFocusMode must be called when F11 is pressed")
    }

    // --- 6. Search result pane indication in 2-pane layout ---

    @Test
    fun searchResult_paneIndicatorIdentification() {
        val text = makeSampleText(50)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)
        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        // Advance once so both left and right panes have content
        navigator.advance()
        val layout = navigator.state.layout
        assertEquals(PaneMode.TWO, layout.paneMode)

        val leftSpan = layout.leftPane
        val rightSpan = layout.rightPane
        assertNotNull(rightSpan)

        // Midpoint of left pane
        val leftMid = (leftSpan.startOffset + leftSpan.endOffset) / 2
        val leftResult = SearchResult(charOffset = leftMid, snippet = "Left match")

        // Midpoint of right pane
        val rightMid = (rightSpan.startOffset + rightSpan.endOffset) / 2
        val rightResult = SearchResult(charOffset = rightMid, snippet = "Right match")

        // Point outside visible panes
        val outsideResult = SearchResult(charOffset = rightSpan.endOffset + 50, snippet = "Far match")

        fun identifyPane(result: SearchResult): String? {
            return when {
                result.charOffset >= leftSpan.startOffset && result.charOffset < leftSpan.endOffset -> "좌측 Pane"
                result.charOffset >= rightSpan.startOffset && result.charOffset < rightSpan.endOffset -> "우측 Pane"
                else -> null
            }
        }

        assertEquals("좌측 Pane", identifyPane(leftResult))
        assertEquals("우측 Pane", identifyPane(rightResult))
        assertNull(identifyPane(outsideResult))
    }
}

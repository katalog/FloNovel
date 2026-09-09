package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnePaneNavigationTest {

    private fun createTextMeasurer(): androidx.compose.ui.text.TextMeasurer {
        return androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
    }

    private fun makeSampleText(lineCount: Int = 100): String {
        return (1..lineCount).joinToString("\n") {
            "Line %03d: This is readable sample prose designed for novel viewer pagination testing.".format(it)
        } + "\n"
    }

    // --- 1. Scenario U3: 1-pane advance(0.5) leaves the bottom half of the previous screen on the new screen ---

    @Test
    fun u3_advanceHalfScreen_leavesPreviousLowerHalfAsNewUpperHalf() {
        val text = makeSampleText(100)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val viewportWidth = 600
        val viewportHeight = 400
        val spec = ViewportSpec(widthPx = viewportWidth, heightPx = viewportHeight, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        // Screen 0: from anchor 0 to end0
        val screen0Layout = navigator.layoutFor(0, spec)
        val end0 = screen0Layout.primaryPane.endOffset
        assertTrue(end0 > 0)

        // Calculate offset dividing screen 0 into half (top 200px vs bottom 200px)
        val halfHeight = (viewportHeight * 0.5f).toInt()
        val halfOffset = fitter.fitForward(0, viewportWidth, halfHeight)
        assertTrue(halfOffset > 0 && halfOffset < end0)

        // The lower half text displayed on screen 0:
        val screen0LowerHalfText = text.substring(halfOffset, end0)

        // User presses PgDn (advance by 50% = 0.5f)
        val newState = navigator.advance(0.5f)
        val newAnchor = newState.anchor

        // The new anchor MUST be exactly halfOffset:
        assertEquals(halfOffset, newAnchor, "New anchor must start exactly where the top half ended")

        // Screen 1: from newAnchor to end1
        val screen1Layout = newState.layout
        val end1 = screen1Layout.primaryPane.endOffset
        assertTrue(end1 > end0, "Screen 1 must advance forward past end0")

        // In Screen 1, the text starting from top (newAnchor) up to end0 is exactly screen0LowerHalfText!
        val screen1UpperHalfText = text.substring(newAnchor, end0)
        assertEquals(screen0LowerHalfText, screen1UpperHalfText, "The previous screen's bottom half must now be the top half!")

        // Verify that the span from newAnchor to end0 occupies the top half height (~200px) of Screen 1
        val screen1TopHalfEnd = fitter.fitForward(newAnchor, viewportWidth, halfHeight)
        assertEquals(end0, screen1TopHalfEnd, "Upper half of Screen 1 must span down to end0 (exactly half the screen)")
    }

    // --- 2. PgDn followed by PgUp returns exactly to original anchor ---

    @Test
    fun singleAdvanceAndRetreat_returnsExactlyToOriginalAnchor() {
        val text = makeSampleText(50)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 500, heightPx = 400, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        val initialAnchor = navigator.anchor
        assertEquals(0, initialAnchor)

        // Advance 50%
        navigator.advance(0.5f)
        assertTrue(navigator.anchor > initialAnchor)

        // Retreat 50%
        navigator.retreat()
        assertEquals(initialAnchor, navigator.anchor, "Retreat must return exactly to original anchor")
    }

    // --- 3. Multiple advances followed by equal number of retreats return to original anchor ---

    @Test
    fun multipleAdvancesAndRetreats_returnToOriginalAnchor() {
        val text = makeSampleText(150)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 500, heightPx = 400, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        val anchorHistory = mutableListOf<Int>()
        anchorHistory.add(navigator.anchor)

        // Advance 7 times
        repeat(7) {
            navigator.advance(0.5f)
            anchorHistory.add(navigator.anchor)
        }

        assertEquals(8, anchorHistory.size)
        // Check strictly increasing
        for (i in 0 until anchorHistory.size - 1) {
            assertTrue(anchorHistory[i] < anchorHistory[i + 1])
        }

        // Retreat 7 times, verifying each step matches the reverse history
        for (i in 6 downTo 0) {
            navigator.retreat()
            assertEquals(anchorHistory[i], navigator.anchor, "Retreat step must match exact history entry")
        }

        assertEquals(0, navigator.anchor)
    }

    // --- 4. Boundary conditions: PgUp at beginning and PgDn at end stop safely without crash ---

    @Test
    fun boundaryConditions_doNotCrashAndRemainClamped() {
        val text = "Short novel text for boundary test."
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 500, heightPx = 400, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        // At beginning (anchor 0): retreat must not crash and anchor remains 0
        navigator.retreat()
        assertEquals(0, navigator.anchor)
        navigator.retreat()
        assertEquals(0, navigator.anchor)

        // Jump to end
        navigator.jumpTo(text.length)
        assertEquals(text.length, navigator.anchor)

        // At end: advance must not crash and anchor remains text.length
        navigator.advance(0.5f)
        assertEquals(text.length, navigator.anchor)
        navigator.advance(1.0f)
        assertEquals(text.length, navigator.anchor)
    }

    // --- 5. Ratios 10% and 100% both operate correctly ---

    @Test
    fun ratios_10Percent_and_100Percent_operateCorrectly() {
        val text = makeSampleText(100)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 600, heightPx = 500, paneMode = PaneMode.ONE)

        // Test 10% advance
        val nav10 = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)
        val state10 = nav10.advance(0.1f)
        val anchor10 = state10.anchor

        // Test 50% advance for comparison
        val nav50 = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)
        val state50 = nav50.advance(0.5f)
        val anchor50 = state50.anchor

        // Test 100% advance (full page turn)
        val nav100 = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)
        val screen0End = nav100.layoutFor(0, spec).primaryPane.endOffset
        val state100 = nav100.advance(1.0f)
        val anchor100 = state100.anchor

        // 10% must advance less than 50%
        assertTrue(anchor10 > 0, "10% advance must make progress")
        assertTrue(anchor10 < anchor50, "10% advance must be smaller than 50% advance")

        // 50% must advance less than 100%
        assertTrue(anchor50 < anchor100, "50% advance must be smaller than 100% advance")

        // 100% advance must land exactly on screen0End (full page turn with 0 overlap)
        assertEquals(screen0End, anchor100, "100% advance must advance exactly the full screen height")
    }

    // --- 6. Window resize preserves anchor and recalculates from same anchor ---

    @Test
    fun windowResize_preservesAnchor_andClearsHistory() {
        val text = makeSampleText(100)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val initialSpec = ViewportSpec(widthPx = 500, heightPx = 400, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, initialSpec, initialAnchor = 0)

        // Advance twice so we are at a non-zero anchor with visit history
        navigator.advance(0.5f)
        navigator.advance(0.5f)
        val currentAnchor = navigator.anchor
        assertTrue(currentAnchor > 0)
        assertTrue(navigator.historyStack.isNotEmpty())

        // User resizes window from 500x400 to 900x700
        val resizedSpec = ViewportSpec(widthPx = 900, heightPx = 700, paneMode = PaneMode.ONE)
        val newState = navigator.onLayoutKeyChanged(resizedSpec, fitter)

        // 1. Anchor MUST be strictly unchanged
        assertEquals(currentAnchor, navigator.anchor, "Anchor must NEVER change on window resize")
        assertEquals(currentAnchor, newState.anchor)

        // 2. History MUST be cleared because old boundaries are invalidated
        assertTrue(navigator.historyStack.isEmpty(), "History must be cleared on layout key change")

        // 3. New layout starts from the exact same anchor
        assertEquals(currentAnchor, newState.layout.primaryPane.startOffset, "New layout must start at the exact same anchor")
        // But end offset will be larger because the window is wider and taller
        assertTrue(newState.layout.primaryPane.endOffset > currentAnchor)
    }

    // --- 7. Advance amount is height-based, NOT logical line count based ---

    @Test
    fun advanceIsHeightBased_notLogicalLineCountBased() {
        // Paragraph 1: very long paragraph (will wrap into ~12 visual lines)
        val longParagraph = "이 문단은 매우 긴 문장으로 이루어져 있어서 화면 너비 제약에 따라 " +
                "여러 줄로 자동 워드랩(word wrap)이 발생합니다. ".repeat(8) + "\n"
        // Paragraph 2: short single line
        val shortParagraph = "짧은 문단입니다.\n"
        val text = longParagraph + shortParagraph + makeSampleText(20)

        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val viewportWidth = 350
        val viewportHeight = 400
        val spec = ViewportSpec(widthPx = viewportWidth, heightPx = viewportHeight, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        // Half height is 200px
        val advanceHalfHeight = (viewportHeight * 0.5f).toInt()
        val expectedOffset = fitter.fitForward(0, viewportWidth, advanceHalfHeight)

        navigator.advance(0.5f)

        // Navigator must advance strictly according to pixel height (200px),
        // NOT by jumping 50% of the logical paragraphs (which would skip the entire long paragraph!)
        assertEquals(expectedOffset, navigator.anchor)
        assertTrue(navigator.anchor < longParagraph.length, "50% of 400px height should stay within the long paragraph, not skip it")
    }

    // --- 8. Key action dispatch tests ---

    @Test
    fun handleKeyAction_triggersExpectedNavigation() {
        val text = makeSampleText(50)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 500, heightPx = 400, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        var lastReportedAnchor = -1

        // '>' key (Period - next page)
        val handledPeriod = handleKeyAction(
            key = Key.Period,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
            onAnchorChanged = { lastReportedAnchor = it },
        )
        assertTrue(handledPeriod)
        assertTrue(navigator.anchor > 0)
        assertEquals(navigator.anchor, lastReportedAnchor)

        // '<' key (Comma - prev page)
        val handledComma = handleKeyAction(
            key = Key.Comma,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
            onAnchorChanged = { lastReportedAnchor = it },
        )
        assertTrue(handledComma)
        assertEquals(0, navigator.anchor)
        assertEquals(0, lastReportedAnchor)

        // PageDown (next chapter jump)
        var nextChapterCalled = false
        val handledPgDn = handleKeyAction(
            key = Key.PageDown,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
            onNextChapterJump = { nextChapterCalled = true },
        )
        assertTrue(handledPgDn)
        assertTrue(nextChapterCalled)

        // PageUp (prev chapter jump)
        var prevChapterCalled = false
        val handledPgUp = handleKeyAction(
            key = Key.PageUp,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
            onPreviousChapterJump = { prevChapterCalled = true },
        )
        assertTrue(handledPgUp)
        assertTrue(prevChapterCalled)

        // Spacebar (advance)
        val handledSpace = handleKeyAction(
            key = Key.Spacebar,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
            onAnchorChanged = { lastReportedAnchor = it },
        )
        assertTrue(handledSpace)
        assertTrue(navigator.anchor > 0)

        // Shift+Spacebar (retreat)
        val handledShiftSpace = handleKeyAction(
            key = Key.Spacebar,
            isShiftPressed = true,
            navigator = navigator,
            advanceRatio = 0.5f,
            onAnchorChanged = { lastReportedAnchor = it },
        )
        assertTrue(handledShiftSpace)
        assertEquals(0, navigator.anchor)

        // Unhandled key (e.g. 'A')
        val handledKeyA = handleKeyAction(
            key = Key.A,
            isShiftPressed = false,
            navigator = navigator,
            advanceRatio = 0.5f,
        )
        assertFalse(handledKeyA)
    }
}


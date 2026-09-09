package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TwoPaneNavigationTest {

    private fun createTextMeasurer(): androidx.compose.ui.text.TextMeasurer {
        return androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
    }

    private fun makeSampleText(lineCount: Int = 200): String {
        return (1..lineCount).joinToString("\n") {
            "Line %03d: Reading novels in two-pane view gives a classic book-like reading experience.".format(it)
        } + "\n"
    }

    // --- 1. M3: 2-pane > once -> New Left == Previous Right (Core Contract) ---

    @Test
    fun m3_twoPane_advanceOnce_newLeftEqualsPreviousRight() {
        val text = makeSampleText(200)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(
            widthPx = 800,
            heightPx = 500,
            paneMode = PaneMode.TWO,
            paneRatio = 0.5f,
            gutterPx = 20,
        )

        // Start at a non-zero anchor with pre-filled history or from initial anchor
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)
        // Advance twice so we have solid text in both panes
        navigator.advance()
        navigator.advance()

        val beforeState = navigator.state
        val prevLeft = beforeState.layout.leftPane
        val prevRight = beforeState.layout.rightPane
        assertNotNull(prevRight)
        assertFalse(prevRight.isEmpty)

        // Press '>' (or PgDn in 2-pane)
        val newState = navigator.advance()
        val newLeft = newState.layout.leftPane
        val newRight = newState.layout.rightPane
        assertNotNull(newRight)

        // CORE CONTRACT M3: New Left == Previous Right
        assertEquals(
            prevRight.startOffset,
            newLeft.startOffset,
            "New left start offset must equal previous right start offset",
        )
        assertEquals(
            prevRight.endOffset,
            newLeft.endOffset,
            "New left end offset must equal previous right end offset",
        )

        // Anchor is right pane's top character
        assertEquals(navigator.anchor, newRight.startOffset)
        // Continuity: left end == right start
        assertEquals(newLeft.endOffset, newRight.startOffset)
    }

    // --- 2. M4: 2-pane > then < -> Exact original position ---

    @Test
    fun m4_twoPane_advanceThenRetreat_exactOriginalPosition() {
        val text = makeSampleText(200)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 20)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)
        navigator.advance()
        navigator.advance()

        val initialAnchor = navigator.anchor
        val initialLeft = navigator.state.layout.leftPane
        val initialRight = navigator.state.layout.rightPane!!

        // Advance >
        navigator.advance()
        assertTrue(navigator.anchor > initialAnchor)

        // Retreat <
        navigator.retreat()
        assertEquals(initialAnchor, navigator.anchor, "Anchor must return exactly to original")

        val restoredLayout = navigator.state.layout
        assertEquals(initialLeft.startOffset, restoredLayout.leftPane.startOffset)
        assertEquals(initialLeft.endOffset, restoredLayout.leftPane.endOffset)
        assertEquals(initialRight.startOffset, restoredLayout.rightPane?.startOffset)
        assertEquals(initialRight.endOffset, restoredLayout.rightPane?.endOffset)
    }

    // --- 3. M5: 2-pane left end == right start (Continuity) ---

    @Test
    fun m5_twoPane_leftEndEqualsRightStart_continuity() {
        val text = makeSampleText(200)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 20)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 500)

        val layout = navigator.state.layout
        val left = layout.leftPane
        val right = layout.rightPane!!

        assertEquals(left.endOffset, right.startOffset, "Left end must equal right start without gap or overlap")
        assertEquals(navigator.anchor, right.startOffset, "Anchor must be right pane's start offset")
    }

    // --- 4. U5 Scenario: 1-pane <-> 2-pane mode switch preserves exact anchor ---

    @Test
    fun u5_modeSwitch_onePaneToTwoPane_andBack_exactAnchorPreserved() {
        val text = makeSampleText(200)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec1Pane = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.ONE)
        val spec2Pane = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 20)

        val navigator = ReaderNavigator(text.length, fitter, spec1Pane, initialAnchor = 0)
        navigator.advance(0.5f)
        navigator.advance(0.5f)

        val targetAnchor = navigator.anchor
        assertTrue(targetAnchor > 0)

        // 1-pane: screen top is targetAnchor
        val layout1 = navigator.state.layout
        assertEquals(targetAnchor, layout1.primaryPane.startOffset)

        // Switch 1-pane -> 2-pane
        val state2 = navigator.onLayoutKeyChanged(spec2Pane, fitter)

        // 1. Anchor MUST be strictly identical
        assertEquals(targetAnchor, navigator.anchor, "Anchor must be invariant across 1->2 switch")
        assertEquals(targetAnchor, state2.anchor)

        // 2. In 2-pane, right pane starts at targetAnchor
        val rightPane = state2.layout.rightPane
        assertNotNull(rightPane)
        assertEquals(targetAnchor, rightPane.startOffset, "Right pane must start at targetAnchor")

        // 3. Left pane is reverse-filled with preceding text (up to targetAnchor)
        val leftPane = state2.layout.leftPane
        assertEquals(targetAnchor, leftPane.endOffset, "Left pane must end at targetAnchor")
        assertTrue(leftPane.startOffset < leftPane.endOffset, "Left pane must contain preceding text")

        // Switch 2-pane -> 1-pane
        val state1Back = navigator.onLayoutKeyChanged(spec1Pane, fitter)

        // 1. Anchor is STILL targetAnchor
        assertEquals(targetAnchor, navigator.anchor, "Anchor must be invariant across 2->1 switch")
        assertEquals(targetAnchor, state1Back.anchor)

        // 2. 1-pane primary pane starts at targetAnchor
        assertEquals(targetAnchor, state1Back.layout.primaryPane.startOffset)
    }

    // --- 5. End of book: left filled with final content, right empty, no crash, no duplication ---

    @Test
    fun endOfBook_leftPaneFilled_rightPaneEmpty_noCrashNoDuplication() {
        val text = "Short novel with just enough text to fill one pane and a little bit more."
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 600, heightPx = 300, paneMode = PaneMode.TWO, gutterPx = 20)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        // Advance to the end of the text
        while (navigator.anchor < text.length) {
            val prevAnchor = navigator.anchor
            navigator.advance()
            if (navigator.anchor == prevAnchor) break
        }

        // At end of book:
        assertEquals(text.length, navigator.anchor, "Anchor must reach totalLength at book end")

        val endLayout = navigator.state.layout
        val left = endLayout.leftPane
        val right = endLayout.rightPane

        // Left pane contains the final text of the book
        assertTrue(left.startOffset < left.endOffset, "Left pane must contain the final content")
        assertEquals(text.length, left.endOffset, "Left pane must end at totalLength")

        // Right pane is EMPTY (does not duplicate left pane!)
        assertNotNull(right)
        assertTrue(right.isEmpty, "Right pane must be empty at the end of the book")
        assertEquals(text.length, right.startOffset)
        assertEquals(text.length, right.endOffset)

        // Advancing further at the end does NOT crash and stays at totalLength
        navigator.advance()
        assertEquals(text.length, navigator.anchor)

        // Retreating from the end returns cleanly
        navigator.retreat()
        assertTrue(navigator.anchor < text.length, "Retreat must move backwards")
    }

    // --- 6. Start of book: anchor 0 has right pane filled, left pane empty ---

    @Test
    fun startOfBook_anchor0_rightPaneFilled_leftPaneEmpty() {
        val text = makeSampleText(50)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 20)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        val layout = navigator.state.layout
        val left = layout.leftPane
        val right = layout.rightPane!!

        // At start (anchor 0):
        assertEquals(0, navigator.anchor)
        assertEquals(0, right.startOffset)
        assertTrue(right.endOffset > 0, "Right pane has text")

        // Left pane has nothing before 0, so it is empty
        assertTrue(left.isEmpty, "Left pane must be empty at book start")
        assertEquals(0, left.startOffset)
        assertEquals(0, left.endOffset)

        // Retreating at anchor 0 does not crash
        navigator.retreat()
        assertEquals(0, navigator.anchor)
    }

    // --- 7. Key actions: >, <, PgDn, PgUp, Right, Left all behave consistently in 2-pane ---

    @Test
    fun keyActions_twoPane_advanceAndRetreatConsistently() {
        val text = makeSampleText(100)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 20)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 0)

        var lastAnchor = -1

        // '>' key (Period)
        val handledPeriod = handleKeyAction(
            key = Key.Period,
            isShiftPressed = true, // '>'
            navigator = navigator,
            advanceRatio = 0.5f,
            onAnchorChanged = { lastAnchor = it },
        )
        assertTrue(handledPeriod)
        val anchorAfterPeriod = navigator.anchor
        assertTrue(anchorAfterPeriod > 0)
        assertEquals(anchorAfterPeriod, lastAnchor)

        // '<' key (Comma)
        val handledComma = handleKeyAction(
            key = Key.Comma,
            isShiftPressed = true, // '<'
            navigator = navigator,
            advanceRatio = 0.5f,
            onAnchorChanged = { lastAnchor = it },
        )
        assertTrue(handledComma)
        assertEquals(0, navigator.anchor)
        assertEquals(0, lastAnchor)

        // PgDn (Chapter jump)
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

        // PgUp (Chapter jump)
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
    }

    @Test
    fun initialOpenAtArbitraryAnchor_leftPaneFillsFullPageNotSingleLine() {
        val text = makeSampleText(200)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 20)
        // An arbitrary anchor somewhere inside the book with empty history
        val arbitraryAnchor = 4567
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = arbitraryAnchor)

        val layout = navigator.state.layout
        val left = layout.leftPane
        val right = layout.rightPane

        assertNotNull(right)
        assertEquals(arbitraryAnchor, right.startOffset)
        assertEquals(arbitraryAnchor, left.endOffset)

        // Left pane span should be a substantial page-sized span, not just 1 line (e.g. > 300 chars)
        val leftSpanLength = left.endOffset - left.startOffset
        assertTrue(
            leftSpanLength > 300,
            "Left pane should be filled with a full page of preceding text, but was only $leftSpanLength chars",
        )
        // And it must completely fit in the pane
        val forwardFromLeft = fitter.fitForward(left.startOffset, spec.leftPaneWidthPx, spec.paneHeightPx)
        assertTrue(
            forwardFromLeft >= arbitraryAnchor,
            "Text from left.startOffset to arbitraryAnchor must fit in the left pane without overflow",
        )
    }
}


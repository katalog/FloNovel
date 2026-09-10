package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovementRulesTest {

    private fun createNavigator(
        totalLength: Int = 1000,
        widthPx: Int = 100,
        heightPx: Int = 100,
        paneMode: PaneMode = PaneMode.ONE,
        initialAnchor: Int = 0,
        charWidth: Int = 10,
        lineHeight: Int = 20,
    ): Pair<ReaderNavigator, FakeTextFitter> {
        val fitter = FakeTextFitter(totalLength = totalLength, charWidth = charWidth, lineHeight = lineHeight)
        val spec = ViewportSpec(
            widthPx = widthPx,
            heightPx = heightPx,
            paneMode = paneMode,
        )
        val navigator = ReaderNavigator(
            totalLength = totalLength,
            textFitter = fitter,
            initialSpec = spec,
            initialAnchor = initialAnchor,
        )
        return navigator to fitter
    }

    // --- M1: 1-pane advance(0.5) then retreat() -> exactly back where it started ---

    @Test
    fun m1_onePane_advanceHalf_thenRetreat_exactOriginalPosition() {
        val (navigator, _) = createNavigator(initialAnchor = 100)

        // height = 100, ratio = 0.5 -> height = 50 -> 2 lines -> 20 chars
        navigator.advance(ratio = 0.5f)
        assertEquals(120, navigator.anchor)
        assertEquals(listOf(100), navigator.historyStack)

        navigator.retreat()
        assertEquals(100, navigator.anchor)
        assertTrue(navigator.historyStack.isEmpty())
    }

    // --- M2: advance N times then retreat N times -> exactly back where it started ---

    @Test
    fun m2_multipleAdvances_thenSameNumberOfRetreats_exactOriginalPosition() {
        val (navigator, _) = createNavigator(initialAnchor = 0)

        // 50 chars per page
        repeat(5) { navigator.advance() }
        assertEquals(250, navigator.anchor)
        assertEquals(listOf(0, 50, 100, 150, 200), navigator.historyStack)

        repeat(5) { navigator.retreat() }
        assertEquals(0, navigator.anchor)
        assertTrue(navigator.historyStack.isEmpty())
    }

    // --- M3: 2-pane, one > -> new left == previous right (the core contract) ---

    @Test
    fun m3_twoPane_advanceOnce_newLeftEqualsPreviousRight() {
        // In 2-pane, width = 200 -> paneWidth = 100. 10 chars/line * 5 lines = 50 chars/pane
        val (navigator, _) = createNavigator(
            widthPx = 200,
            heightPx = 100,
            paneMode = PaneMode.TWO,
            initialAnchor = 100,
        )

        val initialLayout = navigator.state.layout
        val previousRight = initialLayout.rightPane!!
        assertEquals(100, previousRight.startOffset)
        assertEquals(150, previousRight.endOffset)

        navigator.advance() // one press of >

        val newLayout = navigator.state.layout
        val newLeft = newLayout.leftPane
        val newRight = newLayout.rightPane!!

        // The core contract: new left == previous right
        assertEquals(previousRight.startOffset, newLeft.startOffset, "New left pane start must match previous right pane start")
        assertEquals(previousRight.endOffset, newLeft.endOffset, "New left pane end must match previous right pane end")

        // New right starts at new anchor (150)
        assertEquals(150, navigator.anchor)
        assertEquals(150, newRight.startOffset)
        assertEquals(200, newRight.endOffset)
    }

    // --- M4: 2-pane, > then < -> exactly back where it started ---

    @Test
    fun m4_twoPane_advanceThenRetreat_exactOriginalPosition() {
        val (navigator, _) = createNavigator(
            widthPx = 200,
            heightPx = 100,
            paneMode = PaneMode.TWO,
            initialAnchor = 100,
        )

        navigator.advance()
        assertEquals(150, navigator.anchor)

        navigator.retreat()
        assertEquals(100, navigator.anchor)

        val layout = navigator.state.layout
        assertEquals(50, layout.leftPane.startOffset)
        assertEquals(100, layout.leftPane.endOffset)
        assertEquals(100, layout.rightPane?.startOffset)
        assertEquals(150, layout.rightPane?.endOffset)
    }

    // --- M5: 2-pane, left end + 1 == right start (continuity) ---

    @Test
    fun m5_twoPane_leftEndEqualsRightStart_continuity() {
        val (navigator, _) = createNavigator(
            widthPx = 200,
            heightPx = 100,
            paneMode = PaneMode.TWO,
            initialAnchor = 250,
        )

        val layout = navigator.state.layout
        val left = layout.leftPane
        val right = layout.rightPane!!

        // In 0-based character offsets: last char of left pane is left.endOffset - 1.
        // Therefore left.endOffset == right.startOffset (no gap, no overlap).
        assertEquals(left.endOffset, right.startOffset)
        assertEquals(navigator.anchor, right.startOffset)
    }

    // --- M6: retreat() with empty history -> back-calculation works, no crash ---

    @Test
    fun m6_emptyHistory_retreat_reverseEstimationWorksWithoutCrash() {
        val (navigator, _) = createNavigator(initialAnchor = 200)
        assertTrue(navigator.historyStack.isEmpty(), "Prerequisite: history stack must be empty")

        navigator.retreat()

        assertTrue(navigator.anchor < 200, "Retreat must move backwards")
        assertEquals(150, navigator.anchor, "With 50 chars/page, reverse estimation should reach 150")
    }

    // --- M7: advance once after a back-calculation -> history refills with the exact value ---

    @Test
    fun m7_afterReverseEstimation_advanceOnce_historyIsFilledWithExactValue() {
        val (navigator, _) = createNavigator(initialAnchor = 200)
        assertTrue(navigator.historyStack.isEmpty())

        navigator.retreat() // Reverse estimated to 150
        assertEquals(150, navigator.anchor)

        navigator.advance() // Advance forward from 150 -> 200
        assertEquals(200, navigator.anchor)
        assertEquals(listOf(150), navigator.historyStack, "History stack must now contain exact previous anchor")

        navigator.retreat() // Exact retreat using history
        assertEquals(150, navigator.anchor)
    }

    // --- M8: retreat() at the start of the book -> stops at 0, never negative ---

    @Test
    fun m8_retreatAtStartOfBook_stopsAtZeroWithoutNegativeOffset() {
        val (navigator, _) = createNavigator(initialAnchor = 0)

        navigator.retreat()
        assertEquals(0, navigator.anchor)

        // Even with repeated retreats
        repeat(3) { navigator.retreat() }
        assertEquals(0, navigator.anchor)
        assertTrue(navigator.anchor >= 0)
    }

    // --- M9: advance() at the end of the book -> stops at the end, never past it ---

    @Test
    fun m9_advanceAtEndOfBook_stopsAtEndWithoutOverflow() {
        val (navigator, _) = createNavigator(totalLength = 500, initialAnchor = 500)

        navigator.advance()
        assertEquals(500, navigator.anchor)
        assertTrue(navigator.anchor <= 500)
    }

    // --- M10: fitForward reports no progress -> still advance at least one character (no infinite loop) ---

    @Test
    fun m10_fitForwardNoProgress_guaranteesAtLeastOneCharacterAdvance() {
        val (navigator, fitter) = createNavigator(initialAnchor = 10)
        fitter.forceNoProgress = true // Forces fitForward to return `from`

        navigator.advance()
        assertEquals(11, navigator.anchor, "Must advance by at least 1 character to prevent infinite loop")

        navigator.advance()
        assertEquals(12, navigator.anchor)
    }

    // --- M11: empty file / single-character file -> no crash ---

    @Test
    fun m11_emptyFileAndOneCharFile_noCrash() {
        // Empty file (totalLength = 0)
        val (navEmpty, _) = createNavigator(totalLength = 0, initialAnchor = 0)
        navEmpty.advance()
        assertEquals(0, navEmpty.anchor)
        navEmpty.retreat()
        assertEquals(0, navEmpty.anchor)
        navEmpty.jumpTo(10)
        assertEquals(0, navEmpty.anchor)

        // 1-char file (totalLength = 1)
        val (navOne, _) = createNavigator(totalLength = 1, initialAnchor = 0)
        navOne.advance()
        assertEquals(1, navOne.anchor)
        navOne.advance()
        assertEquals(1, navOne.anchor)
        navOne.retreat()
        assertEquals(0, navOne.anchor)
    }

    // --- M12: zero-sized viewport -> no crash ---

    @Test
    fun m12_zeroViewportSize_noCrash() {
        val (navigator, _) = createNavigator(widthPx = 0, heightPx = 0, initialAnchor = 10)

        navigator.advance()
        assertEquals(11, navigator.anchor, "Should guarantee progress by 1 char without crashing")

        navigator.retreat()
        assertEquals(10, navigator.anchor)
    }

    // --- M13: advance ratio 10% / 100% -> both work ---

    @Test
    fun m13_advanceRatio10PercentAnd100Percent_operateSeparately() {
        // height = 200 -> 10 lines * 10 chars = 100 chars per full page
        val (nav10, _) = createNavigator(widthPx = 100, heightPx = 200, initialAnchor = 0)
        val (nav100, _) = createNavigator(widthPx = 100, heightPx = 200, initialAnchor = 0)

        // 10% of 200px = 20px = 1 line = 10 chars
        nav10.advance(ratio = 0.10f)
        assertEquals(10, nav10.anchor)

        // 100% of 200px = 200px = 10 lines = 100 chars
        nav100.advance(ratio = 1.00f)
        assertEquals(100, nav100.anchor)
    }

    // --- M14: history cleared after a jump -> search, TOC and chapter jumps ---

    @Test
    fun m14_jumpTo_clearsHistoryStack() {
        val (navigator, _) = createNavigator(initialAnchor = 0)
        repeat(3) { navigator.advance() }
        assertEquals(listOf(0, 50, 100), navigator.historyStack)

        // Jump to TOC / search / remote sync offset
        navigator.jumpTo(350)

        assertEquals(350, navigator.anchor)
        assertTrue(navigator.historyStack.isEmpty(), "History stack must be cleared upon jumpTo")
    }

    @Test
    fun progressCalculation_returnsAccurateRatio() {
        val (navigator, _) = createNavigator(totalLength = 1000, initialAnchor = 250)
        assertEquals(0.25, navigator.progress())

        navigator.jumpTo(500)
        assertEquals(0.50, navigator.progress())
    }

    // --- M15: Forward stack restores exact jump target after retreat then advance ---

    @Test
    fun m15_afterJump_retreatThenAdvance_returnsExactlyToJumpTarget() {
        val (navigator, _) = createNavigator(initialAnchor = 0)
        navigator.jumpTo(350) // e.g. chapter jump, TOC, or search result
        assertEquals(350, navigator.anchor)
        assertTrue(navigator.historyStack.isEmpty())
        assertTrue(navigator.forwardHistoryStack.isEmpty())

        navigator.retreat() // Retreats backwards (reverse estimation)
        val retreatedAnchor = navigator.anchor
        assertTrue(retreatedAnchor < 350, "Must have moved backward")
        assertEquals(listOf(350), navigator.forwardHistoryStack, "Forward stack must preserve jump target 350")

        // Advance even with a 0.5 ratio (half page) must restore the exact 350 anchor
        navigator.advance(ratio = 0.5f)
        assertEquals(350, navigator.anchor, "Advance must return to exactly 350 using forward stack")
        assertEquals(listOf(retreatedAnchor), navigator.historyStack, "History must now have retreated anchor")
        assertTrue(navigator.forwardHistoryStack.isEmpty(), "Forward stack should now be empty")
    }

    // --- M16: Multiple retreats followed by multiple advances restore exact sequence ---

    @Test
    fun m16_multipleRetreats_thenMultipleAdvances_restoresExactSequence() {
        val (navigator, _) = createNavigator(initialAnchor = 0)
        navigator.jumpTo(400)

        // 2 retreats back
        navigator.retreat() // e.g. 350
        val a1 = navigator.anchor
        navigator.retreat() // e.g. 300
        val a2 = navigator.anchor

        assertEquals(listOf(400, a1), navigator.forwardHistoryStack)

        // 2 advances forward restore exact sequence
        navigator.advance(ratio = 0.5f)
        assertEquals(a1, navigator.anchor)

        navigator.advance(ratio = 0.5f)
        assertEquals(400, navigator.anchor)
        assertTrue(navigator.forwardHistoryStack.isEmpty())
    }

    // --- M17: New jumpTo clears both history and forwardStack ---

    @Test
    fun m17_jumpTo_clearsBothHistoryAndForwardStack() {
        val (navigator, _) = createNavigator(initialAnchor = 0)
        navigator.jumpTo(300)
        navigator.retreat()
        assertTrue(navigator.forwardHistoryStack.isNotEmpty())

        // User jumps to another chapter/search match instead of continuing forward
        navigator.jumpTo(500)
        assertEquals(500, navigator.anchor)
        assertTrue(navigator.historyStack.isEmpty(), "History must be cleared on jumpTo")
        assertTrue(navigator.forwardHistoryStack.isEmpty(), "Forward stack must be cleared on jumpTo")
    }

    // --- M18: onLayoutKeyChanged clears forwardStack ---

    @Test
    fun m18_onLayoutKeyChanged_clearsForwardStack() {
        val (navigator, fitter) = createNavigator(initialAnchor = 0)
        navigator.jumpTo(300)
        navigator.retreat()
        assertTrue(navigator.forwardHistoryStack.isNotEmpty())

        val newSpec = ViewportSpec(widthPx = 150, heightPx = 150, paneMode = PaneMode.ONE)
        navigator.onLayoutKeyChanged(newSpec, fitter)
        assertTrue(navigator.forwardHistoryStack.isEmpty(), "Forward stack must be cleared on layout change")
    }
}

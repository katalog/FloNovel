package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [ReaderNavigator.previewAdvanceCharCount], which auto page-turn uses to size its
 * wait to what a turn will actually reveal. It must exactly match what [ReaderNavigator.advance]
 * would do, without mutating anchor/history — these tests advance for real afterward to verify
 * the preview didn't lie.
 */
class PreviewAdvanceCharCountTest {

    private fun createNavigator(
        totalLength: Int = 1000,
        widthPx: Int = 100,
        heightPx: Int = 100,
        paneMode: PaneMode = PaneMode.ONE,
        initialAnchor: Int = 0,
        charWidth: Int = 10,
        lineHeight: Int = 20,
        forceNoProgress: Boolean = false,
    ): Pair<ReaderNavigator, FakeTextFitter> {
        val fitter = FakeTextFitter(totalLength = totalLength, charWidth = charWidth, lineHeight = lineHeight, forceNoProgress = forceNoProgress)
        val spec = ViewportSpec(widthPx = widthPx, heightPx = heightPx, paneMode = paneMode)
        val navigator = ReaderNavigator(totalLength = totalLength, textFitter = fitter, initialSpec = spec, initialAnchor = initialAnchor)
        return navigator to fitter
    }

    @Test
    fun onePane_matchesActualAdvance_andDoesNotMutateState() {
        // width=100/charWidth=10 -> 10 chars/line; height*0.5=50/lineHeight=20 -> 2 lines -> 20 chars
        val (navigator, _) = createNavigator(initialAnchor = 100)

        val preview = navigator.previewAdvanceCharCount(ratio = 0.5f)
        assertEquals(20, preview)
        assertEquals(100, navigator.anchor) // preview must not mutate anchor

        navigator.advance(ratio = 0.5f)
        assertEquals(100 + preview, navigator.anchor)
    }

    @Test
    fun onePane_halfOfFullRatioAdvance_underDefaultRatio() {
        // height=200 divides evenly at both ratios (10 lines at 1.0, 5 lines at 0.5), so only
        // half of what's on screen (ratio=1.0 span) is new per turn at the default ratio=0.5 —
        // this is the property that made every auto page-turn speed feel ~2x slower than
        // labeled before previewAdvanceCharCount replaced the full-visible-span calculation.
        val (navigator, _) = createNavigator(heightPx = 200, initialAnchor = 0)

        val fullScreenSpan = navigator.previewAdvanceCharCount(ratio = 1.0f)
        val halfRatioAdvance = navigator.previewAdvanceCharCount(ratio = 0.5f)

        assertEquals(100, fullScreenSpan)
        assertEquals(50, halfRatioAdvance)
        assertEquals(fullScreenSpan / 2, halfRatioAdvance)
    }

    @Test
    fun twoPane_matchesActualAdvance_andDoesNotMutateState() {
        // width=200 -> paneWidth=100 -> 10 chars/line; height=100/lineHeight=20 -> 5 lines -> 50 chars
        val (navigator, _) = createNavigator(widthPx = 200, heightPx = 100, paneMode = PaneMode.TWO, initialAnchor = 0)

        val preview = navigator.previewAdvanceCharCount()
        assertEquals(50, preview)
        assertEquals(0, navigator.anchor)

        navigator.advance()
        assertEquals(preview, navigator.anchor)
    }

    @Test
    fun returnsZero_atEndOfBook() {
        val (navigator, _) = createNavigator(totalLength = 500, initialAnchor = 500)

        assertEquals(0, navigator.previewAdvanceCharCount())
    }

    @Test
    fun matchesGuaranteedMinimumOneChar_whenFitterMakesNoProgress() {
        val (navigator, _) = createNavigator(initialAnchor = 100, forceNoProgress = true)

        assertEquals(1, navigator.previewAdvanceCharCount())

        navigator.advance()
        assertEquals(101, navigator.anchor)
    }

    @Test
    fun matchesForwardStackRestore_afterRetreat() {
        val (navigator, _) = createNavigator(initialAnchor = 0)
        navigator.advance() // anchor -> 50, pushes forward-restore point on later retreat
        val advancedAnchor = navigator.anchor
        navigator.retreat() // anchor -> back to 0, forwardStack now holds `advancedAnchor`

        val preview = navigator.previewAdvanceCharCount()
        assertEquals(advancedAnchor, preview) // distance from 0 back to the saved forward anchor

        navigator.advance()
        assertEquals(advancedAnchor, navigator.anchor) // exact restore, not a re-fit
    }
}

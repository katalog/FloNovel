package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class LazyStateLayoutTest {

    private class CountingFitter(private val delegate: TextFitter) : TextFitter {
        var calls = 0
        override fun fitForward(from: Int, widthPx: Int, heightPx: Int): Int {
            calls++
            return delegate.fitForward(from, widthPx, heightPx)
        }
    }

    private fun navigator(paneMode: PaneMode, fitter: TextFitter, initialAnchor: Int = 0) = ReaderNavigator(
        totalLength = 10_000,
        textFitter = fitter,
        initialSpec = ViewportSpec(widthPx = 200, heightPx = 100, paneMode = paneMode, gutterPx = 0),
        initialAnchor = initialAnchor,
    )

    @Test
    fun plainJump_doesNotMeasureWhenStateLayoutIsUnused() {
        val fitter = CountingFitter(FakeTextFitter(totalLength = 10_000))
        val nav = navigator(PaneMode.TWO, fitter)

        nav.jumpTo(5_000)

        assertEquals(5_000, nav.anchor)
        assertEquals(0, fitter.calls, "The discarded 2-pane layout must not be reverse-fitted")
    }

    @Test
    fun advance_measuresOnlyForTheMoveItself() {
        val fitter = CountingFitter(FakeTextFitter(totalLength = 10_000))
        val nav = navigator(PaneMode.ONE, fitter)

        nav.advance(0.5f)

        assertEquals(1, fitter.calls)
    }

    @Test
    fun stateLayout_readLater_describesTheMomentItWasTaken() {
        val fitter = FakeTextFitter(totalLength = 10_000)
        val nav = navigator(PaneMode.TWO, fitter, initialAnchor = 1_000)

        val afterAdvance = nav.advance()
        nav.jumpTo(7_000)

        // Taken right after advance(): the left pane is the page just left (history), not a reverse-fit.
        assertEquals(1_000, afterAdvance.layout.leftPane.startOffset)
        assertEquals(afterAdvance.anchor, afterAdvance.layout.rightPane!!.startOffset)
        assertEquals(nav.layoutFor(afterAdvance.anchor, nav.spec).rightPane, afterAdvance.layout.rightPane)
    }
}

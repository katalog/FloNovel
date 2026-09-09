package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnchorPreservationTest {

    private val targetAnchor = 345
    private val totalLength = 2000

    private fun createNavigator(spec: ViewportSpec): ReaderNavigator {
        val fitter = FakeTextFitter(totalLength = totalLength, charWidth = 10, lineHeight = 20)
        return ReaderNavigator(
            totalLength = totalLength,
            textFitter = fitter,
            initialSpec = spec,
            initialAnchor = targetAnchor,
        )
    }

    @Test
    fun anchorPreserved_onFontSizeOrFontFamilyChanged() {
        val initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
        val navigator = createNavigator(initialSpec)
        repeat(2) { navigator.advance() } // Build some history
        assertTrue(navigator.historyStack.isNotEmpty())

        val currentAnchor = navigator.anchor
        val expectedProgress = navigator.progress()

        // Simulating font size change: new fitter with different charWidth/lineHeight
        val newFitter = FakeTextFitter(totalLength = totalLength, charWidth = 14, lineHeight = 28)
        navigator.onLayoutKeyChanged(initialSpec, newFitter)

        // Anchor must not change!
        assertEquals(currentAnchor, navigator.anchor)
        // Progress must not change!
        assertEquals(expectedProgress, navigator.progress())
        // History must be cleared!
        assertTrue(navigator.historyStack.isEmpty())
        // In 1-pane, layout must start at anchor!
        assertEquals(currentAnchor, navigator.state.layout.primaryPane.startOffset)
    }

    @Test
    fun anchorPreserved_onWindowResize() {
        val initialSpec = ViewportSpec(widthPx = 800, heightPx = 600, paneMode = PaneMode.ONE)
        val navigator = createNavigator(initialSpec)

        // Window resize: wider/narrower/maximize
        val resizedSpec = initialSpec.copy(widthPx = 1920, heightPx = 1080)
        navigator.onLayoutKeyChanged(resizedSpec)

        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetAnchor, navigator.state.layout.primaryPane.startOffset)
        assertEquals(targetAnchor.toDouble() / totalLength, navigator.progress())
    }

    @Test
    fun anchorPreserved_onOnePaneToTwoPaneTransition_andBack() {
        val onePaneSpec = ViewportSpec(widthPx = 1000, heightPx = 600, paneMode = PaneMode.ONE)
        val navigator = createNavigator(onePaneSpec)

        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetAnchor, navigator.state.layout.primaryPane.startOffset)

        // 1-pane -> 2-pane: anchor is preserved at the top of the RIGHT pane
        val twoPaneSpec = onePaneSpec.copy(paneMode = PaneMode.TWO)
        navigator.onLayoutKeyChanged(twoPaneSpec)

        assertEquals(targetAnchor, navigator.anchor, "Anchor must remain identical when switching 1-pane -> 2-pane")
        assertEquals(targetAnchor, navigator.state.layout.rightPane!!.startOffset, "Anchor must be the top of the right pane in 2-pane")
        assertTrue(navigator.state.layout.leftPane.startOffset < targetAnchor, "Left pane is filled with previous content")
        assertEquals(targetAnchor, navigator.state.layout.leftPane.endOffset, "Left pane end must meet right pane start")

        // 2-pane -> 1-pane: anchor is preserved at the top of the single pane
        navigator.onLayoutKeyChanged(onePaneSpec)

        assertEquals(targetAnchor, navigator.anchor, "Anchor must remain identical when switching 2-pane -> 1-pane")
        assertEquals(targetAnchor, navigator.state.layout.primaryPane.startOffset, "Anchor must be the top of the single pane in 1-pane")
    }

    @Test
    fun anchorPreserved_onGutterPaneRatioMaxLineWidthChanged() {
        val initialSpec = ViewportSpec(widthPx = 1200, heightPx = 700, paneMode = PaneMode.TWO, gutterPx = 20)
        val navigator = createNavigator(initialSpec)

        val updatedSpec = initialSpec.copy(
            gutterPx = 60,
            paneRatio = 0.5f,
            maxLineWidthPx = 800,
        )
        navigator.onLayoutKeyChanged(updatedSpec)

        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetAnchor, navigator.state.layout.rightPane!!.startOffset)
        assertEquals(targetAnchor.toDouble() / totalLength, navigator.progress())
    }
}

package com.moonkata.flonovel.desktop.reader

data class ViewportSpec(
    val widthPx: Int,
    val heightPx: Int,
    val paneMode: PaneMode = PaneMode.ONE,
    val paneRatio: Float = 0.5f,
    val gutterPx: Int = 0,
    val maxLineWidthPx: Int? = null,
) {
    /**
     * Effective content width after accounting for maxLineWidth if specified.
     */
    val effectiveWidthPx: Int
        get() = if (maxLineWidthPx != null && maxLineWidthPx > 0) {
            minOf(widthPx, maxLineWidthPx).coerceAtLeast(0)
        } else {
            widthPx.coerceAtLeast(0)
        }

    /**
     * Width available for panes after subtracting gutter.
     */
    val availableForPanesPx: Int
        get() = (effectiveWidthPx - gutterPx).coerceAtLeast(0)

    /**
     * Left pane width in 2-pane mode based on paneRatio.
     */
    val leftPaneWidthPx: Int
        get() = (availableForPanesPx * paneRatio).toInt().coerceAtLeast(0)

    /**
     * Right pane width in 2-pane mode based on paneRatio.
     */
    val rightPaneWidthPx: Int
        get() = (availableForPanesPx - leftPaneWidthPx).coerceAtLeast(0)

    /**
     * Default pane width in 2-pane mode (defaults to rightPaneWidthPx where anchor resides).
     */
    val paneWidthPx: Int
        get() = rightPaneWidthPx

    /**
     * Pane height in 2-pane mode.
     */
    val paneHeightPx: Int
        get() = heightPx.coerceAtLeast(0)
}

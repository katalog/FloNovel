package com.moonkata.flonovel.desktop.reader

data class PaneSpan(
    val startOffset: Int,
    val endOffset: Int,
) {
    val isEmpty: Boolean get() = startOffset >= endOffset
    val length: Int get() = (endOffset - startOffset).coerceAtLeast(0)
}

data class ReaderLayout(
    val paneMode: PaneMode,
    val panes: List<PaneSpan>,
) {
    val primaryPane: PaneSpan get() = panes.first()
    val leftPane: PaneSpan get() = panes.first()
    val rightPane: PaneSpan? get() = if (panes.size > 1) panes[1] else null
}

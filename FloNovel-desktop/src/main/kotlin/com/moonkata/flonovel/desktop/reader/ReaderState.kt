package com.moonkata.flonovel.desktop.reader

data class ReaderState(
    val anchor: Int,
    val layout: ReaderLayout,
    val historyStack: List<Int>,
) {
    val currentAnchor: Anchor get() = Anchor(anchor)
}

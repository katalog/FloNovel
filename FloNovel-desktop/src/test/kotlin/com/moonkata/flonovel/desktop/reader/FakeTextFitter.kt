package com.moonkata.flonovel.desktop.reader

/**
 * Deterministic, fixed-width fake [TextFitter] for UI-independent reader navigation tests.
 *
 * Fixed-width model:
 * charsPerLine = widthPx / charWidth
 * linesPerPage = heightPx / lineHeight
 * charsPerPage = charsPerLine * linesPerPage
 * fitForward(from, w, h) = minOf(from + charsPerPage, totalLength)
 */
class FakeTextFitter(
    val totalLength: Int,
    val charWidth: Int = 10,
    val lineHeight: Int = 20,
    var forceNoProgress: Boolean = false,
) : TextFitter {

    constructor(text: String, charWidth: Int = 10, lineHeight: Int = 20) : this(
        totalLength = text.length,
        charWidth = charWidth,
        lineHeight = lineHeight,
    )

    override fun fitForward(from: Int, widthPx: Int, heightPx: Int): Int {
        if (forceNoProgress) {
            return from
        }
        if (widthPx <= 0 || heightPx <= 0) {
            return from
        }
        val charsPerLine = (widthPx / charWidth).coerceAtLeast(0)
        val linesPerPage = (heightPx / lineHeight).coerceAtLeast(0)
        val charsPerPage = charsPerLine * linesPerPage
        return (from + charsPerPage).coerceAtMost(totalLength)
    }
}

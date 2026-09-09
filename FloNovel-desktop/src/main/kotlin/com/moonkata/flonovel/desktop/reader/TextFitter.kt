package com.moonkata.flonovel.desktop.reader

fun interface TextFitter {
    /**
     * Measures forward from [from] character offset and returns the ending character offset
     * (exclusive) that fits within [widthPx] by [heightPx].
     */
    fun fitForward(from: Int, widthPx: Int, heightPx: Int): Int
}

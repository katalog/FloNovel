package com.moonkata.flonovel.desktop.reader

/**
 * Anchor is the single reading position offset in the text:
 * - 1-pane: character offset at the top of the screen
 * - 2-pane: character offset at the top of the right pane
 *
 * There is only one reading position concept — no separate "display position"
 * or "sync position" exists.
 */
@JvmInline
value class Anchor(val offset: Int) : Comparable<Anchor> {
    init {
        require(offset >= 0) { "Anchor offset must be non-negative: $offset" }
    }

    override fun compareTo(other: Anchor): Int = offset.compareTo(other.offset)
    override fun toString(): String = offset.toString()

    companion object {
        val ZERO = Anchor(0)
    }
}

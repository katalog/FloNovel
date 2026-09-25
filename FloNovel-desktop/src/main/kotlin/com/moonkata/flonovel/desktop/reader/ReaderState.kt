package com.moonkata.flonovel.desktop.reader

/**
 * Snapshot returned by every [ReaderNavigator] move.
 *
 * [layout] is computed on first access, not eagerly: the reader UI ignores these return values
 * and lays out the page itself, so an eager layout was pure waste on every key press — and in
 * 2-pane mode it cost a full reverse-fit (a dozen text measurements) per chapter jump, which
 * made holding PgDn stall for about a second and then skip several chapters at once.
 */
class ReaderState(
    val anchor: Int,
    val historyStack: List<Int>,
    computeLayout: () -> ReaderLayout,
) {
    val layout: ReaderLayout by lazy(LazyThreadSafetyMode.NONE, computeLayout)

    val currentAnchor: Anchor get() = Anchor(anchor)
}

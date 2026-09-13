package com.moonkata.flonovel.desktop.reader

/**
 * Core navigator managing reading position [anchor], viewport layout, and visit history.
 *
 * Rules:
 * 1. [anchor] is the single reading position offset in the text (1-pane: top of screen,
 *    2-pane: top of right pane). There is no separate "display position" or "sync position".
 * 2. [advance] is a single implementation for both 1-pane and 2-pane modes. Mode branching
 *    only occurs in one place to determine the "visible amount" to fit. In 2-pane mode,
 *    the right pane moving to become the new left pane emerges automatically from this rule.
 * 3. [TextFitter] is the only interface injected into the reader package.
 * 4. Pure Kotlin only — no Compose or file IO dependencies.
 * 5. If [TextFitter.fitForward] returns a value with no progress (<= current offset),
 *    an advance of at least 1 character is guaranteed to prevent infinite loops.
 * 6. When the layout key changes, the history stack is cleared and the layout is recalculated
 *    from [anchor]. The [anchor] itself is never modified.
 */
class ReaderNavigator(
    val totalLength: Int,
    var textFitter: TextFitter,
    initialSpec: ViewportSpec,
    initialAnchor: Int = 0,
) {
    var spec: ViewportSpec = initialSpec
        private set

    var anchor: Int = initialAnchor.coerceIn(0, totalLength)
        private set

    private val history = ArrayDeque<Int>()
    private val forwardStack = ArrayDeque<Int>()

    val historyStack: List<Int>
        get() = history.toList()

    val forwardHistoryStack: List<Int>
        get() = forwardStack.toList()

    val state: ReaderState
        get() = ReaderState(
            anchor = anchor,
            layout = layoutFor(anchor, spec),
            historyStack = historyStack,
        )

    /**
     * Calculates the reading progress as a ratio [0.0, 1.0].
     */
    fun progress(currentAnchor: Int = anchor, total: Int = totalLength): Double {
        if (total <= 0) return 0.0
        return (currentAnchor.toDouble() / total).coerceIn(0.0, 1.0)
    }

    /**
     * Advances the reading position forward:
     * - If forward history is present (e.g. user retreated after a jump or page turn), pops the exact
     *   previous anchor from the forward stack, restoring the exact original screen.
     * - If forward history is empty, advances forward by [ratio] of the visible amount.
     *
     * Mode branching only occurs here to compute the visible forward amount:
     * - 1-pane: fitForward(anchor, screenWidth, screenHeight * ratio)
     * - 2-pane: fitForward(anchor, paneWidth, paneHeight)
     */
    fun advance(ratio: Float = 1.0f): ReaderState {
        if (anchor >= totalLength) {
            return state
        }

        // 1. If forward history exists (e.g. retreating after a jump/page turn), restore exact anchor
        if (forwardStack.isNotEmpty()) {
            val nextAnchor = forwardStack.removeLast().coerceIn(0, totalLength)
            if (nextAnchor != anchor) {
                history.addLast(anchor)
                anchor = nextAnchor
            }
            return state
        }

        // 2. Mode branching only in one place to determine visible amount:
        val rawNext = when (spec.paneMode) {
            PaneMode.ONE -> {
                val height = (spec.heightPx * ratio).toInt()
                textFitter.fitForward(anchor, spec.effectiveWidthPx, height)
            }
            PaneMode.TWO -> {
                textFitter.fitForward(anchor, spec.paneWidthPx, spec.paneHeightPx)
            }
        }

        // Guarantee at least 1 character advance to prevent infinite loops (Rule 5)
        val nextAnchor = if (rawNext <= anchor) {
            minOf(anchor + 1, totalLength)
        } else {
            minOf(rawNext, totalLength)
        }

        if (nextAnchor != anchor) {
            history.addLast(anchor)
            anchor = nextAnchor
        }

        return state
    }

    /**
     * Retreats to the previous page:
     * - Saves current anchor into the forward stack so that a subsequent [advance] can return exactly.
     * - If visit history is present: pops the exact previous anchor.
     * - If visit history is empty: reverse-estimates the previous anchor:
     *   - 1-pane: reverse-fits by [ratio] of visible height so that current anchor lands at [ratio] of the screen.
     *   - 2-pane: reverse-fits a single pane.
     */
    fun retreat(ratio: Float = 1.0f): ReaderState {
        if (anchor <= 0) {
            return state
        }

        val currentAnchor = anchor
        val targetAnchor = if (history.isNotEmpty()) {
            history.removeLast()
        } else {
            when (spec.paneMode) {
                PaneMode.ONE -> {
                    val height = maxOf(1, (spec.heightPx * ratio).toInt())
                    estimatePreviousAnchor(anchor, spec.effectiveWidthPx, height)
                }
                PaneMode.TWO -> estimatePreviousAnchor(anchor, spec.paneWidthPx, spec.paneHeightPx)
            }
        }

        val nextAnchor = targetAnchor.coerceIn(0, totalLength)
        if (nextAnchor != currentAnchor) {
            forwardStack.addLast(currentAnchor)
            anchor = nextAnchor
        }
        return state
    }

    /**
     * Jumps directly to [offset] (e.g. from TOC, search, chapter jump, remote sync).
     * Clears both visit history and forward history, recalculating layout from target offset.
     */
    fun jumpTo(offset: Int): ReaderState {
        history.clear()
        forwardStack.clear()
        anchor = offset.coerceIn(0, totalLength)
        return state
    }

    /**
     * Handles layout key changes (font, size, line spacing, margins, window size, reflow, pane mode, etc.).
     * Clears visit history and forward history as past boundaries are invalidated, and recalculates
     * the layout starting strictly at the current [anchor].
     *
     * The [anchor] itself is NEVER modified (Rule 6).
     */
    fun onLayoutKeyChanged(newSpec: ViewportSpec, newFitter: TextFitter = textFitter): ReaderState {
        history.clear()
        forwardStack.clear()
        spec = newSpec
        textFitter = newFitter
        return state
    }

    /**
     * Computes the visible pane span(s) for the given [anchor] and [spec].
     *
     * @param alignChapterToLeftPane If true and a chapter starts at [anchor], starts the chapter on the left pane.
     *                               Default is false (T-13 rule: never enabled by default).
     * @param chapterOffsets Set of chapter starting character offsets.
     */
    fun layoutFor(
        anchor: Int,
        spec: ViewportSpec,
        alignChapterToLeftPane: Boolean = false,
        chapterOffsets: Set<Int> = emptySet(),
    ): ReaderLayout {
        val safeAnchor = anchor.coerceIn(0, totalLength)
        return when (spec.paneMode) {
            PaneMode.ONE -> {
                val end = fitSafe(safeAnchor, spec.effectiveWidthPx, spec.heightPx)
                ReaderLayout(
                    paneMode = PaneMode.ONE,
                    panes = listOf(PaneSpan(safeAnchor, end)),
                )
            }
            PaneMode.TWO -> {
                if (alignChapterToLeftPane && chapterOffsets.contains(safeAnchor)) {
                    val leftStart = safeAnchor
                    val leftEnd = fitSafe(leftStart, spec.leftPaneWidthPx, spec.paneHeightPx)
                    val rightStart = leftEnd
                    val rightEnd = fitSafe(rightStart, spec.rightPaneWidthPx, spec.paneHeightPx)
                    ReaderLayout(
                        paneMode = PaneMode.TWO,
                        panes = listOf(
                            PaneSpan(leftStart, leftEnd),
                            PaneSpan(rightStart, rightEnd),
                        ),
                    )
                } else {
                    val rightStart = safeAnchor
                    val rightEnd = fitSafe(rightStart, spec.rightPaneWidthPx, spec.paneHeightPx)
                    val leftEnd = rightStart
                    val leftStart = if (safeAnchor <= 0) {
                        0
                    } else if (history.isNotEmpty() && history.last() < safeAnchor) {
                        history.last()
                    } else {
                        estimatePreviousAnchor(safeAnchor, spec.leftPaneWidthPx, spec.paneHeightPx)
                    }
                    ReaderLayout(
                        paneMode = PaneMode.TWO,
                        panes = listOf(
                            PaneSpan(leftStart, leftEnd),
                            PaneSpan(rightStart, rightEnd),
                        ),
                    )
                }
            }
        }
    }

    private fun fitSafe(from: Int, widthPx: Int, heightPx: Int): Int {
        if (from >= totalLength) return totalLength
        if (widthPx <= 0 || heightPx <= 0) return minOf(from + 1, totalLength)
        val raw = textFitter.fitForward(from, widthPx, heightPx)
        return if (raw <= from) minOf(from + 1, totalLength) else minOf(raw, totalLength)
    }

    private fun estimatePreviousAnchor(targetEnd: Int, widthPx: Int, heightPx: Int): Int {
        if (targetEnd <= 0) return 0
        if (widthPx <= 0 || heightPx <= 0) return maxOf(0, targetEnd - 1)

        val forwardSpan = (fitSafe(targetEnd, widthPx, heightPx) - targetEnd).coerceAtLeast(1)
        val initialSpan = if (forwardSpan > 1) forwardSpan else fitSafe(0, widthPx, heightPx).coerceAtLeast(50)

        // Find lower bound where fitSafe(low) < targetEnd (or low reaches 0)
        var step = maxOf(initialSpan * 2, 100)
        var low = maxOf(0, targetEnd - step)
        var attempts = 0
        while (low > 0 && fitSafe(low, widthPx, heightPx) >= targetEnd && attempts < 10) {
            attempts++
            step = if (step <= Int.MAX_VALUE / 2) step * 2 else Int.MAX_VALUE
            low = maxOf(0, targetEnd - step)
        }

        // Binary search for the smallest start in [low, targetEnd] such that fitSafe(start) >= targetEnd
        var l = low
        var r = targetEnd
        var best = targetEnd

        while (l <= r) {
            val mid = (l + r) ushr 1
            if (fitSafe(mid, widthPx, heightPx) >= targetEnd) {
                best = mid
                r = mid - 1
            } else {
                l = mid + 1
            }
        }

        return best
    }
}

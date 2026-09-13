package com.moonkata.flonovel.desktop.reader

/**
 * Pure timing state machine for auto page-turn.
 *
 * The wait duration for a page is derived from how many characters are visible on it
 * ([startPage]'s [visibleCharCount]), not from a fixed per-pane constant. A 2-pane page
 * naturally shows roughly twice the text of a 1-pane page, so it naturally waits roughly
 * twice as long — no separate pane-mode branch is needed here, unlike [ReaderNavigator.advance].
 *
 * Kept free of Compose so it can be unit tested without a UI, the same way
 * [EyeStrainScheduler] is.
 */
class AutoPageTurnScheduler(
    private val charsPerMinute: Int = DEFAULT_CHARS_PER_MINUTE,
    private val minDurationMs: Long = MIN_DURATION_MS,
    private val maxDurationMs: Long = MAX_DURATION_MS,
) {
    var totalMs: Long = 0L
        private set

    var remainingMs: Long = 0L
        private set

    val isReadyToTurn: Boolean
        get() = remainingMs <= 0L

    /** Fraction of the wait remaining, in [0.0, 1.0]. 1.0 right after [startPage]. */
    val remainingRatio: Float
        get() = if (totalMs <= 0L) 0f else (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    /**
     * Resets the countdown for a newly displayed page containing [visibleCharCount] characters.
     * Duration is clamped to [minDurationMs, maxDurationMs] so a near-empty page (e.g. a bare
     * chapter title) doesn't turn instantly, and an unusually dense page doesn't stall forever.
     */
    fun startPage(visibleCharCount: Int) {
        val safeCharsPerMinute = charsPerMinute.coerceAtLeast(1)
        val raw = visibleCharCount.coerceAtLeast(0).toLong() * 60_000L / safeCharsPerMinute
        totalMs = raw.coerceIn(minDurationMs, maxDurationMs)
        remainingMs = totalMs
    }

    /** Advances time by [deltaMs]; clamps at zero instead of going negative. */
    fun tick(deltaMs: Long) {
        remainingMs = (remainingMs - deltaMs).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_CHARS_PER_MINUTE: Int = 600
        const val MIN_DURATION_MS: Long = 1_500L
        const val MAX_DURATION_MS: Long = 60_000L
    }
}

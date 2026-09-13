package com.moonkata.flonovel.desktop.reader

/**
 * Pure timing state machine for auto page-turn: waits a fixed [intervalSeconds] after a page
 * is shown, then signals ready to turn. Kept free of Compose so it can be unit tested without
 * a UI, the same way [EyeStrainScheduler] is.
 */
class AutoPageTurnScheduler(
    intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
) {
    val totalMs: Long = intervalSeconds.coerceAtLeast(1) * 1000L

    var remainingMs: Long = totalMs
        private set

    val isReadyToTurn: Boolean
        get() = remainingMs <= 0L

    /** Fraction of the wait remaining, in [0.0, 1.0]. 1.0 right after [startPage]. */
    val remainingRatio: Float
        get() = if (totalMs <= 0L) 0f else (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    /** Resets the countdown for a newly displayed page. */
    fun startPage() {
        remainingMs = totalMs
    }

    /** Advances time by [deltaMs]; clamps at zero instead of going negative. */
    fun tick(deltaMs: Long) {
        remainingMs = (remainingMs - deltaMs).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_INTERVAL_SECONDS: Int = 10
    }
}

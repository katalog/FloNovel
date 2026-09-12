package com.moonkata.flonovel.desktop.reader

/**
 * Pure timing state machine for the 20-20-20 eye strain reminder: after
 * [activeIntervalMs] of continuous reading, force a [breakDurationMs] break,
 * then resume. Kept free of Compose so it can be unit tested without a UI,
 * the same way [ReaderNavigator] is.
 */
class EyeStrainScheduler(
    private val activeIntervalMs: Long = DEFAULT_ACTIVE_INTERVAL_MS,
    private val breakDurationMs: Long = DEFAULT_BREAK_DURATION_MS,
) {
    sealed class State {
        data class Reading(val elapsedMs: Long) : State()
        data class OnBreak(val remainingMs: Long) : State()
    }

    var state: State = State.Reading(0L)
        private set

    /** Advances time by [deltaMs] and transitions state if a threshold is crossed. */
    fun tick(deltaMs: Long) {
        state = when (val current = state) {
            is State.Reading -> {
                val elapsed = current.elapsedMs + deltaMs
                if (elapsed >= activeIntervalMs) {
                    State.OnBreak(breakDurationMs)
                } else {
                    State.Reading(elapsed)
                }
            }
            is State.OnBreak -> {
                val remaining = current.remainingMs - deltaMs
                if (remaining <= 0L) {
                    State.Reading(0L)
                } else {
                    State.OnBreak(remaining)
                }
            }
        }
    }

    /** Clears elapsed/remaining time and returns to [State.Reading]. */
    fun reset() {
        state = State.Reading(0L)
    }

    companion object {
        const val DEFAULT_ACTIVE_INTERVAL_MS: Long = 20 * 60 * 1000L
        const val DEFAULT_BREAK_DURATION_MS: Long = 20 * 1000L
    }
}

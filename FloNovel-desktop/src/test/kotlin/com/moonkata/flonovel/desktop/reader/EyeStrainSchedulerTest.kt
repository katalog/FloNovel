package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EyeStrainSchedulerTest {

    private fun createScheduler(
        activeIntervalMs: Long = 20 * 60 * 1000L,
        breakDurationMs: Long = 20 * 1000L,
    ) = EyeStrainScheduler(activeIntervalMs = activeIntervalMs, breakDurationMs = breakDurationMs)

    @Test
    fun staysReading_belowActiveInterval() {
        val scheduler = createScheduler(activeIntervalMs = 1000L, breakDurationMs = 200L)

        scheduler.tick(400L)
        scheduler.tick(400L)

        val state = assertIs<EyeStrainScheduler.State.Reading>(scheduler.state)
        assertEquals(800L, state.elapsedMs)
    }

    @Test
    fun entersBreak_whenActiveIntervalReached() {
        val scheduler = createScheduler(activeIntervalMs = 1000L, breakDurationMs = 200L)

        scheduler.tick(600L)
        scheduler.tick(400L) // exactly reaches the threshold

        val state = assertIs<EyeStrainScheduler.State.OnBreak>(scheduler.state)
        assertEquals(200L, state.remainingMs)
    }

    @Test
    fun entersBreak_whenSingleLargeTickOvershootsInterval() {
        // A paused/backgrounded app can deliver one large delta instead of many small ticks.
        val scheduler = createScheduler(activeIntervalMs = 1000L, breakDurationMs = 200L)

        scheduler.tick(1500L)

        val state = assertIs<EyeStrainScheduler.State.OnBreak>(scheduler.state)
        assertEquals(200L, state.remainingMs)
    }

    @Test
    fun countsDownDuringBreak() {
        val scheduler = createScheduler(activeIntervalMs = 1000L, breakDurationMs = 200L)
        scheduler.tick(1000L)

        scheduler.tick(50L)

        val state = assertIs<EyeStrainScheduler.State.OnBreak>(scheduler.state)
        assertEquals(150L, state.remainingMs)
    }

    @Test
    fun resumesReading_whenBreakDurationElapses() {
        val scheduler = createScheduler(activeIntervalMs = 1000L, breakDurationMs = 200L)
        scheduler.tick(1000L)

        scheduler.tick(200L)

        val state = assertIs<EyeStrainScheduler.State.Reading>(scheduler.state)
        assertEquals(0L, state.elapsedMs)
    }

    @Test
    fun resumesReading_whenBreakTickOvershoots() {
        val scheduler = createScheduler(activeIntervalMs = 1000L, breakDurationMs = 200L)
        scheduler.tick(1000L)

        scheduler.tick(500L) // well past the break duration in one delta

        val state = assertIs<EyeStrainScheduler.State.Reading>(scheduler.state)
        assertEquals(0L, state.elapsedMs)
    }

    @Test
    fun reset_returnsToFreshReadingState_fromEitherState() {
        val scheduler = createScheduler(activeIntervalMs = 1000L, breakDurationMs = 200L)
        scheduler.tick(500L)
        scheduler.reset()
        assertEquals(EyeStrainScheduler.State.Reading(0L), scheduler.state)

        scheduler.tick(1000L) // now on break
        scheduler.reset()
        assertEquals(EyeStrainScheduler.State.Reading(0L), scheduler.state)
    }
}

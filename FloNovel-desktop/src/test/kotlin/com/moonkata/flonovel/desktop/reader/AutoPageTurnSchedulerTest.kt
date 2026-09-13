package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPageTurnSchedulerTest {

    @Test
    fun totalMs_isIntervalSecondsInMilliseconds() {
        val scheduler = AutoPageTurnScheduler(intervalSeconds = 10)

        assertEquals(10_000L, scheduler.totalMs)
        assertEquals(10_000L, scheduler.remainingMs)
    }

    @Test
    fun coercesIntervalToAtLeastOneSecond() {
        val scheduler = AutoPageTurnScheduler(intervalSeconds = 0)

        assertEquals(1_000L, scheduler.totalMs)
    }

    @Test
    fun tick_countsDownToReady() {
        val scheduler = AutoPageTurnScheduler(intervalSeconds = 10)

        scheduler.tick(4_000L)
        assertFalse(scheduler.isReadyToTurn)
        assertEquals(6_000L, scheduler.remainingMs)

        scheduler.tick(6_000L)
        assertTrue(scheduler.isReadyToTurn)
    }

    @Test
    fun tick_doesNotGoNegative_onOvershoot() {
        val scheduler = AutoPageTurnScheduler(intervalSeconds = 10)

        scheduler.tick(50_000L)

        assertTrue(scheduler.isReadyToTurn)
        assertEquals(0L, scheduler.remainingMs)
    }

    @Test
    fun remainingRatio_startsAtOne_andReachesZeroWhenReady() {
        val scheduler = AutoPageTurnScheduler(intervalSeconds = 10)

        assertEquals(1f, scheduler.remainingRatio)

        scheduler.tick(5_000L)
        assertEquals(0.5f, scheduler.remainingRatio)

        scheduler.tick(5_000L)
        assertEquals(0f, scheduler.remainingRatio)
    }

    @Test
    fun startPage_resetsCountdown() {
        val scheduler = AutoPageTurnScheduler(intervalSeconds = 10)
        scheduler.tick(9_000L)
        assertTrue(scheduler.remainingMs < 2_000L)

        scheduler.startPage()

        assertEquals(10_000L, scheduler.remainingMs)
    }
}

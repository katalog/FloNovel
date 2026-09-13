package com.moonkata.flonovel.desktop.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPageTurnSchedulerTest {

    private fun createScheduler(
        charsPerMinute: Int = 600,
        minDurationMs: Long = 1_500L,
        maxDurationMs: Long = 60_000L,
    ) = AutoPageTurnScheduler(charsPerMinute = charsPerMinute, minDurationMs = minDurationMs, maxDurationMs = maxDurationMs)

    @Test
    fun durationScalesWithVisibleCharCount() {
        // 600 chars/min = 10 chars/sec, so 600 visible chars should wait 60s... but that's
        // clamped by maxDurationMs below; use an unclamped range to check the raw scaling.
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)

        scheduler.startPage(300)

        assertEquals(30_000L, scheduler.totalMs)
        assertEquals(30_000L, scheduler.remainingMs)
    }

    @Test
    fun twoPaneVisibleCountWaitsLongerThanOnePane() {
        // Same chars/min; a 2-pane page's char count is simply larger (both panes summed),
        // so the resulting duration is proportionally larger with no separate pane branch.
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)

        scheduler.startPage(200) // 1-pane
        val onePaneDuration = scheduler.totalMs

        scheduler.startPage(400) // 2-pane: left + right combined
        val twoPaneDuration = scheduler.totalMs

        assertEquals(onePaneDuration * 2, twoPaneDuration)
    }

    @Test
    fun clampsToMinDuration_forVeryShortPage() {
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 1_500L, maxDurationMs = 60_000L)

        scheduler.startPage(1) // near-instant at 600 chars/min, but must not turn instantly

        assertEquals(1_500L, scheduler.totalMs)
    }

    @Test
    fun clampsToMaxDuration_forVeryLongPage() {
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 1_500L, maxDurationMs = 60_000L)

        scheduler.startPage(100_000)

        assertEquals(60_000L, scheduler.totalMs)
    }

    @Test
    fun tick_countsDownToReady() {
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)
        scheduler.startPage(100) // 10s at 600 chars/min

        scheduler.tick(4_000L)
        assertFalse(scheduler.isReadyToTurn)
        assertEquals(6_000L, scheduler.remainingMs)

        scheduler.tick(6_000L)
        assertTrue(scheduler.isReadyToTurn)
    }

    @Test
    fun tick_doesNotGoNegative_onOvershoot() {
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)
        scheduler.startPage(100) // 10s

        scheduler.tick(50_000L)

        assertTrue(scheduler.isReadyToTurn)
        assertEquals(0L, scheduler.remainingMs)
    }

    @Test
    fun remainingRatio_startsAtOne_andReachesZeroWhenReady() {
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)
        scheduler.startPage(100) // 10s

        assertEquals(1f, scheduler.remainingRatio)

        scheduler.tick(5_000L)
        assertEquals(0.5f, scheduler.remainingRatio)

        scheduler.tick(5_000L)
        assertEquals(0f, scheduler.remainingRatio)
    }

    @Test
    fun startPage_resetsCountdownForNewPage() {
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)
        scheduler.startPage(100)
        scheduler.tick(9_000L)
        assertTrue(scheduler.remainingMs < 2_000L)

        scheduler.startPage(200) // new page entered before the old one turned

        assertEquals(20_000L, scheduler.totalMs)
        assertEquals(20_000L, scheduler.remainingMs)
    }
}

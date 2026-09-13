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
    fun durationScalesWithAdvanceCharCount() {
        // 600 chars/min = 10 chars/sec, so a 300-char advance should wait 30s. Use an
        // unclamped range here to check the raw scaling; clamping is tested separately below.
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)

        scheduler.startPage(300)

        assertEquals(30_000L, scheduler.totalMs)
        assertEquals(30_000L, scheduler.remainingMs)
    }

    @Test
    fun durationDoubles_whenAdvanceCharCountDoubles() {
        // The scheduler itself is agnostic to why one turn advances more characters than
        // another (1-pane ratio vs. 2-pane pane width) — it just scales linearly with
        // whatever ReaderNavigator.previewAdvanceCharCount reports for that turn.
        val scheduler = createScheduler(charsPerMinute = 600, minDurationMs = 0L, maxDurationMs = Long.MAX_VALUE)

        scheduler.startPage(200)
        val smallerAdvanceDuration = scheduler.totalMs

        scheduler.startPage(400)
        val largerAdvanceDuration = scheduler.totalMs

        assertEquals(smallerAdvanceDuration * 2, largerAdvanceDuration)
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

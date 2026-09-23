package com.moonkata.flonovel.android.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncTest {

    @Test
    fun firstResume_syncs() {
        assertTrue(shouldAutoSync(lastAttemptAtMillis = 0L, nowMillis = 1_000_000L))
    }

    @Test
    fun resumeWithinAMinute_doesNotSyncAgain() {
        assertFalse(shouldAutoSync(lastAttemptAtMillis = 1_000_000L, nowMillis = 1_000_000L + AUTO_SYNC_MIN_INTERVAL_MILLIS - 1))
        assertTrue(shouldAutoSync(lastAttemptAtMillis = 1_000_000L, nowMillis = 1_000_000L + AUTO_SYNC_MIN_INTERVAL_MILLIS))
    }
}

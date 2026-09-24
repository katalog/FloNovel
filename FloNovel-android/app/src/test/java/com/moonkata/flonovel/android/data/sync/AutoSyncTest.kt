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

    // ── Opening a book: which ones skip the preprocessing check ─────────

    private val dao = FakeSyncBaseDao()

    private fun base(state: String) =
        com.moonkata.flonovel.android.data.db.SyncBaseEntity("series/책.txt", "Series/책.txt", "r", "h", 1, 1, state)

    @Test
    fun syncedCopy_skipsTheCheck_matchingTheKeyLikeSyncDoes() = kotlinx.coroutines.runBlocking {
        dao.upsert(base("SYNCED"))
        // Same key rules as sync: case and Unicode composition do not matter.
        assertTrue(isSyncedCopy(dao, "Series/책.txt"))
        assertTrue(isSyncedCopy(dao, java.text.Normalizer.normalize("SERIES/책.txt", java.text.Normalizer.Form.NFD)))
    }

    @Test
    fun unfinishedDownload_isChecked() = kotlinx.coroutines.runBlocking {
        dao.upsert(base("DOWNLOADING"))
        assertFalse(isSyncedCopy(dao, "Series/책.txt"))
    }

    @Test
    fun bookAddedOnThePhone_isChecked() = kotlinx.coroutines.runBlocking {
        assertFalse(isSyncedCopy(dao, "Series/책.txt"))
    }
}

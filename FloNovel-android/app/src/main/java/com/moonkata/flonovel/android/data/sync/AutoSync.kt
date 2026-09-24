package com.moonkata.flonovel.android.data.sync

import com.moonkata.flonovel.android.data.db.SyncBaseDao

/**
 * The book the reader has open, by stored document URI. Sync runs from the library screen and can
 * still be going when a book is opened; it leaves this one alone until it is closed.
 */
object OpenBook {
    @Volatile
    var documentUri: String? = null
}

/**
 * Whether a book is a copy sync fully brought down from Dropbox. Dropbox holds only preprocessed
 * files (AGENTS.md §1), so such a book needs no preprocessing check when it is first opened; that
 * check reads and normalizes the whole file, which takes seconds for a large novel on a phone.
 * A download still in progress does not count.
 */
suspend fun isSyncedCopy(baseDao: SyncBaseDao, relativePath: String): Boolean =
    baseDao.getByKey(syncKeyOf(relativePath))?.toBase()?.state == BaseState.SYNCED

/**
 * Whether returning to the library should start a sync. Coming back from the reader or from
 * another app happens often; once a minute is enough to pick up the other device's changes
 * without syncing on every screen switch.
 */
fun shouldAutoSync(lastAttemptAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - lastAttemptAtMillis >= AUTO_SYNC_MIN_INTERVAL_MILLIS

const val AUTO_SYNC_MIN_INTERVAL_MILLIS = 60_000L

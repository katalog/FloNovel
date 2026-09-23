package com.moonkata.flonovel.android.data.sync

/**
 * The book the reader has open, by stored document URI. Sync runs from the library screen and can
 * still be going when a book is opened; it leaves this one alone until it is closed.
 */
object OpenBook {
    @Volatile
    var documentUri: String? = null
}

/**
 * Whether returning to the library should start a sync. Coming back from the reader or from
 * another app happens often; once a minute is enough to pick up the other device's changes
 * without syncing on every screen switch.
 */
fun shouldAutoSync(lastAttemptAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - lastAttemptAtMillis >= AUTO_SYNC_MIN_INTERVAL_MILLIS

const val AUTO_SYNC_MIN_INTERVAL_MILLIS = 60_000L

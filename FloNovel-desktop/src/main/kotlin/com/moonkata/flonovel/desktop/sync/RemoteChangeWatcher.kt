package com.moonkata.flonovel.desktop.sync

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tells the app when Dropbox `/books` changed, so edits made on the phone reach the PC without a
 * restart or a button press. One long-poll request is open at a time; it costs next to nothing
 * while waiting.
 *
 * [cursor] is read fresh before every poll: it is the sync's saved cursor, so a poll only reports
 * changes the last completed sync has not seen.
 */
class RemoteChangeWatcher(
    private val client: DropboxClient,
    private val cursor: () -> String?,
    private val onChange: () -> Unit,
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    /** The cursor a change was last reported for, to notice when the sync that followed failed. */
    private var lastReportedCursor: String? = null
    private var repeatWaitMs = 0L

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ loop() }, "Dropbox-Longpoll").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        running.set(false)
        thread?.interrupt()
    }

    private fun loop() {
        while (running.get()) {
            val waitMs = pollOnce()
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    /**
     * One poll; returns how long to wait before the next one.
     *
     * A sync that could not finish keeps its old cursor, and polling that cursor reports the same
     * change at once, every time. Without the growing wait below, a book held open in the reader
     * (whose change is deferred until it is closed) would restart sync every few seconds.
     */
    internal fun pollOnce(): Long {
        val current = cursor() ?: return NO_CURSOR_WAIT_MS
        return when (val result = client.longpoll(current, TIMEOUT_SECONDS)) {
            DropboxLongpollResult.Failure -> FAILURE_WAIT_MS
            is DropboxLongpollResult.Ok -> {
                val backoffMs = result.backoffSeconds * 1000L
                if (!result.changes) return backoffMs
                repeatWaitMs = if (current == lastReportedCursor) {
                    (repeatWaitMs * 2).coerceIn(REPEAT_FIRST_WAIT_MS, REPEAT_MAX_WAIT_MS)
                } else {
                    AFTER_CHANGE_WAIT_MS
                }
                lastReportedCursor = current
                onChange()
                maxOf(backoffMs, repeatWaitMs)
            }
        }
    }

    companion object {
        const val TIMEOUT_SECONDS = 480

        /** No cursor until the first sync completes; there is nothing to poll against yet. */
        const val NO_CURSOR_WAIT_MS = 60_000L
        const val FAILURE_WAIT_MS = 60_000L

        /** Gives the triggered sync time to save a new cursor before polling again. */
        const val AFTER_CHANGE_WAIT_MS = 5_000L
        const val REPEAT_FIRST_WAIT_MS = 30_000L
        const val REPEAT_MAX_WAIT_MS = 300_000L
    }
}

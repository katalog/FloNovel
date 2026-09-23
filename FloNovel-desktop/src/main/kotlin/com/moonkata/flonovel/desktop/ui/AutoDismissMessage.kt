package com.moonkata.flonovel.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A transient message that clears itself [durationMs] after the latest [show].
 *
 * The dismiss timer runs in its own job on [scope], independent of whatever work
 * produced the message. The sync toast used to be cleared at the end of the
 * auto-sync job itself; when a new file event cancelled that job during its
 * 3-second wait, the clearing line never ran, and if the follow-up sync found
 * nothing to report, the toast stayed on screen forever.
 */
class AutoDismissMessage(
    private val scope: CoroutineScope,
    private val durationMs: Long,
) {
    var message by mutableStateOf<String?>(null)
        private set

    private var dismissJob: Job? = null

    fun show(text: String) {
        dismissJob?.cancel()
        message = text
        dismissJob = scope.launch {
            delay(durationMs)
            message = null
        }
    }
}

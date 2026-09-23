package com.moonkata.flonovel.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AutoDismissMessageTest {

    private val durationMs = 200L

    private fun <T> withScope(block: suspend CoroutineScope.(CoroutineScope) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun messageClearsAfterDuration() = withScope { scope ->
        val msg = AutoDismissMessage(scope, durationMs)
        msg.show("synced")
        assertEquals("synced", msg.message)

        delay(durationMs * 3)
        assertNull(msg.message)
    }

    // Regression: the auto-sync job that shows the toast gets cancelled when a
    // new file event arrives. The toast must still go away on its own.
    @Test
    fun messageClearsEvenWhenProducerJobIsCancelled() = withScope { scope ->
        val msg = AutoDismissMessage(scope, durationMs)
        val producer = launch {
            msg.show("synced")
            delay(Long.MAX_VALUE)
        }
        delay(20)
        producer.cancelAndJoin()
        assertEquals("synced", msg.message)

        delay(durationMs * 3)
        assertNull(msg.message)
    }

    @Test
    fun showingAgainRestartsTimerAndKeepsLatestText() = withScope { scope ->
        val msg = AutoDismissMessage(scope, durationMs)
        msg.show("first")
        delay(durationMs / 2)
        msg.show("second")

        // Past the first message's deadline, before the second's.
        delay(durationMs * 3 / 4)
        assertEquals("second", msg.message)

        delay(durationMs * 2)
        assertNull(msg.message)
    }
}

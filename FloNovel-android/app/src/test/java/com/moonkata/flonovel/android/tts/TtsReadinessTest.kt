package com.moonkata.flonovel.android.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsReadinessTest {
    @Test
    fun speechWaitsForSuccessfulInitialization() = runTest {
        val readiness = TtsReadiness()
        val ready = async { readiness.awaitReady() }
        runCurrent()
        assertFalse(ready.isCompleted)
        readiness.complete(TtsReadiness.State.READY)
        ready.await()
        assertTrue(ready.isCompleted)
    }

    @Test
    fun unavailableVoiceAndFailedEngineDoNotPermitSpeech() = runTest {
        for (state in listOf(TtsReadiness.State.VOICE_UNAVAILABLE, TtsReadiness.State.FAILED)) {
            val readiness = TtsReadiness()
            readiness.complete(state)
            assertTrue(runCatching { readiness.awaitReady() }.isFailure)
        }
    }

    @Test
    fun engineThatNeverInitializesTimesOut() = runTest {
        val ready = async { runCatching { TtsReadiness().awaitReady() } }
        advanceUntilIdle()
        assertTrue(ready.await().exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)
    }

    @Test
    fun leavingTtsModeCancelsInitializationWait() = runTest {
        val readiness = TtsReadiness()
        var spoke = false
        val ready = async { readiness.awaitReady(); spoke = true }
        runCurrent()
        ready.cancel()
        readiness.complete(TtsReadiness.State.READY)
        runCurrent()
        assertFalse(spoke)
    }
}

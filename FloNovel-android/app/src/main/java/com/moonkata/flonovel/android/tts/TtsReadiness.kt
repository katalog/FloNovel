package com.moonkata.flonovel.android.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Initialization can finish without a usable voice; callers must wait before speaking. */
class TtsReadiness {
    enum class State { INITIALIZING, READY, VOICE_UNAVAILABLE, FAILED }

    private val state = MutableStateFlow(State.INITIALIZING)

    fun complete(result: State) {
        require(result != State.INITIALIZING)
        state.value = result
    }

    suspend fun awaitReady() {
        val result = withTimeout(10_000L) { state.first { it != State.INITIALIZING } }
        check(result == State.READY) { "Speech initialization failed: $result" }
    }
}

package com.moonkata.flonovel.android.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale

class TtsController(
    context: Context,
    private val onUtteranceDone: (utteranceId: String) -> Unit,
    private val onPlaybackError: (utteranceId: String?) -> Unit,
) {
    private var tts: TextToSpeech? = null

    private val readiness = TtsReadiness()

    init {
        tts = TextToSpeech(context) { status ->
            // Posting also handles engines that report initialization before construction returns.
            Handler(Looper.getMainLooper()).post {
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    readiness.complete(TtsReadiness.State.FAILED)
                } else {
                    val language = engine.setLanguage(Locale.KOREAN)
                    if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                        readiness.complete(TtsReadiness.State.VOICE_UNAVAILABLE)
                    } else {
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                utteranceId?.let(onUtteranceDone)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) = onPlaybackError(utteranceId)
                        })
                        readiness.complete(TtsReadiness.State.READY)
                    }
                }
            }
        }
    }

    suspend fun awaitReady() = readiness.awaitReady()

    fun setRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun speak(utteranceKey: String, text: String) {
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceKey) != TextToSpeech.SUCCESS) onPlaybackError(utteranceKey)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        readiness.complete(TtsReadiness.State.FAILED)
        tts?.shutdown()
        tts = null
    }
}

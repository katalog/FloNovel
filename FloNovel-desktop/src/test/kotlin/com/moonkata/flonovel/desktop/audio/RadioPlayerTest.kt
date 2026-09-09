package com.moonkata.flonovel.desktop.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RadioPlayerTest {

    @Test
    fun testDefaultStreamsLoadSuccessfully() {
        val streams = RadioStreamCatalog.parseStreamsJson(RadioStreamCatalog.defaultJsonContent())
        assertTrue(streams.isNotEmpty(), "Streams list should not be empty")
        assertEquals(3, streams.size, "Default streams count should be 3")

        val lounge = streams.find { it.name == "The Lounge Hour" }
        assertNotNull(lounge, "The Lounge Hour should exist")
        assertEquals("https://listen2.streamaudio.co/stream/8056", lounge.url)

        val jazz = streams.find { it.name == "RelaxingJazz.com" }
        assertNotNull(jazz, "RelaxingJazz.com should exist")
        assertEquals("http://stream-02-eu.relaxingjazz.com/stream/3/", jazz.url)

        val cotn = streams.find { it.name == "COTN Radio" }
        assertNotNull(cotn, "COTN Radio should exist")
        assertEquals("https://streaming.smartradio.ch:8510/stream", cotn.url)
    }

    @Test
    fun testRemainingTimeFormatting() {
        // When not playing, formatted time should be empty
        val notPlayingState = RadioPlaybackState(isPlaying = false, streamName = "Jazz", remainingSeconds = 3600L)
        assertEquals("", notPlayingState.formatRemainingTime())

        // 60 minutes
        val sixtyMin = RadioPlaybackState(isPlaying = true, streamName = "Jazz", remainingSeconds = 3600L)
        assertEquals("60분 남음", sixtyMin.formatRemainingTime())

        // 58 minutes 30 seconds -> ceil to 59 minutes
        val partialMin = RadioPlaybackState(isPlaying = true, streamName = "Jazz", remainingSeconds = 3510L)
        assertEquals("59분 남음", partialMin.formatRemainingTime())

        // Exactly 1 minute (60s)
        val oneMin = RadioPlaybackState(isPlaying = true, streamName = "Jazz", remainingSeconds = 60L)
        assertEquals("1분 남음", oneMin.formatRemainingTime())

        // Under 1 minute (59s)
        val underOneMin = RadioPlaybackState(isPlaying = true, streamName = "Jazz", remainingSeconds = 59L)
        assertEquals("1분 미만 남음", underOneMin.formatRemainingTime())

        // 0 seconds
        val zeroSec = RadioPlaybackState(isPlaying = true, streamName = "Jazz", remainingSeconds = 0L)
        assertEquals("", zeroSec.formatRemainingTime())
    }

    @Test
    fun testStopCleansUpState() {
        RadioPlayer.stop()
        val state = RadioPlayer.state.value
        assertFalse(state.isPlaying)
        assertEquals(null, state.streamName)
        assertEquals(0L, state.remainingSeconds)
    }
}

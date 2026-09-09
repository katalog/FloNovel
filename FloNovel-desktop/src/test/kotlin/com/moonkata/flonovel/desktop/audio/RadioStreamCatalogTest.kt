package com.moonkata.flonovel.desktop.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RadioStreamCatalogTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun ensureConfigFileExists_createsDefaultFileWhenMissing() {
        val configFile = tempDir.resolve("radio_streams.json")
        assertTrue(!Files.exists(configFile))

        RadioStreamCatalog.ensureConfigFileExists(configFile)

        assertTrue(Files.exists(configFile))
        val text = Files.readString(configFile)
        assertTrue(text.contains("The Lounge Hour"))
        assertTrue(text.contains("RelaxingJazz.com"))
    }

    @Test
    fun parseStreamsJson_parsesValidJsonSuccessfully() {
        val json = """
            [
                {"name": "Custom Lo-Fi", "url": "http://example.com/lofi.mp3"},
                {"name": "Classical Piano", "url": "http://example.com/piano.aac"}
            ]
        """.trimIndent()

        val parsed = RadioStreamCatalog.parseStreamsJson(json)
        assertEquals(2, parsed.size)
        assertEquals("Custom Lo-Fi", parsed[0].name)
        assertEquals("http://example.com/lofi.mp3", parsed[0].url)
        assertEquals("Classical Piano", parsed[1].name)
        assertEquals("http://example.com/piano.aac", parsed[1].url)
    }

    @Test
    fun loadStreams_loadsCustomStreamsFromUserFile() {
        val configFile = tempDir.resolve("custom_streams.json")
        val customJson = """
            [
                {"name": "My Favorite Jazz", "url": "https://stream.example.org/live"}
            ]
        """.trimIndent()
        Files.writeString(configFile, customJson)

        val loaded = RadioStreamCatalog.loadStreams(configFile)
        assertEquals(1, loaded.size)
        assertEquals("My Favorite Jazz", loaded[0].name)
        assertEquals("https://stream.example.org/live", loaded[0].url)
    }

    @Test
    fun loadStreams_fallsBackToDefaultsOnEmptyOrInvalidJson() {
        val emptyConfigFile = tempDir.resolve("empty.json")
        Files.writeString(emptyConfigFile, "[]")
        val loadedFromEmpty = RadioStreamCatalog.loadStreams(emptyConfigFile)
        assertEquals(RadioStreamCatalog.DEFAULT_STREAMS, loadedFromEmpty)

        val corruptedConfigFile = tempDir.resolve("corrupted.json")
        Files.writeString(corruptedConfigFile, "Not a valid JSON")
        val loadedFromCorrupted = RadioStreamCatalog.loadStreams(corruptedConfigFile)
        assertEquals(RadioStreamCatalog.DEFAULT_STREAMS, loadedFromCorrupted)
    }

    @Test
    fun parseStreamsJson_ignoresGuideAndMalformedEntries() {
        val json = """
            [
                {"_guide": "Only direct MP3/AAC streams are supported."},
                {"name": "Valid Stream", "url": "http://example.com/stream"},
                {"name": "Missing URL"},
                {"url": "http://example.com/no-name"},
                "Random String in Array"
            ]
        """.trimIndent()

        val parsed = RadioStreamCatalog.parseStreamsJson(json)
        assertEquals(1, parsed.size)
        assertEquals("Valid Stream", parsed[0].name)
        assertEquals("http://example.com/stream", parsed[0].url)
    }
}


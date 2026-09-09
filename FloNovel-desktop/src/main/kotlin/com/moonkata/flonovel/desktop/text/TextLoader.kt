package com.moonkata.flonovel.desktop.text

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/**
 * Result of loading a text file, containing the decoded text and detected charset.
 */
data class LoadedText(
    val text: String,
    val charset: Charset,
)

/**
 * Loads text files into String memory.
 *
 * Reads the first 256KB sample to detect encoding, decodes the full text using the
 * resolved charset, and removes any leading BOM so the resulting String forms the exact
 * reference base for all character offsets.
 */
object TextLoader {

    fun load(path: Path): LoadedText {
        val sampleSize = EncodingDetector.SAMPLE_SIZE_BYTES
        val sample = Files.newInputStream(path).use { stream: InputStream ->
            val buffer = ByteArray(sampleSize)
            val bytesRead = stream.readNBytes(buffer, 0, sampleSize)
            if (bytesRead < sampleSize) buffer.copyOf(bytesRead) else buffer
        }

        val charset = EncodingDetector.detect(sample)
        val rawText = Files.readString(path, charset)
        val cleanText = rawText.removePrefix("\uFEFF")
        return LoadedText(text = cleanText, charset = charset)
    }

    fun loadText(path: Path): String = load(path).text
}

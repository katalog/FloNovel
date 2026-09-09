package com.moonkata.flonovel.desktop.text

import org.mozilla.universalchardet.UniversalDetector
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Detects character encoding of text samples using juniversalchardet.
 *
 * Rules:
 * - Inspects only the first 256KB sample.
 * - Uses juniversalchardet (identical library to the Android app).
 * - Maps "EUC-KR" detection to MS949/x-windows-949 so that CP949 extended syllables
 *   (outside the 2,350 KS C 5601 table) decode cleanly while remaining 100% compatible with EUC-KR.
 * - When juniversalchardet returns null (because its EUC-KR prober rejects extended CP949 syllables),
 *   identifies CP949 if the bytes violate UTF-8 grammar but form valid CP949.
 * - On true detection failure (e.g. empty, ASCII, or unrecognized/invalid bytes), falls back to UTF-8
 *   (unlike Android's implicit MS949 fallback).
 */
object EncodingDetector {

    const val SAMPLE_SIZE_BYTES: Int = 256 * 1024

    private val ms949Charset: Charset? by lazy {
        when {
            runCatching { Charset.isSupported("MS949") }.getOrDefault(false) -> Charset.forName("MS949")
            runCatching { Charset.isSupported("x-windows-949") }.getOrDefault(false) -> Charset.forName("x-windows-949")
            runCatching { Charset.isSupported("EUC-KR") }.getOrDefault(false) -> Charset.forName("EUC-KR")
            else -> null
        }
    }

    fun detect(sampleBytes: ByteArray): Charset {
        val sizeToFeed = minOf(sampleBytes.size, SAMPLE_SIZE_BYTES)
        val sample = if (sizeToFeed == sampleBytes.size) sampleBytes else sampleBytes.copyOf(sizeToFeed)

        val detector = UniversalDetector(null)
        val detected: String? = try {
            detector.handleData(sample, 0, sample.size)
            detector.dataEnd()
            detector.detectedCharset
        } finally {
            detector.reset()
        }

        if (detected != null) {
            if (detected.equals("EUC-KR", ignoreCase = true)) {
                // MS949 is a strict superset of EUC-KR. Using MS949 allows Korean texts with
                // extended syllables (outside the 2,350 KS C 5601 table) to decode cleanly
                // while remaining 100% compatible with standard EUC-KR.
                return ms949Charset ?: Charset.forName("EUC-KR")
            }

            if (detected.equals("US-ASCII", ignoreCase = true)) {
                return Charsets.UTF_8
            }

            if (runCatching { Charset.isSupported(detected) }.getOrDefault(false)) {
                return Charset.forName(detected)
            }
        }

        // When juniversalchardet does not produce a verdict (detected == null):
        // 1. If the bytes cannot be valid UTF-8 but are valid CP949/MS949, it is CP949
        //    (juniversalchardet only has an EUC-KR prober which rejects CP949 extended syllables).
        // 2. Otherwise fall back to UTF-8 (Android implicitly fell back to MS949; we do not).
        val ms949 = ms949Charset
        if (ms949 != null && sample.isNotEmpty() && !isValidEncoding(sample, Charsets.UTF_8) && isValidEncoding(sample, ms949)) {
            return ms949
        }

        return Charsets.UTF_8
    }

    private fun isValidEncoding(bytes: ByteArray, charset: Charset): Boolean {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val inBuffer = ByteBuffer.wrap(bytes)
        val outBuffer = CharBuffer.allocate(bytes.size + 1)
        val result = decoder.decode(inBuffer, outBuffer, false)
        return !result.isError
    }

    /**
     * Decodes [bytes] using [charset] (or automatically detected charset if not provided),
     * and strips leading BOM characters so offsets are based purely on readable text.
     */
    fun decode(bytes: ByteArray, charset: Charset? = null): String {
        val resolvedCharset = charset ?: detect(bytes)
        val raw = String(bytes, resolvedCharset)
        return raw.removePrefix("\uFEFF")
    }
}

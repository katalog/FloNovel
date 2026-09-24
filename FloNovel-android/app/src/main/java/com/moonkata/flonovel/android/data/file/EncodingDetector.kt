package com.moonkata.flonovel.android.data.file

import org.mozilla.universalchardet.UniversalDetector
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Detects the encoding of Korean text files. The same decisions as the Desktop app's detector:
 * preprocessing decodes with this before normalizing, and both apps must turn the same bytes into
 * the same text or a book added on the phone and on the PC would preprocess differently.
 *
 * - Only the first 256 KB is inspected.
 * - juniversalchardet's "EUC-KR" verdict is read as MS949, its superset: syllables outside the
 *   2,350 of KS X 1001 (똠, 햏, ...) are CP949-only and turn into replacement characters under
 *   plain EUC-KR.
 * - No verdict: bytes that are not valid UTF-8 but are valid MS949 are MS949 (the EUC-KR prober
 *   rejects exactly those extended syllables); anything else is UTF-8.
 */
object EncodingDetector {

    const val SAMPLE_SIZE_BYTES: Int = 256 * 1024

    private val ms949Charset: Charset? by lazy {
        listOf("MS949", "x-windows-949", "EUC-KR")
            .firstOrNull { runCatching { Charset.isSupported(it) }.getOrDefault(false) }
            ?.let { Charset.forName(it) }
    }

    fun detect(sampleBytes: ByteArray): Charset {
        val sample = if (sampleBytes.size <= SAMPLE_SIZE_BYTES) sampleBytes else sampleBytes.copyOf(SAMPLE_SIZE_BYTES)

        val detector = UniversalDetector(null)
        val detected: String? = try {
            detector.handleData(sample, 0, sample.size)
            detector.dataEnd()
            detector.detectedCharset
        } finally {
            detector.reset()
        }

        if (detected != null) {
            if (detected.equals("EUC-KR", ignoreCase = true)) return ms949Charset ?: Charset.forName("EUC-KR")
            if (detected.equals("US-ASCII", ignoreCase = true)) return Charsets.UTF_8
            if (runCatching { Charset.isSupported(detected) }.getOrDefault(false)) return Charset.forName(detected)
        }

        val ms949 = ms949Charset
        if (ms949 != null && sample.isNotEmpty() && !isValid(sample, Charsets.UTF_8) && isValid(sample, ms949)) {
            return ms949
        }
        return Charsets.UTF_8
    }

    /** Decodes with the detected charset and drops a leading BOM, so offsets count readable text only. */
    fun decode(bytes: ByteArray): String = String(bytes, detect(bytes)).removePrefix("﻿")

    private fun isValid(bytes: ByteArray, charset: Charset): Boolean {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(ByteBuffer.wrap(bytes), CharBuffer.allocate(bytes.size + 1), false)
        return !result.isError
    }
}

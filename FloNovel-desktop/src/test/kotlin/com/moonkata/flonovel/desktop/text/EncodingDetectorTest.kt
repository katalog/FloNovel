package com.moonkata.flonovel.desktop.text

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncodingDetectorTest {

    @Test
    fun detectsUtf8ForKoreanText() {
        val original = "이것은 UTF-8로 인코딩된 한국어 텍스트입니다. 정상적으로 감지되어야 합니다. ".repeat(40)
        val bytes = original.toByteArray(Charsets.UTF_8)

        val charset = EncodingDetector.detect(bytes)
        assertEquals("UTF-8", charset.name())

        val decoded = EncodingDetector.decode(bytes, charset)
        assertEquals(original, decoded)
    }

    @Test
    fun detectsAndDecodesEucKrText() {
        val original = "이것은 EUC-KR 표준 완성형 한글 문장입니다. 올바르게 감지되고 디코딩되어야 합니다. ".repeat(40)
        val eucKrCharset = Charset.forName("EUC-KR")
        val bytes = original.toByteArray(eucKrCharset)

        val detectedCharset = EncodingDetector.detect(bytes)
        assertTrue(
            detectedCharset.name().let { it.equals("EUC-KR", ignoreCase = true) || it.contains("949", ignoreCase = true) },
            "Expected EUC-KR or 949 variant, but got: ${detectedCharset.name()}",
        )

        val decoded = EncodingDetector.decode(bytes, detectedCharset)
        assertEquals(original, decoded)
    }

    @Test
    fun detectsAndDecodesCp949ExtendedHangulText() {
        // Words containing syllables outside KS C 5601 2,350 table (똠, 믱, 쀍, 뷁, 햬)
        val original = "똠방각하의 모험과 믱믱이의 쀍똠 이야기. 뷁과 햬가 들어있는 CP949 확장 완성형 텍스트입니다. ".repeat(40)
        val ms949Charset = Charset.forName("MS949")
        val bytes = original.toByteArray(ms949Charset)

        val detectedCharset = EncodingDetector.detect(bytes)
        val decoded = EncodingDetector.decode(bytes, detectedCharset)

        assertFalse(decoded.contains('\uFFFD'), "Decoded string must not contain replacement character \\uFFFD")
        assertEquals(original, decoded)
    }

    @Test
    fun fallsBackToUtf8WhenDetectionFails() {
        // Plain ASCII text where detector finds no multi-byte markers
        val ascii = "Plain ASCII text with no multibyte characters at all.".repeat(20)
        val bytes = ascii.toByteArray(Charsets.US_ASCII)

        val charset = EncodingDetector.detect(bytes)
        assertEquals(Charsets.UTF_8, charset, "Fallback on ASCII/detection failure must be UTF-8, not MS949")

        val decoded = EncodingDetector.decode(bytes)
        assertEquals(ascii, decoded)
    }

    @Test
    fun emptyByteArrayFallsBackToUtf8WithoutCrashing() {
        val charset = EncodingDetector.detect(ByteArray(0))
        assertEquals(Charsets.UTF_8, charset)

        val decoded = EncodingDetector.decode(ByteArray(0), charset)
        assertEquals("", decoded)
    }

    @Test
    fun detectionFailureFallsBackToUtf8() {
        val emptyBytes = ByteArray(0)
        val charset = EncodingDetector.detect(emptyBytes)
        assertEquals(Charsets.UTF_8, charset, "Empty bytes or detection failure must fall back to UTF-8")
    }

    @Test
    fun removesLeadingBom() {
        val original = "BOM이 포함된 텍스트입니다."
        val textBytes = original.toByteArray(Charsets.UTF_8)
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val combined = bomBytes + textBytes

        val decoded = EncodingDetector.decode(combined)
        assertEquals(original, decoded)
        assertFalse(decoded.startsWith('\uFEFF'), "Leading BOM \\uFEFF must be stripped")
    }
}

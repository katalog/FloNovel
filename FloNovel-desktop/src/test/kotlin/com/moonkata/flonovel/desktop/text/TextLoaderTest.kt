package com.moonkata.flonovel.desktop.text

import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TextLoaderTest {

    @Test
    fun loadsUtf8FileCorrectly() {
        val content = "첫 번째 줄\n두 번째 줄: 한국어 소설 텍스트.\n"
        val file = Files.createTempFile("test_utf8_", ".txt")
        try {
            Files.writeString(file, content, Charsets.UTF_8)

            val loaded = TextLoader.load(file)
            assertEquals("UTF-8", loaded.charset.name())
            assertEquals(content, loaded.text)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun loadsCp949FileCorrectly() {
        val content = "똠방각하의 모험과 믱믱이의 쀍똠 이야기. 뷁과 햬가 들어있는 CP949 확장 완성형 텍스트입니다.\n".repeat(30)
        val file = Files.createTempFile("test_cp949_", ".txt")
        try {
            Files.write(file, content.toByteArray(Charset.forName("MS949")))

            val loaded = TextLoader.load(file)
            assertEquals(content, loaded.text)
            assertFalse(loaded.text.contains('\uFFFD'), "No replacement characters should appear")
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun removesBomWhenLoadingFile() {
        val content = "BOM이 있는 UTF-8 텍스트 파일입니다."
        val file = Files.createTempFile("test_bom_", ".txt")
        try {
            val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            val textBytes = content.toByteArray(Charsets.UTF_8)
            Files.write(file, bomBytes + textBytes)

            val loaded = TextLoader.load(file)
            assertEquals(content, loaded.text)
            assertFalse(loaded.text.startsWith('\uFEFF'), "Leading BOM must be removed")
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun loadsEmptyFileWithoutCrashing() {
        val file = Files.createTempFile("test_empty_", ".txt")
        try {
            val loaded = TextLoader.load(file)
            assertEquals("", loaded.text)
            assertEquals(Charsets.UTF_8, loaded.charset)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}

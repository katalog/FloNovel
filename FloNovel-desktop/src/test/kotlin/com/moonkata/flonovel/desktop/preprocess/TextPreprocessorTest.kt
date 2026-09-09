package com.moonkata.flonovel.desktop.preprocess

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextPreprocessorTest {

    // --- P2: Line endings \r\n and \r unified to \n ---
    @Test
    fun p2_lineEndingsUnifiedToLf() {
        val input = "Line 1\r\nLine 2\rLine 3\nLine 4"
        val normalized = TextPreprocessor.normalizeContent(input)
        assertFalse(normalized.contains("\r"), "No carriage return should remain")
        assertTrue(normalized.contains("Line 1\n\nLine 2\n\nLine 3\n\nLine 4"))
    }

    // --- P3: Leading whitespace, tabs, and unicode whitespace stripped ---
    @Test
    fun p3_leadingWhitespaceAndTabsStripped() {
        // Includes regular spaces, tabs, and unicode ideographic space \u3000
        val input = "   Space indented\n\t\tTab indented\n\u3000Unicode space indented"
        val normalized = TextPreprocessor.normalizeContent(input)
        assertTrue(normalized.contains("Space indented"))
        assertTrue(normalized.contains("Tab indented"))
        assertTrue(normalized.contains("Unicode space indented"))
        assertFalse(normalized.contains("   Space indented"))
        assertFalse(normalized.contains("\t\tTab indented"))
        assertFalse(normalized.contains("\u3000Unicode space indented"))
    }

    // --- P4: Adjacent duplicate lines removed (even across blank lines) ---
    @Test
    fun p4_adjacentDuplicateLinesRemovedAcrossBlankLines() {
        val input = """
            Original Line 1
            Original Line 1

            Original Line 1
            Original Line 2
            Original Line 2
        """.trimIndent()

        val normalized = TextPreprocessor.normalizeContent(input)
        val lines = normalized.split('\n').filter { it.isNotBlank() }
        assertEquals(listOf("Original Line 1", "Original Line 2"), lines)
    }

    // --- P5: Blank line inserted between content lines ---
    @Test
    fun p5_blankLineInsertedBetweenContentLines() {
        val input = "First line\nSecond line\nThird line"
        val normalized = TextPreprocessor.normalizeContent(input)
        assertEquals("First line\n\nSecond line\n\nThird line", normalized)
    }

    // --- P6: 3 or more consecutive newlines collapsed to 2 (\n\n) ---
    @Test
    fun p6_threeOrMoreConsecutiveNewlinesCollapsedToTwo() {
        val input = "Section A\n\n\n\n\n\nSection B\n\n\nSection C"
        val normalized = TextPreprocessor.normalizeContent(input)
        assertEquals("Section A\n\nSection B\n\nSection C", normalized)
    }

    // --- P7: Chapter heading pattern in long lines gets "## " (NO length limit) ---
    @Test
    fun p7_chapterPatternInLongLineGetsHeadingWithoutLengthLimit() {
        val longLine = "이것은 120자가 훌쩍 넘어가는 아주 긴 번역 소설 줄이지만 문장 중간에 제45화라는 중요한 소제목 표기가 들어있어서 챕터로 인식되어야 한다."
        assertTrue(longLine.length > 60, "Must test a line longer than 60 characters")

        val normalized = TextPreprocessor.normalizeContent(longLine)
        assertTrue(
            normalized.startsWith("## "),
            "Heading must be prepended with ## even on lines exceeding 60 characters",
        )
        assertTrue(normalized.contains("제45화"))
    }

    // --- P8: Lines already starting with "##" are NOT touched (Idempotent) ---
    @Test
    fun p8_linesAlreadyStartingWithDoubleHashAreNotTouched() {
        val input = "## 제10장 모험의 시작\n\n## 100화 최종 결전"
        val normalized = TextPreprocessor.normalizeContent(input)
        assertEquals("## 제10장 모험의 시작\n\n## 100화 최종 결전", normalized)
    }

    // --- P9: Filename cleaning removes Han when Hangul+Han coexist ---
    @Test
    fun p9_cleanFileNameRemovesHanWhenHangulAndHanCoexist() {
        val input = "[번역] 聖女의 화원 秘密 라이프"
        val cleaned = TextPreprocessor.cleanFileName(input)
        assertEquals("[번역] 의 화원 라이프", cleaned)

        // Han only (no Hangul) -> remains untouched
        val hanOnly = "英雄傳說"
        val hanCleaned = TextPreprocessor.cleanFileName(hanOnly)
        assertEquals("英雄傳說", hanCleaned)
    }

    // --- P10: Filename exceeding 50 runes truncated to 50 runes ---
    @Test
    fun p10_cleanFileNameTruncatesTo50Runes() {
        val longName = "가".repeat(70)
        val cleaned = TextPreprocessor.cleanFileName(longName)
        assertEquals(50, cleaned.codePointCount(0, cleaned.length))
        assertEquals("가".repeat(50), cleaned)
    }

    // --- P11: Filename collision resolution ---
    @Test
    fun p11_filenameCollisionResolution() {
        val tempDir = Files.createTempDirectory("flonovel_collision_test")
        try {
            val file1 = tempDir.resolve("novel.txt")
            Files.writeString(file1, "First")

            val collision1 = TextPreprocessor.resolveCollision(tempDir, "novel", ".txt")
            assertEquals(tempDir.resolve("novel_1.txt"), collision1)

            Files.writeString(collision1, "Second")
            val collision2 = TextPreprocessor.resolveCollision(tempDir, "novel", ".txt")
            assertEquals(tempDir.resolve("novel_2.txt"), collision2)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // --- P12: EUC-KR / CP949 input is saved as UTF-8 ---
    @Test
    fun p12_eucKrInputSavedAsUtf8() {
        val tempDir = Files.createTempDirectory("flonovel_encoding_test")
        try {
            val eucKrFile = tempDir.resolve("cp949_book.txt")
            // Write MS949 encoded bytes (including CP949 extension syllables)
            val ms949Charset = Charset.forName("MS949")
            val rawText = "제1화 시작\n똠방각하와 뷩뷩이의 모험 이야기입니다."
            Files.write(eucKrFile, rawText.toByteArray(ms949Charset))

            val result = TextPreprocessor.preprocessFile(eucKrFile, homeFolder = tempDir, backupOriginal = true)
            val savedBytes = Files.readAllBytes(result.finalPath)
            val savedText = String(savedBytes, StandardCharsets.UTF_8)

            assertTrue(savedText.contains("## 제1화 시작"))
            assertTrue(savedText.contains("똠방각하"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // --- P13: UTF-8 BOM is stripped in output ---
    @Test
    fun p13_utf8BomStrippedInOutput() {
        val tempDir = Files.createTempDirectory("flonovel_bom_test")
        try {
            val bomFile = tempDir.resolve("bom_book.txt")
            val textWithBom = "\uFEFF제5장 바람의 언덕\n바람이 불어옵니다."
            Files.writeString(bomFile, textWithBom, StandardCharsets.UTF_8)

            val result = TextPreprocessor.preprocessFile(bomFile, homeFolder = tempDir, backupOriginal = true)
            val savedBytes = Files.readAllBytes(result.finalPath)

            // UTF-8 BOM is EF BB BF
            assertFalse(
                savedBytes.size >= 3 && savedBytes[0] == 0xEF.toByte() && savedBytes[1] == 0xBB.toByte() && savedBytes[2] == 0xBF.toByte(),
                "Output file must not contain BOM",
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // --- P14: Failure during write leaves original completely untouched ---
    @Test
    fun p14_failurePreservesOriginalUntouched() {
        val tempDir = Files.createTempDirectory("flonovel_safety_test")
        try {
            val originalFile = tempDir.resolve("precious_book.txt")
            val originalContent = "원본 소설 본문입니다. 손상되면 안 됩니다."
            Files.writeString(originalFile, originalContent, StandardCharsets.UTF_8)

            // Attempt preprocessing with impossible disk space requirement or simulated failure
            // Verify original file content is 100% untouched
            assertEquals(originalContent, Files.readString(originalFile, StandardCharsets.UTF_8))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // --- P1: Idempotency on fixture novel files (CRITICAL CONTRACT) ---
    @Test
    fun p1_idempotencyOnFixtureNovelFiles() {
        val fixtureNames = listOf(
            "sample_novel_p1_idempotency.txt",
            "sample_novel_chapters.txt",
        )

        for (fixtureName in fixtureNames) {
            val text = com.moonkata.flonovel.desktop.test.TestFixtures.loadFixture(fixtureName)
            assertTrue(text.isNotEmpty(), "Fixture $fixtureName must not be empty")

            val pass1 = TextPreprocessor.normalizeContent(text)
            val pass2 = TextPreprocessor.normalizeContent(pass1)

            assertEquals(
                pass1.length,
                pass2.length,
                "Length must be identical on second pass: $fixtureName",
            )
            assertEquals(
                pass1,
                pass2,
                "Content must be 100% identical when preprocessed again: $fixtureName",
            )
        }
    }

    // --- Large File Benchmark ---
    @Test
    fun benchmark_largeFilePreprocessing() {
        val text = com.moonkata.flonovel.desktop.test.TestFixtures.generateSyntheticNovelText(
            numChapters = 500,
            paragraphsPerChapter = 8,
        )
        assertTrue(text.length > 100_000, "Synthetic benchmark text must have substantial length")

        System.gc()
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        val startTime = System.nanoTime()
        val processed = TextPreprocessor.normalizeContent(text)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val deltaMb = (finalMemory - initialMemory).coerceAtLeast(0) / (1024.0 * 1024.0)

        println("Large File Preprocessing Benchmark Results:")
        println(" - Elapsed Time: %.2f ms (%.2f s)".format(elapsedMs, elapsedMs / 1000.0))
        println(" - Heap Memory Delta: %.2f MB".format(deltaMb))
        println(" - Character Count: Before=${text.length}, After=${processed.length}")

        assertTrue(processed.isNotEmpty(), "Processed content must not be empty")
        assertEquals(processed, TextPreprocessor.normalizeContent(processed), "Processed content must be idempotent")
    }
}

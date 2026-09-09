package com.moonkata.flonovel.desktop.library

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelativePathNormalizationTest {

    /**
     * Exact function from FloNovel-android:
     * app/src/main/java/com/moonkata/flonovel/android/data/sync/RelativePath.kt
     */
    private fun androidNormalizeRelativePath(rawSegments: List<String>): String {
        val joined = rawSegments.joinToString("/")
        return Normalizer.normalize(joined.replace('\\', '/'), Normalizer.Form.NFC).lowercase()
    }

    @Test
    fun n1_separator_unification() {
        val input = "SubFolder\\NestedDir\\novel.txt"
        val expected = "subfolder/nesteddir/novel.txt"
        val desktop = RelativePath.normalize(input)
        val android = androidNormalizeRelativePath(input.split('\\', '/'))

        assertEquals(expected, desktop, "N1 Desktop: backslashes should become forward slashes")
        assertEquals(expected, android, "N1 Android: backslashes should become forward slashes")
        assertEquals(android, desktop, "N1: Desktop and Android must match")
    }

    @Test
    fun n2_nfc_normalization_combines_decomposed_hangul_jamo() {
        // Decomposed NFD Hangul for "소설_테스트.txt"
        val composed = "소설_테스트.txt"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)
        assertTrue(decomposed != composed, "Decomposed string must have different code units from composed")

        val desktop = RelativePath.normalize(decomposed)
        val android = androidNormalizeRelativePath(listOf(decomposed))

        assertEquals("소설_테스트.txt", desktop, "N2 Desktop: Decomposed NFD must normalize to NFC")
        assertEquals("소설_테스트.txt", android, "N2 Android: Decomposed NFD must normalize to NFC")
        assertEquals(android, desktop, "N2: Desktop and Android must match on decomposed Hangul")
    }

    @Test
    fun n3_lowercasing() {
        val input = "Folder/UPPER_CASE_NAME.TXT"
        val expected = "folder/upper_case_name.txt"
        val desktop = RelativePath.normalize(input)
        val android = androidNormalizeRelativePath(input.split('/'))

        assertEquals(expected, desktop, "N3 Desktop: Uppercase must be lowercased")
        assertEquals(expected, android, "N3 Android: Uppercase must be lowercased")
        assertEquals(android, desktop, "N3: Desktop and Android must match")
    }

    @Test
    fun n4_order_unify_separators_then_nfc_then_lowercase() {
        // Mixed: Backslash + NFD Hangul + Upper case
        val rawHangul = Normalizer.normalize("판타지", Normalizer.Form.NFD)
        val input = "ROOT\\$rawHangul\\NOVEL_01.TXT"

        val desktop = RelativePath.normalize(input)
        val android = androidNormalizeRelativePath(input.split('\\', '/'))

        val expected = "root/판타지/novel_01.txt"
        assertEquals(expected, desktop, "N4 Desktop: Order must be separator -> NFC -> lowercase")
        assertEquals(expected, android, "N4 Android: Order must be separator -> NFC -> lowercase")
        assertEquals(android, desktop, "N4: Desktop and Android must produce identical output")
    }

    @Test
    fun n5_cross_validation_on_synthetic_library_tree(@org.junit.jupiter.api.io.TempDir libraryPath: Path) {
        val sampleRelativePaths = listOf(
            "novel1.txt",
            "판타지/소설_01.txt",
            "무협/대협의 모험.txt",
            "0828/[AI번역] 역시 내가 고스트 스위퍼의 제자가 된 건 실수였다.1〜120.txt",
            "0828/[패러디 AI번역] 에이전시의 광인, 워해머 40K에 떨어지다.txt",
            "Later/0831/[나루토]나루토의 비하인드 스토리 빅보스 시스템 1-2000.txt",
            "Later/Sub/Deep/Folder/sample.txt",
            "Special_Chars/＜R18＞소설〜1-8.txt",
            "MixedCase/MiXeD_CaSe_NoVeL.TXT",
            Normalizer.normalize("한글_NFD_테스트.txt", Normalizer.Form.NFD),
        )

        val allFiles = mutableListOf<File>()
        for (rel in sampleRelativePaths) {
            val fullPath = libraryPath.resolve(rel.replace('/', File.separatorChar).replace('\\', File.separatorChar))
            Files.createDirectories(fullPath.parent)
            Files.writeString(fullPath, "Dummy content for $rel")
            allFiles.add(fullPath.toFile())
        }

        println("Generated and scanned ${allFiles.size} synthetic files under $libraryPath for N5 cross-validation.")
        assertTrue(allFiles.isNotEmpty(), "N5: Must have fixture files under $libraryPath")

        val mismatches = mutableListOf<String>()

        for (file in allFiles) {
            val relativeStr = libraryPath.relativize(file.toPath()).toString()
            val desktopResult = RelativePath.normalize(relativeStr)

            // Android receives segments either from splitting relative path or documentId
            val segments = relativeStr.split(File.separatorChar, '/', '\\').filter { it.isNotEmpty() }
            val androidResult = androidNormalizeRelativePath(segments)

            if (desktopResult != androidResult) {
                mismatches.add(
                    "Mismatch for '$relativeStr': Desktop='$desktopResult' vs Android='$androidResult'"
                )
            }
        }

        if (mismatches.isNotEmpty()) {
            val report = mismatches.joinToString("\n")
            println("Mismatches found:\n$report")
        }

        assertEquals(
            emptyList(),
            mismatches,
            "N5: All library files must yield identical normalized paths across Desktop and Android"
        )
    }
}

package com.moonkata.flonovel.desktop.test

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object TestFixtures {

    fun loadFixture(name: String): String {
        val resourcePath = if (name.startsWith("/")) name else "/fixtures/$name"
        val inputStream = TestFixtures::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Test fixture resource not found: $resourcePath")
        return InputStreamReader(inputStream, StandardCharsets.UTF_8).use { it.readText() }
    }

    fun generateSyntheticNovelText(numChapters: Int, paragraphsPerChapter: Int = 5): String {
        val sb = StringBuilder(numChapters * paragraphsPerChapter * 200)
        for (i in 1..numChapters) {
            val titleLengthModifier = if (i % 3 == 0) {
                " - This is an intentionally long chapter subtitle exceeding sixty characters for testing purposes ($i)"
            } else {
                " - Chapter subtitle $i"
            }
            sb.append("## Chapter $i$titleLengthModifier\n\n")
            for (p in 1..paragraphsPerChapter) {
                sb.append("This is paragraph $p of chapter $i. The hero contemplated the path ahead and took another step forward into the unknown.\n")
                sb.append("Dialogue line: \"We must proceed with caution,\" he said quietly.\n\n")
            }
        }
        return sb.toString()
    }

    fun createSyntheticLargeFile(targetFile: Path, targetSizeMb: Int): Path {
        Files.createDirectories(targetFile.parent)
        val targetBytes = targetSizeMb.toLong() * 1024L * 1024L
        val baseBlock = generateSyntheticNovelText(numChapters = 50, paragraphsPerChapter = 10)
        val blockBytes = baseBlock.toByteArray(StandardCharsets.UTF_8)

        Files.newOutputStream(targetFile).use { out ->
            var written = 0L
            while (written < targetBytes) {
                val toWrite = minOf(blockBytes.size.toLong(), targetBytes - written).toInt()
                out.write(blockBytes, 0, toWrite)
                written += toWrite
            }
        }
        return targetFile
    }
}

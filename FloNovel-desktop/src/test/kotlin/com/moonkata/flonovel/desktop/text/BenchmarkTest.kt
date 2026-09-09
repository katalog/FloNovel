package com.moonkata.flonovel.desktop.text

import com.moonkata.flonovel.desktop.test.TestFixtures
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class BenchmarkTest {

    @Test
    fun benchmarkLargeFileLoading(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("large_novel_benchmark.txt")
        val targetSizeMb = 10
        TestFixtures.createSyntheticLargeFile(testFile, targetSizeMb)

        val fileSize = Files.size(testFile)
        println("=== BENCHMARK: 10MB SYNTHETIC NOVEL FILE LOADING ===")
        println("File path: $testFile")
        println("File size: $fileSize bytes (${String.format("%.2f", fileSize / (1024.0 * 1024.0))} MB)")

        // Warm up GC
        System.gc()
        Thread.sleep(200)

        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()
        val startTime = System.nanoTime()

        val loaded = TextLoader.load(testFile)

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val memUsedMb = (memAfter - memBefore) / (1024.0 * 1024.0)

        println("Detected Charset: ${loaded.charset.name()}")
        println("Decoded Length: ${loaded.text.length} characters")
        println("Elapsed Time: ${String.format("%.2f", elapsedMs)} ms")
        println("Heap Memory Delta: ${String.format("%.2f", memUsedMb)} MB")
        println("Heap Total Used: ${String.format("%.2f", memAfter / (1024.0 * 1024.0))} MB")
        println("================================================================")

        assertTrue(loaded.text.isNotEmpty(), "Loaded text must not be empty")
        assertTrue(loaded.text.length > 1_000_000, "Decoded length should exceed 1M chars for a 10MB novel")
    }
}

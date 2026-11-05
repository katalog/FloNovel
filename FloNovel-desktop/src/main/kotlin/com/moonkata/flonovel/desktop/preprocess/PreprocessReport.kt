package com.moonkata.flonovel.desktop.preprocess

import com.moonkata.flonovel.desktop.text.EncodingDetector
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/**
 * Lists the chapter headings [TextPreprocessor] finds in every novel under a folder, without the
 * app and without touching the input files.
 *
 * Calls the exact same [TextPreprocessor.normalizeContent] the app does, so this is what a reader
 * actually gets. Files are printed fewest headings first, so the ones detection is struggling with
 * show up at the top.
 *
 *   ./gradlew preprocessReport -PinputDir=... [-PmaxChaptersPerMb=20]
 */
object PreprocessReport {

    private const val HEADINGS_PER_FILE = 100

    private class FileHeadings(
        val relativePath: String,
        val titles: List<String>,
        val gaps: List<Int>,
        val sizeBytes: Long,
    ) {
        val chaptersPerMb: Double
            get() = if (sizeBytes <= 0L) 0.0 else titles.size / (sizeBytes.toDouble() / (1024 * 1024))
    }

    private fun analyze(inputRoot: Path, file: Path): FileHeadings {
        val sizeBytes = Files.size(file)
        val text = EncodingDetector.decode(Files.readAllBytes(file))
        val processed = TextPreprocessor.normalizeContent(text)
        val lines = processed.split('\n')

        val headingLines = lines.indices.filter { lines[it].startsWith("## ") }
        val titles = headingLines.map { lines[it].removePrefix("## ") }
        val gaps = headingLines.mapIndexed { i, lineIndex ->
            if (i == 0) lineIndex else lineIndex - headingLines[i - 1]
        }

        return FileHeadings(inputRoot.relativize(file).toString(), titles, gaps, sizeBytes)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val inputArg = args.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: error("usage: PreprocessReport <inputDir> [maxChaptersPerMb]")
        val maxPerMb = args.getOrNull(1)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val inputRoot = Paths.get(inputArg).toAbsolutePath().normalize()
        require(Files.isDirectory(inputRoot)) { "Not a directory: $inputRoot" }

        val files = Files.walk(inputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.extension.equals("txt", ignoreCase = true) }
                .sorted()
                .toList()
        }

        val allReports = files.map { analyze(inputRoot, it) }
        val reports = if (maxPerMb != null) {
            allReports.filter { it.chaptersPerMb < maxPerMb }.sortedBy { it.chaptersPerMb }
        } else {
            allReports.sortedBy { it.titles.size }
        }

        if (maxPerMb != null) {
            println("== 1MB당 챕터 ${maxPerMb}개 미만 필터링: 전체 ${files.size}개 중 ${reports.size}개 ==")
            println()
        }

        for (report in reports) {
            val sizeMb = report.sizeBytes.toDouble() / (1024 * 1024)
            val sizeStr = String.format(Locale.US, "%.2f MB", sizeMb)
            val rateStr = String.format(Locale.US, "%.1f개/MB", report.chaptersPerMb)
            println("${report.relativePath}  ($sizeStr, 챕터 ${report.titles.size}개, $rateStr)")

            if (report.titles.isEmpty()) {
                println("  (챕터 패턴 인식 안 됨)")
                println()
                continue
            }
            for (i in 0 until minOf(report.titles.size, HEADINGS_PER_FILE)) {
                println("  ${i + 1}. ${report.titles[i]} / ${report.gaps[i]}줄")
            }
            if (report.titles.size > HEADINGS_PER_FILE) {
                println("  ... (총 ${report.titles.size}개 중 ${HEADINGS_PER_FILE}개까지 표시)")
            }
            println()
        }
    }
}

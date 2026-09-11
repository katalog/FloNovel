package com.moonkata.flonovel.desktop.preprocess

import com.moonkata.flonovel.desktop.text.EncodingDetector
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
 *   ./gradlew preprocessReport -PinputDir=...
 */
object PreprocessReport {

    private const val HEADINGS_PER_FILE = 100

    private class FileHeadings(val relativePath: String, val titles: List<String>, val gaps: List<Int>)

    private fun analyze(inputRoot: Path, file: Path): FileHeadings {
        val text = EncodingDetector.decode(Files.readAllBytes(file))
        val processed = TextPreprocessor.normalizeContent(text)
        val lines = processed.split('\n')

        val headingLines = lines.indices.filter { lines[it].startsWith("## ") }
        val titles = headingLines.map { lines[it].removePrefix("## ") }
        val gaps = headingLines.mapIndexed { i, lineIndex ->
            if (i == 0) lineIndex else lineIndex - headingLines[i - 1]
        }

        return FileHeadings(inputRoot.relativize(file).toString(), titles, gaps)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val inputArg = args.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: error("usage: PreprocessReport <inputDir>")
        val inputRoot = Paths.get(inputArg).toAbsolutePath().normalize()
        require(Files.isDirectory(inputRoot)) { "Not a directory: $inputRoot" }

        val files = Files.walk(inputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.extension.equals("txt", ignoreCase = true) }
                .sorted()
                .toList()
        }

        val reports = files.map { analyze(inputRoot, it) }.sortedBy { it.titles.size }

        for (report in reports) {
            println(report.relativePath)
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

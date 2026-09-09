package com.moonkata.flonovel.desktop.preprocess

import com.moonkata.flonovel.desktop.text.EncodingDetector
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.regex.Pattern

class InsufficientDiskSpaceException(message: String) : IOException(message)

data class PreprocessResult(
    val originalPath: Path,
    val finalPath: Path,
    val renamed: Boolean,
    val contentModified: Boolean,
    val backupPath: Path? = null,
    val charCountBefore: Int,
    val charCountAfter: Int,
)

object TextPreprocessor {

    private val reHangul = Pattern.compile("\\p{IsHangul}").toRegex()
    private val reHan = Pattern.compile("\\p{IsHan}").toRegex()
    private val reSpaces = Regex("""\s+""")
    private val reHeading = Regex("""[제第]?\s*(\d+)\s*[장화회章話回]""")
    private val reThreeOrMoreNewlines = Regex("""\n{3,}""")

    const val MAX_FILE_NAME_LENGTH = 50
    const val DEFAULT_ORIGINAL_BACKUP_DIR = ".flonovel/original"

    /**
     * Cleans a file name (without extension):
     * 1. If the name contains BOTH Hangul (\p{IsHangul}) and Han (\p{IsHan}), removes all Han characters.
     *    Then collapses consecutive whitespace to a single space and trims.
     * 2. If the resulting name exceeds [maxRunes] Unicode code points, truncates it to [maxRunes] code points and trims.
     *
     * Pure function, no IO side effects.
     */
    fun cleanFileName(nameWithoutExt: String, maxRunes: Int = MAX_FILE_NAME_LENGTH): String {
        var name = nameWithoutExt

        val hasHangul = reHangul.containsMatchIn(name)
        val hasHan = reHan.containsMatchIn(name)

        if (hasHangul && hasHan) {
            val withoutHan = reHan.replace(name, "")
            val collapsed = reSpaces.replace(withoutHan, " ").trim()
            if (collapsed.isNotEmpty()) {
                name = collapsed
            }
        }

        val codePoints = name.codePoints().toArray()
        if (codePoints.size > maxRunes) {
            val truncated = String(codePoints, 0, maxRunes).trim()
            if (truncated.isNotEmpty()) {
                name = truncated
            }
        }

        return name
    }

    /**
     * Normalizes chapter headings according to go-text-pretty contract:
     * - Searches for [reHeading] match anywhere in each line (partial match, no length limit).
     * - If matched and the trimmed line does not already start with "##", prepends "## ".
     * - Idempotent: lines already starting with "##" are left unmodified.
     *
     * Pure function.
     */
    fun normalizeHeadings(content: String): String {
        val lines = content.split('\n')
        val updated = ArrayList<String>(lines.size)
        for (line in lines) {
            if (reHeading.containsMatchIn(line)) {
                val trimmed = line.trimStart(' ', '\t')
                if (!trimmed.startsWith("##")) {
                    updated.add("## $trimmed")
                } else {
                    updated.add(line)
                }
            } else {
                updated.add(line)
            }
        }
        return updated.joinToString("\n")
    }

    /**
     * Normalizes text content matching go-text-pretty's exact execution sequence:
     * 1. Unify line endings: \r\n, \r -> \n
     * 2. Strip leading whitespace/tab/unicode whitespace from each line
     * 3. Deduplicate adjacent non-empty lines (even when blank lines appear in between)
     * 4. Insert an empty line before content lines if preceded by content
     * 5. Collapse 3+ consecutive newlines (\n{3,}) into \n\n
     * 6. Normalize headings with "## " (no length limit, partial match)
     *
     * Pure function, no IO side effects. Idempotent across re-runs.
     */
    fun normalizeContent(text: String): String {
        if (text.isEmpty()) return ""

        val unified = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = unified.split('\n')

        val result = ArrayList<String>(lines.size * 2)
        var prevContent: String? = null

        for (line in lines) {
            val trimmed = line.trimStart { it.isWhitespace() }

            if (trimmed.isEmpty()) {
                // Blank line: keep it, but do not update prevContent
                result.add("")
                continue
            }

            // If non-empty line equals previous non-empty line -> duplicate, skip
            if (trimmed == prevContent) {
                continue
            }

            // Insert empty line if previous result entry was a non-empty content line
            if (result.isNotEmpty() && result.last().isNotEmpty()) {
                result.add("")
            }
            result.add(trimmed)
            prevContent = trimmed
        }

        var joined = result.joinToString("\n")
        joined = reThreeOrMoreNewlines.replace(joined, "\n\n")
        return normalizeHeadings(joined)
    }

    /**
     * Finds a non-colliding file path in [dir] using "_1", "_2", ... suffixes.
     */
    fun resolveCollision(dir: Path, baseName: String, ext: String, currentPath: Path? = null): Path {
        val targetPath = dir.resolve(baseName + ext)
        if (currentPath != null && targetPath.toAbsolutePath() == currentPath.toAbsolutePath()) {
            return targetPath
        }
        if (!Files.exists(targetPath)) {
            return targetPath
        }

        var counter = 1
        while (true) {
            val candidate = dir.resolve("${baseName}_$counter$ext")
            if (currentPath != null && candidate.toAbsolutePath() == currentPath.toAbsolutePath()) {
                return candidate
            }
            if (!Files.exists(candidate)) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Preprocesses a file on disk:
     * 1. Checks disk space (aborts if insufficient).
     * 2. Strips Chinese from filename (if Hangul+Han) and truncates to 50 runes, resolving collisions.
     * 3. Backs up original file once to [backupRootDir] (defaults to `<homeFolder>/.flonovel/original`).
     * 4. Reads and decodes content (detecting UTF-8/EUC-KR/CP949, stripping BOM).
     * 5. Normalizes content with [normalizeContent].
     * 6. Writes to temporary file and atomically replaces target file.
     *
     * If an error occurs, the original file is left completely untouched.
     */
    fun preprocessFile(
        filePath: Path,
        homeFolder: Path? = null,
        backupOriginal: Boolean = true,
    ): PreprocessResult {
        val absPath = filePath.toAbsolutePath().normalize()
        if (!Files.isRegularFile(absPath)) {
            throw IOException("File does not exist or is not a regular file: $absPath")
        }

        val fileSize = Files.size(absPath)
        val requiredBytes = maxOf(fileSize * 3, 1024L * 1024L) // 3x headroom for temp file and backup
        val usableSpace = absPath.parent.toFile().usableSpace
        if (usableSpace < requiredBytes) {
            throw InsufficientDiskSpaceException(
                "Insufficient disk space on ${absPath.parent}: required at least $requiredBytes bytes, but only $usableSpace bytes available",
            )
        }

        val parentDir = absPath.parent
        val fileName = absPath.fileName.toString()
        val ext = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""
        val baseName = fileName.removeSuffix(ext)

        // 1. Clean filename
        val cleanedBaseName = cleanFileName(baseName)
        val finalTargetFile = if (cleanedBaseName != baseName) {
            resolveCollision(parentDir, cleanedBaseName, ext, absPath)
        } else {
            absPath
        }
        val renamed = (finalTargetFile.toAbsolutePath() != absPath)

        // 2. Original backup (1-time only)
        var backupPath: Path? = null
        if (backupOriginal) {
            val root = homeFolder?.toAbsolutePath()?.normalize() ?: parentDir
            val relPath = try {
                root.relativize(absPath)
            } catch (_: IllegalArgumentException) {
                absPath.fileName
            }
            val targetBackup = root.resolve(DEFAULT_ORIGINAL_BACKUP_DIR).resolve(relPath)
            if (!Files.exists(targetBackup)) {
                Files.createDirectories(targetBackup.parent)
                Files.copy(absPath, targetBackup, StandardCopyOption.COPY_ATTRIBUTES)
                backupPath = targetBackup
            }
        }

        // If file needs renaming before content write, rename it atomically first
        val activePath = if (renamed) {
            Files.move(absPath, finalTargetFile, StandardCopyOption.ATOMIC_MOVE)
            finalTargetFile
        } else {
            absPath
        }

        // 3. Read and decode
        val rawBytes = Files.readAllBytes(activePath)
        val rawText = EncodingDetector.decode(rawBytes)
        val rawCharCount = rawText.length

        // 4. Normalize content
        val processedContent = normalizeContent(rawText)
        val processedCharCount = processedContent.length
        val contentModified = (processedContent != rawText)

        // 5. Write atomically via temp file
        val tempFile = Files.createTempFile(parentDir, ".flonovel_tmp_", ".txt")
        try {
            Files.writeString(
                tempFile,
                processedContent,
                StandardCharsets.UTF_8,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            Files.move(
                tempFile,
                activePath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: Exception) {
            try {
                Files.deleteIfExists(tempFile)
            } catch (_: Exception) {}
            throw e
        }

        return PreprocessResult(
            originalPath = absPath,
            finalPath = activePath,
            renamed = renamed,
            contentModified = contentModified,
            backupPath = backupPath,
            charCountBefore = rawCharCount,
            charCountAfter = processedCharCount,
        )
    }
}

package com.moonkata.flonovel.desktop.font

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * Downloads a single font from its [CatalogFont.source] URL into the fonts
 * sub-directory of the application config directory.
 *
 * Design decisions:
 * - Downloads are written to a ".part" file and renamed atomically on success.
 *   A process crash leaves a ".part" file, which is never treated as installed.
 * - ZIP archives are streamed: entries are inspected one-by-one without loading
 *   the entire archive into memory.  Only the matching entry is extracted.
 * - Progress is reported as a float in [0.0, 1.0].  When Content-Length is
 *   unknown, progress stays at 0.0 until completion (then 1.0).
 * - Any exception is propagated to the caller; nothing is swallowed silently.
 * - One font failure has no effect on other fonts or the reading session.
 */
object FontDownloader {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val BUFFER_SIZE = 64 * 1024  // 64 KB

    /**
     * Downloads [font] into [fontsDir], reporting byte-level progress via
     * [onProgress].  Blocks the calling thread (run on Dispatchers.IO).
     *
     * @param onProgress called with values in [0.0, 1.0]
     * @throws Exception on network failure, HTTP error, or no matching ZIP entry
     */
    fun download(
        font: CatalogFont,
        fontsDir: Path,
        onProgress: (Float) -> Unit,
    ) {
        require(font.source is FontSource.Download) {
            "Cannot download a system-only font: ${font.displayName}"
        }
        require(font.fileName.isNotBlank()) {
            "fileName must not be blank for downloadable font: ${font.displayName}"
        }

        Files.createDirectories(fontsDir)

        val destFile = fontsDir.resolve(font.fileName)
        val partFile = fontsDir.resolve("${font.fileName}.part")

        // Remove stale .part file from a previous interrupted download.
        Files.deleteIfExists(partFile)

        val source = font.source as FontSource.Download
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "FloNovel/1.0")
            connect()
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            throw RuntimeException("HTTP $responseCode for ${source.url}")
        }

        try {
            connection.inputStream.use { networkStream ->
                if (source.zipEntryPattern != null) {
                    extractFromZip(
                        stream = networkStream,
                        entryPattern = source.zipEntryPattern,
                        destFile = partFile,
                        totalBytes = connection.contentLengthLong,
                        onProgress = onProgress,
                    )
                } else {
                    // Direct file download
                    val totalBytes = connection.contentLengthLong
                    var received = 0L
                    partFile.toFile().outputStream().buffered(BUFFER_SIZE).use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var n: Int
                        while (networkStream.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            received += n
                            onProgress(if (totalBytes > 0) received.toFloat() / totalBytes else 0f)
                        }
                    }
                    onProgress(1f)
                }
            }
        } finally {
            connection.disconnect()
        }

        // Atomic rename: only after the full write succeeds does the file
        // become "installed".
        Files.move(partFile, destFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun extractFromZip(
        stream: InputStream,
        entryPattern: String,
        destFile: Path,
        totalBytes: Long,
        onProgress: (Float) -> Unit,
    ) {
        val pattern = entryPattern.lowercase()
        var bytesRead = 0L
        var found = false

        ZipInputStream(stream.buffered(BUFFER_SIZE)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.lowercase()
                if (!entry.isDirectory && entryName.contains(pattern)) {
                    destFile.toFile().outputStream().buffered(BUFFER_SIZE).use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var n: Int
                        while (zip.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            bytesRead += n
                            onProgress(if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f)
                        }
                    }
                    found = true
                    break
                }
                bytesRead += entry.compressedSize.coerceAtLeast(0)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        onProgress(1f)

        if (!found) {
            Files.deleteIfExists(destFile)
            throw RuntimeException(
                "ZIP archive did not contain an entry matching '$entryPattern'. " +
                "The distribution format may have changed; update FontCatalog.kt."
            )
        }
    }

    /** Returns the .part file path for a font (useful for cleanup checks). */
    fun partFile(font: CatalogFont, fontsDir: Path): Path =
        fontsDir.resolve("${font.fileName}.part")

    /** Absolute path where the finished font file is stored. */
    fun installedFile(font: CatalogFont, fontsDir: Path): Path =
        fontsDir.resolve(font.fileName)
}

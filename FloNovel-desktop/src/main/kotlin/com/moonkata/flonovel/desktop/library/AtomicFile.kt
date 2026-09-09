package com.moonkata.flonovel.desktop.library

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

object AtomicFile {

    /**
     * Atomically writes [content] to [targetPath] using a temporary file in the same directory
     * and renaming it over the destination.
     *
     * If writing fails or is interrupted before rename, [targetPath] remains completely intact.
     */
    fun writeAtomic(targetPath: Path, content: String) {
        val parent = targetPath.toAbsolutePath().parent ?: Path.of(".")
        Files.createDirectories(parent)

        val prefix = targetPath.fileName.toString()
        val tempPath = Files.createTempFile(parent, prefix, ".tmp")

        try {
            Files.writeString(
                tempPath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )

            try {
                Files.move(
                    tempPath,
                    targetPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempPath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            try {
                Files.deleteIfExists(tempPath)
            } catch (_: IOException) {
                // Ignore cleanup failure for already-moved file
            }
        }
    }

    /**
     * Reads the entire content of [path] as UTF-8 string, or returns null if the file does not exist.
     */
    fun readIfExists(path: Path): String? {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return null
        }
        return Files.readString(path, StandardCharsets.UTF_8)
    }
}

package com.moonkata.flonovel.desktop.preprocess

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/**
 * CLI utility to batch-preprocess all `.txt` files under numeric subfolders (e.g., "1011", "1105")
 * in-place, without creating original backups in `.flonovel/original`.
 *
 * Usage:
 *   ./gradlew preprocessBatch -ProotDir="C:/Users/katal/Downloads/books"
 */
object BatchPreprocess {

    private val reNumericFolder = Regex("""^\d+$""")

    @JvmStatic
    fun main(args: Array<String>) {
        val rootPathStr = args.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: error("usage: BatchPreprocess <rootDir>")
        val rootDir = Paths.get(rootPathStr).toAbsolutePath().normalize()

        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            System.err.println("Directory does not exist or is not a directory: $rootDir")
            return
        }

        println("==================================================================")
        println(" FloNovel Batch Preprocessor (In-Place, No Backup)")
        println(" Target root   : $rootDir")
        println(" Folder filter : Numeric subfolders only (e.g. 1011)")
        println("==================================================================")

        val targetFolders = Files.list(rootDir).use { stream ->
            stream.filter { Files.isDirectory(it) && reNumericFolder.matches(it.fileName.toString()) }
                .sorted()
                .toList()
        }

        if (targetFolders.isEmpty()) {
            println("No numeric subfolders found directly under $rootDir.")
            return
        }

        println("Found ${targetFolders.size} numeric folder(s): ${targetFolders.joinToString { it.fileName.toString() }}")
        println()

        var totalFiles = 0
        var totalRenamed = 0
        var totalModified = 0
        var totalErrors = 0

        for (folder in targetFolders) {
            val txtFiles = Files.walk(folder).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.extension.equals("txt", ignoreCase = true) }
                    .sorted()
                    .toList()
            }

            if (txtFiles.isEmpty()) {
                println("[${folder.fileName}] No .txt files.")
                continue
            }

            println("[${folder.fileName}] Processing ${txtFiles.size} txt file(s)...")

            for (file in txtFiles) {
                totalFiles++
                val relPath = rootDir.relativize(file).toString()
                try {
                    val result = TextPreprocessor.preprocessFile(
                        filePath = file,
                        homeFolder = folder,
                        backupOriginal = false,
                    )
                    if (result.renamed) totalRenamed++
                    if (result.contentModified) totalModified++

                    val status = when {
                        result.renamed && result.contentModified -> "RENAMED & MODIFIED"
                        result.renamed -> "RENAMED"
                        result.contentModified -> "MODIFIED"
                        else -> "ALREADY PREPROCESSED"
                    }
                    val finalName = if (result.renamed) " -> ${result.finalPath.fileName}" else ""
                    println("  * $relPath$finalName [$status]")
                } catch (e: Exception) {
                    totalErrors++
                    System.err.println("  ! FAILED: $relPath - ${e.message}")
                }
            }
            println()
        }

        println("==================================================================")
        println(" Completed!")
        println(" Total txt files checked : $totalFiles")
        println(" Content normalized      : $totalModified")
        println(" Files renamed           : $totalRenamed")
        println(" Errors                  : $totalErrors")
        println("==================================================================")
    }
}


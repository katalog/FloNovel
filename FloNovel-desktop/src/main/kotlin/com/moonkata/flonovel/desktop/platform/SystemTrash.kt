package com.moonkata.flonovel.desktop.platform

import java.awt.Desktop
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object SystemTrash {
    private val isLinux: Boolean by lazy {
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    }

    /**
     * True when the OS exposes a recycle bin / trash through AWT (Windows, macOS)
     * or through Linux desktop standards (gio, trash-put, or FreeDesktop trash directory).
     * On headless or unsupported environments we refuse rather than silently deleting permanently.
     */
    val isSupported: Boolean
        get() = isAwtSupported || (isLinux && isLinuxTrashSupported)

    private val isAwtSupported: Boolean
        get() = runCatching {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
        }.getOrDefault(false)

    private val isLinuxTrashSupported: Boolean
        get() = isCommandAvailable("gio", "version") ||
            isCommandAvailable("trash-put", "--version") ||
            canAccessFreeDesktopTrash()

    /** Returns true only when the OS reports the file or folder was moved to the trash. */
    fun moveToTrash(path: Path): Boolean {
        if (!Files.exists(path)) return false

        // 1. Java AWT Desktop.moveToTrash (Windows, macOS, or AWT-supported Linux environments)
        if (isAwtSupported) {
            val awtResult = runCatching { Desktop.getDesktop().moveToTrash(path.toFile()) }.getOrDefault(false)
            if (awtResult) return true
        }

        // 2. Linux desktop integrations
        if (isLinux) {
            if (moveToTrashViaGio(path)) return true
            if (moveToTrashViaTrashPut(path)) return true
            if (moveToFreeDesktopTrash(path)) return true
        }

        return false
    }

    private fun isCommandAvailable(command: String, testArg: String): Boolean {
        return runCatching {
            val process = ProcessBuilder(command, testArg)
                .redirectErrorStream(true)
                .start()
            process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun moveToTrashViaGio(path: Path): Boolean {
        return runCatching {
            val process = ProcessBuilder("gio", "trash", path.toAbsolutePath().normalize().toString())
                .redirectErrorStream(true)
                .start()
            process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun moveToTrashViaTrashPut(path: Path): Boolean {
        return runCatching {
            val process = ProcessBuilder("trash-put", path.toAbsolutePath().normalize().toString())
                .redirectErrorStream(true)
                .start()
            process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun getFreeDesktopTrashDir(): Path? {
        val xdgDataHome = System.getenv("XDG_DATA_HOME")
        val baseDir = if (!xdgDataHome.isNullOrBlank()) {
            Path.of(xdgDataHome)
        } else {
            val home = System.getProperty("user.home").orEmpty()
            if (home.isBlank()) return null
            Path.of(home, ".local", "share")
        }
        return baseDir.resolve("Trash")
    }

    private fun canAccessFreeDesktopTrash(): Boolean {
        val trashDir = getFreeDesktopTrashDir() ?: return false
        return runCatching {
            val filesDir = trashDir.resolve("files")
            val infoDir = trashDir.resolve("info")
            Files.createDirectories(filesDir)
            Files.createDirectories(infoDir)
            Files.isWritable(filesDir) && Files.isWritable(infoDir)
        }.getOrDefault(false)
    }

    /**
     * Implements the FreeDesktop.org Trash Specification (v1.0) directly as fallback when
     * external tools (gio/trash-put) are unavailable.
     * Places the item in $XDG_DATA_HOME/Trash/files/ and creates a matching .trashinfo file
     * in $XDG_DATA_HOME/Trash/info/.
     */
    internal fun moveToFreeDesktopTrash(path: Path): Boolean {
        return runCatching {
            val trashDir = getFreeDesktopTrashDir() ?: return false
            val filesDir = trashDir.resolve("files")
            val infoDir = trashDir.resolve("info")
            Files.createDirectories(filesDir)
            Files.createDirectories(infoDir)

            val originalAbsPath = path.toAbsolutePath().normalize()
            val fileName = originalAbsPath.fileName.toString()

            // Resolve a collision-free target name in Trash/files
            var candidateName = fileName
            var target = filesDir.resolve(candidateName)
            var counter = 1
            val baseName = fileName.substringBeforeLast('.', fileName)
            val extension = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""

            while (Files.exists(target) || Files.exists(infoDir.resolve("$candidateName.trashinfo"))) {
                candidateName = "$baseName.$counter$extension"
                target = filesDir.resolve(candidateName)
                counter++
            }

            // Write metadata .trashinfo (URL escaping for '%' as per spec)
            val infoFile = infoDir.resolve("$candidateName.trashinfo")
            val encodedPath = originalAbsPath.toString().replace("%", "%25")
            val deletionDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
            val infoContent = "[Trash Info]\nPath=$encodedPath\nDeletionDate=$deletionDate\n"
            Files.writeString(infoFile, infoContent, StandardCharsets.UTF_8)

            // Move the file or directory
            try {
                Files.move(originalAbsPath, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                // If atomic move fails (e.g. across mount points), try standard move
                Files.move(originalAbsPath, target, StandardCopyOption.REPLACE_EXISTING)
            }

            true
        }.getOrDefault(false)
    }
}


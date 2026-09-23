package com.moonkata.flonovel.desktop.library

import com.moonkata.flonovel.desktop.platform.SystemTrash
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

enum class RemovalRefusal {
    NOT_FOUND,
    FOLDER_NOT_EMPTY,
    TRASH_UNSUPPORTED,
    TRASH_FAILED,
    MOVE_FOLDER_NOT_SET,
    MOVE_FOLDER_MISSING,
    MOVE_FOLDER_INSIDE_LIBRARY,
}

sealed class RemovalOutcome {
    data class Trashed(val path: Path) : RemovalOutcome()
    data class Moved(val from: Path, val to: Path) : RemovalOutcome()
    data class FolderRemoved(val path: Path) : RemovalOutcome()
    data class Refused(val reason: RemovalRefusal) : RemovalOutcome()
    data class Failed(val message: String) : RemovalOutcome()
}

/**
 * Removes a book file (to the trash, or out to a user-chosen folder) or an empty library folder.
 *
 * Only the file itself is touched. The library watcher notices it is gone and handles the
 * books.json record and the Dropbox deletion, exactly as if the user had removed it in Explorer.
 * The preprocessing backup under `.flonovel/original` is left alone on purpose, as a way back.
 */
object FileRemover {

    /**
     * @param trash moves a path to the OS trash and reports success; null when the OS has no
     *   trash. Injected so tests never touch the real recycle bin.
     */
    fun removeBook(
        file: Path,
        settings: DeleteSettings,
        homeFolder: Path,
        trash: ((Path) -> Boolean)? = if (SystemTrash.isSupported) SystemTrash::moveToTrash else null,
    ): RemovalOutcome {
        if (!Files.isRegularFile(file)) return RemovalOutcome.Refused(RemovalRefusal.NOT_FOUND)
        return when (settings.action) {
            DeleteAction.TRASH -> {
                if (trash == null) return RemovalOutcome.Refused(RemovalRefusal.TRASH_UNSUPPORTED)
                if (trash(file)) RemovalOutcome.Trashed(file)
                else RemovalOutcome.Refused(RemovalRefusal.TRASH_FAILED)
            }
            DeleteAction.MOVE -> {
                checkMoveFolder(settings.moveFolder, homeFolder)?.let { return RemovalOutcome.Refused(it) }
                moveToFolder(file, Path.of(settings.moveFolder))
            }
        }
    }

    /** Deletes [dir] only if it has no entries at all, hidden ones included. */
    fun removeEmptyFolder(dir: Path): RemovalOutcome {
        if (!Files.isDirectory(dir)) return RemovalOutcome.Refused(RemovalRefusal.NOT_FOUND)
        if (!isEmptyFolder(dir)) return RemovalOutcome.Refused(RemovalRefusal.FOLDER_NOT_EMPTY)
        return try {
            // Files.delete refuses a non-empty directory, so a file that appears between the
            // check above and this call cannot be lost.
            Files.delete(dir)
            RemovalOutcome.FolderRemoved(dir)
        } catch (_: DirectoryNotEmptyException) {
            RemovalOutcome.Refused(RemovalRefusal.FOLDER_NOT_EMPTY)
        } catch (e: IOException) {
            RemovalOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun isEmptyFolder(dir: Path): Boolean =
        Files.list(dir).use { !it.findAny().isPresent }

    /**
     * Returns why [moveFolder] cannot be used as the move destination, or null if it can.
     *
     * A folder inside the library is rejected: the watcher would pick the moved file up as a new
     * book and upload it again, so the Delete key would appear to do nothing.
     */
    fun checkMoveFolder(moveFolder: String, homeFolder: Path?): RemovalRefusal? {
        if (moveFolder.isBlank()) return RemovalRefusal.MOVE_FOLDER_NOT_SET
        val target = runCatching { Path.of(moveFolder) }.getOrNull()
            ?: return RemovalRefusal.MOVE_FOLDER_MISSING
        if (!Files.isDirectory(target)) return RemovalRefusal.MOVE_FOLDER_MISSING
        if (homeFolder != null && Files.isDirectory(homeFolder)) {
            if (canonical(target).startsWith(canonical(homeFolder))) {
                return RemovalRefusal.MOVE_FOLDER_INSIDE_LIBRARY
            }
        }
        return null
    }

    /** `name.txt` -> `name.txt`, or `name_1.txt`, `name_2.txt`, ... if taken. */
    fun uniqueDestination(dir: Path, fileName: String): Path {
        val first = dir.resolve(fileName)
        if (!Files.exists(first)) return first
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = dir.resolve("${base}_$n$ext")
            if (!Files.exists(candidate)) return candidate
            n++
        }
    }

    private fun moveToFolder(file: Path, targetDir: Path): RemovalOutcome {
        // Never pass REPLACE_EXISTING: if another file takes the name between uniqueDestination
        // and the move, pick the next free name instead of overwriting it.
        repeat(MOVE_ATTEMPTS) {
            val dest = uniqueDestination(targetDir, file.fileName.toString())
            try {
                Files.move(file, dest)
                return RemovalOutcome.Moved(file, dest)
            } catch (_: FileAlreadyExistsException) {
                // Retry with a fresh name.
            } catch (e: IOException) {
                return RemovalOutcome.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        return RemovalOutcome.Failed("No free file name in $targetDir")
    }

    private fun canonical(path: Path): Path =
        runCatching { path.toRealPath() }.getOrElse { path.toAbsolutePath().normalize() }

    private const val MOVE_ATTEMPTS = 5
}

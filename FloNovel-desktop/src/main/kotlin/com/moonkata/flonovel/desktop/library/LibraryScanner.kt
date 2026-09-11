package com.moonkata.flonovel.desktop.library

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Which of the three visually-distinguished states a book's reading progress falls into. */
enum class ReadingProgressState { UNREAD, IN_PROGRESS, COMPLETED }

/**
 * Represents a book item discovered in the library directory.
 */
data class LibraryBookItem(
    val file: File,
    val relativePath: String,
    val key: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val bookRecord: BookRecord?,
) {
    /** 0.0 when never opened (or not yet registered at all) through 1.0 when finished. */
    val progressFraction: Double
        get() = bookRecord?.progress ?: 0.0

    /** The truncated integer actually shown to the user (e.g. 14, not 14.28). */
    private val progressPercentInt: Int
        get() = (progressFraction * 100).toInt()

    /**
     * Integer percentage formatted (e.g. "14%"), matching Android library list specification.
     */
    val formattedProgress: String
        get() = "$progressPercentInt%"

    /**
     * Three states, not two: a book is registered (and so has a non-null [bookRecord]) the moment
     * it's scanned into the library, well before anyone opens it — so "bookRecord present" was
     * never actually distinguishing "read" from "unread" in practice, only "completed" was missing
     * a color of its own. Compared against [progressPercentInt] rather than [progressFraction]
     * directly so the color always agrees with the number on screen — e.g. 99.9% displays as "99%"
     * and must not be colored as complete.
     */
    val progressState: ReadingProgressState
        get() = when {
            progressPercentInt >= 100 -> ReadingProgressState.COMPLETED
            progressPercentInt > 0 -> ReadingProgressState.IN_PROGRESS
            else -> ReadingProgressState.UNREAD
        }

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            return if (kb < 1024.0) {
                "%.1f KB".format(kb)
            } else {
                "%.1f MB".format(kb / 1024.0)
            }
        }
}

/**
 * Represents a subfolder discovered in the current browsed directory.
 */
data class LibraryFolderEntry(
    val file: File,
    val relativePath: String,
    val name: String,
)

/**
 * Result of scanning the current directory in hierarchical folder view.
 */
data class FolderContent(
    val currentRelativePath: String,
    val subfolders: List<LibraryFolderEntry>,
    val books: List<LibraryBookItem>,
)

enum class LibrarySortOption {
    NAME,
    DATE,
    SIZE,
    RECENT,
}

object LibraryScanner {

    /**
     * Scans only immediate children of [currentRelativePath] under [homeFolder]:
     * - Returns [FolderContent] containing immediate subfolders and immediate .txt books.
     * - Strictly excludes files and folders starting with "." (e.g. .stfolder, .git, .idea).
     * - Retains sorting and reading progress for .txt files in the current folder.
     */
    fun scanDirectory(homeFolder: Path, currentRelativePath: String, booksData: BooksData): FolderContent {
        if (!Files.exists(homeFolder) || !Files.isDirectory(homeFolder)) {
            return FolderContent("", emptyList(), emptyList())
        }

        val normalizedHome = homeFolder.toAbsolutePath().normalize()
        val targetDir = if (currentRelativePath.isBlank()) {
            normalizedHome
        } else {
            normalizedHome.resolve(currentRelativePath).normalize()
        }

        if (!targetDir.startsWith(normalizedHome) || !Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return FolderContent("", emptyList(), emptyList())
        }

        val recordsByKey = booksData.books.associateBy { it.key }
        val subfolders = mutableListOf<LibraryFolderEntry>()
        val books = mutableListOf<LibraryBookItem>()

        val children = targetDir.toFile().listFiles() ?: emptyArray()
        for (child in children) {
            if (child.name.startsWith(".")) continue

            val childPath = child.toPath().toAbsolutePath().normalize()
            val relative = normalizedHome.relativize(childPath).toString()

            if (child.isDirectory) {
                subfolders += LibraryFolderEntry(
                    file = child,
                    relativePath = relative,
                    name = child.name,
                )
            } else if (child.isFile && child.name.endsWith(".txt", ignoreCase = true)) {
                val key = RelativePath.normalize(relative)
                val record = recordsByKey[key]
                books += LibraryBookItem(
                    file = child,
                    relativePath = relative,
                    key = key,
                    displayName = child.nameWithoutExtension,
                    sizeBytes = child.length(),
                    lastModified = child.lastModified(),
                    bookRecord = record,
                )
            }
        }

        val relPath = if (targetDir == normalizedHome) "" else normalizedHome.relativize(targetDir).toString()
        return FolderContent(
            currentRelativePath = relPath,
            subfolders = subfolders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
            books = books,
        )
    }

    /**
     * Recursively scans [homeFolder] for all .txt files:
     * - Strictly excludes files and directories starting with "." (e.g. .stfolder, .git, .idea).
     * - Returns [LibraryBookItem] mapped with existing [booksData].
     */
    fun scan(homeFolder: Path, booksData: BooksData): List<LibraryBookItem> {
        if (!Files.exists(homeFolder) || !Files.isDirectory(homeFolder)) {
            return emptyList()
        }

        val items = mutableListOf<LibraryBookItem>()
        val recordsByKey = booksData.books.associateBy { it.key }

        fun walk(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                // Strictly exclude files and directories starting with "."
                if (child.name.startsWith(".")) continue

                if (child.isDirectory) {
                    walk(child)
                } else if (child.isFile && child.name.endsWith(".txt", ignoreCase = true)) {
                    val relative = homeFolder.relativize(child.toPath()).toString()
                    val key = RelativePath.normalize(relative)
                    val record = recordsByKey[key]
                    items += LibraryBookItem(
                        file = child,
                        relativePath = relative,
                        key = key,
                        displayName = child.nameWithoutExtension,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified(),
                        bookRecord = record,
                    )
                }
            }
        }

        walk(homeFolder.toFile())
        return items
    }

    /**
     * Sorts the library items according to [sortOption].
     */
    fun sort(items: List<LibraryBookItem>, sortOption: LibrarySortOption): List<LibraryBookItem> {
        return when (sortOption) {
            LibrarySortOption.NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
            LibrarySortOption.DATE -> items.sortedByDescending { it.lastModified }
            LibrarySortOption.SIZE -> items.sortedByDescending { it.sizeBytes }
            LibrarySortOption.RECENT -> items.sortedByDescending { it.bookRecord?.lastOpenedAt ?: 0L }
        }
    }
}

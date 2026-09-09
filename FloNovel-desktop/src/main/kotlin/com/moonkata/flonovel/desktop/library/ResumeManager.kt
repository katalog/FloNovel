package com.moonkata.flonovel.desktop.library

import com.moonkata.flonovel.desktop.text.LoadedText
import com.moonkata.flonovel.desktop.text.TextLoader
import java.nio.file.Files
import java.nio.file.Path

data class ResumeTarget(
    val book: BookRecord,
    val filePath: Path,
    val loadedText: LoadedText,
    val clampedAnchor: Int,
)

object ResumeManager {

    /**
     * Finds and verifies the book to resume on application launch.
     *
     * Rules:
     * 1. Prioritizes [Settings.lastOpenedBookKey], falling back to the book with the latest [BookRecord.lastOpenedAt].
     * 2. CRITICAL SAFETY: Verifies the file actually exists and is readable before presenting it as a candidate.
     *    If the file has been deleted or cannot be opened, returns null without crashing (regression test).
     * 3. Clamps [BookRecord.anchor] via coerceIn(0, text.length) in case the file was edited or truncated.
     */
    fun findResumeTarget(
        homeFolder: Path,
        settings: Settings,
        booksData: BooksData,
    ): ResumeTarget? {
        val candidateBook = findCandidateBook(settings, booksData) ?: return null

        val filePath = homeFolder.resolve(candidateBook.path)

        // Safety check before opening: verify file exists and is readable (Rule 3)
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            return null
        }

        val loaded = try {
            TextLoader.load(filePath)
        } catch (_: Exception) {
            // Any IO or decoding error: fail gracefully without crashing
            return null
        }

        // Clamp anchor to decoded text length (Rule 4)
        val safeAnchor = candidateBook.anchor.coerceIn(0, loaded.text.length)

        return ResumeTarget(
            book = candidateBook,
            filePath = filePath,
            loadedText = loaded,
            clampedAnchor = safeAnchor,
        )
    }

    private fun findCandidateBook(settings: Settings, booksData: BooksData): BookRecord? {
        val lastKey = settings.lastOpenedBookKey
        if (lastKey != null) {
            val matched = booksData.books.firstOrNull { it.key == lastKey }
            if (matched != null) return matched
        }

        // Fallback: book with the latest lastOpenedAt
        return booksData.books
            .filter { it.lastOpenedAt != null }
            .maxByOrNull { it.lastOpenedAt!! }
    }
}

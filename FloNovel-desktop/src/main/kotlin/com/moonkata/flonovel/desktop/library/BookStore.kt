package com.moonkata.flonovel.desktop.library

import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BookStore(
    val filePath: Path,
    val debounceMs: Long = 500L,
) : AutoCloseable {

    private val lock = Any()
    private var cachedData: BooksData = loadInitial()

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "BookStore-Debouncer").apply { isDaemon = true }
    }
    private var pendingTask: ScheduledFuture<*>? = null
    private val dirty = AtomicBoolean(false)

    private fun loadInitial(): BooksData {
        val content = AtomicFile.readIfExists(filePath) ?: return BooksData()
        return try {
            BooksData.fromJsonString(content)
        } catch (_: Exception) {
            // Corrupted JSON: start empty as specified in 07-ERROR-HANDLING.md §6
            BooksData()
        }
    }

    fun load(): BooksData {
        synchronized(lock) {
            return cachedData
        }
    }

    fun save(data: BooksData) {
        synchronized(lock) {
            cancelPendingLocked()
            cachedData = data
            AtomicFile.writeAtomic(filePath, data.toJsonString())
            dirty.set(false)
        }
    }

    fun findByKey(key: String): BookRecord? {
        synchronized(lock) {
            return cachedData.books.firstOrNull { it.key == key }
        }
    }

    fun findByPath(path: String): BookRecord? = findByKey(RelativePath.normalize(path))

    fun addOrUpdate(book: BookRecord) {
        synchronized(lock) {
            val existingIndex = cachedData.books.indexOfFirst { it.key == book.key }
            val updatedList = cachedData.books.toMutableList()
            if (existingIndex >= 0) {
                updatedList[existingIndex] = book
            } else {
                updatedList.add(book)
            }
            save(cachedData.copy(books = updatedList))
        }
    }

    /**
     * Updates reading position [anchor] for [bookKey].
     *
     * Debounces writes by [debounceMs] (default 500ms). When [flush] or [close]
     * is called, any pending position write is immediately committed to disk.
     */
    fun updateReadingPosition(
        bookKey: String,
        anchor: Int,
        totalCharCount: Int,
        debounce: Boolean = true,
    ) {
        synchronized(lock) {
            val existingIndex = cachedData.books.indexOfFirst { it.key == bookKey }
            if (existingIndex < 0) return

            val old = cachedData.books[existingIndex]
            val safeAnchor = anchor.coerceIn(0, if (totalCharCount > 0) totalCharCount else old.totalCharCount)
            val progress = if (totalCharCount > 0) {
                (safeAnchor.toDouble() / totalCharCount).coerceIn(0.0, 1.0)
            } else {
                old.progress
            }

            val updatedBook = old.copy(
                anchor = safeAnchor,
                progress = progress,
                lastOpenedAt = System.currentTimeMillis(),
            )

            val updatedList = cachedData.books.toMutableList()
            updatedList[existingIndex] = updatedBook
            cachedData = cachedData.copy(books = updatedList)
            dirty.set(true)

            if (!debounce || debounceMs <= 0) {
                flushLocked()
            } else {
                pendingTask?.cancel(false)
                pendingTask = executor.schedule({
                    flush()
                }, debounceMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    /**
     * Immediately and synchronously commits any pending debounced position writes to disk.
     */
    fun flush() {
        synchronized(lock) {
            flushLocked()
        }
    }

    private fun flushLocked() {
        if (!dirty.get()) return
        cancelPendingLocked()
        AtomicFile.writeAtomic(filePath, cachedData.toJsonString())
        dirty.set(false)
    }

    private fun cancelPendingLocked() {
        pendingTask?.cancel(false)
        pendingTask = null
    }

    /**
     * Converts any legacy absolute file paths in [cachedData] to relative paths
     * based on [homeFolder], ensuring breadcrumbs and navigation work consistently.
     */
    fun sanitizePaths(homeFolder: Path): BooksData {
        synchronized(lock) {
            val normalizedHome = homeFolder.toAbsolutePath().normalize()
            var modified = false
            val updatedList = cachedData.books.map { record ->
                val p = runCatching { Path.of(record.path) }.getOrNull()
                if (p != null && p.isAbsolute) {
                    val rel = if (p.startsWith(normalizedHome)) {
                        normalizedHome.relativize(p).toString().replace('\\', '/')
                    } else {
                        record.key
                    }
                    if (rel != record.path) {
                        modified = true
                        record.copy(path = rel)
                    } else record
                } else record
            }
            if (modified) {
                cachedData = cachedData.copy(books = updatedList)
                dirty.set(true)
                flushLocked()
            }
            return cachedData
        }
    }

    override fun close() {
        flush()
        executor.shutdown()
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            executor.shutdownNow()
        }
    }
}

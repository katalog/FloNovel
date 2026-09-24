package com.moonkata.flonovel.android.data.preprocess

import com.moonkata.flonovel.android.data.sync.FakeLibraryFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPreprocessorTest {

    private val library = FakeLibraryFiles()
    private val preprocessor = LibraryPreprocessor(library)
    private val raw = "  제1화 시작\n본문\n본문\n"
    private val processed = TextPreprocessor.normalizeContent(raw)

    private fun run(rel: String) = runBlocking { preprocessor.preprocess(rel) }

    @Test
    fun rawBook_isReplaced_withOriginalBackedUp() {
        library.put("A/Book.txt", raw)

        val result = run("A/Book.txt")

        assertEquals(LibraryPreprocessor.Result.Processed("A/Book.txt", "A/Book.txt", processed.length), result)
        assertEquals(processed, library.content("A/Book.txt"))
        assertEquals(raw, library.content(".flonovel/original/A/Book.txt"))
        assertFalse("no temporary file left", library.paths().any { it.endsWith(LibraryPreprocessor.TEMP_SUFFIX) })
    }

    @Test
    fun alreadyPreprocessed_isNotTouched() {
        library.put("Book.txt", processed)
        val before = library.stat("Book.txt")

        val result = run("Book.txt")

        assertEquals(LibraryPreprocessor.Result.Unchanged("Book.txt"), result)
        // Same mtime: rewriting identical bytes would look like an edit to sync.
        assertEquals(before, library.stat("Book.txt"))
        assertNull(library.content(".flonovel/original/Book.txt"))
    }

    @Test
    fun nameWithHanja_isCleaned_andCollisionsGetASuffix() {
        library.put("홍길동전.txt", "다른 책")
        library.put("홍길동전 洪吉童傳.txt", raw)

        val result = run("홍길동전 洪吉童傳.txt") as LibraryPreprocessor.Result.Processed

        assertEquals("홍길동전_1.txt", result.to)
        assertEquals(processed, library.content("홍길동전_1.txt"))
        assertEquals("다른 책", library.content("홍길동전.txt"))
        assertNull(library.content("홍길동전 洪吉童傳.txt"))
    }

    @Test
    fun backup_isWrittenOnce() {
        library.put(".flonovel/original/Book.txt", "the very first original")
        library.put("Book.txt", raw)

        run("Book.txt")

        assertEquals("the very first original", library.content(".flonovel/original/Book.txt"))
    }

    @Test
    fun unreadable_fails_andLeavesTheFileAlone() {
        library.put("Book.txt", raw)
        library.unreadable += "Book.txt"

        assertEquals(LibraryPreprocessor.Result.Failed("Book.txt"), run("Book.txt"))
        assertEquals(raw, library.content("Book.txt"))
    }

    @Test
    fun recover_originalGone_temporaryBecomesTheBook() {
        // Cut short after deleting the original, before the rename.
        library.put("A/.홍길동전 洪吉童傳.txt${LibraryPreprocessor.TEMP_SUFFIX}", processed)

        val finished = preprocessor.recover()

        assertEquals(listOf("A/홍길동전.txt"), finished.map { it.to })
        assertEquals(processed, library.content("A/홍길동전.txt"))
    }

    @Test
    fun recover_originalStillThere_discardsTheTemporary() {
        // Cut short before the original was deleted; it is simply preprocessed again later.
        library.put("Book.txt", raw)
        library.put(".Book.txt${LibraryPreprocessor.TEMP_SUFFIX}", processed.take(3))

        assertTrue(preprocessor.recover().isEmpty())
        assertEquals(setOf("Book.txt"), library.paths())
    }
}

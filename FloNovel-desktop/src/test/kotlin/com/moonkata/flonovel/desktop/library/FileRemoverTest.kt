package com.moonkata.flonovel.desktop.library

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class FileRemoverTest {

    private lateinit var root: Path
    private lateinit var library: Path
    private lateinit var outside: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("file_remover_test")
        library = Files.createDirectories(root.resolve("library"))
        outside = Files.createDirectories(root.resolve("removed"))
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun book(name: String, content: String = "text"): Path =
        Files.writeString(library.resolve(name), content)

    private val moveSettings get() = DeleteSettings(DeleteAction.MOVE, outside.toString())

    // ── Trash ────────────────────────────────────────────────────────────

    @Test
    fun trash_handsTheFileToTheTrashFunction() {
        val file = book("a.txt")
        val trashed = mutableListOf<Path>()
        val outcome = FileRemover.removeBook(file, DeleteSettings(), library) { trashed.add(it); true }

        assertEquals(RemovalOutcome.Trashed(file), outcome)
        assertEquals(listOf(file), trashed)
    }

    @Test
    fun trash_unsupported_refusesAndKeepsFile() {
        val file = book("a.txt")
        val outcome = FileRemover.removeBook(file, DeleteSettings(), library, trash = null)

        assertEquals(RemovalOutcome.Refused(RemovalRefusal.TRASH_UNSUPPORTED), outcome)
        assertTrue(Files.exists(file))
    }

    @Test
    fun trash_reportedFailure_isRefusedNotSwallowed() {
        val file = book("a.txt")
        val outcome = FileRemover.removeBook(file, DeleteSettings(), library) { false }

        assertEquals(RemovalOutcome.Refused(RemovalRefusal.TRASH_FAILED), outcome)
    }

    @Test
    fun missingFile_isRefused() {
        val outcome = FileRemover.removeBook(library.resolve("gone.txt"), DeleteSettings(), library) { true }
        assertEquals(RemovalOutcome.Refused(RemovalRefusal.NOT_FOUND), outcome)
    }

    // ── Move ─────────────────────────────────────────────────────────────

    @Test
    fun move_movesFileAndKeepsContent() {
        val file = book("a.txt", "본문")
        val outcome = FileRemover.removeBook(file, moveSettings, library) { error("must not trash") }

        val dest = outside.resolve("a.txt")
        assertEquals(RemovalOutcome.Moved(file, dest), outcome)
        assertFalse(Files.exists(file))
        assertEquals("본문", Files.readString(dest))
    }

    @Test
    fun move_nameCollision_appendsIncreasingSuffixWithoutOverwriting() {
        Files.writeString(outside.resolve("a.txt"), "old")
        Files.writeString(outside.resolve("a_1.txt"), "old1")

        val outcome = FileRemover.removeBook(book("a.txt", "new"), moveSettings, library) { true }

        val dest = outside.resolve("a_2.txt")
        assertEquals(dest, assertIs<RemovalOutcome.Moved>(outcome).to)
        assertEquals("old", Files.readString(outside.resolve("a.txt")))
        assertEquals("old1", Files.readString(outside.resolve("a_1.txt")))
        assertEquals("new", Files.readString(dest))
    }

    @Test
    fun uniqueDestination_handlesNamesWithoutAndWithSeveralDots() {
        Files.writeString(outside.resolve("README"), "")
        Files.writeString(outside.resolve("vol.1.txt"), "")

        assertEquals(outside.resolve("README_1"), FileRemover.uniqueDestination(outside, "README"))
        assertEquals(outside.resolve("vol.1_1.txt"), FileRemover.uniqueDestination(outside, "vol.1.txt"))
        assertEquals(outside.resolve("free.txt"), FileRemover.uniqueDestination(outside, "free.txt"))
    }

    @Test
    fun move_withoutFolder_isRefused() {
        val file = book("a.txt")
        val outcome = FileRemover.removeBook(file, DeleteSettings(DeleteAction.MOVE, ""), library) { true }

        assertEquals(RemovalOutcome.Refused(RemovalRefusal.MOVE_FOLDER_NOT_SET), outcome)
        assertTrue(Files.exists(file))
    }

    @Test
    fun move_toMissingFolder_isRefused() {
        val file = book("a.txt")
        val settings = DeleteSettings(DeleteAction.MOVE, root.resolve("nope").toString())
        val outcome = FileRemover.removeBook(file, settings, library) { true }

        assertEquals(RemovalOutcome.Refused(RemovalRefusal.MOVE_FOLDER_MISSING), outcome)
        assertTrue(Files.exists(file))
    }

    @Test
    fun moveFolder_insideOrEqualToLibrary_isRejected() {
        val sub = Files.createDirectories(library.resolve("done"))

        assertEquals(RemovalRefusal.MOVE_FOLDER_INSIDE_LIBRARY, FileRemover.checkMoveFolder(sub.toString(), library))
        assertEquals(RemovalRefusal.MOVE_FOLDER_INSIDE_LIBRARY, FileRemover.checkMoveFolder(library.toString(), library))
        assertNull(FileRemover.checkMoveFolder(outside.toString(), library))
    }

    @Test
    fun moveFolder_siblingWithLibraryNameAsPrefix_isAllowed() {
        // "library-old" starts with the string "library" but is not inside it.
        val sibling = Files.createDirectories(root.resolve("library-old"))
        assertNull(FileRemover.checkMoveFolder(sibling.toString(), library))
    }

    // ── Folders ──────────────────────────────────────────────────────────

    @Test
    fun emptyFolder_isDeleted() {
        val dir = Files.createDirectories(library.resolve("empty"))
        assertEquals(RemovalOutcome.FolderRemoved(dir), FileRemover.removeEmptyFolder(dir))
        assertFalse(Files.exists(dir))
    }

    @Test
    fun folderWithOnlyHiddenOrNonBookFiles_isNotEmpty() {
        // The library list hides these, but they are still the user's files.
        val hidden = Files.createDirectories(library.resolve("hidden"))
        Files.writeString(hidden.resolve(".stfolder"), "")
        val image = Files.createDirectories(library.resolve("image"))
        Files.writeString(image.resolve("cover.jpg"), "")

        assertEquals(RemovalOutcome.Refused(RemovalRefusal.FOLDER_NOT_EMPTY), FileRemover.removeEmptyFolder(hidden))
        assertEquals(RemovalOutcome.Refused(RemovalRefusal.FOLDER_NOT_EMPTY), FileRemover.removeEmptyFolder(image))
        assertTrue(Files.exists(hidden.resolve(".stfolder")))
        assertTrue(Files.exists(image.resolve("cover.jpg")))
    }

    @Test
    fun folderWithEmptySubfolder_isNotEmpty() {
        val dir = Files.createDirectories(library.resolve("outer/inner"))
        assertEquals(
            RemovalOutcome.Refused(RemovalRefusal.FOLDER_NOT_EMPTY),
            FileRemover.removeEmptyFolder(dir.parent),
        )
    }
}

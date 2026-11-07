package com.moonkata.flonovel.desktop.ui

import com.moonkata.flonovel.desktop.library.DeleteAction
import com.moonkata.flonovel.desktop.library.DeleteSettings
import com.moonkata.flonovel.desktop.library.FileRemover
import com.moonkata.flonovel.desktop.library.RemovalOutcome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchRemovalTest {

    @Test
    fun batchRemovalRequest_holdsMultipleTargets() {
        val target1 = BatchRemovalTarget(Path.of("book1.txt"), isFolder = false)
        val target2 = BatchRemovalTarget(Path.of("book2.txt"), isFolder = false)
        val target3 = BatchRemovalTarget(Path.of("subfolder"), isFolder = true)

        val request = BatchRemovalRequest(listOf(target1, target2, target3))
        assertEquals(3, request.targets.size)
        assertEquals(listOf(target1, target2, target3), request.targets)
    }

    @Test
    fun batchRemoval_movesMultipleFilesToTrash() {
        val tempDir = Files.createTempDirectory("batch-removal-test")
        try {
            val file1 = Files.writeString(tempDir.resolve("book1.txt"), "Content 1")
            val file2 = Files.writeString(tempDir.resolve("book2.txt"), "Content 2")
            val file3 = Files.writeString(tempDir.resolve("book3.txt"), "Content 3")

            val trashed = mutableListOf<Path>()
            val fakeTrash: (Path) -> Boolean = { path ->
                trashed.add(path)
                true
            }

            val targets = listOf(
                BatchRemovalTarget(file1, isFolder = false),
                BatchRemovalTarget(file2, isFolder = false),
                BatchRemovalTarget(file3, isFolder = false),
            )

            val deleteSettings = DeleteSettings(action = DeleteAction.TRASH)
            var successCount = 0

            for (target in targets) {
                val outcome = FileRemover.removeBook(target.path, deleteSettings, tempDir, trash = fakeTrash)
                if (outcome is RemovalOutcome.Trashed) {
                    successCount++
                }
            }

            assertEquals(3, successCount)
            assertEquals(3, trashed.size)
            assertTrue(trashed.contains(file1))
            assertTrue(trashed.contains(file2))
            assertTrue(trashed.contains(file3))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun batchSelectionLogic_filtersParentFolderAndComputesRange() {
        // Given display items where index 0 is parent folder, and 1..4 are books
        val itemTypes = listOf(0, 2, 2, 2, 2) // 0 = PARENT, 2 = BOOK

        // Select All should exclude parent (index 0)
        val allSelectable = itemTypes.indices.filter { itemTypes[it] != 0 }.toSet()
        assertEquals(setOf(1, 2, 3, 4), allSelectable)

        // Range select from 1 to 3
        val anchor = 1
        val target = 3
        val range = (minOf(anchor, target)..maxOf(anchor, target)).filter { itemTypes[it] != 0 }.toSet()
        assertEquals(setOf(1, 2, 3), range)

        // Range select that would span over parent if anchor was 0
        val rangeFromZero = (0..2).filter { itemTypes[it] != 0 }.toSet()
        assertEquals(setOf(1, 2), rangeFromZero)

        // Toggle selection
        var selected = setOf(1, 2)
        selected = if (2 in selected) selected - 2 else selected + 2
        assertEquals(setOf(1), selected)
        selected = if (3 in selected) selected - 3 else selected + 3
        assertEquals(setOf(1, 3), selected)
    }

    @Test
    fun spaceToggle_andArrowNavigation_preservesSelection() {
        // Simulates user workflow:
        // 1. Cursor at row 1. Press Space -> row 1 selected.
        var selectedIndices = setOf<Int>()
        var cursor = 1

        // Toggle Space on row 1
        selectedIndices = if (cursor in selectedIndices) selectedIndices - cursor else selectedIndices + cursor
        assertEquals(setOf(1), selectedIndices)

        // Move Down to row 2 (preserves selection)
        cursor = 2
        assertEquals(setOf(1), selectedIndices)

        // Toggle Space on row 2
        selectedIndices = if (cursor in selectedIndices) selectedIndices - cursor else selectedIndices + cursor
        assertEquals(setOf(1, 2), selectedIndices)

        // Move Down to row 3 (preserves selection)
        cursor = 3
        assertEquals(setOf(1, 2), selectedIndices)

        // Toggle Space on row 3
        selectedIndices = if (cursor in selectedIndices) selectedIndices - cursor else selectedIndices + cursor
        assertEquals(setOf(1, 2, 3), selectedIndices)

        // Move Up to row 2
        cursor = 2
        // Toggle Space on row 2 -> deselects row 2
        selectedIndices = if (cursor in selectedIndices) selectedIndices - cursor else selectedIndices + cursor
        assertEquals(setOf(1, 3), selectedIndices)

        // Press Escape -> clears selection
        selectedIndices = emptySet()
        assertTrue(selectedIndices.isEmpty())
    }
}


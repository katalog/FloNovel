package com.moonkata.flonovel.desktop.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderPickerTest {

    private lateinit var parent: File
    private lateinit var books: File

    @BeforeTest
    fun setUp() {
        parent = Files.createTempDirectory("flonovel_picker_test").toFile()
        books = File(parent, "Books").apply { mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        parent.deleteRecursively()
    }

    @Test
    fun collapsesGtkDoubledPathToTheEnteredFolder() {
        val picked = resolvePickedFolder(File(books, "Books"), currentDirectory = books)
        assertEquals(books, picked)
    }

    @Test
    fun keepsARealNestedFolderWithTheSameName() {
        val nested = File(books, "Books").apply { mkdirs() }
        val picked = resolvePickedFolder(nested, currentDirectory = books)
        assertEquals(nested, picked)
    }

    @Test
    fun keepsAnExistingSelectionUnchanged() {
        val picked = resolvePickedFolder(books, currentDirectory = parent)
        assertEquals(books, picked)
    }

    @Test
    fun keepsAMissingFolderWithADifferentName() {
        val typed = File(books, "NewFolder")
        val picked = resolvePickedFolder(typed, currentDirectory = books)
        assertEquals(typed, picked)
    }
}

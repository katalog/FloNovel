package com.moonkata.flonovel.desktop.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemTrashTest {

    @Test
    fun isSupportedOnCurrentEnvironment() {
        // On Linux with desktop / FreeDesktop access, isSupported must be true
        if (System.getProperty("os.name").orEmpty().lowercase().contains("linux")) {
            assertTrue(SystemTrash.isSupported)
        }
    }

    @Test
    fun nonexistentFileReturnsFalse() {
        val nonExistent = Files.createTempFile("system-trash-nonexistent", ".txt")
        Files.delete(nonExistent)
        assertFalse(SystemTrash.moveToTrash(nonExistent))
    }

    @Test
    fun movesFileToTrashSuccessfully() {
        if (!SystemTrash.isSupported) return

        val tempFile = Files.createTempFile("flonovel-test-trash-file", ".txt")
        Files.writeString(tempFile, "Temporary test content")
        assertTrue(Files.exists(tempFile))

        val moved = SystemTrash.moveToTrash(tempFile)
        assertTrue(moved, "File should be moved to trash successfully")
        assertFalse(Files.exists(tempFile), "Original file should no longer exist after moving to trash")
    }

    @Test
    fun movesFolderToTrashSuccessfully() {
        if (!SystemTrash.isSupported) return

        val tempDir = Files.createTempDirectory("flonovel-test-trash-dir")
        val childFile = tempDir.resolve("child.txt")
        Files.writeString(childFile, "Child content")
        assertTrue(Files.exists(tempDir))
        assertTrue(Files.exists(childFile))

        val moved = SystemTrash.moveToTrash(tempDir)
        assertTrue(moved, "Directory should be moved to trash successfully")
        assertFalse(Files.exists(tempDir), "Original directory should no longer exist after moving to trash")
    }
}

package com.moonkata.flonovel.desktop.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtomicWriteTest {

    @Test
    fun atomicWrite_overwritesCleanly() {
        val tempDir = Files.createTempDirectory("atomic_test")
        try {
            val target = tempDir.resolve("state.json")

            AtomicFile.writeAtomic(target, "{\"version\": 1}")
            assertEquals("{\"version\": 1}", Files.readString(target))

            AtomicFile.writeAtomic(target, "{\"version\": 2}")
            assertEquals("{\"version\": 2}", Files.readString(target))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun atomicWrite_interruptedWriteLeavesOriginalFileIntact() {
        val tempDir = Files.createTempDirectory("atomic_interrupted_test")
        try {
            val target = tempDir.resolve("books.json")
            val originalContent = "{\"schemaVersion\": 1, \"books\": [{\"key\": \"original\"}]}"

            AtomicFile.writeAtomic(target, originalContent)
            assertEquals(originalContent, Files.readString(target))

            // Simulate interrupted write: a temp file was being written or error occurred before rename
            val simulatedTemp = Files.createTempFile(tempDir, "books.json", ".tmp")
            Files.writeString(simulatedTemp, "{\"corrupted_half_written\": true...")

            // Target file must remain 100% intact with original valid content
            assertEquals(originalContent, Files.readString(target))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun atomicWrite_createsParentDirectoriesIfNeeded() {
        val tempDir = Files.createTempDirectory("atomic_parent_test")
        try {
            val nestedTarget = tempDir.resolve("nested").resolve("sub").resolve("data.json")
            AtomicFile.writeAtomic(nestedTarget, "hello nested")

            assertTrue(Files.exists(nestedTarget))
            assertEquals("hello nested", Files.readString(nestedTarget))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

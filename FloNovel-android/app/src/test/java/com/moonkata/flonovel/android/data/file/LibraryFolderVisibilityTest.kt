package com.moonkata.flonovel.android.data.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderVisibilityTest {
    @Test
    fun internalFolderIsHiddenRegardlessOfCase() {
        assertFalse(isVisibleLibraryFolder(".flonovel"))
        assertFalse(isVisibleLibraryFolder(".FloNovel"))
    }

    @Test
    fun ordinaryAndOtherHiddenFoldersRemainVisible() {
        assertTrue(isVisibleLibraryFolder("Novels"))
        assertTrue(isVisibleLibraryFolder(".archive"))
        assertTrue(isVisibleLibraryFolder(".flonovel-backup"))
    }
}

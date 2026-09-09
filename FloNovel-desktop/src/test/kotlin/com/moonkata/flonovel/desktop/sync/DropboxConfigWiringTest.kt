package com.moonkata.flonovel.desktop.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DropboxConfigWiringTest {

    @Test
    fun dropboxConfig_readsSystemPropertiesCorrectly() {
        val originalKey = System.getProperty("flonovel.dropbox.app_key")
        val originalDev = System.getProperty("flonovel.dev")
        try {
            System.setProperty("flonovel.dropbox.app_key", "custom_prop_key_123")
            System.setProperty("flonovel.dev", "true")

            assertEquals("custom_prop_key_123", DropboxConfig.appKey)
            assertTrue(DropboxConfig.isDev)
            assertEquals("secret-dev.json", DropboxConfig.secretFileName)
            assertEquals("FloNovelDev", DropboxConfig.appConfigDirName)
        } finally {
            if (originalKey != null) {
                System.setProperty("flonovel.dropbox.app_key", originalKey)
            } else {
                System.clearProperty("flonovel.dropbox.app_key")
            }
            if (originalDev != null) {
                System.setProperty("flonovel.dev", originalDev)
            } else {
                System.clearProperty("flonovel.dev")
            }
        }
    }

    @Test
    fun dropboxConfig_injectedFromGradleEnvironment() {
        val key = DropboxConfig.appKey
        val prop = System.getProperty("flonovel.dropbox.app_key") ?: ""
        assertEquals(prop, key)
    }
}

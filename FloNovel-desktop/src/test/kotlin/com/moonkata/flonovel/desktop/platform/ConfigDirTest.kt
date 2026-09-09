package com.moonkata.flonovel.desktop.platform

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigDirTest {

    @Test
    fun resolvesWindowsPathFromAppDataEnv() {
        val path = resolveConfigDir(
            osName = "Windows 11",
            env = { key -> if (key == "APPDATA") "C:\\Users\\testuser\\AppData\\Roaming" else null },
            userHome = "C:\\Users\\testuser",
            appName = "FloNovel",
        )
        val expected = Path.of("C:", "Users", "testuser", "AppData", "Roaming", "FloNovel")
        assertEquals(expected, path)
    }

    @Test
    fun resolvesWindowsPathFallbackWhenAppDataMissing() {
        val path = resolveConfigDir(
            osName = "Windows 10",
            env = { null },
            userHome = "C:\\Users\\testuser",
            appName = "FloNovel",
        )
        val expected = Path.of("C:", "Users", "testuser", "AppData", "Roaming", "FloNovel")
        assertEquals(expected, path)
    }

    @Test
    fun resolvesMacOSPath() {
        val path = resolveConfigDir(
            osName = "Mac OS X",
            env = { null },
            userHome = "/Users/testuser",
            appName = "FloNovel",
        )
        val expected = Path.of("/Users/testuser", "Library", "Application Support", "FloNovel")
        assertEquals(expected, path)
    }

    @Test
    fun resolvesLinuxPathFromXdgConfigHome() {
        val path = resolveConfigDir(
            osName = "Linux",
            env = { key -> if (key == "XDG_CONFIG_HOME") "/custom/config" else null },
            userHome = "/home/testuser",
            appName = "FloNovel",
        )
        val expected = Path.of("/custom/config", "FloNovel")
        assertEquals(expected, path)
    }

    @Test
    fun resolvesLinuxPathFallbackWhenXdgConfigHomeMissing() {
        val path = resolveConfigDir(
            osName = "Linux",
            env = { null },
            userHome = "/home/testuser",
            appName = "FloNovel",
        )
        val expected = Path.of("/home/testuser", ".config", "FloNovel")
        assertEquals(expected, path)
    }

    @Test
    fun resolvesCurrentOsConfigDir() {
        val path = configDir()
        assertTrue(path.endsWith(Path.of("FloNovel")), "Path should end with FloNovel: $path")
    }
}

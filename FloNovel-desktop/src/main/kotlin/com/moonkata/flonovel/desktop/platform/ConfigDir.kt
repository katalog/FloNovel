package com.moonkata.flonovel.desktop.platform

import java.nio.file.Path

// Must stay spelled exactly like DropboxConfig.appConfigDirName. Callers that pass
// that value explicitly (settings.json, books.json, credentials.json) and callers
// that take this default (the downloaded font cache) have to land in one directory.
// The two used to differ only in case, which Windows and macOS silently merge but
// Linux does not - there the fonts ended up next to the settings directory instead
// of inside it.
private const val DEFAULT_APP_DIR = "FloNovel"

/**
 * Returns the platform-specific configuration directory for the application.
 *
 * Windows : %APPDATA%/FloNovel (fallback: ~/AppData/Roaming/FloNovel)
 * macOS   : ~/Library/Application Support/FloNovel
 * Linux   : ~/.config/FloNovel (or $XDG_CONFIG_HOME/FloNovel)
 */
fun configDir(appName: String = DEFAULT_APP_DIR): Path =
    resolveConfigDir(
        osName = System.getProperty("os.name") ?: "",
        env = System::getenv,
        userHome = System.getProperty("user.home") ?: "",
        appName = appName,
    )

internal fun resolveConfigDir(
    osName: String,
    env: (String) -> String?,
    userHome: String,
    appName: String = DEFAULT_APP_DIR,
): Path {
    val os = osName.lowercase()
    val baseDir: Path = when {
        os.contains("win") -> {
            val appData = env("APPDATA")
            if (!appData.isNullOrBlank()) {
                Path.of(appData)
            } else {
                Path.of(userHome, "AppData", "Roaming")
            }
        }
        os.contains("mac") || os.contains("darwin") -> {
            Path.of(userHome, "Library", "Application Support")
        }
        else -> {
            val xdgConfig = env("XDG_CONFIG_HOME")
            if (!xdgConfig.isNullOrBlank()) {
                Path.of(xdgConfig)
            } else {
                Path.of(userHome, ".config")
            }
        }
    }
    return if (appName.isBlank()) baseDir else baseDir.resolve(appName)
}

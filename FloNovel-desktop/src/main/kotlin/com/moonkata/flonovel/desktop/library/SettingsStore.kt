package com.moonkata.flonovel.desktop.library

import java.nio.file.Path

class SettingsStore(
    val filePath: Path,
) {
    private val lock = Any()
    private var cachedSettings: Settings = loadInitial()

    private fun loadInitial(): Settings {
        val content = AtomicFile.readIfExists(filePath) ?: return Settings()
        return try {
            Settings.fromJsonString(content)
        } catch (_: Exception) {
            Settings()
        }
    }

    fun load(): Settings {
        synchronized(lock) {
            return cachedSettings
        }
    }

    fun save(settings: Settings) {
        synchronized(lock) {
            cachedSettings = settings
            AtomicFile.writeAtomic(filePath, settings.toJsonString())
        }
    }

    fun update(transform: (Settings) -> Settings): Settings {
        synchronized(lock) {
            val updated = transform(cachedSettings)
            save(updated)
            return updated
        }
    }
}

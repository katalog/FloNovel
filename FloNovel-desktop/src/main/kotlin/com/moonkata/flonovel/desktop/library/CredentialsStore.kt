package com.moonkata.flonovel.desktop.library

import java.nio.file.Path

class CredentialsStore(
    val filePath: Path,
) {
    private val lock = Any()
    private var cachedCredentials: Credentials = loadInitial()

    private fun loadInitial(): Credentials {
        val content = AtomicFile.readIfExists(filePath) ?: return Credentials()
        return try {
            Credentials.fromJsonString(content)
        } catch (_: Exception) {
            Credentials()
        }
    }

    fun load(): Credentials {
        synchronized(lock) {
            return cachedCredentials
        }
    }

    fun save(credentials: Credentials) {
        synchronized(lock) {
            cachedCredentials = credentials
            AtomicFile.writeAtomic(filePath, credentials.toJsonString())
        }
    }

    fun update(transform: (Credentials) -> Credentials): Credentials {
        synchronized(lock) {
            val updated = transform(cachedCredentials)
            save(updated)
            return updated
        }
    }
}

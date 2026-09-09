package com.moonkata.flonovel.desktop.sync

object DropboxConfig {
    val appKey: String
        get() = System.getProperty("flonovel.dropbox.app_key")
            ?: System.getenv("DROPBOX_APP_KEY")
            ?: ""

    val isDev: Boolean
        get() = System.getProperty("flonovel.dev")?.toBoolean()
            ?: System.getenv("FLONOVEL_DEV")?.toBoolean()
            ?: false

    const val REDIRECT_PORT = 52475
    const val REDIRECT_URI = "http://localhost:52475/oauth/callback"

    val secretFileName: String
        get() = if (isDev) "secret-dev.json" else "secret.json"

    val appConfigDirName: String
        get() = if (isDev) "FloNovelDev" else "FloNovel"
}

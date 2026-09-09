package com.moonkata.flonovel.desktop.sync

object SupabaseConfig {
    val url: String
        get() = System.getProperty("flonovel.supabase.url")
            ?: System.getenv("SUPABASE_URL")
            ?: ""

    val publishableKey: String
        get() = System.getProperty("flonovel.supabase.publishable_key")
            ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
            ?: ""

    val isConfigured: Boolean
        get() = url.isNotBlank() && publishableKey.isNotBlank()
}

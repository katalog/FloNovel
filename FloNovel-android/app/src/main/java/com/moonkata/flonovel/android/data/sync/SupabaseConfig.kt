package com.moonkata.flonovel.android.data.sync

import com.moonkata.flonovel.android.BuildConfig

/**
 * Supabase project coordinates for reading-position sync (AGENTS.md §1).
 *
 * The values themselves aren't secret — under Supabase's newer key scheme, the publishable key is by
 * design meant to be exposed in the client bundle (the actual defense is the RLS policy), so committing
 * it directly to source is safe. Still, to avoid leaving it permanently in a public repo's history, this
 * simply exposes the `BuildConfig` field injected via `local.properties`/CI environment variables
 * (`app/build.gradle.kts`). If the value isn't injected it's an empty
 * string, in which case only reading-position sync is silently disabled — the build itself always
 * succeeds. The actual shared secret that must be protected isn't here; it is read from the Dropbox
 * app folder (see [SecretStore]).
 */
object SupabaseConfig {
    val URL: String = BuildConfig.SUPABASE_URL
    val PUBLISHABLE_KEY: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
}

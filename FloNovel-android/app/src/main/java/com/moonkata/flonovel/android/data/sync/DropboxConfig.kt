package com.moonkata.flonovel.android.data.sync

import com.moonkata.flonovel.android.BuildConfig

/**
 * Everything about *which* Dropbox app and *which* remote paths this build talks to.
 *
 * The app key is a public-client PKCE key — there is no client secret — but it is still injected
 * through `local.properties`/CI rather than hardcoded, so it never lands in git (docs 10-TASKS T-24).
 *
 * Desktop and Android share **one** Dropbox app. Dev and release builds are separated by
 * [secretPath] (`secret-dev.json` vs `secret.json`), not by key, because the thing that must not mix
 * is the Supabase partition — the server forbids offset regression, so a stray dev value written to
 * the real partition could never be undone (docs G21).
 */
object DropboxConfig {

    val appKey: String get() = BuildConfig.DROPBOX_APP_KEY.trim()

    /** False when no key was injected — the UI shows "not configured" instead of failing mid-OAuth. */
    val isConfigured: Boolean get() = appKey.isNotBlank()

    /**
     * Dropbox accepts this form without registering it in the app console (it is what the official
     * SDK uses), which is why no console change was needed to add Android alongside Desktop.
     *
     * ⚠️ The scheme is derived from the app key, and dev/release share that key, so **both builds
     * register the same scheme**. With both installed, Android shows a disambiguation dialog when
     * the OAuth redirect comes back. That is a developer-only annoyance (pick the app being
     * authorized); if it ever becomes intolerable, register a separate redirect URI per build in the
     * Dropbox app console. The claim in an earlier draft of docs 10-TASKS — that the scheme "splits
     * dev and release automatically" — is wrong, since the key is shared on purpose.
     */
    val redirectUri: String get() = "db-$appKey://1/connect"

    /**
     * Scopes: metadata + content read is all Android needs. Per docs 06-SYNC-STRATEGY B2 the phone
     * never uploads or deletes remotely, so no write scope is requested.
     */
    const val SCOPES = "account_info.read files.metadata.read files.content.read"

    /** Mirrors the home folder's structure; the app-folder root is already private to this app. */
    const val REMOTE_BOOKS_ROOT = "/books"

    /** Shared secret written by Desktop. Read in T-25; the path is defined here so both agree. */
    val secretPath: String get() = "/.flonovel/${BuildConfig.SECRET_FILE_NAME}"
}

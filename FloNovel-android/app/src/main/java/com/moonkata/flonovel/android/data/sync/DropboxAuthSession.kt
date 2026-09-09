package com.moonkata.flonovel.android.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What came back from the browser. The authorization code never reaches the UI layer. */
sealed class DropboxAuthRedirect {
    data class Code(val code: String, val codeVerifier: String) : DropboxAuthRedirect()

    /** The user closed the consent page, denied it, or the verifier was gone (see below). */
    object Cancelled : DropboxAuthRedirect()
}

/**
 * Carries the OAuth flow across the trip out to the browser and back.
 *
 * The redirect re-enters the app through [DropboxAuthActivity], which is a different Activity from
 * the one that started the flow, so the PKCE code verifier has to live somewhere that outlives both.
 * It is kept in memory and never written to disk: it is single-use and short-lived, and a verifier on
 * disk is a verifier that can be stolen from a backup.
 *
 * **Known limitation.** If Android kills the process while the consent page is open, the verifier is
 * gone and the redirect resolves to [DropboxAuthRedirect.Cancelled]. The user taps "connect" again
 * and it works; persisting the verifier to survive that was judged not worth writing a secret to disk
 * for a ten-second window.
 */
object DropboxAuthSession {

    @Volatile
    private var pendingCodeVerifier: String? = null

    private val _redirect = MutableStateFlow<DropboxAuthRedirect?>(null)
    val redirect: StateFlow<DropboxAuthRedirect?> = _redirect

    /**
     * Opens the Dropbox consent page in the browser. Returns false when no browser could handle it,
     * which the caller reports instead of leaving the user staring at a button that does nothing.
     */
    fun launch(context: Context): Boolean {
        val verifier = DropboxOAuth.generateCodeVerifier()
        pendingCodeVerifier = verifier
        _redirect.value = null

        val url = DropboxOAuth.buildAuthorizeUrl(
            appKey = DropboxConfig.appKey,
            codeChallenge = DropboxOAuth.generateCodeChallenge(verifier),
            redirectUri = DropboxConfig.redirectUri,
        )
        // NEW_TASK because this is launched from a Compose sheet, not necessarily an Activity context.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /** Called by [DropboxAuthActivity] with whatever URI the browser handed back. */
    fun onRedirect(uri: Uri?) {
        val verifier = pendingCodeVerifier
        pendingCodeVerifier = null

        val code = uri?.let { DropboxOAuth.extractAuthorizationCode(it.toString(), DropboxConfig.redirectUri) }
        _redirect.value = if (code != null && verifier != null) {
            DropboxAuthRedirect.Code(code, verifier)
        } else {
            DropboxAuthRedirect.Cancelled
        }
    }

    /** Clears the result once acted on, so a configuration change cannot replay the same code. */
    fun consume() {
        _redirect.value = null
    }
}

package com.moonkata.flonovel.android.data.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moonkata.flonovel.android.data.datastore.ReaderSettings
import com.moonkata.flonovel.android.data.datastore.ReaderSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * The parts of Dropbox sync that need the Android runtime: PKCE runs on `android.util.Base64`, and
 * the credential round-trip runs on the real DataStore. Parsing and delta shaping are pure and live
 * in the JVM test `DropboxListParsingTest`.
 *
 * Uses the device's real settings file, so the Dropbox fields are captured and restored.
 */
@RunWith(AndroidJUnit4::class)
class DropboxAuthTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val repository = ReaderSettingsRepository(application)
    private lateinit var original: ReaderSettings

    @Before
    fun captureOriginal() {
        original = runBlocking { repository.settingsFlow.first() }
    }

    @After
    fun restoreOriginal() = runBlocking {
        if (original.dropboxRefreshToken.isBlank()) {
            repository.unlinkDropbox()
        } else {
            repository.linkDropbox(original.dropboxRefreshToken, original.dropboxAccountEmail)
            repository.updateDropboxSyncState(original.dropboxCursor, original.dropboxLastSyncAtMillis)
        }
    }

    // ── PKCE ───────────────────────────────────────────────────────────────

    /** RFC 7636 §4.1: 43 characters for a 32-byte verifier, and nothing needing URL escaping. */
    @Test
    fun codeVerifierIsUrlSafeAndTheRightLength() {
        val verifier = DropboxOAuth.generateCodeVerifier()

        assertEquals(43, verifier.length)
        assertTrue("Verifier must be URL-safe base64", verifier.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse("Padding would need escaping in a query string", verifier.contains("="))
        assertFalse("A wrapped verifier would break the request line", verifier.contains("\n"))
    }

    @Test
    fun everyCodeVerifierIsDifferent() {
        val verifiers = List(50) { DropboxOAuth.generateCodeVerifier() }

        assertEquals("SecureRandom must not repeat across calls", 50, verifiers.toSet().size)
    }

    /**
     * The challenge is the SHA-256 of the verifier's **ASCII bytes**, base64url without padding. If
     * this ever drifts, Dropbox rejects the token exchange with a generic `invalid_grant` that says
     * nothing about which half is wrong — so it is pinned against an independent computation here.
     */
    @Test
    fun codeChallengeIsBase64UrlSha256OfTheVerifier() {
        val verifier = DropboxOAuth.generateCodeVerifier()
        val expected = android.util.Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )

        assertEquals(expected, DropboxOAuth.generateCodeChallenge(verifier))
    }

    @Test
    fun codeChallengeIsDeterministicForTheSameVerifier() {
        val verifier = DropboxOAuth.generateCodeVerifier()

        assertEquals(DropboxOAuth.generateCodeChallenge(verifier), DropboxOAuth.generateCodeChallenge(verifier))
    }

    @Test
    fun differentVerifiersProduceDifferentChallenges() {
        assertNotEquals(
            DropboxOAuth.generateCodeChallenge(DropboxOAuth.generateCodeVerifier()),
            DropboxOAuth.generateCodeChallenge(DropboxOAuth.generateCodeVerifier()),
        )
    }

    // ── Credential storage ─────────────────────────────────────────────────

    @Test
    fun linkStoresTheTokenAndEmailTogether() = runBlocking {
        repository.linkDropbox("refresh-token-1", "reader@example.com")

        val settings = repository.settingsFlow.first()
        assertEquals("refresh-token-1", settings.dropboxRefreshToken)
        assertEquals("reader@example.com", settings.dropboxAccountEmail)
    }

    /** A rotated refresh token must not wipe the account label shown in settings. */
    @Test
    fun updatingTheRefreshTokenLeavesTheEmailAndCursorAlone() = runBlocking {
        repository.linkDropbox("refresh-token-1", "reader@example.com")
        repository.updateDropboxSyncState(cursor = "CURSOR-A", lastSyncAtMillis = 1_000L)

        repository.updateDropboxRefreshToken("refresh-token-2")

        val settings = repository.settingsFlow.first()
        assertEquals("refresh-token-2", settings.dropboxRefreshToken)
        assertEquals("reader@example.com", settings.dropboxAccountEmail)
        assertEquals("CURSOR-A", settings.dropboxCursor)
    }

    /**
     * A cursor is a position in *that account's* change stream. Keeping it across a sign-out would
     * make the next account's first sync incremental against a history it never had — the phone would
     * quietly miss every book that already existed.
     */
    @Test
    fun signingOutClearsTheCursorAlongWithTheToken() = runBlocking {
        repository.linkDropbox("refresh-token-1", "reader@example.com")
        repository.updateDropboxSyncState(cursor = "CURSOR-A", lastSyncAtMillis = 1_000L)

        repository.unlinkDropbox()

        val settings = repository.settingsFlow.first()
        assertEquals("", settings.dropboxRefreshToken)
        assertEquals("", settings.dropboxAccountEmail)
        assertEquals("A stale cursor would make the next account's first sync incremental", "", settings.dropboxCursor)
        assertEquals(0L, settings.dropboxLastSyncAtMillis)
    }

    /** Nothing is linked by default, so the sheet opens on "connect" rather than a broken signed-in state. */
    @Test
    fun defaultsAreUnlinked() {
        val defaults = ReaderSettings()

        assertEquals("", defaults.dropboxRefreshToken)
        assertEquals("", defaults.dropboxCursor)
        assertEquals(0L, defaults.dropboxLastSyncAtMillis)
    }
}

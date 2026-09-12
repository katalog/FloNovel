package com.moonkata.flonovel.desktop.sync

import com.moonkata.flonovel.desktop.library.CredentialsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RemotePositionNotice(
    val bookKey: String,
    val charOffset: Int,
    val source: String,
    val delta: Int,
)

sealed class ForcePushOutcome {
    data class Success(val charOffset: Int) : ForcePushOutcome()
    object NoBookOpen : ForcePushOutcome()
    object SyncNotAvailable : ForcePushOutcome()
    data class Failure(val message: String) : ForcePushOutcome()
}

/**
 * Coordinates Supabase reading position sync (06-SYNC-STRATEGY.md PART A):
 * - Checkpoint idle: 5 minutes (300_000ms)
 * - Fetch cooldown: 30 seconds (30_000ms)
 * - Notification threshold: remote - local > 500 characters
 * - Gate: secret != verifiedSecret -> client is OFF (null)
 * - Push triggers: 5 min at same position, window blur, app exit
 * - Fetch triggers: book open, window focus (with cooldown)
 * - No polling. No GET before push.
 */
class ReadingPositionSyncCoordinator(
    private val credentialsStore: CredentialsStore,
    private val coroutineScope: CoroutineScope,
    private val clientFactory: ((baseUrl: String, key: String, secret: String) -> ReadingPositionSyncClient)? = null,
    val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val CHECKPOINT_INTERVAL_MS = 300_000L // 5 minutes
        const val FETCH_COOLDOWN_MS = 30_000L // 30 seconds
        const val NOTIFICATION_THRESHOLD = 500 // chars
    }

    var lastFetchTime: Long = 0L
    var lastPushedOffset: Int? = null
    var currentBookKey: String? = null
    var currentEncoding: String? = null
    var currentAnchor: Int = 0

    var activeNotice: RemotePositionNotice? = null
        private set

    var onNoticeChanged: ((RemotePositionNotice?) -> Unit)? = null

    private var checkpointJob: Job? = null

    val isSyncEnabled: Boolean
        get() = getClientOrNull() != null

    fun getClientOrNull(): ReadingPositionSyncClient? {
        val creds = credentialsStore.load()
        val secret = creds.cachedSupabaseSecret
        val verified = creds.verifiedSupabaseSecret
        if (secret.isNullOrBlank() || verified.isNullOrBlank() || secret != verified) {
            return null
        }
        val url = SupabaseConfig.url
        val key = SupabaseConfig.publishableKey
        if (url.isBlank() || key.isBlank()) {
            return null
        }
        return clientFactory?.invoke(url, key, secret) ?: ReadingPositionSyncClient(url, key, secret)
    }

    /**
     * Called when a book is opened in the reader.
     */
    fun onBookOpened(bookKey: String, initialAnchor: Int, encoding: String?) {
        currentBookKey = bookKey
        currentAnchor = initialAnchor
        currentEncoding = encoding
        lastPushedOffset = null
        setNotice(null)
        startCheckpointTimer()
        triggerFetch(force = true)
    }

    /**
     * Called whenever local reading anchor changes.
     */
    fun onAnchorChanged(newAnchor: Int) {
        if (currentAnchor != newAnchor) {
            currentAnchor = newAnchor
            startCheckpointTimer()
        }
    }

    /**
     * Window focus lost: push current position.
     */
    fun onWindowFocusLost() {
        triggerPush()
    }

    /**
     * Window focus gained: fetch remote position if cooldown elapsed.
     */
    fun onWindowFocusGained() {
        triggerFetch(force = false)
    }

    /**
     * Reader exited or app closing: push current position.
     */
    fun onBookClosed() {
        checkpointJob?.cancel()
        triggerPush()
        currentBookKey = null
        setNotice(null)
    }

    /**
     * Starts or resets the 5-minute checkpoint timer for idle position.
     */
    fun startCheckpointTimer() {
        checkpointJob?.cancel()
        checkpointJob = coroutineScope.launch {
            delay(CHECKPOINT_INTERVAL_MS)
            triggerPush()
        }
    }

    /**
     * Pushes current position if active client exists and anchor != lastPushedOffset.
     */
    fun triggerPush(): Job? {
        val client = getClientOrNull() ?: return null
        val bookKey = currentBookKey ?: return null
        val anchor = currentAnchor
        if (lastPushedOffset == anchor) return null

        lastPushedOffset = anchor
        return coroutineScope.launch {
            client.upsert(bookKey, anchor, currentEncoding)
        }
    }

    /**
     * Fetches remote position and evaluates notification:
     * Condition: remote - local > 500
     */
    fun triggerFetch(force: Boolean = false): Job? {
        val client = getClientOrNull() ?: return null
        val bookKey = currentBookKey ?: return null
        val now = clock()
        if (!force && (now - lastFetchTime < FETCH_COOLDOWN_MS)) {
            return null
        }
        lastFetchTime = now

        return coroutineScope.launch {
            val remote = client.fetch(bookKey) ?: return@launch
            val delta = remote.charOffset - currentAnchor
            if (delta > NOTIFICATION_THRESHOLD) {
                setNotice(
                    RemotePositionNotice(
                        bookKey = bookKey,
                        charOffset = remote.charOffset,
                        source = remote.source,
                        delta = delta,
                    )
                )
            }
        }
    }

    /**
     * Explicit user override for a stuck/corrupted remote position. A plain upsert
     * cannot fix this: the server trigger clamps char_offset to greatest(new, old)
     * on UPDATE, so it can never move down. Deleting the row first turns the
     * follow-up upsert into a fresh INSERT, which bypasses that clamp.
     */
    suspend fun forcePush(): ForcePushOutcome {
        val client = getClientOrNull() ?: return ForcePushOutcome.SyncNotAvailable
        val bookKey = currentBookKey ?: return ForcePushOutcome.NoBookOpen
        val anchor = currentAnchor

        if (!client.delete(bookKey)) {
            return ForcePushOutcome.Failure(client.lastSyncError ?: "delete failed")
        }
        client.upsert(bookKey, anchor, currentEncoding)
        val error = client.lastSyncError
        if (error != null) {
            return ForcePushOutcome.Failure(error)
        }
        lastPushedOffset = anchor
        return ForcePushOutcome.Success(anchor)
    }

    fun dismissNotice() {
        setNotice(null)
    }

    private fun setNotice(notice: RemotePositionNotice?) {
        activeNotice = notice
        onNoticeChanged?.invoke(notice)
    }
}

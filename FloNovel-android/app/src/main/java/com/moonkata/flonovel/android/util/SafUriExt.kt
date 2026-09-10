package com.moonkata.flonovel.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Persists both read AND write access to [uri] beyond this process's lifetime.
 *
 * The picker's own result already carries both flags and works immediately after selection, which
 * is why the very first browse/sync after picking a folder can succeed even when the *persisted*
 * grant ends up missing one of them — that gap used to be exactly this function's bug: it only
 * asked to persist read. Reading kept working forever after a cold start (browsing/opening books
 * only needs read), but every Dropbox write (download, delete) failed silently on every device, the
 * very first time the app process was killed and relaunched — see [hasPersistedWritePermission].
 */
fun Context.takePersistableReadWritePermission(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    } catch (e: SecurityException) {
        // URI was already released or the permission can't be granted — ignore and continue
    }
}

/**
 * True only if this URI still has a persisted read grant. A URI string surviving in DataStore
 * (e.g. restored by Android's Auto Backup on a fresh install) doesn't imply the grant survived —
 * SAF permissions are re-issued per install, so a restored URI can look valid while being
 * completely unusable. Check this before treating a saved tree URI as openable at all.
 */
fun Context.hasPersistedReadPermission(uri: Uri): Boolean =
    contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

/**
 * True only if this URI ALSO still has a persisted write grant, on top of read.
 *
 * A folder with read but not write browses and opens books completely normally — reading must never
 * depend on this — but every Dropbox write (download, delete) fails on it, forever, no matter how
 * many times the sync is retried. Checked right before a sync attempt so that case gets a message
 * that actually explains what fixes it ("choose the folder again"), instead of a confusing
 * "N failed, try again" that can never succeed by trying again.
 */
fun Context.hasPersistedWritePermission(uri: Uri): Boolean =
    contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

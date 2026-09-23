package com.moonkata.flonovel.desktop.sync

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two-way sync decision table. The Android suite (SyncDecisionTest there) carries the same
 * cases with the same names; when a rule changes, both lists change together.
 */
class SyncDecisionTest {

    // Content "A" is what both sides last agreed on; "B" and "C" are later, different edits.
    private val base = SyncBase("book.txt", "Book.txt", rev = "r1", contentHash = "A", localSize = 10, localMtime = 100)

    private fun local(hash: String, size: Long = 10, mtime: Long = 100) = LocalFileState(size, mtime, hash)
    private fun remote(hash: String, rev: String = "r1") = RemoteFileState("Book.txt", rev, hash, 10)

    private fun check(expected: SyncAction, base: SyncBase?, local: LocalFileState?, remote: RemoteFileState?) =
        assertEquals(expected, SyncDecision.decide(base, local, remote))

    // ── No base: first time this path is seen ────────────────────────────

    @Test fun noBase_nothingAnywhere_none() = check(SyncAction.None, null, null, null)
    @Test fun noBase_localOnly_uploadAsNew() = check(SyncAction.Upload(null), null, local("A"), null)
    @Test fun noBase_remoteOnly_download() = check(SyncAction.Download(remote("A")), null, null, remote("A"))
    @Test fun noBase_bothSameContent_adopt() = check(SyncAction.AdoptBase(remote("A")), null, local("A"), remote("A"))
    @Test fun noBase_bothDifferentContent_conflict() = check(SyncAction.Conflict(remote("B")), null, local("A"), remote("B"))

    // ── Base present: the table in CLAUDE.md §1 ─────────────────────────

    @Test fun unchanged_unchanged_none() = check(SyncAction.None, base, local("A"), remote("A"))
    @Test fun unchanged_modified_download() = check(SyncAction.Download(remote("B", "r2")), base, local("A"), remote("B", "r2"))
    @Test fun unchanged_deleted_deleteLocal() = check(SyncAction.DeleteLocal, base, local("A"), null)
    @Test fun modified_unchanged_uploadOverBaseRev() = check(SyncAction.Upload("r1"), base, local("B"), remote("A"))
    @Test fun deleted_unchanged_deleteRemoteAtBaseRev() = check(SyncAction.DeleteRemote("r1"), base, null, remote("A"))
    @Test fun modified_modifiedSame_adopt() = check(SyncAction.AdoptBase(remote("B", "r2")), base, local("B"), remote("B", "r2"))
    @Test fun modified_modifiedDifferent_conflict() = check(SyncAction.Conflict(remote("C", "r2")), base, local("B"), remote("C", "r2"))
    @Test fun modified_deleted_editWinsUploadAsNew() = check(SyncAction.Upload(null), base, local("B"), null)
    @Test fun deleted_modified_editWinsDownload() = check(SyncAction.Download(remote("B", "r2")), base, null, remote("B", "r2"))
    @Test fun deleted_deleted_forget() = check(SyncAction.ForgetBase, base, null, null)

    // ── Base refresh without content change ─────────────────────────────

    @Test fun sameContentNewRev_adoptToRefreshRev() =
        check(SyncAction.AdoptBase(remote("A", "r2")), base, local("A"), remote("A", "r2"))

    @Test fun sameContentTouchedLocally_adoptToRefreshStat() =
        check(SyncAction.AdoptBase(remote("A")), base, local("A", mtime = 999), remote("A"))

    // ── Interrupted download ─────────────────────────────────────────────

    private val downloading = base.copy(state = BaseState.DOWNLOADING)

    @Test fun downloading_localLooksEdited_isNeverUploaded() =
        check(SyncAction.Download(remote("A")), downloading, local("TRUNCATED", size = 3), remote("A"))

    @Test fun downloading_remoteGone_deleteLocalLeftover() = check(SyncAction.DeleteLocal, downloading, local("TRUNCATED"), null)
    @Test fun downloading_nothingLeft_forget() = check(SyncAction.ForgetBase, downloading, null, null)

    // ── When the local file must be hashed ──────────────────────────────

    @Test
    fun needsLocalHash_onlyWhenStatDiffersOrBaseUntrusted() {
        assertFalse(SyncDecision.needsLocalHash(base, 10, 100))
        assertTrue(SyncDecision.needsLocalHash(base, 11, 100))
        assertTrue(SyncDecision.needsLocalHash(base, 10, 101))
        assertTrue(SyncDecision.needsLocalHash(null, 10, 100))
        assertTrue(SyncDecision.needsLocalHash(downloading, 10, 100))
    }

    // ── Mass deletion guard ──────────────────────────────────────────────

    @Test
    fun massDeletion_thresholds() {
        assertTrue(SyncDecision.isMassDeletion(deletions = 0, trackedFiles = 3, remoteIsEmpty = true))
        assertFalse(SyncDecision.isMassDeletion(deletions = 0, trackedFiles = 0, remoteIsEmpty = true))
        assertTrue(SyncDecision.isMassDeletion(deletions = 20, trackedFiles = 10_000, remoteIsEmpty = false))
        assertFalse(SyncDecision.isMassDeletion(deletions = 19, trackedFiles = 10_000, remoteIsEmpty = false))
        // 30% only counts from 5 deletions: one book out of three is an ordinary delete.
        assertFalse(SyncDecision.isMassDeletion(deletions = 1, trackedFiles = 3, remoteIsEmpty = false))
        assertFalse(SyncDecision.isMassDeletion(deletions = 4, trackedFiles = 4, remoteIsEmpty = false))
        assertTrue(SyncDecision.isMassDeletion(deletions = 5, trackedFiles = 16, remoteIsEmpty = false))
        assertFalse(SyncDecision.isMassDeletion(deletions = 5, trackedFiles = 17, remoteIsEmpty = false))
    }

    // ── Conflict copy names ──────────────────────────────────────────────

    private val date = LocalDate.of(2026, 9, 23)

    @Test
    fun conflictCopyName_format() {
        assertEquals(
            "책 (충돌 사본 - PC - 2026-09-23).txt",
            SyncDecision.conflictCopyName("책.txt", "충돌 사본", "PC", date) { false },
        )
        assertEquals(
            "Book (conflicted copy - Android - 2026-09-23).txt",
            SyncDecision.conflictCopyName("Book.txt", "conflicted copy", "Android", date) { false },
        )
    }

    @Test
    fun conflictCopyName_takenNamesGetSuffix() {
        val taken = setOf(
            "책 (충돌 사본 - PC - 2026-09-23).txt",
            "책 (충돌 사본 - PC - 2026-09-23)_1.txt",
        )
        assertEquals(
            "책 (충돌 사본 - PC - 2026-09-23)_2.txt",
            SyncDecision.conflictCopyName("책.txt", "충돌 사본", "PC", date) { it in taken },
        )
    }

    @Test
    fun conflictCopyName_withoutExtensionAndWithDots() {
        assertEquals("README (c - PC - 2026-09-23)", SyncDecision.conflictCopyName("README", "c", "PC", date) { false })
        assertEquals("vol.1 (c - PC - 2026-09-23).txt", SyncDecision.conflictCopyName("vol.1.txt", "c", "PC", date) { false })
    }
}

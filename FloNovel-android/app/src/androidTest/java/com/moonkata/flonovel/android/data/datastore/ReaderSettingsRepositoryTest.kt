package com.moonkata.flonovel.android.data.datastore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `ReaderSettingsRepository` has only ever been exercised indirectly, through the other sheet
 * tests. Here, the repository itself is the direct target — verifying round-trip storage of
 * representative types (String/Float/Boolean/Int/enum/Set), plus
 * `updateSupabaseSharedSecret`, which has real conditional logic rather than a simple round
 * trip. Uses the real device's actual DataStore file, so every field touched
 * has its starting value remembered and restored at the end.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSettingsRepositoryTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val repository = ReaderSettingsRepository(application)
    private lateinit var original: ReaderSettings

    @Before
    fun captureOriginal() {
        original = runBlocking { repository.settingsFlow.first() }
    }

    @After
    fun restoreOriginal() = runBlocking {
        repository.updateFontSizeSp(original.fontSizeSp)
        repository.updateKeepScreenOnEnabled(original.keepScreenOnEnabled)
        repository.updateChapterJumpDivisions(original.chapterJumpDivisions)
        repository.updateThemePreset(original.themePreset)
        repository.updateChapterPatternEnabledIds(original.chapterPatternEnabledIds)
        repository.updateLastUsedSafTreeUri(original.lastUsedSafTreeUri)
        repository.updateTouchLeftAction(original.touchLeftAction)
        repository.updateTouchRightAction(original.touchRightAction)
        repository.updateSwipeLeftAction(original.swipeLeftAction)
        repository.updateSwipeRightAction(original.swipeRightAction)
        repository.updateSwipeUpAction(original.swipeUpAction)
        repository.updateSwipeDownAction(original.swipeDownAction)
        repository.updateSupabaseSharedSecret(original.supabaseSharedSecret, original.supabaseVerifiedSecret)
    }

    @Test
    fun floatValue_roundTripsThroughDataStore() = runBlocking {
        val target = if (original.fontSizeSp < 30f) original.fontSizeSp + 3f else original.fontSizeSp - 3f
        repository.updateFontSizeSp(target)
        assertEquals(target, repository.settingsFlow.first().fontSizeSp)
    }

    @Test
    fun booleanValue_roundTripsThroughDataStore() = runBlocking {
        val target = !original.keepScreenOnEnabled
        repository.updateKeepScreenOnEnabled(target)
        assertEquals(target, repository.settingsFlow.first().keepScreenOnEnabled)
    }

    @Test
    fun intValue_roundTripsThroughDataStore() = runBlocking {
        val target = original.chapterJumpDivisions + 1
        repository.updateChapterJumpDivisions(target)
        assertEquals(target, repository.settingsFlow.first().chapterJumpDivisions)
    }

    @Test
    fun enumValue_roundTripsThroughDataStore_storedByName() = runBlocking {
        val target = if (original.themePreset == ThemePreset.DARK_NAVY) ThemePreset.SEPIA_CREAM else ThemePreset.DARK_NAVY
        repository.updateThemePreset(target)
        assertEquals(target, repository.settingsFlow.first().themePreset)
    }

    @Test
    fun gestureActionSettings_eachRoundTripsThroughItsOwnDataStoreKey() = runBlocking {
        // One representative value change per gesture, each checked against a fresh read — this is
        // the failure mode a copy-paste mistake in the Keys object (e.g. TOUCH_LEFT_ACTION reading
        // back SWIPE_RIGHT_ACTION's stored value) would produce: the wrong field would silently
        // reflect a value that was never written to it.
        val target = PageGestureAction.entries.first { it != original.touchLeftAction }
        repository.updateTouchLeftAction(target)
        assertEquals(target, repository.settingsFlow.first().touchLeftAction)

        val touchRightTarget = PageGestureAction.entries.first { it != original.touchRightAction }
        repository.updateTouchRightAction(touchRightTarget)
        assertEquals(touchRightTarget, repository.settingsFlow.first().touchRightAction)

        val swipeLeftTarget = PageGestureAction.entries.first { it != original.swipeLeftAction }
        repository.updateSwipeLeftAction(swipeLeftTarget)
        assertEquals(swipeLeftTarget, repository.settingsFlow.first().swipeLeftAction)

        val swipeRightTarget = PageGestureAction.entries.first { it != original.swipeRightAction }
        repository.updateSwipeRightAction(swipeRightTarget)
        assertEquals(swipeRightTarget, repository.settingsFlow.first().swipeRightAction)

        val swipeUpTarget = PageGestureAction.entries.first { it != original.swipeUpAction }
        repository.updateSwipeUpAction(swipeUpTarget)
        assertEquals(swipeUpTarget, repository.settingsFlow.first().swipeUpAction)

        val swipeDownTarget = PageGestureAction.entries.first { it != original.swipeDownAction }
        repository.updateSwipeDownAction(swipeDownTarget)
        assertEquals(swipeDownTarget, repository.settingsFlow.first().swipeDownAction)

        // None of the six should have leaked into a different field.
        val settings = repository.settingsFlow.first()
        assertEquals(target, settings.touchLeftAction)
        assertEquals(touchRightTarget, settings.touchRightAction)
        assertEquals(swipeLeftTarget, settings.swipeLeftAction)
        assertEquals(swipeRightTarget, settings.swipeRightAction)
        assertEquals(swipeUpTarget, settings.swipeUpAction)
        assertEquals(swipeDownTarget, settings.swipeDownAction)
    }

    @Test
    fun stringSetValue_roundTripsThroughDataStore() = runBlocking {
        val target = setOf("custom-pattern-1", "custom-pattern-2")
        repository.updateChapterPatternEnabledIds(target)
        assertEquals(target, repository.settingsFlow.first().chapterPatternEnabledIds)
    }

    @Test
    fun nullableStringValue_passingNull_actuallyRemovesTheKey_ratherThanStoringLiteralNull() = runBlocking {
        repository.updateLastUsedSafTreeUri("content://some/tree")
        assertEquals("content://some/tree", repository.settingsFlow.first().lastUsedSafTreeUri)

        repository.updateLastUsedSafTreeUri(null)

        assertNull(
            "Passing null must remove the key itself — safely falls back to the default (null)",
            repository.settingsFlow.first().lastUsedSafTreeUri,
        )
    }

    @Test
    fun updateSupabaseSharedSecret_withoutVerifiedSecret_leavesTheVerifiedFieldUnchanged() = runBlocking {
        repository.updateSupabaseSharedSecret("first-secret", verifiedSecret = "first-secret")
        assertEquals("first-secret", repository.settingsFlow.first().supabaseVerifiedSecret)

        // When verifiedSecret isn't passed (e.g. only the secret was edited but the connection
        // test hasn't been run yet), the verified value must stay unchanged — the "Connected"
        // badge must not incorrectly linger or incorrectly disappear.
        repository.updateSupabaseSharedSecret("second-secret", verifiedSecret = null)

        val settings = repository.settingsFlow.first()
        assertEquals("second-secret", settings.supabaseSharedSecret)
        assertEquals("Calling without verifiedSecret must not change the verified value", "first-secret", settings.supabaseVerifiedSecret)
    }

    @Test
    fun updateSupabaseSharedSecret_withVerifiedSecret_commitsBothInOneCall() = runBlocking {
        repository.updateSupabaseSharedSecret("verified-secret", verifiedSecret = "verified-secret")
        val settings = repository.settingsFlow.first()
        assertEquals("verified-secret", settings.supabaseSharedSecret)
        assertEquals("verified-secret", settings.supabaseVerifiedSecret)
    }

}

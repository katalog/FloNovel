package com.moonkata.flonovel.android.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moonkata.flonovel.android.model.FolderSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flonovel_settings")

class ReaderSettingsRepository(private val context: Context) {

    private object Keys {
        val FONT_FAMILY_ID = stringPreferencesKey("font_family_id")
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val LINE_HEIGHT_MULTIPLIER = floatPreferencesKey("line_height_multiplier")
        val LETTER_SPACING_SP = floatPreferencesKey("letter_spacing_sp")
        val MARGIN_HORIZONTAL_DP = floatPreferencesKey("margin_horizontal_dp")
        val MARGIN_TOP_DP = floatPreferencesKey("margin_top_dp")
        val MARGIN_BOTTOM_DP = floatPreferencesKey("margin_bottom_dp")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val CUSTOM_BG_COLOR = intPreferencesKey("custom_bg_color")
        val CUSTOM_TEXT_COLOR = intPreferencesKey("custom_text_color")
        val PAGE_TURN_MODE = stringPreferencesKey("page_turn_mode")
        val BRIGHTNESS_OVERRIDE_ENABLED = booleanPreferencesKey("brightness_override_enabled")
        val BRIGHTNESS_VALUE = floatPreferencesKey("brightness_value")
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_KEY_PAGING = booleanPreferencesKey("volume_key_paging")
        // The stored key name keeps the old "skip" naming as-is — changing it would wipe out existing saved chapter jump settings.
        val CHAPTER_JUMP_DIVISIONS = intPreferencesKey("chapter_skip_divisions")
        val AUTO_ADVANCE_MODE = stringPreferencesKey("auto_advance_mode")
        val AUTO_PAGE_TURN_INTERVAL_SECONDS = intPreferencesKey("auto_page_turn_interval_seconds")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val LAST_USED_SAF_TREE_URI = stringPreferencesKey("last_used_saf_tree_uri")
        val LIBRARY_SORT_OPTION = stringPreferencesKey("library_sort_option")
        val CHAPTER_PATTERN_ENABLED_IDS = stringSetPreferencesKey("chapter_pattern_enabled_ids")
        val CHAPTER_CUSTOM_PATTERNS = stringSetPreferencesKey("chapter_custom_patterns")
        val TOUCH_ZONE_MODE = stringPreferencesKey("touch_zone_mode")
        val GRID_TOUCH_ACTIONS = stringPreferencesKey("grid_touch_actions")
        val TOUCH_LEFT_ACTION = stringPreferencesKey("touch_left_action")
        val TOUCH_RIGHT_ACTION = stringPreferencesKey("touch_right_action")
        val SWIPE_LEFT_ACTION = stringPreferencesKey("swipe_left_action")
        val SWIPE_RIGHT_ACTION = stringPreferencesKey("swipe_right_action")
        val SWIPE_UP_ACTION = stringPreferencesKey("swipe_up_action")
        val SWIPE_DOWN_ACTION = stringPreferencesKey("swipe_down_action")
        val PAGE_TRANSITION_ANIMATION = stringPreferencesKey("page_transition_animation")
        val SUPABASE_SHARED_SECRET = stringPreferencesKey("supabase_shared_secret")
        val SUPABASE_VERIFIED_SECRET = stringPreferencesKey("supabase_verified_secret")
        val DROPBOX_REFRESH_TOKEN = stringPreferencesKey("dropbox_refresh_token")
        val DROPBOX_ACCOUNT_EMAIL = stringPreferencesKey("dropbox_account_email")
        val DROPBOX_CURSOR = stringPreferencesKey("dropbox_cursor")
        val DROPBOX_LAST_SYNC_AT_MILLIS = longPreferencesKey("dropbox_last_sync_at_millis")
    }

    val settingsFlow: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        val defaults = ReaderSettings()
        ReaderSettings(
            fontFamilyId = prefs[Keys.FONT_FAMILY_ID] ?: defaults.fontFamilyId,
            fontSizeSp = prefs[Keys.FONT_SIZE_SP] ?: defaults.fontSizeSp,
            lineHeightMultiplier = prefs[Keys.LINE_HEIGHT_MULTIPLIER] ?: defaults.lineHeightMultiplier,
            letterSpacingSp = prefs[Keys.LETTER_SPACING_SP] ?: defaults.letterSpacingSp,
            marginHorizontalDp = prefs[Keys.MARGIN_HORIZONTAL_DP] ?: defaults.marginHorizontalDp,
            marginTopDp = prefs[Keys.MARGIN_TOP_DP] ?: defaults.marginTopDp,
            marginBottomDp = prefs[Keys.MARGIN_BOTTOM_DP] ?: defaults.marginBottomDp,
            themePreset = prefs[Keys.THEME_PRESET]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() } ?: defaults.themePreset,
            customBackgroundColorArgb = prefs[Keys.CUSTOM_BG_COLOR] ?: defaults.customBackgroundColorArgb,
            customTextColorArgb = prefs[Keys.CUSTOM_TEXT_COLOR] ?: defaults.customTextColorArgb,
            pageTurnMode = prefs[Keys.PAGE_TURN_MODE]?.let { runCatching { PageTurnMode.valueOf(it) }.getOrNull() } ?: defaults.pageTurnMode,
            brightnessOverrideEnabled = prefs[Keys.BRIGHTNESS_OVERRIDE_ENABLED] ?: defaults.brightnessOverrideEnabled,
            brightnessValue = prefs[Keys.BRIGHTNESS_VALUE] ?: defaults.brightnessValue,
            orientationLock = prefs[Keys.ORIENTATION_LOCK]?.let { runCatching { OrientationLock.valueOf(it) }.getOrNull() } ?: defaults.orientationLock,
            keepScreenOnEnabled = prefs[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOnEnabled,
            volumeKeyPagingEnabled = prefs[Keys.VOLUME_KEY_PAGING] ?: defaults.volumeKeyPagingEnabled,
            chapterJumpDivisions = prefs[Keys.CHAPTER_JUMP_DIVISIONS] ?: defaults.chapterJumpDivisions,
            autoAdvanceMode = prefs[Keys.AUTO_ADVANCE_MODE]?.let { runCatching { AutoAdvanceMode.valueOf(it) }.getOrNull() } ?: defaults.autoAdvanceMode,
            autoPageTurnIntervalSeconds = prefs[Keys.AUTO_PAGE_TURN_INTERVAL_SECONDS] ?: defaults.autoPageTurnIntervalSeconds,
            ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: defaults.ttsSpeechRate,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: defaults.ttsPitch,
            lastUsedSafTreeUri = prefs[Keys.LAST_USED_SAF_TREE_URI] ?: defaults.lastUsedSafTreeUri,
            librarySortOption = prefs[Keys.LIBRARY_SORT_OPTION]?.let { runCatching { FolderSortOption.valueOf(it) }.getOrNull() } ?: defaults.librarySortOption,
            chapterPatternEnabledIds = prefs[Keys.CHAPTER_PATTERN_ENABLED_IDS] ?: defaults.chapterPatternEnabledIds,
            chapterCustomPatterns = prefs[Keys.CHAPTER_CUSTOM_PATTERNS] ?: defaults.chapterCustomPatterns,
            touchZoneMode = prefs[Keys.TOUCH_ZONE_MODE]?.let { runCatching { TouchZoneMode.valueOf(it) }.getOrNull() } ?: defaults.touchZoneMode,
            gridTouchActions = prefs[Keys.GRID_TOUCH_ACTIONS]?.let { raw ->
                val tokens = raw.split(",")
                if (tokens.size == 9) {
                    tokens.map { token ->
                        runCatching { PageGestureAction.valueOf(token) }.getOrDefault(PageGestureAction.NEXT_PAGE)
                    }
                } else null
            } ?: defaults.gridTouchActions,
            touchLeftAction = prefs[Keys.TOUCH_LEFT_ACTION]?.let { runCatching { PageGestureAction.valueOf(it) }.getOrNull() } ?: defaults.touchLeftAction,
            touchRightAction = prefs[Keys.TOUCH_RIGHT_ACTION]?.let { runCatching { PageGestureAction.valueOf(it) }.getOrNull() } ?: defaults.touchRightAction,
            swipeLeftAction = prefs[Keys.SWIPE_LEFT_ACTION]?.let { runCatching { PageGestureAction.valueOf(it) }.getOrNull() } ?: defaults.swipeLeftAction,
            swipeRightAction = prefs[Keys.SWIPE_RIGHT_ACTION]?.let { runCatching { PageGestureAction.valueOf(it) }.getOrNull() } ?: defaults.swipeRightAction,
            swipeUpAction = prefs[Keys.SWIPE_UP_ACTION]?.let { runCatching { PageGestureAction.valueOf(it) }.getOrNull() } ?: defaults.swipeUpAction,
            swipeDownAction = prefs[Keys.SWIPE_DOWN_ACTION]?.let { runCatching { PageGestureAction.valueOf(it) }.getOrNull() } ?: defaults.swipeDownAction,
            pageTransitionAnimation = prefs[Keys.PAGE_TRANSITION_ANIMATION]
                ?.let { runCatching { PageTransitionAnimation.valueOf(it) }.getOrNull() } ?: defaults.pageTransitionAnimation,
            supabaseSharedSecret = prefs[Keys.SUPABASE_SHARED_SECRET] ?: defaults.supabaseSharedSecret,
            supabaseVerifiedSecret = prefs[Keys.SUPABASE_VERIFIED_SECRET] ?: defaults.supabaseVerifiedSecret,
            dropboxRefreshToken = prefs[Keys.DROPBOX_REFRESH_TOKEN] ?: defaults.dropboxRefreshToken,
            dropboxAccountEmail = prefs[Keys.DROPBOX_ACCOUNT_EMAIL] ?: defaults.dropboxAccountEmail,
            dropboxCursor = prefs[Keys.DROPBOX_CURSOR] ?: defaults.dropboxCursor,
            dropboxLastSyncAtMillis = prefs[Keys.DROPBOX_LAST_SYNC_AT_MILLIS] ?: defaults.dropboxLastSyncAtMillis,
        )
    }

    suspend fun updateFontFamilyId(value: String) = edit { it[Keys.FONT_FAMILY_ID] = value }
    suspend fun updateFontSizeSp(value: Float) = edit { it[Keys.FONT_SIZE_SP] = value }
    suspend fun updateLineHeightMultiplier(value: Float) = edit { it[Keys.LINE_HEIGHT_MULTIPLIER] = value }
    suspend fun updateLetterSpacingSp(value: Float) = edit { it[Keys.LETTER_SPACING_SP] = value }
    suspend fun updateMarginHorizontalDp(value: Float) = edit { it[Keys.MARGIN_HORIZONTAL_DP] = value }
    suspend fun updateMarginTopDp(value: Float) = edit { it[Keys.MARGIN_TOP_DP] = value }
    suspend fun updateMarginBottomDp(value: Float) = edit { it[Keys.MARGIN_BOTTOM_DP] = value }
    suspend fun updateThemePreset(value: ThemePreset) = edit { it[Keys.THEME_PRESET] = value.name }
    suspend fun updateCustomColors(background: Int, text: Int) = edit {
        it[Keys.CUSTOM_BG_COLOR] = background
        it[Keys.CUSTOM_TEXT_COLOR] = text
    }
    suspend fun updatePageTurnMode(value: PageTurnMode) = edit { it[Keys.PAGE_TURN_MODE] = value.name }
    suspend fun updateBrightnessOverrideEnabled(value: Boolean) = edit { it[Keys.BRIGHTNESS_OVERRIDE_ENABLED] = value }
    suspend fun updateBrightnessValue(value: Float) = edit { it[Keys.BRIGHTNESS_VALUE] = value }
    suspend fun updateOrientationLock(value: OrientationLock) = edit { it[Keys.ORIENTATION_LOCK] = value.name }
    suspend fun updateKeepScreenOnEnabled(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun updateVolumeKeyPagingEnabled(value: Boolean) = edit { it[Keys.VOLUME_KEY_PAGING] = value }
    suspend fun updateChapterJumpDivisions(value: Int) = edit { it[Keys.CHAPTER_JUMP_DIVISIONS] = value }
    suspend fun updateAutoAdvanceMode(value: AutoAdvanceMode) = edit { it[Keys.AUTO_ADVANCE_MODE] = value.name }
    suspend fun updateAutoPageTurnIntervalSeconds(value: Int) = edit { it[Keys.AUTO_PAGE_TURN_INTERVAL_SECONDS] = value }
    suspend fun updateTtsSpeechRate(value: Float) = edit { it[Keys.TTS_SPEECH_RATE] = value }
    suspend fun updateTtsPitch(value: Float) = edit { it[Keys.TTS_PITCH] = value }
    suspend fun updateLastUsedSafTreeUri(value: String?) = edit {
        if (value != null) it[Keys.LAST_USED_SAF_TREE_URI] = value else it.remove(Keys.LAST_USED_SAF_TREE_URI)
    }
    suspend fun updateLibrarySortOption(value: FolderSortOption) = edit { it[Keys.LIBRARY_SORT_OPTION] = value.name }
    suspend fun updateChapterPatternEnabledIds(value: Set<String>) = edit { it[Keys.CHAPTER_PATTERN_ENABLED_IDS] = value }
    suspend fun updateChapterCustomPatterns(value: Set<String>) = edit { it[Keys.CHAPTER_CUSTOM_PATTERNS] = value }
    suspend fun updateTouchZoneMode(value: TouchZoneMode) = edit { it[Keys.TOUCH_ZONE_MODE] = value.name }
    suspend fun updateGridTouchActions(value: List<PageGestureAction>) = edit {
        it[Keys.GRID_TOUCH_ACTIONS] = value.take(9).joinToString(",") { action -> action.name }
    }
    suspend fun updateGridTouchAction(index: Int, action: PageGestureAction) = edit { prefs ->
        val current = prefs[Keys.GRID_TOUCH_ACTIONS]?.let { raw ->
            val tokens = raw.split(",")
            if (tokens.size == 9) {
                tokens.map { token ->
                    runCatching { PageGestureAction.valueOf(token) }.getOrDefault(PageGestureAction.NEXT_PAGE)
                }
            } else null
        } ?: ReaderSettings.defaultGridTouchActions
        val updated = current.toMutableList()
        if (index in updated.indices) {
            updated[index] = action
        }
        prefs[Keys.GRID_TOUCH_ACTIONS] = updated.joinToString(",") { it.name }
    }
    suspend fun updateTouchLeftAction(value: PageGestureAction) = edit { it[Keys.TOUCH_LEFT_ACTION] = value.name }
    suspend fun updateTouchRightAction(value: PageGestureAction) = edit { it[Keys.TOUCH_RIGHT_ACTION] = value.name }
    suspend fun updateSwipeLeftAction(value: PageGestureAction) = edit { it[Keys.SWIPE_LEFT_ACTION] = value.name }
    suspend fun updateSwipeRightAction(value: PageGestureAction) = edit { it[Keys.SWIPE_RIGHT_ACTION] = value.name }
    suspend fun updateSwipeUpAction(value: PageGestureAction) = edit { it[Keys.SWIPE_UP_ACTION] = value.name }
    suspend fun updateSwipeDownAction(value: PageGestureAction) = edit { it[Keys.SWIPE_DOWN_ACTION] = value.name }
    suspend fun updatePageTransitionAnimation(value: PageTransitionAnimation) = edit { it[Keys.PAGE_TRANSITION_ANIMATION] = value.name }
    /** When [verifiedSecret] is also passed (on a successful connection test), the secret and its verified state are saved together in a single commit. */
    suspend fun updateSupabaseSharedSecret(value: String, verifiedSecret: String? = null) = edit {
        it[Keys.SUPABASE_SHARED_SECRET] = value
        if (verifiedSecret != null) it[Keys.SUPABASE_VERIFIED_SECRET] = verifiedSecret
    }

    /** Sign-in result. The email is stored alongside the token so the two can never disagree. */
    suspend fun linkDropbox(refreshToken: String, accountEmail: String) = edit {
        it[Keys.DROPBOX_REFRESH_TOKEN] = refreshToken
        it[Keys.DROPBOX_ACCOUNT_EMAIL] = accountEmail
    }

    /** Only for a rotated refresh token — leaves the email and the cursor alone. */
    suspend fun updateDropboxRefreshToken(refreshToken: String) = edit {
        it[Keys.DROPBOX_REFRESH_TOKEN] = refreshToken
    }

    /**
     * Clears the cursor along with the token: a cursor describes a position in *that account's*
     * change stream, so keeping it across a sign-out would make the next account's first sync
     * incremental against a history it never had.
     */
    suspend fun unlinkDropbox() = edit {
        it.remove(Keys.DROPBOX_REFRESH_TOKEN)
        it.remove(Keys.DROPBOX_ACCOUNT_EMAIL)
        it.remove(Keys.DROPBOX_CURSOR)
        it.remove(Keys.DROPBOX_LAST_SYNC_AT_MILLIS)
    }

    suspend fun updateDropboxSyncState(cursor: String, lastSyncAtMillis: Long) = edit {
        it[Keys.DROPBOX_CURSOR] = cursor
        it[Keys.DROPBOX_LAST_SYNC_AT_MILLIS] = lastSyncAtMillis
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

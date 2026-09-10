package com.moonkata.flonovel.android.data.datastore

import com.moonkata.flonovel.android.data.parser.ChapterPatternCatalog
import com.moonkata.flonovel.android.model.FolderSortOption

enum class ThemePreset { WARM_IVORY, SEPIA_CREAM, DARK_NAVY, SOFT_GRAY, COOL_LIGHT, SOFT_DARK_BROWN, CUSTOM }
enum class PageTurnMode { HORIZONTAL_PAGE, VERTICAL_SCROLL }
enum class OrientationLock { AUTO, PORTRAIT, LANDSCAPE }
enum class AutoAdvanceMode { OFF, TIMER, TTS }

enum class TouchZoneMode { STANDARD_3_COLUMN, GRID_3X3 }

/**
 * What a page-turn gesture (touch zone or swipe direction) does. Each of the six gestures
 * (touch zones, swipe left/right/up/down) is assigned one of these independently.
 */
enum class PageGestureAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    PREVIOUS_CHAPTER_JUMP,
    NEXT_CHAPTER_JUMP,
    PREVIOUS_CHAPTER,
    NEXT_CHAPTER,
    SHOW_MENU,
    NONE,
}

/** Transition effect when turning a page. NONE: instant switch, SLIDE: both pages slide together, COVER: the new page slides over the top. */
enum class PageTransitionAnimation { NONE, SLIDE, COVER }

data class ReaderSettings(
    val fontFamilyId: String = "system_default",
    val fontSizeSp: Float = 20f,
    val lineHeightMultiplier: Float = 1.5f,
    val letterSpacingSp: Float = 0f,
    val marginHorizontalDp: Float = 16f,
    val marginTopDp: Float = 16f,
    val marginBottomDp: Float = 16f,
    val themePreset: ThemePreset = ThemePreset.WARM_IVORY,
    val customBackgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val customTextColorArgb: Int = 0xFF000000.toInt(),
    val pageTurnMode: PageTurnMode = PageTurnMode.HORIZONTAL_PAGE,
    val brightnessOverrideEnabled: Boolean = false,
    val brightnessValue: Float = 0.5f,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val keepScreenOnEnabled: Boolean = true,
    val volumeKeyPagingEnabled: Boolean = false,
    val chapterJumpDivisions: Int = 4,
    val autoAdvanceMode: AutoAdvanceMode = AutoAdvanceMode.OFF,
    val autoPageTurnIntervalSeconds: Int = 15,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val lastUsedSafTreeUri: String? = null,
    val librarySortOption: FolderSortOption = FolderSortOption.NAME_ASC,
    val chapterPatternEnabledIds: Set<String> = ChapterPatternCatalog.defaultEnabledIds,
    val chapterCustomPatterns: Set<String> = emptySet(),
    // STANDARD_3_COLUMN's left/right tap zones are intentionally fixed (previous/next page — see
    // settings_touch_zone_standard_desc) rather than user-configurable, unlike every other gesture
    // here. There used to be a touchLeftAction/touchRightAction pair for that, but the settings UI
    // that let you change them was replaced by Standard3ColumnDiagram() (a non-interactive
    // explanation) when GRID_3X3 was added, leaving those two fields impossible to reach from
    // anywhere — GRID_3X3's per-cell gridTouchActions below is the configurable equivalent now.
    val touchZoneMode: TouchZoneMode = TouchZoneMode.STANDARD_3_COLUMN,
    val gridTouchActions: List<PageGestureAction> = defaultGridTouchActions,
    val swipeLeftAction: PageGestureAction = PageGestureAction.NEXT_CHAPTER,
    val swipeRightAction: PageGestureAction = PageGestureAction.PREVIOUS_CHAPTER,
    val swipeUpAction: PageGestureAction = PageGestureAction.NEXT_CHAPTER_JUMP,
    val swipeDownAction: PageGestureAction = PageGestureAction.PREVIOUS_CHAPTER_JUMP,
    val pageTransitionAnimation: PageTransitionAnimation = PageTransitionAnimation.NONE,
    // Reading-position sync (docs 06-SYNC-STRATEGY Part A) — the Supabase URL/publishable key are
    // fine to be public anyway (RLS is the actual line of defense), so they're hardcoded in
    // SupabaseConfig; this settings class holds only the shared secret that actually needs
    // protecting (§1 "secret management" decision).
    val supabaseSharedSecret: String = "",
    /** The secret value that last passed a connection test — shown as "connected" only when it matches
     * [supabaseSharedSecret]. Changing the secret automatically makes this differ, so re-verification
     * is naturally required without any separate invalidation logic. */
    val supabaseVerifiedSecret: String = "",

    // Dropbox file sync (docs 06-SYNC-STRATEGY Part B). The phone only ever downloads.
    /** OAuth refresh token. Long-lived, so it is excluded from cloud backup — see backup_rules.xml. */
    val dropboxRefreshToken: String = "",
    /** Shown in settings so it is obvious *which* account is linked; not used for any request. */
    val dropboxAccountEmail: String = "",
    /** `list_folder` cursor. Per-device and never shared: it describes what *this* phone has seen.
     * Blank means "no incremental baseline yet", which makes the next sync a full reconciliation. */
    val dropboxCursor: String = "",
    /** Device clock, epoch millis. Display only — the delta comes from the cursor, never from time
     * comparison (a downloaded file's local mtime is "when it arrived", which re-downloads everything). */
    val dropboxLastSyncAtMillis: Long = 0L,
) {
    companion object {
        /**
         * 3x3 default grid:
         * Row 0: PREVIOUS_PAGE, SHOW_MENU, PREVIOUS_PAGE
         * Row 1: NEXT_PAGE, NEXT_PAGE, NEXT_PAGE
         * Row 2: NEXT_PAGE, NEXT_PAGE, NEXT_PAGE
         */
        val defaultGridTouchActions: List<PageGestureAction> = listOf(
            PageGestureAction.PREVIOUS_PAGE,
            PageGestureAction.SHOW_MENU,
            PageGestureAction.PREVIOUS_PAGE,
            PageGestureAction.NEXT_PAGE,
            PageGestureAction.NEXT_PAGE,
            PageGestureAction.NEXT_PAGE,
            PageGestureAction.NEXT_PAGE,
            PageGestureAction.NEXT_PAGE,
            PageGestureAction.NEXT_PAGE,
        )
    }
}

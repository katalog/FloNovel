package com.moonkata.flonovel.desktop.font

/**
 * How a font is obtained.
 *
 * [Download] carries a direct URL that returns the actual font bytes (TTF/OTF)
 * or a ZIP archive. When it is a ZIP, [zipEntryPattern] is a case-insensitive
 * substring that must appear in the entry path.
 *
 * [OfficialPage] means the vendor distributes the font only through a web page
 * or a sign-up form, so there is no stable direct-download URL. We open that
 * page and let the user install the font themselves; the app then detects it.
 *
 * [SystemOnly] means the font ships with the OS under a commercial license.
 * Repackaging or redistributing these would violate that license.
 */
sealed class FontSource {
    data class Download(
        val url: String,
        /** Non-null → the URL is a ZIP; extract the first entry whose path
         *  (case-insensitive) contains this string. */
        val zipEntryPattern: String? = null,
    ) : FontSource()

    /** Vendor has no direct-download URL — open [pageUrl] for manual install. */
    data class OfficialPage(val pageUrl: String) : FontSource()

    /** Bundled with the OS — never downloadable. */
    object SystemOnly : FontSource()
}

/**
 * One entry in the curated font list shown in Settings.
 *
 * [familyName] is the string matched against system/loaded font families and
 * stored in [com.moonkata.flonovel.desktop.library.ViewSettings.fontFamily].
 *
 * [fileName] is the on-disk file name saved under configDir()/fonts/. It must
 * not contain path separators. A ".part" suffix is appended while downloading
 * so a partial file is never treated as installed. Empty for fonts we never
 * write to disk (Group B and C).
 */
data class CatalogFont(
    val displayName: String,
    val familyName: String,
    val fileName: String,
    val source: FontSource,
    val license: String,
    /** Why a font is not directly downloadable. Shown in the UI. */
    val note: String = "",
)

/**
 * The curated font list shown in Settings — 13 entries in three groups.
 *
 * ── Group A — direct download (6) ──────────────────────────────────────────
 * Every URL below was verified on 2026-09-08 by fetching the leading bytes and
 * checking the file magic (00010000 = TTF, 4F54544F = OTF, 504B0304 = ZIP),
 * with a deliberately invalid URL as a control to confirm what failure looks
 * like. URLs live here alone so a future breakage is a one-place edit.
 *
 * ⚠️ Do NOT use `https://fonts.google.com/download?family=X`. That is the web
 * page, not a download endpoint: it returns 200 with `text/html` for ANY family
 * name, including ones that do not exist. An earlier revision of this file used
 * it for six fonts and would have silently saved 193 KB of HTML as `.ttf`. The
 * correct source for Google Fonts files is the `google/fonts` repository.
 *
 * ── Group C — official page only (4) ──────────────────────────────────────
 * Naver (나눔바른고딕 · 마루부리), the Korean Publishers Association
 * (KoPubWorld 바탕체) and RIDI (리디바탕) distribute through web pages or
 * sign-up forms with no stable direct URL. Both `google/fonts` and the `naver`
 * GitHub organisation were searched on 2026-09-08 — none of the four is there.
 *
 * Third-party mirrors (e.g. `fonts-archive` repos) are deliberately NOT used: they
 * are personal repositories rather than vendor distribution, and can disappear
 * or serve altered files.
 *
 * ── Group B — system-only (3) ─────────────────────────────────────────────
 * Georgia / Palatino Linotype / MS Gothic ship with Windows or macOS under
 * commercial licenses. Shown when installed, otherwise reported as unavailable.
 */
object FontCatalog {

    // ── Group A URLs — edit here when one breaks ───────────────────────────

    /** GitHub release asset. ZIP → public/static/Pretendard-Regular.otf */
    private const val PRETENDARD_URL =
        "https://github.com/orioncactus/pretendard/releases/download/v1.3.9/Pretendard-1.3.9.zip"

    /** Upstream repository behind fonts.google.com; serves raw font files. */
    private const val GOOGLE_FONTS_RAW =
        "https://raw.githubusercontent.com/google/fonts/main/ofl"

    // `[wght]` in a variable-font file name is URL-encoded as %5Bwght%5D.
    private const val NOTO_SANS_KR_URL = "$GOOGLE_FONTS_RAW/notosanskr/NotoSansKR%5Bwght%5D.ttf"
    private const val NANUM_GOTHIC_URL = "$GOOGLE_FONTS_RAW/nanumgothic/NanumGothic-Regular.ttf"
    private const val KOPUB_BATANG_URL = "$GOOGLE_FONTS_RAW/kopubbatang/KoPubBatang-Regular.ttf"
    private const val EB_GARAMOND_URL = "$GOOGLE_FONTS_RAW/ebgaramond/EBGaramond%5Bwght%5D.ttf"

    /** SIL official download server. ZIP → CharisSIL-Regular.ttf */
    private const val CHARIS_SIL_URL =
        "https://software.sil.org/downloads/r/charis/CharisSIL-6.200.zip"

    // ── Group C pages — manual install ─────────────────────────────────────

    private const val NAVER_FONT_PAGE = "https://hangeul.naver.com/font"
    private const val KOPUS_FONT_PAGE = "https://www.kopus.org/biz-electronic-font/"
    private const val RIDI_FONT_PAGE = "https://ridicorp.com/story/ridibatang/"

    val fonts: List<CatalogFont> = listOf(

        // ── Group A — direct download ──────────────────────────────────────

        CatalogFont(
            displayName = "프리텐다드 (Pretendard)",
            familyName = "Pretendard",
            fileName = "Pretendard-Regular.otf",
            source = FontSource.Download(
                url = PRETENDARD_URL,
                zipEntryPattern = "public/static/Pretendard-Regular.otf",
            ),
            license = "OFL 1.1",
        ),

        CatalogFont(
            displayName = "본고딕 (Noto Sans KR)",
            familyName = "Noto Sans KR",
            fileName = "NotoSansKR-Variable.ttf",
            source = FontSource.Download(url = NOTO_SANS_KR_URL),
            license = "OFL 1.1",
        ),

        CatalogFont(
            displayName = "나눔고딕",
            familyName = "Nanum Gothic",
            fileName = "NanumGothic-Regular.ttf",
            source = FontSource.Download(url = NANUM_GOTHIC_URL),
            license = "OFL 1.1",
        ),

        CatalogFont(
            displayName = "KoPub 바탕체",
            familyName = "KoPub Batang",
            fileName = "KoPubBatang-Regular.ttf",
            source = FontSource.Download(url = KOPUB_BATANG_URL),
            license = "OFL 1.1 (google/fonts 동봉 OFL.txt)",
        ),

        CatalogFont(
            displayName = "EB Garamond",
            familyName = "EB Garamond",
            fileName = "EBGaramond-Variable.ttf",
            source = FontSource.Download(url = EB_GARAMOND_URL),
            license = "OFL 1.1",
        ),

        CatalogFont(
            displayName = "Charis SIL",
            familyName = "Charis SIL",
            fileName = "CharisSIL-Regular.ttf",
            source = FontSource.Download(
                url = CHARIS_SIL_URL,
                zipEntryPattern = "CharisSIL-Regular.ttf",
            ),
            license = "OFL 1.1",
        ),

        // ── Group C — official page only ───────────────────────────────────
        // No stable direct URL. We open the vendor page; the user installs the
        // font and the app detects it on the next check.

        CatalogFont(
            displayName = "나눔바른고딕",
            familyName = "NanumBarunGothic",
            fileName = "",
            source = FontSource.OfficialPage(NAVER_FONT_PAGE),
            license = "네이버 나눔글꼴 라이선스",
            note = "네이버가 웹페이지로만 배포합니다. 내려받아 설치하면 앱이 인식합니다.",
        ),

        CatalogFont(
            displayName = "마루부리 (MaruBuri)",
            familyName = "MaruBuri",
            fileName = "",
            source = FontSource.OfficialPage(NAVER_FONT_PAGE),
            license = "네이버 마루부리 라이선스",
            note = "네이버가 웹페이지로만 배포합니다. 내려받아 설치하면 앱이 인식합니다.",
        ),

        CatalogFont(
            displayName = "KoPubWorld 바탕체",
            familyName = "KoPubWorld Batang",
            fileName = "",
            source = FontSource.OfficialPage(KOPUS_FONT_PAGE),
            license = "대한출판문화협회",
            note = "신청 양식을 거쳐야 내려받을 수 있습니다. 설치하면 앱이 인식합니다.",
        ),

        CatalogFont(
            displayName = "리디바탕",
            familyName = "RIDIBatang",
            fileName = "",
            source = FontSource.OfficialPage(RIDI_FONT_PAGE),
            license = "RIDI 자체 약관 (OFL 아님)",
            note = "직접 내려받는 주소가 없고 재배포 조건이 확인되지 않아 자동 설치를 지원하지 않습니다.",
        ),

        // ── Group B — system-only ──────────────────────────────────────────
        // Never written to disk, never downloaded.

        CatalogFont(
            displayName = "Georgia",
            familyName = "Georgia",
            fileName = "",
            source = FontSource.SystemOnly,
            license = "상용 (Monotype) — 재배포 불가",
            note = "Windows·macOS 기본 포함 폰트입니다.",
        ),

        CatalogFont(
            displayName = "Palatino Linotype",
            familyName = "Palatino Linotype",
            fileName = "",
            source = FontSource.SystemOnly,
            license = "상용 (Monotype) — 재배포 불가",
            note = "Windows 기본 포함 폰트입니다. macOS 에서는 'Palatino'.",
        ),

        CatalogFont(
            displayName = "MS Gothic",
            familyName = "MS Gothic",
            fileName = "",
            source = FontSource.SystemOnly,
            license = "상용 (Ricoh) — 재배포 불가",
            note = "Windows 기본 포함 폰트입니다.",
        ),
    )

    /** Group A only — the entries the app can install by itself. */
    val downloadable: List<CatalogFont>
        get() = fonts.filter { it.source is FontSource.Download }
}

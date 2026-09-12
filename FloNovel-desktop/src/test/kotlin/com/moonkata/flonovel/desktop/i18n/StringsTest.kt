package com.moonkata.flonovel.desktop.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringsTest {

    @Test
    fun testDefaultStringsLoaded() {
        val keys = Strings.getAllDefaultKeys()
        assertTrue(keys.isNotEmpty(), "Default strings should not be empty")
        assertTrue(keys.contains("app_name"), "Should contain app_name")
        assertTrue(keys.contains("library_title"), "Should contain library_title")
        assertTrue(keys.contains("settings_title"), "Should contain settings_title")
    }

    @Test
    fun testKoreanStringsLoaded() {
        val koKeys = Strings.getKeysForLanguage("ko")
        assertTrue(koKeys.isNotEmpty(), "Korean strings should not be empty")
        assertTrue(koKeys.contains("app_name"), "Should contain app_name")
        assertTrue(koKeys.contains("library_title"), "Should contain library_title")
    }

    @Test
    fun testKeyParityBetweenEnglishAndKorean() {
        val defaultKeys = Strings.getAllDefaultKeys()
        val koKeys = Strings.getKeysForLanguage("ko")

        val missingInKorean = defaultKeys - koKeys
        assertTrue(
            missingInKorean.isEmpty(),
            "All keys in default strings.xml should exist in values-ko/strings.xml. Missing: $missingInKorean"
        )
    }

    @Test
    fun testLocaleSwitching() {
        val originalLocale = Strings.currentLocale
        try {
            // Test Korean
            Strings.setLocale(Locale.KOREAN)
            assertEquals("서재 (Library)", Strings.get("library_title"))
            assertEquals("안읽음", Strings.get("library_unread"))

            // Test English
            Strings.setLocale(Locale.ENGLISH)
            assertEquals("Library", Strings.get("library_title"))
            assertEquals("Unread", Strings.get("library_unread"))
        } finally {
            Strings.setLocale(originalLocale)
        }
    }

    @Test
    fun testFormattedArguments() {
        val originalLocale = Strings.currentLocale
        try {
            Strings.setLocale(Locale.KOREAN)
            assertEquals("전처리 실패: 3건", Strings.get("library_preprocess_failed_title", 3))

            Strings.setLocale(Locale.ENGLISH)
            assertEquals("Preprocessing failed: 3 items", Strings.get("library_preprocess_failed_title", 3))
        } finally {
            Strings.setLocale(originalLocale)
        }
    }

    @Test
    fun testFallbackToDefaultWhenKeyMissingInKorean() {
        // XML test parser with synthetic stream
        val xml = """
            <resources>
                <string name="test_key">Hello World</string>
            </resources>
        """.trimIndent()
        val map = Strings.parseStringsXml(xml.byteInputStream())
        assertEquals("Hello World", map["test_key"])
    }

    @Test
    fun testApplyLanguage() {
        val originalLocale = Strings.currentLocale
        try {
            Strings.applyLanguage("KO")
            assertEquals("ko", Strings.currentLocale.language.lowercase())
            assertEquals("언어 (Language)", Strings.get("settings_language_title"))
            assertEquals("한국어", Strings.get("settings_language_ko"))

            Strings.applyLanguage("EN")
            assertEquals("en", Strings.currentLocale.language.lowercase())
            assertEquals("Language", Strings.get("settings_language_title"))
            assertEquals("Korean", Strings.get("settings_language_ko"))

            Strings.applyLanguage("SYSTEM")
            assertEquals(Locale.getDefault().language.lowercase(), Strings.currentLocale.language.lowercase())
        } finally {
            Strings.setLocale(originalLocale)
        }
    }

    @Test
    fun testFontCustomizationStringsParityAndSwitching() {
        val originalLocale = Strings.currentLocale
        try {
            val fontKeys = listOf(
                "settings_font_weight_title",
                "settings_font_weight_300",
                "settings_font_weight_350",
                "settings_font_weight_400",
                "settings_font_weight_500",
                "settings_font_weight_700",
                "settings_font_tab_recommended",
                "settings_font_tab_custom",
                "settings_font_tab_system",
                "settings_font_folder_open",
                "settings_font_folder_refresh",
                "settings_font_folder_hint",
                "settings_font_search_hint",
                "settings_font_custom_empty",
                "settings_font_category_serif",
                "settings_font_category_sans",
                "settings_font_category_latin",
                "settings_preview_title",
                "settings_preview_sample_text",
            )

            // Test Korean
            Strings.applyLanguage("KO")
            for (key in fontKeys) {
                val value = Strings.get(key)
                assertFalse(value.startsWith("!"), "Key '$key' missing in Korean: $value")
                assertTrue(value.isNotBlank(), "Key '$key' blank in Korean")
            }
            assertEquals("폰트 굵기 (Weight)", Strings.get("settings_font_weight_title"))
            assertEquals("추천 글꼴", Strings.get("settings_font_tab_recommended"))
            assertTrue(Strings.get("settings_preview_sample_text").contains("The quick brown fox"))
            assertTrue(Strings.get("settings_preview_sample_text").contains("1234567890"))
            assertTrue(Strings.get("settings_preview_sample_text").contains("달빛이"))

            // Test English
            Strings.applyLanguage("EN")
            for (key in fontKeys) {
                val value = Strings.get(key)
                assertFalse(value.startsWith("!"), "Key '$key' missing in English: $value")
                assertTrue(value.isNotBlank(), "Key '$key' blank in English")
            }
            assertEquals("Font Weight", Strings.get("settings_font_weight_title"))
            assertEquals("Recommended", Strings.get("settings_font_tab_recommended"))
            assertTrue(Strings.get("settings_preview_sample_text").contains("The quick brown fox"))
            assertTrue(Strings.get("settings_preview_sample_text").contains("1234567890"))
            assertTrue(Strings.get("settings_preview_sample_text").contains("달빛이"))
        } finally {
            Strings.setLocale(originalLocale)
        }
    }
}

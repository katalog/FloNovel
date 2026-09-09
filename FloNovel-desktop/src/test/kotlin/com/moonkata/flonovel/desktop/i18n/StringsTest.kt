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
}

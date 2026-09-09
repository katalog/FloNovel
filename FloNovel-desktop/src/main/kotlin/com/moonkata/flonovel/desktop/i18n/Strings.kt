package com.moonkata.flonovel.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.w3c.dom.Element
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Desktop UI String Localization (i18n) Engine.
 *
 * Implements Android-style string resource resolution:
 * - Default/fallback strings are loaded from `values/strings.xml` (English).
 * - Localized strings are loaded from `values-<lang>/strings.xml` (e.g. `values-ko/strings.xml`).
 * - Supports synchronous access from anywhere (composable UI, callbacks, background services).
 */
object Strings {
    private val defaultStrings = mutableMapOf<String, String>()
    private val localizedStrings = mutableMapOf<String, MutableMap<String, String>>()

    var currentLocale: Locale by mutableStateOf(Locale.getDefault())
        private set

    init {
        loadAll()
    }

    /**
     * Set the current UI locale and refresh string bindings.
     */
    fun setLocale(locale: Locale) {
        currentLocale = locale
    }

    /**
     * Retrieve a string by key, formatting with arguments if provided.
     */
    fun get(key: String, vararg args: Any?): String {
        val lang = currentLocale.language.lowercase()
        val template = localizedStrings[lang]?.get(key)
            ?: defaultStrings[key]
            ?: key

        if (args.isEmpty()) {
            return template
        }

        return try {
            String.format(currentLocale, template, *args)
        } catch (e: Exception) {
            template
        }
    }

    /**
     * Check if a key exists in either default or current localized resources.
     */
    fun hasKey(key: String): Boolean {
        val lang = currentLocale.language.lowercase()
        return localizedStrings[lang]?.containsKey(key) == true || defaultStrings.containsKey(key)
    }

    /**
     * Get all keys defined in default strings.
     */
    fun getAllDefaultKeys(): Set<String> = defaultStrings.keys.toSet()

    /**
     * Get all keys defined for a specific language.
     */
    fun getKeysForLanguage(lang: String): Set<String> = localizedStrings[lang.lowercase()]?.keys.orEmpty()

    internal fun reload() {
        defaultStrings.clear()
        localizedStrings.clear()
        loadAll()
    }

    private fun loadAll() {
        // 1. Load default strings from values/strings.xml
        loadFromResource("values/strings.xml")?.let { defaultStrings.putAll(it) }

        // 2. Load Korean strings from values-ko/strings.xml
        loadFromResource("values-ko/strings.xml")?.let {
            localizedStrings.getOrPut("ko") { mutableMapOf() }.putAll(it)
        }
    }

    private fun loadFromResource(path: String): Map<String, String>? {
        val classLoader = Thread.currentThread().contextClassLoader
            ?: Strings::class.java.classLoader
        val stream = classLoader.getResourceAsStream(path) ?: return null
        return parseStringsXml(stream)
    }

    internal fun parseStringsXml(inputStream: InputStream): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            // Disable DTD/external entity resolution for safety
            factory.isExpandEntityReferences = false
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {}

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)
            doc.documentElement.normalize()

            val stringNodes = doc.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                if (node is Element) {
                    val name = node.getAttribute("name")
                    if (name.isNotBlank()) {
                        val text = node.textContent
                            .replace("\\'", "'")
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                        result[name] = text
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Strings: Failed to parse XML: ${e.message}")
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
        }
        return result
    }
}

/**
 * Composable helper to resolve localized strings within Compose UI.
 */
@Composable
fun stringResource(key: String, vararg args: Any?): String {
    // Reading currentLocale ensures recomposition when locale changes
    @Suppress("UNUSED_VARIABLE")
    val locale = Strings.currentLocale
    return Strings.get(key, *args)
}

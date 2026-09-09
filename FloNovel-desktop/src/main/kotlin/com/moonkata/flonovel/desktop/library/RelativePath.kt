package com.moonkata.flonovel.desktop.library

import java.text.Normalizer

object RelativePath {
    /**
     * Normalizes a relative file path to form a book key and remote sync path.
     *
     * Strict contract order:
     * 1. Separator unification ('\' -> '/')
     * 2. Unicode NFC normalization (combining decomposed Hangul Jamo)
     * 3. Lowercasing
     */
    fun normalize(relativePath: String): String {
        val unified = relativePath.replace('\\', '/')
        val nfc = Normalizer.normalize(unified, Normalizer.Form.NFC)
        return nfc.lowercase()
    }
}

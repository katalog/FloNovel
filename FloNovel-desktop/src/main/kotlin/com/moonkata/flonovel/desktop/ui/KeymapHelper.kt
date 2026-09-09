package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Helper for keyboard shortcut matching and display names.
 */
object KeymapHelper {

    /**
     * Return user-friendly display name for a key identifier.
     */
    fun toDisplayName(keyName: String): String {
        return when (keyName.uppercase()) {
            "PERIOD" -> ">  (.)"
            "COMMA" -> "<  (,)"
            "PAGE_DOWN" -> "PgDn"
            "PAGE_UP" -> "PgUp"
            "ESCAPE" -> "ESC"
            "DIRECTION_RIGHT" -> "→"
            "DIRECTION_LEFT" -> "←"
            "DIRECTION_UP" -> "↑"
            "DIRECTION_DOWN" -> "↓"
            "SPACEBAR", "SPACE" -> "Space"
            "SLASH" -> "/"
            "RIGHT_BRACKET" -> "]"
            "LEFT_BRACKET" -> "["
            "BACKSPACE" -> "Backspace"
            "ENTER" -> "Enter"
            "TAB" -> "Tab"
            else -> keyName.uppercase()
        }
    }

    /**
     * Map a Compose [Key] and optional [codePoint] to a canonical key identifier string.
     */
    fun resolveKeyIdentifier(key: Key, codePoint: Int = 0): String {
        return when {
            key == Key.Period || codePoint == '>'.code || codePoint == '.'.code -> "PERIOD"
            key == Key.Comma || codePoint == '<'.code || codePoint == ','.code -> "COMMA"
            key == Key.PageDown -> "PAGE_DOWN"
            key == Key.PageUp -> "PAGE_UP"
            key == Key.Escape -> "ESCAPE"
            key == Key.F1 -> "F1"
            key == Key.F2 -> "F2"
            key == Key.F3 -> "F3"
            key == Key.F4 -> "F4"
            key == Key.F5 -> "F5"
            key == Key.F6 -> "F6"
            key == Key.F7 -> "F7"
            key == Key.F8 -> "F8"
            key == Key.F9 -> "F9"
            key == Key.F10 -> "F10"
            key == Key.F11 -> "F11"
            key == Key.F12 -> "F12"
            key == Key.DirectionRight -> "DIRECTION_RIGHT"
            key == Key.DirectionLeft -> "DIRECTION_LEFT"
            key == Key.DirectionUp -> "DIRECTION_UP"
            key == Key.DirectionDown -> "DIRECTION_DOWN"
            key == Key.Spacebar -> "SPACEBAR"
            key == Key.Home || key == Key.MoveHome -> "HOME"
            key == Key.MoveEnd -> "END"
            key == Key.Slash || codePoint == '/'.code -> "SLASH"
            key == Key.RightBracket || codePoint == ']'.code -> "RIGHT_BRACKET"
            key == Key.LeftBracket || codePoint == '['.code -> "LEFT_BRACKET"
            key == Key.Backspace -> "BACKSPACE"
            key == Key.Enter -> "ENTER"
            key == Key.Tab -> "TAB"
            key == Key.A -> "A"
            key == Key.B -> "B"
            key == Key.C -> "C"
            key == Key.D -> "D"
            key == Key.E -> "E"
            key == Key.F -> "F"
            key == Key.G -> "G"
            key == Key.H -> "H"
            key == Key.I -> "I"
            key == Key.J -> "J"
            key == Key.K -> "K"
            key == Key.L -> "L"
            key == Key.M -> "M"
            key == Key.N -> "N"
            key == Key.O -> "O"
            key == Key.P -> "P"
            key == Key.Q -> "Q"
            key == Key.R -> "R"
            key == Key.S -> "S"
            key == Key.T -> "T"
            key == Key.U -> "U"
            key == Key.V -> "V"
            key == Key.W -> "W"
            key == Key.X -> "X"
            key == Key.Y -> "Y"
            key == Key.Z -> "Z"
            key == Key.Zero -> "0"
            key == Key.One -> "1"
            key == Key.Two -> "2"
            key == Key.Three -> "3"
            key == Key.Four -> "4"
            key == Key.Five -> "5"
            key == Key.Six -> "6"
            key == Key.Seven -> "7"
            key == Key.Eight -> "8"
            key == Key.Nine -> "9"
            else -> {
                if (codePoint in 32..126) {
                    codePoint.toChar().uppercase()
                } else {
                    key.toString().substringAfterLast('.').substringAfterLast(':').uppercase().trim()
                }
            }
        }
    }

    /**
     * Map a Compose [KeyEvent] to a canonical key identifier string.
     */
    fun fromKeyEvent(keyEvent: KeyEvent): String {
        return resolveKeyIdentifier(keyEvent.key, keyEvent.utf16CodePoint)
    }

    /**
     * Check if a [KeyEvent] matches a configured key identifier string.
     */
    fun matches(actionKey: String, keyEvent: KeyEvent): Boolean {
        val mapped = fromKeyEvent(keyEvent)
        return mapped.equals(actionKey, ignoreCase = true)
    }

    /**
     * Check if a [Key] and [codePoint] match a configured key identifier string.
     */
    fun matches(actionKey: String, key: Key, codePoint: Int = 0): Boolean {
        val mapped = resolveKeyIdentifier(key, codePoint)
        return mapped.equals(actionKey, ignoreCase = true)
    }
}

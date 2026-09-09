package com.moonkata.flonovel.desktop.platform

import androidx.compose.ui.graphics.Color
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import java.awt.Window
import kotlin.math.roundToInt

/**
 * Native Windows Title Bar color integration using Desktop Window Manager (DWM) API.
 * Supported on Windows 11 (Build 22000+) for caption/text colors, and Windows 10 (1903+) for immersive dark mode.
 * Safe no-op on non-Windows platforms or earlier Windows versions.
 */
object WindowsTitleBar {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private interface DwmapiLib : Library {
        fun DwmSetWindowAttribute(
            hwnd: HWND,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int,
        ): Int
    }

    private val dwmapi: DwmapiLib? by lazy {
        try {
            val osName = System.getProperty("os.name")?.lowercase() ?: ""
            if (osName.contains("windows")) {
                Native.load("dwmapi", DwmapiLib::class.java)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun toColorRef(color: Color): Int {
        val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
        // COLORREF is 0x00BBGGRR
        return r or (g shl 8) or (b shl 16)
    }

    fun isDarkColor(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance < 0.5f
    }

    fun updateTitleBarColor(window: Window?, backgroundColor: Color, textColor: Color) {
        val lib = dwmapi ?: return
        if (window == null || !window.isDisplayable) return

        try {
            val pointer = Native.getWindowPointer(window) ?: return
            val hwnd = HWND(pointer)

            // 1. Immersive dark mode (controls window controls icons: minimize/maximize/close)
            val isDark = if (isDarkColor(backgroundColor)) 1 else 0
            val darkRef = IntByReference(isDark)
            lib.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, darkRef.pointer, 4)

            // 2. Caption (Title bar background) color - Windows 11 Build 22000+
            val captionColorRef = IntByReference(toColorRef(backgroundColor))
            lib.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, captionColorRef.pointer, 4)

            // 3. Text (Title bar font) color - Windows 11 Build 22000+
            val textColorRef = IntByReference(toColorRef(textColor))
            lib.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, textColorRef.pointer, 4)
        } catch (_: Throwable) {
            // Silently ignore on unsupported Windows builds
        }
    }
}

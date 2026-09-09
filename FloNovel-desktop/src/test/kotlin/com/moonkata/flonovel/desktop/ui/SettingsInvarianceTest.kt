package com.moonkata.flonovel.desktop.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.moonkata.flonovel.desktop.library.ViewSettings
import com.moonkata.flonovel.desktop.reader.PaneMode
import com.moonkata.flonovel.desktop.reader.ReaderNavigator
import com.moonkata.flonovel.desktop.reader.ViewportSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsInvarianceTest {

    private fun createTextMeasurer(): androidx.compose.ui.text.TextMeasurer {
        return androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
    }

    private fun makeSampleText(lineCount: Int = 200): String {
        return (1..lineCount).joinToString("\n") {
            "Line %03d: Reading position must remain strictly invariant across all typography settings changes.".format(it)
        } + "\n"
    }

    // --- U6 Scenario: Settings change preserves anchor and progress percentage ---

    @Test
    fun u6_changingFontSize_strictlyPreservesAnchorAndProgress() {
        val text = makeSampleText(150)
        val measurer = createTextMeasurer()
        val initialStyle = TextStyle(fontSize = 18.sp, lineHeight = 26.sp)
        val initialFitter = ComposeTextFitter(text, measurer, initialStyle)

        val spec = ViewportSpec(widthPx = 600, heightPx = 500, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, initialFitter, spec, initialAnchor = 0)

        // Advance to a mid-book anchor
        navigator.advance(0.5f)
        navigator.advance(0.5f)
        val targetAnchor = navigator.anchor
        val targetProgress = navigator.progress()
        val targetFormattedProgress = ProgressFormatter.format(targetProgress)
        assertTrue(targetAnchor > 0)

        // User changes font size to 30sp (much larger)
        val newStyle = TextStyle(fontSize = 30.sp, lineHeight = 42.sp)
        val newFitter = ComposeTextFitter(text, measurer, newStyle)

        // Handle layout key change
        val newState = navigator.onLayoutKeyChanged(spec, newFitter)

        // CRITICAL INVARIANT: Anchor and progress MUST NOT CHANGE
        assertEquals(targetAnchor, navigator.anchor, "Anchor must NEVER change when font size changes")
        assertEquals(targetAnchor, newState.anchor)
        assertEquals(targetProgress, navigator.progress(), "Raw progress ratio must remain identical")
        assertEquals(targetFormattedProgress, ProgressFormatter.format(navigator.progress()), "Formatted progress % must remain identical")

        // Screen starts with the exact same character at targetAnchor
        assertEquals(targetAnchor, newState.layout.primaryPane.startOffset)
    }

    @Test
    fun u6_changingLineHeight_strictlyPreservesAnchorAndProgress() {
        val text = makeSampleText(150)
        val measurer = createTextMeasurer()
        val initialStyle = TextStyle(fontSize = 18.sp, lineHeight = 22.sp)
        val initialFitter = ComposeTextFitter(text, measurer, initialStyle)

        val spec = ViewportSpec(widthPx = 600, heightPx = 500, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, initialFitter, spec, initialAnchor = 400)

        val targetAnchor = navigator.anchor
        val targetProgress = navigator.progress()

        // Change line height multiplier to 2.5x
        val newStyle = TextStyle(fontSize = 18.sp, lineHeight = 45.sp)
        val newFitter = ComposeTextFitter(text, measurer, newStyle)
        val newState = navigator.onLayoutKeyChanged(spec, newFitter)

        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetProgress, navigator.progress())
        assertEquals(targetAnchor, newState.layout.primaryPane.startOffset)
    }

    @Test
    fun u6_changingLetterSpacing_strictlyPreservesAnchorAndProgress() {
        val text = makeSampleText(150)
        val measurer = createTextMeasurer()
        val initialStyle = TextStyle(fontSize = 18.sp, letterSpacing = 0.sp)
        val initialFitter = ComposeTextFitter(text, measurer, initialStyle)

        val spec = ViewportSpec(widthPx = 600, heightPx = 500, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, initialFitter, spec, initialAnchor = 350)

        val targetAnchor = navigator.anchor
        val targetProgress = navigator.progress()

        // Change letter spacing to 3sp
        val newStyle = TextStyle(fontSize = 18.sp, letterSpacing = 3.sp)
        val newFitter = ComposeTextFitter(text, measurer, newStyle)
        val newState = navigator.onLayoutKeyChanged(spec, newFitter)

        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetProgress, navigator.progress())
        assertEquals(targetAnchor, newState.layout.primaryPane.startOffset)
    }

    @Test
    fun u6_changingMargins_strictlyPreservesAnchorAndProgress() {
        val text = makeSampleText(150)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 18.sp, lineHeight = 26.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val initialSpec = ViewportSpec(widthPx = 600, heightPx = 500, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, initialSpec, initialAnchor = 600)

        val targetAnchor = navigator.anchor
        val targetProgress = navigator.progress()

        // Margins increased significantly -> content width/height decreased
        val newSpec = ViewportSpec(widthPx = 400, heightPx = 350, paneMode = PaneMode.ONE)
        val newState = navigator.onLayoutKeyChanged(newSpec, fitter)

        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetProgress, navigator.progress())
        assertEquals(targetAnchor, newState.layout.primaryPane.startOffset)
    }

    @Test
    fun u6_changingTheme_strictlyPreservesAnchorAndProgress() {
        val text = makeSampleText(150)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 18.sp, lineHeight = 26.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec = ViewportSpec(widthPx = 600, heightPx = 500, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, fitter, spec, initialAnchor = 550)

        val targetAnchor = navigator.anchor
        val targetProgress = navigator.progress()

        // Theme change (all 6 themes and legacy aliases) only changes colors, layout spec is unchanged
        listOf(
            "WARM_IVORY", "SEPIA_CREAM", "DARK_NAVY", "SOFT_GRAY", "COOL_LIGHT", "SOFT_DARK_BROWN",
            "LIGHT", "DARK", "SEPIA"
        ).forEach { theme ->
            val colors = ReaderColors.forName(theme)
            assertTrue(colors.background.value != 0UL)
            assertEquals(targetAnchor, navigator.anchor, "Theme change ($theme) must never modify anchor")
            assertEquals(targetProgress, navigator.progress(), "Theme change ($theme) must never modify progress")
        }
    }

    @Test
    fun u6_changingPaneMode_strictlyPreservesAnchorAndProgress() {
        val text = makeSampleText(200)
        val measurer = createTextMeasurer()
        val style = TextStyle(fontSize = 18.sp, lineHeight = 26.sp)
        val fitter = ComposeTextFitter(text, measurer, style)

        val spec1Pane = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.ONE)
        val spec2Pane = ViewportSpec(widthPx = 800, heightPx = 500, paneMode = PaneMode.TWO, gutterPx = 24)

        val navigator = ReaderNavigator(text.length, fitter, spec1Pane, initialAnchor = 750)
        val targetAnchor = navigator.anchor
        val targetProgress = navigator.progress()

        // 1-pane -> 2-pane
        val state2 = navigator.onLayoutKeyChanged(spec2Pane, fitter)
        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetProgress, navigator.progress())
        assertEquals(targetAnchor, state2.layout.rightPane?.startOffset)

        // 2-pane -> 1-pane
        val state1 = navigator.onLayoutKeyChanged(spec1Pane, fitter)
        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetProgress, navigator.progress())
        assertEquals(targetAnchor, state1.layout.primaryPane.startOffset)
    }

    // --- System Font Helpers ---

    @Test
    fun availableSystemFonts_startsWithSystemAndIsNotEmpty() {
        val fonts = getAvailableSystemFonts()
        assertTrue(fonts.isNotEmpty())
        assertEquals("system", fonts[0])
    }

    @Test
    fun resolveFontFamily_gracefulFallback() {
        assertEquals(FontFamily.Default, resolveFontFamily(""))
        assertEquals(FontFamily.Default, resolveFontFamily("system"))
        assertEquals(FontFamily.Default, resolveFontFamily("default"))
        assertEquals(FontFamily.Default, resolveFontFamily("   "))
        assertEquals(FontFamily.Default, resolveFontFamily("NonExistentFont_XYZ_12345"))
    }

    @Test
    fun resolveFontFamily_msUiGothicAndRendering() {
        val measurer = createTextMeasurer()
        val ff1 = resolveFontFamily("MS UI Gothic")
        assertNotNull(ff1)

        // Verifies measuring text with resolved font succeeds without throwing FontLoadFailedException
        val layoutResult1 = measurer.measure(
            text = androidx.compose.ui.text.AnnotatedString("테스트 텍스트"),
            style = androidx.compose.ui.text.TextStyle(fontFamily = ff1, fontSize = 16.sp)
        )
        assertTrue(layoutResult1.size.width > 0)
        assertTrue(layoutResult1.size.height > 0)

        // Case-insensitivity check (e.g. "ms ui gothic")
        val ff2 = resolveFontFamily("ms ui gothic")
        assertNotNull(ff2)
        val layoutResult2 = measurer.measure(
            text = androidx.compose.ui.text.AnnotatedString("테스트 텍스트"),
            style = androidx.compose.ui.text.TextStyle(fontFamily = ff2, fontSize = 16.sp)
        )
        assertTrue(layoutResult2.size.width > 0)
    }

    @Test
    fun u6_changingUiScale_strictlyPreservesAnchorAndProgress() {
        val text = makeSampleText(150)
        val measurer = createTextMeasurer()
        val initialStyle = TextStyle(fontSize = 18.sp, lineHeight = 26.sp)
        val initialFitter = ComposeTextFitter(text, measurer, initialStyle)

        val spec = ViewportSpec(widthPx = 600, heightPx = 500, paneMode = PaneMode.ONE)
        val navigator = ReaderNavigator(text.length, initialFitter, spec, initialAnchor = 500)

        val targetAnchor = navigator.anchor
        val targetProgress = navigator.progress()

        // Simulating UI scale change to 1.5x (fitter with scaled style)
        val scaledStyle = TextStyle(fontSize = (18 * 1.5f).sp, lineHeight = (26 * 1.5f).sp)
        val scaledFitter = ComposeTextFitter(text, measurer, scaledStyle)
        val newState = navigator.onLayoutKeyChanged(spec, scaledFitter)

        assertEquals(targetAnchor, navigator.anchor)
        assertEquals(targetProgress, navigator.progress())
        assertEquals(targetAnchor, newState.layout.primaryPane.startOffset)
    }
}


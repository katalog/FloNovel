package com.moonkata.flonovel.android

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A minimal smoke test confirming the app actually launches and reaches the library screen
 * without crashing. Rather than forcing a reset to a specific state, it accepts either of the two
 * shapes the library screen can take, so it runs reliably regardless of environment (another
 * developer's PC, state left by a previous test):
 *
 * - no home folder yet  -> the "Add folder" FAB
 * - a home folder set   -> the top bar's action icons, of which Settings is always present
 *
 * The FAB used to be shown in both states (labelled "Change folder" in the second), so checking it
 * alone was enough. It is now offered only while there is no folder to browse — changing an
 * existing one moved into the settings sheet — which is why the second case keys off the top bar.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_libraryScaffoldRenders() {
        // ExtendedFloatingActionButton does not merge its icon+text into a single merged semantics
        // node, so the default lookup against the merged tree can't find the text inside it — look
        // it up in the unmerged tree instead.
        val addFolderLabel = composeTestRule.activity.getString(R.string.library_add_folder)
        val settingsDesc = composeTestRule.activity.getString(R.string.library_settings_desc)
        composeTestRule
            .onNode(hasText(addFolderLabel) or hasContentDescription(settingsDesc), useUnmergedTree = true)
            .assertExists()
    }
}

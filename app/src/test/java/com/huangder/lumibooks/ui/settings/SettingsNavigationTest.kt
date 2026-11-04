package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
class SettingsNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movedLinksOpenTheRightPageAndSection() {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi") {
                Column {
                    MovedSettingsLink("Reading layout?", category = "reading", targetSection = "reading_layout")
                    MovedSettingsLink("Listening?", category = "listening_preferences")
                    MovedSettingsLink("Glass?", category = "visual_motion", targetSection = "visual_glass")
                }
            }
        }
        val app = shadowOf(RuntimeEnvironment.getApplication())
        compose.onNodeWithText("Reading layout?").performClick()
        app.nextStartedActivity.let {
            assertEquals(DetailActivity::class.java.name, it.component?.className)
            assertEquals("reading", it.getStringExtra("category"))
            assertEquals("reading_layout", it.getStringExtra("target_section"))
        }
        compose.onNodeWithText("Listening?").performClick()
        app.nextStartedActivity.let {
            assertEquals("listening_preferences", it.getStringExtra("category"))
            assertNull(it.getStringExtra("target_section"))
        }
        compose.onNodeWithText("Glass?").performClick()
        app.nextStartedActivity.let {
            assertEquals("visual_motion", it.getStringExtra("category"))
            assertEquals("visual_glass", it.getStringExtra("target_section"))
        }
    }

    @Test fun targetSectionStopsBelowToolbarAndDoesNotReapplyAfterScrolling() {
        var revision by mutableIntStateOf(0)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(LocalSettingsTargetSection provides "destination") {
                    CollapsingSettingsScaffold("Settings", {}) {
                        Spacer(Modifier.height(1000.dp))
                        SettingsSectionTitle("Destination $revision", "destination")
                        Spacer(Modifier.height(1000.dp))
                    }
                }
            }
        }
        compose.onNodeWithTag("settings-section-destination").assertIsDisplayed()
        val sectionTop = compose.onNodeWithTag("settings-section-destination").fetchSemanticsNode().boundsInRoot.top
        val toolbarBottom = compose.onNodeWithTag("settings-toolbar").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Target must clear the toolbar", sectionTop >= toolbarBottom - 1f)
        assertTrue("Target should align near toolbar", sectionTop < toolbarBottom + 40f)
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        val afterScroll = compose.onNodeWithTag("settings-section-destination").getUnclippedBoundsInRoot().top
        compose.runOnIdle { revision++ }
        assertEquals(afterScroll, compose.onNodeWithTag("settings-section-destination").getUnclippedBoundsInRoot().top)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(afterScroll, compose.onNodeWithTag("settings-section-destination").getUnclippedBoundsInRoot().top)
    }
}

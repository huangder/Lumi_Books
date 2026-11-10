package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w1280dp-h800dp-mdpi")
class AdaptiveSettingsOverviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabletUsesColumnsAndWindowResizeRestoresStackedContent() {
        var width by mutableStateOf(1280.dp)
        var height by mutableStateOf(800.dp)
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                Box(Modifier.width(width).height(height)) {
                    AdaptiveSettingsOverview({}, { Text("Profile") }, { Text("Preferences") })
                }
            }
        }
        compose.onNodeWithTag("settings-two-columns").assertIsDisplayed()
        val left = compose.onNodeWithTag("settings-overview").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("settings-preferences").fetchSemanticsNode().boundsInRoot
        assertTrue("Columns must not overlap", left.right <= right.left)
        assertTrue("Columns must align at the top", left.top == right.top)
        val toolbar = compose.onNodeWithTag("settings-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("Toolbar should align with centered content", toolbar.left == left.left)

        compose.runOnIdle { width = 600.dp }
        compose.onNodeWithTag("settings-single-column").assertIsDisplayed()
        compose.onNodeWithTag("settings-two-columns").assertDoesNotExist()
        val top = compose.onNodeWithTag("settings-overview").fetchSemanticsNode().boundsInRoot
        val bottom = compose.onNodeWithTag("settings-preferences").fetchSemanticsNode().boundsInRoot
        assertTrue("Compact content must stack without overlap", top.bottom <= bottom.top)

        // A wide but short phone window must retain a readable single column.
        compose.runOnIdle { width = 1000.dp; height = 400.dp }
        compose.onNodeWithTag("settings-single-column").assertIsDisplayed()
        compose.runOnIdle { height = 800.dp }
        compose.onNodeWithTag("settings-two-columns").assertIsDisplayed()
    }
}

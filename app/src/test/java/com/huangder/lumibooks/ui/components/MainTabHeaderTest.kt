package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home, Library and Statistics share this header; their large titles must sit at one height. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w393dp-h852dp-mdpi")
class MainTabHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titleTopIsTheSameWithOrWithoutEyebrowAndTrailingControls() {
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                Column {
                    MainTabHeader(eyebrow = "Tuesday, November 4", includeStatusBar = false,
                        modifier = Modifier.testTag("withEyebrow")) {
                        MainTabTitle("Home", Modifier.testTag("titleA"))
                    }
                    MainTabHeader(eyebrow = null, includeStatusBar = false, modifier = Modifier.testTag("plain"),
                        trailing = { LiquidGlassIconButton(com.huangder.lumibooks.ui.icons.AppIcons.PencilSimple, null, {}, size = 40.dp) }) {
                        MainTabTitle("Library", Modifier.testTag("titleB"))
                    }
                }
            }
        }
        val headerA = compose.onNodeWithTag("withEyebrow").getBoundsInRoot()
        val headerB = compose.onNodeWithTag("plain").getBoundsInRoot()
        val titleA = compose.onNodeWithTag("titleA").getBoundsInRoot()
        val titleB = compose.onNodeWithTag("titleB").getBoundsInRoot()
        val offsetA = titleA.top - headerA.top
        val offsetB = titleB.top - headerB.top
        assertEquals(offsetA.value, offsetB.value, 1f)
    }
}

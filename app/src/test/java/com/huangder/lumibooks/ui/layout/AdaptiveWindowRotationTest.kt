package com.huangder.lumibooks.ui.layout

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.components.FloatingTabBar
import com.huangder.lumibooks.ui.components.adaptiveSheetFrame
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1280dp-h1280dp-mdpi")
class AdaptiveWindowRotationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun navigationAndSheetsFollowTheLiveWindowWhenResourceDimensionsAreStale() {
        val window = object : WindowInfo {
            override val isWindowFocused = true
            override var containerDpSize by mutableStateOf(DpSize(400.dp, 900.dp))
        }
        lateinit var observed: AdaptiveWindowInfo
        compose.setContent {
            // Model a locale-wrapped context whose Configuration still describes portrait.
            val staleConfig = android.content.res.Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 400
                screenHeightDp = 900
            }
            CompositionLocalProvider(LocalConfiguration provides staleConfig, LocalWindowInfo provides window) {
                EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                    observed = currentAdaptiveWindowInfo()
                    Box(Modifier.size(window.containerDpSize).testTag("root")) {
                        Box(adaptiveSheetFrame(heightFraction = 0.3f).testTag("sheet"))
                        FloatingTabBar(selectedIndex = 0, onTabSelected = {}, vertical = observed.usesSideChrome)
                    }
                }
            }
        }

        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertFalse(observed.usesSideChrome)
            assertFalse(observed.usesCardSheets)
        }
        assertEquals(bounds("root").bottom, bounds("sheet").bottom, 1f)
        assertTrue(bounds("home_tab").center.x < bounds("statistics_tab").center.x)

        compose.runOnIdle { window.containerDpSize = DpSize(900.dp, 400.dp) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(900, observed.widthDp)
            assertTrue(observed.usesSideChrome)
            assertTrue(observed.usesCardSheets)
        }
        assertEquals(bounds("root").center.y, bounds("sheet").center.y, 1f)
        assertTrue(bounds("home_tab").center.y < bounds("statistics_tab").center.y)

        compose.runOnIdle { window.containerDpSize = DpSize(400.dp, 900.dp) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(400, observed.widthDp)
            assertFalse(observed.usesSideChrome)
            assertFalse(observed.usesCardSheets)
        }
        assertEquals(bounds("root").bottom, bounds("sheet").bottom, 1f)
        assertTrue(bounds("home_tab").center.x < bounds("statistics_tab").center.x)
    }
}

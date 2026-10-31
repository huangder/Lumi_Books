package com.huangder.lumibooks.ui.layout

import android.app.Application
import android.content.res.Configuration
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1280dp-h1280dp-mdpi")
class AdaptiveWindowFoldTest {
    @get:Rule val compose = createComposeRule()

    private class LiveWindow(initialSize: DpSize) : WindowInfo {
        override val isWindowFocused = true
        override var containerDpSize by mutableStateOf(initialSize)
    }

    private lateinit var window: LiveWindow
    private lateinit var observed: AdaptiveWindowInfo

    private fun mount(width: Int, height: Int, smallestWidth: Int) {
        window = LiveWindow(DpSize(width.dp, height.dp))
        compose.setContent {
            // Keep every Configuration size stale while only the live host window changes.
            val staleConfiguration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = width
                screenHeightDp = height
                smallestScreenWidthDp = smallestWidth
            }
            CompositionLocalProvider(
                LocalConfiguration provides staleConfiguration,
                LocalWindowInfo provides window
            ) {
                EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                    observed = currentAdaptiveWindowInfo()
                    Box(Modifier.size(window.containerDpSize).testTag("root")) {
                        Box(adaptiveSheetFrame(heightFraction = 0.7f).testTag("sheet"))
                        FloatingTabBar(
                            selectedIndex = 0,
                            onTabSelected = {},
                            vertical = observed.usesSideChrome
                        )
                    }
                }
            }
        }
    }

    private fun assertLayout(width: Int, height: Int, card: Boolean, sideNavigation: Boolean) {
        compose.runOnIdle { window.containerDpSize = DpSize(width.dp, height.dp) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(width, observed.widthDp)
            assertEquals(height, observed.heightDp)
            assertEquals(card, observed.isMediumWidthOrLarger)
            assertEquals(card, observed.usesCardSheets)
            assertEquals(sideNavigation, observed.usesSideChrome)
            assertEquals(sideNavigation, observed.usesWideLayout)
        }

        fun bounds(tag: String) =
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        val root = bounds("root")
        val sheet = bounds("sheet")
        assertEquals(width.toFloat(), root.width, 1f)
        assertEquals(height.toFloat(), root.height, 1f)
        assertEquals(root.center.x, sheet.center.x, 1f)
        if (card) {
            assertEquals(root.center.y, sheet.center.y, 1f)
            assertEquals(480f, sheet.width, 1f)
            assertTrue(sheet.top >= root.top + 24f)
            assertTrue(sheet.bottom <= root.bottom - 24f)
        } else {
            assertEquals(root.bottom, sheet.bottom, 1f)
            assertEquals(root.width, sheet.width, 1f)
        }

        val home = bounds("home_tab")
        val statistics = bounds("statistics_tab")
        if (sideNavigation) {
            assertTrue(home.center.y < statistics.center.y)
            assertEquals(home.center.x, statistics.center.x, 1f)
        } else {
            assertTrue(home.center.x < statistics.center.x)
            assertEquals(home.center.y, statistics.center.y, 1f)
        }
    }

    @Test
    fun unfoldingRotatingAndFoldingFollowTheLiveWindowWithStaleCoverConfiguration() {
        mount(width = 412, height = 915, smallestWidth = 412)
        assertLayout(412, 915, card = false, sideNavigation = false)
        assertLayout(673, 841, card = true, sideNavigation = true)
        assertLayout(841, 673, card = true, sideNavigation = true)
        assertLayout(673, 673, card = true, sideNavigation = true)
        assertLayout(852, 883, card = true, sideNavigation = true)
        assertLayout(883, 852, card = true, sideNavigation = true)
        assertLayout(800, 1280, card = true, sideNavigation = false)
        assertLayout(673, 841, card = true, sideNavigation = true)
        assertLayout(412, 915, card = false, sideNavigation = false)
    }

    @Test
    fun narrowInnerSplitAndCoverUseCompactLayoutWithStaleTabletConfiguration() {
        mount(width = 673, height = 841, smallestWidth = 673)
        assertLayout(673, 841, card = true, sideNavigation = true)
        assertLayout(599, 841, card = false, sideNavigation = false)
        assertLayout(600, 841, card = true, sideNavigation = false)
        assertLayout(600, 750, card = true, sideNavigation = true)
        assertLayout(600, 751, card = true, sideNavigation = false)
        assertLayout(500, 800, card = false, sideNavigation = false)
        assertLayout(841, 673, card = true, sideNavigation = true)
        assertLayout(412, 915, card = false, sideNavigation = false)
    }
}

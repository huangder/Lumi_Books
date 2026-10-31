package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalLumiBackgroundBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tablet presentation of sheets and the vertical tab rail; mdpi keeps 1dp == 1px. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class AdaptiveChromeLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Themed(appTheme: String, content: @Composable () -> Unit) {
        EBookReaderTheme(appTheme = appTheme, darkTheme = false) {
            CompositionLocalProvider(
                LocalAppTheme provides appTheme,
                LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                LocalLumiBackgroundBackdrop provides null,
                content = content
            )
        }
    }

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun tabletSheetFrameIsACenteredCardWithPhoneProportions() {
        compose.setContent {
            Themed("lumi") {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    Box(adaptiveSheetFrame(heightFraction = 0.7f).testTag("sheet"))
                }
            }
        }
        val root = bounds("root")
        val sheet = bounds("sheet")
        assertEquals(480f, sheet.width, 1f)
        // 0.7 of the 820dp phone reference, not 70% of the tablet's height.
        assertEquals(574f, sheet.height, 1f)
        assertEquals(root.center.x, sheet.center.x, 1f)
        assertEquals(root.center.y, sheet.center.y, 1f)
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-mdpi")
    fun landscapePhoneSheetIsCenteredAndFitsTheShortWindow() {
        compose.setContent {
            Themed("lumi") {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    Box(adaptiveSheetFrame(heightFraction = 0.9f).testTag("sheet"))
                }
            }
        }
        val root = bounds("root")
        val sheet = bounds("sheet")
        assertEquals(480f, sheet.width, 1f)
        assertEquals(root.center.x, sheet.center.x, 1f)
        assertEquals(root.center.y, sheet.center.y, 1f)
        assertTrue(sheet.height > 0f)
        assertTrue(sheet.top >= root.top + 24f)
        assertTrue(sheet.bottom <= root.bottom - 24f)
    }

    @Test
    fun tallTabletSheetIsCappedNearSquare() {
        compose.setContent {
            Themed("lumi") {
                Box(Modifier.fillMaxSize()) {
                    Box(adaptiveSheetFrame(heightFraction = 0.9f).testTag("sheet"))
                }
            }
        }
        val sheet = bounds("sheet")
        assertEquals(480f, sheet.width, 1f)
        assertEquals(640f, sheet.height, 1f)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-port-mdpi")
    fun phoneSheetFrameStaysAnchoredToTheBottomEdge() {
        compose.setContent {
            Themed("lumi") {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    Box(adaptiveSheetFrame(heightFraction = 0.7f).testTag("sheet"))
                }
            }
        }
        val root = bounds("root")
        val sheet = bounds("sheet")
        assertEquals(root.width, sheet.width, 1f)
        assertEquals(root.bottom, sheet.bottom, 1f)
        assertEquals(root.height * 0.7f, sheet.height, 1f)
    }

    @Test
    fun tabletBottomDialogIsPresentedAsACenteredCard() {
        compose.setContent {
            Themed("liquid_glass") {
                LiquidGlassDialogHost(Modifier.fillMaxSize().testTag("root")) {
                    LiquidGlassDialog(onDismissRequest = {}, alignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().height(300.dp).testTag("sheet"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val root = bounds("root")
        val sheet = bounds("sheet")
        assertEquals(480f, sheet.width, 1f)
        assertEquals(root.center.x, sheet.center.x, 1f)
        assertEquals(root.center.y, sheet.center.y, 1f)
    }

    @Test
    fun defaultThemeVerticalRailStacksTabsTopToBottom() = verifyVerticalRail("lumi")

    @Test
    fun liquidGlassVerticalRailStacksTabsTopToBottom() = verifyVerticalRail("liquid_glass")

    private fun verifyVerticalRail(theme: String) {
        var selected = -1
        compose.setContent {
            Themed(theme) {
                Box(Modifier.fillMaxSize()) {
                    FloatingTabBar(
                        selectedIndex = 0,
                        onTabSelected = { selected = it },
                        modifier = Modifier.testTag("rail"),
                        vertical = true
                    )
                }
            }
        }
        val rail = bounds("rail")
        val home = bounds("home_tab")
        val shelf = bounds("bookshelf_tab")
        val stats = bounds("statistics_tab")
        assertTrue("$theme rail must be taller than wide", rail.height > rail.width)
        assertTrue("$theme tabs must run top to bottom", home.center.y < shelf.center.y && shelf.center.y < stats.center.y)
        assertEquals(home.center.x, stats.center.x, 1f)

        compose.onNodeWithTag("statistics_tab").performClick()
        compose.runOnIdle { assertEquals("$theme rail must select the tapped tab", 2, selected) }
    }
}

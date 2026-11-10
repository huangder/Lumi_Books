package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderExpandingSlidersTest {
    @get:Rule val compose = createComposeRule()
    private val font = mutableStateOf(20f)
    private val weight = mutableStateOf(400f)
    private val brightness = mutableStateOf(0.5f)
    private val mask = mutableStateOf(0.3f)
    private var layoutPreview = false
    private var commits = 0
    private val tags = listOf("reader-font-size-slider", "reader-font-weight-slider",
        "reader-brightness-slider", "reader-mask-slider")

    private fun mount(animated: Boolean = false) {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                Box(Modifier.fillMaxSize().testTag("touch-root")) {
                    ThemeSettingsSheet(visible = true, currentFontSize = font.value,
                        currentFontType = "kaiti", currentBodyFontWeight = weight.value,
                        currentTheme = "day", currentBrightness = brightness.value,
                        currentDarkMaskOpacity = mask.value, eInkModeEnabled = !animated,
                        onFontSizeChange = { font.value = it; commits++ },
                        onFontSizePreview = { font.value = it },
                        onBodyFontWeightChange = { weight.value = it; commits++ },
                        onBodyFontWeightPreview = { weight.value = it },
                        onBrightnessChange = { brightness.value = it },
                        onDarkMaskOpacityChange = { mask.value = it },
                        onDarkMaskOpacityPreview = { mask.value = it },
                        onLayoutPreviewActiveChange = { layoutPreview = it },
                        onThemeChange = {}, onOpenAdvanced = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(index: Int): Rect = compose.onNodeWithTag(tags[index]).fetchSemanticsNode().boundsInRoot
    private fun root() = compose.onNodeWithTag("touch-root")
    private fun values() = listOf(font.value, weight.value, brightness.value, mask.value)

    @Test fun allFourSlidersExpandTheirOwnRowAndRecoverWithoutChangingThePeer() {
        mount()
        for (index in tags.indices) {
            val before = tags.indices.map(::bounds)
            val initial = values()
            val start = before[index].center
            val end = start + Offset(90f, 0f)
            root().performTouchInput { down(start); moveTo(end, delayMillis = 80) }
            val expanded = bounds(index)
            val peer = index xor 1
            assertTrue("Active slider gets more room", expanded.width > before[index].width * 1.25f)
            assertTrue("Peer shrinks", bounds(peer).width < before[peer].width)
            assertEquals("Row keeps its position", before[index].top, expanded.top, 1f)
            assertEquals("Other row stays put", before[index xor 2], bounds(index xor 2))
            assertEquals(initial[peer], values()[peer], 0f)
            if (index < 2) assertTrue("Reader preview stays active", layoutPreview)
            // Send another event at the same screen coordinate after the row has translated.
            val stable = values()[index]
            root().performTouchInput { moveTo(end, delayMillis = 80) }
            assertEquals("A stationary finger must not change the value", stable, values()[index], 0.001f)
            root().performTouchInput { up() }
            assertEquals(before[index].width, bounds(index).width, 1f)
            assertEquals(initial[peer], values()[peer], 0f)
            compose.runOnIdle { assertFalse("Preview ends after release ($index)", layoutPreview) }
        }
    }

    @Test fun tapAndVerticalScrollDoNotExpandOrStartLayoutPreview() {
        mount()
        val before = bounds(0)
        root().performTouchInput { click(before.center) }
        assertEquals(before.width, bounds(0).width, 1f)
        assertFalse(layoutPreview)
        root().performTouchInput {
            down(before.center)
            moveTo(before.center + Offset(0f, -100f), delayMillis = 100)
        }
        assertEquals(before.width, bounds(0).width, 1f)
        assertFalse(layoutPreview)
        root().performTouchInput { cancel() }
    }

    @Test fun reversalCancelAndSecondPointerKeepOneOwnerAndCommitOnlyOnce() {
        mount()
        val before = bounds(1)
        val start = before.center
        root().performTouchInput { down(start); moveTo(start + Offset(-90f, 0f), delayMillis = 80) }
        assertEquals(0, commits)
        val first = weight.value
        val peer = bounds(0).center
        root().performTouchInput { down(1, peer); moveTo(1, peer + Offset(24f, 0f), delayMillis = 80) }
        assertEquals(20f, font.value, 0f)
        assertEquals(first, weight.value, 0.001f)
        root().performTouchInput { up(1); moveTo(0, start + Offset(90f, 0f), delayMillis = 80) }
        root().performTouchInput { cancel() }
        assertEquals(1, commits)
        assertTrue("The final commit includes the reversal even if previews were throttled", weight.value > first)
        assertEquals(before.width, bounds(1).width, 1f)
        assertFalse(layoutPreview)
    }

    @Test fun animationGrowsMonotonicallyAndStationaryPointerDoesNotDrift() {
        mount(animated = true)
        compose.mainClock.autoAdvance = false
        val before = bounds(1)
        val end = before.center + Offset(90f, 0f)
        root().performTouchInput { down(before.center); moveTo(end, delayMillis = 80) }
        compose.mainClock.advanceTimeByFrame()
        var previous = bounds(1).width
        val stable = weight.value
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            assertTrue(bounds(1).width >= previous - 1f)
            previous = bounds(1).width
            root().performTouchInput { moveTo(end, delayMillis = 16) }
            assertEquals(stable, weight.value, 0.001f)
        }
        assertTrue(bounds(1).width > before.width * 1.3f)
        root().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(220)
        assertEquals(before.width, bounds(1).width, 1f)
    }

    @Test fun pendingPointerReleasesCleanlyWhenAnotherSliderClaimsTheDrag() {
        mount()
        val fontStart = bounds(0).center
        val weightStart = bounds(1).center
        root().performTouchInput {
            down(0, fontStart)
            down(1, weightStart)
            moveTo(1, weightStart + Offset(-90f, 0f), delayMillis = 80)
        }
        root().performTouchInput { up(0); up(1) }
        assertEquals(20f, font.value, 0f)
        // An abandoned press must not block synchronization with later settings changes.
        compose.runOnIdle { font.value = 30f }
        compose.waitForIdle()
        val start = bounds(0).center
        root().performTouchInput {
            down(start)
            moveTo(start + Offset(-90f, 0f), delayMillis = 80)
            up()
        }
        assertTrue("The next drag starts from the updated font size", font.value > 20f)
        assertFalse(layoutPreview)
    }

    @Test fun expandedDragClampsToBothFontLimitsAndAutoBrightnessRemainsAccessible() {
        mount()
        val start = bounds(0).center
        root().performTouchInput { down(start); moveTo(start + Offset(800f, 0f), delayMillis = 80); up() }
        assertEquals(33f, font.value, 0f)
        root().performTouchInput { down(start); moveTo(Offset(-800f, start.y), delayMillis = 80); up() }
        assertEquals(12f, font.value, 0f)
        compose.onNodeWithTag("reader-brightness-auto-toggle").performClick()
        assertEquals(-1f, brightness.value, 0f)
    }
}

package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
class ReaderOrientationContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenLandscapeKeepsItsCssSizeButFitsBothLeavesInsideThePhysicalViewport() {
        val measured = mutableMapOf<Boolean, IntSize>()
        val drawn = mutableMapOf<Boolean, Rect>()
        compose.setContent {
            ReaderOrientationContent(Unit, Modifier.requiredSize(320.dp, 470.dp)) { viewport, _, events ->
                Box(Modifier.onGloballyPositioned {
                    measured[viewport.landscape] = it.size
                    drawn[viewport.landscape] = it.boundsInRoot()
                })
                SideEffect { events.dispatch("onPage", true) {} }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(IntSize(470, 320), measured[true])
            val active = drawn.getValue(false)
            val hidden = drawn.getValue(true)
            assertEquals(320f, hidden.width, 1f)
            assertEquals(320f * 320f / 470f, hidden.height, 1f)
            assertTrue(hidden.left >= active.left - 1f)
            assertTrue(hidden.right <= active.right + 1f)
            assertTrue(hidden.top >= active.top - 1f)
            assertTrue(hidden.bottom <= active.bottom + 1f)
        }
    }

    @Test fun preparedSubtreeSurvivesRapidRotationAndParentRecomposition() {
        val owner = Any()
        var landscape by mutableStateOf(false)
        var revision by mutableIntStateOf(0)
        val created = mutableListOf<ReaderViewport>()
        val published = mutableListOf<Pair<Boolean, Int>>()
        compose.setContent {
            val update = revision
            ReaderOrientationContent(owner, Modifier.requiredSize(
                if (landscape) 470.dp else 320.dp, if (landscape) 320.dp else 470.dp)) { viewport, _, events ->
                remember(viewport) { created.add(viewport) }
                SideEffect { events.dispatch("onPage", true) { published += viewport.landscape to update } }
                Box(Modifier)
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(setOf(ReaderViewport(320, 470), ReaderViewport(470, 320)), created.toSet())
            assertTrue(published.all { !it.first })
            landscape = true
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(published.last().first); landscape = false }
        compose.waitForIdle()
        compose.runOnIdle { landscape = true }
        compose.waitForIdle()
        compose.runOnIdle { revision++ }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("matching prepared geometry must not be rebuilt", 2, created.size)
            assertEquals(true to 1, published.last())
        }
    }
}

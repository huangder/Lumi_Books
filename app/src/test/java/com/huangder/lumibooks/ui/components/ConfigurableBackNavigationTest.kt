package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConfigurableBackNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchBackTakesPriorityWhenTabBackBecomesEnabled() = verifySearchBack(
        predictiveBackEnabled = false,
        returnFromDetails = false
    )

    @Test
    fun searchGestureTakesPriorityWhenTabBackBecomesEnabled() = verifySearchBack(
        predictiveBackEnabled = true,
        returnFromDetails = false
    )

    @Test
    fun searchBackTakesPriorityAfterReturningFromDetails() = verifySearchBack(
        predictiveBackEnabled = false,
        returnFromDetails = true
    )

    @Test
    fun searchGestureTakesPriorityAfterReturningFromDetails() = verifySearchBack(
        predictiveBackEnabled = true,
        returnFromDetails = true
    )

    private fun verifySearchBack(predictiveBackEnabled: Boolean, returnFromDetails: Boolean) {
        val owner = TestBackOwner()
        var bookshelfSelected by mutableStateOf(false)
        var searchVisible by mutableStateOf(false)
        var homeNavigationCount = 0

        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides owner,
                LocalLifecycleOwner provides owner,
                LocalPredictiveBackEnabled provides predictiveBackEnabled
            ) {
                // The pager composes the shelf before the tab settles and enables this handler.
                ConfigurableBackHandler(enabled = bookshelfSelected) { homeNavigationCount++ }
                BackHandler(enabled = searchVisible) { searchVisible = false }
                Box(Modifier.fillMaxSize())
            }
        }
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.RESUMED
            bookshelfSelected = true
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { searchVisible = true }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            if (returnFromDetails) {
                owner.registry.currentState = Lifecycle.State.CREATED
                owner.registry.currentState = Lifecycle.State.RESUMED
            }
            if (predictiveBackEnabled) {
                owner.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
                owner.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.7f))
            }
            owner.onBackPressedDispatcher.onBackPressed()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertFalse("Search must handle back before returning to the home tab", searchVisible)
            assertEquals(0, homeNavigationCount)
        }
        compose.runOnIdle { owner.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(1, homeNavigationCount) }
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 100f, progress, BackEventCompat.EDGE_LEFT)

    private class TestBackOwner : OnBackPressedDispatcherOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
    }
}

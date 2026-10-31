package com.huangder.lumibooks.ui.navigation

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderMountGateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `heavy reader content waits until after the window finishes`() {
        val windowReady = mutableStateOf(false)
        var mounted = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (rememberReaderMountReady("book", windowReady.value)) {
                remember { mounted++ }
            }
        }
        compose.mainClock.advanceTimeBy(640)
        compose.runOnIdle { assertEquals(0, mounted) }
        compose.runOnIdle { windowReady.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0, mounted) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals(1, mounted) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, mounted) }
    }

    @Test fun `opening without a hero mounts immediately`() {
        var mounted = 0
        compose.setContent {
            if (rememberReaderMountReady("book", windowReady = true)) {
                remember { mounted++ }
            }
        }
        compose.runOnIdle { assertEquals(1, mounted) }
    }
}

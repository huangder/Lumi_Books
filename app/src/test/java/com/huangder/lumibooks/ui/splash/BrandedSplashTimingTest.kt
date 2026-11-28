package com.huangder.lumibooks.ui.splash

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrandedSplashTimingTest {
    @Test
    fun readyOnLaunchStillShowsBrandForMinimumTime() = runTest {
        awaitBrandedSplashExit(MutableStateFlow(true))
        assertEquals(300L, currentTime)
    }

    @Test
    fun contentReleasesSplashWithoutWaitingForTheDeadline() = runTest {
        val ready = MutableStateFlow(false)
        launch {
            delay(570)
            ready.value = true
        }
        awaitBrandedSplashExit(ready)
        assertEquals(570L, currentTime)
    }

    @Test
    fun unavailableContentCannotKeepSplashOverTheApp() = runTest {
        awaitBrandedSplashExit(MutableStateFlow(false))
        assertEquals(1_100L, currentTime)
    }
}

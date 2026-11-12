package com.huangder.lumibooks.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBannerStateTest {

    @Test
    fun messagesQueueInOrderAndShowReturnsAfterDismiss() = runTest {
        val state = NotificationBannerState()
        val finished = mutableListOf<String>()
        launch { state.show("first"); finished += "first" }
        launch { state.show("second"); finished += "second" }
        runCurrent()

        assertEquals("first", state.current?.message?.title)
        state.onDismissed(state.current!!)
        runCurrent()
        assertEquals(listOf("first"), finished)
        assertEquals("second", state.current?.message?.title)

        state.onDismissed(state.current!!)
        runCurrent()
        assertEquals(listOf("first", "second"), finished)
        assertNull(state.current)
    }

    @Test
    fun repeatedMessageRestartsTimerInsteadOfQueueing() = runTest {
        val state = NotificationBannerState()
        launch { state.show("正在同步…") }
        runCurrent()
        val entry = state.current!!
        assertEquals(0, entry.restartToken)

        var duplicateReturned = false
        launch { state.show("正在同步…"); duplicateReturned = true }
        runCurrent()

        assertTrue(duplicateReturned)
        assertEquals(1, entry.restartToken)
        state.onDismissed(entry)
        runCurrent()
        assertNull(state.current)
    }

    @Test
    fun staleDismissDoesNotCompleteNewerBanner() = runTest {
        val state = NotificationBannerState()
        launch { state.show("first") }
        runCurrent()
        val first = state.current!!
        state.onDismissed(first)
        runCurrent()
        launch { state.show("second") }
        runCurrent()

        state.onDismissed(first)
        runCurrent()
        assertEquals("second", state.current?.message?.title)
        state.onDismissed(state.current!!)
        runCurrent()
    }

    @Test
    fun swipeDismissThresholds() {
        val density = 3f
        val height = 180f
        // 30 % of the banner height upwards dismisses.
        assertTrue(shouldDismissBanner(-54f, height, 0f, density))
        assertFalse(shouldDismissBanner(-50f, height, 0f, density))
        // A fast upward fling dismisses even after a short drag.
        assertTrue(shouldDismissBanner(-10f, height, -600f * density, density))
        assertFalse(shouldDismissBanner(-10f, height, -500f * density, density))
        // Downward pulls never dismiss.
        assertFalse(shouldDismissBanner(30f, height, 2_000f, density))
    }

    @Test
    fun downwardPullIsRubberBanded() {
        assertEquals(-40f, rubberBandBannerOffset(-40f, 36f), 0f)
        assertEquals(10f, rubberBandBannerOffset(40f, 36f), 0f)
        assertEquals(36f, rubberBandBannerOffset(400f, 36f), 0f)
    }
}

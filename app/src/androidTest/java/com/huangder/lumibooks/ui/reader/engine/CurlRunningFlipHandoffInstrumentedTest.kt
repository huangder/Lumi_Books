package com.huangder.lumibooks.ui.reader.engine

import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurlRunningFlipHandoffInstrumentedTest {
    @Test
    fun gestureHandoffCommitsRunningCurlSynchronously() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = FrameLayout(context)
            val previous = View(context)
            val current = View(context)
            val next = View(context)
            val width = 1080
            val height = 1920
            root.layout(0, 0, width, height)
            previous.layout(0, 0, width, height)
            current.layout(0, 0, width, height)
            next.layout(0, 0, width, height)

            val surface = PageAnimationSurface(
                root = root,
                prevPageView = previous,
                curPageView = current,
                nextPageView = next,
                backgroundColorProvider = { 0xFFFFFFFF.toInt() },
                directPageRenderer = { _, _ -> true }
            )
            val controller = CurlPageAnim(surface, baseDurationMs = 800)
            var completionCount = 0
            controller.onCanFlip = { true }
            controller.onAnimationComplete = { completionCount++ }

            controller.startFromTap(PageAnimationController.Direction.NEXT)
            assertTrue(controller.isRunning)

            assertEquals(
                PageAnimationController.RunningFlipHandoff.COMPLETED_SYNCHRONOUSLY,
                controller.completeRunningFlipForGestureHandoff()
            )
            assertEquals(1, completionCount)
            assertTrue(!controller.isRunning)

            controller.destroy()
        }
    }

    @Test
    fun expeditedDirectCurlRemainsRunningUntilItsTerminalFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = FrameLayout(context)
            val previous = View(context)
            val current = View(context)
            val next = View(context)
            val width = 1080
            val height = 1920
            root.layout(0, 0, width, height)
            previous.layout(0, 0, width, height)
            current.layout(0, 0, width, height)
            next.layout(0, 0, width, height)

            val surface = PageAnimationSurface(
                root = root,
                prevPageView = previous,
                curPageView = current,
                nextPageView = next,
                backgroundColorProvider = { 0xFFFFFFFF.toInt() },
                directPageRenderer = { _, _ -> true }
            )
            val controller = CurlPageAnim(surface, baseDurationMs = 800)
            var completionCount = 0
            controller.onCanFlip = { true }
            controller.onAnimationComplete = { completionCount++ }

            controller.startFromTap(PageAnimationController.Direction.NEXT)
            assertTrue(controller.isRunning)

            assertEquals(
                PageAnimationController.RunningFlipHandoff.COMPLETING_ASYNCHRONOUSLY,
                controller.completeRunningFlipForNewInput()
            )
            assertTrue(controller.isRunning)
            assertEquals(0, completionCount)

            controller.destroy()
        }
    }
}

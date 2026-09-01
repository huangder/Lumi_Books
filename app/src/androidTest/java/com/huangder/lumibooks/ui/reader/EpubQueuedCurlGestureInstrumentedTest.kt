package com.huangder.lumibooks.ui.reader

import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.util.epub.EpubHighlightTestActivity
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubQueuedCurlGestureInstrumentedTest {
    @Test
    fun busySwipeResumesBeforeReleaseAndCommitsExactlyOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, EpubHighlightTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as EpubHighlightTestActivity
        lateinit var host: EpubPageTurnHost
        val commits = AtomicInteger(0)
        val committedPageCount = AtomicInteger(0)
        var preloadGeneration = 10

        instrumentation.runOnMainSync {
            host = EpubPageTurnHost(activity)
            activity.setContentView(host)
        }
        instrumentation.waitForIdleSync()

        try {
            instrumentation.runOnMainSync {
                assertTrue("the curl host must be attached and laid out", host.isAttachedToWindow)
                assertTrue(host.width > 0)
                assertTrue(host.height > 0)
            host.allWebViews().forEachIndexed { index, view ->
                view.setBackgroundColor(
                    when (index) {
                        0 -> Color.RED
                        1 -> Color.GREEN
                        else -> Color.BLUE
                    }
                )
            }

            fun prepare(slot: EpubPageTurnHost.PreloadSlot, target: EpubPageTarget) {
                val source = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                    host.previousWebView
                } else {
                    host.nextWebView
                }
                val generation = ++preloadGeneration
                host.markPreloadLoading(slot, target, generation)
                host.markPreloadReady(
                    slot = slot,
                    requested = target,
                    generation = generation,
                    actualPageIndex = target.pageIndex,
                    actualPageCount = 5,
                    sourceView = source
                )
            }

            host.setTransition("curl", 300)
            host.setCurrentPage(chapterIndex = 0, pageIndex = 1, pageCount = 5)
            prepare(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(0, 0))
            prepare(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2))
            host.onSlideLookaheadRequested = { slot, target -> prepare(slot, target) }
            host.onPageCommit = { _, target, pageCount ->
                commits.incrementAndGet()
                committedPageCount.set(pageCount)
                host.setCurrentPage(target.chapterIndex, target.pageIndex, 5)
            }

            assertTrue(host.turnFromTap(1))
            }

            val downTime = SystemClock.uptimeMillis()
            val startX = host.width * 0.84f
            val startY = host.height * 0.65f
            instrumentation.runOnMainSync {
                dispatch(host, downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY)
                dispatch(
                    host,
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE,
                    host.width * 0.58f,
                    startY
                )
            }

            var firstOffset: Float? = null
            val resumeDeadline = SystemClock.uptimeMillis() + 2_000L
            while (firstOffset == null && SystemClock.uptimeMillis() < resumeDeadline) {
                SystemClock.sleep(16L)
                instrumentation.runOnMainSync {
                    host.computeScroll()
                    firstOffset = host.activeCurlGestureOffsetPx()
                }
            }
            assertNotNull("the queued swipe must resume before ACTION_UP", firstOffset)
            assertEquals("only the first turn is committed before release", 1, commits.get())
            assertEquals("visual commit carries the destination page count", 5, committedPageCount.get())

            instrumentation.runOnMainSync {
                dispatch(
                    host,
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE,
                    host.width * 0.32f,
                    startY
                )
                val secondOffset = host.activeCurlGestureOffsetPx()
                assertNotNull(secondOffset)
                assertTrue(
                    "the resumed curl must continue following MOVE",
                    secondOffset!! > firstOffset!!
                )

                dispatch(
                    host,
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    host.width * 0.32f,
                    startY
                )
            }

            val commitDeadline = SystemClock.uptimeMillis() + 2_000L
            while (commits.get() < 2 && SystemClock.uptimeMillis() < commitDeadline) {
                SystemClock.sleep(16L)
                instrumentation.runOnMainSync { host.computeScroll() }
            }
            instrumentation.runOnMainSync {
                host.computeScroll()
                assertEquals("release must commit the resumed gesture once", 2, commits.get())
                host.computeScroll()
                assertEquals(2, commits.get())
            }
        } finally {
            instrumentation.runOnMainSync {
                host.setNativePagingEnabled(false)
                activity.finish()
            }
        }
    }

    private fun dispatch(
        host: EpubPageTurnHost,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            host.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}

package com.huangder.lumibooks.ui.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.util.epub.EpubHighlightTestActivity
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubVerticalPagingInstrumentedTest {
    @Test
    fun verticalTurnShowsIncomingPageAndPromotesItOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, EpubHighlightTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as EpubHighlightTestActivity
        lateinit var host: EpubPageTurnHost
        val commits = AtomicInteger(0)
        var committedTarget: EpubPageTarget? = null
        var generation = 0

        instrumentation.runOnMainSync {
            host = EpubPageTurnHost(activity)
            activity.setContentView(host)
        }
        instrumentation.waitForIdleSync()

        try {
            lateinit var incoming: EpubContentWebView
            instrumentation.runOnMainSync {
                assertTrue(host.width > 0)
                assertTrue(host.height > 0)
                host.setTransition("scroll", 100)
                host.setCurrentPage(chapterIndex = 0, pageIndex = 1, pageCount = 5)

                fun prepare(slot: EpubPageTurnHost.PreloadSlot, page: Int) {
                    val source = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                        host.previousWebView
                    } else {
                        host.nextWebView
                    }
                    val target = EpubPageTarget(0, page)
                    val requestGeneration = ++generation
                    host.markPreloadLoading(slot, target, requestGeneration)
                    host.markPreloadReady(
                        slot = slot,
                        requested = target,
                        generation = requestGeneration,
                        actualPageIndex = page,
                        actualPageCount = 5,
                        sourceView = source
                    )
                }

                prepare(EpubPageTurnHost.PreloadSlot.PREVIOUS, 0)
                prepare(EpubPageTurnHost.PreloadSlot.NEXT, 2)
                incoming = host.nextWebView
                host.onPageCommit = { _, target, _ ->
                    commits.incrementAndGet()
                    committedTarget = target
                    host.setCurrentPage(target.chapterIndex, target.pageIndex, 5)
                }
                assertTrue(host.turnFromTap(1))
            }

            val frame = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
            try {
                instrumentation.runOnMainSync {
                    host.draw(Canvas(frame))
                    assertEquals(1f, incoming.alpha, 0f)
                    assertEquals(
                        host.height.toFloat(),
                        incoming.translationY - host.activeWebView.translationY,
                        1f
                    )
                }

                val deadline = SystemClock.uptimeMillis() + 2_000L
                while (commits.get() == 0 && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(16L)
                    instrumentation.runOnMainSync { host.computeScroll() }
                }
                instrumentation.runOnMainSync {
                    assertEquals(1, commits.get())
                    assertEquals(EpubPageTarget(0, 2), committedTarget)
                    assertSame(incoming, host.activeWebView)
                    assertEquals(0f, host.previousWebView.alpha, 0f)
                    assertEquals(0f, host.nextWebView.alpha, 0f)
                    assertEquals(-host.height.toFloat(), host.previousWebView.translationY, 1f)
                    assertEquals(host.height.toFloat(), host.nextWebView.translationY, 1f)
                }
            } finally {
                frame.recycle()
            }
        } finally {
            instrumentation.runOnMainSync {
                host.setNativePagingEnabled(false)
                activity.finish()
            }
        }
    }
}

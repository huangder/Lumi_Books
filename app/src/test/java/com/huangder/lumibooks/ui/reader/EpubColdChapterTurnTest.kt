package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class EpubColdChapterTurnTest {
    private fun host(transition: String, chapter: Int = 1) =
        EpubPageTurnHost(RuntimeEnvironment.getApplication()).apply {
            setTransition(transition)
            setNativePagingEnabled(true)
            setCurrentPage(chapter, 0, 1)
            onHasPotentialTurn = { direction, target, count ->
                hasPotentialEpubPageTurn(target, count, 5, direction)
            }
            Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(this)
            measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            layout(0, 0, 360, 720)
            // Robolectric's mock WebView provider does not apply View.setFrame.
            allWebViews().forEach { view ->
                ReflectionHelpers.setField(view, "mRight", 360)
                ReflectionHelpers.setField(view, "mBottom", 720)
            }
        }

    @Test fun missingNeighbourConsumesInputAndRequestsPreparationWithoutChangingVisiblePage() {
        for (transition in listOf("slide", "scroll", "curl", "wave")) {
            for (direction in listOf(-1, 1)) {
                val host = host(transition)
                val active = host.activeWebView
                val requests = mutableListOf<Pair<EpubPageTurnHost.PreloadSlot, EpubPageTarget>>()
                host.onSlideLookaheadRequested = { slot, target -> requests += slot to target }
                assertTrue("$transition/$direction must wait inside the host", host.turnFromTap(direction))
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue("$transition/$direction did not request a neighbour", requests.contains(
                    (if (direction > 0) EpubPageTurnHost.PreloadSlot.NEXT else EpubPageTurnHost.PreloadSlot.PREVIOUS) to
                        EpubPageTarget(1 + direction, if (direction > 0) 0 else Int.MAX_VALUE)))
                assertSame(active, host.activeWebView)
                assertEquals(1f, active.alpha)
                assertEquals(View.VISIBLE, active.visibility)
                assertFalse(host.hasPendingPageHandoff())
                host.release()
            }
        }
    }

    @Test fun curlDoesNotAbandonAColdChapterAfterBriefPreloadSilence() {
        val host = host("curl")
        var recoveries = 0
        host.onUnpreparedChapterTurn = { recoveries++ }
        assertTrue(host.turnFromTap(1))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200))
        assertEquals(0, recoveries)
        assertNull(shadowOf(host.activeWebView).lastEvaluatedJavascript)
        assertFalse(host.hasPendingPageHandoff())
        host.release()
    }

    @Test fun queuedLiveTurnStartsOnlyAfterTheTargetIsPrepared() {
        for (transition in listOf("slide", "scroll")) {
            val host = host(transition)
            val active = host.activeWebView
            val targetView = host.nextWebView
            val target = EpubPageTarget(2, 0)
            host.onSlideLookaheadRequested = { slot, requested ->
                if (host.preloadTarget(slot) != requested) host.markPreloadLoading(slot, requested, 1)
            }
            assertTrue(host.turnFromTap(1))
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(host.ownsPage(targetView))
            assertSame(active, host.activeWebView)
            assertEquals(target, host.preloadTarget(EpubPageTurnHost.PreloadSlot.NEXT))
            // Android lays out the newly visible preload before delivering its draw fence.
            targetView.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            targetView.layout(0, 0, 360, 720)
            ReflectionHelpers.setField(targetView, "mRight", 360)
            ReflectionHelpers.setField(targetView, "mBottom", 720)
            assertTrue("target must be laid out", targetView.width > 0 && targetView.height > 0)
            host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, target, 1, 0, 1, targetView)
            assertTrue("target must be ready", host.isPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("$transition must resume the waiting input", host.ownsPage(targetView))
            host.release()
        }
    }

    @Test fun failedCurlChapterRecoversOffscreenRatherThanMutatingActiveDocument() {
        val host = host("curl")
        val active = host.activeWebView
        val recoveries = mutableListOf<Int>()
        host.onUnpreparedChapterTurn = { recoveries += it }
        assertTrue(host.turnFromTap(1))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(EPUB_NAVIGATION_TIMEOUT_MS + 1))
        assertEquals(listOf(1), recoveries)
        assertSame(active, host.activeWebView)
        assertNull(shadowOf(active).lastEvaluatedJavascript)
        host.release()
    }

    @Test fun forwardBookBoundaryDoesNotRequestAnotherChapter() {
        val host = host("slide", chapter = 4)
        var requests = 0
        host.onSlideLookaheadRequested = { _, _ -> requests++ }
        assertTrue(host.turnFromTap(1))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, requests)
        host.release()
    }

    @Test fun repeatedChapterInputPreservesPendingNavigationButAllowsOtherDestinations() {
        val destination = EpubNavigationDestination.Page(0)
        val pending = EpubNavigationRequest(7, EpubNavigationOrigin.PAGE_TURN, 1, 0, 2, destination)
        assertTrue(pending.coalescesPageTurn(EpubNavigationOrigin.PAGE_TURN, 1, 2, destination))
        assertFalse(pending.coalescesPageTurn(EpubNavigationOrigin.PAGE_TURN, 2, 3, destination))
        assertFalse(pending.coalescesPageTurn(EpubNavigationOrigin.PAGE_TURN, 1, 0,
            EpubNavigationDestination.Page(Int.MAX_VALUE)))
        assertFalse(pending.coalescesPageTurn(EpubNavigationOrigin.TOC, 1, 2, destination))
        assertFalse(pending.coalescesPageTurn(EpubNavigationOrigin.PAGE_TURN, 1, 2,
            EpubNavigationDestination.Fragment("note")))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun darkChaptersNeverRevealWhitePaperDuringScrollOrFadeHandoff() {
        for (vertical in listOf(false, true)) {
            for (direction in listOf(-1, 1)) {
                val host = host("slide")
                val slot = if (direction > 0) EpubPageTurnHost.PreloadSlot.NEXT else EpubPageTurnHost.PreloadSlot.PREVIOUS
                val target = EpubPageTarget(1 + direction, 0)
                val incoming = host.preloadView(slot)!!
                host.activeWebView.background = ColorDrawable(Color.BLACK)
                incoming.background = ColorDrawable(Color.BLACK)
                host.markPreloadLoading(slot, target, 1)
                host.markPreloadReady(slot, target, 1, 0, 1, incoming)
                assertTrue(host.fadePreparedChapter(slot, target, {}, { fail("handoff invalidated") },
                    verticalMotion = vertical, durationMs = 360))
                val bitmap = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888)
                for (step in 0..4) {
                    if (step > 0) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(90))
                    host.draw(Canvas(bitmap))
                    for (y in listOf(host.height / 10, host.height / 2, host.height * 9 / 10)) {
                        assertEquals("vertical=$vertical direction=$direction step=$step y=$y",
                            Color.BLACK, bitmap.getPixel(host.width / 2, y))
                    }
                }
                bitmap.recycle()
                host.release()
            }
        }
    }
}

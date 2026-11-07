package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** JS does not execute in Robolectric; inject completed leaves, then exercise the real host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class EpubSpreadLifecycleTest {
    private val activity = Robolectric.buildActivity(Activity::class.java)
    private lateinit var host: EpubSpreadTurnHost

    @Before fun setUp() {
        val screen = activity.setup().get()
        host = EpubSpreadTurnHost(screen)
        screen.setContentView(host)
        host.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 1000, 700)
    }

    @After fun tearDown() {
        host.release()
        activity.pause().stop().destroy()
    }

    @Test fun constructionOwnsOnlyThreePairsAndExposesNoUncommittedLeaf() {
        assertEquals(6, host.allWebViews().size)
        assertEquals(6, host.allWebViews().distinct().size)
        assertEquals(3, host.childCount)
        assertTrue(host.visibleWebViews().isEmpty())
        assertNull(host.readyView(EpubPageTarget(0, 0)))
    }

    @Test fun partialDestinationNeverCommitsHalfOfASpread() {
        val target = spread(0)
        val destination = slot("next")
        prepare(destination, target, secondReady = false)
        var commits = 0
        host.onSpreadChanged = { commits++ }
        commit(destination, 1)
        assertEquals(0, commits)
        assertTrue(host.visibleWebViews().isEmpty())

        set(leaves(destination)[1], "page", EpubSpreadPage(target.second!!, 20))
        commit(destination, 1)
        assertEquals(1, commits)
        assertEquals(2, host.visibleWebViews().size)
    }

    @Test fun committedGroupsRotateTogetherAndReuseTheSameSixWebViews() {
        val original = host.allWebViews().toSet()
        val initial = slot("next")
        prepare(initial, spread(0))
        commit(initial, 0)
        val firstViews = host.visibleWebViews().toList()

        val destination = slot("next")
        prepare(destination, spread(2))
        val destinationViews = leaves(destination).map { get(it, "view") as EpubContentWebView }
        var notified: EpubSpreadState? = null
        host.onSpreadChanged = { notified = it }
        commit(destination, 1)
        assertEquals(destinationViews, host.visibleWebViews())
        assertSame(firstViews[0], host.readyView(EpubPageTarget(0, 0)))
        assertSame(firstViews[1], host.readyView(EpubPageTarget(0, 1)))
        assertEquals(spread(2), notified!!.target)
        assertEquals(listOf(EpubPageTarget(0, 2), EpubPageTarget(0, 3)),
            listOf(notified!!.first.target, notified!!.second!!.target))
        assertEquals(original, host.allWebViews().toSet())
    }

    @Test fun stalePositioningCommandAndOldLayoutFenceCannotBecomeReady() {
        val leaf = leaves(slot("next"))[0]
        val view = get(leaf, "view") as EpubContentWebView
        val request = EpubSpreadLeafRequest(EpubPageTarget(0, 0), EpubNavigationDestination.Page(0), 1)
        set(leaf, "request", request)
        set(leaf, "fence", 8L)
        set(host, "configurationIdentity", "old-width")
        set(leaf, "configurationIdentity", "old-width")
        view.documentLifecycle.configure("layout-one")
        val generation = view.documentLifecycle.configurationGeneration
        view.documentLifecycle.observeLayout(generation, 4, true)
        assertTrue(accepts(leaf, request, 8, generation, 4))
        assertFalse(accepts(leaf, request, 7, generation, 4))
        assertFalse(accepts(leaf, request.copy(documentGeneration = 2), 8, generation, 4))
        view.documentLifecycle.observeLayout(generation, 5, false)
        assertFalse(accepts(leaf, request, 8, generation, 4))
        assertFalse(accepts(leaf, request, 8, generation, 5))
        view.documentLifecycle.observeLayout(generation, 5, true)
        assertTrue(accepts(leaf, request, 8, generation, 5))
        set(host, "configurationIdentity", "new-width")
        assertFalse(accepts(leaf, request, 8, generation, 5))
        set(leaf, "configurationIdentity", "new-width")
        assertTrue(accepts(leaf, request, 8, generation, 5))
        host.release()
        assertFalse(accepts(leaf, request, 8, generation, 5))
    }

    @Test fun releaseDropsQueuedDrawCallbacksAndCancelsAnUncommittedAnimation() {
        val views = host.allWebViews().toList()
        var staleCallbacks = 0
        views.forEach { it.runAfterNextDraw { staleCallbacks++ } }
        set(host, "animationCommit", { staleCallbacks++ })
        host.release()
        assertTrue(host.released)
        views.forEach {
            assertTrue(it.released)
            assertNull(it.parent)
            it.evaluateJavascript("true") { staleCallbacks++ }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        host.computeScroll()
        assertEquals(0, staleCallbacks)
        assertFalse(host.turn(1))
    }

    @Test fun releaseDetachesAllLeavesBeforeDestroyingAfterTheFrameFence() {
        var detachedFrame: (() -> Unit)? = null
        val closing = EpubSpreadTurnHost(activity.get()) { _, complete -> detachedFrame = complete }
        val views = closing.allWebViews()
        var completions = 0
        closing.release { completions++ }
        closing.release { completions++ }
        assertEquals(0, closing.childCount)
        assertTrue(closing.allWebViews().isEmpty())
        assertTrue(closing.visibleWebViews().isEmpty())
        assertEquals(0, completions)
        views.forEach {
            assertTrue(it.released)
            assertFalse(it.destroyed)
            assertNull(it.parent)
        }
        detachedFrame!!.invoke()
        assertEquals(2, completions)
        views.forEach { assertTrue(it.destroyed) }
        closing.release { completions++ }
        assertEquals(3, completions)
    }

    @Test fun deadBackgroundRendererIsReplacedWithoutIncreasingTheLiveViewPool() {
        val original = host.allWebViews().toSet()
        val background = leaves(slot("next"))[0]
        val dead = get(background, "view") as EpubContentWebView
        host.javaClass.declaredMethods.single { it.name == "failLeaf" }.apply { isAccessible = true }
            .invoke(host, background, EpubInitialLoadFailureReason.RENDERER_GONE)
        val replacement = get(background, "view") as EpubContentWebView
        assertTrue(dead.released)
        assertNull(dead.parent)
        assertFalse(replacement.released)
        assertFalse(dead === replacement)
        assertEquals(6, host.allWebViews().size)
        assertEquals(original - dead + replacement, host.allWebViews().toSet())
    }

    @Test fun oldRendererCallbacksCannotInvalidateTheReplacementDocument() {
        val background = leaves(slot("next"))[0]
        val dead = get(background, "view") as EpubContentWebView
        val oldClient = dead.webViewClient
        host.javaClass.declaredMethods.single { it.name == "failLeaf" }.apply { isAccessible = true }
            .invoke(host, background, EpubInitialLoadFailureReason.RENDERER_GONE)
        val replacement = get(background, "view") as EpubContentWebView
        val url = "https://appassets.androidplatform.net/book/chapter.xhtml"
        set(background, "documentUrl", url)
        set(background, "documentReady", false)
        val generation = replacement.documentLifecycle.configurationGeneration
        oldClient.onPageStarted(dead, url, null)
        oldClient.onPageFinished(dead, url)
        assertEquals(generation, replacement.documentLifecycle.configurationGeneration)
        assertEquals(false, get(background, "documentReady"))
    }

    @Test fun draggingMovesTheWholeSpreadBeforeReleaseAndCommitsExactlyOnce() {
        val target = gestureFixture()
        var commits = 0
        host.onSpreadChanged = { commits++ }
        gestureStart(700f, 400f)
        drawFrame()
        assertEquals(-300f, (get(host, "controller") as PageAnimationController).getOffsetX(), 0.01f)
        assertEquals(-300f, (slot("current") as View).translationX, 0.01f)
        assertEquals(0, commits)
        assertEquals(spread(4), get(host, "state").let { (it as EpubSpreadState).target })
        touch(MotionEvent.ACTION_UP, 400f, 180)
        settleFrames()
        assertEquals(1, commits)
        assertEquals(target, (get(host, "state") as EpubSpreadState).target)
        assertNull(get(host, "controller"))
    }

    @Test fun cancelledDragAndSlowShortDragReturnWithoutChangingPages() {
        gestureFixture()
        var commits = 0
        host.onSpreadChanged = { commits++ }
        gestureStart(700f, 400f)
        touch(MotionEvent.ACTION_CANCEL, 400f, 200)
        settleFrames()
        assertEquals(0, commits)
        assertEquals(0f, (slot("current") as View).translationX, 0.01f)
        assertNull(get(host, "controller"))

        gestureStart(200f, 180f)
        touch(MotionEvent.ACTION_UP, 180f, 700)
        settleFrames()
        assertEquals(0, commits)
        assertEquals(spread(4), (get(host, "state") as EpubSpreadState).target)
        assertNull(get(host, "controller"))
    }

    @Test fun draggingBackAcrossTheOriginUsesThePreviousGroup() {
        gestureFixture()
        gestureStart(500f, 200f)
        touch(MotionEvent.ACTION_MOVE, 800f, 180)
        drawFrame()
        assertEquals(300f, (get(host, "controller") as PageAnimationController).getOffsetX(), 0.01f)
        touch(MotionEvent.ACTION_UP, 800f, 230)
        settleFrames()
        assertEquals(spread(2), (get(host, "state") as EpubSpreadState).target)
    }

    @Test fun rtlDragTracksPhysicalMotionAndCommitsTheNextLogicalGroup() {
        gestureFixture()
        set(host, "reverseAxis", true)
        gestureStart(300f, 600f)
        drawFrame()
        assertEquals(300f, (slot("current") as View).translationX, 0.01f)
        touch(MotionEvent.ACTION_UP, 600f, 180)
        settleFrames()
        assertEquals(spread(6), (get(host, "state") as EpubSpreadState).target)
    }

    @Test fun fadeUpdatesBothGroupAlphasWhileTheFingerIsStillDown() {
        gestureFixture("fade")
        gestureStart(700f, 400f)
        drawFrame()
        assertEquals(0.7f, (slot("current") as View).alpha, 0.01f)
        assertEquals(0.3f, (slot("next") as View).alpha, 0.01f)
        touch(MotionEvent.ACTION_CANCEL, 400f, 180)
        settleFrames()
        assertEquals(1f, (slot("current") as View).alpha, 0.01f)
        assertEquals(spread(4), (get(host, "state") as EpubSpreadState).target)
    }

    @Test fun slowDestinationResumesTheHeldFingerPositionAndCancelDropsIt() {
        gestureFixture()
        set(leaves(slot("next"))[1], "page", null)
        gestureStart(700f, 400f)
        assertNotNull(get(host, "waitingGesture"))
        assertNull(get(host, "controller"))
        prepare(slot("next"), spread(6))
        host.javaClass.getDeclaredMethod("resumeWaitingGesture").apply { isAccessible = true }.invoke(host)
        drawFrame()
        assertEquals(-300f, (slot("current") as View).translationX, 0.01f)
        assertNull(get(host, "waitingGesture"))
        touch(MotionEvent.ACTION_CANCEL, 400f, 200)
        settleFrames()
        assertEquals(spread(4), (get(host, "state") as EpubSpreadState).target)
    }

    @Test fun cancellationWhileLoadingNeverReplaysTheOldGesture() {
        gestureFixture()
        set(leaves(slot("next"))[1], "page", null)
        gestureStart(700f, 400f)
        touch(MotionEvent.ACTION_CANCEL, 400f, 200)
        assertNull(get(host, "waitingGesture"))
        prepare(slot("next"), spread(6))
        host.javaClass.getDeclaredMethod("resumeWaitingGesture").apply { isAccessible = true }.invoke(host)
        assertNull(get(host, "controller"))
        assertEquals(spread(4), (get(host, "state") as EpubSpreadState).target)
    }

    private fun gestureFixture(transition: String = "slide"): EpubSpreadTarget {
        prepare(slot("next"), spread(4))
        commit(slot("next"), 0)
        prepare(slot("previous"), spread(2))
        val target = spread(6)
        prepare(slot("next"), target)
        set(host, "transition", transition)
        return target
    }

    private fun gestureStart(from: Float, to: Float) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, from, 350f, 0)
        host.onInterceptTouchEvent(down)
        down.recycle()
        val move = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, to, 350f, 0)
        assertTrue(host.onInterceptTouchEvent(move))
        host.onTouchEvent(move)
        move.recycle()
    }

    private fun touch(action: Int, x: Float, at: Long) {
        val event = MotionEvent.obtain(0, at, action, x, 350f, 0)
        host.onTouchEvent(event)
        event.recycle()
    }

    private fun drawFrame() {
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        try { host.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }

    private fun settleFrames() {
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            host.computeScroll()
            drawFrame()
        }
    }

    private fun spread(firstPage: Int) = EpubSpreadTarget(
        EpubPageTarget(0, firstPage), EpubPageTarget(0, firstPage + 1)
    )

    private fun slot(name: String): Any = get(host, name)!!

    @Suppress("UNCHECKED_CAST")
    private fun leaves(slot: Any): List<Any> = get(slot, "leaves") as List<Any>

    private fun prepare(slot: Any, target: EpubSpreadTarget, secondReady: Boolean = true) {
        slot.javaClass.getDeclaredMethod("arrange", EpubSpreadTarget::class.java).apply { isAccessible = true }
            .invoke(slot, target)
        leaves(slot).forEachIndexed { index, leaf ->
            set(leaf, "page", if (index == 0 || secondReady) EpubSpreadPage(target.pages[index], 20) else null)
        }
    }

    private fun commit(slot: Any, direction: Int) {
        host.javaClass.declaredMethods.single { it.name == "animateCommit" }.apply { isAccessible = true }
            .invoke(host, slot, direction, null, null, null)
    }

    private fun accepts(leaf: Any, request: EpubSpreadLeafRequest, fence: Long, generation: Long, revision: Long): Boolean =
        host.javaClass.declaredMethods.single { it.name == "acceptsFence" }.apply { isAccessible = true }
            .invoke(host, leaf, request, fence, generation, revision) as Boolean

    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun set(owner: Any, name: String, value: Any?) {
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    }
}

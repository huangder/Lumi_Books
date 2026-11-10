package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.view.MotionEvent
import android.view.View
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class EpubFootnoteGestureTest {
    private val activity = Robolectric.buildActivity(Activity::class.java)
    private lateinit var host: EpubPageTurnHost
    private val childEvents = mutableListOf<Int>()
    private var bookmarkStarts = 0
    private var bookmarkCommits = 0

    @Before fun setUp() {
        val screen = activity.setup().get()
        host = EpubPageTurnHost(screen)
        screen.setContentView(host)
        host.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 400, 800)
        host.activeWebView.apply {
            right = 400
            bottom = 800
            setOnTouchListener { _, event -> childEvents += event.actionMasked; true }
        }
        host.setBookmarkPullEnabled(true)
        host.onBookmarkPullStart = { bookmarkStarts++ }
        host.onBookmarkPullFinished = { if (it) bookmarkCommits++ }
    }

    @After fun tearDown() { host.release(); activity.pause().stop().destroy() }

    @Test fun openNoteKeepsUpwardDownwardAndHorizontalStrokesInWebView() {
        host.activeWebView.footnotePopoverVisible = true
        for ((x, y) in listOf(200f to 150f, 200f to 550f, 30f to 350f)) {
            childEvents.clear()
            stroke(x, y)
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP), childEvents)
        }
        assertEquals(0, bookmarkStarts)
        assertEquals(0, bookmarkCommits)
    }

    @Test fun dismissalDoesNotGiveTheRemainderOfAStrokeToBookmarks() {
        host.activeWebView.footnotePopoverVisible = true
        touch(MotionEvent.ACTION_DOWN, 200f, 350f)
        host.activeWebView.footnotePopoverVisible = false
        touch(MotionEvent.ACTION_MOVE, 200f, 550f)
        touch(MotionEvent.ACTION_UP, 200f, 550f)
        assertEquals(0, bookmarkStarts)
        assertEquals(MotionEvent.ACTION_UP, childEvents.last())
        // The next reading-page stroke can add a bookmark again.
        stroke(200f, 550f)
        assertEquals(1, bookmarkStarts)
        assertEquals(1, bookmarkCommits)
    }

    @Test fun preloadPopoverDoesNotBlockActivePageAndReloadClearsState() {
        host.nextWebView.footnotePopoverVisible = true
        stroke(200f, 550f)
        assertEquals(1, bookmarkCommits)
        host.activeWebView.footnotePopoverVisible = true
        host.activeWebView.loadUrl("https://appassets.androidplatform.net/next.xhtml")
        assertFalse(host.activeWebView.footnotePopoverVisible)
    }

    private fun stroke(x: Float, y: Float) {
        touch(MotionEvent.ACTION_DOWN, 200f, 350f)
        touch(MotionEvent.ACTION_MOVE, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 100, action, x, y, 0)
        try { host.dispatchTouchEvent(event) } finally { event.recycle() }
    }
}

package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkdownTouchSessionTest {
    private val session = MarkdownTouchSession(8, 500)
    private fun event(action: Int, time: Long, x: Float = 40f, y: Float = 80f) =
        MotionEvent.obtain(100, time, action, x, y, 0)

    private fun record(action: Int, time: Long, x: Float = 40f, y: Float = 80f, scrolling: Boolean = false) {
        val event = event(action, time, x, y)
        try { session.record(event, scrolling) } finally { event.recycle() }
    }

    @Test fun stationaryTapDoesNotAuthorizeNativeSelection() {
        record(MotionEvent.ACTION_DOWN, 100)
        session.selectionAllowed = true
        val up = event(MotionEvent.ACTION_UP, 160)
        try { session.record(up, false); assertTrue(session.isTap(up)) } finally { up.recycle() }
        assertFalse(session.canStartSelection(160))
        assertFalse("delayed selection callbacks cannot turn a short tap into a long press", session.canStartSelection(900))
    }

    @Test fun swipeReturningToItsStartingPointIsStillNotATapOrSelection() {
        record(MotionEvent.ACTION_DOWN, 100)
        session.selectionAllowed = true
        record(MotionEvent.ACTION_MOVE, 200, y = 140f)
        val up = event(MotionEvent.ACTION_UP, 300)
        try { session.record(up, false); assertFalse(session.isTap(up)) } finally { up.recycle() }
        assertFalse(session.canStartSelection(800))
    }

    @Test fun touchingToStopAFlingCannotOpenMenusEvenIfTheListBecomesIdle() {
        record(MotionEvent.ACTION_DOWN, 100, scrolling = true)
        session.selectionAllowed = true
        val up = event(MotionEvent.ACTION_UP, 180)
        try { session.record(up, false); assertFalse(session.isTap(up)) } finally { up.recycle() }
        assertFalse(session.canStartSelection(800))
    }

    @Test fun cancelledOrInterceptedGestureCannotCreateLateSelections() {
        record(MotionEvent.ACTION_DOWN, 100)
        session.selectionAllowed = true
        session.onScrollStarted()
        assertFalse(session.canStartSelection(800))
        record(MotionEvent.ACTION_DOWN, 100)
        session.selectionAllowed = true
        record(MotionEvent.ACTION_CANCEL, 180)
        assertFalse(session.canStartSelection(800))
    }

    @Test fun deliberateLongPressOnTextRemainsSelectableAfterRelease() {
        record(MotionEvent.ACTION_DOWN, 100)
        session.selectionAllowed = true
        assertTrue(session.canStartSelection(700))
        val up = event(MotionEvent.ACTION_UP, 750)
        try { session.record(up, false); assertFalse(session.isTap(up)) } finally { up.recycle() }
        assertTrue(session.canStartSelection(760))
    }

    @Test fun longPressOnBlankSpaceCannotSelectTheNearestText() {
        record(MotionEvent.ACTION_DOWN, 100)
        assertFalse(session.canStartSelection(800))
    }
}

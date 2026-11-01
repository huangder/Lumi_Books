package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.graphics.PointF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.ui.reader.engine.ReaderMagnifierSink
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuousSelectionHandleDragTest {
    private fun withReader(test: (ContinuousSelectableTextView) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val view = ContinuousSelectableTextView(activity.get()).apply {
                typeface = Typeface.MONOSPACE
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28f)
                setReaderText("abcdefghijklmnopqrstuvwxyz".repeat(100))
                setMagnifierSinkForTest(object : ReaderMagnifierSink {
                    override val windowHeightPx = 80
                    override fun show(sourceX: Float, sourceY: Float, windowCenterX: Float, windowCenterY: Float) {}
                    override fun update() {}
                    override fun dismiss() {}
                })
            }
            activity.get().setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 360, 4000)
            view.onSelectionViewport = { it.set(0, 0, view.width, view.height); true }
            test(view)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private fun touch(view: View, action: Int, point: PointF) {
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, point.x, point.y, 0).also {
            assertTrue(view.onTouchEvent(it))
            it.recycle()
        }
    }

    @Test fun grabbingEitherHandleWithoutMovingKeepsTheExactRange() = withReader { view ->
        val start = view.layout.getLineStart(3) + 4
        val end = start + 8
        for (trailing in listOf(false, true)) {
            Selection.setSelection(view.text as Spannable, start, end)
            val point = view.readerHandleCirclePosition(if (trailing) end else start, trailing)!!
            touch(view, MotionEvent.ACTION_DOWN, point)
            assertEquals(!trailing, view.readerDraggingStartHandle)
            touch(view, MotionEvent.ACTION_MOVE, point)
            touch(view, MotionEvent.ACTION_UP, point)
            assertEquals(start, Selection.getSelectionStart(view.text))
            assertEquals(end, Selection.getSelectionEnd(view.text))
        }
    }

    @Test fun grabbingTheOuterTouchTargetDoesNotJumpToAnotherCharacterOrLine() = withReader { view ->
        val start = view.layout.getLineStart(3) + 2
        val end = start + 12
        val density = view.resources.displayMetrics.density
        for (trailing in listOf(false, true)) {
            Selection.setSelection(view.text as Spannable, start, end)
            val circle = view.readerHandleCirclePosition(if (trailing) end else start, trailing)!!
            val grab = PointF(circle.x + (if (trailing) 18f else -18f) * density,
                circle.y + 18f * density)
            touch(view, MotionEvent.ACTION_DOWN, grab)
            assertEquals(!trailing, view.readerDraggingStartHandle)
            touch(view, MotionEvent.ACTION_MOVE, PointF(grab.x + 1f, grab.y))
            touch(view, MotionEvent.ACTION_UP, PointF(grab.x + 1f, grab.y))
            assertEquals(start, Selection.getSelectionStart(view.text))
            assertEquals(end, Selection.getSelectionEnd(view.text))
        }
    }

    @Test fun horizontalMovesAdvanceAndRetractExactlyOneCharacter() = withReader { view ->
        val start = view.layout.getLineStart(3) + 4
        val end = start + 1
        Selection.setSelection(view.text as Spannable, start, end)
        val point = view.readerHandleCirclePosition(end, true)!!
        val nextX = view.readerHorizontalPosition(end + 1, true)!!
        val deltaX = nextX - view.readerHorizontalPosition(end, true)!!
        touch(view, MotionEvent.ACTION_DOWN, point)
        assertEquals(false, view.readerDraggingStartHandle)
        touch(view, MotionEvent.ACTION_MOVE, PointF(point.x + deltaX, point.y))
        assertEquals(start, Selection.getSelectionStart(view.text))
        assertEquals(end + 1, Selection.getSelectionEnd(view.text))
        touch(view, MotionEvent.ACTION_MOVE, point)
        touch(view, MotionEvent.ACTION_UP, point)
        assertEquals(start, Selection.getSelectionStart(view.text))
        assertEquals(end, Selection.getSelectionEnd(view.text))
    }

    @Test fun aTrailingHandleAtTheWrapBoundaryStaysOnItsOriginalLine() = withReader { view ->
        val start = view.layout.getLineStart(3) + 4
        val end = view.layout.getLineStart(4)
        Selection.setSelection(view.text as Spannable, start, end)
        val point = view.readerHandleCirclePosition(end, true)!!
        touch(view, MotionEvent.ACTION_DOWN, point)
        touch(view, MotionEvent.ACTION_MOVE, point)
        assertEquals(end, Selection.getSelectionEnd(view.text))
        val previousX = view.readerHorizontalPosition(end - 1, true)!!
        touch(view, MotionEvent.ACTION_MOVE, PointF(previousX, point.y))
        assertEquals(end - 1, Selection.getSelectionEnd(view.text))
        touch(view, MotionEvent.ACTION_MOVE, point)
        touch(view, MotionEvent.ACTION_UP, point)
        assertEquals(start, Selection.getSelectionStart(view.text))
        assertEquals(end, Selection.getSelectionEnd(view.text))
    }

    @Test fun scrollingUnderAHeldHandleKeepsTheGrabOffset() = withReader { view ->
        val start = view.layout.getLineStart(3) + 4
        val end = start + 8
        Selection.setSelection(view.text as Spannable, start, end)
        val point = view.readerHandleCirclePosition(end, true)!!
        val lineHeight = view.layout.getLineTop(4) - view.layout.getLineTop(3)
        touch(view, MotionEvent.ACTION_DOWN, point)
        // LazyColumn moves the chapter up while the finger stays in window coordinates.
        assertTrue(view.updateReaderSelectionHandlePosition(point.x, point.y + lineHeight))
        val expected = end + view.layout.getLineStart(4) - view.layout.getLineStart(3)
        assertEquals(start, Selection.getSelectionStart(view.text))
        assertEquals(expected, Selection.getSelectionEnd(view.text))
        touch(view, MotionEvent.ACTION_UP, PointF(point.x, point.y + lineHeight))
        assertEquals(expected, Selection.getSelectionEnd(view.text))
    }

    @Test fun releasingBelowTheViewportCanStillSelectItsLastVisibleLine() = withReader { view ->
        val start = view.layout.getLineStart(3) + 4
        val end = start + 8
        val lastVisibleLine = 6
        val bottom = view.layout.getLineBottom(lastVisibleLine)
        view.onSelectionViewport = { it.set(0, 0, view.width, bottom); true }
        Selection.setSelection(view.text as Spannable, start, end)
        val circle = view.readerHandleCirclePosition(end, true)!!
        val grab = PointF(circle.x, circle.y + 18f * view.resources.displayMetrics.density)
        touch(view, MotionEvent.ACTION_DOWN, grab)
        val target = PointF(grab.x, bottom + 60f)
        touch(view, MotionEvent.ACTION_MOVE, target)
        touch(view, MotionEvent.ACTION_UP, target)
        val expected = end + view.layout.getLineStart(lastVisibleLine) - view.layout.getLineStart(3)
        assertEquals(start, Selection.getSelectionStart(view.text))
        assertEquals(expected, Selection.getSelectionEnd(view.text))
    }
}

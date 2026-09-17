package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionMagnifierInstrumentedTest {

    private data class Point(val x: Float, val y: Float)

    private class ShowCall(
        val sourceX: Float,
        val sourceY: Float,
        val windowCenterX: Float,
        val windowCenterY: Float
    )

    private class RecordingMagnifierSink(windowHeightPx: Int) : ReaderMagnifierSink {
        override val windowHeightPx: Int = windowHeightPx
        val showCalls = mutableListOf<ShowCall>()
        var updateCount = 0
        var dismissCount = 0

        val showCount: Int get() = showCalls.size
        val lastShow: ShowCall? get() = showCalls.lastOrNull()

        override fun show(
            sourceX: Float,
            sourceY: Float,
            windowCenterX: Float,
            windowCenterY: Float
        ) {
            showCalls += ShowCall(sourceX, sourceY, windowCenterX, windowCenterY)
        }

        override fun update() {
            updateCount += 1
        }

        override fun dismiss() {
            dismissCount += 1
        }
    }

    @Test
    fun longPressSelectionShowsSystemMagnifierAndTracksTheFinger() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sink = RecordingMagnifierSink(windowHeightPx = 96)
        lateinit var view: RoundedHighlightTextView
        instrumentation.runOnMainSync {
            view = createView(instrumentation.targetContext)
            view.setMagnifierSinkForTest(sink)
            dispatch(view, MotionEvent.ACTION_DOWN, view.width * 0.5f, lineCenterY(view, 1))
            // A selection that arrives before the long-press timeout must not flash
            // the magnifier (this is the double-tap word-selection path).
            setSelection(view, 2, 5)
            assertEquals(0, sink.showCount)
        }
        instrumentation.waitForIdleSync()

        waitForLongPress()
        assertTrue("long press must show the system magnifier", sink.showCount >= 1)
        val first = requireNotNull(sink.lastShow)
        assertTrue("floating window must sit above the anchored line", first.windowCenterY < first.sourceY)

        instrumentation.runOnMainSync {
            dispatch(view, MotionEvent.ACTION_MOVE, view.width * 0.5f, lineCenterY(view, 2))
        }
        instrumentation.waitForIdleSync()
        assertTrue("moving the finger must reposition the magnifier", sink.showCount >= 2)
        assertTrue(requireNotNull(sink.lastShow).sourceY > first.sourceY)

        instrumentation.runOnMainSync {
            dispatch(view, MotionEvent.ACTION_UP, view.width * 0.5f, lineCenterY(view, 2))
        }
        instrumentation.waitForIdleSync()
        assertTrue(sink.dismissCount >= 1)
    }

    @Test
    fun draggingCustomHandleShowsAndMovesMagnifierThenDismisses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sink = RecordingMagnifierSink(windowHeightPx = 96)
        lateinit var view: RoundedHighlightTextView
        instrumentation.runOnMainSync {
            view = createView(instrumentation.targetContext)
            view.setMagnifierSinkForTest(sink)
            setSelection(view, 2, 6)

            val endHandle = handlePoint(view, offset = 6, trailing = true)
            dispatch(view, MotionEvent.ACTION_DOWN, endHandle.x, endHandle.y)
            assertTrue(view.isReaderSelectionHandleDragActive())
            assertEquals("handle drag must show immediately", 1, sink.showCount)
            val firstSourceX = requireNotNull(sink.lastShow).sourceX

            val endX = handlePoint(view, offset = 9, trailing = true).x
            val endLineCenter = lineCenterY(view, lineOfOffset(view, 9))
            dispatch(view, MotionEvent.ACTION_MOVE, endX, endLineCenter)
            val spannable = view.text as Spannable
            assertEquals(9, Selection.getSelectionEnd(spannable))
            assertTrue(sink.showCount >= 2)
            assertTrue(requireNotNull(sink.lastShow).sourceX != firstSourceX)

            val dismissesBeforeUp = sink.dismissCount
            dispatch(view, MotionEvent.ACTION_UP, endX, endLineCenter)
            assertTrue(sink.dismissCount > dismissesBeforeUp)
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun clearingSelectionDismissesMagnifier() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sink = RecordingMagnifierSink(windowHeightPx = 96)
        lateinit var view: RoundedHighlightTextView
        instrumentation.runOnMainSync {
            view = createView(instrumentation.targetContext)
            view.setMagnifierSinkForTest(sink)
            setSelection(view, 2, 6)

            val start = handlePoint(view, offset = 2, trailing = false)
            dispatch(view, MotionEvent.ACTION_DOWN, start.x, start.y)
            assertEquals(1, sink.showCount)
            val dismissesBeforeClear = sink.dismissCount

            Selection.removeSelection(view.text as Spannable)
            assertTrue(sink.dismissCount > dismissesBeforeClear)
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun magnifierFollowsHandleAfterCrossingTheOtherHandle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sink = RecordingMagnifierSink(windowHeightPx = 96)
        lateinit var view: RoundedHighlightTextView
        instrumentation.runOnMainSync {
            view = createView(instrumentation.targetContext)
            view.setMagnifierSinkForTest(sink)
            setSelection(view, 4, 8)

            // Grab the visual end handle and drag it left past the start handle.
            val endHandle = handlePoint(view, offset = 8, trailing = true)
            dispatch(view, MotionEvent.ACTION_DOWN, endHandle.x, endHandle.y)

            val crossedX = handlePoint(view, offset = 1, trailing = false).x
            val crossedLineCenter = lineCenterY(view, lineOfOffset(view, 1))
            dispatch(view, MotionEvent.ACTION_MOVE, crossedX, crossedLineCenter)
            val crossedSpannable = view.text as Spannable
            assertEquals(4, Selection.getSelectionStart(crossedSpannable))
            assertEquals(1, Selection.getSelectionEnd(crossedSpannable))
            assertEquals(
                "crossed handle must keep the magnifier under the finger",
                crossedX,
                requireNotNull(sink.lastShow).sourceX,
                1.5f
            )

            // Drag back to the right past the other handle again.
            val backX = handlePoint(view, offset = 6, trailing = true).x
            val backLineCenter = lineCenterY(view, lineOfOffset(view, 6))
            dispatch(view, MotionEvent.ACTION_MOVE, backX, backLineCenter)
            assertEquals(
                "re-crossed handle must keep the magnifier under the finger",
                backX,
                requireNotNull(sink.lastShow).sourceX,
                1.5f
            )
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun replacingTextDismissesMagnifier() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sink = RecordingMagnifierSink(windowHeightPx = 96)
        lateinit var view: RoundedHighlightTextView
        instrumentation.runOnMainSync {
            view = createView(instrumentation.targetContext)
            view.setMagnifierSinkForTest(sink)
            setSelection(view, 2, 6)

            val start = handlePoint(view, offset = 2, trailing = false)
            dispatch(view, MotionEvent.ACTION_DOWN, start.x, start.y)
            assertEquals(1, sink.showCount)

            view.setText(SpannableString("替换后的正文内容"), TextView.BufferType.SPANNABLE)
            assertTrue(sink.dismissCount >= 1)
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun imageOnlySelectionDoesNotShowMagnifier() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sink = RecordingMagnifierSink(windowHeightPx = 96)
        lateinit var view: RoundedHighlightTextView
        instrumentation.runOnMainSync {
            view = createView(instrumentation.targetContext)
            val imageText = SpannableString("封面图片占位文字")
            imageText.setSpan(
                ImageSpan(ColorDrawable(Color.RED)),
                0,
                1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            view.setText(imageText, TextView.BufferType.SPANNABLE)
            view.setMagnifierSinkForTest(sink)
            dispatch(view, MotionEvent.ACTION_DOWN, view.width * 0.2f, lineCenterY(view, 0))
            setSelection(view, 0, 1)
        }
        waitForLongPress()
        assertEquals(0, sink.showCount)
    }

    private fun createView(context: Context): RoundedHighlightTextView {
        val density = context.resources.displayMetrics.density
        val width = (360f * density).toInt()
        val height = (480f * density).toInt()
        val padding = (12f * density).toInt()
        return RoundedHighlightTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(width, height)
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f * density)
            setLineSpacing(0f, 1.5f)
            setPadding(padding, 0, padding, 0)
            setText(
                SpannableString(
                    "第一行文字用于验证放大镜跟随。第二行文字用于验证跨行锚点位置。" +
                        "第三行文字用于验证手柄拖动。第四行文字用于兜底测量长度。"
                ),
                TextView.BufferType.SPANNABLE
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, width, height)
        }
    }

    private fun setSelection(view: RoundedHighlightTextView, start: Int, end: Int) {
        val spannable = view.text as Spannable
        Selection.setSelection(spannable, start, end)
    }

    private fun lineCenterY(view: RoundedHighlightTextView, line: Int): Float {
        val textLayout = requireNotNull(view.layout)
        val safeLine = line.coerceIn(0, textLayout.lineCount - 1)
        return (textLayout.getLineTop(safeLine) + textLayout.getLineBottom(safeLine)) / 2f +
            view.totalPaddingTop - view.scrollY
    }

    private fun lineOfOffset(view: RoundedHighlightTextView, offset: Int): Int {
        val textLayout = requireNotNull(view.layout)
        return textLayout.getLineForOffset(offset.coerceIn(0, textLayout.getLineEnd(textLayout.lineCount - 1)))
    }

    private fun handlePoint(
        view: RoundedHighlightTextView,
        offset: Int,
        trailing: Boolean
    ): Point {
        val textLayout = requireNotNull(view.layout)
        val spannable = view.text as Spannable
        val x = ReaderLineGeometry(
            textLayout,
            spannable,
            view.readerJustificationMode,
            view.readerForceLastLineJustification
        ).horizontalPosition(offset, trailing) ?: error("no handle x for $offset")
        val length = spannable.length
        val safeOffset = offset.coerceIn(0, length)
        val line = if (trailing && safeOffset > 0 && safeOffset < length &&
            textLayout.getLineForOffset(safeOffset) > 0 &&
            textLayout.getLineStart(textLayout.getLineForOffset(safeOffset)) == safeOffset
        ) {
            textLayout.getLineForOffset(safeOffset) - 1
        } else {
            textLayout.getLineForOffset(safeOffset.coerceAtMost((length - 1).coerceAtLeast(0)))
        }
        val density = view.resources.displayMetrics.density
        val y = textLayout.getLineBottom(line) + density * 7f + view.totalPaddingTop - view.scrollY
        return Point(x + view.totalPaddingLeft - view.scrollX, y)
    }

    private fun dispatch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun waitForLongPress() {
        SystemClock.sleep(700)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}

package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Color
import android.text.Selection
import android.text.Spannable
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerticalTextSelectionInstrumentedTest {

    @Test
    fun draggingSelectionHandlesUpdatesRangeAndReleasesGesture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val text = "甲乙丙丁戊己庚辛"
            val width = 160
            val height = 240
            val page = VerticalTextLayouter.layout(
                text = text,
                paint = TextPaint().apply {
                    textSize = 20f
                    density = 1f
                },
                width = width,
                height = height,
                lineSpacingExtra = 0f,
                lineSpacingMultiplier = 1f,
                letterSpacing = 0f
            ).single()
            val pageView = PageContentView(instrumentation.targetContext).apply {
                configure(
                    fontSizePx = 20f,
                    textColor = Color.BLACK,
                    marginLeftPx = 0f,
                    marginTopPx = 0f,
                    marginRightPx = 0f,
                    marginBottomPx = 0f,
                    writingMode = ReaderWritingMode.VERTICAL_RL
                )
                setPageContent(text, 0, text.length, verticalGeometry = page.geometry)
                measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, width, height)
            }
            val spannable = requireNotNull(pageView.getTextSpannable())
            Selection.setSelection(spannable, 1, 4)

            val initialStart = page.geometry.glyphs.first { it.startOffset == 1 }.bounds
            dispatch(pageView, MotionEvent.ACTION_DOWN, initialStart.centerX, initialStart.top)
            assertTrue(pageView.isVerticalSelectionHandleDragActive())

            val newStart = page.geometry.glyphs.first { it.startOffset == 0 }.bounds
            dispatch(pageView, MotionEvent.ACTION_MOVE, newStart.centerX, newStart.centerY)
            assertSelection(spannable, 0, 4)
            dispatch(pageView, MotionEvent.ACTION_UP, newStart.centerX, newStart.centerY)
            assertFalse(pageView.isVerticalSelectionHandleDragActive())

            val initialEnd = page.geometry.glyphs.first { it.endOffset == 4 }.bounds
            dispatch(pageView, MotionEvent.ACTION_DOWN, initialEnd.centerX, initialEnd.bottom)
            assertTrue(pageView.isVerticalSelectionHandleDragActive())

            val newEnd = page.geometry.glyphs.first { it.endOffset == 7 }.bounds
            dispatch(pageView, MotionEvent.ACTION_MOVE, newEnd.centerX, newEnd.centerY)
            assertSelection(spannable, 0, 7)
            dispatch(pageView, MotionEvent.ACTION_UP, newEnd.centerX, newEnd.centerY)
            assertFalse(pageView.isVerticalSelectionHandleDragActive())
        }
    }

    private fun dispatch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            assertTrue(view.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun assertSelection(spannable: Spannable, start: Int, end: Int) {
        assertEquals(start, Selection.getSelectionStart(spannable))
        assertEquals(end, Selection.getSelectionEnd(spannable))
    }
}

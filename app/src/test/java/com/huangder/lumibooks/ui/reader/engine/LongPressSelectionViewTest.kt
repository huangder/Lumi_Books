package com.huangder.lumibooks.ui.reader.engine

import android.app.Activity
import android.os.SystemClock
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LongPressSelectionViewTest {
    @Test fun pagedLineSelectionKeepsVisibleDraggableHandlesAfterControllerReplacement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val page = PageContentView(activity.get())
            activity.get().setContentView(page)
            val accent = android.graphics.Color.BLUE
            page.configure(24f, android.graphics.Color.BLACK, marginLeftPx = 24f,
                marginRightPx = 24f, marginTopPx = 24f, marginBottomPx = 24f, accentColor = accent)
            val content = "第一行文字需要自动换行，用来验证整行选区的两端手柄仍然可见并且可以拖动。".repeat(6)
            page.setPageContent(content, 0, content.length)
            page.longPressSelectionRange = LongPressSelectionRange.LINE
            fun layoutPage() {
                page.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
                page.layout(0, 0, 400, 700)
            }
            layoutPage()
            val view = page.textView as RoundedHighlightTextView
            view.setMagnifierSinkForTest(object : ReaderMagnifierSink {
                override val windowHeightPx = 80
                override fun show(sourceX: Float, sourceY: Float, windowCenterX: Float, windowCenterY: Float) {}
                override fun update() {}
                override fun dismiss() {}
            })
            val line = 2
            val offset = view.layout.getLineStart(line) + 3
            val x = view.layout.getPrimaryHorizontal(offset) + view.totalPaddingLeft
            val y = (view.layout.getLineTop(line) + view.layout.getLineBottom(line)) / 2f + view.totalPaddingTop
            val now = SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).also {
                view.onTouchEvent(it); it.recycle()
            }
            Selection.setSelection(view.text as Spannable, offset, offset + 1)
            RoundedHighlightTextView::class.java.getDeclaredMethod("onMagnifierLongPressReady")
                .apply { isAccessible = true }.invoke(view)
            shadowOf(Looper.getMainLooper()).idle()
            layoutPage()
            val start = Selection.getSelectionStart(view.text)
            val end = Selection.getSelectionEnd(view.text)
            assertEquals(view.layout.getLineStart(line), start)
            assertTrue(end > start)
            MotionEvent.obtain(now, now + 600, MotionEvent.ACTION_UP, x, y, 0).also {
                view.onTouchEvent(it); it.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
            layoutPage()
            val bitmap = android.graphics.Bitmap.createBitmap(400, 700, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            for ((position, trailing) in listOf(start to false, end to true)) {
                val point = view.readerHandleCirclePosition(position, trailing)!!
                assertEquals("handle must be painted at its hit target", accent,
                    bitmap.getPixel(point.x.toInt(), point.y.toInt()))
                MotionEvent.obtain(now + 700, now + 700, MotionEvent.ACTION_DOWN, point.x, point.y, 0).also {
                    view.onTouchEvent(it); it.recycle()
                }
                assertTrue(view.isReaderSelectionHandleDragActive())
                MotionEvent.obtain(now + 700, now + 750, MotionEvent.ACTION_UP, point.x, point.y, 0).also {
                    view.onTouchEvent(it); it.recycle()
                }
            }
            bitmap.recycle()
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun paragraphExtendsPastPageOnReleaseButPreservesManualAdjustment() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val reader = ReadView(activity.get())
        try {
            val chapter = "This is one long paragraph that continues across several reader pages. ".repeat(40).trim()
            activity.get().setContentView(reader)
            reader.setContentProvider { chapter }
            reader.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            reader.layout(0, 0, 400, 400)
            reader.configure(24f, "day", 1, 0, 0, marginLeftDp = 12f, marginRightDp = 12f,
                marginTopDp = 12f, marginBottomDp = 12f, width = 400, height = 400)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!reader.slotManager.getCurSlot().isLoaded && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.yield()
            }
            assertTrue(reader.slotManager.getCurSlot().isLoaded)
            reader.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            reader.layout(0, 0, 400, 400)
            while ((!reader.slotManager.getCurSlot().isLoaded ||
                    reader.slotManager.getPrimaryContentView().textView.text.isNullOrEmpty()) &&
                System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.yield()
            }
            reader.setLongPressSelectionRange(LongPressSelectionRange.PARAGRAPH)
            val page = reader.slotManager.getPrimaryContentView()
            val source = page.textView.text as Spannable
            assertTrue(source.isNotEmpty())
            assertTrue(source.length < chapter.length)
            val resolver = page.initialSelectionRangeProvider!!
            val finish = ReadView::class.java.getDeclaredMethod("finishInitialSelectionExtension").apply { isAccessible = true }
            val local = resolver(3, 0 until source.length)!!
            Selection.setSelection(source, local.first, local.last + 1)
            finish.invoke(reader)
            assertEquals(chapter, reader.getSelectionInfo()?.selectedText)
            assertTrue(reader.hasActiveReaderSelection())

            reader.clearActiveTextSelection()
            resolver(3, 0 until source.length)
            Selection.setSelection(source, 1, 5)
            finish.invoke(reader)
            assertFalse(reader.hasActiveReaderSelection())
            assertEquals(source.subSequence(1, 5).toString(), reader.getSelectionInfo()?.selectedText)
        } finally {
            reader.slotManager.destroy()
            activity.pause().stop().destroy()
        }
    }

    @Test fun initialScopeUsesTouchedLineAndManualChangesRemainPossible() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val view = RoundedHighlightTextView(activity.get()).apply {
                setTextIsSelectable(true)
                readerLongPressSelectionDragEnabled = true
                textSize = 20f
                text = "First sentence. Second sentence with several words that wrap to another line."
            }
            activity.get().setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 250, 600)
            val source = view.text as Spannable
            val offset = view.text.indexOf("Second") + 2
            val line = view.layout.getLineForOffset(offset)
            val x = view.layout.getPrimaryHorizontal(offset) + view.totalPaddingLeft
            val y = (view.layout.getLineTop(line) + view.layout.getLineBottom(line)) / 2f + view.totalPaddingTop
            val ready = RoundedHighlightTextView::class.java.getDeclaredMethod("onMagnifierLongPressReady").apply { isAccessible = true }
            for (scope in LongPressSelectionRange.entries) {
                Selection.removeSelection(source)
                view.longPressSelectionRange = scope
                val now = SystemClock.uptimeMillis()
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).also {
                    view.onTouchEvent(it); it.recycle()
                }
                // The framework selects a word; the reader then applies the user's initial scope.
                Selection.setSelection(source, offset - 2, offset + 4)
                ready.invoke(view)
                val start = Selection.getSelectionStart(source)
                val end = Selection.getSelectionEnd(source)
                when (scope) {
                    LongPressSelectionRange.PARAGRAPH -> assertEquals(view.text.toString(), source.subSequence(start, end).toString())
                    LongPressSelectionRange.LINE -> assertEquals(view.layout.getLineStart(line), start)
                    LongPressSelectionRange.SENTENCE -> assertEquals("Second sentence with several words that wrap to another line.", source.subSequence(start, end).toString())
                    LongPressSelectionRange.CHARACTER -> assertEquals("c", source.subSequence(start, end).toString())
                    LongPressSelectionRange.WORD -> assertEquals("Second", source.subSequence(start, end).toString())
                }
                MotionEvent.obtain(now, now + 600, MotionEvent.ACTION_UP, x, y, 0).also {
                    view.onTouchEvent(it); it.recycle()
                }
                Selection.setSelection(source, 1, 3)
                assertEquals(1, Selection.getSelectionStart(source))
                assertEquals(3, Selection.getSelectionEnd(source))
            }
        } finally { activity.pause().stop().destroy() }
    }
}

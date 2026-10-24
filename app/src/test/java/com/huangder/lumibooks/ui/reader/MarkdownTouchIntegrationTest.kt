package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.huangder.lumibooks.ui.reader.engine.ReaderMagnifierSink
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.util.markdown.native.MarkdownNativeParser
import com.huangder.lumibooks.util.markdown.native.MarkwonBlockSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownTouchIntegrationTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun withSurface(markdown: String, initialOffset: Int = 0, test: (MarkdownNativeSurface, TextView) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val source = MarkwonBlockSource(activity.get(), MarkdownNativeParser.parse(markdown), emptyMap())
        val surface = MarkdownNativeSurface(activity.get(), source)
        surface.failed = { throw AssertionError(it) }
        try {
            val state = ReaderUiState(epubLocatorJson = com.huangder.lumibooks.util.markdown.MarkdownLocatorMigration.locator(
                source.document.text, com.huangder.lumibooks.util.markdown.MarkdownLocatorMigration.Match(initialOffset, initialOffset)))
            surface.configure(state, Typeface.DEFAULT, android.graphics.Color.BLACK, false, true)
            activity.get().setContentView(surface, ViewGroup.LayoutParams(600, 900))
            val deadline = System.nanoTime() + 10_000_000_000L
            var text: TextView? = null
            while (text == null && System.nanoTime() < deadline) {
                ShadowLooper.idleMainLooper()
                surface.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
                surface.layout(0, 0, 600, 900)
                text = descendants(surface).filterIsInstance<TextView>().firstOrNull { it.text.isNotEmpty() }
                if (text == null) Thread.yield()
            }
            assertNotNull("the native block must render before touch assertions", text)
            test(surface, text!!)
        } finally {
            surface.release(); source.close(); activity.pause().stop().destroy()
        }
    }

    private fun touch(surface: MarkdownNativeSurface, text: TextView, swipe: Boolean = false) {
        val position = IntArray(2); val origin = IntArray(2)
        text.getLocationOnScreen(position); surface.getLocationOnScreen(origin)
        val x = position[0] - origin[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(2)
        val y = position[1] - origin[1] + text.totalPaddingTop + text.layout.getLineBottom(0) / 2f
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, time: Long, atY: Float) {
            val event = MotionEvent.obtain(down, time, action, x, atY, 0)
            try { surface.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, down, y)
        if (swipe) send(MotionEvent.ACTION_MOVE, down + 40, y - 80)
        send(MotionEvent.ACTION_UP, down + 80, if (swipe) y - 80 else y)
        ShadowLooper.idleMainLooper()
    }

    @Test fun shortTapsOnCodeAndDocumentStrikethroughCannotPublishSelections() {
        for (markdown in listOf("```kotlin\nval value = 10\n```", "~~document strikethrough~~",
            "| Heading | Value |\n| --- | --- |\n| Cell | Text |", "> Quoted text")) {
            withSurface(markdown) { surface, text ->
                var selections = 0
                surface.selected = { selections++ }
                touch(surface, text)
                // Android may produce an asynchronous range after processing a short tap.
                Selection.setSelection(text.text as Spannable, 0, 4)
                ShadowLooper.idleMainLooper()
                assertEquals(0, selections)
            }
        }
    }

    @Test fun savedAnnotationsOpenOnTapButNotOnSwipe() {
        withSurface("Annotated text\n\n" + "More lines.\n\n".repeat(40)) { surface, text ->
            val note = Note(bookId = "md", chapterIndex = 0, startPosition = 0, endPosition = 9,
                selectedText = "Annotated", note = "", color = "#FFCA28", createdAt = 0)
            surface.setNotes(listOf(note))
            var selected: EpubSelectionInfo? = null
            surface.selected = { selected = it }
            touch(surface, text)
            assertNotNull(selected)
            assertTrue(selected!!.annotationOnly)
            assertEquals("Annotated", selected!!.text)
            surface.clearSelection(1)
            selected = null
            touch(surface, text, swipe = true)
            assertNull(selected)
        }
    }

    @Test fun longPressOnFormattedTextKeepsSelectionAfterRelease() {
        withSurface("~~Formatted text~~") { surface, text ->
            var selected: EpubSelectionInfo? = null
            surface.selected = { selected = it }
            val position = IntArray(2); val origin = IntArray(2)
            text.getLocationOnScreen(position); surface.getLocationOnScreen(origin)
            val x = position[0] - origin[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(2)
            val y = position[1] - origin[1] + text.totalPaddingTop + text.layout.getLineBottom(0) / 2f
            val down = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
            try { surface.dispatchTouchEvent(downEvent) } finally { downEvent.recycle() }
            ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS)
            val up = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            try { surface.dispatchTouchEvent(up) } finally { up.recycle() }
            ShadowLooper.idleMainLooper()
            assertNotNull(selected)
            assertFalse(selected!!.annotationOnly)
            assertEquals("Formatted", selected!!.text)
        }
    }

    @Test fun realLongPressSelectsTextInCodeQuotesHeadingsAndTables() {
        for (markdown in listOf("```kotlin\nval value = 10\n```", "> Quoted words", "# Heading words",
            "| Heading | Value |\n| --- | --- |\n| Cell | Text |", "中文段落可以选择文字")) {
            withSurface(markdown) { surface, text ->
                var selected: EpubSelectionInfo? = null
                var menuOpens = 0
                surface.selected = { selected = it }
                surface.menu = { menuOpens++ }
                val position = IntArray(2); val origin = IntArray(2)
                text.getLocationOnScreen(position); surface.getLocationOnScreen(origin)
                val x = position[0] - origin[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(2)
                val y = position[1] - origin[1] + text.totalPaddingTop + text.layout.getLineBottom(0) / 2f
                val down = SystemClock.uptimeMillis()
                val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
                try { surface.dispatchTouchEvent(press) } finally { press.recycle() }
                ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS)
                val up = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
                try { surface.dispatchTouchEvent(up) } finally { up.recycle() }
                ShadowLooper.idleMainLooper()
                assertNotNull("long press must select: $markdown", selected)
                assertTrue(selected!!.text.isNotBlank())
                assertEquals(selected!!.text, MarkdownNativeParser.parse(markdown).text.substring(
                    selected!!.startPosition, selected!!.endPosition))
                assertEquals("long press must not toggle the reader menu", 0, menuOpens)
            }
        }
    }

    @Test fun scrollingCoalescesProgressEvenWhenEachFrameMovesManyCharacters() {
        withSurface("# Heading\n\n" + "Long paragraph to scroll smoothly. ".repeat(4000)) { surface, _ ->
            val list = descendants(surface).filterIsInstance<androidx.recyclerview.widget.RecyclerView>().single()
            val times = mutableListOf<Long>()
            val positions = mutableListOf<Int>()
            ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            surface.pageChanged = { _, _, locator ->
                times += SystemClock.uptimeMillis()
                positions += org.json.JSONObject(locator).getInt("textPosition")
            }
            repeat(24) {
                list.scrollBy(0, 35)
                ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue("scrolling must publish progress", times.size >= 2)
            assertTrue("progress must be throttled, not emitted every frame: $times",
                times.zipWithNext().all { (before, after) -> after - before >= 80 })
            assertTrue("progress moves forward", positions.zipWithNext().all { (before, after) -> after > before })
            // The last coalesced position must not be lost when the gesture stops.
            val manager = list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            val item = manager.findFirstVisibleItemPosition()
            val frame = manager.findViewByPosition(item) as ViewGroup
            val tv = frame.getChildAt(0) as TextView
            val block = com.huangder.lumibooks.util.markdown.native.MarkdownNativeParser.parse(
                "# Heading\n\n" + "Long paragraph to scroll smoothly. ".repeat(4000)).blocks[item]
            val line = tv.layout.getLineForVertical((-frame.top - tv.totalPaddingTop).coerceAtLeast(0))
            assertEquals(block.start + tv.layout.getLineStart(line), positions.last())
            val count = times.size
            surface.release()
            ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertEquals("released readers must not publish queued progress", count, times.size)
        }
    }

    @Test fun distantRestoreAfterGroupedTableUsesAdapterCoordinatesAndRendersText() {
        val markdown = "# Title\n\n| Head | Value |\n| --- | --- |\n" +
            (1..20).joinToString("\n") { "| Row $it | Value $it |" } + "\n\n" +
            (1..30).joinToString("\n\n") { "Paragraph $it " + "Readable text. ".repeat(20) }
        val document = MarkdownNativeParser.parse(markdown)
        val offset = document.text.indexOf("Paragraph 20")
        withSurface(markdown, initialOffset = offset) { surface, _ ->
            ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            val list = descendants(surface).filterIsInstance<androidx.recyclerview.widget.RecyclerView>().single()
            val manager = list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            val item = manager.findFirstVisibleItemPosition()
            val frame = manager.findViewByPosition(item) as ViewGroup
            val text = frame.getChildAt(0) as TextView
            assertTrue("restored text must use the grouped adapter position", text.text.startsWith("Paragraph 20"))
            var position = -1
            surface.pageChanged = { _, _, locator -> position = org.json.JSONObject(locator).getInt("textPosition") }
            list.scrollBy(0, 50)
            ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue("progress must use the source block, not the table's adapter index", position >= offset)
        }
    }

    private class MagnifierRecorder : ReaderMagnifierSink {
        override val windowHeightPx = 48
        val anchors = mutableListOf<Pair<Float, Float>>()
        var dismissals = 0
        override fun show(sourceX: Float, sourceY: Float, windowCenterX: Float, windowCenterY: Float) {
            anchors += sourceX to sourceY
            assertTrue("the lens must stay above the selected line", windowCenterY < sourceY)
        }
        override fun update() = Unit
        override fun dismiss() { dismissals++ }
    }

    private fun select(surface: MarkdownNativeSurface, text: TextView, start: Int, end: Int) {
        val position = IntArray(2); val origin = IntArray(2)
        text.getLocationOnScreen(position); surface.getLocationOnScreen(origin)
        val local = start.coerceAtMost(text.text.length - 1)
        val line = text.layout.getLineForOffset(local)
        val x = position[0] - origin[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(local)
        val y = position[1] - origin[1] + text.totalPaddingTop +
            (text.layout.getLineTop(line) + text.layout.getLineBottom(line)) / 2f
        val down = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        try { surface.dispatchTouchEvent(event) } finally { event.recycle() }
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS)
        Selection.setSelection(text.text as Spannable, start, end)
        ShadowLooper.idleMainLooper()
        val up = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        try { surface.dispatchTouchEvent(up) } finally { up.recycle() }
        ShadowLooper.idleMainLooper()
    }

    @Test fun endHandleOfShortSelectionWinsOverOverlappingStartHandleAndShowsMagnifier() {
        withSurface("abcdefghijk") { surface, text ->
            val magnifier = MagnifierRecorder()
            surface.setMagnifierSinkForTest(magnifier)
            var selection: EpubSelectionInfo? = null
            val dragging = mutableListOf<Boolean>()
            surface.selected = { selection = it }
            surface.selectionDragging = { dragging += it }
            select(surface, text, 2, 3)
            assertEquals(2, selection!!.startPosition)
            assertEquals(3, selection!!.endPosition)
            val position = IntArray(2); val origin = IntArray(2)
            text.getLocationOnScreen(position); surface.getLocationOnScreen(origin)
            val x = position[0] - origin[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(3)
            val y = position[1] - origin[1] + text.totalPaddingTop + text.layout.getLineBottom(0) +
                7f * surface.resources.displayMetrics.density
            val nextX = position[0] - origin[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(6)
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, time: Long, atX: Float) {
                val event = MotionEvent.obtain(down, time, action, atX, y, 0)
                try { surface.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            // Native Editor leaves this flag set on some devices after long press.
            surface.requestDisallowInterceptTouchEvent(true)
            send(MotionEvent.ACTION_DOWN, down, x)
            assertEquals(listOf(true), dragging)
            assertTrue(magnifier.anchors.isNotEmpty())
            send(MotionEvent.ACTION_MOVE, down + 40, nextX)
            // A late callback from the old TextView selection cannot overwrite the handle drag.
            Selection.setSelection(text.text as Spannable, 0, 1)
            ShadowLooper.idleMainLooper()
            send(MotionEvent.ACTION_UP, down + 80, nextX)
            assertEquals(listOf(true, false), dragging)
            assertEquals(2, selection!!.startPosition)
            assertEquals(6, selection!!.endPosition)
            assertTrue(magnifier.anchors.size >= 2)
            assertTrue(magnifier.dismissals > 0)
        }
    }

    @Test fun wrappedEndHandleStaysOnPreviousLineAndCancellationClosesMagnifier() {
        withSurface("abcdefghij  \nklmnopqrst") { surface, text ->
            val magnifier = MagnifierRecorder()
            surface.setMagnifierSinkForTest(magnifier)
            val dragging = mutableListOf<Boolean>()
            surface.selectionDragging = { dragging += it }
            select(surface, text, 2, 11)
            val position = IntArray(2); val origin = IntArray(2)
            text.getLocationOnScreen(position); surface.getLocationOnScreen(origin)
            val x = position[0] - origin[0] + text.totalPaddingLeft + text.layout.getLineRight(0)
            val y = position[1] - origin[1] + text.totalPaddingTop + text.layout.getLineBottom(0) +
                7f * surface.resources.displayMetrics.density
            val down = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
            try { surface.dispatchTouchEvent(event) } finally { event.recycle() }
            assertEquals(listOf(true), dragging)
            assertEquals(position[1] - origin[1] + text.totalPaddingTop +
                (text.layout.getLineTop(0) + text.layout.getLineBottom(0)) / 2f, magnifier.anchors.last().second, 1f)
            val cancel = MotionEvent.obtain(down, down + 40, MotionEvent.ACTION_CANCEL, x, y, 0)
            try { surface.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
            assertEquals(listOf(true, false), dragging)
            assertTrue(magnifier.dismissals > 0)
        }
    }
}

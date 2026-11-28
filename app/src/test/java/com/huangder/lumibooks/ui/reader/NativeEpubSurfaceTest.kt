package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.util.epub.EpubRenderSession
import com.huangder.lumibooks.util.epub.epub3Entries
import com.huangder.lumibooks.util.epub.writeEpub
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeEpubSurfaceTest {
    @Test fun warmedChapterSharesTheSlideGestureAndReturnsToThePreviousPage() {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("native-turn", ".epub", context.cacheDir)
        val entries = epub3Entries().toMutableMap()
        entries.computeIfPresent("OPS/package.opf") { _, opf ->
            opf.replace("page-progression-direction=\"rtl\"", "page-progression-direction=\"ltr\"")
                .replace(" properties=\"rendition:layout-pre-paginated\"", "")
        }
        entries["OPS/Text/chapter 1.xhtml"] = "<html><body style='background:red'><p id='start'>First</p></body></html>"
        entries["OPS/Text/chapter2.xhtml"] = "<html><body style='background:blue'><p>Second</p></body></html>"
        writeEpub(file, entries)
        try {
            EpubRenderSession.open(file.absolutePath).use { session ->
                val surface = NativeEpubSurface(context, session, checkNotNull(session.compileNativeChapter(0)?.document), 0)
                val pages = mutableListOf<Pair<Int, Int>>()
                val promoted = mutableListOf<Int>()
                surface.events = { NativeEpubCallbacks(
                    page = { chapter, page, _, _ -> pages += chapter to page },
                    count = { _, _, _ -> }, signature = {}, chapter = { error("Skipped native page animation") },
                    chapterPrepared = { chapter, _ -> promoted += chapter }, handoff = { _, _ -> error("Unexpected WebView handoff") },
                    menu = {}, manual = {}, selection = {}, selectionCleared = {}, internalLink = { _, _ -> }, externalLink = {},
                    search = { _, _ -> }, navigation = {}, failure = { error("Native layout failed") }, bookmarkStart = {},
                    bookmarkProgress = { _, _ -> }, bookmarkFinish = {}, image = {}) }
                val frame = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
                val main = shadowOf(Looper.getMainLooper())
                try {
                    surface.configure(NativeEpubReaderStyle(18f, "system", null, 400f, null, Color.WHITE, true,
                        12f, 12f, 12f, 12f, EpubTextSpacing(1.5f, 2f, 2f)), "slide", 120,
                        ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT, "fixture")
                    surface.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
                    surface.layout(0, 0, 320, 480)
                    await { pages == listOf(0 to 0) }
                    surface.draw(Canvas(frame))
                    assertEquals(Color.RED, frame.getPixel(310, 440))
                    val down = SystemClock.uptimeMillis()
                    fun touch(action: Int, x: Float) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, 240f, 0)
                        try { surface.onTouchEvent(event) } finally { event.recycle() }
                    }
                    touch(MotionEvent.ACTION_DOWN, 280f)
                    touch(MotionEvent.ACTION_MOVE, 130f)
                    await {
                        surface.draw(Canvas(frame))
                        val incoming = frame.getPixel(310, 440)
                        Color.red(incoming) == 0 && Color.blue(incoming) > 128
                    }
                    assertEquals(listOf(0 to 0), pages)
                    touch(MotionEvent.ACTION_UP, 130f)
                    repeat(8) { main.idleFor(Duration.ofMillis(32)); surface.computeScroll() }
                    assertEquals(listOf(0 to 0, 1 to 0), pages)
                    surface.draw(Canvas(frame))
                    assertEquals(Color.BLUE, frame.getPixel(310, 440))
                    assertTrue(surface.turn(-1))
                    repeat(8) { main.idleFor(Duration.ofMillis(32)); surface.computeScroll() }
                    assertEquals(listOf(0 to 0, 1 to 0, 0 to 0), pages)
                    assertEquals(listOf(1, 0), promoted)
                    surface.draw(Canvas(frame))
                    assertEquals(Color.RED, frame.getPixel(310, 440))
                } finally { surface.release(); frame.recycle() }
            }
        } finally { file.delete() }
    }

    @Test fun rapidTapsAndSwipesAdvanceEachSlide() = verifyRapidTurns("slide")

    @Test fun rapidTapsAndSwipesAdvanceEachWave() = verifyRapidTurns("wave")

    @Test fun rapidTapsAndSwipesAdvanceEachCurl() = verifyRapidTurns("curl")

    @Test fun rapidTurnsWaitForChapterPreloadAndContinueFromTheNewChapter() = verifyRapidTurns("slide", true)

    private fun verifyRapidTurns(transition: String, atChapterEnd: Boolean = false) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = activity.get()
        val file = File.createTempFile("native-rapid-turn", ".epub", context.cacheDir)
        val entries = epub3Entries().toMutableMap()
        entries.computeIfPresent("OPS/package.opf") { _, opf ->
            opf.replace("page-progression-direction=\"rtl\"", "page-progression-direction=\"ltr\"")
                .replace(" properties=\"rendition:layout-pre-paginated\"", "")
        }
        val paragraphs = "<p>${"Words for a multi-page chapter. ".repeat(6)}</p>".repeat(32)
        entries["OPS/Text/chapter 1.xhtml"] = "<html><body>$paragraphs</body></html>"
        entries["OPS/Text/chapter2.xhtml"] = "<html><body>$paragraphs</body></html>"
        writeEpub(file, entries)
        try {
            EpubRenderSession.open(file.absolutePath).use { session ->
                val surface = NativeEpubSurface(context, session, checkNotNull(session.compileNativeChapter(0)?.document), 0)
                val chapterReady = CompletableDeferred<Unit>()
                surface.prepareChapterNotes = { if (atChapterEnd) chapterReady.await() }
                if (atChapterEnd) surface.restoredProgression = 1f
                val pages = mutableListOf<Pair<Int, Int>>()
                surface.events = { NativeEpubCallbacks(
                    page = { chapter, page, _, _ -> pages += chapter to page },
                    count = { _, _, _ -> }, signature = {}, chapter = { error("Unexpected chapter fallback") },
                    chapterPrepared = { _, _ -> }, handoff = { _, _ -> error("Unexpected WebView handoff") },
                    menu = { error("Unexpected center tap") }, manual = {}, selection = {}, selectionCleared = {},
                    internalLink = { _, _ -> }, externalLink = {}, search = { _, _ -> }, navigation = {},
                    failure = { error("Native layout failed") }, bookmarkStart = {}, bookmarkProgress = { _, _ -> },
                    bookmarkFinish = {}, image = {}) }
                val main = shadowOf(Looper.getMainLooper())
                val frame = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(frame)
                fun advanceFrame() {
                    main.idleFor(Duration.ofMillis(16))
                    surface.computeScroll()
                    surface.draw(canvas)
                }
                fun gesture(start: Float, end: Float = start, finalAction: Int = MotionEvent.ACTION_UP) {
                    val down = SystemClock.uptimeMillis()
                    fun touch(action: Int, x: Float) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, 240f, 0)
                        try { surface.onTouchEvent(event) } finally { event.recycle() }
                    }
                    touch(MotionEvent.ACTION_DOWN, start)
                    if (start != end) touch(MotionEvent.ACTION_MOVE, end)
                    touch(finalAction, end)
                }
                try {
                    activity.get().setContentView(surface)
                    surface.configure(NativeEpubReaderStyle(18f, "system", null, 400f, null, Color.WHITE, true,
                        12f, 12f, 12f, 12f, EpubTextSpacing(1.5f, 2f, 2f)), transition, 260,
                        ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT, "rapid-fixture")
                    surface.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
                    surface.layout(0, 0, 320, 480)
                    await { pages.isNotEmpty() }
                    val initial = pages.last()
                    pages.clear()
                    gesture(290f)
                    gesture(290f)
                    gesture(270f, 60f)
                    gesture(270f, 60f, MotionEvent.ACTION_CANCEL)
                    gesture(270f, 60f)
                    assertTrue(pages.isEmpty())
                    chapterReady.complete(Unit)
                    val forward = if (atChapterEnd) (0..3).map { 1 to it }
                        else (1..4).map { 0 to (initial.second + it) }
                    await { advanceFrame(); pages.size >= forward.size }
                    assertEquals(forward, pages)
                    val (chapter, page) = forward.last()
                    gesture(30f)
                    surface.setDecorations(emptyList(), null, false, Color.YELLOW, 1)
                    gesture(60f, 270f)
                    gesture(290f)
                    gesture(60f, 270f)
                    val expected = forward + listOf(chapter to (page - 1), chapter to (page - 2))
                    await { advanceFrame(); pages.size >= expected.size }
                    repeat(32) { advanceFrame() }
                    assertEquals(expected, pages)
                } finally { surface.release(); frame.recycle() }
            }
        } finally { activity.destroy(); file.delete() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Native EPUB page was not ready", condition())
    }
}

package com.huangder.lumibooks.ui.reader.engine

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.tts.TtsPageChangeOrigin
import com.huangder.lumibooks.util.parser.EpubParser
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderImageTapEventTest {
    private val activity = Robolectric.buildActivity(Activity::class.java)
    private lateinit var reader: ReadView
    private var menus = 0
    private var links = 0
    private var longPresses = 0
    private var pageChanges = 0

    @Before fun setUp() {
        val screen = activity.setup().get()
        reader = ReadView(screen)
        screen.setContentView(reader)
        reader.setBookmarkPullEnabled(false)
        reader.setCallbacks(object : ReadViewCallbacks {
            override fun onMenuToggle() { menus++ }
            override fun onLoadingChanged(isLoading: Boolean) = Unit
            override fun onPageChanged(globalPage: Int, chapterIndex: Int, pageInChapter: Int,
                chapterTotalPages: Int, origin: TtsPageChangeOrigin) { pageChanges++ }
            override fun onLinkClick(href: String, x: Float, y: Float) { links++ }
            override fun onImageLongPress(chapterIndex: Int, image: ReaderImageHit) { longPresses++ }
        })
        reader.slotManager.getCurSlot().apply { chapterIndex = 1; pageIndex = 0; isLoaded = true }
        showImage()
    }

    private fun showImage(linked: Boolean = false) {
        val image = ColorDrawable(Color.BLUE).apply { setBounds(0, 0, 400, 800) }
        val text = SpannableString("\uFFFC").apply {
            setSpan(ImageSpan(image, "comic.jpg"), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(EpubParser.CoverPageSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (linked) setSpan(URLSpan("chapter2.xhtml"), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        reader.curPageView.setPageContent(text, 0, text.length)
        reader.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        reader.layout(0, 0, 400, 800)
        assertEquals(View.INVISIBLE, reader.curPageView.textView.visibility)
    }

    private fun event(action: Int, down: Long, elapsed: Long, x: Float = 200f, y: Float = 400f) {
        val event = MotionEvent.obtain(down, down + elapsed, action, x, y, 0)
        assertTrue(reader.dispatchTouchEvent(event))
        event.recycle()
    }

    private fun tap(x: Float = 200f) {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, down, 0, x)
        event(MotionEvent.ACTION_UP, down, 60, x)
    }

    @After fun tearDown() { activity.pause().stop().destroy() }

    @Test fun fullPageImageCenterTogglesExactlyOnceInEveryTransition() {
        for (transition in listOf("curl", "slide", "scroll", "fade", "none")) {
            reader.setPageTransition(transition)
            val before = menus
            val location = reader.getCurrentLocation()
            tap()
            assertEquals(transition, before + 1, menus)
            assertEquals(location, reader.getCurrentLocation())
            assertFalse(reader.animationController.isDragging)
        }
        assertEquals(0, pageChanges)
    }

    @Test fun edgeTapHasOneOwnerAndDoesNotToggleMenu() {
        var edges = 0
        reader.animationController.onTapRight = { edges++ }
        tap(340f)
        assertEquals(1, edges)
        assertEquals(0, menus)
    }

    @Test fun linkedImageKeepsLinkPriority() {
        reader.setPageTransition("curl")
        showImage(linked = true)
        tap()
        assertEquals(1, links)
        assertEquals(0, menus)
    }

    @Test fun cancelledOrMovedTouchCannotOpenMenu() {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, down, 0)
        event(MotionEvent.ACTION_CANCEL, down, 60)
        event(MotionEvent.ACTION_DOWN, down + 100, 0)
        event(MotionEvent.ACTION_MOVE, down + 100, 40, y = 470f)
        event(MotionEvent.ACTION_UP, down + 100, 60, y = 470f)
        assertEquals(0, menus)
    }

    @Test fun imageLongPressOpensPreviewWithoutMenuOnRelease() {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, down, 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        event(MotionEvent.ACTION_UP, down, 650)
        assertEquals(1, longPresses)
        assertEquals(0, menus)
    }

    @Test fun consumingChildCannotHideOrDuplicateTheTap() {
        reader.curPageView.addView(View(reader.context).apply {
            setOnTouchListener { _, _ -> parent.requestDisallowInterceptTouchEvent(true); true }
        }, android.widget.FrameLayout.LayoutParams(400, 800))
        reader.curPageView.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        reader.curPageView.layout(0, 0, 400, 800)
        tap()
        assertEquals(1, menus)
    }

    @Test fun ttsSingleTapWaitsAndDoubleTapDoesNotOpenMenu() {
        reader.setTtsSentenceJumpEnabled(true)
        tap()
        assertEquals(0, menus)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        assertEquals(1, menus)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        tap()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(70))
        tap()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        assertEquals(1, menus)
    }
}

package com.huangder.lumibooks.service

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.FloatingSubtitleSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingSubtitleViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val actions = mutableListOf<String>()
    private fun view() = FloatingSubtitleView(context, { false }, { actions += "subtitle" },
        { actions += "play" }, { actions += "previous" }, { actions += "next" },
        { actions += "close" }, { actions += "return" }).apply {
        subtitle.text = "The next chapter begins here."
        appearance(FloatingSubtitleSettings())
    }

    @Test fun barKeepsPlayInPlaceAndCardAddsDetailsAndControls() {
        val view = view()
        view.setInfo("The Long Way", "Chapter 3 / 12")
        view.setProgress(.4f)
        view.showMode(FloatingSubtitleMode.SUBTITLE, false)
        layout(view, 320, 56)
        assertEquals(1, view.subtitle.layout.lineCount)
        val barPlay = visible(view, R.string.tts_play).single()
        assertEquals(40, barPlay.width)
        assertTrue(barPlay.right <= 320 - 4)
        assertTrue(visible(view, R.string.tts_stop).isEmpty())
        capture(view, "subtitle")
        view.subtitle.performClick()
        view.showMode(FloatingSubtitleMode.CONTROLS, false)
        layout(view, 320, 56 + FloatingSubtitleView.DETAILS_HEIGHT_DP.toInt())
        val play = visible(view, R.string.tts_play).single()
        assertEquals(barPlay.left, play.left)
        val controls = listOf(R.string.tts_previous_sentence, R.string.tts_next_sentence,
            R.string.tts_return_to_book, R.string.tts_stop).map { visible(view, it).single() }
        controls.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left) }
        controls.forEach { assertEquals(44, it.width) }
        assertEquals(320 - controls.last().right, controls.first().left)
        (listOf(play) + controls).forEach { it.performClick() }
        assertEquals(listOf("subtitle", "play", "previous", "next", "return", "close"), actions)
        capture(view, "controls")
        view.setPlaying(true)
        assertEquals(1, visible(view, R.string.tts_pause).size)
        view.showMode(FloatingSubtitleMode.SUBTITLE, false)
        assertTrue(visible(view, R.string.tts_stop).isEmpty())
    }

    @Test fun longSubtitleRemainsOneScrollableLineStartingAtTheBeginning() {
        val view = view()
        view.subtitle.text = "A long sentence that continues beyond the capsule width and must remain readable from its first word."
        view.showMode(FloatingSubtitleMode.SUBTITLE, false)
        layout(view, 320, 56)
        assertEquals(1, view.subtitle.layout.lineCount)
        assertEquals(0f, view.subtitle.layout.getLineLeft(0))
        assertTrue(view.subtitle.layout.getLineWidth(0) > view.subtitle.width)
        capture(view, "long-subtitle")
    }

    @Test fun edgeTabsRestoreTheSubtitleOnTap() {
        val view = view()
        val cover = Bitmap.createBitmap(30, 45, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(196, 120, 72))
        }
        view.setCover(cover)
        view.setProgress(.65f)
        val width = FloatingSubtitleView.DOCK_WIDTH_DP.toInt()
        val height = FloatingSubtitleView.DOCK_HEIGHT_DP.toInt()
        for (mode in listOf(FloatingSubtitleMode.RIGHT, FloatingSubtitleMode.LEFT)) {
            view.showMode(mode, false)
            view.appearance(FloatingSubtitleSettings())
            layout(view, width, height)
            assertTrue(visible(view, R.string.tts_play).isEmpty())
            assertTrue(visible(view, R.string.tts_stop).isEmpty())
            visible(view, R.string.tts_restore_subtitle).single().performClick()
            capture(view, mode.name.lowercase() + if (mode == FloatingSubtitleMode.RIGHT) "-paused" else "")
        }
        view.setPlaying(true)
        view.showMode(FloatingSubtitleMode.RIGHT, false)
        layout(view, width, height)
        capture(view, "right")
        assertEquals(listOf("subtitle", "subtitle"), actions)
    }

    @Test fun progressFollowsTheSpokenSentenceWithinTheChapter() {
        val location = com.huangder.lumibooks.tts.TtsPageLocation(chapterIndex = 2, pageIndex = 0)
        val page = com.huangder.lumibooks.tts.TtsPageContent(location, "x".repeat(50), null, null, 100)
        val chapter = com.huangder.lumibooks.tts.TtsChapterSnapshot(2, 10, "x".repeat(400))
        val utterance = com.huangder.lumibooks.tts.TtsTextSegment("x", 100, 101)
        assertEquals(.25f, listeningProgress(com.huangder.lumibooks.tts.TtsListeningFrame(chapter, page, utterance)), 1e-4f)
        assertEquals(100f / 150f, listeningProgress(com.huangder.lumibooks.tts.TtsListeningFrame(null, page, null)), 1e-4f)
        assertEquals(0f, listeningProgress(com.huangder.lumibooks.tts.TtsListeningFrame()), 0f)
    }

    private fun visible(view: View, label: Int): List<View> {
        if (view.visibility != View.VISIBLE) return emptyList()
        val own = if (view.contentDescription == context.getString(label)) listOf(view) else emptyList()
        return own + if (view is ViewGroup) (0 until view.childCount).flatMap { visible(view.getChildAt(it), label) } else emptyList()
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun capture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap((view.width + 40) * 3, (view.height + 40) * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(241, 243, 244))
        canvas.scale(3f, 3f)
        canvas.translate(20f, 20f)
        view.draw(canvas)
        val file = File("build/reports/floating-subtitles/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.getPixel(60, bitmap.height / 2) != android.graphics.Color.TRANSPARENT)
        bitmap.recycle()
    }
}

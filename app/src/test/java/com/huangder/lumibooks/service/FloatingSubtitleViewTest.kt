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

    @Test fun subtitleExpandsToFiveCenteredWorkingControls() {
        val view = view()
        view.showMode(FloatingSubtitleMode.SUBTITLE, false)
        layout(view, 320, 56)
        assertEquals(android.view.Gravity.CENTER, view.subtitle.gravity)
        val textLayout = view.subtitle.layout
        assertTrue(textLayout.getLineLeft(0) > 0)
        assertTrue(textLayout.getLineRight(0) <= view.subtitle.width - view.subtitle.paddingLeft - view.subtitle.paddingRight)
        assertEquals(emptyList<View>(), visible(view, R.string.tts_play))
        capture(view, "subtitle")
        view.subtitle.performClick()
        view.showMode(FloatingSubtitleMode.CONTROLS, false)
        layout(view, 320, 112)
        val controls = listOf(R.string.tts_previous_sentence, R.string.tts_play,
            R.string.tts_next_sentence, R.string.tts_return_to_book, R.string.tts_stop)
            .map { visible(view, it).single() }
        assertEquals(50, controls.first().left)
        assertEquals(270, controls.last().right)
        controls.forEach { assertEquals(44, it.width); it.performClick() }
        assertEquals(listOf("subtitle", "previous", "play", "next", "return", "close"), actions)
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

    @Test fun edgeStripsMirrorCoverAndActionsWithSafePadding() {
        val view = view()
        view.showMode(FloatingSubtitleMode.RIGHT, false)
        view.appearance(FloatingSubtitleSettings())
        layout(view, 148, 56)
        val cover = visible(view, R.string.tts_restore_subtitle).filterIsInstance<ImageView>().single()
        assertEquals(null, cover.drawable)
        val coverColor = (cover.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor
        assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(coverColor) > 0.7)
        val rightPlay = visible(view, R.string.tts_play).single()
        val rightClose = visible(view, R.string.tts_stop).single()
        val rightPlayX = rightPlay.left
        val rightCloseX = rightClose.left
        assertTrue(rightPlayX < rightCloseX)
        assertEquals(136, rightClose.right)
        capture(view, "right")
        view.showMode(FloatingSubtitleMode.LEFT, false)
        view.appearance(FloatingSubtitleSettings())
        layout(view, 148, 56)
        val leftPlay = visible(view, R.string.tts_play).single()
        val leftClose = visible(view, R.string.tts_stop).single()
        assertEquals(148 - rightCloseX - 44, leftClose.left)
        assertEquals(148 - rightPlayX - 44, leftPlay.left)
        assertEquals(12, leftClose.left)
        leftPlay.performClick()
        leftClose.performClick()
        assertEquals(listOf("play", "close"), actions)
        capture(view, "left")
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

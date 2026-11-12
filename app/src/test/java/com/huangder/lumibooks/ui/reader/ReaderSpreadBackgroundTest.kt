package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import com.huangder.lumibooks.ui.reader.engine.FadePageAnim
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.util.epub.EpubPageSide
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderSpreadBackgroundTest {
    private fun photo() = Bitmap.createBitmap(1001, 700, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); val paint = Paint()
        paint.color = Color.RED; canvas.drawRect(0f, 0f, 480f, 700f, paint)
        paint.color = Color.CYAN; canvas.drawRect(480f, 0f, 521f, 700f, paint)
        paint.color = Color.BLUE; canvas.drawRect(521f, 0f, 1001f, 700f, paint)
    }
    private fun installPhoto(owner: Any, photo: Bitmap) {
        val background = owner.javaClass.getDeclaredField("spreadBackground").apply { isAccessible = true }.get(owner)
        background.javaClass.getDeclaredField("image").apply { isAccessible = true }.set(background,
            BitmapDrawable(RuntimeEnvironment.getApplication().resources, photo))
    }
    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1001, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1001, 700)
    }
    private fun assertPhoto(bitmap: Bitmap) {
        assertEquals(Color.RED, bitmap.getPixel(100, 350))
        assertEquals(Color.CYAN, bitmap.getPixel(500, 350))
        assertEquals(Color.BLUE, bitmap.getPixel(900, 350))
    }

    @Test fun nativeSpreadPhotoCrossesGutterAndSurvivesFadeAbort() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        val photo = photo()
        reader.slotManager.backgroundPreparationEnabled = false
        try {
            reader.configure(24f, "day", 1, 0, 0, twoPageSpread = true)
            measure(reader)
            reader.setReaderBackground(Color.WHITE, Color.BLACK, "missing-test-photo")
            installPhoto(reader, photo)
            reader.setPageTransition("fade")
            val output = Bitmap.createBitmap(1001, 700, Bitmap.Config.ARGB_8888)
            try {
                reader.draw(Canvas(output)); assertPhoto(output)
                (reader.animationController as FadePageAnim).startFromTap(PageAnimationController.Direction.NEXT)
                reader.draw(Canvas(output)); assertPhoto(output)
                reader.animationController.abortAnim()
                reader.draw(Canvas(output)); assertPhoto(output)
            } finally { output.recycle() }
        } finally { reader.slotManager.destroy(); photo.recycle() }
    }

    @Test fun epubGlassSnapshotUsesOnePhotoForBothLeavesAndGutter() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val host = EpubSpreadTurnHost(activity.get())
        activity.get().setContentView(host)
        val photo = photo()
        try {
            measure(host)
            val slot = host.javaClass.getDeclaredField("current").apply { isAccessible = true }.get(host)
            val target = EpubSpreadTarget(EpubPageTarget(0, 0), EpubPageTarget(0, 1), firstSide = EpubPageSide.LEFT)
            slot.javaClass.getDeclaredMethod("arrange", EpubSpreadTarget::class.java).apply { isAccessible = true }.invoke(slot, target)
            measure(host)
            host.setReaderBackground(Color.WHITE, "missing-test-photo", 1f, 0f)
            installPhoto(host, photo)
            host.visibleWebViews().forEachIndexed { index, view ->
                view.left = if (index == 0) 0 else 508; view.right = view.left + 492
                view.top = 0; view.bottom = 700
                view.setBackgroundColor(Color.TRANSPARENT)
            }
            host.javaClass.getDeclaredField("state").apply { isAccessible = true }.set(host,
                EpubSpreadState(target, EpubSpreadPage(target.first, 10), EpubSpreadPage(target.second!!, 10), false))
            val backdrop = host.captureGlassBackdrop(LiquidGlassSamplingQuality.ORIGINAL)!!
            val pixels = backdrop.javaClass.getDeclaredField("pixels").apply { isAccessible = true }.get(backdrop) as ImageBitmap
            assertPhoto(pixels.asAndroidBitmap())
            backdrop.close()
            // Both fading text layers share one stationary photo underneath.
            host.javaClass.getDeclaredField("fade").apply { isAccessible = true }.set(host,
                android.animation.ValueAnimator.ofFloat(0f, 1f))
            (slot as View).alpha = .5f
            val frame = Bitmap.createBitmap(1001, 700, Bitmap.Config.ARGB_8888)
            try { host.draw(Canvas(frame)); assertPhoto(frame) } finally { frame.recycle() }
        } finally { host.release(); activity.pause().stop().destroy(); photo.recycle() }
    }
}

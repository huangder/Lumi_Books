package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import com.huangder.lumibooks.util.epub.EpubPageSide
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real WebView compositor geometry is also checked against phone screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubSpreadSnapshotTest {
    @Test fun leavesKeepSquareLandmarksAndTheirOwnBackgroundAtNativeDensity() {
        withSpread(false, false) { host, views ->
            val bitmap = snapshot(host, LiquidGlassSamplingQuality.ORIGINAL)
            try {
                assertEquals(1000, bitmap.width)
                assertEquals(700, bitmap.height)
                assertEquals(Color.RED, bitmap.getPixel(views[0].left + 50, 130))
                assertEquals(Color.RED, bitmap.getPixel(views[0].left + 50, 179))
                assertEquals(Color.YELLOW, bitmap.getPixel(views[0].left + 50, 180))
                assertEquals(Color.BLUE, bitmap.getPixel(views[1].left + 50, 130))
                assertEquals(Color.BLUE, bitmap.getPixel(views[1].left + 50, 179))
                assertEquals(Color.GREEN, bitmap.getPixel(views[1].left + 50, 180))
                assertEquals(Color.WHITE, bitmap.getPixel(500, 130))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun reverseReadingAndSampledBackdropKeepTheSameLeafCoordinates() {
        withSpread(true, false) { host, views ->
            val bitmap = snapshot(host, LiquidGlassSamplingQuality.BALANCED)
            try {
                assertEquals(500, bitmap.width)
                assertEquals(350, bitmap.height)
                assertTrue(views[0].left > views[1].left)
                for ((index, color) in listOf(Color.RED, Color.BLUE).withIndex()) {
                    assertEquals(color, bitmap.getPixel((views[index].left + 50) / 2, 65))
                    assertEquals(color, bitmap.getPixel((views[index].left + 50) / 2, 89))
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun fullWidthPublisherPageCapturesOnlyItsVisibleLeaf() {
        withSpread(false, true) { host, _ ->
            val bitmap = snapshot(host, LiquidGlassSamplingQuality.ORIGINAL)
            try {
                assertEquals(Color.YELLOW, bitmap.getPixel(900, 300))
                assertEquals(Color.RED, bitmap.getPixel(50, 130))
            } finally { bitmap.recycle() }
        }
    }

    private fun snapshot(host: EpubSpreadTurnHost, quality: LiquidGlassSamplingQuality): Bitmap {
        val backdrop = host.captureGlassBackdrop(quality)!!
        val image = backdrop.javaClass.getDeclaredField("pixels").apply { isAccessible = true }.get(backdrop) as ImageBitmap
        val bitmap = image.asAndroidBitmap()
        backdrop.close()
        return bitmap
    }

    private fun withSpread(reverse: Boolean, full: Boolean, check: (EpubSpreadTurnHost, List<EpubContentWebView>) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val host = EpubSpreadTurnHost(activity.get())
        activity.get().setContentView(host)
        val slot = host.javaClass.getDeclaredField("current").apply { isAccessible = true }.get(host)
        val target = EpubSpreadTarget(EpubPageTarget(0, 0), if (full) null else EpubPageTarget(0, 1),
            firstSide = if (reverse) EpubPageSide.RIGHT else EpubPageSide.LEFT, fullWidth = full)
        val size = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY)
        host.measure(size, height); host.layout(0, 0, 1000, 700)
        slot.javaClass.getDeclaredMethod("arrange", EpubSpreadTarget::class.java).apply { isAccessible = true }.invoke(slot, target)
        host.measure(size, height); host.layout(0, 0, 1000, 700)
        val views = host.visibleWebViews()
        views.forEachIndexed { index, view ->
            val w = if (full) 1000 else (1000 - (16f * view.resources.displayMetrics.density).toInt()) / 2
            // Robolectric's WebView does not implement setFrame.
            val left = if ((index == 0) == reverse && !full) 1000 - w else 0
            view.left = left; view.right = left + w; view.top = 0; view.bottom = 700
            view.foreground = object : Drawable() {
                private val paint = Paint()
                override fun draw(canvas: Canvas) {
                    canvas.drawColor(if (index == 0) Color.YELLOW else Color.GREEN)
                    paint.color = if (index == 0) Color.RED else Color.BLUE
                    canvas.drawRect(30f, 100f, 110f, 180f, paint)
                }
                override fun setAlpha(alpha: Int) = Unit
                override fun setColorFilter(colorFilter: ColorFilter?) = Unit
                @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
            }
        }
        host.javaClass.getDeclaredField("state").apply { isAccessible = true }.set(host,
            EpubSpreadState(target, EpubSpreadPage(target.first, 10), target.second?.let { EpubSpreadPage(it, 10) }, reverse))
        try { check(host, views) } finally { host.release(); activity.pause().stop().destroy() }
    }
}

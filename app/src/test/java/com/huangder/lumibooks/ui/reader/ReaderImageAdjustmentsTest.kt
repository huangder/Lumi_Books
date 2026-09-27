package com.huangder.lumibooks.ui.reader

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderImageAdjustmentsTest {
    private fun edgeBitmap(): Bitmap = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888).apply {
        for (y in 0..2) for (x in 0..4) {
            val gray = intArrayOf(80, 80, 120, 160, 160)[x]
            setPixel(x, y, Color.rgb(gray, gray, gray))
        }
    }

    @Test fun neutralReturnsOriginalAndSharpenChangesEdgesWithoutMutatingSource() {
        val source = edgeBitmap()
        assertSame(source, sharpenReaderBitmap(source, 0f))
        val result = sharpenReaderBitmap(source, 0.5f)
        assertTrue(Color.red(result.getPixel(1, 1)) < 80)
        assertTrue(Color.red(result.getPixel(3, 1)) > 160)
        assertEquals(120, Color.red(result.getPixel(2, 1)))
        assertEquals(80, Color.red(source.getPixel(1, 1)))
        assertEquals(255, Color.alpha(result.getPixel(3, 1)))
    }

    @Test fun flatImagesKeepTheirColorIncludingBorders() {
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(110, 90, 70)) }
        val result = sharpenReaderBitmap(source, 1f)
        for (y in 0..3) for (x in 0..3) assertEquals(source.getPixel(x, y), result.getPixel(x, y))
    }

    @Test fun drawableBrightnessContrastAndResetChangeActualPixels() {
        val source = edgeBitmap()
        val drawable = AdjustedReaderDrawable(BitmapDrawable(null, source).apply { setBounds(0, 0, 5, 3) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        fun render(settings: ReaderImageAdjustments): Int {
            drawable.update(settings, scope) {}
            val output = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(output))
            return Color.red(output.getPixel(1, 1))
        }
        assertEquals(80, render(ReaderImageAdjustments()))
        assertEquals(255, render(ReaderImageAdjustments(brightness = 1f)))
        assertTrue(render(ReaderImageAdjustments(contrast = 2f)) < 80)
        assertEquals(80, render(ReaderImageAdjustments()))
        scope.cancel()
    }

    @Test fun preferencesSurviveNewReaderAndStayScopedToBook() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreManager(context)
        val book = "image-test-${System.nanoTime()}"
        val settings = ReaderImageAdjustments(1f, 1.65f, 0.7f)
        store.persistReaderImageAdjustments(book, settings)
        withTimeout(5000) { store.readerPreferences(book).first { it.imageAdjustments == settings } }
        val reopened = DataStoreManager(context)
        assertEquals(settings, reopened.readerPreferences(book).first().imageAdjustments)
        assertEquals(ReaderImageAdjustments(), reopened.readerPreferences("$book-other").first().imageAdjustments)
        store.saveReaderImageAdjustments(book, ReaderImageAdjustments())
        assertEquals(ReaderImageAdjustments(), reopened.readerPreferences(book).first().imageAdjustments)
    }

    @Test fun lazyImageRecoveryDoesNotSharpenAndCacheTheFailurePlaceholder() {
        val pixels = edgeBitmap()
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val source = object : android.graphics.drawable.Drawable(),
            com.huangder.lumibooks.util.parser.ReaderImagePixelSource {
            override fun acquireReaderBitmap(): Bitmap? = pixels.takeIf { ready.get() }
            override fun draw(canvas: Canvas) { canvas.drawColor(Color.MAGENTA) }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(filter: ColorFilter?) = Unit
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
        }.apply { setBounds(0, 0, 5, 3) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val drawable = AdjustedReaderDrawable(source)
        val frame = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888)
        try {
            drawable.update(ReaderImageAdjustments(sharpen = 1f), scope) {}
            drawable.draw(Canvas(frame))
            ready.set(true)
            val deadline = System.nanoTime() + 5_000_000_000L
            do {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofMillis(300))
                Thread.sleep(10)
                drawable.draw(Canvas(frame))
            } while (Color.red(frame.getPixel(1, 1)) >= 80 && System.nanoTime() < deadline)
            assertTrue("real edge pixels replace the placeholder", Color.red(frame.getPixel(1, 1)) < 80)
            assertEquals("source pixels remain intact", 80, Color.red(pixels.getPixel(1, 1)))
        } finally { scope.cancel(); frame.recycle() }
    }

    @Test fun corruptLegacyMissingAndOutOfRangeSettingsAreSafe() {
        assertEquals(ReaderImageAdjustments(), ReaderImageAdjustments.decode(null))
        assertEquals(ReaderImageAdjustments(), ReaderImageAdjustments.decode("broken"))
        assertEquals(ReaderImageAdjustments(), ReaderImageAdjustments.decode("{}"))
        assertEquals(ReaderImageAdjustments(1f, 0.5f, 1f), ReaderImageAdjustments(4f, -1f, 8f).normalized())
        assertEquals(0f, ReaderImageAdjustments(sharpen = 1f).forDisplay(true).sharpen)
    }

    @Test fun webSettingsApplySameConvolutionAndExcludeText() {
        val script = epubImageAdjustmentScript(ReaderImageAdjustments(0.2f, 1.4f, 0.5f))
        assertTrue(script.contains("feConvolveMatrix"))
        assertTrue(script.contains("0 -0.5 0 -0.5 3.0 -0.5 0 -0.5 0"))
        assertTrue(script.contains("img, svg image"))
        assertTrue(epubImageAdjustmentScript(ReaderImageAdjustments()).contains("style.textContent = \"\""))
    }

    @Test fun tiledSharpenMatchesFullImageAcrossTileBoundary() {
        val source = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val gray = 80 + (x + y) * 5 + if (x >= 4) 30 else 0
                setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        val decoder = object : com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder {
            override fun init(context: android.content.Context, uri: android.net.Uri) = Point(source.width, source.height)
            override val isReady = true
            override fun recycle() {}
            override fun decodeRegion(sRect: Rect, sampleSize: Int) = Bitmap.createBitmap(source, sRect.left, sRect.top, sRect.width(), sRect.height())
        }
        val tiled = SharpenedRegionDecoder(decoder, 0.65f)
        tiled.init(ApplicationProvider.getApplicationContext(), android.net.Uri.EMPTY)
        val full = sharpenReaderBitmap(source, 0.65f)
        for (bounds in listOf(Rect(0, 0, 4, 6), Rect(4, 0, 8, 6))) {
            val tile = tiled.decodeRegion(bounds, 1)
            assertEquals(bounds.width(), tile.width)
            for (y in 0 until tile.height) for (x in 0 until tile.width) {
                assertEquals(full.getPixel(bounds.left + x, y), tile.getPixel(x, y))
            }
        }
    }
}

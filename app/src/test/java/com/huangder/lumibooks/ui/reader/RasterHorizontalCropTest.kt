package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.local.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterHorizontalCropTest {
    private fun page(left: Int, right: Int, border: Int = Color.WHITE): Bitmap =
        Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(border)
            for (y in 0 until height) for (x in left until width - right) {
                setPixel(x, y, if ((x + y) % 9 == 0) Color.BLACK else Color.rgb(52, 57, 58))
            }
        }

    private fun assertMargins(bitmap: Bitmap, left: Int, right: Int) {
        val crop = detectRasterHorizontalCrop(bitmap)
        assertEquals(left / bitmap.width.toFloat(), crop.left, 0.015f)
        assertEquals(1f - right / bitmap.width.toFloat(), crop.right, 0.015f)
    }

    @Test fun detectsWhiteGrayAndYellowMarginsIndependently() {
        assertMargins(page(32, 40), 32, 40)
        assertMargins(page(12, 50, Color.rgb(205, 205, 202)), 12, 50)
        assertMargins(page(40, 20, Color.rgb(242, 228, 190)), 40, 20)
    }

    @Test fun preservesMarksInBorderAndUncertainPages() {
        val marked = page(35, 35).apply {
            for (y in 90..230) for (x in 5..8) setPixel(x, y, Color.BLACK)
        }
        assertMargins(marked, 0, 35)
        val topRule = page(35, 35).apply {
            for (x in 4..22) setPixel(x, 4, Color.BLACK)
        }
        assertMargins(topRule, 0, 35)
        assertEquals(RasterHorizontalCrop.FULL, detectRasterHorizontalCrop(page(0, 0)))
        assertEquals(RasterHorizontalCrop.FULL, detectRasterHorizontalCrop(
            Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        ))
        assertEquals(RasterHorizontalCrop.FULL, detectRasterHorizontalCrop(page(110, 0)))
    }

    @Test fun sourceRectAndInkCoordinatesRoundTrip() {
        val crop = RasterHorizontalCrop(0.1f, 0.875f)
        assertEquals(android.graphics.Rect(32, 0, 280, 400), crop.sourceRect(320, 400))
        for (fit in listOf(false, true)) {
            val transform = RasterInkTransform(crop, 0.8f, 1080f, 1600f, fit)
            val original = PdfInkPoint(0.45f, 0.52f)
            val restored = transform.toOriginal(transform.toDisplay(original))
            assertEquals(original.x, restored.x, 0.0001f)
            assertEquals(original.y, restored.y, 0.0001f)
        }
    }

    @Test fun manualPixelsUseEachPagesOriginalWidthAndClampSides() {
        val crop = RasterManualCrop(40, 80)
        assertEquals(RasterHorizontalCrop(0.1f, 0.8f), crop.forWidth(400))
        assertEquals(RasterHorizontalCrop(0.05f, 0.9f), crop.forWidth(800))
        val capped = RasterManualCrop(900, 900).forWidth(400)
        assertEquals(0.45f, capped.left, 0.0001f)
        assertEquals(0.55f, capped.right, 0.0001f)
        assertEquals(RasterHorizontalCrop.FULL, crop.forWidth(0))
    }

    @Test fun manualCropEncodingRejectsBadValues() {
        assertEquals(RasterManualCrop(12, 34), RasterManualCrop.decode("12,34"))
        assertEquals(RasterManualCrop(), RasterManualCrop.decode("bad"))
        assertEquals(RasterManualCrop(0, 100000), RasterManualCrop.decode("-5,999999"))
    }

    @Test fun manualSettingIsScopedToBook() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreManager(context)
        val book = "manual-crop-test-${System.nanoTime()}"
        store.saveRasterManualCrop(book, true, RasterManualCrop(30, 70))
        val saved = DataStoreManager(context).readerPreferences(book).first()
        assertTrue(saved.rasterManualCropEnabled)
        assertEquals(RasterManualCrop(30, 70), saved.rasterManualCrop)
        assertTrue(!store.readerPreferences("$book-other").first().rasterManualCropEnabled)
    }

    @Test fun settingDefaultsOffAndIsScopedToBook() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreManager(context)
        val book = "crop-test-${System.nanoTime()}"
        assertTrue(!store.readerPreferences(book).first().rasterHorizontalCropEnabled)
        store.saveRasterHorizontalCropEnabled(book, true)
        assertTrue(DataStoreManager(context).readerPreferences(book).first().rasterHorizontalCropEnabled)
        assertTrue(!store.readerPreferences("$book-other").first().rasterHorizontalCropEnabled)
        store.saveRasterHorizontalCropEnabled(book, false)
        assertTrue(!store.readerPreferences(book).first().rasterHorizontalCropEnabled)
    }
}

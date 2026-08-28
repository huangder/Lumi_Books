package com.huangder.lumibooks.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderBackgroundImageProcessorInstrumentedTest {
    @Test
    fun blurIsWrittenIntoBitmapPixels() {
        val bitmap = Bitmap.createBitmap(21, 21, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                bitmap.setPixel(x, y, if (x < bitmap.width / 2) Color.BLACK else Color.WHITE)
            }
        }

        ReaderBackgroundImageProcessor.blur(bitmap, radius = 3)

        val boundaryPixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertEquals(255, Color.alpha(boundaryPixel))
        assertTrue(Color.red(boundaryPixel) in 1..254)
        assertTrue(Color.green(boundaryPixel) in 1..254)
        assertTrue(Color.blue(boundaryPixel) in 1..254)
    }
}

package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterTilePromotionTest {
    @Test fun visiblePageDiscardsLatePreviewTileAndRequestsFullResolution() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val view = SubsamplingScaleImageView(ApplicationProvider.getApplicationContext())
        fun field(name: String, value: Any) = SubsamplingScaleImageView::class.java.getDeclaredField(name).apply {
            isAccessible = true; set(view, value)
        }
        try {
            view.backgroundDispatcher = dispatcher
            view.layout(0, 0, 400, 400)
            view.downSampling = 4
            field("sWidth", 400); field("sHeight", 400); field("scale", 1f)
            field("minimumTileDpi", -1); field("vTranslate", PointF())
            field("fullImageSampleSize", 1)
            val requests = mutableListOf<Int>()
            val decoded = mutableListOf<Bitmap>()
            val decoder = object : ImageRegionDecoder {
                override fun init(context: Context, uri: Uri) = Point(400, 400)
                override val isReady = true
                override fun recycle() = Unit
                override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
                    requests += sampleSize
                    // The already-running preview decode finishes after foreground promotion.
                    if (sampleSize == 4) view.downSampling = 1
                    return Bitmap.createBitmap(sRect.width() / sampleSize, sRect.height() / sampleSize,
                        Bitmap.Config.ARGB_8888).also(decoded::add)
                }
            }
            field("decoder", decoder)
            val tileClass = Class.forName("com.davemorrissey.labs.subscaleview.internal.Tile")
            val tile = tileClass.getDeclaredConstructor().newInstance()
            tileClass.getField("sampleSize").setInt(tile, 1)
            tileClass.getField("isVisible").setBoolean(tile, true)
            tileClass.getField("sRect").set(tile, Rect(0, 0, 400, 400))
            @Suppress("UNCHECKED_CAST")
            val tiles = Class.forName("com.davemorrissey.labs.subscaleview.internal.TileMap")
                .getDeclaredConstructor().newInstance() as MutableMap<Int, List<Any>>
            tiles[1] = listOf(tile)
            field("tileMap", tiles)
            SubsamplingScaleImageView::class.java.getDeclaredMethod("loadTile", ImageRegionDecoder::class.java, tileClass)
                .apply { isAccessible = true }.invoke(view, decoder, tile)
            advanceUntilIdle()
            assertEquals(listOf(4, 1), requests)
            assertTrue("stale preview pixels released", decoded.first().isRecycled)
            val displayed = tileClass.getMethod("getBitmap").invoke(tile) as Bitmap
            assertEquals(400, displayed.width)
            assertEquals(400, displayed.height)
        } finally { view.recycle(); Dispatchers.resetMain() }
    }
}

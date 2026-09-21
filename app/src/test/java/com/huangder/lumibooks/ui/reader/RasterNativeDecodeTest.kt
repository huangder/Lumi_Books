package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import com.huangder.lumibooks.domain.model.PageRenderMode
import com.huangder.lumibooks.util.parser.CbzPageIndex
import com.huangder.lumibooks.util.parser.OpenedCbzArchive
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterNativeDecodeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `ZIP images decode with sampled dimensions and equivalent quality identity`() {
        val file = temporary.newFile("sample.cbz")
        val source = Bitmap.createBitmap(2001, 3001, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("001.png"))
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.closeEntry()
        }
        source.recycle()
        CbzPageDecoder(OpenedCbzArchive(null, ZipFile(file), CbzPageIndex.build(listOf("001.png")), null)).use { decoder ->
            val dimensions = decoder.dimensions(0)!!
            assertEquals(RasterDimensions(2001, 3001), dimensions)
            val tile = decoder.tileSource(0, dimensions)!!
            assertEquals("file+zip", tile.uri.scheme)
            assertEquals(file.absolutePath, tile.uri.schemeSpecificPart)
            assertEquals("001.png", tile.uri.fragment)
            val preview = decoder.spec(0, dimensions, 240, PageRenderMode.NORMAL, true)
            val normal = decoder.spec(0, dimensions, 720, PageRenderMode.NORMAL, false)
            val high = decoder.spec(0, dimensions, 720, PageRenderMode.HIGH, false)
            assertEquals(high, decoder.spec(0, dimensions, 1440, PageRenderMode.NATIVE, false))
            assertTrue(preview.width < normal.width)
            assertTrue(normal.width < high.width)
            val image = decoder.decode(normal)!!
            assertEquals(normal.width, image.width)
            assertEquals(normal.height, image.height)
            assertEquals(Color.BLUE, image.getPixel(20, 20))
            image.recycle()
        }
    }
}

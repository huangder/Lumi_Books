package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.epub.epub2Entries
import com.huangder.lumibooks.util.parser.EpubParser
import com.huangder.lumibooks.util.parser.ReaderImageTransparencyInfo
import com.huangder.lumibooks.util.parser.pngHasAlphaChannel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverThemeColorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fallback = Color.rgb(245, 239, 220)

    @Test fun pngAlphaChannelSkipsColorEvenWhenEveryPixelIsOpaqueAndExtensionIsJpeg() {
        for (alpha in listOf(255, 128, 0)) {
            val file = temporary.newFile("cover-$alpha.jpg")
            val bytes = pngBytes(hasAlpha = true, alpha = alpha)
            assertTrue(pngHasAlphaChannel(bytes.inputStream()))
            file.writeBytes(bytes)
            assertNull(extractCoverEdgeColor(file.absolutePath))
        }
    }

    @Test fun opaqueRgbPngUsesTopAndBottomEdgesInsteadOfDarkCenter() {
        val file = temporary.newFile("opaque.png")
        val bytes = pngBytes(hasAlpha = false)
        assertFalse(pngHasAlphaChannel(bytes.inputStream()))
        file.writeBytes(bytes)
        assertEquals(Color.RED, extractCoverEdgeColor(file.absolutePath))
    }

    @Test fun opaqueJpegStillExtractsColor() {
        val bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setHasAlpha(false)
        }
        val file = temporary.newFile("cover.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        assertEquals(Color.WHITE, extractCoverEdgeColor(file.absolutePath))
        bitmap.recycle()
    }

    @Test fun bitmapAlphaMetadataIsPreservedThroughAdjustmentWrappers() {
        val bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val original = BitmapDrawable(null, bitmap).apply { setBounds(0, 0, 20, 40) }
        val adjusted = AdjustedReaderDrawable(AdjustedReaderDrawable(original))
        assertEquals(fallback, readerCoverEdgeColor(adjusted, fallback))
        bitmap.setHasAlpha(false)
        assertEquals(Color.RED, readerCoverEdgeColor(adjusted, fallback))
        bitmap.recycle()
    }

    @Test fun lazyEpubDrawableRetainsPngChannelInformationBeforeRasterization() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        for (hasAlpha in listOf(true, false)) {
            val archive = temporary.newFile("cover-$hasAlpha.epub")
            val entries = epub2Entries().toMutableMap().apply {
                this["OEBPS/chapter.xhtml"] = "<html><body><p>Before</p><img src=\"cover.png\"/><p>After</p></body></html>"
            }
            ZipOutputStream(archive.outputStream()).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("OEBPS/cover.png"))
                zip.write(pngBytes(hasAlpha))
                zip.closeEntry()
            }
            val parser = EpubParser(context)
            try {
                parser.parse(archive.absolutePath)
                val chapter = parser.getChapterContent(0) as Spanned
                val source = chapter.getSpans(0, chapter.length, ImageSpan::class.java).single().drawable
                assertEquals(hasAlpha, (source as ReaderImageTransparencyInfo).hasAlphaChannel)
                val bounds = Rect(source.bounds)
                assertEquals(if (hasAlpha) fallback else Color.RED,
                    readerCoverEdgeColor(AdjustedReaderDrawable(source), fallback))
                assertEquals(bounds, source.bounds)
            } finally {
                parser.close()
            }
        }
    }

    @Test fun samplingRestoresLazyDrawableAndWrapperBounds() {
        val source = object : Drawable() {
            val paint = Paint().apply { color = Color.RED }
            override fun draw(canvas: Canvas) = canvas.drawRect(bounds, paint)
            override fun getIntrinsicWidth() = 1000
            override fun getIntrinsicHeight() = 1600
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
        }.apply { setBounds(0, 0, 200, 320) }
        val adjusted = AdjustedReaderDrawable(source)
        repeat(3) {
            assertEquals(Color.RED, readerCoverEdgeColor(adjusted, fallback))
            assertEquals(Rect(0, 0, 200, 320), source.bounds)
            assertEquals(Rect(0, 0, 200, 320), adjusted.bounds)
        }
    }

    /** Explicit RGB/RGBA fixture: encoders may otherwise optimize opaque RGBA into RGB. */
    private fun pngBytes(hasAlpha: Boolean, alpha: Int = 255): ByteArray {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.writeLong(-8552249625308161526L)
        fun chunk(type: String, bytes: ByteArray) {
            val name = type.toByteArray(Charsets.US_ASCII)
            data.writeInt(bytes.size)
            data.write(name)
            data.write(bytes)
            data.writeInt(CRC32().apply { update(name); update(bytes) }.value.toInt())
        }
        val header = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).apply {
                writeInt(20); writeInt(40); writeByte(8); writeByte(if (hasAlpha) 6 else 2)
                writeByte(0); writeByte(0); writeByte(0)
            }
        }
        chunk("IHDR", header.toByteArray())
        val pixels = ByteArrayOutputStream()
        DeflaterOutputStream(pixels).use { compressed ->
            repeat(40) { y ->
                compressed.write(0)
                repeat(20) {
                    compressed.write(if (y < 4 || y >= 36) 255 else 0)
                    compressed.write(0); compressed.write(0)
                    if (hasAlpha) compressed.write(alpha)
                }
            }
        }
        chunk("IDAT", pixels.toByteArray())
        chunk("IEND", byteArrayOf())
        return output.toByteArray()
    }
}

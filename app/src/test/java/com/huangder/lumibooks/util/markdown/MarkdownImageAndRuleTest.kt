package com.huangder.lumibooks.util.markdown

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.markdown.native.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownImageAndRuleTest {
    private val style = MarkdownRenderStyle(320, 480, 20f, Color.BLACK, false)

    @Test fun thematicBreakDrawsAcrossTheReadingColumnWithoutChangingOffsets() = runBlocking {
        val document = MarkdownNativeParser.parse("Before\n\n---\n\nAfter")
        val source = MarkwonBlockSource(ApplicationProvider.getApplicationContext(), document, emptyMap())
        try {
            val block = document.blocks.single { it.kind == "rule" }
            val text = source.renderBlock(block.index, style)
            assertEquals(block.text, text.toString())
            val span = text.getSpans(0, text.length, ReplacementSpan::class.java).single()
            val paint = Paint().apply { textSize = 20f }
            assertEquals(320, span.getSize(paint, text, 0, text.length, null))
            val bitmap = Bitmap.createBitmap(320, 40, Bitmap.Config.ARGB_8888)
            span.draw(Canvas(bitmap), text, 0, text.length, 0f, 0, 25, 40, paint)
            assertTrue((0 until bitmap.width).count { bitmap.getPixel(it, 20) != Color.TRANSPARENT } > 300)
        } finally { source.close() }
    }

    // Coil resumes image requests on Android's main dispatcher. Keep its looper
    // running while the Robolectric test thread waits for the result.
    @Test
    @LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
    fun httpRasterAndSvgImagesRenderAndFailuresKeepAPlaceholder() = runBlocking {
        MockWebServer().use { server ->
            val png = ByteArrayOutputStream().also { output ->
                val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.RED)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                } finally {
                    bitmap.recycle()
                }
            }.toByteArray()
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png)))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/svg+xml").setBody(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"20\"><rect width=\"40\" height=\"20\" fill=\"red\"/></svg>"
            ))
            server.enqueue(MockResponse().setResponseCode(404))
            server.start()
            val document = MarkdownNativeParser.parse("![raster](${server.url("/picture.png")})\n\n" +
                "![svg](${server.url("/picture.svg")})\n\n![missing](${server.url("/missing.png")})")
            MarkwonBlockSource(ApplicationProvider.getApplicationContext(), document, emptyMap()).use { source ->
                withTimeout(20_000) {
                    for ((index, block) in document.blocks.withIndex()) {
                        val text = source.renderBlock(index, style)
                        assertEquals(block.text, text.toString())
                        val spans = text.getSpans(0, text.length, ImageSpan::class.java)
                        if (index < 2) {
                            val drawable = spans.single().drawable
                            assertTrue(drawable.bounds.width() in 1..style.width)
                            assertTrue(drawable.bounds.height() > 0)
                        } else {
                            assertTrue(spans.isEmpty())
                            assertEquals(1, text.getSpans(0, text.length, ReplacementSpan::class.java).size)
                        }
                    }
                    assertEquals(3, server.requestCount)
                }
            }
        }
    }
}

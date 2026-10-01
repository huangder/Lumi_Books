package com.huangder.lumibooks.ui.excerpt

import android.app.Application
import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExcerptRendererTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val request = ExcerptRequest("弱鸟如何先飞", "作者名", "闽东九县调查随感",
        "可以通过外引内联，建立稳定的物资协作网络；可以鼓励各县制定一些让利政策。",
        "每一个平凡的日子，都值得认真阅读、思考与记录。", backgroundColor = 0xFF4A3728.toInt(),
        textColor = 0xFFF5E6D3.toInt(), createdAt = 1790640000000)

    @Test fun longBodyAndNotesGrowWithoutChangingWidthOrClippingFooter() {
        for (style in ExcerptStyle.entries) {
            val short = ExcerptRenderer.prepare(context, request, style)
            val long = ExcerptRenderer.prepare(context, request.copy(text = request.text.repeat(45)), style)
            val longNote = ExcerptRenderer.prepare(context, request.copy(note = request.note.repeat(45)), style)
            assertEquals(short.width, long.width)
            assertTrue("Body must increase height for $style", long.height > short.height + 1000)
            assertTrue("Notes must increase height for $style", longNote.height > short.height + 1000)
            val footer = long.render(long.height - 160, 160)
            assertTrue((714 until 820).any { x -> (25 until 100).any { y -> footer.getPixel(x, y) != long.backgroundColor } })
            footer.recycle()
        }
    }

    @Test fun streamedPngMatchesRenderedPixelsIncludingTileSeams() {
        val document = ExcerptRenderer.prepare(context, request.copy(text = request.text.repeat(6)), ExcerptStyle.READER)
        val encoded = ByteArrayOutputStream().also(document::writePng).toByteArray()
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
        val rendered = document.render()
        assertEquals(document.height, decoded.height)
        val expected = IntArray(rendered.width * rendered.height)
        val actual = IntArray(expected.size)
        rendered.getPixels(expected, 0, rendered.width, 0, 0, rendered.width, rendered.height)
        decoded.getPixels(actual, 0, decoded.width, 0, 0, decoded.width, decoded.height)
        // Native Skia may round antialiased SVG edges by one channel value after tile translation.
        val firstDifference = expected.indices.firstOrNull { pixel ->
            listOf(0, 8, 16, 24).any { shift ->
                kotlin.math.abs(((expected[pixel] ushr shift) and 255) - ((actual[pixel] ushr shift) and 255)) > 1
            }
        }
        assertNull("Different pixel at $firstDifference: " + firstDifference?.let {
            "${it % rendered.width},${it / rendered.width} ${expected[it].toUInt().toString(16)} / ${actual[it].toUInt().toString(16)}"
        }, firstDifference)
        rendered.recycle(); decoded.recycle()
    }

    @Test fun exportReferencePreviewsAndMissingCoverFallback() {
        val output = File("build/excerpt-previews").apply { mkdirs() }
        val cover = File(context.cacheDir, "sample-cover.png")
        context.assets.open("excerpt_reading.png").use { input -> cover.outputStream().use(input::copyTo) }
        for (style in ExcerptStyle.entries) {
            val document = ExcerptRenderer.prepare(context, request.copy(coverPath = cover.path), style)
            File(output, "${style.name.lowercase()}.png").outputStream().use(document::writePng)
        }
        val fallback = ExcerptRenderer.prepare(context, request.copy(coverPath = "/missing.jpg"), ExcerptStyle.COVER)
        assertEquals(920, fallback.render(0, 1).also { it.recycle() }.width)
        File(output, "classic-english.png").outputStream().use {
            ExcerptRenderer.prepare(context, request.copy(bookTitle = "The Art of Reading 阅读的艺术"), ExcerptStyle.CLASSIC).writePng(it)
        }
    }

    @Test fun verticalTitleKeepsEnglishWordsTogether() {
        val runs = ExcerptRenderer.verticalTitleRuns("阅读 The Art of Reading 2026 艺术")
        assertEquals(listOf("阅", "读", "The Art of Reading 2026", "艺", "术"), runs.map { it.text })
        assertTrue(runs[2].sideways)
        assertFalse(runs[0].sideways)
    }
}

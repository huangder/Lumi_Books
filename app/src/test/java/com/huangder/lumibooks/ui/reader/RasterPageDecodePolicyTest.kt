package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.PageRenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterPageDecodePolicyTest {
    private val phoneWidthPx = 1080

    @Test
    fun `normal mode never decodes below the display width`() {
        // 旧策略会把 3000px 宽的页解成 750px，再放大 1.44 倍显示——越大的图越糊。
        val sampleSize = comicSampleSize(3000, 4500, phoneWidthPx, PageRenderMode.NORMAL)

        assertEquals(2, sampleSize)
        assertEquals(1500, 3000 / sampleSize)
    }

    @Test
    fun `normal mode keeps decoded width between one and two times the display width`() {
        val widths = listOf(1080, 1400, 1600, 2000, 2200, 3000, 4000, 6000, 8000)

        widths.forEach { width ->
            val height = width * 3 / 2
            val sampleSize = comicSampleSize(width, height, phoneWidthPx, PageRenderMode.NORMAL)
            val decodedWidth = width / sampleSize

            assertTrue(
                "width=$width decoded=$decodedWidth sample=$sampleSize",
                decodedWidth >= phoneWidthPx && decodedWidth < phoneWidthPx * 2
            )
        }
    }

    @Test
    fun `normal mode keeps moderate pages at native resolution`() {
        assertEquals(1, comicSampleSize(2000, 3000, phoneWidthPx, PageRenderMode.NORMAL))
        assertEquals(1, comicSampleSize(1080, 1620, phoneWidthPx, PageRenderMode.NORMAL))
    }

    @Test
    fun `normal mode bounds very long pages by the pixel budget`() {
        // 1200x20000 的条漫：旧的"单边 ≤ 4096"会一路降到 150px 宽。
        val sampleSize = comicSampleSize(1200, 20000, phoneWidthPx, PageRenderMode.NORMAL)

        assertEquals(2, sampleSize)
        val decodedWidth = 1200L / sampleSize
        val decodedHeight = 20000L / sampleSize
        assertTrue(decodedWidth * decodedHeight <= NORMAL_RENDER_MAX_PIXELS)
    }

    @Test
    fun `normal mode keeps the minimum decode width on narrow targets`() {
        val sampleSize = comicSampleSize(800, 1200, 100, PageRenderMode.NORMAL)

        assertEquals(2, sampleSize)
        assertTrue(800 / sampleSize >= MIN_DECODE_WIDTH_PX)
    }

    @Test
    fun `high mode decodes natively until the pixel budget is exceeded`() {
        // 3000x4500 = 13.5MP，仍在预算内。
        assertEquals(1, comicSampleSize(3000, 4500, phoneWidthPx, PageRenderMode.HIGH))
        // 4000x8000 = 32MP，超预算后只降一档。
        assertEquals(2, comicSampleSize(4000, 8000, phoneWidthPx, PageRenderMode.HIGH))
    }

    @Test
    fun `high mode keeps long pages sharper than normal mode`() {
        // 1200x10000 = 12MP：正常档为了控制峰值内存会降到 600px 宽，高清档仍在预算内、按原图解码。
        assertEquals(1, comicSampleSize(1200, 10000, phoneWidthPx, PageRenderMode.HIGH))
        assertEquals(2, comicSampleSize(1200, 10000, phoneWidthPx, PageRenderMode.NORMAL))
    }

    @Test
    fun `native mode always decodes the original bitmap`() {
        assertEquals(1, comicSampleSize(10000, 15000, phoneWidthPx, PageRenderMode.NATIVE))
        assertEquals(1, comicSampleSize(1200, 20000, phoneWidthPx, PageRenderMode.NATIVE))
        assertEquals(1, comicSampleSize(3000, 4500, phoneWidthPx, PageRenderMode.NATIVE))
    }

    @Test
    fun `invalid image bounds fall back to no sampling`() {
        assertEquals(1, comicSampleSize(0, 0, phoneWidthPx, PageRenderMode.NORMAL))
        assertEquals(1, comicSampleSize(-10, 200, phoneWidthPx, PageRenderMode.HIGH))
    }

    @Test
    fun `pdf budgets and scales grow with the mode`() {
        assertTrue(
            pdfMaxRenderScale(PageRenderMode.NORMAL) <
                pdfMaxRenderScale(PageRenderMode.HIGH)
        )
        assertTrue(
            pdfMaxRenderScale(PageRenderMode.HIGH) <
                pdfMaxRenderScale(PageRenderMode.NATIVE)
        )

        assertEquals(4_000_000L, pdfRenderPixelBudget(PageRenderMode.NORMAL))
        assertEquals(16_000_000L, pdfRenderPixelBudget(PageRenderMode.HIGH))
        assertNull(pdfRenderPixelBudget(PageRenderMode.NATIVE))
    }

    @Test
    fun `pdf quality modes render progressively wider bitmaps`() {
        val pageWidth = 612
        val pageHeight = 792

        val normal = pdfRenderScale(pageWidth, pageHeight, phoneWidthPx, PageRenderMode.NORMAL)
        val high = pdfRenderScale(pageWidth, pageHeight, phoneWidthPx, PageRenderMode.HIGH)
        val native = pdfRenderScale(pageWidth, pageHeight, phoneWidthPx, PageRenderMode.NATIVE)

        assertTrue(normal * pageWidth > phoneWidthPx)
        assertTrue(high > normal)
        assertTrue(native > high)
    }

    @Test
    fun `pdf normal mode respects its pixel budget`() {
        val scale = pdfRenderScale(612, 792, phoneWidthPx * 5, PageRenderMode.NORMAL)
        val pixels = 612.0 * 792.0 * scale * scale

        assertTrue(pixels <= pdfRenderPixelBudget(PageRenderMode.NORMAL)!!)
    }

    @Test
    fun `zoom render bucket rounds up by half steps`() {
        assertEquals(1f, pdfZoomRenderBucket(1.01f), 0f)
        assertEquals(1.5f, pdfZoomRenderBucket(1.1f), 0f)
        assertEquals(2.5f, pdfZoomRenderBucket(2.1f), 0f)
        assertEquals(5f, pdfZoomRenderBucket(6f), 0f)
    }

    @Test
    fun `page cache keys separate modes of the same page`() {
        val normal = pageRenderCacheKey(7, PageRenderMode.NORMAL, phoneWidthPx)
        val high = pageRenderCacheKey(7, PageRenderMode.HIGH, phoneWidthPx)
        val native = pageRenderCacheKey(7, PageRenderMode.NATIVE, phoneWidthPx)

        assertEquals(normal, pageRenderCacheKey(7, PageRenderMode.NORMAL, phoneWidthPx))
        assertNotEquals(normal, high)
        assertNotEquals(high, native)
        assertNotEquals(pageRenderCacheKey(8, PageRenderMode.NORMAL, phoneWidthPx), normal)
        assertNotEquals(pageRenderCacheKey(7, PageRenderMode.NORMAL, phoneWidthPx * 2), normal)
    }

    @Test
    fun `cache width rounds up to its bucket boundary`() {
        assertEquals(1_152, pageRenderTargetWidth(1_081))
        assertEquals(1_152, pageRenderTargetWidth(1_152))
        assertEquals(
            pageRenderCacheKey(3, PageRenderMode.NORMAL, 1_081),
            pageRenderCacheKey(3, PageRenderMode.NORMAL, 1_152)
        )
    }
}

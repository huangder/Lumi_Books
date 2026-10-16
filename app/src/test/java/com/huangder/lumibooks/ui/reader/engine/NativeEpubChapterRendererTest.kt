package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.*
import com.huangder.lumibooks.util.epub.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeEpubChapterRendererTest {
    private fun document(body: String, css: String = "") = NativeEpubCompiler.compile(NativeEpubSource("c.xhtml",
        EpubResource("c.xhtml", "application/xhtml+xml", "<html><body>$body</body></html>".toByteArray()),
        listOf("style.css" to css), EpubRenditionLayout.REFLOWABLE)).document!!
    private val config = NativeEpubRenderConfig(320, 480, 20f, Typeface.SERIF, null, Color.WHITE, true, 16f, 16f, 16f, 16f)

    @Test fun horizontalPagesHaveContiguousTextAndSharedHitGeometry() {
        val doc = document((1..25).joinToString("") { "<p>第${it}段。${"平静阅读体验。".repeat(8)}</p>" }, "p{margin:0 0 8px}")
        val layout = NativeEpubChapterRenderer.layout(doc, config) { null }
        try {
            assertTrue(layout.pages.size > 2)
            assertTrue(layout.pages.zipWithNext().all { (a, b) -> a.end <= b.start })
            layout.pages.forEachIndexed { index, page ->
                assertEquals(index, layout.pageFor(page.start))
                val item = page.items.filterIsInstance<NativeEpubHorizontalItem>().first()
                val y = item.y + 4; val x = item.x + item.layout.getLineLeft(item.firstLine) + 4
                val hit = item.hit(x, y)!!
                assertTrue(hit.start in page.start until page.end)
                assertEquals(hit.start, doc.resolve(doc.locator(hit.start)))
                val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
                layout.draw(Canvas(bitmap), index)
                assertTrue(bitmapPixels(bitmap).any { it != Color.WHITE }); bitmap.recycle()
            }
        } finally { layout.close() }
    }
    @Test fun rubyReservesMetricsAndVerticalAnnotationUsesSourceRange() {
        val base = NativeEpubChapterRenderer.layout(document("<p>漢字</p>", "p{margin:0}"), config) { null }
        val ruby = NativeEpubChapterRenderer.layout(document("<p><ruby>漢字<rt>かんじ</rt></ruby></p>", "p{margin:0}"), config) { null }
        try {
            val normal = (base.pages.first().items.first() as NativeEpubHorizontalItem).layout
            val annotated = (ruby.pages.first().items.first() as NativeEpubHorizontalItem).layout
            assertTrue(annotated.height > normal.height)
        } finally { base.close(); ruby.close() }
        val doc = document("<p>${"文😀<ruby>漢字<rt>かんじ</rt></ruby>。".repeat(35)}</p>", "body{writing-mode:vertical-rl}p{margin:0}")
        val vertical = NativeEpubChapterRenderer.layout(doc, config) { null }
        try {
            assertTrue(vertical.pages.size > 1)
            assertTrue(vertical.pages.all { page -> page.items.all { it is NativeEpubGlyphItem } })
            vertical.pages.forEach { page -> assertTrue(page.start < page.end) }
        } finally { vertical.close() }
    }
    @Test fun oversizedLineFailsInsteadOfClippingAndForcedBreakSeparatesBlocks() {
        val doc = document("<p>甲</p><p>乙</p>", "p{margin:0}p+p{break-before:page}")
        val layout = NativeEpubChapterRenderer.layout(doc, config) { null }
        assertEquals(2, layout.pages.size); layout.close()
        assertThrows(IllegalArgumentException::class.java) {
            NativeEpubChapterRenderer.layout(document("<p>甲</p>", "p{font-size:160px}"), config.copy(height = 80)) { null }
        }
    }
    @Test fun readerTextSpacingOverridesParagraphCssWhenCustomized() {
        val doc = document("<p>${"甲乙丙丁戊己".repeat(20)}</p><p>${"庚辛壬癸子丑".repeat(20)}</p>",
            "p{margin:0;text-indent:0;line-height:1.0}")
        val tallConfig = config.copy(height = 1200)
        val publisherLayout = NativeEpubChapterRenderer.layout(doc, tallConfig) { null }
        val readerLayout = NativeEpubChapterRenderer.layout(doc, tallConfig.copy(
            lineHeightOverride = 2f, paragraphSpacingPx = 24f, firstLineIndentChars = 1.5f
        )) { null }
        try {
            val publisherItems = publisherLayout.pages.single().items.filterIsInstance<NativeEpubHorizontalItem>()
            val readerItems = readerLayout.pages.single().items.filterIsInstance<NativeEpubHorizontalItem>()
            assertEquals(2, publisherItems.size)
            assertEquals(2, readerItems.size)
            val publisherLineHeight = publisherItems.first().layout.getLineBottom(0) - publisherItems.first().layout.getLineTop(0)
            val readerLineHeight = readerItems.first().layout.getLineBottom(0) - readerItems.first().layout.getLineTop(0)
            assertTrue(readerLineHeight > publisherLineHeight)
            val first = readerItems.first()
            val second = readerItems.last()
            val firstBottom = first.y + first.layout.getLineBottom(first.lastLine - 1) - first.layout.getLineTop(first.firstLine)
            assertEquals(24f, second.y - firstBottom, 0.01f)
            val indent = (readerItems.first().layout.text as android.text.Spanned)
                .getSpans(0, readerItems.first().layout.text.length, android.text.style.LeadingMarginSpan::class.java)
                .single()
            assertEquals(30, indent.getLeadingMargin(true))
        } finally {
            publisherLayout.close()
            readerLayout.close()
        }
    }
    @Test fun publisherNightShadeDimsThePageWithoutChangingLayout() {
        val doc = document("<p>Black text on white paper</p>", "body{background:#fff;color:#000}p{margin:0}")
        val layout = NativeEpubChapterRenderer.layout(doc, config.copy(publisherNightShade = true)) { null }
        try {
            val text = layout.pages.single().items.filterIsInstance<NativeEpubHorizontalItem>().single().layout.text
            val colors = (text as android.text.Spanned).getSpans(
                0, text.length, android.text.style.ForegroundColorSpan::class.java
            )
            assertTrue(colors.any { it.foregroundColor == android.graphics.Color.rgb(0xE8, 0xE8, 0xEA) })
            val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
            layout.draw(Canvas(bitmap), 0)
            val paper = bitmap.getPixel(0, 0)
            assertTrue(android.graphics.Color.red(paper) in 130..150)
            assertEquals(1, layout.pages.size)
            bitmap.recycle()
        } finally {
            layout.close()
        }
    }
    private fun bitmapPixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
}

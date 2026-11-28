package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.*
import com.huangder.lumibooks.util.epub.*
import org.junit.Assert.*
import com.huangder.lumibooks.ui.reader.nativeEpubPaginationSignature
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeEpubChapterRendererTest {

    @Test fun nativePageIndexKeyUsesLayoutGeometryAndStableFontIdentity() {
        val config = NativeEpubRenderConfig(300, 500, 18f, android.graphics.Typeface.DEFAULT, null,
            android.graphics.Color.WHITE, false, 10f, 10f, 10f, 10f)
        fun signature(settings: NativeEpubRenderConfig = config, font: String = "system-font") =
            nativeEpubPaginationSignature("content", font, settings)
        org.junit.Assert.assertEquals(signature(), signature(config.copy(color = android.graphics.Color.RED,
            paper = android.graphics.Color.BLACK, typeface = android.graphics.Typeface.SERIF)))
        org.junit.Assert.assertNotEquals(signature(), signature(config.copy(width = 301)))
        org.junit.Assert.assertNotEquals(signature(), signature(config.copy(paragraphSpacingPx = 12f)))
        org.junit.Assert.assertNotEquals(signature(), signature(font = "updated-font"))
    }
    private fun document(body: String, css: String = "") = NativeEpubCompiler.compile(NativeEpubSource("c.xhtml",
        EpubResource("c.xhtml", "application/xhtml+xml", "<html><body>$body</body></html>".toByteArray()),
        listOf("style.css" to css), EpubRenditionLayout.REFLOWABLE)).document!!
    private val config = NativeEpubRenderConfig(320, 480, 20f, Typeface.SERIF, null, Color.WHITE, true, 16f, 16f, 16f, 16f)

    @Test fun originalFontChangesGlyphMetricsAndReaderFontCanOverrideIt() {
        val doc = document("<p>iiiiiiiiii WWWWWWWWWW</p>", "p{font-family:monospace,serif;margin:0}")
        fun textWidth(settings: NativeEpubRenderConfig): Float = NativeEpubChapterRenderer.layout(doc, settings) { null }
            .use { layout -> (layout.pages.single().items.single() as NativeEpubHorizontalItem).layout.getLineWidth(0) }
        val original = textWidth(config)
        val overridden = textWidth(config.copy(overridePublisherFont = true))
        assertTrue(kotlin.math.abs(original - overridden) > 3f)
    }

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

    @Test fun rightFloatNarrowsIntersectingLinesAndRestoresWidthOnNextPage() {
        val doc = document("<div><img src='float.png' width='100%'/></div><p>${"甲乙丙丁戊己".repeat(55)}</p>",
            "div{float:right;width:40%;margin:0 0 0 8px}p{margin:0;text-indent:0}")
        val png = artwork()
        NativeEpubChapterRenderer.layout(doc, config) { png }.use { layout ->
            val first = layout.pages.first()
            val float = first.items.filterIsInstance<NativeEpubImageItem>().single()
            val text = first.items.filterIsInstance<NativeEpubHorizontalItem>().single()
            assertTrue(text.x + text.layout.getLineRight(text.firstLine) <= float.bounds.left)
            assertTrue(layout.pages.size > 1)
            val second = layout.pages[1].items.filterIsInstance<NativeEpubHorizontalItem>().first()
            assertEquals(text.end, second.start)
            assertTrue(second.x + second.layout.getLineRight(second.firstLine) > float.bounds.left)
        }
    }

    @Test fun inlineAvatarHitMatchesPaintAndWideAnswerRetainsTheLink() {
        val doc = document("""
            <p>甲<img src='avatar.png' height='2em'/>乙</p>
            <a epub:type='noteref' class='duokan-footnote' href='#answer'><img src='question.png' width='100%'/></a>
            <aside epub:type='footnote'><div id='answer'><img src='answer.png'/></div></aside>
        """.trimIndent(), "p{margin:0}p img{vertical-align:middle;margin-left:3px}")
        val png = artwork()
        NativeEpubChapterRenderer.layout(doc, config) { png }.use { layout ->
            val page = layout.pages.first()
            val text = page.items.filterIsInstance<NativeEpubHorizontalItem>().single()
            val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
            try {
                layout.draw(Canvas(bitmap), 0)
                val painted = (16 until 95).flatMap { y -> (16 until 110).map { x -> x to y } }
                    .filter { (x, y) -> bitmap.getPixel(x, y) == Color.MAGENTA }
                assertTrue(painted.isNotEmpty())
                val (x, y) = painted[painted.size / 3]
                assertEquals("avatar.png", text.hit(x.toFloat() + .5f, y.toFloat() + .5f)?.imagePath)
            } finally { bitmap.recycle() }
            val question = page.items.filterIsInstance<NativeEpubImageItem>().single()
            assertEquals(288f, question.bounds.width(), .01f)
            val hit = question.hit(question.bounds.centerX(), question.bounds.centerY())!!
            assertEquals(listOf("answer.png"), doc.footnotes[doc.imageLinks[hit.start]]?.imagePaths)
        }
    }

    private fun artwork(): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        return try {
            java.io.ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun bitmapPixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
}

package com.huangder.lumibooks.util.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import android.text.style.URLSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine
import com.huangder.lumibooks.ui.reader.engine.PageRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class EpubParserLargeChapterTest {
    @Test
    fun blockquoteKeepsIndentWithoutAndroidBlueStripe() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parser = EpubParser(context)
        val parse = EpubParser::class.java.getDeclaredMethod(
            "parseNativeHtmlInChunks",
            String::class.java,
            Html.ImageGetter::class.java
        ).apply { isAccessible = true }

        val parsed = parse.invoke(
            parser,
            "<p>正文</p><blockquote>引用文字</blockquote>",
            Html.ImageGetter { null }
        ) as Spanned

        assertEquals(0, parsed.getSpans(0, parsed.length, QuoteSpan::class.java).size)
        val margins = parsed.getSpans(0, parsed.length, LeadingMarginSpan::class.java)
        assertTrue(margins.isNotEmpty())
        assertTrue(margins.any { it.getLeadingMargin(true) > 0 })
        assertTrue(parsed.toString().contains("引用文字"))
    }

    @Test
    fun largeHtmlChunkSizesAvoidRepeatedParserOverhead() {
        val paragraph = "<p>正文内容用于验证超大章节能够按段落分块解析并正常分页。</p>"
        val html = buildString { repeat(12_000) { append(paragraph) } }
        val timings = listOf(64 * 1024, 256 * 1024, Int.MAX_VALUE).associateWith { target ->
            var converted = ""
            val elapsed = measureTimeMillis {
                val combined = SpannableStringBuilder()
                splitAtParagraphBoundaries(html, target).forEach { chunk ->
                    combined.append(Html.fromHtml(chunk, Html.FROM_HTML_MODE_LEGACY))
                }
                converted = combined.toString()
            }
            android.util.Log.i("EpubChunkBench", "target=$target elapsed=${elapsed}ms length=${converted.length}")
            assertTrue(converted.contains("正文内容"))
            elapsed
        }
        assertTrue(timings.values.all { it > 0L })
    }

    @Test
    fun profilesLargeHtmlPreparationStages() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parser = EpubParser(context)
        val payload = "正文内容用于验证超大章节能够按段落分块解析并正常分页。".repeat(10)
        val html = buildString {
            repeat(2_826) { index ->
                append("<p id=\"p$index\">$payload</p>")
                if (index < 71) append("<p><img src=\"image-$index.png\"/></p>")
            }
        }

        val collect = EpubParser::class.java.getDeclaredMethod(
            "collectFootnoteHrefs",
            Int::class.javaPrimitiveType,
            String::class.java
        ).apply { isAccessible = true }
        val insert = EpubParser::class.java.getDeclaredMethod(
            "insertAnchorMarkers",
            String::class.java
        ).apply { isAccessible = true }
        val parse = EpubParser::class.java.getDeclaredMethod(
            "parseNativeHtmlInChunks",
            String::class.java,
            Html.ImageGetter::class.java
        ).apply { isAccessible = true }

        val collectMs = measureTimeMillis { collect.invoke(parser, 0, html) }
        var marked = ""
        val insertMs = measureTimeMillis { marked = insert.invoke(parser, html) as String }
        var withImages = ""
        val imageBreakMs = measureTimeMillis {
            withImages = marked.replace(Regex("""(<img[^>]*/?>)""", RegexOption.IGNORE_CASE), "\n$1\n")
        }
        var parsed: Spanned? = null
        val parseMs = measureTimeMillis {
            parsed = parse.invoke(parser, withImages, Html.ImageGetter { null }) as Spanned
        }
        android.util.Log.i(
            "EpubStageBench",
            "collect=${collectMs}ms insert=${insertMs}ms imageBreak=${imageBreakMs}ms " +
                "parse=${parseMs}ms html=${html.length} marked=${marked.length} text=${parsed?.length}"
        )
        assertTrue(parsed?.length ?: 0 > 650_000)
    }

    @Test
    fun largeChapterPaginatesAndZipImagesDecodeOnlyWhenDrawn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "epub-large-chapter-test.epub")
        writeLargeChapterEpub(epub)
        val parser = EpubParser(context).apply {
            contentWidth = 360
            paragraphSpacingDp = 4f
            firstLineIndentChars = 2f
        }

        try {
            val book = parser.parse(epub.absolutePath)
            val chapter = parser.getChapterContent(0)

            assertEquals("Large chapter fixture", book.title)
            assertTrue(chapter.length > 650_000)
            assertTrue(chapter.toString().contains("FIRST_PARAGRAPH"))
            assertTrue(chapter.toString().contains("LAST_PARAGRAPH"))

            val spanned = chapter as Spanned
            val link = spanned.getSpans(0, spanned.length, URLSpan::class.java).single()
            assertEquals("#p2825", link.url)
            val target = parser.resolveLink(0, link.url)
            assertEquals(0, target?.chapterIndex)
            assertTrue((target?.characterOffset ?: 0) > 0)
            val images = spanned.getSpans(0, spanned.length, ImageSpan::class.java)
            assertEquals(71, images.size)
            assertEquals(0, parser.decodedImageCacheSizeForTest())

            val engine = PageLayoutEngine().apply {
                configure(
                    width = 420,
                    height = 640,
                    fontSizePx = 30f,
                    marginLeftPx = 30f,
                    marginRightPx = 30f,
                    marginTopPx = 30f,
                    marginBottomPx = 30f,
                    chapterCount = 1
                )
            }
            val layout = engine.layout(0, chapter)
            assertTrue(layout.pages.isNotEmpty())
            assertTrue(layout.pages.first().endCharOffset > 0)
            assertEquals(0, parser.decodedImageCacheSizeForTest())

            val drawable = images.last().drawable
            assertTrue(drawable.bounds.width() in 1..360)
            assertTrue(drawable.bounds.height() > 0)

            val imageOffset = spanned.getSpanStart(images.last())
            val imagePage = layout.pages.first { imageOffset in it.startCharOffset until it.endCharOffset }
            val renderer = PageRenderer().apply {
                configure(
                    width = 420,
                    height = 640,
                    backgroundColor = Color.WHITE,
                    textColor = Color.BLACK,
                    marginLeftPx = 30f,
                    marginTopPx = 30f,
                    visibleHeightPx = 580f
                )
            }
            val rendered = renderer.renderPage(layout, imagePage.pageIndex)

            assertTrue(parser.decodedImageCacheSizeForTest() > 0)
            assertTrue(rendered.containsColor(Color.rgb(28, 136, 72)))
            renderer.releaseBitmap(rendered)
            renderer.destroy()

            parser.clearHtmlCache()
            assertEquals(0, parser.decodedImageCacheSizeForTest())
        } finally {
            parser.close()
            epub.delete()
        }
    }

    private fun writeLargeChapterEpub(target: File) {
        val paragraphPayload = "正文内容用于验证超大章节能够按段落分块解析并正常分页。".repeat(10)
        val body = buildString {
            repeat(2_826) { index ->
                val marker = when (index) {
                    0 -> "FIRST_PARAGRAPH <a href=\"#p2825\">JUMP</a> "
                    2_825 -> "LAST_PARAGRAPH "
                    else -> ""
                }
                append("<p id=\"p$index\">$marker$paragraphPayload</p>")
                if (index < 71) append("<p><img src=\"image-$index.png\"/></p>")
            }
        }
        val textEntries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
            """.trimIndent(),
            "OPS/package.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Large chapter fixture</dc:title>
                  </metadata>
                  <manifest>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="chapter"/></spine>
                </package>
            """.trimIndent(),
            "OPS/chapter.xhtml" to "<html><head><title>Large chapter</title></head><body>$body</body></html>"
        )
        val image = pngBytes()

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            textEntries.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            repeat(71) { index ->
                zip.putNextEntry(ZipEntry("OPS/image-$index.png"))
                zip.write(image)
                zip.closeEntry()
            }
        }
    }

    private fun splitAtParagraphBoundaries(html: String, target: Int): List<String> {
        if (target == Int.MAX_VALUE) return listOf(html)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < html.length) {
            val desired = (start + target).coerceAtMost(html.length)
            if (desired == html.length) {
                chunks += html.substring(start)
                break
            }
            val end = html.indexOf("</p>", desired).takeIf { it >= 0 }?.plus(4) ?: html.length
            chunks += html.substring(start, end)
            start = end
        }
        return chunks
    }

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(28, 136, 72))
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun Bitmap.containsColor(expected: Int): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getPixel(x, y) == expected) return true
            }
        }
        return false
    }
}

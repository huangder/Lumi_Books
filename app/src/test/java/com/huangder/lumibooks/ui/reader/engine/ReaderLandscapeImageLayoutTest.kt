package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ImageSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.parser.EpubParser
import com.huangder.lumibooks.util.epub.EpubRenderSession
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderLandscapeImageLayoutTest {
    @get:Rule val temporary = TemporaryFolder()

    /** Optional local reproduction; the user's EPUB is never included in the repository. */
    @Test @Config(sdk = [35]) fun localBookImagesStayInsideTheLandscapePages() = runBlocking {
        val path = System.getenv("LUMI_TEST_EPUB")
        assumeTrue("Set LUMI_TEST_EPUB for local reproduction", path != null && File(path).isFile)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val parser = EpubParser(context)
        try {
            val book = parser.parse(path!!)
            val chapter = book.chapters.first { it.title.contains("人物") }
            val text = parser.getChapterContent(chapter.index, 868, 1000) as Spanned
            val engine = PageLayoutEngine().apply {
                configure(1000, 1200, 32f, lineSpacingPx = 0f, lineSpacingMult = 1.5f,
                    marginLeftPx = 66f, marginRightPx = 66f, marginTopPx = 118f, marginBottomPx = 82f)
            }
            val layout = engine.layout(chapter.index, text)
            val report = File("build/reports/epub-landscape-images").apply { mkdirs() }
            val spread = Bitmap.createBitmap(2000, 1200, Bitmap.Config.ARGB_8888)
            spread.eraseColor(Color.WHITE)
            var imagesSeen = 0
            layout.pages.forEachIndexed { index, slice ->
                val page = PageContentView(context).apply {
                    configure(32f, Color.BLACK, lineHeightMult = 1.5f,
                        marginLeftPx = 66f, marginRightPx = 66f, marginTopPx = 118f, marginBottomPx = 82f)
                    setPageContent(text, slice.startCharOffset, slice.endCharOffset)
                    measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
                    layout(0, 0, 1000, 1200)
                }
                val frame = Bitmap.createBitmap(1000, 1200, Bitmap.Config.ARGB_8888)
                frame.eraseColor(Color.WHITE)
                page.draw(Canvas(frame))
                File(report, "native-page-$index.png").outputStream().use {
                    frame.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                if (index < 2) Canvas(spread).drawBitmap(frame, index * 1000f, 0f, null)
                val visible = page.textView.text as Spanned
                visible.getSpans(0, visible.length, ImageSpan::class.java).forEach { image ->
                    val line = page.textView.layout.getLineForOffset(visible.getSpanStart(image))
                    val top = 118 + readerImageTop(page.textView.layout, line, image)
                    val hit = page.getImageAt(80f, top + 5f)
                    assertNotNull("image $index has a matching hit box", hit)
                    assertTrue("image $index fits bottom inset: $hit", hit!!.bottomPx <= 1118f)
                    imagesSeen++
                }
                page.clear()
                frame.recycle()
            }
            File(report, "native-spread.png").outputStream().use {
                spread.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            spread.recycle()
            assertEquals(2, imagesSeen)
            EpubRenderSession.open(path).use { session ->
                val navigation = session.epubPackage.navigation.first { it.title.contains("人物") }
                val index = (0 until session.chapterCount).first { session.chapterHref(it) == navigation.href }
                val response = requireNotNull(session.assetLoader.shouldInterceptRequest(Uri.parse(session.chapterUrl(index))))
                val html = response.data.bufferedReader().use { it.readText() }
                    .replace(Regex("<base[^>]+>"), "<base href=\"./\" />")
                File(report, "original.xhtml").writeText(html)
                session.epubPackage.manifest.values.filter { it.mediaType.startsWith("image/") }.forEach { item ->
                    session.read(item.fullPath)?.let { resource ->
                        File(report, item.fullPath.substringAfterLast('/')).writeBytes(resource.bytes)
                    }
                }
            }
            println("LOCAL EPUB PASS: ${layout.pages.size} landscape pages, $imagesSeen complete image boxes; reports: ${report.absolutePath}")
        } finally { parser.close() }
    }

    @Test fun publisherPixelWidthCannotOverflowALandscapeColumnOrItsHeight() {
        val book = temporary.newFile("landscape-images.epub")
        val bitmap = Bitmap.createBitmap(628, 1000, Bitmap.Config.ARGB_8888)
        val image = ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        bitmap.recycle()
        val entries = mapOf(
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Landscape images</dc:title></metadata><manifest><item id="body" href="body.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="body"/></spine></package>""",
            "body.xhtml" to """<html><body><p>Before</p><p><img src="image.png" width="2000" height="1000"/></p><p>After</p><p><img src="image.png" width="2000" height="1000"/></p></body></html>"""
        )
        ZipOutputStream(book.outputStream()).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("image.png"))
            zip.write(image)
            zip.closeEntry()
        }
        val parser = EpubParser(ApplicationProvider.getApplicationContext())
        try {
            parser.parse(book.absolutePath)
            val paged = parser.getChapterContent(0, 400, 500) as Spanned
            val images = paged.getSpans(0, paged.length, ImageSpan::class.java)
            assertEquals(2, images.size)
            images.forEach {
                assertTrue("single column width", it.drawable.bounds.width() <= 400)
                assertTrue("complete image fits page", it.drawable.bounds.height() <= 500)
                assertEquals(628f / 1000f,
                    it.drawable.bounds.width().toFloat() / it.drawable.bounds.height(), 0.003f)
            }
            val scrolled = parser.getChapterContent(0, 400, 0) as Spanned
            val scrollImage = scrolled.getSpans(0, scrolled.length, ImageSpan::class.java).first()
            assertEquals(400, scrollImage.drawable.bounds.width())
            assertTrue("scrolling retains natural height", scrollImage.drawable.bounds.height() > 500)
        } finally { parser.close() }
    }

    @Test fun lineSpacingStaysBelowTheImage() {
        for (inline in listOf(false, true)) for (multiplier in listOf(1f, 1.5f, 2f)) {
            val prefix = if (inline) "Text " else ""
            val text = SpannableString(prefix + "\uFFFC\nAfter").apply {
                setSpan(ImageSpan(ColorDrawable(Color.BLUE).apply { setBounds(0, 0, 200, 300) }, "image.png"),
                    prefix.length, prefix.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length,
                TextPaint().apply { textSize = 20f }, 400)
                .setIncludePad(false).setLineSpacing(8f, multiplier).build()
            val reference = Bitmap.createBitmap(400, 900, Bitmap.Config.ARGB_8888)
            StaticLayout.Builder.obtain(text, 0, text.length, layout.paint, 400)
                .setIncludePad(false).setLineSpacing(0f, 1f).build().draw(Canvas(reference))
            val frame = Bitmap.createBitmap(400, 900, Bitmap.Config.ARGB_8888)
            ReaderTextPainter().draw(Canvas(frame), layout, text,
                ReaderLineGeometry(layout, text, Layout.JUSTIFICATION_MODE_NONE))
            val expectedTop = if (inline) {
                (0 until reference.height).first { reference.getPixel(100, it) == Color.BLUE }
            } else 0
            val actualTop = (0 until frame.height).first { frame.getPixel(100, it) == Color.BLUE }
            assertEquals("line spacing does not push image down at $multiplier", expectedTop, actualTop)
            assertEquals(Color.BLUE, frame.getPixel(100, actualTop + 299))
            reference.recycle()
            frame.recycle()
        }
    }
}

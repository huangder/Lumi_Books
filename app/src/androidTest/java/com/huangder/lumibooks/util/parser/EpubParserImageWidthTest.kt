package com.huangder.lumibooks.util.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 连续滚动阅读器把量出的正文宽度交给解析器，插图必须按该宽度重排，
 * 否则拖动左右边距时图片尺寸不跟随（用户反馈的“滚动模式下左右边距不生效”）。
 */
@RunWith(AndroidJUnit4::class)
class EpubParserImageWidthTest {
    @Test
    fun imageBoundsFollowParserContentWidth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = File(context.cacheDir, "epub-image-width-test.epub")
        writeEpub(epub)
        val parser = EpubParser(context)
        try {
            parser.parse(epub.absolutePath)

            parser.contentWidth = 600
            parser.clearHtmlCache()
            assertEquals(600, firstImageWidth(parser))

            // 阅读器改变边距后走的就是这条路径：新宽度 + 清缓存 → 重新排版图片。
            parser.contentWidth = 400
            parser.clearHtmlCache()
            assertEquals(400, firstImageWidth(parser))
        } finally {
            parser.close()
            epub.delete()
        }
    }

    private fun firstImageWidth(parser: EpubParser): Int {
        val chapter = parser.getChapterContent(0) as Spanned
        val image = chapter.getSpans(0, chapter.length, ImageSpan::class.java).single()
        return image.drawable.bounds.width()
    }

    private fun writeEpub(target: File) {
        val textEntries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to
                "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                "<rootfiles><rootfile full-path=\"OPS/package.opf\" " +
                "media-type=\"application/oebps-package+xml\"/></rootfiles></container>",
            "OPS/package.opf" to
                "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\">" +
                "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
                "<dc:title>Image width fixture</dc:title></metadata>" +
                "<manifest><item id=\"chapter\" href=\"chapter.xhtml\" " +
                "media-type=\"application/xhtml+xml\"/></manifest>" +
                "<spine><itemref idref=\"chapter\"/></spine></package>",
            "OPS/chapter.xhtml" to
                "<html><head><title>Image width</title></head><body>" +
                "<p>宽图跟随阅读器内容宽度。</p>" +
                "<p><img src=\"image.png\"/></p></body></html>"
        )
        val image = pngBytes()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            textEntries.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("OPS/image.png"))
            zip.write(image)
            zip.closeEntry()
        }
    }

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(28, 136, 72))
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}

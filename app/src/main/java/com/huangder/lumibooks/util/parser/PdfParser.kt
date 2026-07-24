package com.huangder.lumibooks.util.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import java.io.File

class PdfParser(private val context: Context) : BookParser {
    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f
    override var contentWidth: Int = 0
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: android.os.ParcelFileDescriptor? = null
    private var pageCount: Int = 0
    private var fileName: String = ""

    // LRU 缓存：最多 3 页，避免 OOM
    private val htmlCache = object : LinkedHashMap<Int, String>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
            return size > 3
        }
    }

    override fun parse(filePath: String): BookContent {
        close()
        val file = File(filePath)
        fileName = file.nameWithoutExtension
        fileDescriptor = android.os.ParcelFileDescriptor.open(
            file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
        )
        pdfRenderer = PdfRenderer(fileDescriptor!!)
        pageCount = pdfRenderer?.pageCount ?: 0

        // 每页作为独立"章节"，ReaderViewModel 控制无动画切换
        val chapters = (0 until pageCount).map { pageIndex ->
            Chapter(
                index = pageIndex,
                title = "第${pageIndex + 1}页",
                content = "PDF 第${pageIndex + 1}页 / 共${pageCount}页",
                htmlContent = "" // 按需渲染
            )
        }

        return BookContent(
            title = fileName,
            author = "未知作者",
            chapters = chapters
        )
    }

    /**
     * 按需渲染单页（JPEG 压缩，质量 85，缓存最近 3 页）
     */
    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String {
        if (chapterIndex !in 0 until pageCount) return ""
        htmlCache[chapterIndex]?.let { return it }

        val html = try {
            val page = pdfRenderer?.openPage(chapterIndex) ?: return ""

            // 动态缩放：大页面降分辨率避免 OOM
            val maxDim = maxOf(page.width, page.height)
            val scale = when {
                maxDim > 3000 -> 0.7f
                maxDim > 2000 -> 1.0f
                maxDim > 1500 -> 1.5f
                else -> 2f
            }
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            bitmap.recycle()
            val base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)

            """
                |<html><head>
                |<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                |<style>
                |  * { box-sizing: border-box; }
                |  body { margin: 0; padding: 0; background: #f5f5f5; }
                |  img { max-width: 100%; height: auto; display: block; margin: 0 auto; }
                |</style></head>
                |<body><img src="data:image/jpeg;base64,$base64"></body></html>
            """.trimMargin()
        } catch (e: Exception) {
            "<html><body><p>页面加载失败: ${e.message}</p></body></html>"
        }

        htmlCache[chapterIndex] = html
        return html
    }

    override fun getChapterContent(chapterIndex: Int): CharSequence {
        return if (chapterIndex in 0 until pageCount) "PDF 第${chapterIndex + 1}页 / 共${pageCount}页" else ""
    }

    override fun getChapterCount(): Int = pageCount

    fun close() {
        htmlCache.clear()
        runCatching { pdfRenderer?.close() }
        runCatching { fileDescriptor?.close() }
        pdfRenderer = null
        fileDescriptor = null
        pageCount = 0
    }
}

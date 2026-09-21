package com.huangder.lumibooks.util.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.FileUtils
import java.io.File

/**
 * CBZ 漫画解析器 —— 只建立页索引，不解码图片。
 *
 * 每张图片是一"页"，压缩包内的子目录是一"话"。阅读器统一按全局页号定位，
 * 目录（tocEntries）只描述每一话的首页，用于目录面板分组与跳转。
 * 图片解码由 `CbzBitmapPageSource` 负责，避免解析阶段占用内存。
 */
class CbzParser(private val context: Context) : BookParser {
    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f
    override var contentWidth: Int = 0
    override var contentHeight: Int = 0
    override var useEpubCss: Boolean = false
    override var preserveEpubBackground: Boolean = false

    private var archive: OpenedCbzArchive? = null
    private var pageTitles: List<String> = emptyList()

    /** ComicInfo.xml 声明从右往左阅读时为 true，仅作为该书翻页方向的初始默认值。 */
    var prefersRightToLeft: Boolean = false
        private set

    override fun parse(filePath: String): BookContent {
        close()
        val opened = runCatching { CbzArchiveOpener.open(context, filePath) }.getOrElse { error ->
            throw IllegalArgumentException(context.getString(R.string.cbz_open_failed), error)
        }
        archive = opened
        require(opened.index.pages.isNotEmpty()) {
            context.getString(R.string.cbz_archive_empty)
        }

        val fileName = runCatching { FileUtils.getFileNameFromLocation(context, filePath) }
            .getOrDefault(File(filePath).name)
            .substringBeforeLast('.')
        prefersRightToLeft = opened.comicInfo?.prefersRightToLeft == true
        val bookTitle = opened.comicInfo?.resolveTitle(fileName) ?: fileName
        val unknownAuthor = context.getString(R.string.book_author_unknown)
        val bookAuthor = opened.comicInfo?.resolveAuthor(unknownAuthor) ?: unknownAuthor

        val chapterLabels = opened.index.chapters.mapIndexed { index, chapter ->
            CbzPageIndex.chapterDirectoryLabel(chapter.directory)
                ?: context.getString(R.string.cbz_chapter_label, index + 1)
        }
        val singleUnnamedChapter = opened.index.chapters.size == 1 &&
            opened.index.chapters.first().directory == null
        pageTitles = opened.index.pages.map { page ->
            val chapterLabel = chapterLabels.getOrNull(page.chapterIndex)
            if (singleUnnamedChapter || chapterLabel == null) {
                context.getString(R.string.cbz_page_title, page.pageInChapter + 1)
            } else {
                context.getString(
                    R.string.cbz_page_title_in_chapter,
                    chapterLabel,
                    page.pageInChapter + 1
                )
            }
        }

        val chapters = pageTitles.mapIndexed { index, title ->
            Chapter(index = index, title = title, content = title, htmlContent = "")
        }
        val tocEntries = if (singleUnnamedChapter) {
            emptyList()
        } else {
            opened.index.chapters.mapIndexed { index, chapter ->
                TocEntry(
                    title = chapterLabels[index],
                    level = 1,
                    chapterIndex = chapter.firstPageIndex
                )
            }
        }
        return BookContent(
            title = bookTitle,
            author = bookAuthor,
            chapters = chapters,
            tocEntries = tocEntries
        )
    }

    override fun getChapterContent(chapterIndex: Int): CharSequence =
        pageTitles.getOrElse(chapterIndex) { "" }

    /** 位图页阅读器直接解码图片，不使用 HTML。 */
    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String = ""

    override fun getChapterCount(): Int = pageTitles.size

    /**
     * 轻量封面提取：解码第一页并等比缩放到 400px 宽，存 `filesDir/covers/`。
     * 与 [parse] 互相独立，导入时无需建立完整会话。
     */
    override fun extractCoverPath(filePath: String): String? {
        val opened = runCatching { CbzArchiveOpener.open(context, filePath) }.getOrNull() ?: return null
        return try {
            val firstPage = opened.index.pages.firstOrNull() ?: return null
            val bitmap = decodeCoverBitmap(opened, firstPage.entryName) ?: return null
            val coversDir = File(context.filesDir, "covers")
            coversDir.mkdirs()
            val coverFile = File(coversDir, "${filePath.hashCode()}.jpg")
            coverFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            runCatching { opened.close() }
        }
    }

    private fun decodeCoverBitmap(archive: OpenedCbzArchive, entryName: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        archive.openEntry(entryName)?.use { stream ->
            runCatching { BitmapFactory.decodeStream(stream, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= COVER_WIDTH_PX) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = archive.openEntry(entryName)?.use { stream ->
            runCatching { BitmapFactory.decodeStream(stream, null, options) }.getOrNull()
        } ?: return null
        val targetHeight = (decoded.height.toFloat() / decoded.width.toFloat() * COVER_WIDTH_PX)
            .toInt()
            .coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, COVER_WIDTH_PX, targetHeight, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    override fun close() {
        runCatching { archive?.close() }
        archive = null
        pageTitles = emptyList()
        prefersRightToLeft = false
    }

    private companion object {
        const val COVER_WIDTH_PX = 400
    }
}

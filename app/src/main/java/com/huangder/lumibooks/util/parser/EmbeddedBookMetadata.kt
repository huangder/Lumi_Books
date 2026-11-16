package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.epub.EpubPackageReader
import com.huangder.lumibooks.util.epub.MobiFile
import com.huangder.lumibooks.util.kindle.KindleEpubCache
import com.huangder.lumibooks.util.kindle.KindleProbe
import com.huangder.lumibooks.util.markdown.native.MarkdownNativeParser
import com.huangder.lumibooks.util.word.WordEpubCache
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible

/** Reads stored fields without opening a reader session or generating a library cover. */
internal suspend fun readEmbeddedBookMetadata(
    context: Context,
    location: String,
    format: BookFormat
): BookNameMetadata? = try {
    runInterruptible {
        when (format) {
            BookFormat.TXT -> {
                // Verify availability for existing books without reading or indexing the text.
                BookFileAccess.openInputStream(context, location).use { BookNameMetadata() }
            }
            BookFormat.EPUB -> BookFileAccess.openSeekable(context, location).use {
                EpubPackageReader.readMetadata(it.path)
            }
            BookFormat.CBZ -> CbzArchiveOpener.open(context, location).use {
                val info = it.comicInfo
                BookNameMetadata(info?.resolveTitle(""), info?.resolveAuthor(""))
            }
            BookFormat.PDF -> BookFileAccess.openSeekable(context, location).use { source ->
                val memory = MemoryUsageSetting.setupMixed(32L * 1024 * 1024).apply {
                    tempDir = context.cacheDir
                }
                PDDocument.load(File(source.path), memory).use { pdf ->
                    BookNameMetadata(pdf.documentInformation.title, pdf.documentInformation.author)
                }
            }
            BookFormat.MOBI, BookFormat.AZW, BookFormat.AZW3 -> readKindleMetadata(context, location)
            BookFormat.DOC, BookFormat.DOCX -> WordEpubCache.acquire(context, location, format.name.lowercase()).use {
                EpubPackageReader.readMetadata(it.file.absolutePath)
            }
            BookFormat.MARKDOWN -> {
                val source = File(location)
                require(source.isFile && source.length() <= 64L * 1024 * 1024)
                val metadata = MarkdownNativeParser.inspect(source.readText())
                BookNameMetadata(metadata.title, metadata.author)
            }
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
} catch (_: LinkageError) {
    null
}

private fun readKindleMetadata(context: Context, location: String): BookNameMetadata =
    BookFileAccess.openSeekable(context, location).use { source ->
        val probe = KindleProbe.read(File(source.path))
        if (probe.needsReconstruction) {
            KindleEpubCache.acquire(context, source.path).use { EpubPackageReader.readMetadata(it.file.absolutePath) }
        } else {
            MobiFile.open(source.path).use { mobi ->
                val header = mobi.header
                BookNameMetadata(header.titleOverride.ifBlank { header.fullName }, header.author)
            }
        }
    }

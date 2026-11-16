package com.huangder.lumibooks.ui.home

import android.content.Context
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore
import com.huangder.lumibooks.util.parser.BookFilenameMetadata
import com.huangder.lumibooks.util.parser.BookNameMetadata
import com.huangder.lumibooks.util.parser.readEmbeddedBookMetadata
import com.huangder.lumibooks.util.parser.usableBookAuthor
import java.io.File

internal data class BookMetadataSuggestion(
    val book: Book,
    val fileName: String,
    val title: String,
    val author: String
)

internal data class BookMetadataRepairResult(val updated: Int, val skipped: Int, val failed: Int)

internal suspend fun previewBookMetadataRepair(context: Context, book: Book): BookMetadataSuggestion? {
    if (book.isCloudOnly || book.isMissing || book.filePath.isBlank()) return null

    fun String?.sourceName(): String? = this?.takeIf { name ->
        val extension = FileUtils.getFileExtension(name)
        extension in BookFormat.importExtensions && BookFormat.fromExtension(extension) == book.format &&
            BookFilenameMetadata.stem(name).isNotBlank()
    }

    val fileName = book.sourceDisplayName.sourceName()
        ?: book.sourceUri?.let { BookFileAccess.displayName(context, it).sourceName() }
        ?: (if (book.format == BookFormat.MARKDOWN) {
            MarkdownSnapshotStore.manifest(File(book.filePath))?.optString("originalName").sourceName()
        } else {
            BookFileAccess.displayName(context, book.filePath).sourceName()
        })
        ?: return null

    if (!hasDefaultTitle(book, fileName) && book.author.usableBookAuthor() != null) return null
    val embedded = readEmbeddedBookMetadata(context, book.filePath, book.format) ?: return null
    return suggestBookMetadata(book, fileName, embedded)
}

internal fun suggestBookMetadata(book: Book, fileName: String, embedded: BookNameMetadata): BookMetadataSuggestion? {
    val metadata = embedded.withFallback(BookFilenameMetadata.read(fileName))
    val title = metadata.title?.takeIf { hasDefaultTitle(book, fileName) } ?: book.title
    val author = metadata.author?.takeIf { book.author.usableBookAuthor() == null } ?: book.author
    if (title == book.title && author == book.author) return null
    return BookMetadataSuggestion(book, fileName, title, author)
}

private fun hasDefaultTitle(book: Book, fileName: String): Boolean =
    book.title.isBlank() || book.title == fileName.substringBeforeLast('.') ||
        book.title == BookFilenameMetadata.stem(fileName)

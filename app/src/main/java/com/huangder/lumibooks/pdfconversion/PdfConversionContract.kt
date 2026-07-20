package com.huangder.lumibooks.pdfconversion

import android.content.Context
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.util.FileUtils
import java.io.File

object PdfConversionContract {
    const val KEY_SOURCE_BOOK_ID = "source_book_id"
    const val KEY_REPLACE_EXISTING = "replace_existing"
    const val KEY_CURRENT_PAGE = "current_page"
    const val KEY_TOTAL_PAGES = "total_pages"
    const val KEY_PROGRESS = "progress"
    const val KEY_CONVERTED_BOOK_ID = "converted_book_id"
    const val KEY_TEXT_PAGES = "text_pages"
    const val KEY_ERROR_CODE = "error_code"

    const val ERROR_NO_TEXT = "no_text"
    const val ERROR_ENCRYPTED = "encrypted"
    const val ERROR_FILE_MISSING = "file_missing"
    const val ERROR_STORAGE = "storage"
    const val ERROR_UNKNOWN = "unknown"

    private const val CONVERTED_ID_SUFFIX = "__pdf_parsed"
    private const val CONVERTED_DIRECTORY = "converted_pdf"

    fun convertedBookId(sourceBookId: String): String = sourceBookId + CONVERTED_ID_SUFFIX

    fun uniqueWorkName(sourceBookId: String): String = "pdf_text_conversion_$sourceBookId"

    fun progressNotificationId(sourceBookId: String): Int {
        return 12_000 + positiveHash(sourceBookId) % 10_000
    }

    fun resultNotificationId(sourceBookId: String): Int {
        return 22_000 + positiveHash(sourceBookId) % 10_000
    }

    fun outputFile(context: Context, sourceBookId: String): File {
        val directory = File(FileUtils.getBooksDirectory(context), CONVERTED_DIRECTORY).apply { mkdirs() }
        return File(directory, "${safeFilePart(sourceBookId)}.txt")
    }

    fun temporaryFile(context: Context, sourceBookId: String): File {
        return File(outputFile(context, sourceBookId).absolutePath + ".tmp")
    }

    fun backupFile(context: Context, sourceBookId: String): File {
        return File(outputFile(context, sourceBookId).absolutePath + ".bak")
    }

    fun isConvertedBook(context: Context, book: Book): Boolean {
        if (!book.id.endsWith(CONVERTED_ID_SUFFIX)) return false
        val convertedDirectory = File(FileUtils.getBooksDirectory(context), CONVERTED_DIRECTORY)
        return runCatching {
            File(book.filePath).canonicalFile.parentFile == convertedDirectory.canonicalFile
        }.getOrDefault(false)
    }

    private fun safeFilePart(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun positiveHash(value: String): Int = value.hashCode() and Int.MAX_VALUE
}

sealed interface PdfConversionState {
    data object Idle : PdfConversionState

    data class Running(
        val currentPage: Int,
        val totalPages: Int,
        val progress: Int
    ) : PdfConversionState

    data class Succeeded(
        val bookId: String,
        val textPages: Int,
        val totalPages: Int
    ) : PdfConversionState

    data class Failed(val errorCode: String) : PdfConversionState
    data object Cancelled : PdfConversionState
}

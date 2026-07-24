package com.huangder.lumibooks.pdfconversion

import android.content.Context
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.util.FileUtils
import java.io.File

object PdfConversionContract {
    const val KEY_SOURCE_BOOK_ID = "source_book_id"
    const val KEY_REPLACE_EXISTING = "replace_existing"
    const val KEY_ENGINE = "engine"
    const val KEY_MINERU_MODE = "mineru_mode"
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
    const val ERROR_MINERU_NOT_CONFIGURED = "mineru_not_configured"
    const val ERROR_MINERU_FILE_LIMIT = "mineru_file_limit"
    const val ERROR_MINERU_PAGE_LIMIT = "mineru_page_limit"
    const val ERROR_MINERU_AUTH = "mineru_auth"
    const val ERROR_MINERU_RATE_LIMIT = "mineru_rate_limit"
    const val ERROR_MINERU_NETWORK = "mineru_network"
    const val ERROR_MINERU_UPLOAD = "mineru_upload"
    const val ERROR_MINERU_SERVICE = "mineru_service"
    const val ERROR_MINERU_RESULT = "mineru_result"
    const val ERROR_MINERU_MANUAL_FORMAT = "mineru_manual_format"
    const val ERROR_MINERU_MANUAL_TOO_LARGE = "mineru_manual_too_large"
    const val ERROR_MINERU_MANUAL_IMPORT = "mineru_manual_import"

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

    fun mineruOutputFile(context: Context, sourceBookId: String): File {
        val directory = File(FileUtils.getBooksDirectory(context), CONVERTED_DIRECTORY).apply { mkdirs() }
        return File(directory, "${safeFilePart(sourceBookId)}.epub")
    }

    fun mineruTemporaryFile(context: Context, sourceBookId: String): File {
        return File(mineruOutputFile(context, sourceBookId).absolutePath + ".tmp")
    }

    fun mineruBackupFile(context: Context, sourceBookId: String): File {
        return File(mineruOutputFile(context, sourceBookId).absolutePath + ".bak")
    }

    fun isManagedConvertedFile(context: Context, file: File): Boolean {
        val directory = File(FileUtils.getBooksDirectory(context), CONVERTED_DIRECTORY)
        return runCatching { file.canonicalFile.parentFile == directory.canonicalFile }.getOrDefault(false)
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

enum class PdfConversionEngine(val key: String) {
    LOCAL("local"),
    MINERU("mineru")
}

sealed interface PdfConversionState {
    data object Idle : PdfConversionState

    data class Running(
        val currentPage: Int,
        val totalPages: Int,
        val progress: Int,
        val manualImport: Boolean = false
    ) : PdfConversionState

    data class Succeeded(
        val bookId: String,
        val textPages: Int,
        val totalPages: Int,
        val manualImport: Boolean = false
    ) : PdfConversionState

    data class Failed(val errorCode: String) : PdfConversionState
    data object Cancelled : PdfConversionState
}

package com.huangder.lumibooks.mineru

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.pdfconversion.PdfConversionContract
import com.huangder.lumibooks.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MineruManualImportResult(
    val bookId: String,
    val title: String,
    val chapterCount: Int
)

@Singleton
class MineruManualImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val database: AppDatabase,
    private val epubBuilder: MineruEpubBuilder
) {
    suspend fun importStandalone(
        uri: Uri,
        fallbackTitle: String,
        author: String
    ): MineruManualImportResult = withContext(Dispatchers.IO) {
        val bookId = FileUtils.generateBookId()
        val workDirectory = createWorkDirectory(bookId)
        val outputDirectory = File(FileUtils.getBooksDirectory(context), MANUAL_DIRECTORY).apply { mkdirs() }
        val outputFile = File(outputDirectory, "${safePart(bookId)}.epub")
        val temporaryFile = File(outputFile.absolutePath + ".tmp")
        var installed = false

        try {
            val source = copyResult(uri, workDirectory)
            val title = resultTitle(source.displayName, fallbackTitle)
            val buildResult = epubBuilder.build(
                remoteResult = MineruRemoteResult(source.file, source.isZip),
                outputFile = temporaryFile,
                workDirectory = workDirectory,
                title = title,
                author = author
            )
            moveReplacing(temporaryFile, outputFile)
            installed = true

            val now = System.currentTimeMillis()
            bookRepository.insertBook(
                Book(
                    id = bookId,
                    title = title,
                    author = author,
                    filePath = outputFile.absolutePath,
                    coverPath = null,
                    format = BookFormat.EPUB,
                    lastReadTime = now,
                    readingProgress = 0f,
                    createdAt = now
                )
            )
            MineruManualImportResult(bookId, title, buildResult.chapterCount)
        } catch (error: Throwable) {
            if (installed) outputFile.delete()
            throw error
        } finally {
            temporaryFile.delete()
            workDirectory.deleteRecursively()
        }
    }

    suspend fun importForPdf(
        uri: Uri,
        sourceBookId: String,
        replaceExisting: Boolean
    ): MineruManualImportResult = withContext(Dispatchers.IO) {
        val sourceBook = bookRepository.getBookById(sourceBookId)
            ?: throw FileNotFoundException("Source PDF is missing")
        val convertedBookId = PdfConversionContract.convertedBookId(sourceBookId)
        val existingBook = bookRepository.getBookById(convertedBookId)
        if (existingBook != null && !replaceExisting) {
            return@withContext MineruManualImportResult(existingBook.id, existingBook.title, 0)
        }

        val workDirectory = createWorkDirectory(sourceBookId)
        val outputFile = PdfConversionContract.mineruOutputFile(context, sourceBookId)
        val temporaryFile = PdfConversionContract.mineruTemporaryFile(context, sourceBookId)
        val backupFile = PdfConversionContract.mineruBackupFile(context, sourceBookId)
        temporaryFile.delete()
        if (backupFile.exists()) {
            if (outputFile.exists()) backupFile.delete() else moveReplacing(backupFile, outputFile)
        }

        try {
            val source = copyResult(uri, workDirectory)
            val buildResult = epubBuilder.build(
                remoteResult = MineruRemoteResult(source.file, source.isZip),
                outputFile = temporaryFile,
                workDirectory = workDirectory,
                title = sourceBook.title,
                author = sourceBook.author
            )
            installOutputFile(temporaryFile, outputFile, backupFile)

            val convertedTitle = context.getString(
                com.huangder.lumibooks.R.string.pdf_convert_mineru_book_title,
                sourceBook.title
            )
            try {
                val now = System.currentTimeMillis()
                database.withTransaction {
                    if (existingBook != null) {
                        database.bookmarkDao().deleteAllBookmarksByBookId(convertedBookId)
                        database.noteDao().deleteAllNotesByBookId(convertedBookId)
                    }
                    database.bookDao().insertBook(
                        BookEntity(
                            id = convertedBookId,
                            title = convertedTitle,
                            author = sourceBook.author,
                            filePath = outputFile.absolutePath,
                            coverPath = existingBook?.coverPath ?: sourceBook.coverPath,
                            format = BookFormat.EPUB.name,
                            lastReadTime = now,
                            readingProgress = 0f,
                            createdAt = existingBook?.createdAt ?: now,
                            isFavorite = existingBook?.isFavorite ?: sourceBook.isFavorite
                        )
                    )
                }
            } catch (error: Throwable) {
                restorePreviousOutput(outputFile, backupFile)
                throw error
            }

            backupFile.delete()
            deletePreviousConvertedFile(existingBook?.filePath, outputFile)
            MineruManualImportResult(convertedBookId, convertedTitle, buildResult.chapterCount)
        } catch (error: Throwable) {
            temporaryFile.delete()
            if (backupFile.exists()) restorePreviousOutput(outputFile, backupFile)
            throw error
        } finally {
            temporaryFile.delete()
            workDirectory.deleteRecursively()
        }
    }

    private fun copyResult(uri: Uri, workDirectory: File): ManualSource {
        val displayName = FileUtils.getFileNameFromUri(context, uri).orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
        val isZip = extension == "zip" || mimeType in ZIP_MIME_TYPES
        val isMarkdown = extension in MARKDOWN_EXTENSIONS || mimeType in MARKDOWN_MIME_TYPES
        if (!isZip && !isMarkdown) {
            throw MineruApiException(
                MineruApiException.Kind.INVALID_RESULT,
                "Select a MinerU ZIP or Markdown result"
            )
        }

        val declaredSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            ?: -1L
        val maxBytes = if (isZip) MAX_ZIP_INPUT_BYTES else MAX_MARKDOWN_INPUT_BYTES
        if (declaredSize > maxBytes) {
            throw MineruApiException(MineruApiException.Kind.FILE_LIMIT, "MinerU result is too large")
        }

        val destination = File(workDirectory, if (isZip) "manual-result.zip" else "manual-result.md")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open MinerU result")
        input.buffered().use { source ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                var totalBytes = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    if (totalBytes > maxBytes) {
                        throw MineruApiException(
                            MineruApiException.Kind.FILE_LIMIT,
                            "MinerU result is too large"
                        )
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        if (!destination.isFile || destination.length() == 0L) {
            throw MineruApiException(MineruApiException.Kind.INVALID_RESULT, "MinerU result is empty")
        }
        return ManualSource(destination, isZip, displayName)
    }

    private fun createWorkDirectory(key: String): File {
        return File(context.cacheDir, "mineru/manual-${safePart(key)}-${UUID.randomUUID()}").apply {
            mkdirs()
        }
    }

    private fun resultTitle(displayName: String, fallbackTitle: String): String {
        val baseName = displayName.substringBeforeLast('.', displayName).trim()
            .removeSuffix("_full")
            .removeSuffix("-full")
            .trim()
        return baseName.take(MAX_TITLE_LENGTH).ifBlank { fallbackTitle }
    }

    private fun installOutputFile(temporaryFile: File, outputFile: File, backupFile: File) {
        if (outputFile.exists()) moveReplacing(outputFile, backupFile)
        try {
            moveReplacing(temporaryFile, outputFile)
        } catch (error: Throwable) {
            if (backupFile.exists()) moveReplacing(backupFile, outputFile)
            throw error
        }
    }

    private fun restorePreviousOutput(outputFile: File, backupFile: File) {
        outputFile.delete()
        if (backupFile.exists()) moveReplacing(backupFile, outputFile)
    }

    private fun deletePreviousConvertedFile(previousPath: String?, installedFile: File) {
        if (previousPath.isNullOrBlank()) return
        val previous = File(previousPath)
        if (previous.absolutePath != installedFile.absolutePath &&
            PdfConversionContract.isManagedConvertedFile(context, previous)
        ) {
            previous.delete()
        }
    }

    private fun moveReplacing(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            throw error
        }
    }

    private fun safePart(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private data class ManualSource(
        val file: File,
        val isZip: Boolean,
        val displayName: String
    )

    private companion object {
        const val MANUAL_DIRECTORY = "mineru_manual"
        const val MAX_TITLE_LENGTH = 200
        const val MAX_ZIP_INPUT_BYTES = 512L * 1_024L * 1_024L
        const val MAX_MARKDOWN_INPUT_BYTES = 64L * 1_024L * 1_024L
        val ZIP_MIME_TYPES = setOf("application/zip", "application/x-zip-compressed")
        val MARKDOWN_EXTENSIONS = setOf("md", "markdown")
        val MARKDOWN_MIME_TYPES = setOf("text/markdown", "text/x-markdown", "text/plain")
    }
}

package com.huangder.lumibooks.data.sync

import android.content.Context
import android.util.Log
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.BookFileAccess
import java.net.URLEncoder
import com.huangder.lumibooks.data.local.WebdavTokenStore
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavSyncManager @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val webdavClient: WebdavClient,
    private val tokenStore: WebdavTokenStore,
    private val dataStoreManager: DataStoreManager,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressSyncJob: Job? = null
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // ── Full sync ───────────────────────────────────────────────────

    /**
     * Full bidirectional sync: compares local vs remote manifest,
     * uploads newer local files, downloads newer remote files.
     * Returns a result description string.
     */
    suspend fun fullSync(): SyncResult {
        if (!syncMutex.tryLock()) return SyncResult("正在同步中", false)
        _isSyncing.value = true
        return try {
            withContext(Dispatchers.IO) { runFullSync() }
        } finally {
            _isSyncing.value = false
            syncMutex.unlock()
        }
    }

    private suspend fun runFullSync(): SyncResult {
        val config = dataStoreManager.webdavConfig.first()
        if (!config.enabled) return SyncResult("WebDAV \u672a\u542f\u7528", false)

        val password = tokenStore.read()
        if (password.isNullOrBlank()) return SyncResult("\u672a\u914d\u7f6e\u5bc6\u7801", false)

        val normalized = config.normalized()
        val serverUrl = normalized.serverUrl
        val username = normalized.username
        val syncPath = normalized.syncPath

        return try {
            webdavClient.ensureDirectory(serverUrl, username, password, syncPath)
            webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/books")
            webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/data")

            // Never delete the manifest or blindly re-upload every book. Re-uploading unchanged
            // books is slow and repeatedly consumes the WebDAV provider's upload allowance.
            val remoteManifest = downloadManifestOrNull(serverUrl, username, password, syncPath)

            val books = bookRepository.getAllBooks().first()
            val confirmedBooks = remoteManifest?.books?.toMutableMap() ?: mutableMapOf()
            val confirmedBookIds = mutableSetOf<String>()
            var booksUploaded = 0
            var booksAlreadyPresent = 0
            var booksFailed = 0
            val failedBookTitles = mutableListOf<String>()
            var quotaError: WebdavException? = null

            for (book in books) {
                val remoteFileName = remoteBookFileName(book)
                val localSize = BookFileAccess.size(context, book.filePath)
                val manifestEntry = remoteManifest?.books?.get(book.id)
                val manifestConfirmsFile = manifestEntry != null &&
                    manifestEntry.fileName == remoteFileName &&
                    (localSize <= 0L || manifestEntry.sizeBytes <= 0L || manifestEntry.sizeBytes == localSize)

                if (manifestConfirmsFile) {
                    confirmedBooks[book.id] = manifestEntry
                    confirmedBookIds.add(book.id)
                    booksAlreadyPresent++
                    continue
                }

                val availableBytes = quotaError?.availableBytes
                if (availableBytes != null && localSize > availableBytes) {
                    booksFailed++
                    failedBookTitles.add(book.title)
                    Log.w(
                        "WebDAV",
                        "Skipping book=${book.id}: size=$localSize exceeds remaining upload quota=$availableBytes"
                    )
                    continue
                }

                try {
                    val entry = uploadBookFile(book, serverUrl, username, password, syncPath)
                    if (entry != null) {
                        confirmedBooks[book.id] = entry
                        confirmedBookIds.add(book.id)
                        booksUploaded++
                    } else {
                        booksFailed++
                        failedBookTitles.add(book.title)
                    }
                } catch (error: Exception) {
                    Log.e("WebDAV", "Upload failed book=${book.id} file=${book.filePath}: ${error.message}", error)
                    if (error is WebdavException && error.serverCode == "TrafficRateExhausted") {
                        quotaError = error
                    }
                    booksFailed++
                    failedBookTitles.add(book.title)
                }
            }

            var dataSynced = 0
            var dataFailed = 0
            val dataEntries = remoteManifest?.data?.toMutableMap() ?: mutableMapOf()
            for (book in books) {
                try {
                    uploadBookData(book.id, serverUrl, username, password, syncPath)
                    dataEntries[book.id] = buildBookDataManifestEntry(book)
                    dataSynced++
                } catch (error: Exception) {
                    dataFailed++
                    Log.e("WebDAV", "Reading data upload failed book=${book.id}: ${error.message}", error)
                }
            }

            webdavClient.upload(
                "$serverUrl/$syncPath/manifest.json",
                SyncManifest(books = confirmedBooks, data = dataEntries).toJson().toByteArray(Charsets.UTF_8),
                username,
                password,
                "application/json"
            )

            val now = System.currentTimeMillis()
            dataStoreManager.updateWebdavLastSyncTime(now)
            dataStoreManager.saveWebdavSyncedBookIds(confirmedBookIds)
            dataStoreManager.saveWebdavConfig(normalized.copy(lastSyncTime = now))

            val failedNames = failedBookTitles.take(2).joinToString("\u3001")
            val failureHint = if (booksFailed > 0) {
                "\uff0c\u5931\u8d25 $booksFailed \u672c" +
                    if (failedNames.isNotBlank()) "\uff1a$failedNames" else ""
            } else ""
            val dataFailureHint = if (dataFailed > 0) {
                "\uff0c\u9605\u8bfb\u6570\u636e\u5931\u8d25 $dataFailed \u6761"
            } else ""
            val quotaHint = quotaError?.let { "\n" + userFacingWebdavError(it) }.orEmpty()
            SyncResult(
                message = "\u5df2\u540c\u6b65 ${confirmedBookIds.size} \u672c\u4e66" +
                    "\uff08\u65b0\u4e0a\u4f20 $booksUploaded \u672c\uff0c\u4e91\u7aef\u5df2\u6709 $booksAlreadyPresent \u672c\uff09" +
                    failureHint +
                    "\uff0c\u540c\u6b65 $dataSynced \u6761\u9605\u8bfb\u6570\u636e" +
                    dataFailureHint + quotaHint,
                success = booksFailed == 0 && dataFailed == 0
            )
        } catch (error: WebdavException) {
            SyncResult(message = "\u540c\u6b65\u5931\u8d25\uff1a${userFacingWebdavError(error)}", success = false)
        } catch (error: Exception) {
            SyncResult(message = "\u540c\u6b65\u5931\u8d25\uff1a${error.message.orEmpty()}", success = false)
        }
    }

    // Reading progress sync (debounced)

    /** Debounced sync for a single book's reading data. Call after progress changes. */
    fun scheduleReadingProgressSync(bookId: String) {
        progressSyncJob?.cancel()
        progressSyncJob = scope.launch {
            delay(5_000) // 5 second debounce
            val config = dataStoreManager.webdavConfig.first()
            if (!config.enabled || config.syncMode != "auto") return@launch
            val password = tokenStore.read() ?: return@launch
            val n = config.normalized()
            try {
                uploadBookData(bookId, n.serverUrl, n.username, password, n.syncPath)
                // Update only the reading-data entry in the remote manifest.
                // Do not rebuild the local book manifest here because that hashes every book file
                // and makes background progress sync feel stuck on large SAF libraries.
                val book = bookRepository.getBookById(bookId)
                val remoteManifest = downloadManifestOrNull(n.serverUrl, n.username, password, n.syncPath)
                if (book != null && remoteManifest != null) {
                    val newManifest = remoteManifest.copy(
                        data = remoteManifest.data + (bookId to buildBookDataManifestEntry(book))
                    )
                    webdavClient.upload(
                        "${n.serverUrl}/${n.syncPath}/manifest.json",
                        newManifest.toJson().toByteArray(Charsets.UTF_8),
                        n.username, password,
                        "application/json"
                    )
                }
            } catch (_: Exception) {
                // Silent fail for background sync
            }
        }
    }

    // ── Test connection ─────────────────────────────────────────────

    suspend fun testConnection(serverUrl: String, username: String, password: String): SyncResult {
        return try {
            webdavClient.testConnection(serverUrl, username, password)
            SyncResult(message = "连接成功", success = true)
        } catch (e: WebdavException) {
            SyncResult(message = e.message ?: "连接失败", success = false)
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    /** Encode a file name for safe use in a URL path segment.
     *  Handles Chinese, spaces, and other non-ASCII characters. */
    private fun encodePathSegment(name: String): String =
        URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun userFacingWebdavError(error: WebdavException): String {
        if (error.serverCode == "TrafficRateExhausted") {
            val remaining = error.availableBytes?.let(::formatBytes) ?: "\u672a\u77e5"
            val required = error.requiredBytes?.let(::formatBytes) ?: "\u672a\u77e5"
            return "WebDAV \u670d\u52a1\u7aef\u4e0a\u4f20\u6d41\u91cf\u989d\u5ea6\u4e0d\u8db3" +
                "\uff08\u5269\u4f59 $remaining\uff0c\u5f53\u524d\u6587\u4ef6\u9700\u8981 $required\uff09\u3002" +
                "\u8bf7\u7b49\u5f85\u989d\u5ea6\u6062\u590d\uff0c\u6216\u5728 WebDAV \u670d\u52a1\u5546\u8c03\u6574\u4e0a\u4f20\u989d\u5ea6\u3002"
        }
        return error.message ?: "WebDAV \u8bf7\u6c42\u5931\u8d25"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)
        else -> "$bytes B"
    }

    private suspend fun buildLocalManifest(): SyncManifest {
        val books = bookRepository.getAllBooks().first()
        val booksMap = mutableMapOf<String, SyncFileEntry>()
        val dataMap = mutableMapOf<String, SyncFileEntry>()

        for (book in books) {
            buildBookManifestEntry(book)?.let { entry ->
                booksMap[book.id] = entry
            }

            // Build data entry using the book's lastReadTime as proxy for data freshness
            dataMap[book.id] = buildBookDataManifestEntry(book)
        }
        return SyncManifest(books = booksMap, data = dataMap)
    }

    private fun buildBookManifestEntry(book: Book): SyncFileEntry? {
        return runCatching {
            if (BookFileAccess.isContentUri(book.filePath)) {
                val data = readBookData(book.filePath)
                SyncFileEntry(
                    fileName = contentBookFileName(book),
                    sha256 = sha256Bytes(data),
                    sizeBytes = data.size.toLong(),
                    lastModified = book.lastReadTime // SAF providers often omit reliable file timestamps.
                )
            } else {
                val file = File(book.filePath)
                if (!file.exists()) return null
                val data = file.readBytes()
                SyncFileEntry(
                    fileName = file.name,
                    sha256 = sha256Bytes(data),
                    sizeBytes = data.size.toLong(),
                    lastModified = file.lastModified()
                )
            }
        }.onFailure { error ->
            Log.e("WebDAV", "Manifest skip: cannot read book id=${book.id} file=${book.filePath}: ${error.message}", error)
        }.getOrNull()
    }

    private fun contentBookFileName(book: Book): String {
        val displayName = BookFileAccess.displayName(context, book.filePath)
            ?.takeIf { it.isNotBlank() }
        if (displayName != null) return displayName
        return book.title + when (book.format) {
            com.huangder.lumibooks.domain.model.BookFormat.EPUB -> ".epub"
            com.huangder.lumibooks.domain.model.BookFormat.PDF -> ".pdf"
            else -> ".txt"
        }
    }

    private fun remoteBookFileName(book: Book): String = "${book.id}.${bookFileExtension(book)}"

    private fun bookFileExtension(book: Book): String {
        val fromName = BookFileAccess.displayName(context, book.filePath)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
        if (fromName != null) return fromName
        return when (book.format) {
            com.huangder.lumibooks.domain.model.BookFormat.EPUB -> "epub"
            com.huangder.lumibooks.domain.model.BookFormat.PDF -> "pdf"
            else -> "txt"
        }
    }

    private fun buildBookDataManifestEntry(book: Book): SyncFileEntry = SyncFileEntry(
        fileName = "${book.id}.json",
        sha256 = "",
        sizeBytes = 0,
        lastModified = book.lastReadTime
    )

    private suspend fun uploadBookFile(
        book: Book,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): SyncFileEntry? {
        val fileName: String
        val expectedSize: Long
        val lastModified: Long

        fileName = remoteBookFileName(book)
        if (BookFileAccess.isContentUri(book.filePath)) {
            expectedSize = BookFileAccess.size(context, book.filePath).let { if (it > 0L) it else -1L }
            lastModified = book.lastReadTime // SAF providers often omit reliable file timestamps.
        } else {
            val file = File(book.filePath)
            if (!file.exists()) return null
            expectedSize = file.length()
            lastModified = file.lastModified()
        }

        if (expectedSize < 0L) {
            // Some SAF providers do not expose a size. In that case use one in-memory read so
            // the WebDAV PUT still has a Content-Length instead of relying on chunked upload,
            // which not every WebDAV server accepts.
            val data = readBookData(book.filePath)
            webdavClient.upload(
                "$serverUrl/$syncPath/books/${encodePathSegment(fileName)}",
                data,
                username,
                password
            )
            return SyncFileEntry(
                fileName = fileName,
                sha256 = sha256Bytes(data),
                sizeBytes = data.size.toLong(),
                lastModified = lastModified
            )
        }

        return try {
            val uploadResult = webdavClient.uploadStream(
                url = "$serverUrl/$syncPath/books/${encodePathSegment(fileName)}",
                contentLength = expectedSize,
                inputStreamProvider = { BookFileAccess.openInputStream(context, book.filePath) },
                username = username,
                password = password
            )

            SyncFileEntry(
                fileName = fileName,
                sha256 = uploadResult.sha256,
                sizeBytes = uploadResult.bytesWritten,
                lastModified = lastModified
            )
        } catch (serverError: WebdavException) {
            // A definitive HTTP rejection will not be fixed by sending the same bytes twice.
            throw serverError
        } catch (streamError: Exception) {
            // Only fall back when streaming failed before the server returned an HTTP response.
            Log.w("WebDAV", "Stream upload failed; retrying fixed-length upload book=${book.id}: ${streamError.message}")
            val data = readBookData(book.filePath)
            webdavClient.upload(
                "$serverUrl/$syncPath/books/${encodePathSegment(fileName)}",
                data,
                username,
                password
            )
            SyncFileEntry(
                fileName = fileName,
                sha256 = sha256Bytes(data),
                sizeBytes = data.size.toLong(),
                lastModified = lastModified
            )
        }
    }

    private suspend fun downloadManifestOrNull(
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): SyncManifest? {
        return runCatching {
            val data = webdavClient.download(
                "$serverUrl/$syncPath/manifest.json",
                username,
                password
            )
            SyncManifest.fromJson(data.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    private suspend fun uploadBookData(
        bookId: String,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ) {
        val json = buildBookDataJson(bookId)
        val data = json.toByteArray(Charsets.UTF_8)
        webdavClient.upload(
            "$serverUrl/$syncPath/data/$bookId.json",
            data, username, password,
            "application/json"
        )
    }

    private suspend fun downloadBookData(
        bookId: String,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ) {
        try {
            val data = webdavClient.download(
                "$serverUrl/$syncPath/data/$bookId.json",
                username, password
            )
            val json = data.toString(Charsets.UTF_8)
            applyBookDataJson(bookId, json)
        } catch (_: WebdavException) { }
    }

    private suspend fun buildBookDataJson(bookId: String): String {
        val book = bookRepository.getBookById(bookId)
        val bookmarks = readingRepository.getBookmarksByBookId(bookId).first()
        val notes = readingRepository.getNotesByBookId(bookId).first()

        return JSONObject().apply {
            put("bookId", bookId)
            if (book != null) {
                put("readingProgress", book.readingProgress.toDouble())
                book.locatorJson?.let { put("locatorJson", it) }
                put("lastReadTime", book.lastReadTime)
            }
            put("bookmarks", JSONArray().apply {
                for (b in bookmarks) {
                    put(JSONObject().apply {
                        put("id", b.id)
                        put("chapterIndex", b.chapterIndex)
                        put("position", b.position.toDouble())
                        b.locatorJson?.let { put("locatorJson", it) }
                        put("title", b.title)
                        put("createdAt", b.createdAt)
                    })
                }
            })
            put("notes", JSONArray().apply {
                for (n in notes) {
                    put(JSONObject().apply {
                        put("id", n.id)
                        put("chapterIndex", n.chapterIndex)
                        put("startPosition", n.startPosition)
                        put("endPosition", n.endPosition)
                        n.startLocatorJson?.let { put("startLocatorJson", it) }
                        n.endLocatorJson?.let { put("endLocatorJson", it) }
                        put("selectedText", n.selectedText)
                        put("note", n.note)
                        put("color", n.color)
                        put("createdAt", n.createdAt)
                    })
                }
            })
        }.toString(2)
    }

    private suspend fun applyBookDataJson(bookId: String, json: String) {
        val root = JSONObject(json)
        val book = bookRepository.getBookById(bookId)
        if (book != null) {
            val progress = root.optDouble("readingProgress", book.readingProgress.toDouble()).toFloat()
            val locator = root.optString("locatorJson", null)
            val lastReadTime = root.optLong("lastReadTime", book.lastReadTime)
            bookRepository.updateReadingProgress(bookId, progress, locator)
            if (lastReadTime > book.lastReadTime) {
                bookRepository.updateLastReadTime(bookId, lastReadTime)
            }
        }

        // Sync bookmarks: clear local, re-insert from remote
        val bookmarksArr = root.optJSONArray("bookmarks") ?: JSONArray()
        readingRepository.deleteAllBookmarksByBookId(bookId)
        for (i in 0 until bookmarksArr.length()) {
            val b = bookmarksArr.getJSONObject(i)
            readingRepository.insertBookmark(Bookmark(
                id = 0, // let Room auto-assign
                bookId = bookId,
                chapterIndex = b.getInt("chapterIndex"),
                position = b.getDouble("position").toFloat(),
                locatorJson = b.optString("locatorJson", null),
                title = b.getString("title"),
                createdAt = b.getLong("createdAt")
            ))
        }

        // Sync notes: clear local, re-insert from remote
        val notesArr = root.optJSONArray("notes") ?: JSONArray()
        readingRepository.deleteAllNotesByBookId(bookId)
        for (i in 0 until notesArr.length()) {
            val n = notesArr.getJSONObject(i)
            readingRepository.insertNote(Note(
                id = 0,
                bookId = bookId,
                chapterIndex = n.getInt("chapterIndex"),
                startPosition = n.getInt("startPosition"),
                endPosition = n.getInt("endPosition"),
                startLocatorJson = n.optString("startLocatorJson", null),
                endLocatorJson = n.optString("endLocatorJson", null),
                selectedText = n.getString("selectedText"),
                note = n.getString("note"),
                color = n.getString("color"),
                createdAt = n.getLong("createdAt")
            ))
        }
    }

    private suspend fun importDownloadedBook(
        file: File,
        bookId: String,
        entry: SyncFileEntry
    ) {
        // Check if book already exists in DB
        val existing = bookRepository.getBookById(bookId)
        if (existing != null) {
            // Update file path if changed
            bookRepository.updateBook(existing.copy(filePath = file.absolutePath))
            return
        }

        val extension = file.extension.lowercase()
        val format = when (extension) {
            "epub" -> com.huangder.lumibooks.domain.model.BookFormat.EPUB
            "pdf" -> com.huangder.lumibooks.domain.model.BookFormat.PDF
            else -> com.huangder.lumibooks.domain.model.BookFormat.TXT
        }

        // Extract cover and metadata from EPUB
        val coverPath = try {
            val parser = BookParserFactory.createParser(format, context)
            parser.extractCoverPath(file.absolutePath)
        } catch (_: Exception) { null }

        val (title, author) = if (format == com.huangder.lumibooks.domain.model.BookFormat.EPUB) {
            try {
                val epubParser = com.huangder.lumibooks.util.parser.EpubParser(context)
                val content = epubParser.parse(file.absolutePath)
                val t = content.title.takeIf { it.isNotBlank() && it != file.nameWithoutExtension }
                    ?: file.nameWithoutExtension
                val a = content.author.takeIf { it.isNotBlank() && it != "未知作者" } ?: "未知作者"
                t to a
            } catch (_: Exception) {
                file.nameWithoutExtension to "未知作者"
            }
        } else {
            file.nameWithoutExtension to "未知作者"
        }

        val book = Book(
            id = bookId,
            title = title,
            author = author,
            filePath = file.absolutePath,
            coverPath = coverPath,
            format = format,
            lastReadTime = entry.lastModified,
            readingProgress = 0f,
            locatorJson = null,
            createdAt = System.currentTimeMillis(),
            isFavorite = false
        )
        bookRepository.insertBook(book)
    }

    /** Read all bytes from a book regardless of storage type (app-internal file or SAF content URI). */
    private fun readBookData(location: String): ByteArray = BookFileAccess.readBytes(context, location)

    private fun sha256Bytes(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class SyncResult(
    val message: String,
    val success: Boolean
)

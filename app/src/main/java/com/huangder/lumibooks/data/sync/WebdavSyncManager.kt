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
        if (!config.enabled) return SyncResult("WebDAV 未启用", false)

        val password = tokenStore.read()
        if (password.isNullOrBlank()) return SyncResult("未配置密码", false)

        val normalized = config.normalized()
        val serverUrl = normalized.serverUrl
        val username = normalized.username
        val syncPath = normalized.syncPath

        return try {
            // 1. Ensure remote directory structure exists
            webdavClient.ensureDirectory(serverUrl, username, password, syncPath)
            webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/books")
            webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/data")

            // 2. Delete old manifest to force a clean rebuild.
            //    Prevents ghost entries from polluted manifests blocking future uploads.
            try {
                webdavClient.delete(
                    "$serverUrl/$syncPath/manifest.json", username, password
                )
            } catch (_: Exception) { }

            val books = bookRepository.getAllBooks().first()
            val uploadedBooks = mutableMapOf<String, SyncFileEntry>()
            val uploadedBookIds = mutableSetOf<String>()
            var booksUploaded = 0
            var booksFailed = 0
            val failedBookTitles = mutableListOf<String>()

            // 3. Upload books one by one. The request body streams from storage and computes
            //    SHA-256 while uploading, avoiding the previous full-library pre-scan and re-scan.
            for (book in books) {
                val entry = try {
                    uploadBookFile(book, serverUrl, username, password, syncPath)
                } catch (e: Exception) {
                    Log.e("WebDAV", "Upload failed book=${book.id} file=${book.filePath}: ${e.message}", e)
                    failedBookTitles.add(book.title)
                    null
                }

                if (entry != null) {
                    uploadedBooks[book.id] = entry
                    uploadedBookIds.add(book.id)
                    booksUploaded++
                } else {
                    booksFailed++
                }
            }

            // 4. Sync reading data (bookmarks, notes, progress) - upload all
            var dataSynced = 0
            var dataFailed = 0
            val dataEntries = mutableMapOf<String, SyncFileEntry>()
            for (book in books) {
                dataEntries[book.id] = buildBookDataManifestEntry(book)
                try {
                    uploadBookData(book.id, serverUrl, username, password, syncPath)
                    dataSynced++
                } catch (e: Exception) {
                    dataFailed++
                    Log.e("WebDAV", "Reading data upload failed book=${book.id}: ${e.message}", e)
                }
            }

            // 5. Upload manifest - only includes books confirmed on server
            val finalManifest = SyncManifest(
                books = uploadedBooks,
                data = dataEntries
            )
            webdavClient.upload(
                "$serverUrl/$syncPath/manifest.json",
                finalManifest.toJson().toByteArray(Charsets.UTF_8),
                username, password,
                "application/json"
            )

            // 6. Update last sync time + synced book IDs (only actually-uploaded books)
            val now = System.currentTimeMillis()
            dataStoreManager.updateWebdavLastSyncTime(now)
            dataStoreManager.saveWebdavSyncedBookIds(uploadedBookIds)
            val newConfig = normalized.copy(lastSyncTime = now)
            dataStoreManager.saveWebdavConfig(newConfig)

            val failureHint = if (booksFailed > 0) {
                val names = failedBookTitles.take(2).joinToString("、")
                "，失败 $booksFailed 本${if (names.isNotBlank()) "：$names" else ""}"
            } else {
                ""
            }
            val dataFailureHint = if (dataFailed > 0) "，阅读数据失败 $dataFailed 条" else ""
            SyncResult(
                message = "上传 $booksUploaded 本书$failureHint，同步 $dataSynced 条阅读数据$dataFailureHint",
                success = booksFailed == 0 && dataFailed == 0
            )
        } catch (e: WebdavException) {
            SyncResult(message = "同步失败：${e.message}", success = false)
        } catch (e: Exception) {
            SyncResult(message = "同步失败：${e.message}", success = false)
        }
    }

    // ── Reading progress sync (debounced) ───────────────────────────

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
        } catch (streamError: Exception) {
            // Some WebDAV servers reject streamed PUT for certain paths/providers. Fall back to
            // the older fixed byte-array upload for this single book so sync can still finish.
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

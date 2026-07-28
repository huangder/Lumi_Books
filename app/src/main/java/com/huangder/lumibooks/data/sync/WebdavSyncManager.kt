package com.huangder.lumibooks.data.sync

import android.content.Context
import android.util.Log
import com.huangder.lumibooks.data.local.DataStoreManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
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

    // ── Full sync ───────────────────────────────────────────────────

    /**
     * Full bidirectional sync: compares local vs remote manifest,
     * uploads newer local files, downloads newer remote files.
     * Returns a result description string.
     */
    suspend fun fullSync(): SyncResult = withContext(Dispatchers.IO) {
        val config = dataStoreManager.webdavConfig.first()
        if (!config.enabled) return@withContext SyncResult("WebDAV 未启用", false)

        val password = tokenStore.read()
        if (password.isNullOrBlank()) return@withContext SyncResult("未配置密码", false)

        val normalized = config.normalized()
        val serverUrl = normalized.serverUrl
        val username = normalized.username
        val syncPath = normalized.syncPath

        try {
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

            // 3. Build local manifest and upload everything fresh
            val localManifest = buildLocalManifest()

            // 4. Upload all local books (individually try-catch'd)
            var booksUploaded = 0
            val uploadedBookIds = mutableSetOf<String>()

            // 4a. Upload all local books — fresh start, no diff needed
            for ((bookId, localEntry) in localManifest.books) {
                try {
                    val book = bookRepository.getBookById(bookId)
                    if (book == null) {
                        Log.w("WebDAV", "Upload skip: book not in DB id=$bookId")
                        continue
                    }
                    val file = File(book.filePath)
                    if (!file.exists()) {
                        Log.w("WebDAV", "Upload skip: file missing path=${book.filePath} book=${book.title}")
                        continue
                    }
                    val data = file.readBytes()
                    webdavClient.upload(
                        "$serverUrl/$syncPath/books/${encodePathSegment(localEntry.fileName)}",
                        data, username, password
                    )
                    booksUploaded++
                    uploadedBookIds.add(bookId)
                } catch (e: Exception) {
                    Log.e("WebDAV", "Upload failed book=$bookId file=${localEntry.fileName}: ${e.message}", e)
                }
            }

            // 5. Sync reading data (bookmarks, notes, progress) — upload all
            var dataSynced = 0
            for ((bookId) in localManifest.data) {
                try {
                    uploadBookData(bookId, serverUrl, username, password, syncPath)
                    dataSynced++
                } catch (_: Exception) { }
            }

            // 6. Upload manifest — only includes books confirmed on server
            val rawManifest = buildLocalManifest()
            val finalManifest = SyncManifest(
                books = rawManifest.books.filterKeys { it in uploadedBookIds },
                data = rawManifest.data
            )
            webdavClient.upload(
                "$serverUrl/$syncPath/manifest.json",
                finalManifest.toJson().toByteArray(Charsets.UTF_8),
                username, password,
                "application/json"
            )

            // 7. Update last sync time + synced book IDs (only actually-uploaded books)
            val now = System.currentTimeMillis()
            dataStoreManager.updateWebdavLastSyncTime(now)
            dataStoreManager.saveWebdavSyncedBookIds(uploadedBookIds)
            val newConfig = normalized.copy(lastSyncTime = now)
            dataStoreManager.saveWebdavConfig(newConfig)

            SyncResult(
                message = "上传 $booksUploaded 本书，同步 $dataSynced 条阅读数据",
                success = true
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
            if (!config.enabled) return@launch
            val password = tokenStore.read() ?: return@launch
            val n = config.normalized()
            try {
                uploadBookData(bookId, n.serverUrl, n.username, password, n.syncPath)
                // Update manifest
                val newManifest = buildLocalManifest()
                webdavClient.upload(
                    "${n.serverUrl}/${n.syncPath}/manifest.json",
                    newManifest.toJson().toByteArray(Charsets.UTF_8),
                    n.username, password,
                    "application/json"
                )
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
            val file = File(book.filePath)
            if (file.exists()) {
                val sha = sha256(file)
                booksMap[book.id] = SyncFileEntry(
                    fileName = file.name,
                    sha256 = sha,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }

            // Build data entry using the book's lastReadTime as proxy for data freshness
            dataMap[book.id] = SyncFileEntry(
                fileName = "${book.id}.json",
                sha256 = "", // will be replaced after upload
                sizeBytes = 0,
                lastModified = book.lastReadTime
            )
        }
        return SyncManifest(books = booksMap, data = dataMap)
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class SyncResult(
    val message: String,
    val success: Boolean
)

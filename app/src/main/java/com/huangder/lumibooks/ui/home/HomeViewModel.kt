package com.huangder.lumibooks.ui.home

import android.app.Application
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookDeleteMode
import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.BookTagLink
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.FolderNameValidator
import com.huangder.lumibooks.domain.model.FolderMoveResult
import com.huangder.lumibooks.domain.model.FolderPreviewPlanner
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.domain.model.LibraryTag
import com.huangder.lumibooks.domain.model.TagNameValidator
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.FolderRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.domain.repository.TagRepository
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.TimeUtils
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** 每日阅读数据 */
data class DailyReading(val date: String, val duration: Long, val dayLabel: String)

data class BookImportCandidate(
    val uri: Uri,
    val name: String,
    val sourceDirectoryUri: String? = null,
    val sourceDirectoryName: String? = null
)

data class HomeUiState(
    val books: List<Book> = emptyList(),
    val todayReadingTime: Long = 0,
    val dailyGoal: Int = 30, // 分钟
    val avatarUri: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortBy: SortBy = SortBy.LAST_READ,
    val isLoading: Boolean = true,
    val bookshelfLayoutMode: Int = 2,
    val importBooksLayoutMode: Int = 2,
    val importMessage: String? = null,
    val authorizedBookDirectories: List<String> = emptyList(),
    val tagMessage: String? = null,
    val error: String? = null,
    val tags: List<LibraryTag> = emptyList(),
    val bookTagLinks: List<BookTagLink> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val bookFolderLinks: List<BookFolderLink> = emptyList(),
    val folderMessage: String? = null,
    /** 当前日历周的阅读数据（周日至周六） */
    val weeklyData: List<DailyReading> = emptyList(),
    /** 连胜天数 */
    val streakDays: Int = 0,
    /** WebDAV 同步已完成的书籍 ID 集合，用于在书架标题旁显示云图标 */
    val syncedBookIds: Set<String> = emptySet(),
    val isWebdavSyncing: Boolean = false,
    val downloadStates: Map<String, BookDownloadState> = emptyMap(),
    val isBookDeleteInProgress: Boolean = false
)

enum class SortBy {
    LAST_READ, TITLE, AUTHOR, DATE_ADDED
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val tagRepository: TagRepository,
    private val folderRepository: FolderRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager,
    private val application: Application,
    private val webdavSyncManager: com.huangder.lumibooks.data.sync.WebdavSyncManager
) : ViewModel() {

    private companion object {
        val SUPPORTED_BOOK_EXTENSIONS = setOf("epub", "pdf", "txt", "mobi")
        const val READING_HISTORY_START_DATE = "1970-01-01"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _downloadedBooks = MutableSharedFlow<Book>(extraBufferCapacity = 1)
    val downloadedBooks: SharedFlow<Book> = _downloadedBooks.asSharedFlow()

    // Importing several documents writes books and folder links one at a time. Defer the
    // first-non-empty folder snapshot until the whole batch is complete, otherwise the first
    // document would permanently consume the folder's one-time snapshot.
    private var folderPreviewInitializationSuspended = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dayLabels = listOf(
        application.getString(R.string.day_sunday),
        application.getString(R.string.day_monday),
        application.getString(R.string.day_tuesday),
        application.getString(R.string.day_wednesday),
        application.getString(R.string.day_thursday),
        application.getString(R.string.day_friday),
        application.getString(R.string.day_saturday)
    )

    init {
        loadBooks()
        loadTags()
        loadFolders()
        loadTodayReadingTime()
        loadAvatar()
        loadWeeklyData()
        loadWebdavSyncStatus()
        loadBookshelfLayoutMode()
        loadImportBooksLayoutMode()
        loadAuthorizedBookDirectories()
    }

    fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                bookRepository.getAllBooks().collectLatest { books ->
                    val sortedBooks = sortBooks(books, _uiState.value.sortBy)
                    _uiState.value = _uiState.value.copy(
                        books = sortedBooks,
                        syncedBookIds = books.asSequence()
                            .filter { it.remoteFileName != null }
                            .mapTo(mutableSetOf()) { it.id },
                        isLoading = false,
                        error = null
                    )
                    scheduleFolderPreviewInitialization(
                        books = sortedBooks,
                        folders = _uiState.value.folders,
                        links = _uiState.value.bookFolderLinks
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            combine(
                tagRepository.getAllTags(),
                tagRepository.getAllBookTagLinks()
            ) { tags, links ->
                tags to links
            }.collectLatest { (tags, links) ->
                _uiState.value = _uiState.value.copy(
                    tags = tags,
                    bookTagLinks = links
                )
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            combine(
                folderRepository.getAllFolders(),
                folderRepository.getAllBookFolderLinks()
            ) { folders, links -> folders to links }
                .collectLatest { (folders, links) ->
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        bookFolderLinks = links
                    )
                    scheduleFolderPreviewInitialization(
                        books = _uiState.value.books,
                        folders = folders,
                        links = links
                    )
                }
        }
    }

    /**
     * Initializes each folder's book-cover snapshot exactly once, after the current database
     * state contains both books and folder links. The DAO conditionally updates the nullable
     * snapshot field, so repeated calls from independent book/folder flows are harmless.
     */
    private fun scheduleFolderPreviewInitialization(
        books: List<Book>,
        folders: List<LibraryFolder>,
        links: List<BookFolderLink>
    ) {
        if (folderPreviewInitializationSuspended) return
        val uninitializedFolders = folders.filter { it.previewBookIds == null }
        if (uninitializedFolders.isEmpty() || books.isEmpty() || links.isEmpty()) return

        val orderedBooks = books.toList()
        viewModelScope.launch(Dispatchers.IO) {
            uninitializedFolders.forEach { folder ->
                val previewIds = FolderPreviewPlanner.selectBookIds(
                    booksInLibraryOrder = orderedBooks,
                    folders = folders,
                    links = links,
                    folderId = folder.id
                )
                if (previewIds.isNotEmpty()) {
                    folderRepository.initializeFolderPreview(folder.id, previewIds)
                }
            }
        }
    }

    private fun loadTodayReadingTime() {
        viewModelScope.launch {
            val today = TimeUtils.getCurrentDate()
            readingRepository.getTotalDurationByDate(today).collectLatest { duration ->
                _uiState.value = _uiState.value.copy(
                    todayReadingTime = duration ?: 0
                )
            }
        }
    }

    private fun loadAvatar() {
        viewModelScope.launch {
            dataStoreManager.avatarUri.collectLatest { uri ->
                _uiState.value = _uiState.value.copy(avatarUri = uri)
            }
        }
    }

    private fun loadAuthorizedBookDirectories() {
        viewModelScope.launch {
            dataStoreManager.authorizedBookDirectories.collectLatest { directories ->
                _uiState.value = _uiState.value.copy(authorizedBookDirectories = directories)
            }
        }
    }

    private fun loadWeeklyData() {
        viewModelScope.launch {
            // 日历周：从本周日开始，到本周六结束
            val startOfWeek = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.SUNDAY
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val today = LocalDate.now()
            combine(
                dataStoreManager.dailyGoal,
                readingRepository.getDailyTotalsBetween(READING_HISTORY_START_DATE, today.toString())
            ) { goal, dailyTotals ->
                goal to dailyTotals
            }.collectLatest { (goal, dailyTotals) ->
                val durationMap = dailyTotals.associate { it.date to it.totalDuration }
                val weeklyData = (0..6).map { i ->
                    val cal = (startOfWeek.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                    val date = dateFormat.format(cal.time)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
                    DailyReading(date, durationMap[date] ?: 0L, dayLabels[dayOfWeek])
                }
                val streak = ReadingStreakCalculator.calculate(
                    dailyDurations = durationMap,
                    today = today,
                    goalDurationMs = goal * 60 * 1000L
                )

                _uiState.value = _uiState.value.copy(
                    dailyGoal = goal,
                    weeklyData = weeklyData,
                    streakDays = streak
                )
            }
        }
    }

    private fun loadBookshelfLayoutMode() {
        viewModelScope.launch {
            dataStoreManager.bookshelfLayoutMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(bookshelfLayoutMode = mode)
            }
        }
    }

    fun setBookshelfLayoutMode(mode: Int) {
        val normalizedMode = mode.coerceIn(1, 3)
        _uiState.value = _uiState.value.copy(bookshelfLayoutMode = normalizedMode)
        viewModelScope.launch {
            dataStoreManager.saveBookshelfLayoutMode(normalizedMode)
        }
    }

    private fun loadImportBooksLayoutMode() {
        viewModelScope.launch {
            dataStoreManager.importBooksLayoutMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(importBooksLayoutMode = mode)
            }
        }
    }

    fun setImportBooksLayoutMode(mode: Int) {
        val normalizedMode = mode.coerceIn(1, 3)
        _uiState.value = _uiState.value.copy(importBooksLayoutMode = normalizedMode)
        viewModelScope.launch {
            dataStoreManager.saveImportBooksLayoutMode(normalizedMode)
        }
    }

    private fun loadWebdavSyncStatus() {
        viewModelScope.launch {
            webdavSyncManager.isSyncing.collectLatest { syncing ->
                _uiState.value = _uiState.value.copy(isWebdavSyncing = syncing)
            }
        }
        viewModelScope.launch {
            webdavSyncManager.downloadStates.collectLatest { states ->
                _uiState.value = _uiState.value.copy(downloadStates = states)
            }
        }
    }

    fun downloadCloudBook(bookId: String) {
        viewModelScope.launch {
            val result = webdavSyncManager.downloadBook(bookId)
            if (result.success && result.book != null) {
                _downloadedBooks.emit(result.book)
            } else {
                _uiState.value = _uiState.value.copy(error = result.message)
            }
        }
    }

    fun syncWebdavNow() {
        _uiState.value = _uiState.value.copy(importMessage = application.getString(R.string.webdav_syncing))
        viewModelScope.launch {
            val result = webdavSyncManager.fullSync()
            _uiState.value = _uiState.value.copy(importMessage = result.message)
        }
    }

    fun saveDailyGoal(minutes: Int) {
        viewModelScope.launch {
            dataStoreManager.saveDailyGoal(minutes)
            _uiState.value = _uiState.value.copy(dailyGoal = minutes)
        }
    }

    fun searchBooks(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            loadBooks()
        } else {
            viewModelScope.launch {
                bookRepository.searchBooks(query).collectLatest { books ->
                    val sortedBooks = sortBooks(books, _uiState.value.sortBy)
                    _uiState.value = _uiState.value.copy(
                        books = sortedBooks,
                        isSearchActive = true
                    )
                }
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isSearchActive = active)
        if (!active) {
            _uiState.value = _uiState.value.copy(searchQuery = "")
            loadBooks()
        }
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
        val sortedBooks = sortBooks(_uiState.value.books, sortBy)
        _uiState.value = _uiState.value.copy(books = sortedBooks)
        scheduleFolderPreviewInitialization(
            books = sortedBooks,
            folders = _uiState.value.folders,
            links = _uiState.value.bookFolderLinks
        )
    }

    private fun sortBooks(books: List<Book>, sortBy: SortBy): List<Book> {
        return when (sortBy) {
            SortBy.LAST_READ -> books.sortedByDescending { it.lastReadTime }
            SortBy.TITLE -> books.sortedBy { it.title.lowercase() }
            SortBy.AUTHOR -> books.sortedBy { it.author.lowercase() }
            SortBy.DATE_ADDED -> books.sortedByDescending { it.createdAt }
        }
    }

    fun insertBook(book: Book) {
        viewModelScope.launch {
            try {
                bookRepository.insertBook(book)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * 异步导入书籍：文件复制 + EPUB/PDF/TXT解析 + 数据库插入
     * 全部在 Dispatchers.IO 上执行，不阻塞主线程
     */
    fun importBook(context: Context, uri: Uri, targetFolderId: String? = null) {
        importBooks(context, listOf(uri), targetFolderId)
    }

    fun importBooks(context: Context, uris: List<Uri>, targetFolderId: String? = null) {
        importDocumentsAsync(
            context = context,
            documents = uris.mapNotNull { uri ->
                val name = FileUtils.getFileNameFromUri(context, uri) ?: return@mapNotNull null
                BookDocument(uri, name)
            },
            copyIntoApp = true,
            targetFolderId = targetFolderId,
            groupByAuthorizedSource = false
        )
    }

    fun importAuthorizedBooks(
        context: Context,
        candidates: List<BookImportCandidate>,
        groupBySourceFolder: Boolean
    ) {
        importDocumentsAsync(
            context = context,
            documents = candidates.map { candidate ->
                BookDocument(
                    uri = candidate.uri,
                    name = candidate.name,
                    sourceDirectoryUri = candidate.sourceDirectoryUri,
                    sourceDirectoryName = candidate.sourceDirectoryName
                )
            },
            copyIntoApp = false,
            targetFolderId = null,
            groupByAuthorizedSource = groupBySourceFolder
        )
    }

    private fun importDocumentsAsync(
        context: Context,
        documents: List<BookDocument>,
        copyIntoApp: Boolean,
        targetFolderId: String?,
        groupByAuthorizedSource: Boolean
    ) {
        if (documents.isEmpty()) return
        folderPreviewInitializationSuspended = true
        viewModelScope.launch {
            val result = try {
                _uiState.value = _uiState.value.copy(importMessage = context.getString(R.string.importing))
                withContext(Dispatchers.IO) {
                    importDocuments(
                        context = context,
                        documents = documents,
                        copyIntoApp = copyIntoApp,
                        targetFolderId = targetFolderId,
                        groupByAuthorizedSource = groupByAuthorizedSource
                    )
                }
            } finally {
                folderPreviewInitializationSuspended = false
            }

            // The Room collectors may still be processing the per-document emissions. Read the
            // final persisted state so the one-time snapshot sees every book in this batch.
            val (books, folders, links) = withContext(Dispatchers.IO) {
                Triple(
                    bookRepository.getAllBooks().first(),
                    folderRepository.getAllFolders().first(),
                    folderRepository.getAllBookFolderLinks().first()
                )
            }
            scheduleFolderPreviewInitialization(
                books = sortBooks(books, _uiState.value.sortBy),
                folders = folders,
                links = links
            )
            _uiState.value = _uiState.value.copy(importMessage = result.toMessage(context))
        }
    }

    fun authorizeBookDirectory(
        context: Context,
        treeUri: Uri,
        onDiscovered: (List<BookImportCandidate>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                dataStoreManager.addAuthorizedBookDirectory(treeUri.toString())
                withContext(Dispatchers.IO) { discoverNewBooks(context, listOf(treeUri)) }
            }
            result.fold(
                onSuccess = { discovery ->
                    onDiscovered(discovery.documents.map { it.toCandidate() })
                    _uiState.value = _uiState.value.copy(
                        importMessage = discovery.messageWhenEmpty(context)
                    )
                },
                onFailure = { error ->
                    onDiscovered(emptyList())
                    _uiState.value = _uiState.value.copy(
                        importMessage = context.getString(R.string.import_failed, error.message.orEmpty())
                    )
                }
            )
        }
    }

    fun scanAuthorizedBookDirectories(
        context: Context,
        onDiscovered: (List<BookImportCandidate>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val directories = dataStoreManager.authorizedBookDirectories.first().map(Uri::parse)
                if (directories.isEmpty()) {
                    return@runCatching BookDiscoveryResult(noAuthorizedDirectories = true)
                }
                withContext(Dispatchers.IO) { discoverNewBooks(context, directories) }
            }
            result.fold(
                onSuccess = { discovery ->
                    onDiscovered(discovery.documents.map { it.toCandidate() })
                    _uiState.value = _uiState.value.copy(
                        importMessage = discovery.messageWhenEmpty(context)
                    )
                },
                onFailure = { error ->
                    onDiscovered(emptyList())
                    _uiState.value = _uiState.value.copy(
                        importMessage = context.getString(R.string.import_failed, error.message.orEmpty())
                    )
                }
            )
        }
    }

    private suspend fun discoverNewBooks(
        context: Context,
        directories: List<Uri>
    ): BookDiscoveryResult {
        val existingLocations = _uiState.value.books.mapTo(mutableSetOf()) { it.filePath }
        val documents = mutableListOf<BookDocument>()
        var inaccessibleDirectories = 0
        directories.forEach { treeUri ->
            runCatching { documents += discoverBooks(context, treeUri) }
                .onFailure {
                    inaccessibleDirectories++
                    dataStoreManager.removeAuthorizedBookDirectory(treeUri.toString())
                }
        }
        val newDocuments = documents
            .distinctBy { it.uri.toString() }
            .filterNot { it.uri.toString() in existingLocations }
        return BookDiscoveryResult(
            documents = newDocuments,
            inaccessibleDirectories = inaccessibleDirectories
        )
    }

    private fun discoverBooks(context: Context, treeUri: Uri): List<BookDocument> {
        val resolver = context.contentResolver
        val sourceDirectoryName = resolveAuthorizedDirectoryName(context, treeUri)
        val pendingDirectories = ArrayDeque<String>()
        pendingDirectories += DocumentsContract.getTreeDocumentId(treeUri)
        val discovered = mutableListOf<BookDocument>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        while (pendingDirectories.isNotEmpty()) {
            val parentId = pendingDirectories.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mimeType = cursor.getString(mimeIndex).orEmpty()
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pendingDirectories += documentId
                    } else if (FileUtils.getFileExtension(name) in SUPPORTED_BOOK_EXTENSIONS) {
                        discovered += BookDocument(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            name = name,
                            sourceDirectoryUri = treeUri.toString(),
                            sourceDirectoryName = sourceDirectoryName
                        )
                    }
                }
            }
        }
        return discovered
    }

    private fun resolveAuthorizedDirectoryName(context: Context, treeUri: Uri): String {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        val displayName = runCatching {
            resolver.query(
                rootDocument,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return displayName ?: Uri.decode(treeId)
            .substringAfter(':', treeId)
            .trim('/')
            .substringAfterLast('/')
            .ifBlank { context.getString(R.string.bookshelf_title) }
    }

    private suspend fun importDocuments(
        context: Context,
        documents: List<BookDocument>,
        copyIntoApp: Boolean,
        targetFolderId: String?,
        groupByAuthorizedSource: Boolean
    ): ImportResult {
        var imported = 0
        var skipped = 0
        var failed = 0
        documents.forEach { document ->
            val extension = FileUtils.getFileExtension(document.name)
            if (extension !in SUPPORTED_BOOK_EXTENSIONS) {
                skipped++
                return@forEach
            }
            var importedLocation: String? = null
            var extractedCoverPath: String? = null
            var insertedBook: Book? = null
            runCatching {
                val location = if (copyIntoApp) {
                    FileUtils.copyFileToInternal(context, document.uri, document.name)?.absolutePath
                        ?: error("Unable to copy ${document.name}")
                } else {
                    document.uri.toString()
                }
                importedLocation = location
                val format = extension.toBookFormat()
                val parser = BookParserFactory.createParser(format, context)
                val coverPath = try {
                    parser.extractCoverPath(location)
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { parser.close() }
                }
                extractedCoverPath = coverPath
                val now = System.currentTimeMillis()
                val bookId = FileUtils.generateBookId()
                val book = Book(
                        id = bookId,
                        title = document.name.substringBeforeLast('.'),
                        author = context.getString(R.string.book_author_unknown),
                        filePath = location,
                        coverPath = coverPath,
                        format = format,
                        lastReadTime = now,
                        readingProgress = 0f,
                        createdAt = now
                    )
                bookRepository.insertBook(book)
                insertedBook = book
                val destinationFolderId = if (groupByAuthorizedSource) {
                    document.sourceDirectoryName
                        ?.let(FolderNameValidator::clean)
                        ?.take(FolderNameValidator.MAX_LENGTH)
                        ?.takeIf(FolderNameValidator::isValid)
                        ?.let { folderRepository.getOrCreateRootFolder(it).id }
                } else {
                    targetFolderId
                }
                if (destinationFolderId != null) {
                    folderRepository.moveBooks(setOf(bookId), destinationFolderId)
                }
            }.onSuccess {
                imported++
            }.onFailure {
                failed++
                insertedBook?.let { book -> runCatching { bookRepository.deleteBook(book) } }
                if (copyIntoApp) {
                    importedLocation?.let { FileUtils.deleteAppManagedBookFile(context, it) }
                }
                FileUtils.deleteAppOwnedFile(context, extractedCoverPath)
            }
        }
        return ImportResult(imported = imported, skipped = skipped, failed = failed)
    }

    private data class BookDocument(
        val uri: Uri,
        val name: String,
        val sourceDirectoryUri: String? = null,
        val sourceDirectoryName: String? = null
    ) {
        fun toCandidate() = BookImportCandidate(
            uri = uri,
            name = name,
            sourceDirectoryUri = sourceDirectoryUri,
            sourceDirectoryName = sourceDirectoryName
        )
    }

    private data class BookDiscoveryResult(
        val documents: List<BookDocument> = emptyList(),
        val inaccessibleDirectories: Int = 0,
        val noAuthorizedDirectories: Boolean = false
    ) {
        fun messageWhenEmpty(context: Context): String? {
            if (documents.isNotEmpty()) return null
            return when {
                noAuthorizedDirectories -> context.getString(R.string.import_no_authorized_directory)
                inaccessibleDirectories > 0 -> context.getString(R.string.import_directory_permission_lost)
                else -> context.getString(R.string.import_no_new_books)
            }
        }
    }

    private data class ImportResult(
        val imported: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val inaccessibleDirectories: Int = 0,
        val noAuthorizedDirectories: Boolean = false
    ) {
        fun toMessage(context: Context): String = when {
            noAuthorizedDirectories -> context.getString(R.string.import_no_authorized_directory)
            failed > 0 -> context.getString(R.string.import_summary_with_failures, imported, failed)
            imported > 0 -> context.getString(R.string.import_summary, imported)
            inaccessibleDirectories > 0 -> context.getString(R.string.import_directory_permission_lost)
            else -> context.getString(R.string.import_no_new_books)
        }
    }

    private fun String.toBookFormat(): BookFormat = when (this) {
        "epub" -> BookFormat.EPUB
        "pdf" -> BookFormat.PDF
        "mobi" -> BookFormat.MOBI
        else -> BookFormat.TXT
    }

    fun clearImportMessage() {
        _uiState.value = _uiState.value.copy(importMessage = null)
    }

    fun createAndAssignTag(bookId: String, rawName: String, parentId: String? = null) {
        if (!validateTagName(rawName)) return
        viewModelScope.launch {
            runCatching { tagRepository.createAndAssignTag(bookId, rawName, parentId) }
                .onSuccess { tag ->
                    if (tag == null) showTagMessage(application.getString(R.string.tag_name_exists))
                }
                .onFailure { showTagMessage(it.message ?: application.getString(R.string.error)) }
        }
    }

    fun setBookTag(bookId: String, tagId: String, isAssigned: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (isAssigned) {
                    tagRepository.assignTag(bookId, tagId)
                } else {
                    tagRepository.removeTagFromBook(bookId, tagId)
                }
            }.onFailure { showTagMessage(it.message ?: application.getString(R.string.error)) }
        }
    }

    fun addTagToBooks(bookIds: Set<String>, tagId: String) {
        if (bookIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                bookIds.forEach { bookId -> tagRepository.assignTag(bookId, tagId) }
            }.onFailure { showTagMessage(it.message ?: application.getString(R.string.error)) }
        }
    }

    fun createAndAssignTagToBooks(bookIds: Set<String>, rawName: String) {
        if (bookIds.isEmpty() || !validateTagName(rawName)) return
        viewModelScope.launch {
            runCatching {
                val firstBookId = bookIds.first()
                val tag = tagRepository.createAndAssignTag(firstBookId, rawName)
                    ?: return@runCatching
                bookIds.asSequence()
                    .filterNot { it == firstBookId }
                    .forEach { bookId -> tagRepository.assignTag(bookId, tag.id) }
            }.onFailure { showTagMessage(it.message ?: application.getString(R.string.error)) }
        }
    }

    fun renameTag(tagId: String, rawName: String) {
        if (!validateTagName(rawName)) return
        viewModelScope.launch {
            runCatching { tagRepository.renameTag(tagId, rawName) }
                .onSuccess { renamed ->
                    if (!renamed) showTagMessage(application.getString(R.string.tag_name_exists))
                }
                .onFailure { showTagMessage(it.message ?: application.getString(R.string.error)) }
        }
    }

    fun deleteTag(tagId: String, deleteChildren: Boolean = false) {
        viewModelScope.launch {
            runCatching { tagRepository.deleteTag(tagId, deleteChildren) }
                .onFailure { showTagMessage(it.message ?: application.getString(R.string.error)) }
        }
    }

    fun clearTagMessage() {
        _uiState.value = _uiState.value.copy(tagMessage = null)
    }

    fun createFolder(
        rawName: String,
        parentId: String?,
        onResult: (LibraryFolder?) -> Unit = {}
    ) {
        if (!validateFolderName(rawName)) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            runCatching { folderRepository.createFolder(rawName, parentId) }
                .onSuccess { folder ->
                    if (folder == null) showFolderMessage(application.getString(R.string.folder_name_exists))
                    onResult(folder)
                }
                .onFailure {
                    showFolderMessage(it.message ?: application.getString(R.string.error))
                    onResult(null)
                }
        }
    }

    fun renameFolder(folderId: String, rawName: String, onResult: (Boolean) -> Unit = {}) {
        if (!validateFolderName(rawName)) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            runCatching { folderRepository.renameFolder(folderId, rawName) }
                .onSuccess { renamed ->
                    if (!renamed) showFolderMessage(application.getString(R.string.folder_name_exists))
                    onResult(renamed)
                }
                .onFailure {
                    showFolderMessage(it.message ?: application.getString(R.string.error))
                    onResult(false)
                }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            runCatching { folderRepository.deleteFolderTree(folderId) }
                .onSuccess { coverPaths ->
                    withContext(Dispatchers.IO) {
                        FileUtils.deleteFolderCoverPaths(application, coverPaths)
                    }
                }
                .onFailure { showFolderMessage(it.message ?: application.getString(R.string.error)) }
        }
    }

    fun updateFolderCover(
        folder: LibraryFolder,
        uri: Uri,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            var newCoverPath: String? = null
            runCatching {
                newCoverPath = withContext(Dispatchers.IO) {
                    FileUtils.copyFolderCoverImage(application, uri, folder.id)
                } ?: error("Unable to copy the selected folder cover")
                check(folderRepository.updateFolderCover(folder.id, newCoverPath)) {
                    "Folder no longer exists"
                }
                withContext(Dispatchers.IO) {
                    FileUtils.deleteOtherFolderCustomCovers(application, folder.id, newCoverPath)
                }
            }.onSuccess {
                showFolderMessage(application.getString(R.string.folder_cover_updated))
                onResult(true)
            }.onFailure { error ->
                newCoverPath?.let { failedCover ->
                    withContext(Dispatchers.IO) {
                        FileUtils.deleteAppOwnedFile(application, failedCover)
                    }
                }
                showFolderMessage(error.message ?: application.getString(R.string.error))
                onResult(false)
            }
        }
    }

    fun removeFolderCover(folder: LibraryFolder, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                check(folderRepository.updateFolderCover(folder.id, null)) {
                    "Folder no longer exists"
                }
                withContext(Dispatchers.IO) {
                    FileUtils.deleteFolderCustomCover(application, folder.id)
                }
            }.onSuccess {
                showFolderMessage(application.getString(R.string.folder_cover_removed))
                onResult(true)
            }.onFailure { error ->
                showFolderMessage(error.message ?: application.getString(R.string.error))
                onResult(false)
            }
        }
    }

    fun moveFolder(
        folderId: String,
        targetParentId: String?,
        onResult: (FolderMoveResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching { folderRepository.moveFolder(folderId, targetParentId) }
                .onSuccess { result ->
                    val message = when (result) {
                        FolderMoveResult.Success -> R.string.folder_relocation_success
                        FolderMoveResult.NoChange -> R.string.folder_relocation_no_change
                        FolderMoveResult.SourceNotFound -> R.string.folder_source_missing
                        FolderMoveResult.TargetNotFound -> R.string.folder_target_missing
                        FolderMoveResult.InvalidTarget -> R.string.folder_invalid_target
                        FolderMoveResult.DuplicateName -> R.string.folder_name_exists
                    }
                    showFolderMessage(application.getString(message))
                    onResult(result)
                }
                .onFailure { error ->
                    showFolderMessage(error.message ?: application.getString(R.string.error))
                }
        }
    }

    fun moveBooksToFolder(
        bookIds: Set<String>,
        targetFolderId: String?,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (bookIds.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            runCatching { folderRepository.moveBooks(bookIds, targetFolderId) }
                .onSuccess {
                    scheduleFolderPreviewInitialization(
                        books = _uiState.value.books,
                        folders = _uiState.value.folders,
                        links = _uiState.value.bookFolderLinks
                    )
                    onResult(true)
                }
                .onFailure {
                    showFolderMessage(it.message ?: application.getString(R.string.error))
                    onResult(false)
                }
        }
    }

    fun clearFolderMessage() {
        _uiState.value = _uiState.value.copy(folderMessage = null)
    }

    private fun validateFolderName(rawName: String): Boolean {
        if (FolderNameValidator.isValid(rawName)) return true
        val message = if (FolderNameValidator.clean(rawName).isEmpty()) {
            application.getString(R.string.folder_name_required)
        } else {
            application.getString(R.string.folder_name_too_long, FolderNameValidator.MAX_LENGTH)
        }
        showFolderMessage(message)
        return false
    }

    private fun showFolderMessage(message: String) {
        _uiState.value = _uiState.value.copy(folderMessage = message)
    }

    private fun validateTagName(rawName: String): Boolean {
        if (TagNameValidator.isValid(rawName)) return true
        val message = if (TagNameValidator.clean(rawName).isEmpty()) {
            application.getString(R.string.tag_name_required)
        } else {
            application.getString(R.string.tag_name_too_long, TagNameValidator.MAX_LENGTH)
        }
        showTagMessage(message)
        return false
    }

    private fun showTagMessage(message: String) {
        _uiState.value = _uiState.value.copy(tagMessage = message)
    }

    /**
     * 重新提取原始封面（用于移除自定义封面后恢复）
     * 在 Dispatchers.IO 上执行 parse，不阻塞主线程
     */
    fun updateCustomCover(book: Book, uri: Uri) {
        viewModelScope.launch {
            var newCoverPath: String? = null
            try {
                newCoverPath = withContext(Dispatchers.IO) {
                    FileUtils.copyCoverImage(application, uri, book.id)
                } ?: error("Unable to copy the selected cover image")

                bookRepository.updateBookMetadata(book.copy(coverPath = newCoverPath))
                withContext(Dispatchers.IO) {
                    FileUtils.deleteOtherCustomCovers(application, book.id, newCoverPath)
                }
            } catch (error: Exception) {
                newCoverPath?.let { failedCover ->
                    withContext(Dispatchers.IO) {
                        FileUtils.deleteAppOwnedFile(application, failedCover)
                    }
                }
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun removeCustomCover(book: Book) {
        viewModelScope.launch {
            try {
                val originalCover = extractOriginalCover(application, book)
                bookRepository.updateBookMetadata(book.copy(coverPath = originalCover))
                withContext(Dispatchers.IO) {
                    FileUtils.deleteCustomCover(application, book.id)
                }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun reExtractCover(context: Context, book: Book) {
        viewModelScope.launch {
            try {
                val originalCover = extractOriginalCover(context.applicationContext, book)
                bookRepository.updateBookMetadata(book.copy(coverPath = originalCover))
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    private suspend fun extractOriginalCover(context: Context, book: Book): String? {
        return withContext(Dispatchers.IO) {
            val parser = BookParserFactory.createParser(book.format, context)
            try {
                parser.extractCoverPath(book.filePath)
            } finally {
                runCatching { parser.close() }
            }
        }
    }

    fun deleteBook(book: Book, mode: BookDeleteMode = BookDeleteMode.LOCAL_ONLY) {
        deleteBooks(listOf(book), mode)
    }

    fun deleteBooks(
        books: List<Book>,
        mode: BookDeleteMode = BookDeleteMode.LOCAL_ONLY
    ) {
        val booksToDelete = books.distinctBy { it.id }
        if (booksToDelete.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBookDeleteInProgress = true)
            if (mode == BookDeleteMode.LOCAL_AND_CLOUD) {
                val remoteIds = booksToDelete.asSequence()
                    .filter { it.remoteFileName != null }
                    .mapTo(mutableSetOf()) { it.id }
                if (remoteIds.isNotEmpty()) {
                    val remoteResult = webdavSyncManager.deleteRemoteBooks(remoteIds)
                    if (!remoteResult.success) {
                        _uiState.value = _uiState.value.copy(
                            error = remoteResult.message,
                            isBookDeleteInProgress = false
                        )
                        return@launch
                    }
                }
            }
            val failures = deleteBooksAndManagedData(booksToDelete, mode)
            if (failures.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    error = failures.joinToString(separator = "\n"),
                    isBookDeleteInProgress = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isBookDeleteInProgress = false)
            }
        }
    }

    private suspend fun deleteBooksAndManagedData(
        books: List<Book>,
        mode: BookDeleteMode
    ): List<String> {
        val librarySnapshot = _uiState.value.books
        val requestedIds = books.mapTo(mutableSetOf()) { it.id }
        val pathsUsedByRemainingBooks = librarySnapshot.asSequence()
            .filterNot { it.id in requestedIds }
            .map { it.filePath }
            .toSet()
        val deletedIds = mutableSetOf<String>()
        val failures = mutableListOf<String>()

        books.forEach { book ->
            val keepCloudPlaceholder = mode == BookDeleteMode.LOCAL_ONLY &&
                book.remoteFileName != null
            if (keepCloudPlaceholder && book.isCloudOnly) return@forEach
            try {
                val shouldDeletePhysicalFile = book.filePath.isNotBlank() &&
                    book.filePath !in pathsUsedByRemainingBooks
                if (shouldDeletePhysicalFile) {
                    val fileDeleted = withContext(Dispatchers.IO) {
                        FileUtils.deleteAppManagedBookFile(application, book.filePath)
                    }
                    check(fileDeleted) { "Unable to delete the local file for ${book.title}" }
                }

                if (keepCloudPlaceholder) {
                    bookRepository.markBookCloudOnly(book.id)
                    withContext(Dispatchers.IO) {
                        FileUtils.deleteTxtIndexCache(application, book.filePath)
                    }
                } else {
                    bookRepository.deleteBook(book)
                    deletedIds += book.id
                }
            } catch (error: Exception) {
                failures += error.message ?: "Unable to delete ${book.title}"
            }
        }

        if (deletedIds.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                books.filter { it.id in deletedIds }.forEach { book ->
                    FileUtils.deleteCustomCover(application, book.id)
                    FileUtils.deleteTxtIndexCache(application, book.filePath)
                }

                books.asSequence()
                    .filter { it.id in deletedIds }
                    .mapNotNull { it.coverPath }
                    .distinct()
                    .filter { coverPath ->
                        librarySnapshot.none { it.id !in deletedIds && it.coverPath == coverPath }
                    }
                    .forEach { coverPath ->
                        FileUtils.deleteAppOwnedFile(application, coverPath)
                    }
            }

        }

        return failures
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            try {
                bookRepository.updateBookMetadata(book)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

}

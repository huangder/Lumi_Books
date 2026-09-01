package com.huangder.lumibooks.ui.home

import android.app.Application
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.provider.DocumentsContract
import android.util.Log
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
import com.huangder.lumibooks.util.AuthorizedStorageManager
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val sourceDirectoryName: String? = null,
    /** Directory path relative to the authorized root, separated by '/'. */
    val sourceRelativeDirectory: String? = null,
    val sourceDirectoryDocumentUri: String? = null,
    val sourceDocumentKey: String? = null,
    val sourceLastModified: Long = 0L,
    val sourceSize: Long = 0L,
    val sourceDirectoryBindings: List<FolderRepository.StorageBinding> = emptyList()
) {
    val lastModified: Long get() = sourceLastModified
    val documentKey: String? get() = sourceDocumentKey
    val physicalParentUri: String? get() = sourceDirectoryDocumentUri
}

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
    val bookshelfLayoutModeLoaded: Boolean = false,
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
    private val webdavSyncManager: com.huangder.lumibooks.data.sync.WebdavSyncManager,
    private val authorizedStorageManager: AuthorizedStorageManager
) : ViewModel() {

    private companion object {
        val SUPPORTED_BOOK_EXTENSIONS = setOf("epub", "pdf", "txt", "mobi")
        const val READING_HISTORY_START_DATE = "1970-01-01"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _downloadedBooks = MutableSharedFlow<Book>(extraBufferCapacity = 1)
    val downloadedBooks: SharedFlow<Book> = _downloadedBooks.asSharedFlow()

    // Importing several documents writes books and folder links one at a time. Defer preview
    // refreshes until the whole batch is complete so the cover collage is computed once.
    private var folderPreviewInitializationSuspended = false
    private val authorizedStorageMutex = Mutex()

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
        loadBookshelfSortMode()
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

    /** Refreshes each folder's four-book cover snapshot after books or links change. */
    private fun scheduleFolderPreviewInitialization(
        books: List<Book>,
        folders: List<LibraryFolder>,
        links: List<BookFolderLink>
    ) {
        if (folderPreviewInitializationSuspended) return
        if (folders.isEmpty()) return

        val orderedBooks = books.toList()
        viewModelScope.launch(Dispatchers.IO) {
            folders.forEach { folder ->
                val previewIds = FolderPreviewPlanner.selectBookIds(
                    booksInLibraryOrder = orderedBooks,
                    folders = folders,
                    links = links,
                    folderId = folder.id
                )
                folderRepository.refreshFolderPreview(folder.id, previewIds)
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
                _uiState.value = _uiState.value.copy(
                    bookshelfLayoutMode = mode,
                    bookshelfLayoutModeLoaded = true
                )
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

    private fun loadBookshelfSortMode() {
        viewModelScope.launch {
            dataStoreManager.bookshelfSortMode.collectLatest { stored ->
                val sort = runCatching { SortBy.valueOf(stored) }.getOrDefault(SortBy.LAST_READ)
                _uiState.value = _uiState.value.copy(
                    sortBy = sort,
                    books = sortBooks(_uiState.value.books, sort)
                )
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
        viewModelScope.launch { dataStoreManager.saveBookshelfSortMode(sortBy.name) }
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
                BookDocument(
                    uri = uri,
                    name = name,
                    sourceDocumentKey = authorizedStorageManager.documentKey(uri),
                    sourceLastModified = authorizedStorageManager.queryLastModified(context, uri),
                    sourceSize = runCatching { com.huangder.lumibooks.util.BookFileAccess.size(context, uri.toString()) }.getOrDefault(0L)
                )
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
                    sourceDirectoryName = candidate.sourceDirectoryName,
                    sourceRelativeDirectory = candidate.sourceRelativeDirectory,
                    sourceDirectoryDocumentUri = candidate.sourceDirectoryDocumentUri,
                    sourceDocumentKey = candidate.sourceDocumentKey,
                    sourceLastModified = candidate.sourceLastModified,
                    sourceSize = candidate.sourceSize,
                    sourceDirectoryBindings = candidate.sourceDirectoryBindings
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
                withContext(Dispatchers.IO) { reconcileAuthorizedBooks(context, directories) }
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
        val documents = mutableListOf<BookDocument>()
        var inaccessibleDirectories = 0
        directories.forEach { treeUri ->
            runCatching { documents += discoverBooks(context, treeUri) }
                .onFailure {
                    inaccessibleDirectories++
                }
        }
        var existing = bookRepository.getAllBooks().first()
        var missingHashesChecked = false
        val newDocuments = mutableListOf<BookDocument>()
        documents.distinctBy {
            authorizedDocumentIdentity(it.sourceDocumentKey, it.uri.toString())
        }.forEach { document ->
            val uri = document.uri.toString()
            val directMatch = findMatchingAuthorizedBook(
                documentKey = document.sourceDocumentKey,
                uri = uri,
                sha256 = null,
                books = existing
            )
            if (directMatch != null) return@forEach

            val hash = runCatching { authorizedStorageManager.sha256(context, uri) }.getOrNull()
            if (hash != null && !missingHashesChecked) {
                existing = repairMissingSourceHashes(context, existing)
                missingHashesChecked = true
            }
            if (findMatchingAuthorizedBook(document.sourceDocumentKey, uri, hash, existing) == null) {
                newDocuments += document
            }
        }
        return BookDiscoveryResult(
            documents = newDocuments,
            inaccessibleDirectories = inaccessibleDirectories
        )
    }

    private suspend fun repairMissingSourceHashes(
        context: Context,
        books: List<Book>
    ): List<Book> = backfillMissingSourceHashes(
        books = books,
        hashLocation = { location ->
            runCatching { authorizedStorageManager.sha256(context, location) }.getOrNull()
        },
        persist = bookRepository::updateSourceSha256
    )

    private fun discoverBooks(
        context: Context,
        treeUri: Uri,
        scan: AuthorizedStorageManager.ScanResult = authorizedStorageManager.scan(context, treeUri)
    ): List<BookDocument> {
        val sourceDirectoryName = scan.directories
            .firstOrNull { it.relativePath == null }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: resolveAuthorizedDirectoryName(context, treeUri)
        val directoriesByPath = scan.directories.associateBy { it.relativePath.orEmpty() }
        return scan.documents.map { item ->
            val rootBinding = scan.directories.firstOrNull { it.relativePath == null }?.let { directory ->
                FolderRepository.StorageBinding(
                    name = directory.name,
                    treeUri = directory.treeUri.toString(),
                    documentUri = directory.uri.toString(),
                    parentUri = directory.parentUri?.toString()
                )
            }
            val childBindings = item.relativeDirectory.orEmpty()
                .split('/')
                .filter(String::isNotBlank)
                .runningFold("") { path, segment ->
                    if (path.isBlank()) segment else "$path/$segment"
                }
                .drop(1)
                .mapNotNull { path -> directoriesByPath[path] }
                .map { directory ->
                    FolderRepository.StorageBinding(
                        name = directory.name,
                        treeUri = directory.treeUri.toString(),
                        documentUri = directory.uri.toString(),
                        parentUri = directory.parentUri?.toString()
                    )
                }
            val bindings = listOfNotNull(rootBinding) + childBindings
            BookDocument(
                uri = item.uri,
                name = item.name,
                sourceDirectoryUri = treeUri.toString(),
                sourceDirectoryName = sourceDirectoryName,
                sourceRelativeDirectory = item.relativeDirectory,
                sourceDirectoryDocumentUri = item.parentUri.toString(),
                sourceDocumentKey = item.documentKey,
                sourceLastModified = item.lastModified,
                sourceSize = item.size,
                sourceDirectoryBindings = bindings
            )
        }
    }

    /** Reconciles authorized documents with existing records without deleting user data. */
    private suspend fun reconcileAuthorizedBooks(
        context: Context,
        directories: List<Uri>
    ): BookDiscoveryResult = authorizedStorageMutex.withLock {
        val allDocuments = mutableListOf<BookDocument>()
        val allDirectories = mutableListOf<AuthorizedStorageManager.ScannedDirectory>()
        val scannedTreeUris = mutableSetOf<String>()
        var inaccessibleDirectories = 0
        directories.forEach { treeUri ->
            runCatching {
                val scan = authorizedStorageManager.scan(context, treeUri)
                allDirectories += scan.directories
                scannedTreeUris += treeUri.toString()
                ensureStorageFolders(scan)
                allDocuments += discoverBooks(context, treeUri, scan)
            }
                .onFailure { inaccessibleDirectories++ }
        }
        var existing = bookRepository.getAllBooks().first()
        val folders = folderRepository.getAllFolders().first()
        val foldersByStorageUri = folders.mapNotNull { folder ->
            folder.storageDocumentUri?.let { it to folder }
        }.toMap()

        // Rebind physical folders first so subsequent book reconciliation can resolve ownership.
        allDirectories.forEach { directory ->
            val folder = foldersByStorageUri[directory.uri.toString()] ?: return@forEach
            val parentFolderId = directory.parentUri?.toString()
                ?.let { parentUri -> foldersByStorageUri[parentUri]?.id }
            folderRepository.reconcileStorageFolder(
                folderId = folder.id,
                name = directory.name,
                storageTreeUri = directory.treeUri.toString(),
                storageDocumentUri = directory.uri.toString(),
                storageParentUri = directory.parentUri?.toString(),
                storageMissing = false
            )
            if (folder.parentId != parentFolderId) {
                folderRepository.reconcileFolderParent(
                    folderId = folder.id,
                    parentId = parentFolderId,
                    storageParentUri = directory.parentUri?.toString()
                )
            }
        }
        val scannedDocumentUris = allDirectories.mapTo(mutableSetOf()) { it.uri.toString() }
        folders.filter { it.storageTreeUri in scannedTreeUris && it.storageDocumentUri != null }
            .filter { it.storageDocumentUri !in scannedDocumentUris }
            .forEach { folderRepository.markStorageMissing(it.id, true) }
        folders.filter { it.storageTreeUri != null && it.storageTreeUri !in scannedTreeUris }
            .forEach { folderRepository.markStorageMissing(it.id, true) }
        val matchedIds = mutableSetOf<String>()
        var updatedCount = 0
        val newDocuments = mutableListOf<BookDocument>()
        var missingHashesChecked = false

        for (document in allDocuments.distinctBy {
            authorizedDocumentIdentity(it.sourceDocumentKey, it.uri.toString())
        }) {
            val uri = document.uri.toString()
            val directMatch = findMatchingAuthorizedBook(
                documentKey = document.sourceDocumentKey,
                uri = uri,
                sha256 = null,
                books = existing,
                claimedBookIds = matchedIds
            )
            val hash = if (directMatch == null ||
                shouldRefreshAuthorizedHash(directMatch, document.sourceLastModified)
            ) {
                runCatching { authorizedStorageManager.sha256(context, uri) }.getOrNull()
            } else {
                directMatch.sourceSha256
            }
            if (directMatch == null && hash != null && !missingHashesChecked) {
                existing = repairMissingSourceHashes(context, existing)
                missingHashesChecked = true
            }
            val match = directMatch ?: findMatchingAuthorizedBook(
                documentKey = document.sourceDocumentKey,
                uri = uri,
                sha256 = hash,
                books = existing,
                claimedBookIds = matchedIds
            )
            if (match == null) {
                newDocuments += document
                continue
            }

            matchedIds += match.id
            val oldDefaultTitle = match.sourceDisplayName
                ?.substringBeforeLast('.', match.sourceDisplayName)
                .orEmpty()
            val shouldUpdateTitle = oldDefaultTitle.isNotBlank() &&
                match.title == oldDefaultTitle
            val updated = match.copy(
                title = if (shouldUpdateTitle) document.name.substringBeforeLast('.') else match.title,
                filePath = document.uri.toString(),
                sourceUri = match.sourceUri ?: document.uri.toString(),
                sourceDocumentKey = document.sourceDocumentKey,
                sourceParentUri = document.sourceDirectoryDocumentUri,
                sourceSha256 = hash ?: match.sourceSha256,
                sourceDisplayName = document.name,
                sourceLastModified = document.sourceLastModified,
                isMissing = false
            )
            if (updated != match) {
                bookRepository.updateBook(updated)
                updatedCount++
            }
            val physicalFolder = folders.firstOrNull {
                !it.storageDocumentUri.isNullOrBlank() &&
                    it.storageDocumentUri == document.sourceDirectoryDocumentUri
            }
            if (physicalFolder != null) {
                runCatching { folderRepository.moveBooks(setOf(match.id), physicalFolder.id) }
            }
        }

        var missingCount = 0
        existing.filter { it.id !in matchedIds && it.sourceParentUri != null }.forEach { book ->
            if (!book.isMissing) {
                bookRepository.updateBook(book.copy(isMissing = true))
                missingCount++
            }
        }
        BookDiscoveryResult(
            documents = newDocuments,
            inaccessibleDirectories = inaccessibleDirectories,
            updatedCount = updatedCount,
            missingCount = missingCount
        )
    }

    private suspend fun ensureStorageFolders(scan: AuthorizedStorageManager.ScanResult) {
        val root = scan.directories.firstOrNull { it.relativePath == null } ?: return
        val byPath = scan.directories.associateBy { it.relativePath.orEmpty() }
        val rootBinding = FolderRepository.StorageBinding(
            name = root.name,
            treeUri = root.treeUri.toString(),
            documentUri = root.uri.toString(),
            parentUri = root.parentUri?.toString()
        )

        // Providers are free to return children in any order. Creating shallow paths first
        // makes the parent relationship deterministic and also ensures empty directories are
        // represented even when they contain no supported book files.
        scan.directories
            .sortedBy { it.relativePath?.count { ch -> ch == '/' } ?: -1 }
            .forEach { directory ->
                val bindings = directory.relativePath.orEmpty()
                    .split('/')
                    .filter(String::isNotBlank)
                    .runningFold("") { path, segment ->
                        if (path.isBlank()) segment else "$path/$segment"
                    }
                    .drop(1)
                    .mapNotNull { byPath[it] }
                    .map { item ->
                        FolderRepository.StorageBinding(
                            name = item.name,
                            treeUri = item.treeUri.toString(),
                            documentUri = item.uri.toString(),
                            parentUri = item.parentUri?.toString()
                        )
                    }
                // One malformed provider row must not prevent all other physical folders from
                // being reconciled. The associated book scan will still report the directory as
                // inaccessible if its contents cannot be read.
                runCatching {
                    val folder = folderRepository.getOrCreateFolderPath(
                        rootName = root.name,
                        relativeDirectory = directory.relativePath,
                        storageBindings = listOf(rootBinding) + bindings
                    )
                    check(
                        folderRepository.reconcileStorageFolder(
                            folderId = folder.id,
                            name = directory.name,
                            storageTreeUri = directory.treeUri.toString(),
                            storageDocumentUri = directory.uri.toString(),
                            storageParentUri = directory.parentUri?.toString(),
                            storageMissing = false
                        )
                    ) { "Unable to persist physical folder binding" }
                }.onFailure { error ->
                    Log.w(
                        "AuthorizedStorage",
                        "Unable to reconcile physical folder ${directory.relativePath ?: root.name}",
                        error
                    )
                }
            }
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
        var knownBooks = bookRepository.getAllBooks().first()
        var missingHashesChecked = false
        documents.forEach { document ->
            val extension = FileUtils.getFileExtension(document.name)
            if (extension !in SUPPORTED_BOOK_EXTENSIONS) {
                skipped++
                return@forEach
            }
            val incomingKey = document.sourceDocumentKey
                ?: authorizedStorageManager.documentKey(document.uri)
            val incomingUri = document.uri.toString()
            val incomingHash = runCatching {
                authorizedStorageManager.sha256(context, incomingUri)
            }.getOrNull()
            val directDuplicate = findMatchingAuthorizedBook(
                documentKey = incomingKey,
                uri = incomingUri,
                sha256 = null,
                books = knownBooks
            )
            if (directDuplicate == null && incomingHash != null && !missingHashesChecked) {
                knownBooks = repairMissingSourceHashes(context, knownBooks)
                missingHashesChecked = true
            }
            val duplicate = directDuplicate ?: findMatchingAuthorizedBook(
                documentKey = incomingKey,
                uri = incomingUri,
                sha256 = incomingHash,
                books = knownBooks
            )
            if (duplicate != null) {
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
                        createdAt = now,
                        sourceUri = incomingUri,
                        sourceDocumentKey = incomingKey,
                        sourceParentUri = document.sourceDirectoryDocumentUri,
                        sourceSha256 = incomingHash,
                        sourceDisplayName = document.name,
                        sourceLastModified = document.sourceLastModified,
                        isMissing = false
                    )
                bookRepository.insertBook(book)
                insertedBook = book
                val destinationFolderId = if (groupByAuthorizedSource) {
                    document.sourceDirectoryName
                        ?.let(FolderNameValidator::clean)
                        ?.take(FolderNameValidator.MAX_LENGTH)
                        ?.takeIf(FolderNameValidator::isValid)
                        ?.let {
                            folderRepository.getOrCreateFolderPath(
                                rootName = it,
                                relativeDirectory = document.sourceRelativeDirectory,
                                storageBindings = document.sourceDirectoryBindings
                            ).id
                        }
                } else {
                    targetFolderId
                }
                if (destinationFolderId != null) {
                    folderRepository.moveBooks(setOf(bookId), destinationFolderId)
                }
            }.onSuccess {
                imported++
                insertedBook?.let { knownBooks = knownBooks + it }
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
        val sourceDirectoryName: String? = null,
        val sourceRelativeDirectory: String? = null,
        val sourceDirectoryDocumentUri: String? = null,
        val sourceDocumentKey: String? = null,
        val sourceLastModified: Long = 0L,
        val sourceSize: Long = 0L,
        val sourceDirectoryBindings: List<FolderRepository.StorageBinding> = emptyList()
    ) {
        fun toCandidate() = BookImportCandidate(
            uri = uri,
            name = name,
            sourceDirectoryUri = sourceDirectoryUri,
            sourceDirectoryName = sourceDirectoryName,
            sourceRelativeDirectory = sourceRelativeDirectory,
            sourceDirectoryDocumentUri = sourceDirectoryDocumentUri,
            sourceDocumentKey = sourceDocumentKey,
            sourceLastModified = sourceLastModified,
            sourceSize = sourceSize,
            sourceDirectoryBindings = sourceDirectoryBindings
        )
    }

    private data class PendingAuthorizedDirectory(
        val documentId: String,
        val relativeDirectory: String?
    )

    private data class BookDiscoveryResult(
        val documents: List<BookDocument> = emptyList(),
        val inaccessibleDirectories: Int = 0,
        val noAuthorizedDirectories: Boolean = false,
        val updatedCount: Int = 0,
        val missingCount: Int = 0
    ) {
        fun messageWhenEmpty(context: Context): String? {
            if (documents.isNotEmpty()) return null
            return when {
                noAuthorizedDirectories -> context.getString(R.string.import_no_authorized_directory)
                inaccessibleDirectories > 0 -> context.getString(R.string.import_directory_permission_lost)
                updatedCount > 0 || missingCount > 0 -> context.getString(
                    R.string.import_refresh_summary,
                    updatedCount,
                    missingCount
                )
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
        storageTreeUri: String? = null,
        onResult: (LibraryFolder?) -> Unit = {}
    ) {
        if (!validateFolderName(rawName)) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            runCatching {
                authorizedStorageMutex.withLock {
                val folders = folderRepository.getAllFolders().first()
                val parent = parentId?.let { id -> folders.firstOrNull { it.id == id } }
                val explicitVirtual = storageTreeUri == ""
                val treeUri = if (explicitVirtual) null else storageTreeUri ?: parent?.storageTreeUri
                val physicalParentUri: Uri? = if (explicitVirtual) null else {
                    if (storageTreeUri != null) {
                        treeUri?.let { authorizedStorageManager.treeRootUri(Uri.parse(it)) }
                    } else {
                        parent?.storageDocumentUri?.let(Uri::parse)
                            ?: treeUri?.let { authorizedStorageManager.treeRootUri(Uri.parse(it)) }
                    }
                }
                if (physicalParentUri == null) {
                    folderRepository.createFolder(rawName, parentId)
                } else {
                    val createdUri = withContext(Dispatchers.IO) {
                        authorizedStorageManager.createDirectory(application, physicalParentUri, FolderNameValidator.clean(rawName))
                    }
                    val storedDocumentUri = authorizedStorageManager.documentUriUsingTree(
                        treeUri?.let(Uri::parse) ?: error("Missing authorized tree URI"),
                        createdUri
                    )
                    runCatching {
                        folderRepository.createFolder(
                            rawName = rawName,
                            parentId = parentId,
                            storageTreeUri = treeUri,
                            storageDocumentUri = storedDocumentUri.toString(),
                            storageParentUri = physicalParentUri.toString()
                        ) ?: error(application.getString(R.string.folder_name_exists))
                    }.onFailure {
                        // Do not leave an orphaned physical directory when Room rejects the name.
                        runCatching { DocumentsContract.deleteDocument(application.contentResolver, createdUri) }
                        throw it
                    }.getOrThrow()
                }
                }
            }
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
            runCatching {
                val folder = folderRepository.getAllFolders().first().firstOrNull { it.id == folderId }
                    ?: return@runCatching false
                val cleanName = FolderNameValidator.clean(rawName)
                val oldName = folder.name
                val renamedUri = folder.storageDocumentUri?.let { uri ->
                    withContext(Dispatchers.IO) {
                        authorizedStorageManager.rename(application, Uri.parse(uri), cleanName)
                    }
                }
                val renamed = folderRepository.renameFolder(folderId, cleanName)
                if (!renamed) {
                    renamedUri?.let { uri ->
                        runCatching {
                            withContext(Dispatchers.IO) {
                                authorizedStorageManager.rename(application, uri, oldName)
                            }
                        }
                    }
                    false
                } else {
                    if (renamedUri != null) {
                        folderRepository.reconcileStorageFolder(
                            folderId = folderId,
                            name = cleanName,
                            storageTreeUri = folder.storageTreeUri,
                            storageDocumentUri = renamedUri.toString(),
                            storageParentUri = folder.storageParentUri,
                            storageMissing = false
                        )
                    }
                    true
                }
            }
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
            runCatching {
                authorizedStorageMutex.withLock {
                val folders = folderRepository.getAllFolders().first()
                val source = folders.firstOrNull { it.id == folderId }
                    ?: return@runCatching FolderMoveResult.SourceNotFound
                val target = targetParentId?.let { id -> folders.firstOrNull { it.id == id } }
                if (source.storageDocumentUri != null) {
                    if (targetParentId != null && target?.storageDocumentUri == null) {
                        showFolderMessage(application.getString(R.string.folder_physical_move_blocked))
                        return@runCatching FolderMoveResult.InvalidTarget
                    }
                    val targetParentUri: Uri? = target?.storageDocumentUri?.let(Uri::parse)
                        ?: source.storageTreeUri?.let { authorizedStorageManager.treeRootUri(Uri.parse(it)) }
                    if (targetParentUri == null || source.storageParentUri.isNullOrBlank()) {
                        showFolderMessage(application.getString(R.string.folder_physical_move_blocked))
                        return@runCatching FolderMoveResult.InvalidTarget
                    }
                    val movedUri = withContext(Dispatchers.IO) {
                        authorizedStorageManager.move(
                            application,
                            Uri.parse(source.storageDocumentUri),
                            Uri.parse(source.storageParentUri),
                            targetParentUri
                        )
                    }
                    val result = folderRepository.moveFolder(folderId, targetParentId)
                    if (result != FolderMoveResult.Success) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                authorizedStorageManager.move(application, movedUri, targetParentUri, Uri.parse(source.storageParentUri))
                            }
                        }
                    } else {
                        folderRepository.reconcileStorageFolder(
                            folderId = folderId,
                            name = source.name,
                            storageTreeUri = source.storageTreeUri,
                            storageDocumentUri = movedUri.toString(),
                            storageParentUri = targetParentUri.toString(),
                            storageMissing = false
                        )
                    }
                    result
                } else if (target?.storageDocumentUri != null) {
                    showFolderMessage(application.getString(R.string.folder_physical_move_blocked))
                    FolderMoveResult.InvalidTarget
                } else {
                    folderRepository.moveFolder(folderId, targetParentId)
                }
                }
            }
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
        allowCrossStorageMove: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (bookIds.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            runCatching {
                authorizedStorageMutex.withLock {
                val folders = folderRepository.getAllFolders().first()
                val books = bookRepository.getAllBooks().first().filter { it.id in bookIds }
                val target = targetFolderId?.let { id -> folders.firstOrNull { it.id == id } }
                val failures = mutableListOf<String>()
                var movedCount = 0
                books.forEach { book ->
                    try {
                        if (target == null) {
                            if (book.sourceParentUri != null) {
                                error(application.getString(R.string.folder_physical_move_blocked))
                            }
                            folderRepository.moveBooks(setOf(book.id), null)
                        } else if (target.storageDocumentUri == null) {
                            if (book.sourceParentUri != null) {
                                error(application.getString(R.string.folder_physical_move_blocked))
                            }
                            folderRepository.moveBooks(setOf(book.id), target.id)
                        } else {
                            val targetParent = Uri.parse(target.storageDocumentUri).let { documentUri ->
                                target.storageTreeUri
                                    ?.let { treeUri ->
                                        authorizedStorageManager.documentUriUsingTree(
                                            Uri.parse(treeUri),
                                            documentUri
                                        )
                                    }
                                    ?: documentUri
                            }
                            val sourceIsAuthorized = book.sourceParentUri != null && book.filePath.startsWith("content://")
                            if (!sourceIsAuthorized && !allowCrossStorageMove) {
                                error(application.getString(R.string.folder_cross_storage_confirmation))
                            }
                            val movedUri: Uri
                            val hash: String
                            if (sourceIsAuthorized) {
                                val sourceParent = Uri.parse(book.sourceParentUri).let { documentUri ->
                                    val treeUri = target.storageTreeUri?.let(Uri::parse)
                                    val sameTree = treeUri != null &&
                                        treeUri.authority == documentUri.authority &&
                                        runCatching {
                                            DocumentsContract.getTreeDocumentId(treeUri) ==
                                                DocumentsContract.getTreeDocumentId(documentUri)
                                        }.getOrDefault(false)
                                    if (sameTree) {
                                        authorizedStorageManager.documentUriUsingTree(treeUri, documentUri)
                                    } else {
                                        documentUri
                                    }
                                }
                                if (sourceParent == targetParent) {
                                    movedUri = Uri.parse(book.filePath)
                                    hash = book.sourceSha256 ?: withContext(Dispatchers.IO) {
                                        authorizedStorageManager.sha256(application, book.filePath)
                                    }
                                } else {
                                    movedUri = withContext(Dispatchers.IO) {
                                        runCatching {
                                            authorizedStorageManager.move(application, Uri.parse(book.filePath), sourceParent, targetParent)
                                        }.getOrElse {
                                            authorizedStorageManager.copyThenDelete(
                                                application,
                                                book.filePath,
                                                targetParent,
                                                book.sourceDisplayName ?: Uri.parse(book.filePath).lastPathSegment.orEmpty(),
                                                expectedSha256 = runCatching {
                                                    authorizedStorageManager.sha256(application, book.filePath)
                                                }.getOrNull()
                                            ).destinationUri
                                        }
                                    }
                                    hash = withContext(Dispatchers.IO) {
                                        authorizedStorageManager.sha256(application, movedUri.toString())
                                    }
                                }
                            } else {
                                check(FileUtils.isAppManagedBookLocation(application, book.filePath)) {
                                    application.getString(R.string.folder_physical_move_blocked)
                                }
                                val result = withContext(Dispatchers.IO) {
                                    val currentHash = authorizedStorageManager.sha256(application, book.filePath)
                                    authorizedStorageManager.copyThenDelete(
                                        application,
                                        book.filePath,
                                        targetParent,
                                        book.sourceDisplayName ?: FileUtils.getFileNameFromUri(application, Uri.parse(book.filePath))
                                            ?: java.io.File(book.filePath).name.takeIf { it.isNotBlank() }
                                            ?: book.title,
                                        expectedSha256 = currentHash
                                    )
                                }
                                movedUri = result.destinationUri
                                hash = result.sha256
                            }
                            val movedBook = book.copy(
                                filePath = movedUri.toString(),
                                sourceDocumentKey = authorizedStorageManager.documentKey(movedUri),
                                sourceParentUri = target.storageDocumentUri,
                                sourceSha256 = hash,
                                sourceDisplayName = withContext(Dispatchers.IO) {
                                    authorizedStorageManager.queryDisplayName(application, movedUri)
                                } ?: book.sourceDisplayName,
                                isMissing = false
                            )
                            bookRepository.updateBook(movedBook)
                            folderRepository.moveBooks(setOf(book.id), target.id)
                        }
                        movedCount++
                    } catch (error: Throwable) {
                        failures += error.message ?: book.title
                    }
                }
                if (failures.isNotEmpty()) {
                    showFolderMessage(application.getString(R.string.folder_move_partial, movedCount, failures.size))
                }
                check(failures.isEmpty()) { failures.joinToString("; ") }
                }
            }.onSuccess {
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

    /**
     * Returns whether moving these books to the target would copy files out of the app-managed
     * books directory. The UI uses this to show an explicit confirmation before the destructive
     * copy-and-delete operation begins.
     */
    fun requiresCrossStorageMoveConfirmation(bookIds: Set<String>, targetFolderId: String?): Boolean {
        val target = targetFolderId?.let { id -> _uiState.value.folders.firstOrNull { it.id == id } }
        if (target?.storageDocumentUri == null) return false
        return _uiState.value.books.any { book ->
            book.id in bookIds &&
                book.sourceParentUri == null &&
                FileUtils.isAppManagedBookLocation(application, book.filePath)
        }
    }

    /** Compatibility overload for existing callers that pass the completion lambda third. */
    fun moveBooksToFolder(
        bookIds: Set<String>,
        targetFolderId: String?,
        onResult: (Boolean) -> Unit
    ) = moveBooksToFolder(bookIds, targetFolderId, false, onResult)

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
            var remoteFailure: String? = null
            if (mode.cloudFailureBlocksLocalDelete) {
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
            if (mode.forcesLocalDelete) {
                val remoteFileNames = booksToDelete.associate { book ->
                    book.id to (book.remoteFileName ?: "${book.id}.${book.format.name.lowercase()}")
                }
                val remoteResult = webdavSyncManager.deleteRemoteBooks(
                    bookIds = booksToDelete.mapTo(mutableSetOf()) { it.id },
                    remoteFileNames = remoteFileNames,
                    publishPortableState = true
                )
                if (!remoteResult.success) {
                    remoteFailure = application.getString(
                        R.string.force_delete_cloud_failed,
                        remoteResult.message
                    )
                }
            }
            val messages = failures + listOfNotNull(remoteFailure)
            if (messages.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    error = messages.joinToString(separator = "\n"),
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
                    val fileDeleted = if (mode.forcesLocalDelete) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                FileUtils.deleteAppManagedBookFile(application, book.filePath)
                            }
                        }.getOrDefault(false)
                    } else {
                        withContext(Dispatchers.IO) {
                            FileUtils.deleteAppManagedBookFile(application, book.filePath)
                        }
                    }
                    if (!fileDeleted) {
                        if (mode.forcesLocalDelete) {
                            failures += application.getString(
                                R.string.force_delete_file_cleanup_failed,
                                book.title
                            )
                        } else {
                            error("Unable to delete the local file for ${book.title}")
                        }
                    }
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

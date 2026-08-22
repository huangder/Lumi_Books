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
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookTagLink
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.LibraryTag
import com.huangder.lumibooks.domain.model.TagNameValidator
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.domain.repository.TagRepository
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.TimeUtils
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class BookImportCandidate(val uri: Uri, val name: String)

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
    /** 当前日历周的阅读数据（周日至周六） */
    val weeklyData: List<DailyReading> = emptyList(),
    /** 连胜天数 */
    val streakDays: Int = 0,
    /** WebDAV 同步已完成的书籍 ID 集合，用于在书架标题旁显示云图标 */
    val syncedBookIds: Set<String> = emptySet(),
    val isWebdavSyncing: Boolean = false
)

enum class SortBy {
    LAST_READ, TITLE, AUTHOR, DATE_ADDED
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val tagRepository: TagRepository,
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
                        isLoading = false,
                        error = null
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
            dataStoreManager.webdavSyncedBookIds.collectLatest { ids ->
                _uiState.value = _uiState.value.copy(syncedBookIds = ids)
            }
        }
        viewModelScope.launch {
            webdavSyncManager.isSyncing.collectLatest { syncing ->
                _uiState.value = _uiState.value.copy(isWebdavSyncing = syncing)
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
    fun importBook(context: Context, uri: Uri) {
        importBooks(context, listOf(uri))
    }

    fun importBooks(context: Context, uris: List<Uri>) {
        importBooks(context, uris, copyIntoApp = true)
    }

    fun importAuthorizedBooks(context: Context, uris: List<Uri>) {
        importBooks(context, uris, copyIntoApp = false)
    }

    private fun importBooks(context: Context, uris: List<Uri>, copyIntoApp: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importMessage = context.getString(R.string.importing))
            val result = withContext(Dispatchers.IO) {
                importDocuments(context, uris.mapNotNull { uri ->
                    val name = FileUtils.getFileNameFromUri(context, uri) ?: return@mapNotNull null
                    BookDocument(uri, name)
                }, copyIntoApp = copyIntoApp)
            }
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
                    onDiscovered(discovery.documents.map { BookImportCandidate(it.uri, it.name) })
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
                    onDiscovered(discovery.documents.map { BookImportCandidate(it.uri, it.name) })
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
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            name
                        )
                    }
                }
            }
        }
        return discovered
    }

    private suspend fun importDocuments(
        context: Context,
        documents: List<BookDocument>,
        copyIntoApp: Boolean
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
                bookRepository.insertBook(
                    Book(
                        id = FileUtils.generateBookId(),
                        title = document.name.substringBeforeLast('.'),
                        author = context.getString(R.string.book_author_unknown),
                        filePath = location,
                        coverPath = coverPath,
                        format = format,
                        lastReadTime = now,
                        readingProgress = 0f,
                        createdAt = now
                    )
                )
            }.onSuccess {
                imported++
            }.onFailure {
                failed++
                if (copyIntoApp) {
                    importedLocation?.let { FileUtils.deleteAppManagedBookFile(context, it) }
                }
                FileUtils.deleteAppOwnedFile(context, extractedCoverPath)
            }
        }
        return ImportResult(imported = imported, skipped = skipped, failed = failed)
    }

    private data class BookDocument(val uri: Uri, val name: String)

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

                bookRepository.updateBook(book.copy(coverPath = newCoverPath))
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
                bookRepository.updateBook(book.copy(coverPath = originalCover))
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
                bookRepository.updateBook(book.copy(coverPath = originalCover))
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

    fun deleteBook(book: Book) {
        deleteBooks(listOf(book))
    }

    fun deleteBooks(books: List<Book>) {
        val booksToDelete = books.distinctBy { it.id }
        if (booksToDelete.isEmpty()) return
        viewModelScope.launch {
            val failures = deleteBooksAndManagedData(booksToDelete)
            if (failures.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    error = failures.joinToString(separator = "\n")
                )
            }
        }
    }

    private suspend fun deleteBooksAndManagedData(books: List<Book>): List<String> {
        val librarySnapshot = _uiState.value.books
        val requestedIds = books.mapTo(mutableSetOf()) { it.id }
        val pathsUsedByRemainingBooks = librarySnapshot.asSequence()
            .filterNot { it.id in requestedIds }
            .map { it.filePath }
            .toSet()
        val deletedIds = mutableSetOf<String>()
        val failures = mutableListOf<String>()

        books.forEach { book ->
            try {
                val shouldDeletePhysicalFile = book.filePath !in pathsUsedByRemainingBooks
                if (shouldDeletePhysicalFile) {
                    val fileDeleted = withContext(Dispatchers.IO) {
                        FileUtils.deleteAppManagedBookFile(application, book.filePath)
                    }
                    check(fileDeleted) { "?????${book.title}????????" }
                }

                // Room transaction removes the book and all database rows that reference it.
                // Authorized-directory content:// documents are intentionally not touched.
                bookRepository.deleteBook(book)
                deletedIds += book.id
            } catch (error: Exception) {
                failures += error.message ?: "???${book.title}???"
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

            runCatching {
                val syncedIds = dataStoreManager.webdavSyncedBookIds.first()
                dataStoreManager.saveWebdavSyncedBookIds(syncedIds - deletedIds)
            }
        }

        return failures
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            try {
                bookRepository.updateBook(book)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

}

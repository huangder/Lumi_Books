package com.huangder.lumibooks

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import android.view.ActionMode
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.ui.navigation.MainNavGraph
import com.huangder.lumibooks.ui.navigation.Screen
import com.huangder.lumibooks.ui.home.backfillMissingSourceHashes
import com.huangder.lumibooks.ui.home.findMatchingAuthorizedBook
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.service.TtsMediaButtons
import com.huangder.lumibooks.ui.splash.SplashScreen
import com.huangder.lumibooks.ui.components.AppUpdateDialog
import com.huangder.lumibooks.ui.components.ExternalImportChoiceDialog
import com.huangder.lumibooks.ui.components.ExternalImportFolder
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.PolicyUpdateDialog
import com.huangder.lumibooks.ui.components.RemoteNoticeDialog
import com.huangder.lumibooks.ui.settings.WebViewActivity
import com.huangder.lumibooks.ui.welcome.WelcomeActivity
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.AuthorizedFolderDocumentSnapshot
import com.huangder.lumibooks.util.AuthorizedFolderSnapshotStore
import com.huangder.lumibooks.util.AuthorizedStorageManager
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.BuiltinGuideSeeder
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.UpdateChecker
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 启动时更新检查的待处理结果（用于弹出 Dialog） */
private data class PendingPolicyUpdate(
    val hasTermsUpdate: Boolean,
    val termsVersion: Int,
    val hasPrivacyUpdate: Boolean,
    val privacyVersion: Int
)

/** 外部打开/分享导入支持的书籍扩展名。 */
private val SUPPORTED_IMPORT_EXTENSIONS = setOf("epub", "pdf", "txt", "mobi", "cbz")

private fun Intent?.extractImportUris(): List<Uri> {
    val importIntent = this ?: return emptyList()
    if (importIntent.action !in setOf(
            Intent.ACTION_VIEW,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
        )
    ) {
        return emptyList()
    }

    val uris = mutableListOf<Uri>()
    importIntent.data?.let(uris::add)
    when (importIntent.action) {
        Intent.ACTION_SEND -> {
            IntentCompat.getParcelableExtra(
                importIntent,
                Intent.EXTRA_STREAM,
                Uri::class.java
            )?.let(uris::add)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            IntentCompat.getParcelableArrayListExtra(
                importIntent,
                Intent.EXTRA_STREAM,
                Uri::class.java
            )?.let(uris::addAll)
        }
    }
    importIntent.clipData?.let { clipData ->
        repeat(clipData.itemCount) { index ->
            clipData.getItemAt(index).uri?.let(uris::add)
        }
    }
    return uris.distinctBy(Uri::toString)
}


private data class PendingAppUpdate(
    val appVersion: String,
    val latestVersionCode: Long,
    val title: String,
    val message: String,
    val changelog: String,
    val releaseUrl: String,
    val force: Boolean
)

private data class PendingRemoteNotice(
    val id: String,
    val title: String,
    val message: String
)

enum class ReaderPageDirection {
    PREVIOUS,
    NEXT
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_BOOK_ID = "open_book_id"
        const val EXTRA_OPEN_DESTINATION = "open_destination"
        const val EXTRA_OPEN_FOLDER_ID = "open_folder_id"
        const val DESTINATION_BOOKSHELF = "bookshelf"
    }

    private var systemDarkMode by mutableStateOf(false)
    private var requestedOpenBookId by mutableStateOf<String?>(null)
    /** 外部文件打开已匹配到本地书籍时，绕过首页和书籍过渡页直达阅读器。 */
    private var requestedOpenBookDirect by mutableStateOf(false)
    private var requestedOpenBookshelf by mutableStateOf(false)
    private var requestedOpenFolderId by mutableStateOf<String?>(null)
    private val authorizedStorageManager = AuthorizedStorageManager()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var builtinGuideSeeder: BuiltinGuideSeeder

    @Inject
    lateinit var ttsController: TtsController

    @Inject
    lateinit var authorizedFolderSnapshotStore: AuthorizedFolderSnapshotStore

    /**
     * 当 ReaderScreen 处于前台时置为 true，
     * 确保 ActionMode 拦截只在阅读页生效，不影响其他页面。
     */
    var isInReaderScreen = false

    /** 非空时由当前阅读页接管音量键；阅读页离开或设置关闭时恢复系统音量行为。 */
    var readerVolumeKeyHandler: ((ReaderPageDirection) -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = readerVolumeKeyHandler
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> ReaderPageDirection.PREVIOUS
            KeyEvent.KEYCODE_VOLUME_DOWN -> ReaderPageDirection.NEXT
            else -> null
        }
        if (handler != null && direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                handler(direction)
            }
            return true
        }
        // 部分 ROM / 耳机把媒体按键投递给前台窗口而不是 MediaSession。
        // 听书进行中时在这里兜底处理；未听书时不拦截，仍交给系统的媒体按键路由。
        if (TtsMediaButtons.isSupportedEvent(event) &&
            ttsController.playbackState.value != TtsPlaybackState.IDLE
        ) {
            TtsMediaButtons.handle(ttsController, event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // Startup dialog pending states.
    /** Pending app update dialog; non-null means show it after startup. */
    private var pendingAppUpdate by mutableStateOf<PendingAppUpdate?>(null)

    /** Pending remote notice dialog; non-null means show it after startup. */
    private var pendingRemoteNotice by mutableStateOf<PendingRemoteNotice?>(null)

    /** Pending terms/privacy policy update dialog; non-null means show it after startup. */
    private var pendingPolicyUpdate by mutableStateOf<PendingPolicyUpdate?>(null)

    /** Externally opened files waiting for a copy/move decision. */
    private var pendingExternalImportUris by mutableStateOf<List<Uri>>(emptyList())
    private var pendingExternalImportFolders by mutableStateOf<List<ExternalImportFolder>>(emptyList())

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val requestedBookId = intent
            ?.getStringExtra(EXTRA_OPEN_BOOK_ID)
            ?.takeIf { it.isNotBlank() }
        requestedOpenBookId = requestedBookId
        requestedOpenBookDirect = false
        requestedOpenFolderId = intent
            ?.getStringExtra(EXTRA_OPEN_FOLDER_ID)
            ?.takeIf { it.isNotBlank() }
        requestedOpenBookshelf = requestedBookId == null &&
            (requestedOpenFolderId != null ||
                intent?.getStringExtra(EXTRA_OPEN_DESTINATION) == DESTINATION_BOOKSHELF)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    /** 处理“打开方式”或系统分享菜单传入的书籍文件。 */
    private fun handleImportIntent(intent: Intent?) {
        val uris = intent.extractImportUris()
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val directories = runCatching { dataStoreManager.authorizedBookDirectories.first() }
                .getOrDefault(emptyList())
            val knownBooks = withContext(Dispatchers.IO) { bookRepository.getAllBooks().first() }
            val needsChoice = directories.isNotEmpty() &&
                uris.any { uri -> needsExternalImportChoice(uri, knownBooks) }
            val folders = if (needsChoice) {
                withContext(Dispatchers.IO) { resolveAuthorizedFolderChoices(directories) }
            } else {
                emptyList()
            }
            if (folders.isEmpty()) {
                runExternalImport(uris, ExternalImportMode.COPY_INTO_APP, targetTreeUri = null)
            } else {
                pendingExternalImportFolders = folders
                pendingExternalImportUris = uris
            }
        }
    }

    /** 需要用户决定保存位置：文件受支持，且书库里还没有它的记录。 */
    private fun needsExternalImportChoice(uri: Uri, knownBooks: List<Book>): Boolean {
        val fileName = FileUtils.getFileNameFromUri(this, uri) ?: return false
        if (FileUtils.getFileExtension(fileName) !in SUPPORTED_IMPORT_EXTENSIONS) return false
        if (findAppManagedBook(uri, knownBooks) != null) return false
        return findMatchingAuthorizedBook(
            documentKey = authorizedStorageManager.documentKey(uri),
            uri = uri.toString(),
            sha256 = null,
            books = knownBooks
        ) == null
    }

    /** 授权文件夹的展示名，读取失败时退回树 URI 的最后一段路径。 */
    private fun resolveAuthorizedFolderChoices(directories: List<String>): List<ExternalImportFolder> =
        directories.map { value ->
            val name = runCatching {
                val treeUri = Uri.parse(value)
                authorizedStorageManager.queryDisplayName(
                    this,
                    authorizedStorageManager.treeRootUri(treeUri)
                )
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallbackAuthorizedFolderName(value)
            ExternalImportFolder(name = name, treeUri = value)
        }

    private fun fallbackAuthorizedFolderName(treeUri: String): String = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            .substringAfter(':', missingDelimiterValue = "")
            .trim('/')
            .substringAfterLast('/')
            .ifBlank { treeUri }
    }.getOrDefault(treeUri)

    private fun runExternalImport(
        uris: List<Uri>,
        mode: ExternalImportMode,
        targetTreeUri: String?
    ) {
        if (uris.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            var imported = 0
            var opened = 0
            var failed = 0
            uris.forEach { uri ->
                runCatching { importBookFromUri(uri, mode, targetTreeUri) }
                    .onSuccess { outcome ->
                        when (outcome) {
                            ImportOutcome.IMPORTED -> imported++
                            ImportOutcome.OPENED -> opened++
                            ImportOutcome.UNSUPPORTED -> failed++
                        }
                    }
                    .onFailure { error ->
                        failed++
                        Log.e("MainActivity", "Unable to import shared book: $uri", error)
                    }
            }
            withContext(Dispatchers.Main) {
                val message = when {
                    failed > 0 -> getString(R.string.import_summary_with_failures, imported, failed)
                    imported == 0 && opened > 0 -> getString(R.string.import_already_opened, opened)
                    opened > 0 -> getString(R.string.import_summary_with_opened, imported, opened)
                    else -> getString(R.string.import_summary, imported)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private enum class ImportOutcome { IMPORTED, OPENED, UNSUPPORTED }

    /** 外部文件的保存方式：复制进应用目录，或移动进授权文件夹。 */
    private enum class ExternalImportMode { COPY_INTO_APP, MOVE_TO_AUTHORIZED }

    /**
     * FileProvider URIs created by this app point at the same managed file stored in the book
     * record. Match that exact canonical path before falling back to a whole-file hash scan.
     */
    private fun findAppManagedBook(uri: Uri, books: List<Book>): Book? {
        if (!uri.scheme.equals("content", ignoreCase = true) ||
            uri.authority != "${BuildConfig.APPLICATION_ID}.fileprovider"
        ) {
            return null
        }
        val segments = uri.pathSegments
        if (segments.size < 2 || segments.first() != "books") return null

        val booksRoot = runCatching { FileUtils.getBooksDirectory(this).canonicalFile }.getOrNull()
            ?: return null
        val candidate = runCatching {
            segments.drop(1).fold(booksRoot) { parent, segment -> File(parent, segment) }.canonicalFile
        }.getOrNull() ?: return null
        if (!candidate.isFile || !candidate.path.startsWith(booksRoot.path + File.separator)) return null

        return books.firstOrNull { book ->
            if (book.isCloudOnly || book.filePath.isBlank() || BookFileAccess.isContentUri(book.filePath)) {
                false
            } else {
                runCatching { File(book.filePath).canonicalFile == candidate }.getOrDefault(false)
            }
        }
    }

    private suspend fun importBookFromUri(
        uri: Uri,
        mode: ExternalImportMode = ExternalImportMode.COPY_INTO_APP,
        targetTreeUri: String? = null
    ): ImportOutcome {
        val fileName = FileUtils.getFileNameFromUri(this, uri) ?: return ImportOutcome.UNSUPPORTED
        val extension = FileUtils.getFileExtension(fileName)
        if (extension !in SUPPORTED_IMPORT_EXTENSIONS) return ImportOutcome.UNSUPPORTED

        val sourceKey = authorizedStorageManager.documentKey(uri)
        val sourceUri = uri.toString()
        var knownBooks = bookRepository.getAllBooks().first()
        val directExisting = findAppManagedBook(uri, knownBooks) ?: findMatchingAuthorizedBook(
            documentKey = sourceKey,
            uri = sourceUri,
            sha256 = null,
            books = knownBooks
        )
        if (directExisting != null) {
            withContext(Dispatchers.Main) {
                requestedOpenBookId = directExisting.id
                requestedOpenBookDirect = true
            }
            return ImportOutcome.OPENED
        }

        val sourceHash = runCatching { authorizedStorageManager.sha256(this, sourceUri) }.getOrNull()
        val existing = if (sourceHash != null) {
            knownBooks = backfillMissingSourceHashes(
                books = knownBooks,
                hashLocation = { location ->
                    runCatching { authorizedStorageManager.sha256(this, location) }.getOrNull()
                },
                persist = bookRepository::updateSourceSha256
            )
            findMatchingAuthorizedBook(
                documentKey = sourceKey,
                uri = sourceUri,
                sha256 = sourceHash,
                books = knownBooks
            )
        } else null
        if (existing != null) {
            withContext(Dispatchers.Main) {
                requestedOpenBookId = existing.id
                requestedOpenBookDirect = true
            }
            return ImportOutcome.OPENED
        }

        return when (mode) {
            ExternalImportMode.COPY_INTO_APP -> importCopiedBook(
                uri = uri,
                fileName = fileName,
                extension = extension,
                sourceKey = sourceKey,
                sourceHash = sourceHash
            )
            ExternalImportMode.MOVE_TO_AUTHORIZED -> {
                val treeUri = targetTreeUri
                    ?.let { value -> runCatching { Uri.parse(value) }.getOrNull() }
                    ?: return ImportOutcome.UNSUPPORTED
                importMovedBook(
                    uri = uri,
                    fileName = fileName,
                    extension = extension,
                    treeUri = treeUri
                )
            }
        }
    }

    /** 复制到应用私有目录，沿用一直以来的外部打开导入行为。 */
    private suspend fun importCopiedBook(
        uri: Uri,
        fileName: String,
        extension: String,
        sourceKey: String?,
        sourceHash: String?
    ): ImportOutcome {
        val file = FileUtils.copyFileToInternal(this, uri, fileName) ?: return ImportOutcome.UNSUPPORTED
        val format = extension.toBookFormat()
        val coverPath = extractBookCover(file.absolutePath, format)
        // 导入时从文件解析真实标题和作者，而不是写死"未知作者"
        val (parsedTitle, parsedAuthor) = resolveBookMetadata(file.absolutePath, fileName, format)

        val book = Book(
            id = FileUtils.generateBookId(),
            title = parsedTitle,
            author = parsedAuthor,
            filePath = file.absolutePath,
            coverPath = coverPath,
            format = format,
            lastReadTime = System.currentTimeMillis(),
            readingProgress = 0f,
            createdAt = System.currentTimeMillis(),
            sourceUri = uri.toString(),
            sourceDocumentKey = sourceKey,
             sourceSha256 = sourceHash ?: runCatching {
                 authorizedStorageManager.sha256(this, file.absolutePath)
             }.getOrNull(),
             sourceDisplayName = fileName,
             sourceLastModified = authorizedStorageManager.queryLastModified(this, uri)
         )
        try {
            bookRepository.insertBook(book)
        } catch (error: Throwable) {
            FileUtils.deleteAppManagedBookFile(this, file.absolutePath)
            FileUtils.deleteAppOwnedFile(this, coverPath)
            throw error
        }
        Log.d("MainActivity", "Imported book from intent: ${book.title}")
        return ImportOutcome.IMPORTED
    }

    /**
     * 把外部文件搬进授权文件夹：先复制到授权目录，再尽力删除原文件。
     * ACTION_VIEW 通常只授予读权限，删不掉时保留原文件，等价于复制。
     */
    private suspend fun importMovedBook(
        uri: Uri,
        fileName: String,
        extension: String,
        treeUri: Uri
    ): ImportOutcome {
        val treeRootUri = authorizedStorageManager.treeRootUri(treeUri)
        val move = runCatching {
            authorizedStorageManager.copyThenDelete(
                context = this,
                sourceLocation = uri.toString(),
                targetParentUri = treeRootUri,
                fileName = fileName,
                deleteSource = false
            )
        }.getOrNull() ?: return ImportOutcome.UNSUPPORTED

        val location = move.destinationUri.toString()
        val sourceRemoved = runCatching {
            DocumentsContract.deleteDocument(contentResolver, uri)
        }.getOrDefault(false)
        if (!sourceRemoved) {
            Log.w("MainActivity", "Book copied into an authorized folder but the source stayed: $uri")
        }

        val format = extension.toBookFormat()
        val coverPath = extractBookCover(location, format)
        val (parsedTitle, parsedAuthor) = resolveBookMetadata(location, fileName, format)
        val documentKey = authorizedStorageManager.documentKey(move.destinationUri)
        val lastModified = authorizedStorageManager.queryLastModified(this, move.destinationUri)
        val now = System.currentTimeMillis()
        val book = Book(
            id = FileUtils.generateBookId(),
            title = parsedTitle,
            author = parsedAuthor,
            filePath = location,
            coverPath = coverPath,
            format = format,
            lastReadTime = now,
            readingProgress = 0f,
            createdAt = now,
            sourceUri = location,
            sourceDocumentKey = documentKey,
            sourceParentUri = treeRootUri.toString(),
            sourceSha256 = move.sha256,
            sourceDisplayName = fileName,
            sourceLastModified = lastModified
        )
        bookRepository.insertBook(book)
        // 让“选择文件夹书籍”页面立刻能看到这本书，无需等待下次扫描。
        runCatching {
            authorizedFolderSnapshotStore.addDocument(
                treeUri = treeUri,
                document = AuthorizedFolderDocumentSnapshot(
                    documentUri = location,
                    documentKey = documentKey,
                    parentDocumentUri = treeRootUri.toString(),
                    displayName = fileName,
                    relativeDirectory = null,
                    size = BookFileAccess.size(this, location),
                    lastModified = lastModified
                )
            )
        }
        Log.d("MainActivity", "Moved book from intent into authorized folder: ${book.title}")
        return ImportOutcome.IMPORTED
    }

    private fun extractBookCover(location: String, format: BookFormat): String? = runCatching {
        val parser = BookParserFactory.createParser(format, this)
        try {
            parser.extractCoverPath(location)
        } finally {
            runCatching { parser.close() }
        }
    }.getOrNull()

    private fun resolveBookMetadata(
        location: String,
        fileName: String,
        format: BookFormat
    ): Pair<String, String> {
        val fallback = fileName.substringBeforeLast('.') to getString(R.string.book_author_unknown)
        if (format != BookFormat.EPUB && format != BookFormat.MOBI) return fallback
        return runCatching {
            val parser = BookParserFactory.createParser(format, this)
            try {
                val content = parser.parse(location)
                val title = content.title
                    .takeIf { it.isNotBlank() && it != fileName.substringBeforeLast('.') }
                    ?: fallback.first
                val unknownAuthor = getString(R.string.book_author_unknown)
                val author = content.author
                    .takeIf { it.isNotBlank() && it != unknownAuthor }
                    ?: unknownAuthor
                title to author
            } finally {
                runCatching { parser.close() }
            }
        }.getOrDefault(fallback)
    }

    private fun String.toBookFormat(): BookFormat = when (this) {
        "epub" -> BookFormat.EPUB
        "pdf" -> BookFormat.PDF
        "mobi" -> BookFormat.MOBI
        "cbz" -> BookFormat.CBZ
        else -> BookFormat.TXT
    }

    /**
     * 不拦截 ActionMode：选区检测由 ReadView 的 SpanWatcher 处理，
     * 系统浮动工具栏通过 menu.clear() 清空菜单项（显示为空气泡）。
     * 不调用 mode.finish()，避免破坏选区手柄状态。
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val debugPreviewLanguage = intent.getBooleanExtra(
            WelcomeActivity.EXTRA_DEBUG_PREVIEW_LANGUAGE_SETUP,
            false
        )
        val debugPreviewPolicy = intent.getBooleanExtra(
            WelcomeActivity.EXTRA_DEBUG_PREVIEW_POLICY_DOCUMENT,
            false
        )
        val debugPreviewSupport = intent.getBooleanExtra(
            WelcomeActivity.EXTRA_DEBUG_PREVIEW_SUPPORT_WELCOME,
            false
        )
        if (BuildConfig.DEBUG && (debugPreviewLanguage || debugPreviewPolicy || debugPreviewSupport)) {
            startActivity(
                Intent(this, WelcomeActivity::class.java)
                    .putExtra(WelcomeActivity.EXTRA_DEBUG_PREVIEW_LANGUAGE_SETUP, debugPreviewLanguage)
                    .putExtra(WelcomeActivity.EXTRA_DEBUG_PREVIEW_POLICY_DOCUMENT, debugPreviewPolicy)
                    .putExtra(WelcomeActivity.EXTRA_DEBUG_PREVIEW_SUPPORT_WELCOME, debugPreviewSupport)
            )
            finish()
            return
        }

        // Install the bundled multi-language guide without delaying the first frame.
        lifecycleScope.launch(Dispatchers.IO) { builtinGuideSeeder.seed() }

        systemDarkMode = resources.configuration.isNightModeEnabled()

        // 处理外部文件打开（冷启动）
        handleNavigationIntent(intent)
        handleImportIntent(intent)

        // 启动时自动检查更新（静默执行，有变更时弹窗）
        performStartupUpdateCheck()

        val isExternalBookOpen = intent.extractImportUris().isNotEmpty()
        val splashEnabledAtLaunch = if (isExternalBookOpen) {
            false
        } else if (intent.hasExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED)) {
            intent.getBooleanExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED, false)
        } else {
            LaunchThemeController.splashEnabledSnapshot(this)
        }
        val iconStyleAtLaunch = LaunchThemeController.iconStyleSnapshot(this)

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val startupScreen by produceState<String?>(initialValue = null, dataStoreManager) {
                value = dataStoreManager.startupScreen.first()
            }
            val bookshelfLayoutMode by produceState<Int?>(initialValue = null, dataStoreManager) {
                value = dataStoreManager.bookshelfLayoutMode.first()
            }
            val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = DEFAULT_APP_ACCENT_HEX)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val cardOutlinesEnabled by dataStoreManager.cardOutlinesEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val entranceAnimationsEnabled by dataStoreManager.entranceAnimationsEnabled.collectAsState(initial = true)
            val motionPreferenceValue by dataStoreManager.motionPreference.collectAsState(initial = "standard")
            val eInkModeEnabled by dataStoreManager.eInkModeEnabled.collectAsState(initial = false)
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = if (eInkModeEnabled) {
                false
            } else when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            val liquidGlassCapability = rememberLiquidGlassCapability(eInkModeEnabled, LocalView.current)
            val effectiveAppTheme = effectiveAppTheme(appTheme, liquidGlassCapability)
            val isLiquidGlass = !eInkModeEnabled && effectiveAppTheme == "liquid_glass"
            val mainBackdrop = rememberLayerBackdrop()

            // 条款/政策更新弹窗状态
            var policyDialog by remember { mutableStateOf<PendingPolicyUpdate?>(null) }
            var showSplash by remember { mutableStateOf(splashEnabledAtLaunch) }

            LaunchedEffect(eInkModeEnabled) {
                if (eInkModeEnabled) {
                    showSplash = false
                } else {
                    if (splashEnabledAtLaunch) {
                        delay(1_100)
                    }
                    showSplash = false
                }
            }

            val mainContentAlpha by animateFloatAsState(
                targetValue = if (eInkModeEnabled || !showSplash) 1f else 0f,
                animationSpec = tween(460),
                label = "mainContentAlpha"
            )
            val mainContentScale by animateFloatAsState(
                targetValue = if (eInkModeEnabled || !showSplash) 1f else 0.985f,
                animationSpec = tween(520),
                label = "mainContentScale"
            )
            val mainContentOffset by animateDpAsState(
                targetValue = if (eInkModeEnabled || !showSplash) 0.dp else 12.dp,
                animationSpec = tween(520),
                label = "mainContentOffset"
            )

            // 监听后台检查结果
            val pending = pendingPolicyUpdate
            if (pending != null && policyDialog == null) {
                policyDialog = pending
                pendingPolicyUpdate = null
            }

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = effectiveAppTheme == "material3",
                appTheme = effectiveAppTheme,
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkModeEnabled,
                cardOutlinesEnabled = cardOutlinesEnabled,
                eInkMode = eInkModeEnabled,
                globalFontMode = globalFontMode,
                motionPreference = MotionPreference.fromStoredValue(motionPreferenceValue)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LiquidGlassDialogHost(
                        modifier = Modifier.fillMaxSize(),
                        backdrop = mainBackdrop.takeIf { isLiquidGlass }
                    ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val navCurrentEntry by navController.currentBackStackEntryAsState()
                        // 阅读页（EPUB/TXT/PDF 共用 reader/{bookId} 路由）禁止弹出全局启动弹窗：
                        // 弹窗会切换主内容 layerBackdrop 导致阅读内容闪烁，退出阅读页后补显示。
                        val onReaderRoute = navCurrentEntry?.destination?.route == Screen.Reader.route
                        val globalGlassDialogVisible = !onReaderRoute && (
                            pendingAppUpdate != null ||
                                pendingRemoteNotice != null ||
                                policyDialog != null ||
                                // 外部导入询问弹窗也是玻璃容器，同样需要在它显示期间捕获主内容，
                                // 否则玻璃表面拿到的是一份没有内容的 backdrop，只剩半透明底色。
                                pendingExternalImportUris.isNotEmpty())
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isLiquidGlass && globalGlassDialogVisible) {
                                        Modifier.layerBackdrop(mainBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                                .graphicsLayer {
                                    alpha = mainContentAlpha
                                    scaleX = mainContentScale
                                    scaleY = mainContentScale
                                    translationY = mainContentOffset.toPx()
                                }
                        ) {
                            startupScreen?.let { configuredStartupScreen ->
                                bookshelfLayoutMode?.let { configuredBookshelfLayoutMode ->
                                val startupRoute = when (configuredStartupScreen) {
                                    DataStoreManager.STARTUP_SCREEN_BOOKSHELF -> Screen.Bookshelf.route
                                    DataStoreManager.STARTUP_SCREEN_STATISTICS -> Screen.Statistics.route
                                    else -> Screen.Home.route
                                }
                                MainNavGraph(
                                    navController = navController,
                                    startDestination = startupRoute,
                                    initialBookshelfLayoutMode = configuredBookshelfLayoutMode,
                                    entranceAnimationsEnabled = entranceAnimationsEnabled &&
                                        motionPreferenceValue == "standard" &&
                                        !showSplash && !eInkModeEnabled,
                                    predictiveBackEnabled = predictiveBackEnabled && !eInkModeEnabled,
                                    requestedOpenBookId = requestedOpenBookId,
                                    requestedOpenBookDirect = requestedOpenBookDirect,
                                    requestedOpenBookshelf = requestedOpenBookshelf,
                                    requestedOpenFolderId = requestedOpenFolderId,
                                    onBeforeOpenDifferentBook = ttsController::stop,
                                    onOpenBookRequestConsumed = {
                                        requestedOpenBookId = null
                                        requestedOpenBookDirect = false
                                    },
                                    onOpenBookshelfRequestConsumed = {
                                        requestedOpenBookshelf = false
                                        requestedOpenFolderId = null
                                    }
                                )
                                }
                            }
                        }

                    // Remote startup dialog priority: app update > notice > policy.
                    // 阅读页不渲染启动弹窗，避免 layerBackdrop 切换导致内容闪烁。
                    val appUpdate = pendingAppUpdate
                    val remoteNotice = pendingRemoteNotice
                    when {
                        !onReaderRoute && appUpdate != null -> {
                            AppUpdateDialog(
                                appVersion = appUpdate.appVersion,
                                updateTitle = appUpdate.title,
                                updateMessage = appUpdate.message,
                                changelog = appUpdate.changelog,
                                force = appUpdate.force,
                                onDownload = {
                                    openRemoteUrl(appUpdate.releaseUrl)
                                    if (!appUpdate.force) pendingAppUpdate = null
                                },
                                onLater = {
                                    if (!appUpdate.force) pendingAppUpdate = null
                                },
                                onIgnoreVersion = if (appUpdate.force) null else {
                                    {
                                        lifecycleScope.launch {
                                            dataStoreManager.ignoreAppUpdate(appUpdate.latestVersionCode)
                                        }
                                        pendingAppUpdate = null
                                    }
                                }
                            )
                        }
                        !onReaderRoute && remoteNotice != null -> {
                            RemoteNoticeDialog(
                                title = remoteNotice.title,
                                message = remoteNotice.message,
                                onConfirm = {
                                    lifecycleScope.launch {
                                        dataStoreManager.acknowledgeNotice(remoteNotice.id)
                                    }
                                    pendingRemoteNotice = null
                                }
                            )
                        }
                        !onReaderRoute && policyDialog != null -> {
                            val update = policyDialog
                            if (update != null) {
                                PolicyUpdateDialog(
                                    hasTermsUpdate = update.hasTermsUpdate,
                                    termsVersion = update.termsVersion,
                                    hasPrivacyUpdate = update.hasPrivacyUpdate,
                                    privacyVersion = update.privacyVersion,
                                    onAccept = {
                                        lifecycleScope.launch {
                                            if (update.hasTermsUpdate) {
                                                dataStoreManager.saveAcceptedTermsVersion(update.termsVersion)
                                            }
                                            if (update.hasPrivacyUpdate) {
                                                dataStoreManager.saveAcceptedPrivacyVersion(update.privacyVersion)
                                            }
                                        }
                                        policyDialog = null
                                    },
                                    onDecline = { finishAffinity() },
                                    onViewTerms = {
                                        openUpdateDocument(getString(R.string.terms_of_service), "terms.html")
                                    },
                                    onViewPrivacy = {
                                        openUpdateDocument(getString(R.string.privacy_policy), "privacy.html")
                                    }
                                )
                            }
                        }
                    }

                    // 外部打开书籍：授权过文件夹时先问用户复制进应用还是移动进授权夹。
                    if (pendingExternalImportUris.isNotEmpty()) {
                        ExternalImportChoiceDialog(
                            fileCount = pendingExternalImportUris.size,
                            folders = pendingExternalImportFolders,
                            onCopyIntoApp = {
                                val uris = pendingExternalImportUris
                                pendingExternalImportUris = emptyList()
                                pendingExternalImportFolders = emptyList()
                                runExternalImport(
                                    uris = uris,
                                    mode = ExternalImportMode.COPY_INTO_APP,
                                    targetTreeUri = null
                                )
                            },
                            onMoveToFolder = { folder ->
                                val uris = pendingExternalImportUris
                                pendingExternalImportUris = emptyList()
                                pendingExternalImportFolders = emptyList()
                                runExternalImport(
                                    uris = uris,
                                    mode = ExternalImportMode.MOVE_TO_AUTHORIZED,
                                    targetTreeUri = folder.treeUri
                                )
                            },
                            onDismiss = {
                                pendingExternalImportUris = emptyList()
                                pendingExternalImportFolders = emptyList()
                            }
                        )
                    }

                        AnimatedVisibility(
                            visible = showSplash && !eInkModeEnabled,
                            exit = fadeOut(animationSpec = tween(260)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            SplashScreen(isDark = isDark, iconStyle = iconStyleAtLaunch)
                        }
                    }
                    }
                }
            }
        }
    }

    private fun Configuration.isNightModeEnabled(): Boolean {
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun openUpdateDocument(title: String, assetFile: String) {
        startActivity(
            Intent(this, WebViewActivity::class.java)
                .putExtra("title", title)
                .putExtra("file", assetFile)
        )
    }

    private fun openRemoteUrl(url: String) {
        if (url.isBlank()) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error ->
            Log.w("MainActivity", "Failed to open remote URL", error)
            Toast.makeText(this, R.string.network_error, Toast.LENGTH_LONG).show()
        }
    }


    /**
     * 启动时自动检查更新：拉取 update_config.json，
     * 对比条款/政策版本，如有更新则标记待弹窗。
     */
    private fun performStartupUpdateCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = UpdateChecker.fetchUpdateConfig() ?: return@launch
                val packageInfo = try {
                    packageManager.getPackageInfo(packageName, 0)
                } catch (_: Exception) { null }
                val currentVersion = packageInfo?.versionName ?: "1.0"
                val currentVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

                val acceptedTerms = dataStoreManager.acceptedTermsVersion.first()
                val acceptedPrivacy = dataStoreManager.acceptedPrivacyVersion.first()
                val acknowledgedNoticeIds = dataStoreManager.acknowledgedNoticeIds.first()
                val ignoredAppUpdateVersionCode = dataStoreManager.ignoredAppUpdateVersionCode.first()

                val result = UpdateChecker.evaluate(
                    config = config,
                    currentVersion = currentVersion,
                    currentVersionCode = currentVersionCode,
                    acceptedTerms = acceptedTerms,
                    acceptedPrivacy = acceptedPrivacy
                )

                val nextAppUpdate = result
                    .takeIf {
                        it.hasAppUpdate && !(
                                !it.isForceUpdate &&
                                        it.latestVersionCode > 0L &&
                                        it.latestVersionCode == ignoredAppUpdateVersionCode
                                )
                    }
                    ?.let {
                        PendingAppUpdate(
                            appVersion = it.appVersion,
                            latestVersionCode = it.latestVersionCode,
                            title = it.updateTitle,
                            message = it.updateMessage,
                            changelog = it.changelog,
                            releaseUrl = it.releaseUrl,
                            force = it.isForceUpdate
                        )
                    }
                val nextNotice = result.notice
                    ?.takeIf { it.id !in acknowledgedNoticeIds }
                    ?.let { PendingRemoteNotice(id = it.id, title = it.title, message = it.message) }
                val nextPolicy = if (result.hasTermsUpdate || result.hasPrivacyUpdate) {
                    PendingPolicyUpdate(
                        hasTermsUpdate = result.hasTermsUpdate,
                        termsVersion = result.termsVersion,
                        hasPrivacyUpdate = result.hasPrivacyUpdate,
                        privacyVersion = result.privacyVersion
                    )
                } else {
                    null
                }

                withContext(Dispatchers.Main) {
                    pendingAppUpdate = nextAppUpdate
                    pendingRemoteNotice = nextNotice
                    pendingPolicyUpdate = nextPolicy
                }
            } catch (_: Exception) {
                // Fail silently so startup is not blocked.
            }
        }
    }
}

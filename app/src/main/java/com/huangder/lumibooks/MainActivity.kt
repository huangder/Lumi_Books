package com.huangder.lumibooks

import com.huangder.lumibooks.util.diagnostics.StartupTrace
import com.huangder.lumibooks.data.local.StartupNavigationSnapshot
import com.huangder.lumibooks.ui.splash.awaitBrandedSplashExit
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
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.withFrameNanos
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.sync.LocalNetworkAccessManager
import com.huangder.lumibooks.data.sync.WebdavAutoSyncScheduler
import com.huangder.lumibooks.data.sync.WebdavSyncUpgradeStore
import com.huangder.lumibooks.domain.model.AppUiIconStyle
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.ui.navigation.MainNavGraph
import com.huangder.lumibooks.ui.navigation.Screen
import com.huangder.lumibooks.ui.home.BookImportPipeline
import com.huangder.lumibooks.ui.home.findBookAtExternalLocation
import com.huangder.lumibooks.ui.home.findMatchingAuthorizedBook
import com.huangder.lumibooks.util.word.WordEpubCache
import com.huangder.lumibooks.util.word.WordException
import com.huangder.lumibooks.util.word.userMessage
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.service.TtsMediaButtons
import com.huangder.lumibooks.service.logMediaButtonDelivery
import com.huangder.lumibooks.ui.splash.SplashScreen
import com.huangder.lumibooks.ui.components.AppUpdateDialog
import com.huangder.lumibooks.ui.components.ExternalImportChoiceDialog
import com.huangder.lumibooks.ui.components.ExternalImportFolder
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.rememberLocalNetworkPermissionGate
import com.huangder.lumibooks.ui.components.PolicyUpdateDialog
import com.huangder.lumibooks.ui.components.RemoteNoticeDialog
import com.huangder.lumibooks.domain.model.EARLY_DONOR_NOTICE_ID
import com.huangder.lumibooks.domain.model.ReceivedNotice
import com.huangder.lumibooks.ui.components.WebdavSyncUpgradeDialog
import com.huangder.lumibooks.ui.settings.DetailActivity
import com.huangder.lumibooks.ui.settings.BundledPolicy
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
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** Build types driven by the macrobenchmark and Baseline Profile generator. */
internal val BENCHMARK_BUILD_TYPES = setOf("benchmark", "nonMinifiedRelease")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_BOOK_ID = "open_book_id"
        const val EXTRA_RELOAD_READING_POSITION = "reload_reading_position"
        const val EXTRA_OPEN_DESTINATION = "open_destination"
        const val EXTRA_OPEN_FOLDER_ID = "open_folder_id"
        const val DESTINATION_BOOKSHELF = "bookshelf"
        const val DESTINATION_STATISTICS = "statistics"
        /** Opens the hidden library page; it shows its own passcode panel unless already unlocked. */
        const val DESTINATION_HIDDEN_LIBRARY = "hidden_library"
    }

    private var systemDarkMode by mutableStateOf(false)
    private var requestedOpenBookId by mutableStateOf<String?>(null)
    private var requestedReloadBookId by mutableStateOf<String?>(null)
    private var readingPositionReloadToken by mutableStateOf(0L)
    /** 外部文件打开已匹配到本地书籍时，绕过首页和书籍过渡页直达阅读器。 */
    private var requestedOpenBookDirect by mutableStateOf(false)
    private var requestedOpenBookshelf by mutableStateOf(false)
    private var requestedOpenStatistics by mutableStateOf(false)
    private var requestedOpenFolderId by mutableStateOf<String?>(null)
    private var requestedOpenHiddenLibrary by mutableStateOf(false)
    private val authorizedStorageManager = AuthorizedStorageManager()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var bookImportPipeline: BookImportPipeline

    @Inject
    lateinit var builtinGuideSeeder: BuiltinGuideSeeder

    @Inject
    lateinit var ttsController: dagger.Lazy<TtsController>

    @Inject
    lateinit var floatingSubtitleOverlayController: com.huangder.lumibooks.service.FloatingSubtitleOverlayController

    @Inject
    lateinit var authorizedFolderSnapshotStore: AuthorizedFolderSnapshotStore

    @Inject
    lateinit var localNetworkAccessManager: LocalNetworkAccessManager

    @Inject
    lateinit var webdavAutoSyncScheduler: WebdavAutoSyncScheduler

    /**
     * 当 ReaderScreen 处于前台时置为 true，
     * 确保 ActionMode 拦截只在阅读页生效，不影响其他页面。
     */
    var isInReaderScreen = false

    /** 当前阅读页可分别处理两个音量键；返回 false 时交给系统处理。 */
    var readerVolumeKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKey && readerVolumeKeyHandler?.invoke(event) == true) {
            return true
        }
        // 部分 ROM / 耳机把媒体按键投递给前台窗口而不是 MediaSession。
        // 听书进行中时在这里兜底处理；未听书时不拦截，仍交给系统的媒体按键路由。
        if (TtsMediaButtons.isSupportedKey(event.keyCode)) {
            val controller = ttsController.get()
            val result = TtsMediaButtons.handleEvent(controller, event)
            logMediaButtonDelivery("activity_window", event, result, controller)
            if (result.consumed && controller.playbackState.value != TtsPlaybackState.IDLE) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Startup dialog pending states.
    /** Pending app update dialog; non-null means show it after startup. */
    private var pendingAppUpdate by mutableStateOf<PendingAppUpdate?>(null)

    /** Pending remote notice dialog; non-null means show it after startup. */
    private var pendingRemoteNotice by mutableStateOf<UpdateChecker.NoticeConfig?>(null)
    private var pendingEarlyDonorNotice by mutableStateOf(false)

    /** Pending terms/privacy policy update dialog; non-null means show it after startup. */
    private var pendingPolicyUpdate by mutableStateOf<PendingPolicyUpdate?>(null)

    @Inject lateinit var webdavSyncUpgradeStore: WebdavSyncUpgradeStore
    private var pendingWebdavSyncUpgrade by mutableStateOf(false)

    /** Externally opened files waiting for a copy/move decision. */
    private var pendingExternalImportUris by mutableStateOf<List<Uri>>(emptyList())
    private var pendingExternalImportFolders by mutableStateOf<List<ExternalImportFolder>>(emptyList())
    private val externalImportMutex = Mutex()

    override fun onNewIntent(intent: Intent) {
        com.huangder.lumibooks.widget.WidgetDiagnostics.openIntent(intent, "on_new_intent")
        super.onNewIntent(intent)
        StartupTrace.event("main_new_intent", StartupTrace.intentFields(intent))
        setIntent(intent)
        handleNavigationIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        com.huangder.lumibooks.widget.WidgetDiagnostics.openIntent(intent, "navigation_requested")
        val requestedBookId = intent
            ?.getStringExtra(EXTRA_OPEN_BOOK_ID)
            ?.takeIf { it.isNotBlank() }
        requestedOpenBookId = requestedBookId
        if (requestedBookId != null && intent?.getBooleanExtra(EXTRA_RELOAD_READING_POSITION, false) == true) {
            requestedReloadBookId = requestedBookId
            readingPositionReloadToken++
        }
        requestedOpenBookDirect = false
        requestedOpenFolderId = intent
            ?.getStringExtra(EXTRA_OPEN_FOLDER_ID)
            ?.takeIf { it.isNotBlank() }
        requestedOpenBookshelf = requestedBookId == null &&
            (requestedOpenFolderId != null ||
                intent?.getStringExtra(EXTRA_OPEN_DESTINATION) == DESTINATION_BOOKSHELF)
        requestedOpenStatistics = requestedBookId == null && !requestedOpenBookshelf &&
            intent?.getStringExtra(EXTRA_OPEN_DESTINATION) == DESTINATION_STATISTICS
        requestedOpenHiddenLibrary = requestedBookId == null &&
            intent?.getStringExtra(EXTRA_OPEN_DESTINATION) == DESTINATION_HIDDEN_LIBRARY
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
            val folders = if (directories.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val knownBooks = bookRepository.getAllBooks().first()
                    if (uris.any { uri -> needsExternalImportChoice(uri, knownBooks) }) {
                        resolveAuthorizedFolderChoices(directories)
                    } else {
                        emptyList()
                    }
                }
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
        if (FileUtils.getBookImportName(this, uri) == null) return false
        return findDirectExternalBook(uri, authorizedStorageManager.documentKey(uri), knownBooks) == null
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
            var wordFailure: String? = null
            externalImportMutex.withLock {
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
                            if (error is CancellationException) throw error
                            failed++
                            if (wordFailure == null) wordFailure = when (error) {
                                is WordImportFailed -> error.message
                                is WordException -> error.userMessage(this@MainActivity)
                                else -> null
                            }
                            Log.e("MainActivity", "Unable to import shared book: $uri", error)
                        }
                }
            }
            withContext(Dispatchers.Main) {
                val message = when {
                    failed > 0 -> getString(R.string.import_summary_with_failures, imported, failed)
                    imported == 0 && opened > 0 -> getString(R.string.import_already_opened, opened)
                    opened > 0 -> getString(R.string.import_summary_with_opened, imported, opened)
                    else -> getString(R.string.import_summary, imported)
                }
                val detail = wordFailure
                Toast.makeText(this@MainActivity, listOfNotNull(message, detail).joinToString("\n"),
                    if (detail == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
        }
    }

    private enum class ImportOutcome { IMPORTED, OPENED, UNSUPPORTED }
    private class WordImportFailed(message: String) : java.io.IOException(message)

    /** 外部文件的保存方式：复制进应用目录，或移动进授权文件夹。 */
    private enum class ExternalImportMode { COPY_INTO_APP, MOVE_TO_AUTHORIZED }

    private fun findDirectExternalBook(uri: Uri, documentKey: String?, books: List<Book>): Book? =
        findAppManagedBook(uri, books) ?: findBookAtExternalLocation(uri, documentKey, books)

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
        val fileName = FileUtils.getBookImportName(this, uri) ?: return ImportOutcome.UNSUPPORTED
        val extension = FileUtils.getFileExtension(fileName)
        val format = BookFormat.fromExtension(extension) ?: return ImportOutcome.UNSUPPORTED

        val sourceKey = authorizedStorageManager.documentKey(uri)
        val sourceUri = uri.toString()
        val knownBooks = bookRepository.getAllBooks().first()
        val directExisting = findDirectExternalBook(uri, sourceKey, knownBooks)
        if (directExisting != null && (!format.isWord || !directExisting.isCloudOnly)) {
            return openExistingBook(directExisting.id)
        }

        if (format.isWord) {
            return if (mode == ExternalImportMode.COPY_INTO_APP) {
                externalWordOutcome(importExternalWord(uri, fileName, copy = true))
            } else {
                val treeUri = targetTreeUri?.let(Uri::parse) ?: return ImportOutcome.UNSUPPORTED
                importMovedWord(uri, fileName, treeUri)
            }
        }

        val sourceHash = runCatching { authorizedStorageManager.sha256(this, sourceUri) }.getOrNull()
        val existing = if (sourceHash != null) {
            findMatchingAuthorizedBook(
                documentKey = sourceKey,
                uri = sourceUri,
                sha256 = sourceHash,
                books = bookImportPipeline.repairMissingSourceHashes(this, knownBooks)
            )
        } else null
        if (existing != null) return openExistingBook(existing.id)

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

    private suspend fun importExternalWord(
        uri: Uri,
        name: String,
        copy: Boolean,
        parent: Uri? = null
    ): BookImportPipeline.Outcome = bookImportPipeline.import(
        context = this,
        documents = listOf(BookImportPipeline.Document(
            uri = uri,
            name = name,
            sourceDirectoryDocumentUri = parent?.toString(),
            sourceLastModified = authorizedStorageManager.queryLastModified(this, uri)
        )),
        copyIntoApp = copy,
        targetFolderId = null,
        groupByAuthorizedSource = false,
        inheritHighlightSwitches = true
    ).outcomes.single()

    private suspend fun externalWordOutcome(outcome: BookImportPipeline.Outcome): ImportOutcome = when (outcome.status) {
        BookImportPipeline.Status.IMPORTED -> ImportOutcome.IMPORTED
        BookImportPipeline.Status.DUPLICATE -> openExistingBook(checkNotNull(outcome.bookId))
        BookImportPipeline.Status.UNSUPPORTED -> ImportOutcome.UNSUPPORTED
        BookImportPipeline.Status.FAILED -> throw WordImportFailed(outcome.failureMessage ?: getString(R.string.word_error_io))
    }

    private suspend fun openExistingBook(bookId: String): ImportOutcome = withContext(Dispatchers.Main) {
        requestedOpenBookId = bookId
        requestedOpenBookDirect = true
        ImportOutcome.OPENED
    }

    private suspend fun importMovedWord(uri: Uri, name: String, treeUri: Uri): ImportOutcome {
        val hash = runInterruptible { WordEpubCache.sourceHash(this, uri.toString()) }
        bookImportPipeline.findExisting(this, null, authorizedStorageManager.documentKey(uri), hash)
            ?.takeUnless { it.isCloudOnly }?.let { return openExistingBook(it.id) }
        val root = authorizedStorageManager.treeRootUri(treeUri)
        val move = authorizedStorageManager.copyThenDelete(this, uri.toString(), root, name, deleteSource = false)
        val destination = move.destinationUri
        var keepDestination = false
        try {
            if (move.sha256 != hash) throw WordImportFailed(getString(R.string.word_error_io))
            val outcome = importExternalWord(destination, name, copy = false, parent = root)
            if (outcome.status == BookImportPipeline.Status.IMPORTED) {
                keepDestination = true
                runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                rememberAuthorizedCopy(treeUri, root, destination, name,
                    authorizedStorageManager.documentKey(destination), authorizedStorageManager.queryLastModified(this, destination))
            }
            return externalWordOutcome(outcome)
        } finally {
            if (!keepDestination) runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
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
        val format = checkNotNull(BookFormat.fromExtension(extension))
        val bookId = FileUtils.generateBookId()
        if (format == BookFormat.MARKDOWN) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val file = if (format == BookFormat.MARKDOWN) {
            com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore.create(this, bookId, uri.toString())
        } else FileUtils.copyFileToInternal(this, uri, fileName) ?: return ImportOutcome.UNSUPPORTED
        val coverPath = extractBookCover(file.absolutePath, format)
        // 导入时从文件解析真实标题和作者，而不是写死"未知作者"
        val (parsedTitle, parsedAuthor) = resolveBookMetadata(file.absolutePath, fileName, format)

        val book = Book(
            id = bookId,
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
             sourceSha256 = if (format == BookFormat.MARKDOWN) {
                 com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore.manifest(file)
                     ?.optString("sourceSha256")?.takeIf { it.isNotBlank() }
             } else sourceHash ?: runCatching {
                 authorizedStorageManager.sha256(this, file.absolutePath)
             }.getOrNull(),
             sourceDisplayName = fileName,
             sourceLastModified = authorizedStorageManager.queryLastModified(this, uri)
         )
        try {
            bookRepository.insertExternalBook(book)
        } catch (error: Throwable) {
            FileUtils.deleteAppManagedBookFile(this, file.absolutePath)
            FileUtils.deleteAppOwnedFile(this, coverPath)
            throw error
        }
        Log.d("MainActivity", "Imported book from intent: ${book.title}")
        return ImportOutcome.IMPORTED
    }

    /**
     * 把外部文件搬进授权文件夹：先复制并入库，再尽力删除原文件。
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
        val format = checkNotNull(BookFormat.fromExtension(extension))
        val bookId = FileUtils.generateBookId()
        val documentKey = authorizedStorageManager.documentKey(move.destinationUri)
        val lastModified = authorizedStorageManager.queryLastModified(this, move.destinationUri)
        var markdownSnapshot: File? = null
        var coverPath: String? = null
        val book = try {
            if (format == BookFormat.MARKDOWN) {
                markdownSnapshot = com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore
                    .create(this, bookId, location, treeRootUri.toString())
            }
            val readingLocation = markdownSnapshot?.absolutePath ?: location
            coverPath = extractBookCover(readingLocation, format)
            val (parsedTitle, parsedAuthor) = resolveBookMetadata(readingLocation, fileName, format)
            val now = System.currentTimeMillis()
            Book(
                id = bookId,
                title = parsedTitle,
                author = parsedAuthor,
                filePath = readingLocation,
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
            ).also { bookRepository.insertExternalBook(it) }
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, move.destinationUri) }
            markdownSnapshot?.let { FileUtils.deleteAppManagedBookFile(this, it.absolutePath) }
            FileUtils.deleteAppOwnedFile(this, coverPath)
            throw error
        }
        val sourceRemoved = runCatching {
            DocumentsContract.deleteDocument(contentResolver, uri)
        }.getOrDefault(false)
        if (!sourceRemoved) {
            Log.w("MainActivity", "Book copied into an authorized folder but the source stayed: $uri")
        }
        rememberAuthorizedCopy(treeUri, treeRootUri, move.destinationUri, fileName, documentKey, lastModified)
        Log.d("MainActivity", "Moved book from intent into authorized folder: ${book.title}")
        return ImportOutcome.IMPORTED
    }

    private suspend fun rememberAuthorizedCopy(
        treeUri: Uri, root: Uri, destination: Uri, fileName: String, documentKey: String?, lastModified: Long
    ) {
        val location = destination.toString()
        // 让“选择文件夹书籍”页面立刻能看到这本书，无需等待下次扫描。
        runCatching {
            authorizedFolderSnapshotStore.addDocument(
                treeUri = treeUri,
                document = AuthorizedFolderDocumentSnapshot(
                    documentUri = location,
                    documentKey = documentKey,
                    parentDocumentUri = root.toString(),
                    displayName = fileName,
                    relativeDirectory = null,
                    size = BookFileAccess.size(this, location),
                    lastModified = lastModified
                )
            )
        }
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
        if (format == BookFormat.MARKDOWN) {
            val metadata = com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore.manifest(File(location))
            return (metadata?.optString("title")?.takeIf { it.isNotBlank() } ?: fallback.first) to
                (metadata?.optString("author")?.takeIf { it.isNotBlank() } ?: fallback.second)
        }
        if (!format.supportsEpubFeatures) return fallback
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


    /**
     * 不拦截 ActionMode：选区检测由 ReadView 的 SpanWatcher 处理，
     * 系统浮动工具栏通过 menu.clear() 清空菜单项（显示为空气泡）。
     * 不调用 mode.finish()，避免破坏选区手柄状态。
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        com.huangder.lumibooks.widget.WidgetDiagnostics.openIntent(intent, "on_create")
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
        if (BuildConfig.STARTUP_TRACE_ENABLED) StartupTrace.event("main_splash_decision", mapOf("enabled" to splashEnabledAtLaunch, "source" to if (isExternalBookOpen) "external_book" else if (intent.hasExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED)) "intent" else "cache", "snapshot" to LaunchThemeController.splashEnabledSnapshot(this)))
        val iconStyleAtLaunch = LaunchThemeController.iconStyleSnapshot(this)

        val launchTheme = LaunchThemeController.themeSnapshot(this)
        val startupNavigationAtLaunch: StartupNavigationSnapshot? = LaunchThemeController.startupNavigationSnapshot(this)
        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = launchTheme.appTheme)
            val appUiIconStyle by dataStoreManager.appUiIconStyle.collectAsState(initial = AppUiIconStyle.DEFAULT.storedValue)
            // The mirrored value decides this launch; the Application collector refreshes
            // it from DataStore, so a stale mirror is corrected for the next launch.
            val startupNavigation by produceState(startupNavigationAtLaunch, dataStoreManager) {
                if (value == null) value = dataStoreManager.startupNavigationSnapshot.first()
            }
            val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = launchTheme.appAccentColor)
            val appFontSize by dataStoreManager.appFontSize.collectAsState(initial = launchTheme.appFontSize)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = launchTheme.liquidGlassTransparency)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
            val cardOutlinesEnabled by dataStoreManager.cardOutlinesEnabled.collectAsState(initial = launchTheme.cardOutlinesEnabled)
            val coverReadingProgressEnabled by dataStoreManager.coverReadingProgressEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
            val entranceAnimationsEnabled by dataStoreManager.entranceAnimationsEnabled.collectAsState(initial = true)
            val motionPreferenceValue by dataStoreManager.motionPreference.collectAsState(initial = launchTheme.motionPreference)
            val bookOpenTransition by dataStoreManager.bookOpenTransition.collectAsState(
                initial = com.huangder.lumibooks.domain.model.BookOpenTransition.HERO.storedValue
            )
            val eInkModeEnabled by dataStoreManager.eInkModeEnabled.collectAsState(initial = launchTheme.eInkModeEnabled)
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = launchTheme.predictiveBackEnabled)
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
            var startupContentReady by remember { mutableStateOf(false) }
            val splashVisibility = remember { MutableTransitionState(splashEnabledAtLaunch) }
            SideEffect { splashVisibility.targetState = showSplash && !eInkModeEnabled }

            if (BuildConfig.STARTUP_TRACE_ENABLED) {
                LaunchedEffect(showSplash, eInkModeEnabled) {
                    StartupTrace.event("splash_visibility_decision", mapOf("showSplash" to showSplash, "eInkMode" to eInkModeEnabled, "visible" to (showSplash && !eInkModeEnabled)))
                }
                LaunchedEffect(startupNavigation, startupContentReady) {
                    StartupTrace.event("main_content_readiness", mapOf("startupScreen" to startupNavigation?.screen, "bookshelfLayoutReady" to (startupNavigation != null), "contentPlaced" to startupContentReady))
                }
            }
            LaunchedEffect(eInkModeEnabled) {
                if (showSplash && !eInkModeEnabled) {
                    awaitBrandedSplashExit(snapshotFlow { startupContentReady })
                }
                showSplash = false
            }

            val mainContentAlpha by animateFloatAsState(
                targetValue = if (eInkModeEnabled || !showSplash) 1f else 0f,
                animationSpec = tween(260),
                label = "mainContentAlpha"
            )
            val mainContentOpaque by remember { derivedStateOf { mainContentAlpha == 1f } }
            val splashGone = eInkModeEnabled || (!showSplash && splashVisibility.isIdle && !splashVisibility.currentState)
            val startupInteractive = startupContentReady && splashGone && mainContentOpaque
            if (!isExternalBookOpen) {
                ReportDrawnWhen { startupInteractive }
                LaunchedEffect(startupInteractive) {
                    if (startupInteractive) {
                        withFrameNanos { }
                        com.huangder.lumibooks.util.performance.ReaderClassWarmup.start(classLoader)
                    }
                }
            }
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

            val menuAnimationStyleValue by dataStoreManager.menuAnimationStyle.collectAsState(initial = launchTheme.menuAnimationStyle)
            val liquidGlassStyle by dataStoreManager.liquidGlassStyle
                .collectAsState(initial = launchTheme.liquidGlassStyle)
            val liquidGlassSamplingQuality by dataStoreManager.liquidGlassSamplingQuality
                .collectAsState(initial = launchTheme.liquidGlassSamplingQuality)
            val reduceTransparencyEnabled by dataStoreManager.reduceTransparencyEnabled
                .collectAsState(initial = launchTheme.reduceTransparencyEnabled)
            EBookReaderTheme(
                liquidGlassStyle = liquidGlassStyle,
                liquidGlassSamplingQuality = liquidGlassSamplingQuality,
                reduceTransparencyEnabled = reduceTransparencyEnabled,
                menuAnimationStyle = com.huangder.lumibooks.domain.model.MenuAnimationStyle.fromStoredValue(menuAnimationStyleValue),
                darkTheme = isDark,
                dynamicColor = effectiveAppTheme == "material3",
                appTheme = appTheme,
                appUiIconStyle = AppUiIconStyle.fromStoredValue(appUiIconStyle),
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkModeEnabled,
                cardOutlinesEnabled = cardOutlinesEnabled,
                coverReadingProgressEnabled = coverReadingProgressEnabled,
                eInkMode = eInkModeEnabled,
                appFontSize = appFontSize,
                globalFontMode = globalFontMode,
                motionPreference = MotionPreference.fromStoredValue(motionPreferenceValue)
            ) {
                val requestLocalNetworkPermission = rememberLocalNetworkPermissionGate(
                    accessManager = localNetworkAccessManager,
                    onPermissionGranted = webdavAutoSyncScheduler::onLocalNetworkPermissionGranted
                )
                LaunchedEffect(Unit) {
                    val config = dataStoreManager.webdavConfig.first()
                    pendingWebdavSyncUpgrade = webdavSyncUpgradeStore.shouldPrompt(config)
                    if (config.enabled && config.serverUrl.isNotBlank() && !pendingWebdavSyncUpgrade) {
                        requestLocalNetworkPermission(
                            config.serverUrl,
                            {},
                            {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.webdav_error_local_network_permission,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }
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
                        val navVisibleEntries by navController.visibleEntries.collectAsState()
                        // 阅读页（EPUB/TXT/PDF 共用 reader/{bookId} 路由）禁止弹出全局启动弹窗：
                        // 弹窗会切换主内容 layerBackdrop 导致阅读内容闪烁，退出阅读页后补显示。
                        val onReaderRoute = !com.huangder.lumibooks.ui.navigation.navigationBackdropCaptureAllowed(
                            navCurrentEntry?.destination?.route,
                            navVisibleEntries.map { it.destination.route }
                        )
                        LaunchedEffect(onReaderRoute) {
                            floatingSubtitleOverlayController.setReaderVisible(onReaderRoute)
                        }
                        var inactiveTabsPrecomposed by remember { mutableStateOf(false) }
                        val canPrecomposeTabs = startupInteractive && !onReaderRoute && requestedOpenBookId == null
                        LaunchedEffect(canPrecomposeTabs) {
                            if (canPrecomposeTabs && !inactiveTabsPrecomposed) {
                                // Let the startup frame reach the display before preparing the other tabs.
                                kotlinx.coroutines.delay(350)
                                inactiveTabsPrecomposed = true
                            }
                        }
                        val globalGlassDialogVisible = !onReaderRoute && (
                            pendingAppUpdate != null ||
                                pendingEarlyDonorNotice || pendingRemoteNotice != null ||
                                policyDialog != null ||
                                pendingWebdavSyncUpgrade ||
                                // 外部导入询问弹窗也是玻璃容器，同样需要在它显示期间捕获主内容，
                                // 否则玻璃表面拿到的是一份没有内容的 backdrop，只剩半透明底色。
                                pendingExternalImportUris.isNotEmpty())
                        com.huangder.lumibooks.ui.layout.AdaptiveWindowLayout(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (BuildConfig.BUILD_TYPE in BENCHMARK_BUILD_TYPES && startupInteractive) {
                                    Modifier.semantics { testTagsAsResourceId = true }.testTag("startup_content_interactive")
                                } else Modifier)
                                .then(com.huangder.lumibooks.util.diagnostics.startupTraceModifier(
                                    if (startupNavigation != null) "main_content" else "main_loading"
                                ))
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
                            startupNavigation?.let { navigation ->
                                val startupRoute = when (navigation.screen) {
                                    DataStoreManager.STARTUP_SCREEN_BOOKSHELF -> Screen.Bookshelf.route
                                    DataStoreManager.STARTUP_SCREEN_STATISTICS -> Screen.Statistics.route
                                    else -> Screen.Home.route
                                }
                                MainNavGraph(
                                    navController = navController,
                                    startDestination = startupRoute,
                                    initialBookshelfLayoutMode = navigation.bookshelfLayoutMode,
                                    precomposeInactiveTabs = inactiveTabsPrecomposed,
                                    onStartupContentPlaced = if (startupContentReady) null else {
                                        { startupContentReady = true }
                                    },
                                    entranceAnimationsEnabled = entranceAnimationsEnabled &&
                                        motionPreferenceValue != "reduced" &&
                                        !showSplash && !eInkModeEnabled,
                                    bookOpenUsesHeroTransition = bookOpenTransition ==
                                        com.huangder.lumibooks.domain.model.BookOpenTransition.HERO.storedValue,
                                    predictiveBackEnabled = predictiveBackEnabled && !eInkModeEnabled,
                                    requestedOpenBookId = requestedOpenBookId,
                                    requestedReloadBookId = requestedReloadBookId,
                                    readingPositionReloadToken = readingPositionReloadToken,
                                    requestedOpenBookDirect = requestedOpenBookDirect,
                                    requestedOpenBookshelf = requestedOpenBookshelf,
                                    requestedOpenStatistics = requestedOpenStatistics,
                                    onOpenStatisticsRequestConsumed = { requestedOpenStatistics = false },
                                    requestedOpenFolderId = requestedOpenFolderId,
                                    requestedOpenHiddenLibrary = requestedOpenHiddenLibrary,
                                    onOpenHiddenLibraryRequestConsumed = { requestedOpenHiddenLibrary = false },
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

                    // Startup dialog priority: app update > notice > policy > sync upgrade.
                    // 阅读页不渲染启动弹窗，避免 layerBackdrop 切换导致内容闪烁。
                    val appUpdate = pendingAppUpdate
                    val earlyDonorNotice = pendingEarlyDonorNotice
                    val remoteNotice = pendingRemoteNotice
                    when {
                        !onReaderRoute && appUpdate != null -> {
                            AppUpdateDialog(
                                appVersion = appUpdate.appVersion,
                                downloadUrl = appUpdate.releaseUrl,
                                updateTitle = appUpdate.title,
                                updateMessage = appUpdate.message,
                                changelog = appUpdate.changelog,
                                force = appUpdate.force,
                                onDownloadStarted = {
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
                        !onReaderRoute && earlyDonorNotice -> {
                            RemoteNoticeDialog(
                                title = stringResource(R.string.early_donor_notice_title),
                                message = stringResource(R.string.early_donor_notice_message),
                                imageResId = R.drawable.early_donor_claim_code,
                                onConfirm = { acknowledgeStartupNotice(EARLY_DONOR_NOTICE_ID) }
                            )
                        }
                        !onReaderRoute && remoteNotice != null -> {
                            RemoteNoticeDialog(
                                title = remoteNotice.title,
                                message = remoteNotice.message,
                                imageUrl = remoteNotice.imageUrl,
                                onConfirm = { acknowledgeStartupNotice(remoteNotice.id) }
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
                                            dataStoreManager.acceptPolicyVersions(
                                                terms = update.termsVersion.takeIf { update.hasTermsUpdate },
                                                privacy = update.privacyVersion.takeIf { update.hasPrivacyUpdate }
                                            )
                                            policyDialog = null
                                        }
                                    },
                                    onDecline = { finishAffinity() },
                                    onViewTerms = { BundledPolicy.TERMS.open(this@MainActivity, update.termsVersion) },
                                    onViewPrivacy = { BundledPolicy.PRIVACY.open(this@MainActivity, update.privacyVersion) }
                                )
                            }
                        }
                        !onReaderRoute && !showSplash && pendingWebdavSyncUpgrade &&
                            pendingExternalImportUris.isEmpty() -> {
                            WebdavSyncUpgradeDialog(
                                onOpenSync = {
                                    lifecycleScope.launch { webdavSyncUpgradeStore.acknowledge() }
                                    pendingWebdavSyncUpgrade = false
                                    startActivity(DetailActivity.createIntent(this@MainActivity, "webdav"))
                                },
                                onDismiss = {
                                    lifecycleScope.launch { webdavSyncUpgradeStore.acknowledge() }
                                    pendingWebdavSyncUpgrade = false
                                }
                            )
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

                        if (!eInkModeEnabled) {
                            AnimatedVisibility(
                                visibleState = splashVisibility,
                                exit = fadeOut(animationSpec = tween(260)),
                                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            ) {
                                SplashScreen(isDark = isDark, iconStyle = iconStyleAtLaunch)
                            }
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

    private fun acknowledgeStartupNotice(id: String) {
        if (id == EARLY_DONOR_NOTICE_ID) {
            pendingEarlyDonorNotice = false
        } else {
            pendingRemoteNotice = null
        }
        lifecycleScope.launch { dataStoreManager.acknowledgeNotice(id) }
    }

    private suspend fun archiveStartupNotice(
        id: String,
        title: String,
        message: String,
        imageUrl: String? = null
    ) {
        try {
            dataStoreManager.recordReceivedNotice(
                ReceivedNotice(id, title, message, System.currentTimeMillis(), imageUrl)
            )
        } catch (error: IOException) {
            Log.w("MainActivity", "Failed to save startup notice history", error)
        }
    }

    /** Load the bundled notice before starting the remote configuration request. */
    private fun performStartupUpdateCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val terms = BundledPolicy.TERMS.version
                val privacy = BundledPolicy.PRIVACY.version
                val needsTerms = dataStoreManager.acceptedTermsVersion.first() < terms
                val needsPrivacy = dataStoreManager.acceptedPrivacyVersion.first() < privacy
                if (needsTerms || needsPrivacy) {
                    withContext(Dispatchers.Main) {
                        pendingPolicyUpdate = PendingPolicyUpdate(needsTerms, terms, needsPrivacy, privacy)
                    }
                    dataStoreManager.hasAcceptedBundledPolicies.first { it }
                }
                val acknowledgedNoticeIds = dataStoreManager.acknowledgedNoticeIds.first()
                // The bundled announcement can be recovered even if it was confirmed before history existed.
                archiveStartupNotice(
                    EARLY_DONOR_NOTICE_ID,
                    getString(R.string.early_donor_notice_title),
                    getString(R.string.early_donor_notice_message)
                )
                withContext(Dispatchers.Main) {
                    pendingEarlyDonorNotice = EARLY_DONOR_NOTICE_ID !in acknowledgedNoticeIds
                }

                val config = UpdateChecker.fetchUpdateConfig() ?: return@launch
                val packageInfo = try {
                    packageManager.getPackageInfo(packageName, 0)
                } catch (_: Exception) { null }
                val currentVersion = packageInfo?.versionName ?: "1.0"
                val currentVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

                val acceptedTerms = dataStoreManager.acceptedTermsVersion.first()
                val acceptedPrivacy = dataStoreManager.acceptedPrivacyVersion.first()
                val ignoredAppUpdateVersionCode = dataStoreManager.ignoredAppUpdateVersionCode.first()

                val result = UpdateChecker.evaluate(
                    config = config,
                    currentVersion = currentVersion,
                    currentVersionCode = currentVersionCode,
                    acceptedTerms = acceptedTerms,
                    acceptedPrivacy = acceptedPrivacy
                )

                val remoteNotice = result.notice
                remoteNotice?.let { archiveStartupNotice(it.id, it.title, it.message, it.imageUrl) }

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
                val nextNotice = remoteNotice
                    ?.takeIf { it.id != EARLY_DONOR_NOTICE_ID && it.id !in acknowledgedNoticeIds }
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

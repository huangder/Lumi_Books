@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.huangder.lumibooks.ui.navigation

import com.huangder.lumibooks.ui.theme.LumiBackgroundHost
import com.huangder.lumibooks.ui.theme.LumiBackgroundScene
import com.huangder.lumibooks.ui.theme.LocalAppThemeVariant
import androidx.compose.runtime.saveable.rememberSaveable
import com.huangder.lumibooks.ui.theme.AppColors

import android.net.Uri
import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.bookshelf.BookshelfScreen
import com.huangder.lumibooks.ui.components.BookTransitionOverlay
import com.huangder.lumibooks.ui.components.FloatingTabBar
import com.huangder.lumibooks.ui.components.Material3BottomNavigationBar
import com.huangder.lumibooks.ui.components.LiquidGlassImportButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.ImmersiveMode
import com.huangder.lumibooks.ui.components.MainSystemBarStyle
import com.huangder.lumibooks.ui.components.ConfigurableNavigationBack
import com.huangder.lumibooks.ui.components.CloudBookDownloadDialog
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.animation.coverFlowEntranceItem
import com.huangder.lumibooks.ui.animation.LocalCoverFlowEntrance
import com.huangder.lumibooks.ui.home.HomeScreen
import com.huangder.lumibooks.ui.home.ImportBooksActionSheet
import com.huangder.lumibooks.ui.home.ImportBooksConfirmationSheet
import com.huangder.lumibooks.ui.home.AuthorizedFolderBooksActivity
import com.huangder.lumibooks.ui.home.toSelectedImportBook
import com.huangder.lumibooks.ui.home.ImportDestinationSheet
import com.huangder.lumibooks.ui.home.SelectedImportBook
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.home.ReadingGoalSheet
import com.huangder.lumibooks.ui.animation.PageEntranceTracker
import com.huangder.lumibooks.ui.animation.PAGE_ENTRANCE_PLAYBACK_MILLIS
import com.huangder.lumibooks.ui.animation.BookHeroWindowOverlay
import com.huangder.lumibooks.ui.animation.BookReaderAnchorScope
import com.huangder.lumibooks.ui.animation.BookReaderLibraryLayer
import com.huangder.lumibooks.ui.animation.LocalBookReaderAnchorScope
import com.huangder.lumibooks.ui.animation.bookReaderLibraryLayer
import com.huangder.lumibooks.ui.animation.bookReaderWindowClip
import com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import com.huangder.lumibooks.ui.animation.rememberBookReaderTransitionState
import com.huangder.lumibooks.ui.reader.EpubComicSwitchDialog
import com.huangder.lumibooks.ui.reader.RasterReaderScreen
import com.huangder.lumibooks.ui.reader.LocalReaderOpeningComplete
import androidx.compose.runtime.rememberUpdatedState
import com.huangder.lumibooks.ui.reader.ReaderScreen
import com.huangder.lumibooks.ui.reader.ReaderViewModel
import com.huangder.lumibooks.ui.statistics.StatisticsScreen
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalAppAccentHex
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalGlobalFontMode
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassHdrHighlightEnabled
import com.huangder.lumibooks.ui.theme.LocalMotionPreference
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme
import com.huangder.lumibooks.ui.theme.LocalReaderColors
import com.huangder.lumibooks.ui.theme.ReaderColors
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.performance.ReaderOpenPerformance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 根据书籍格式路由：PDF → 竖向滚动，EPUB/TXT → 横向翻页
 */
@Composable
private fun ReaderRouter(
    bookId: String,
    onNavigateBack: () -> Unit,
    onFirstContentDrawn: () -> Unit,
    onInteractive: () -> Unit,
    openingComplete: Boolean,
    readerActive: Boolean,
    onOpenBook: (String) -> Unit
) {
    val viewModel: ReaderViewModel = hiltViewModel()
    var firstContentDrawn by remember(bookId) { mutableStateOf(false) }
    var backgroundWorkReady by remember(bookId) { mutableStateOf(false) }
    val activeReader = rememberUpdatedState(readerActive)
    val latestFirstContentCallback = rememberUpdatedState(onFirstContentDrawn)
    val reportFirstContent = {
        if (activeReader.value && !firstContentDrawn) {
            firstContentDrawn = true
            latestFirstContentCallback.value()
        }
    }
    LaunchedEffect(firstContentDrawn, openingComplete, readerActive) {
        backgroundWorkReady = false
        if (readerActive) {
            viewModel.resumeOpenDeferredWork()
            if (firstContentDrawn && openingComplete) {
                onInteractive()
                // Let the last animation frame and navigation disposal finish
                // before adjacent-page work invalidates the reader again.
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                backgroundWorkReady = true
                viewModel.onOpenTransitionComplete()
            }
        } else {
            viewModel.cancelOpenDeferredWork()
        }
    }
    val documentState by viewModel.documentState.collectAsState()
    if (!shouldMountReaderContent(documentState)) {
        // The ViewModel loads independently. Avoid constructing the text engine
        // with default settings before the actual book/renderer is known.
        ReaderOpeningPlaceholder(onNavigateBack)
        return
    }
    // PDF and CBZ share the raster page reader; every other format uses the text engines.
    val isRasterPageFormat = documentState.book?.format?.isRasterPageFormat == true || documentState.epubComicReader
    val isAppDarkTheme = LocalIsDarkTheme.current
    val appTheme = LocalAppThemeVariant.current
    val appAccentColor = LocalAppAccentHex.current
    val liquidGlassTransparency = LocalLiquidGlassTransparency.current
    val liquidGlassHdrHighlightEnabled = LocalLiquidGlassHdrHighlightEnabled.current
    val useMaterial3Theme = LocalUseMaterial3Theme.current
    val eInkMode = LocalEInkMode.current
    val globalFontMode = LocalGlobalFontMode.current
    val motionPreference = LocalMotionPreference.current
    val comicDialogVisible by remember(viewModel) {
        viewModel.uiState.map { it.epubComicPreparing || it.epubComicConfirmation || it.epubComicError != null }
            .distinctUntilChanged()
    }.collectAsState(initial = false)
    // Navigation deliberately stops capturing while reading. Capture the actual reader
    // only while this dialog is visible, including the original-layout WebView.
    val comicDialogBackdrop = rememberLayerBackdrop()
    val activeComicDialogBackdrop = comicDialogBackdrop.takeIf {
        comicDialogVisible && LocalAppTheme.current == "liquid_glass" && !eInkMode
    }

    // 正文颜色由阅读主题控制，弹层和应用级控件继承全局主题。
    EBookReaderTheme(
        darkTheme = isAppDarkTheme,
        dynamicColor = useMaterial3Theme,
        appTheme = appTheme,
        appAccentColor = appAccentColor,
        liquidGlassTransparency = liquidGlassTransparency,
        liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkMode,
        eInkMode = eInkMode,
        globalFontMode = globalFontMode,
        motionPreference = motionPreference
    ) {
        CompositionLocalProvider(
            LocalReaderColors provides ReaderColors.Light,
            LocalReaderOpeningComplete provides backgroundWorkReady
        ) {
            Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(
                activeComicDialogBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
            )) {
            if (isRasterPageFormat) {
                RasterReaderScreen(
                    bookId = bookId,
                    onNavigateBack = onNavigateBack,
                    onOpenBook = onOpenBook,
                    onFirstContentDrawn = reportFirstContent,
                    readerActive = readerActive,
                    viewModel = viewModel
                )
            } else {
                ReaderScreen(
                    bookId = bookId,
                    onNavigateBack = onNavigateBack,
                    onFirstContentDrawn = reportFirstContent,
                    onInteractive = {},
                    readerActive = readerActive,
                    viewModel = viewModel
                )
            }
            }
            EpubComicSwitchDialog(viewModel, activeComicDialogBackdrop)
            }
        }
    }
}

@Composable
private fun rememberPageEntrancePlayback(
    pageKey: String,
    entryKey: String,
    enabled: Boolean,
    tracker: PageEntranceTracker
): Boolean {
    var play by remember(entryKey, enabled) {
        mutableStateOf(
            enabled && tracker.shouldPlay(
                pageKey = pageKey,
                entryKey = entryKey,
                nowMillis = SystemClock.elapsedRealtime()
            )
        )
    }
    LaunchedEffect(play) {
        if (play) {
            delay(PAGE_ENTRANCE_PLAYBACK_MILLIS)
            play = false
        }
    }
    return play
}

@Composable
fun MainNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    initialBookshelfLayoutMode: Int? = null,
    entranceAnimationsEnabled: Boolean = true,
    bookOpenUsesHeroTransition: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    requestedOpenBookId: String? = null,
    requestedOpenBookDirect: Boolean = false,
    requestedOpenBookshelf: Boolean = false,
    requestedOpenFolderId: String? = null,
    onBeforeOpenDifferentBook: () -> Unit = {},
    onOpenBookRequestConsumed: () -> Unit = {},
    onOpenBookshelfRequestConsumed: () -> Unit = {}
) {
    val mainStartDestination = when (startDestination) {
        Screen.Bookshelf.route -> Screen.Bookshelf.route
        Screen.Statistics.route -> Screen.Statistics.route
        else -> Screen.Home.route
    }
    var selectedTab by remember(mainStartDestination) {
        mutableIntStateOf(
            when (mainStartDestination) {
                Screen.Bookshelf.route -> 1
                Screen.Statistics.route -> 2
                else -> 0
            }
        )
    }
    val bookReaderTransition = rememberBookReaderTransitionState()
    val coverFlowEntrance = com.huangder.lumibooks.ui.animation.rememberCoverFlowEntranceState()
    val readerNavigationScope = rememberCoroutineScope()
    var showTransition by remember { mutableStateOf(false) }
    var transitionCover by remember { mutableStateOf<String?>(null) }
    var transitionTitle by remember { mutableStateOf("") }
    var transitionBookId by remember { mutableStateOf<String?>(null) }
    var readerReady by remember { mutableStateOf(false) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var tabBarVisible by remember { mutableStateOf(true) }
    var bookshelfContextMenuVisible by remember { mutableStateOf(false) }
    var useMainReturnTabBarTransition by remember { mutableStateOf(false) }
    var previousRoute by remember { mutableStateOf<String?>(null) }
    var bookshelfOverlayProgress by remember { mutableFloatStateOf(0f) }
    var homeGoalSheetVisible by remember { mutableStateOf(false) }
    var showImportActions by remember { mutableStateOf(false) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    var showImportDestination by remember { mutableStateOf(false) }
    var selectedImportBooks by remember { mutableStateOf(emptyList<SelectedImportBook>()) }
    var selectedImportBookUris by remember { mutableStateOf(emptySet<String>()) }
    var importCopiesIntoApp by remember { mutableStateOf(true) }
    var importRequestFolderId by remember { mutableStateOf<String?>(null) }
    var isPreparingImport by remember { mutableStateOf(false) }
    var importPreparationGeneration by remember { mutableIntStateOf(0) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var pendingCloudBook by remember { mutableStateOf<Book?>(null) }
    var autoOpenDownloadedBookId by remember { mutableStateOf<String?>(null) }
    var cloudOpenSource by remember {
        mutableStateOf<Triple<String, Rect?, com.huangder.lumibooks.ui.animation.BookReaderPresentation?>?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val entranceTracker = remember { PageEntranceTracker() }
    val hazeState = remember { HazeState() }
    val eInkMode = LocalEInkMode.current
    val useMaterial3Navigation = LocalUseMaterial3Theme.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    val isLumiChan = LocalAppThemeVariant.current == "lumi_chan"
    val glassBackdropBackground = AppColors.WindowBg
    val liquidGlassBackdrop = rememberLayerBackdrop(onDraw = {
        drawRect(glassBackdropBackground)
        drawContent()
    })
    val homeViewModel: HomeViewModel = hiltViewModel()
    val context = LocalContext.current
    val authorizedStorageManager = remember { com.huangder.lumibooks.util.AuthorizedStorageManager() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (showImportActions) {
            val candidates = uris.mapNotNull { uri ->
                val name = FileUtils.getFileNameFromUri(context, uri) ?: return@mapNotNull null
                if (FileUtils.getFileExtension(name) !in setOf("epub", "pdf", "txt", "mobi", "cbz")) {
                    return@mapNotNull null
                }
                SelectedImportBook(
                    uri = uri,
                    name = name,
                    sourceDocumentKey = authorizedStorageManager.documentKey(uri),
                    sourceLastModified = authorizedStorageManager.queryLastModified(context, uri),
                    sourceSize = com.huangder.lumibooks.util.BookFileAccess.size(context, uri.toString())
                )
            }.distinctBy { it.uri.toString() }
            if (candidates.isNotEmpty()) {
                isPreparingImport = false
                showImportActions = false
                homeViewModel.importBooks(
                    context = context,
                    uris = candidates.map { it.uri },
                    targetFolderId = importRequestFolderId
                )
                importRequestFolderId = null
            }
        }
    }
    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null && showImportActions) {
            val requestGeneration = importPreparationGeneration + 1
            importPreparationGeneration = requestGeneration
            isPreparingImport = true
            homeViewModel.authorizeBookDirectory(context, uri) { candidates ->
                if (showImportActions && importPreparationGeneration == requestGeneration) {
                    isPreparingImport = false
                    val selected = candidates.map { candidate ->
                        SelectedImportBook(
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
                    }
                    if (selected.isNotEmpty()) {
                        selectedImportBooks = selected
                        selectedImportBookUris = emptySet()
                        importCopiesIntoApp = false
                        showImportActions = false
                        showImportConfirmation = true
                    }
                }
            }
        } else {
            isPreparingImport = false
        }
    }
    val homeUiState by homeViewModel.uiState.collectAsState()
    val folderBooksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val selectedUris = AuthorizedFolderBooksActivity.selectedUrisFrom(result.data)
        if (result.resultCode == Activity.RESULT_OK && selectedUris.isNotEmpty()) {
            homeViewModel.resolveAuthorizedFolderFiles(selectedUris) { files ->
                if (files.isNotEmpty()) {
                    selectedImportBooks = files.map { it.toSelectedImportBook() }
                    selectedImportBookUris = files.mapTo(linkedSetOf()) { it.uri.toString() }
                    importCopiesIntoApp = false
                    importRequestFolderId = null
                    showImportDestination = true
                }
            }
        }
    }
    val startAuthorizedRefresh: (Boolean) -> Unit = { keepActionSheet ->
        val requestGeneration = importPreparationGeneration + 1
        importPreparationGeneration = requestGeneration
        isPreparingImport = true
        showImportConfirmation = false
        if (!keepActionSheet) showImportActions = false
        homeViewModel.scanAuthorizedBookDirectories(context) { candidates ->
            if (importPreparationGeneration != requestGeneration) return@scanAuthorizedBookDirectories
            isPreparingImport = false
            val selected = candidates.map { candidate ->
                SelectedImportBook(
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
            }
            if (selected.isNotEmpty()) {
                selectedImportBooks = selected
                selectedImportBookUris = emptySet()
                importCopiesIntoApp = false
                showImportActions = false
                showImportConfirmation = true
            }
        }
    }
    fun openLocalBook(
        book: Book,
        animated: Boolean = true,
        sourceBounds: Rect? = null,
        downloadState: com.huangder.lumibooks.data.sync.BookDownloadState? = null,
        sourcePresentation: com.huangder.lumibooks.ui.animation.BookReaderPresentation? = null
    ) {
        if (book.isMissing) {
            transientMessage = context.getString(R.string.book_file_unavailable)
        } else if (eInkMode) {
            ReaderOpenPerformance.start(book.id)
            transitionBookId = book.id
            navController.navigate(Screen.Reader.createRoute(book.id))
        } else if (!animated) {
            ReaderOpenPerformance.start(book.id)
            transitionBookId = null
            readerReady = false
            showTransition = false
            navController.navigate(Screen.Reader.createRoute(book.id))
        } else if (entranceAnimationsEnabled && bookOpenUsesHeroTransition) {
            ReaderOpenPerformance.start(book.id)
            readerReady = false
            showTransition = false
            pendingBookId = null
            readerNavigationScope.launch {
                val started = bookReaderTransition.startOpen(
                    book = book,
                    downloadState = downloadState,
                    sourceBounds = sourceBounds,
                    sourceCornerRadiusDp = 0f,
                    sourcePresentation = sourcePresentation
                )
                if (started) {
                    navController.navigate(Screen.Reader.createRoute(book.id))
                } else {
                    bookReaderTransition.fallbackToLibrary()
                    transitionBookId = book.id
                    transitionCover = book.coverPath
                    transitionTitle = book.title
                    showTransition = true
                    pendingBookId = book.id
                }
            }
        } else {
            ReaderOpenPerformance.start(book.id)
            transitionBookId = book.id
            transitionCover = book.coverPath
            transitionTitle = book.title
            readerReady = false
            showTransition = true
            pendingBookId = book.id
        }
    }

    fun openBookRouteWithoutBook(bookId: String) {
        ReaderOpenPerformance.start(bookId)
        transitionBookId = null
        readerReady = false
        showTransition = false
        navController.navigate(Screen.Reader.createRoute(bookId))
    }

    fun requestOpenBook(book: Book, sourceBounds: Rect? = null,
        sourcePresentation: com.huangder.lumibooks.ui.animation.BookReaderPresentation? = null) {
        if (book.isCloudOnly) {
            cloudOpenSource = Triple(book.id, sourceBounds, sourcePresentation)
            if (homeUiState.downloadStates[book.id] is com.huangder.lumibooks.data.sync.BookDownloadState.Downloading) {
                autoOpenDownloadedBookId = book.id
            } else {
                pendingCloudBook = book
            }
        } else {
            openLocalBook(
                book = book,
                animated = true,
                sourceBounds = sourceBounds,
                downloadState = homeUiState.downloadStates[book.id],
                sourcePresentation = sourcePresentation
            )
        }
    }

    fun requestOpenBook(
        bookId: String,
        coverPath: String?,
        title: String,
        sourceBounds: Rect? = null,
        animated: Boolean = true
    ) {
        val book = homeUiState.books.firstOrNull { it.id == bookId }
            ?: Book(
                id = bookId,
                title = title,
                author = "",
                filePath = "",
                coverPath = coverPath,
                format = BookFormat.TXT,
                lastReadTime = 0L,
                readingProgress = 0f,
                createdAt = 0L
            )
        if (book.isCloudOnly) {
            if (homeUiState.downloadStates[book.id] is com.huangder.lumibooks.data.sync.BookDownloadState.Downloading) {
                autoOpenDownloadedBookId = book.id
            } else {
                pendingCloudBook = book
            }
        } else {
            if (homeUiState.books.any { it.id == bookId }) {
                openLocalBook(
                    book = book,
                    animated = animated,
                    sourceBounds = sourceBounds,
                    downloadState = homeUiState.downloadStates[book.id]
                )
            } else if (!animated) {
                // External open intents can arrive before HomeViewModel emits its first list.
                // ReaderViewModel resolves the authoritative local record by ID.
                openBookRouteWithoutBook(bookId)
            }
        }
    }
    val snackbarMessage = transientMessage
        ?: homeUiState.importMessage
        ?: homeUiState.tagMessage
        ?: homeUiState.error
    val homeLastReadBook = remember(homeUiState.books) {
        homeUiState.books.sortedByDescending { it.lastReadTime }.firstOrNull()
    }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        when {
            transientMessage == message -> transientMessage = null
            homeUiState.importMessage == message -> homeViewModel.clearImportMessage()
            homeUiState.tagMessage == message -> homeViewModel.clearTagMessage()
            homeUiState.error == message -> homeViewModel.clearError()
        }
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.downloadedBooks.collect { book ->
            if (autoOpenDownloadedBookId == book.id) {
                autoOpenDownloadedBookId = null
                pendingCloudBook = null
                val source = cloudOpenSource?.takeIf { it.first == book.id }
                cloudOpenSource = null
                openLocalBook(book, sourceBounds = source?.second, sourcePresentation = source?.third)
            }
        }
    }

    LaunchedEffect(requestedOpenBookId, requestedOpenBookDirect, homeUiState.isLoading) {
        val requestedId = requestedOpenBookId ?: return@LaunchedEffect
        if (homeUiState.isLoading && !requestedOpenBookDirect) return@LaunchedEffect
        val currentReaderBookId = navController.currentBackStackEntry
            ?.arguments
            ?.getString("bookId")
        if (currentReaderBookId != requestedId) {
            onBeforeOpenDifferentBook()
            val requestedBook = homeUiState.books.firstOrNull { it.id == requestedId }
            requestOpenBook(
                requestedId,
                requestedBook?.coverPath,
                requestedBook?.title.orEmpty(),
                animated = !requestedOpenBookDirect
            )
        }
        onOpenBookRequestConsumed()
    }

    LaunchedEffect(requestedOpenBookshelf) {
        if (!requestedOpenBookshelf) return@LaunchedEffect
        onBeforeOpenDifferentBook()
        if (navController.currentDestination?.route != Screen.Bookshelf.route) {
            navController.navigate(Screen.Bookshelf.route) {
                popUpTo(mainStartDestination)
                launchSingleTop = true
            }
        }
        if (requestedOpenFolderId == null) onOpenBookshelfRequestConsumed()
    }

    // 监听路由变化，从阅读页/设置页返回时延迟显示 TabBar
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val visibleEntries by navController.visibleEntries.collectAsState()
    val canCaptureNavigationBackdrop = navigationBackdropCaptureAllowed(
        currentRoute, visibleEntries.map { it.destination.route }
    )
    var lastMainRoute by rememberSaveable {
        mutableStateOf(if (requestedOpenBookshelf) Screen.Bookshelf.route else mainStartDestination)
    }
    var bookshelfFolderOpen by rememberSaveable { mutableStateOf(requestedOpenFolderId != null) }
    val windowConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    var bookshelfHeaderBottom by remember(windowConfiguration.screenWidthDp, windowConfiguration.screenHeightDp, windowConfiguration.fontScale) {
        mutableFloatStateOf(0f)
    }
    val backgroundRoute = if (requestedOpenBookshelf) Screen.Bookshelf.route else currentRoute?.takeIf {
        it == Screen.Home.route || it == Screen.Bookshelf.route || it == Screen.Statistics.route
    } ?: lastMainRoute
    androidx.compose.runtime.SideEffect { lastMainRoute = backgroundRoute }
    val backgroundScene = when {
        backgroundRoute != Screen.Bookshelf.route -> LumiBackgroundScene.HOME
        bookshelfFolderOpen -> LumiBackgroundScene.SECONDARY
        else -> LumiBackgroundScene.BOOKSHELF
    }

    if (currentRoute == Screen.Reader.route) {
        // Keep one owner while switching between PDF and parsed TXT reader entries.
        ImmersiveMode()
    } else {
        MainSystemBarStyle()
    }
    LaunchedEffect(currentRoute, showTransition) {
        val returningFromReader = previousRoute == Screen.Reader.route &&
            currentRoute != null &&
            currentRoute != Screen.Reader.route
        selectedTab = when (currentRoute) {
            Screen.Home.route -> 0
            Screen.Bookshelf.route -> 1
            Screen.Statistics.route -> 2
            else -> selectedTab
        }
        if (currentRoute != Screen.Home.route) {
            homeGoalSheetVisible = false
        }
        if (currentRoute != Screen.Bookshelf.route) {
            bookshelfOverlayProgress = 0f
        }
        if (currentRoute == Screen.Reader.route || showTransition) {
            tabBarVisible = false
            useMainReturnTabBarTransition = false
        } else if (returningFromReader) {
            useMainReturnTabBarTransition = true
            tabBarVisible = true
        } else {
            if (!eInkMode) {
                delay(800)
            }
            useMainReturnTabBarTransition = false
            tabBarVisible = true
        }
        previousRoute = currentRoute
    }

    // Navigate immediately so the reader can load behind the original loading page.
    // The overlay intentionally receives no source bounds: there is no connected-cover
    // transition, only the established loading surface.
    LaunchedEffect(pendingBookId) {
        val bookId = pendingBookId ?: return@LaunchedEffect
        if (!showTransition) return@LaunchedEffect
        navController.navigate(Screen.Reader.createRoute(bookId))
        pendingBookId = null
    }

    CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled,
        LocalCoverFlowEntrance provides coverFlowEntrance) {
    LumiBackgroundHost(
        scene = backgroundScene,
        modifier = Modifier.fillMaxSize(),
        bookshelfHeaderBottom = bookshelfHeaderBottom,
        backgroundBackdrop = liquidGlassBackdrop
    ) {
    LiquidGlassMenuHost(
        modifier = Modifier.fillMaxSize(),
        backdrop = liquidGlassBackdrop.takeIf {
            isLiquidGlass && canCaptureNavigationBackdrop
        }
    ) {
        LiquidGlassDialogHost(
            modifier = Modifier.fillMaxSize(),
            backdrop = liquidGlassBackdrop.takeIf {
                isLiquidGlass && canCaptureNavigationBackdrop
            }
        ) {
        ConfigurableNavigationBack(
            predictiveBackEnabled = predictiveBackEnabled,
            bridgeEnabled = currentRoute != null && navController.previousBackStackEntry != null
        ) {
            // 主内容
            // Cover motion is rendered by BookHeroWindowOverlay without resizing library cells.
            Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = mainStartDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (canCaptureNavigationBackdrop && isLiquidGlass && !isLumiChan) {
                            Modifier.layerBackdrop(liquidGlassBackdrop)
                        } else if (canCaptureNavigationBackdrop && !eInkMode) {
                            Modifier.haze(hazeState)
                        } else {
                            Modifier
                        }
                    )
            ) {
            composable(
                route = Screen.Home.route,
                enterTransition = { if (eInkMode) EnterTransition.None else null },
                exitTransition = {
                    when {
                        eInkMode -> ExitTransition.None
                        targetState.destination.route == Screen.Reader.route &&
                            bookReaderTransition.usesHeroTransition -> scaleOut(
                            targetScale = 1f,
                            animationSpec = tween(
                                durationMillis =
                                    com.huangder.lumibooks.ui.animation.BookReaderMotion.WINDOW_DURATION_MS,
                                easing = LinearEasing
                            )
                        )
                        else -> null
                    }
                },
                popEnterTransition = {
                    if (eInkMode) {
                        EnterTransition.None
                    } else if (
                        initialState.destination.route == Screen.Reader.route &&
                        bookReaderTransition.usesHeroTransition
                    ) {
                        EnterTransition.None
                    } else if (initialState.destination.route == Screen.Reader.route) {
                        fadeIn(tween(300, easing = FastOutSlowInEasing)) + scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        )
                    } else {
                        null
                    }
                },
                popExitTransition = { if (eInkMode) ExitTransition.None else null }
            ) { backStackEntry ->
                val playEntranceAnimation = rememberPageEntrancePlayback(
                    pageKey = Screen.Home.route,
                    entryKey = backStackEntry.id,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                val bookAnchorScope = remember(bookReaderTransition) {
                    BookReaderAnchorScope(transitionState = bookReaderTransition)
                }
                CompositionLocalProvider(
                    LocalBookReaderAnchorScope provides bookAnchorScope.takeIf {
                        entranceAnimationsEnabled && !eInkMode
                    }
                ) {
                    BookReaderLibraryLayer(
                        transition = bookReaderTransition,
                        blurEnabled = !eInkMode
                    ) {
                    HomeScreen(
                    playEntranceAnimation = playEntranceAnimation,
                    onNavigateToReader = { book, sourceBounds ->
                        requestOpenBook(book, sourceBounds)
                    },
                    onTabBarVisibleChange = { visible -> tabBarVisible = visible },
                    onNavigateToStatistics = {
                        selectedTab = 2
                        navController.navigate(Screen.Statistics.route) {
                            popUpTo(mainStartDestination) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToBookshelf = {
                        selectedTab = 1
                        navController.navigate(Screen.Bookshelf.route) {
                            popUpTo(mainStartDestination) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onImportClick = {
                        importRequestFolderId = null
                        selectedImportBooks = emptyList()
                        selectedImportBookUris = emptySet()
                        importCopiesIntoApp = true
                        isPreparingImport = false
                        importPreparationGeneration++
                        showImportActions = true
                        showImportConfirmation = false
                    },
                    showImportButton = !isLiquidGlass,
                    showReadingGoalSheet = homeGoalSheetVisible,
                    onReadingGoalSheetVisibleChange = { visible -> homeGoalSheetVisible = visible },
                    renderReadingGoalSheet = false,
                    viewModel = homeViewModel
                )
                    }
                }
            }

            composable(
                route = Screen.Bookshelf.route,
                enterTransition = { if (eInkMode) EnterTransition.None else null },
                exitTransition = {
                    when {
                        eInkMode -> ExitTransition.None
                        targetState.destination.route == Screen.Reader.route &&
                            bookReaderTransition.usesHeroTransition -> scaleOut(
                            targetScale = 1f,
                            animationSpec = tween(
                                durationMillis =
                                    com.huangder.lumibooks.ui.animation.BookReaderMotion.WINDOW_DURATION_MS,
                                easing = LinearEasing
                            )
                        )
                        else -> null
                    }
                },
                popEnterTransition = {
                    if (eInkMode) {
                        EnterTransition.None
                    } else if (
                        initialState.destination.route == Screen.Reader.route &&
                        bookReaderTransition.usesHeroTransition
                    ) {
                        EnterTransition.None
                    } else if (initialState.destination.route == Screen.Reader.route) {
                        fadeIn(tween(300, easing = FastOutSlowInEasing)) + scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        )
                    } else {
                        null
                    }
                },
                popExitTransition = { if (eInkMode) ExitTransition.None else null }
            ) { backStackEntry ->
                val playEntranceAnimation = rememberPageEntrancePlayback(
                    pageKey = Screen.Bookshelf.route,
                    entryKey = backStackEntry.id,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                val bookAnchorScope = remember(bookReaderTransition) {
                    BookReaderAnchorScope(transitionState = bookReaderTransition)
                }
                CompositionLocalProvider(
                    LocalBookReaderAnchorScope provides bookAnchorScope.takeIf {
                        entranceAnimationsEnabled && !eInkMode
                    }
                ) {
                    BookReaderLibraryLayer(
                        transition = bookReaderTransition,
                        blurEnabled = !eInkMode
                    ) {
                BookshelfScreen(
                    onFolderOpenChange = { bookshelfFolderOpen = it },
                    onHeaderBottomChange = { bookshelfHeaderBottom = it },
                    initialLayoutMode = initialBookshelfLayoutMode,
                    playEntranceAnimation = playEntranceAnimation,
                    onNavigateToReader = { book, sourceBounds ->
                        requestOpenBook(book, sourceBounds)
                    },
                    onNavigateToReaderCoverFlow = { book, sourceBounds ->
                        requestOpenBook(book, sourceBounds,
                            com.huangder.lumibooks.ui.animation.BookReaderPresentation.CoverFlow)
                    },
                    onAddBook = { folderId ->
                        importRequestFolderId = folderId
                        selectedImportBooks = emptyList()
                        selectedImportBookUris = emptySet()
                        importCopiesIntoApp = true
                        isPreparingImport = false
                        importPreparationGeneration++
                        showImportActions = true
                        showImportConfirmation = false
                    },
                    requestedFolderId = requestedOpenFolderId,
                    onRequestedFolderConsumed = onOpenBookshelfRequestConsumed,
                    onMessage = { transientMessage = it },
                    onRefreshAuthorizedDirectories = { startAuthorizedRefresh(false) },
                    onOverlayProgressChange = { progress ->
                        bookshelfOverlayProgress = progress.coerceIn(0f, 1f)
                    },
                    onContextMenuVisibleChange = { visible ->
                        bookshelfContextMenuVisible = visible
                    },
                    onEnterCoverFlow = { commit ->
                        if (eInkMode) commit() else coverFlowEntrance.enter(entranceAnimationsEnabled, commit)
                    },
                    viewModel = homeViewModel
                )
                    }
                }
            }

            composable(
                route = Screen.Statistics.route,
                enterTransition = { if (eInkMode) EnterTransition.None else null },
                exitTransition = { if (eInkMode) ExitTransition.None else null },
                popEnterTransition = {
                    if (eInkMode) {
                        EnterTransition.None
                    } else if (initialState.destination.route == Screen.Reader.route) {
                        fadeIn(tween(300, easing = FastOutSlowInEasing)) + scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        )
                    } else {
                        null
                    }
                },
                popExitTransition = { if (eInkMode) ExitTransition.None else null }
            ) { backStackEntry ->
                val playEntranceAnimation = rememberPageEntrancePlayback(
                    pageKey = Screen.Statistics.route,
                    entryKey = backStackEntry.id,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                StatisticsScreen(
                    playEntranceAnimation = playEntranceAnimation,
                    onMessage = { transientMessage = it }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                enterTransition = {
                    if (bookReaderTransition.usesHeroTransition) {
                        EnterTransition.None
                    } else {
                        null
                    }
                },
                exitTransition = {
                    if (bookReaderTransition.usesHeroTransition) {
                        scaleOut(
                            targetScale = 1f,
                            animationSpec = tween(
                                durationMillis =
                                    com.huangder.lumibooks.ui.animation.BookReaderMotion.WINDOW_DURATION_MS,
                                easing = LinearEasing
                            )
                        )
                    } else {
                        null
                    }
                },
                popExitTransition = {
                    if (!eInkMode && bookReaderTransition.usesHeroTransition) {
                        scaleOut(
                            targetScale = 1f,
                            animationSpec = tween(
                                durationMillis =
                                    com.huangder.lumibooks.ui.animation.BookReaderMotion.WINDOW_DURATION_MS,
                                easing = LinearEasing
                            )
                        )
                    } else if (!eInkMode && targetState.destination.route != Screen.Reader.route) {
                        fadeOut(tween(240, easing = FastOutSlowInEasing)) + scaleOut(
                            targetScale = 0.985f,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        )
                    } else {
                        null
                    }
                }
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                // Keep this reader's CF layer through the entire Navigation exit. The shared
                // state returns to Library before Navigation disposes its outgoing destination.
                val coverFlowReader = remember(bookId) {
                    bookReaderTransition.activeBookId == bookId && bookReaderTransition.presentation ==
                        com.huangder.lumibooks.ui.animation.BookReaderPresentation.CoverFlow
                }
                val bookAnchorScope = remember(bookReaderTransition) {
                    BookReaderAnchorScope(transitionState = bookReaderTransition)
                }
                val openingSession = remember(backStackEntry.id) { bookReaderTransition.sessionId }
                val readerActive = currentEntry?.id == backStackEntry.id

                CompositionLocalProvider(
                    LocalBookReaderAnchorScope provides bookAnchorScope.takeIf {
                        entranceAnimationsEnabled && !eInkMode
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .bookReaderWindowClip(bookReaderTransition)
                                .then(
                                    if (coverFlowReader ||
                                        bookReaderTransition.phase ==
                                        com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.Opening ||
                                        bookReaderTransition.phase ==
                                        com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.ReaderLoading ||
                                        bookReaderTransition.phase ==
                                        com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.Ready ||
                                        bookReaderTransition.phase ==
                                        com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.Closing
                                    ) {
                                        Modifier.graphicsLayer {
                                            val transitionPhase = bookReaderTransition.phase
                                            alpha = when (transitionPhase) {
                                                com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.Ready ->
                                                    bookReaderTransition.readerRevealSnapshot.value
                                                com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.Opening,
                                                com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.ReaderLoading -> 0f
                                                com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase.Closing ->
                                                    bookReaderTransition.readerExitAlphaSnapshot.value
                                                else -> 1f
                                            }
                                            translationY = 0f
                                            if (coverFlowReader) {
                                                val p = bookReaderTransition.coverFlowProgressSnapshot.value
                                                alpha = com.huangder.lumibooks.ui.animation.CoverFlowReaderMotion.readerAlpha(p) *
                                                    bookReaderTransition.readerRevealSnapshot.value
                                                scaleX = com.huangder.lumibooks.ui.animation.CoverFlowReaderMotion.readerScale(p)
                                                scaleY = scaleX
                                                translationY = 0f
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                ReaderRouter(
                    bookId = bookId,
                    onNavigateBack = {
                        if (bookReaderTransition.activeBookId == bookId) {
                            if (!bookReaderTransition.startClose()) {
                                bookReaderTransition.fallbackToLibrary()
                            }
                        }
                        showTransition = false
                        pendingBookId = null
                        ReaderOpenPerformance.cancel(bookId)
                        navController.popBackStack()
                    },
                    onFirstContentDrawn = {
                        if (readerActive) {
                            ReaderOpenPerformance.markFirstContentDrawn(bookId)
                            readerReady = true
                            if (bookReaderTransition.activeBookId == bookId) {
                                bookReaderTransition.markReaderReady(openingSession)
                            }
                        }
                    },
                    onInteractive = {
                        if (readerActive) ReaderOpenPerformance.markInteractive(bookId)
                    },
                    readerActive = readerActive,
                    openingComplete = if (bookReaderTransition.activeBookId == bookId) {
                        bookReaderTransition.phase == BookReaderTransitionPhase.Reader
                    } else !showTransition,
                    onOpenBook = { targetBookId ->
                        onBeforeOpenDifferentBook()
                        val target = homeUiState.books.firstOrNull { it.id == targetBookId }
                        requestOpenBook(
                            targetBookId,
                            target?.coverPath,
                            target?.title.orEmpty()
                        )
                    }
                )
                        }
                    }
                }
            }

            }
            }
        }


        val heroTabTransition = bookReaderTransition.usesHeroTransition
        val heroBackdropVisible = heroTabTransition &&
            bookReaderTransition.phase != BookReaderTransitionPhase.Reader
        // Keep the bar in the same full-screen backdrop transform; the moving window occludes it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .bookReaderWindowClip(bookReaderTransition, outside = true)
                .bookReaderLibraryLayer(bookReaderTransition, blurEnabled = !eInkMode)
                .graphicsLayer {
                    if (bookReaderTransition.presentation == com.huangder.lumibooks.ui.animation.BookReaderPresentation.CoverFlow) {
                        alpha = 1f - com.huangder.lumibooks.ui.animation.CoverFlowReaderMotion.readerAlpha(
                            bookReaderTransition.coverFlowProgressSnapshot.value)
                    }
                    renderEffect = if (
                        !eInkMode &&
                        bookshelfOverlayProgress > 0.01f &&
                        android.os.Build.VERSION.SDK_INT >= 31
                    ) {
                        android.graphics.RenderEffect.createBlurEffect(
                            20f * bookshelfOverlayProgress,
                            20f * bookshelfOverlayProgress,
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
        ) {
        // Keep the transition host composed so the bar animates out for context menus
        // and animates back in after the menu returns to Idle.
        AnimatedVisibility(
            visible = (heroBackdropVisible || tabBarVisible) && !bookshelfContextMenuVisible,
            enter = if (eInkMode || heroTabTransition) {
                EnterTransition.None
            } else if (useMainReturnTabBarTransition) {
                fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 3 }
                    )
            } else {
                fadeIn(animationSpec = tween(400)) +
                    slideInVertically(
                        animationSpec = tween(400, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 3 }
                    )
            },
            exit = if (eInkMode || heroTabTransition) {
                ExitTransition.None
            } else {
                fadeOut(animationSpec = tween(300)) +
                    slideOutVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        targetOffsetY = { it / 3 }
                    )
            },
            modifier = Modifier.align(Alignment.BottomCenter).coverFlowEntranceItem(6)
                .then(if (heroTabTransition) Modifier.clearAndSetSemantics { } else Modifier)
                .pointerInput(heroTabTransition) {
                    if (heroTabTransition) awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            val showLiquidImport = isLiquidGlass
            val selectTab: (Int) -> Unit = { index ->
                selectedTab = index
                val route = when (index) {
                    0 -> Screen.Home.route
                    1 -> Screen.Bookshelf.route
                    2 -> Screen.Statistics.route
                    else -> Screen.Home.route
                }
                navController.navigate(route) {
                    popUpTo(mainStartDestination) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            if (useMaterial3Navigation) {
                Material3BottomNavigationBar(
                    selectedIndex = selectedTab,
                    onTabSelected = selectTab
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val requestImport = {
                        importRequestFolderId = null
                        selectedImportBooks = emptyList()
                        selectedImportBookUris = emptySet()
                        importCopiesIntoApp = true
                        isPreparingImport = false
                        importPreparationGeneration++
                        showImportActions = true
                        showImportConfirmation = false
                    }
                    Box(
                        modifier = Modifier.widthIn(
                            max = if (isLiquidGlass) 480.dp else 430.dp
                        )
                    ) {
                        if (showLiquidImport) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                                    .padding(end = 24.dp, bottom = 10.dp)
                            ) {
                                LiquidGlassImportButton(
                                    onClick = requestImport,
                                    liquidGlassBackdrop = liquidGlassBackdrop
                                )
                            }
                        }
                        FloatingTabBar(
                            selectedIndex = selectedTab,
                            hazeState = hazeState,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            reserveImportButtonSpace = showLiquidImport,
                            onTabSelected = selectTab
                        )
                    }
                }
            }
        }

        }
        if (showImportActions) {
            ImportBooksActionSheet(
                isPreparing = isPreparingImport,
                authorizedDirectoryUris = homeUiState.authorizedBookDirectories,
                onDismiss = {
                    importPreparationGeneration++
                    isPreparingImport = false
                    selectedImportBooks = emptyList()
                    selectedImportBookUris = emptySet()
                    showImportActions = false
                },
                onSelectFiles = {
                    runCatching {
                        importLauncher.launch(arrayOf("*/*"))
                    }.onFailure { error ->
                        transientMessage = context.getString(
                            R.string.import_failed,
                            error.message.orEmpty()
                        )
                    }
                },
                onAuthorizeDirectory = {
                    runCatching {
                        directoryLauncher.launch(null)
                    }.onFailure { error ->
                        transientMessage = context.getString(
                            R.string.import_failed,
                            error.message.orEmpty()
                        )
                    }
                },
                onOpenFolderBooks = {
                    if (homeUiState.authorizedBookDirectories.isEmpty()) {
                        transientMessage = context.getString(R.string.import_no_authorized_directory)
                    } else {
                        importPreparationGeneration++
                        isPreparingImport = false
                        selectedImportBooks = emptyList()
                        selectedImportBookUris = emptySet()
                        showImportActions = false
                        folderBooksLauncher.launch(
                            AuthorizedFolderBooksActivity.createIntent(context)
                        )
                    }
                }
            )
        }

        if (showImportConfirmation) {
            ImportBooksConfirmationSheet(
                selectedBooks = selectedImportBooks,
                selectedBookUris = selectedImportBookUris,
                layoutMode = homeUiState.importBooksLayoutMode,
                onLayoutModeChange = homeViewModel::setImportBooksLayoutMode,
                onBookSelectionToggle = { book ->
                    val uriKey = book.uri.toString()
                    selectedImportBookUris = if (uriKey in selectedImportBookUris) {
                        selectedImportBookUris - uriKey
                    } else {
                        selectedImportBookUris + uriKey
                    }
                },
                onSelectAll = {
                    val allUris = selectedImportBooks
                        .mapTo(linkedSetOf()) { it.uri.toString() }
                    selectedImportBookUris = if (selectedImportBookUris.containsAll(allUris)) {
                        emptySet()
                    } else {
                        allUris
                    }
                },
                onDismiss = {
                    importPreparationGeneration++
                    isPreparingImport = false
                    selectedImportBooks = emptyList()
                    selectedImportBookUris = emptySet()
                    showImportConfirmation = false
                },
                onConfirmImport = {
                    val selected = selectedImportBooks
                        .filter { it.uri.toString() in selectedImportBookUris }
                    if (selected.isNotEmpty()) {
                        importPreparationGeneration++
                        isPreparingImport = false
                        showImportConfirmation = false
                        if (!importCopiesIntoApp || importRequestFolderId != null) {
                            showImportDestination = true
                        } else {
                            homeViewModel.importBooks(context, selected.map { it.uri })
                            selectedImportBooks = emptyList()
                            selectedImportBookUris = emptySet()
                        }
                    }
                }
            )
        }

        pendingCloudBook?.let { book ->
            CloudBookDownloadDialog(
                book = book,
                onDismiss = { pendingCloudBook = null },
                onDownload = {
                    autoOpenDownloadedBookId = book.id
                    pendingCloudBook = null
                    homeViewModel.downloadCloudBook(book.id)
                }
            )
        }

        if (showImportDestination) {
            val selected = selectedImportBooks
                .filter { it.uri.toString() in selectedImportBookUris }
            val sourceNames = selected.mapNotNull { it.sourceDirectoryName }.distinct()
            val currentFolderName = importRequestFolderId
                ?.let { id -> homeUiState.folders.firstOrNull { it.id == id }?.name }
            val primaryLabel = if (importCopiesIntoApp) {
                context.getString(
                    R.string.import_to_current_folder,
                    currentFolderName ?: context.getString(R.string.bookshelf_title)
                )
            } else if (sourceNames.size == 1) {
                context.getString(R.string.import_to_authorized_folder, sourceNames.first())
            } else {
                context.getString(R.string.import_group_authorized_folders)
            }
            val primaryDetail = if (!importCopiesIntoApp && sourceNames.size > 1) {
                context.getString(
                    R.string.import_group_authorized_folders_detail,
                    sourceNames.joinToString("、")
                )
            } else {
                null
            }
            val clearPendingImport = {
                selectedImportBooks = emptyList()
                selectedImportBookUris = emptySet()
                showImportDestination = false
                importRequestFolderId = null
            }
            ImportDestinationSheet(
                primaryLabel = primaryLabel,
                primaryDetail = primaryDetail,
                onPrimary = {
                    if (importCopiesIntoApp) {
                        homeViewModel.importBooks(
                            context,
                            selected.map { it.uri },
                            importRequestFolderId
                        )
                    } else {
                        homeViewModel.importAuthorizedBooks(
                            context,
                            selected.map { book ->
                                com.huangder.lumibooks.ui.home.BookImportCandidate(
                                    uri = book.uri,
                                    name = book.name,
                                    sourceDirectoryUri = book.sourceDirectoryUri,
                                    sourceDirectoryName = book.sourceDirectoryName,
                                    sourceRelativeDirectory = book.sourceRelativeDirectory,
                                    sourceDirectoryDocumentUri = book.sourceDirectoryDocumentUri,
                                    sourceDocumentKey = book.sourceDocumentKey,
                                    sourceLastModified = book.sourceLastModified,
                                    sourceSize = book.sourceSize,
                                    sourceDirectoryBindings = book.sourceDirectoryBindings
                                )
                            },
                            groupBySourceFolder = true
                        )
                    }
                    clearPendingImport()
                },
                onRoot = {
                    if (importCopiesIntoApp) {
                        homeViewModel.importBooks(context, selected.map { it.uri })
                    } else {
                        homeViewModel.importAuthorizedBooks(
                            context,
                            selected.map { book ->
                                com.huangder.lumibooks.ui.home.BookImportCandidate(
                                    uri = book.uri,
                                    name = book.name,
                                    sourceDirectoryUri = book.sourceDirectoryUri,
                                    sourceDirectoryName = book.sourceDirectoryName,
                                    sourceRelativeDirectory = book.sourceRelativeDirectory,
                                    sourceDirectoryDocumentUri = book.sourceDirectoryDocumentUri,
                                    sourceDocumentKey = book.sourceDocumentKey,
                                    sourceLastModified = book.sourceLastModified,
                                    sourceSize = book.sourceSize,
                                    sourceDirectoryBindings = book.sourceDirectoryBindings
                                )
                            },
                            groupBySourceFolder = false
                        )
                    }
                    clearPendingImport()
                },
                onDismiss = clearPendingImport
            )
        }

        ReadingGoalSheet(
            visible = homeGoalSheetVisible && currentRoute == Screen.Home.route,
            todayReadingTime = homeUiState.todayReadingTime,
            dailyGoal = homeUiState.dailyGoal,
            currentBook = homeLastReadBook,
            weeklyData = homeUiState.weeklyData,
            streakDays = homeUiState.streakDays,
            onDismiss = { homeGoalSheetVisible = false },
            onSaveGoal = { minutes -> homeViewModel.saveDailyGoal(minutes) }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = if (useMaterial3Navigation) 96.dp else 88.dp
                )
        )

        BookHeroWindowOverlay(
            transition = bookReaderTransition,
            modifier = Modifier.fillMaxSize()
        )
        com.huangder.lumibooks.ui.animation.CoverFlowEntranceOverlay(coverFlowEntrance)

        if (showTransition && !eInkMode) {
            BookTransitionOverlay(
                title = transitionTitle,
                coverPath = transitionCover,
                isReady = readerReady,
                onBackNavigationStarted = {
                    pendingBookId = null
                    readerReady = false
                    transitionBookId?.let(ReaderOpenPerformance::cancel)
                    transitionBookId = null
                    if (navController.currentDestination?.route == Screen.Reader.route) {
                        navController.popBackStack()
                    }
                },
                onBack = { showTransition = false },
                onTransitionComplete = {
                    showTransition = false
                    transitionBookId = null
                }
            )
        }

        }
    }
    }
    }
}

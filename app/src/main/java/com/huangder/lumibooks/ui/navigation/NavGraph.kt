package com.huangder.lumibooks.ui.navigation

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.huangder.lumibooks.ui.home.HomeScreen
import com.huangder.lumibooks.ui.home.ImportBooksActionSheet
import com.huangder.lumibooks.ui.home.ImportBooksConfirmationSheet
import com.huangder.lumibooks.ui.home.ImportDestinationSheet
import com.huangder.lumibooks.ui.home.SelectedImportBook
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.home.ReadingGoalSheet
import com.huangder.lumibooks.ui.animation.PageEntranceTracker
import com.huangder.lumibooks.ui.animation.PAGE_ENTRANCE_PLAYBACK_MILLIS
import com.huangder.lumibooks.ui.reader.PdfViewerScreen
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

/**
 * 根据书籍格式路由：PDF → 竖向滚动，EPUB/TXT → 横向翻页
 */
@Composable
private fun ReaderRouter(
    bookId: String,
    onNavigateBack: () -> Unit,
    onFirstContentDrawn: () -> Unit,
    onInteractive: () -> Unit,
    onOpenBook: (String) -> Unit
) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val documentState by viewModel.documentState.collectAsState()
    val isPdf = documentState.book?.format?.name == "PDF"
    val isAppDarkTheme = LocalIsDarkTheme.current
    val appTheme = LocalAppTheme.current
    val appAccentColor = LocalAppAccentHex.current
    val liquidGlassTransparency = LocalLiquidGlassTransparency.current
    val liquidGlassHdrHighlightEnabled = LocalLiquidGlassHdrHighlightEnabled.current
    val useMaterial3Theme = LocalUseMaterial3Theme.current
    val eInkMode = LocalEInkMode.current
    val globalFontMode = LocalGlobalFontMode.current
    val motionPreference = LocalMotionPreference.current

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
        CompositionLocalProvider(LocalReaderColors provides ReaderColors.Light) {
            if (isPdf) {
                PdfViewerScreen(
                    bookId = bookId,
                    onNavigateBack = onNavigateBack,
                    onOpenBook = onOpenBook,
                    viewModel = viewModel
                )
                LaunchedEffect(Unit) {
                    onFirstContentDrawn()
                    if (eInkMode) onInteractive()
                }
            } else {
                ReaderScreen(
                    bookId = bookId,
                    onNavigateBack = onNavigateBack,
                    onFirstContentDrawn = onFirstContentDrawn,
                    onInteractive = onInteractive,
                    viewModel = viewModel
                )
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
    entranceAnimationsEnabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    requestedOpenBookId: String? = null,
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
    var showTransition by remember { mutableStateOf(false) }
    var transitionCover by remember { mutableStateOf<String?>(null) }
    var transitionTitle by remember { mutableStateOf("") }
    var transitionBookId by remember { mutableStateOf<String?>(null) }
    var readerReady by remember { mutableStateOf(false) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var tabBarVisible by remember { mutableStateOf(true) }
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
    val snackbarHostState = remember { SnackbarHostState() }
    val entranceTracker = remember { PageEntranceTracker() }
    val hazeState = remember { HazeState() }
    val eInkMode = LocalEInkMode.current
    val useMaterial3Navigation = LocalUseMaterial3Theme.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    val liquidGlassBackdrop = rememberLayerBackdrop()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val context = LocalContext.current
    val authorizedStorageManager = remember { com.huangder.lumibooks.util.AuthorizedStorageManager() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (showImportActions) {
            val candidates = uris.mapNotNull { uri ->
                val name = FileUtils.getFileNameFromUri(context, uri) ?: return@mapNotNull null
                if (FileUtils.getFileExtension(name) !in setOf("epub", "pdf", "txt", "mobi")) {
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
    val openLocalBook: (Book) -> Unit = { book ->
        if (book.isMissing) {
            transientMessage = context.getString(R.string.book_file_unavailable)
        } else if (eInkMode) {
            ReaderOpenPerformance.start(book.id)
            transitionBookId = book.id
            navController.navigate(Screen.Reader.createRoute(book.id))
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
    val requestOpenBook: (String, String?, String) -> Unit = { bookId, coverPath, title ->
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
            openLocalBook(book)
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
                openLocalBook(book)
            }
        }
    }

    LaunchedEffect(requestedOpenBookId, homeUiState.isLoading) {
        val requestedId = requestedOpenBookId ?: return@LaunchedEffect
        if (homeUiState.isLoading) return@LaunchedEffect
        val currentReaderBookId = navController.currentBackStackEntry
            ?.arguments
            ?.getString("bookId")
        if (currentReaderBookId != requestedId) {
            onBeforeOpenDifferentBook()
            val requestedBook = homeUiState.books.firstOrNull { it.id == requestedId }
            requestOpenBook(
                requestedId,
                requestedBook?.coverPath,
                requestedBook?.title.orEmpty()
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

    CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled) {
    LiquidGlassMenuHost(
        modifier = Modifier.fillMaxSize(),
        backdrop = liquidGlassBackdrop.takeIf {
            isLiquidGlass && currentRoute != Screen.Reader.route
        }
    ) {
        LiquidGlassDialogHost(
            modifier = Modifier.fillMaxSize(),
            backdrop = liquidGlassBackdrop.takeIf {
                isLiquidGlass && currentRoute != Screen.Reader.route
            }
        ) {
        ConfigurableNavigationBack(
            predictiveBackEnabled = predictiveBackEnabled,
            bridgeEnabled = currentRoute != null && navController.previousBackStackEntry != null
        ) {
            // 主内容
            NavHost(
                navController = navController,
                startDestination = mainStartDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLiquidGlass && currentRoute != Screen.Reader.route) {
                            Modifier.layerBackdrop(liquidGlassBackdrop)
                        } else if (!eInkMode) {
                            Modifier.haze(hazeState)
                        } else {
                            Modifier
                        }
                    )
            ) {
            composable(
                route = Screen.Home.route,
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
                    pageKey = Screen.Home.route,
                    entryKey = backStackEntry.id,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                    HomeScreen(
                    playEntranceAnimation = playEntranceAnimation,
                    onNavigateToReader = { bookId, coverPath, title, _ ->
                        requestOpenBook(bookId, coverPath, title)
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

            composable(
                route = Screen.Bookshelf.route,
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
                    pageKey = Screen.Bookshelf.route,
                    entryKey = backStackEntry.id,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                BookshelfScreen(
                    playEntranceAnimation = playEntranceAnimation,
                    onNavigateToReader = { bookId, coverPath, title, _ ->
                        requestOpenBook(bookId, coverPath, title)
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
                    viewModel = homeViewModel
                )
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
                popExitTransition = {
                    if (!eInkMode && targetState.destination.route != Screen.Reader.route) {
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
                ReaderRouter(
                    bookId = bookId,
                    onNavigateBack = {
                        showTransition = false
                        pendingBookId = null
                        navController.popBackStack()
                    },
                    onFirstContentDrawn = {
                        ReaderOpenPerformance.markFirstContentDrawn(bookId)
                        readerReady = true
                    },
                    onInteractive = { ReaderOpenPerformance.markInteractive(bookId) },
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


        // 浮动导航栏（渐隐渐显）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
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
        AnimatedVisibility(
            visible = tabBarVisible,
            enter = if (eInkMode) {
                EnterTransition.None
            } else if (useMainReturnTabBarTransition) {
                fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
            } else {
                fadeIn(animationSpec = tween(400))
            },
            exit = if (eInkMode) ExitTransition.None else fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
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
                                    .navigationBarsPadding()
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
                    onRefreshDirectories = {
                    startAuthorizedRefresh(true)
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
                    transitionBookId?.let(ReaderOpenPerformance::markInteractive)
                    transitionBookId = null
                }
            )
        }

        }
    }
    }
}

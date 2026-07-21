package com.huangder.lumibooks.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import com.huangder.lumibooks.ui.bookshelf.BookshelfScreen
import com.huangder.lumibooks.ui.components.BookTransitionOverlay
import com.huangder.lumibooks.ui.components.FloatingTabBar
import com.huangder.lumibooks.ui.components.ImmersiveMode
import com.huangder.lumibooks.ui.components.ConfigurableNavigationBack
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.home.HomeScreen
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.home.ReadingGoalSheet
import com.huangder.lumibooks.ui.animation.PageEntranceTracker
import com.huangder.lumibooks.ui.animation.PAGE_ENTRANCE_PLAYBACK_MILLIS
import com.huangder.lumibooks.ui.reader.PdfViewerScreen
import com.huangder.lumibooks.ui.reader.ReaderScreen
import com.huangder.lumibooks.ui.reader.PdfViewerScreen
import com.huangder.lumibooks.ui.reader.ReaderViewModel
import com.huangder.lumibooks.ui.statistics.StatisticsScreen
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme
import com.huangder.lumibooks.ui.theme.LocalReaderColors
import com.huangder.lumibooks.ui.theme.ReaderColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/**
 * 根据书籍格式路由：PDF → 竖向滚动，EPUB/TXT → 横向翻页
 */
@Composable
private fun ReaderRouter(
    bookId: String,
    onNavigateBack: () -> Unit,
    onLoadingComplete: () -> Unit,
    onOpenBook: (String) -> Unit
) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val isPdf = uiState.book?.format?.name == "PDF"
    val isAppDarkTheme = LocalIsDarkTheme.current
    val appTheme = LocalAppTheme.current
    val liquidGlassTransparency = LocalLiquidGlassTransparency.current
    val useMaterial3Theme = LocalUseMaterial3Theme.current

    // 正文颜色由阅读主题控制，弹层和应用级控件继承全局主题。
    EBookReaderTheme(
        darkTheme = isAppDarkTheme,
        dynamicColor = useMaterial3Theme,
        appTheme = appTheme,
        liquidGlassTransparency = liquidGlassTransparency
    ) {
        CompositionLocalProvider(LocalReaderColors provides ReaderColors.Light) {
            if (isPdf) {
                PdfViewerScreen(
                    bookId = bookId,
                    onNavigateBack = onNavigateBack,
                    onOpenBook = onOpenBook,
                    viewModel = viewModel
                )
                LaunchedEffect(Unit) { onLoadingComplete() }
            } else {
                ReaderScreen(bookId = bookId, onNavigateBack = onNavigateBack, onLoadingComplete = onLoadingComplete, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun rememberPageEntrancePlayback(
    pageKey: String,
    entryKey: Any,
    enabled: Boolean,
    tracker: PageEntranceTracker
): Boolean {
    var play by remember(entryKey, enabled) {
        mutableStateOf(
            enabled && tracker.shouldPlay(pageKey, SystemClock.elapsedRealtime())
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
    entranceAnimationsEnabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    requestedOpenBookId: String? = null,
    onOpenBookRequestConsumed: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTransition by remember { mutableStateOf(false) }
    var transitionCover by remember { mutableStateOf<String?>(null) }
    var transitionTitle by remember { mutableStateOf("") }
    var readerReady by remember { mutableStateOf(false) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var tabBarVisible by remember { mutableStateOf(true) }
    var homeGoalSheetVisible by remember { mutableStateOf(false) }
    val entranceTracker = remember { PageEntranceTracker() }
    val hazeState = remember { HazeState() }
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val liquidGlassBackdrop = rememberLayerBackdrop()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val homeUiState by homeViewModel.uiState.collectAsState()
    val homeLastReadBook = remember(homeUiState.books) {
        homeUiState.books.sortedByDescending { it.lastReadTime }.firstOrNull()
    }

    LaunchedEffect(requestedOpenBookId) {
        val requestedId = requestedOpenBookId ?: return@LaunchedEffect
        val currentReaderBookId = navController.currentBackStackEntry
            ?.arguments
            ?.getString("bookId")
        if (currentReaderBookId != requestedId) {
            navController.navigate(Screen.Reader.createRoute(requestedId))
        }
        onOpenBookRequestConsumed()
    }

    // 监听路由变化，从阅读页/设置页返回时延迟显示 TabBar
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    if (currentRoute == Screen.Reader.route) {
        // Keep one owner while switching between PDF and parsed TXT reader entries.
        ImmersiveMode()
    }
    LaunchedEffect(currentRoute, showTransition) {
        selectedTab = when (currentRoute) {
            Screen.Home.route -> 0
            Screen.Bookshelf.route -> 1
            Screen.Statistics.route -> 2
            else -> selectedTab
        }
        if (currentRoute != Screen.Home.route) {
            homeGoalSheetVisible = false
        }
        if (currentRoute == Screen.Reader.route || showTransition) {
            tabBarVisible = false
        } else {
            delay(800)
            tabBarVisible = true
        }
    }

    // 延迟导航：过渡动画入场完成后才跳转阅读页
    LaunchedEffect(pendingBookId) {
        val bookId = pendingBookId ?: return@LaunchedEffect
        delay(600) // 等入场动画完成
        if (pendingBookId != bookId || !showTransition) return@LaunchedEffect
        navController.navigate(Screen.Reader.createRoute(bookId))
        pendingBookId = null
    }

    CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled) {
    Box(modifier = Modifier.fillMaxSize()) {
        ConfigurableNavigationBack(
            predictiveBackEnabled = predictiveBackEnabled,
            bridgeEnabled = currentRoute != null && currentRoute != Screen.Home.route
        ) {
            // 主内容
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLiquidGlass) {
                            Modifier.layerBackdrop(liquidGlassBackdrop)
                        } else {
                            Modifier.haze(hazeState)
                        }
                    )
            ) {
            composable(Screen.Home.route) { backStackEntry ->
                val playEntranceAnimation = rememberPageEntrancePlayback(
                    pageKey = Screen.Home.route,
                    entryKey = backStackEntry,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                HomeScreen(
                    playEntranceAnimation = playEntranceAnimation,
                    onNavigateToReader = { bookId, coverPath, title ->
                        transitionCover = coverPath
                        transitionTitle = title
                        readerReady = false
                        showTransition = true
                        pendingBookId = bookId
                    },
                    onTabBarVisibleChange = { visible -> tabBarVisible = visible },
                    onNavigateToStatistics = {
                        selectedTab = 2
                        navController.navigate(Screen.Statistics.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToBookshelf = {
                        selectedTab = 1
                        navController.navigate(Screen.Bookshelf.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    showReadingGoalSheet = homeGoalSheetVisible,
                    onReadingGoalSheetVisibleChange = { visible -> homeGoalSheetVisible = visible },
                    renderReadingGoalSheet = false,
                    viewModel = homeViewModel
                )
            }

            composable(Screen.Bookshelf.route) { backStackEntry ->
                val playEntranceAnimation = rememberPageEntrancePlayback(
                    pageKey = Screen.Bookshelf.route,
                    entryKey = backStackEntry,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                BookshelfScreen(
                    playEntranceAnimation = playEntranceAnimation,
                    onNavigateToReader = { bookId, coverPath, title ->
                        transitionCover = coverPath
                        transitionTitle = title
                        readerReady = false
                        showTransition = true
                        pendingBookId = bookId
                    },
                    onOverlayActiveChange = { active -> tabBarVisible = !active }
                )
            }

            composable(Screen.Statistics.route) { backStackEntry ->
                val playEntranceAnimation = rememberPageEntrancePlayback(
                    pageKey = Screen.Statistics.route,
                    entryKey = backStackEntry,
                    enabled = entranceAnimationsEnabled,
                    tracker = entranceTracker
                )
                StatisticsScreen(
                    playEntranceAnimation = playEntranceAnimation
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                ReaderRouter(
                    bookId = bookId,
                    onNavigateBack = {
                        showTransition = false
                        navController.popBackStack()
                    },
                    onLoadingComplete = { readerReady = true },
                    onOpenBook = { targetBookId ->
                        navController.navigate(Screen.Reader.createRoute(targetBookId))
                    }
                )
            }

            }
        }


        // 浮动导航栏（渐隐渐显）
        AnimatedVisibility(
            visible = tabBarVisible,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingTabBar(
                selectedIndex = selectedTab,
                hazeState = hazeState,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onTabSelected = { index ->
                    selectedTab = index
                    val r = when (index) {
                        0 -> Screen.Home.route
                        1 -> Screen.Bookshelf.route
                        2 -> Screen.Statistics.route
                        else -> Screen.Home.route
                    }
                    navController.navigate(r) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 过渡动画覆盖层
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

        if (showTransition) {
            BookTransitionOverlay(
                title = transitionTitle,
                coverPath = transitionCover,
                isReady = readerReady,
                onBack = {
                    pendingBookId = null
                    readerReady = false
                    showTransition = false
                    if (navController.currentDestination?.route == Screen.Reader.route) {
                        navController.popBackStack()
                    }
                },
                onTransitionComplete = {
                    showTransition = false
                }
            )
        }
    }
    }
}

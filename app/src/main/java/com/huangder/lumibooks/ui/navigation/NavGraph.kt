package com.huangder.lumibooks.ui.navigation

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
import com.huangder.lumibooks.ui.home.HomeScreen
import com.huangder.lumibooks.ui.reader.PdfViewerScreen
import com.huangder.lumibooks.ui.reader.ReaderScreen
import com.huangder.lumibooks.ui.reader.PdfViewerScreen
import com.huangder.lumibooks.ui.reader.ReaderViewModel
import com.huangder.lumibooks.ui.statistics.StatisticsScreen
import com.huangder.lumibooks.ui.welcome.WelcomeScreen
import com.huangder.lumibooks.ui.welcome.WelcomeViewModel
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalReaderColors
import com.huangder.lumibooks.ui.theme.ReaderColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

/**
 * 根据书籍格式路由：PDF → 竖向滚动，EPUB/TXT → 横向翻页
 */
@Composable
private fun ReaderRouter(
    bookId: String,
    onNavigateBack: () -> Unit,
    onLoadingComplete: () -> Unit
) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val isPdf = uiState.book?.format?.name == "PDF"

    // 阅读页强制浅色模式 + 阅读页专用颜色
    EBookReaderTheme(darkTheme = false) {
        CompositionLocalProvider(LocalReaderColors provides ReaderColors.Light) {
            if (isPdf) {
                PdfViewerScreen(bookId = bookId, onNavigateBack = onNavigateBack, viewModel = viewModel)
                LaunchedEffect(Unit) { onLoadingComplete() }
            } else {
                ReaderScreen(bookId = bookId, onNavigateBack = onNavigateBack, onLoadingComplete = onLoadingComplete, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainNavGraph(navController: NavHostController) {
    val welcomeViewModel: WelcomeViewModel = hiltViewModel()
    val hasSeenWelcome by welcomeViewModel.hasSeenWelcome.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTransition by remember { mutableStateOf(false) }
    var transitionCover by remember { mutableStateOf<String?>(null) }
    var transitionTitle by remember { mutableStateOf("") }
    var readerReady by remember { mutableStateOf(false) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var tabBarVisible by remember { mutableStateOf(hasSeenWelcome != false) }

    // 监听路由变化，从阅读页/设置页返回时延迟显示 TabBar
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    LaunchedEffect(currentRoute, showTransition) {
        if (currentRoute == Screen.Reader.route || currentRoute == Screen.Welcome.route || showTransition) {
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
        navController.navigate(Screen.Reader.createRoute(bookId))
        pendingBookId = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容
        NavHost(
            navController = navController,
            startDestination = when (hasSeenWelcome) {
                null -> Screen.Home.route // 加载中，先显示 Home
                true -> Screen.Home.route
                false -> Screen.Welcome.route
            }
        ) {
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    onContinue = {
                        welcomeViewModel.saveHasSeenWelcome(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onExit = {
                        // 退出应用
                        val activity = (navController.context as? android.app.Activity)
                        activity?.finish()
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
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
                    }
                )
            }

            composable(Screen.Bookshelf.route) {
                BookshelfScreen(
                    onNavigateToReader = { bookId, coverPath, title ->
                        transitionCover = coverPath
                        transitionTitle = title
                        readerReady = false
                        showTransition = true
                        pendingBookId = bookId
                    }
                )
            }

            composable(Screen.Statistics.route) {
                StatisticsScreen()
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
                    onLoadingComplete = { readerReady = true }
                )
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
        if (showTransition) {
            BookTransitionOverlay(
                title = transitionTitle,
                coverPath = transitionCover,
                isReady = readerReady,
                onTransitionComplete = {
                    showTransition = false
                }
            )
        }
    }
}

package com.ebook.reader.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import com.ebook.reader.ui.bookshelf.BookshelfScreen
import com.ebook.reader.ui.components.BookTransitionOverlay
import com.ebook.reader.ui.components.FloatingTabBar
import com.ebook.reader.ui.home.HomeScreen
import com.ebook.reader.ui.reader.PdfViewerScreen
import com.ebook.reader.ui.reader.ReaderScreen
import com.ebook.reader.ui.reader.PdfViewerScreen
import com.ebook.reader.ui.reader.ReaderViewModel
import com.ebook.reader.ui.statistics.StatisticsScreen
import com.ebook.reader.domain.model.BookFormat
import com.ebook.reader.ui.theme.EBookReaderTheme
import com.ebook.reader.ui.theme.LocalReaderColors
import com.ebook.reader.ui.theme.ReaderColors
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTransition by remember { mutableStateOf(false) }
    var transitionCover by remember { mutableStateOf<String?>(null) }
    var transitionTitle by remember { mutableStateOf("") }
    var readerReady by remember { mutableStateOf(false) }
    var pendingBookId by remember { mutableStateOf<String?>(null) }
    var tabBarVisible by remember { mutableStateOf(true) }
    val contentScale = remember { Animatable(1f) }

    // 监听路由变化，从阅读页返回时延迟显示 TabBar
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    LaunchedEffect(currentRoute, showTransition) {
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
        navController.navigate(Screen.Reader.createRoute(bookId))
        pendingBookId = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容（带缩放效果，过渡动画时缩小 5%）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                }
        ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
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
                StatisticsScreen(
                    onNavigateBack = { navController.popBackStack() }
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
                    onLoadingComplete = { readerReady = true }
                )
            }
        }
        } // contentScale Box 结束

        // 浮动导航栏（延迟淡入）
        AnimatedVisibility(
            visible = tabBarVisible,
            enter = fadeIn(animationSpec = tween(400)) +
                    slideInVertically(animationSpec = tween(400)) { it / 2 },
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
                isReady = readerReady,
                contentScale = contentScale,
                onTransitionComplete = {
                    showTransition = false
                }
            )
        }
    }
}

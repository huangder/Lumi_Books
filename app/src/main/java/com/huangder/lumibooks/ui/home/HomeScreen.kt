package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.huangder.lumibooks.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookDeleteMode
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.OverscrollBounceState
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.StatusGradientOverlay
import com.huangder.lumibooks.ui.components.BookCoverProgressOverlay
import com.huangder.lumibooks.ui.components.CloudAwareBookDeleteDialog
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.cardOutline
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme
import com.huangder.lumibooks.ui.theme.SansSerif
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import com.huangder.lumibooks.ui.animation.BookCoverTitleColor
import com.huangder.lumibooks.ui.animation.BookCoverTitleStyle
import com.huangder.lumibooks.ui.animation.bookCoverMemoryCacheKey
import com.huangder.lumibooks.ui.animation.bookCoverTransitionAnchor
import com.huangder.lumibooks.ui.layout.currentAdaptiveWindowInfo
import com.huangder.lumibooks.util.TimeUtils

@Composable
fun HomeScreen(
    playEntranceAnimation: Boolean = false,
    onNavigateToReader: (book: Book, sourceBounds: Rect?) -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onImportClick: () -> Unit,
    showImportButton: Boolean = true,
    onTabBarVisibleChange: (Boolean) -> Unit = {},
    showReadingGoalSheet: Boolean = false,
    onReadingGoalSheetVisibleChange: (Boolean) -> Unit = {},
    renderReadingGoalSheet: Boolean = true,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isMaterial3 = LocalUseMaterial3Theme.current
    val useWideLayout = currentAdaptiveWindowInfo().isMediumWidthOrLarger
    val topBlurBackdrop = rememberLayerBackdrop()
    val statusBarTopPadding = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val bottomContentPadding = if (isMaterial3) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
    } else {
        120.dp
    }
    val context = LocalContext.current
    var localShowGoalSheet by remember { mutableStateOf(false) }
    val showGoalSheet = if (renderReadingGoalSheet) localShowGoalSheet else showReadingGoalSheet
    val setShowGoalSheet: (Boolean) -> Unit = { visible ->
        if (renderReadingGoalSheet) {
            localShowGoalSheet = visible
        } else {
            onReadingGoalSheetVisibleChange(visible)
        }
    }
    val booksByLastRead = remember(uiState.books) {
        uiState.books.sortedByDescending { it.lastReadTime }
    }
    val lastReadBook = booksByLastRead.firstOrNull()
    val recentBooks = booksByLastRead

    // 顶部下拉「松手继续阅读」：只读取下拉位移，判定与回弹都由 OverscrollBounce 维持原样。
    val bounceState = remember { OverscrollBounceState() }
    val pullToResumeThresholdPx = with(LocalDensity.current) {
        PULL_TO_RESUME_THRESHOLD_DP.dp.toPx()
    }
    val latestNavigateToReader = rememberUpdatedState(onNavigateToReader)
    val latestLastReadBook = rememberUpdatedState(lastReadBook)
    val onTopPullRelease: (Float) -> Unit = { releasedOffsetPx ->
        if (shouldOpenRecentBookOnRelease(releasedOffsetPx, pullToResumeThresholdPx)) {
            latestLastReadBook.value?.let { book ->
                latestNavigateToReader.value(book, null)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .semantics { testTagsAsResourceId = true }
            .testTag(HOME_SCREEN_TAG)
    ) {
        OverscrollBounce(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(topBlurBackdrop) else Modifier
                ),
            state = bounceState,
            onTopPullRelease = onTopPullRelease
        ) {
            LazyColumn(
                modifier = Modifier
                    .then(if (useWideLayout) Modifier.widthIn(max = 840.dp) else Modifier)
                    .fillMaxWidth()
                    .then(if (useWideLayout) Modifier.align(Alignment.TopCenter) else Modifier)
            ) {
                item(key = "header") {
                    Spacer(Modifier.height(statusBarTopPadding + AppSpace.md))
                    PageEntranceItem(play = playEntranceAnimation, index = 0) {
                        HomeHeader(
                            avatarUri = uiState.avatarUri,
                            onAvatarClick = {
                                context.startActivity(
                                    android.content.Intent(context, com.huangder.lumibooks.ui.settings.SettingsActivity::class.java)
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(AppSpace.lg))
                }

                if (uiState.books.isEmpty()) {
                    item(key = "empty_library") {
                        PageEntranceItem(play = playEntranceAnimation, index = 1) {
                            ImportHint(onImportClick = onImportClick)
                        }
                        Spacer(Modifier.height(AppSpace.lg))
                    }
                }

                if (lastReadBook != null) {
                    item(key = "continue_reading") {
                        PageEntranceItem(play = playEntranceAnimation, index = 2) {
                            ContinueReadingCard(
                                book = lastReadBook,
                                downloadState = uiState.downloadStates[lastReadBook.id],
                                onClick = { bounds -> onNavigateToReader(lastReadBook, bounds) },
                                onToggleFavorite = { viewModel.updateBook(lastReadBook.copy(isFavorite = !lastReadBook.isFavorite)) },
                                onDelete = { mode -> viewModel.deleteBook(lastReadBook, mode) },
                                modifier = Modifier.padding(horizontal = AppSpace.lg)
                            )
                        }
                        Spacer(Modifier.height(AppSpace.lg))
                    }
                }

                if (recentBooks.isNotEmpty()) {
                    item(key = "recently_read") {
                        PageEntranceItem(play = playEntranceAnimation, index = 3) {
                            Column {
                                SectionHeader(stringResource(R.string.section_recently_read))
                                Spacer(Modifier.height(AppSpace.md))
                                BooksReadGrid(
                                    books = recentBooks,
                                    downloadStates = uiState.downloadStates,
                                    useWideLayout = useWideLayout,
                                    onBookClick = { book, bounds ->
                                        onNavigateToReader(book, bounds)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(AppSpace.lg))
                    }
                }

                item(key = "reading_goal") {
                    PageEntranceItem(play = playEntranceAnimation, index = 4) {
                        ReadingGoalCard(
                            readingTime = uiState.todayReadingTime,
                            dailyGoal = uiState.dailyGoal,
                            weeklyData = uiState.weeklyData,
                            onCardClick = { setShowGoalSheet(true) },
                            onContinueClick = {
                                lastReadBook?.let { onNavigateToReader(it, null) }
                            }
                        )
                    }
                    Spacer(Modifier.height(AppSpace.lg))
                }

                item(key = "bottom_spacing") {
                    Spacer(Modifier.height(bottomContentPadding))
                }
            }
        } // OverscrollBounce 结束

        StatusGradientOverlay(backdrop = topBlurBackdrop.takeIf { isLiquidGlass })

        // 提示绘制在顶部渐隐之上，避免文字被窗口色渐变冲淡；无书时整个手势都不可用。
        if (lastReadBook != null) {
            HomePullToResumeHint(
                bounceState = bounceState,
                statusBarTopPadding = statusBarTopPadding
            )
        }

        if (renderReadingGoalSheet) {
            ReadingGoalSheet(
                visible = showGoalSheet,
                todayReadingTime = uiState.todayReadingTime,
                dailyGoal = uiState.dailyGoal,
                currentBook = lastReadBook,
                weeklyData = uiState.weeklyData,
                streakDays = uiState.streakDays,
                onDismiss = { setShowGoalSheet(false) },
                onSaveGoal = { minutes -> viewModel.saveDailyGoal(minutes) }
            )
        }

        if (showImportButton) {
            // 非液态主题沿用原有导入 FAB；液态按钮由导航层与 Tab 同排绘制。
            PageEntranceItem(
                play = playEntranceAnimation,
                index = 5,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(
                        if (isMaterial3) {
                            Modifier
                                .navigationBarsPadding()
                                .padding(end = 24.dp, bottom = 96.dp)
                        } else {
                            Modifier.padding(end = 24.dp, bottom = 100.dp)
                        }
                    )
            ) {
                if (isMaterial3) {
                    FloatingActionButton(
                        onClick = onImportClick,
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            AppIcons.Plus,
                            contentDescription = stringResource(R.string.import_books),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(8.dp, CircleShape, ambientColor = AppColors.Shadow)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .clickable(onClick = onImportClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Plus, stringResource(R.string.import_books), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    } // 外层 Box 结束
}

// ─── Header ──────────────────────────────────────────────────────

@Composable
private fun HomeHeader(
    avatarUri: String? = null,
    onAvatarClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg, vertical = AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_page),
            fontSize = AppType.Display,
            fontWeight = FontWeight.Bold,
            fontFamily = resolveAppFontFamily(KaiTi),
            letterSpacing = (-0.02).sp,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.weight(1f))
        // 头像（点击进入设置页）
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppColors.BgGray)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onAvatarClick() },
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = java.io.File(avatarUri),
                    contentDescription = stringResource(R.string.avatar),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = AppIcons.UserCircle,
                    contentDescription = stringResource(R.string.settings),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// ─── 空书库 ────────────────────────────────────────────────────

@Composable
private fun ImportHint(onImportClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.BgGray)
            .clickable(onClick = onImportClick)
            .padding(horizontal = AppSpace.lg, vertical = AppSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            AppIcons.Book,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = stringResource(R.string.home_manage_hint),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary,
            fontFamily = SansSerif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpace.md))
        Text(
            text = stringResource(R.string.import_books),
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Accent
        )
    }
}

// ─── 继续阅读卡片 ──────────────────────────────────────────────

@Composable
private fun ContinueReadingCard(
    book: Book,
    downloadState: BookDownloadState?,
    onClick: (Rect?) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (BookDeleteMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val liquidMenuHost = LocalLiquidGlassMenuHost.current
    val favoriteMenuLabel = if (book.isFavorite) {
        stringResource(R.string.remove_favorite)
    } else {
        stringResource(R.string.add_favorite)
    }
    val deleteMenuLabel = stringResource(R.string.delete_book)
    val coverBounds = remember { java.util.concurrent.atomic.AtomicReference(Rect.Zero) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(RoundedCornerShape(AppRadius.lg))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onClick(coverBounds.get().takeUnless { it == Rect.Zero })
            }
            .padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面（3:4 比例）
        Box(
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(0.75f)
                .onGloballyPositioned { coverBounds.set(it.boundsInRoot()) }
                .bookCoverTransitionAnchor(
                    bookId = book.id,
                    cornerRadiusDp = AppRadius.sm.value,
                    titleStyle = BookCoverTitleStyle(
                        fontSizeSp = AppType.Caption.value,
                        maxLines = 3,
                        paddingDp = AppSpace.sm.value,
                        color = BookCoverTitleColor.Primary,
                        useKaiTi = true,
                        textAlignCenter = true
                    )
                )
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AppColors.BgGray)
        ) {
            if (book.coverPath == null) {
                DefaultBookCoverTitle(
                    title = book.title,
                    fontSize = AppType.Caption,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverPath)
                        .memoryCacheKey(
                            bookCoverMemoryCacheKey(book.id, book.coverPath)
                        )
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            BookCoverProgressOverlay(
                book = book,
                downloadState = downloadState,
                compact = true
            )
        }
        Spacer(Modifier.width(AppSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontSize = AppType.Body,
                fontWeight = FontWeight.SemiBold,
                fontFamily = resolveAppFontFamily(KaiTi),
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppSpace.xs))
            Text(
                text = book.author,
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
            if (book.isMissing) {
                Text(
                    text = stringResource(R.string.book_file_unavailable),
                    fontSize = AppType.Caption,
                    color = Color(0xFFD92D3A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(AppSpace.xs))
            Text(
                text = if (downloadState is BookDownloadState.Downloading) {
                    stringResource(
                        R.string.book_download_progress,
                        (downloadState.progress * 100f).toInt().coerceIn(0, 100)
                    )
                } else {
                    stringResource(R.string.book_progress, (book.readingProgress * 100).toInt())
                },
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
        }
        Box {
            // A button instead of a bare icon, so the anchored menu has a trigger
            // control of its own to morph out of (matches the bookshelf bar).
            //
            // It deliberately runs on the material fallback: the page backdrop
            // captures this very card, and a surface that samples its own capture
            // makes the render tree reference itself (native HWUI crash). The
            // fallback still draws the glass gradient, outline and shadow, and the
            // pressed stretch/highlight keeps working; only refraction is dropped.
            CompositionLocalProvider(LocalLiquidGlassBackdrop provides null) {
                LiquidGlassIconButton(
                imageVector = AppIcons.DotsThreeVertical,
                contentDescription = stringResource(R.string.more_options),
                onClick = {
                    if (liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                        liquidMenuHost.toggle(
                            LiquidGlassMenuSpec(
                                anchorBounds = menuAnchorBounds,
                                width = 132.dp,
                                items = listOf(
                                    LiquidGlassMenuItem(
                                        label = favoriteMenuLabel,
                                        icon = AppIcons.Heart.regular,
                                        onClick = onToggleFavorite
                                    ),
                                    LiquidGlassMenuItem(
                                        label = deleteMenuLabel,
                                        icon = AppIcons.Trash,
                                        destructive = true,
                                        onClick = { showDeleteConfirm = true }
                                    )
                                )
                            )
                        )
                    } else {
                        menuExpanded = true
                    }
                },
                size = 32.dp,
                iconSize = 18.dp,
                contentColor = AppColors.TextSecondary,
                liquidContainerColor = AppColors.CardBg,
                liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
                modifier = Modifier.liquidGlassMenuAnchor().onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
            )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.width(116.dp),
                shape = RoundedCornerShape(AppRadius.xl),
                containerColor = AppColors.WindowBg,
                border = BorderStroke(1.dp, AppColors.Divider),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(AppRadius.lg))
                        .clickable {
                        menuExpanded = false
                        onToggleFavorite()
                    },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Heart.regular, null, modifier = Modifier.size(17.dp), tint = AppColors.TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (book.isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite),
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(AppRadius.lg))
                        .clickable {
                        menuExpanded = false
                        showDeleteConfirm = true
                    },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Trash, null, modifier = Modifier.size(17.dp), tint = AppColors.Accent)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete_book), fontSize = AppType.BodySmall, color = AppColors.Accent)
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        CloudAwareBookDeleteDialog(
            bookCount = 1,
            hasRemoteBooks = book.remoteFileName != null,
            onDismiss = { showDeleteConfirm = false },
            onDelete = { mode ->
                showDeleteConfirm = false
                onDelete(mode)
            }
        )
    }
}

// ─── 区块标题 ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = AppType.Section,
        fontWeight = FontWeight.Bold,
        fontFamily = resolveAppFontFamily(KaiTi),
        color = AppColors.TextPrimary,
        modifier = Modifier.padding(horizontal = AppSpace.lg)
    )
}

// ─── 最近阅读卡片 ──────────────────────────────────────────────

@Composable
private fun RecentBookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fixedWidth: Boolean = true
) {
    Row(
        modifier = modifier
            .then(if (fixedWidth) Modifier.width(260.dp) else Modifier)
            .shadow(10.dp, RoundedCornerShape(AppRadius.md), ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(RoundedCornerShape(AppRadius.md))
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp, 74.dp)
                .bookCoverTransitionAnchor(
                    bookId = book.id,
                    cornerRadiusDp = 6f,
                    titleStyle = BookCoverTitleStyle(
                        fontSizeSp = 10f,
                        maxLines = 3,
                        paddingDp = AppSpace.sm.value,
                        color = BookCoverTitleColor.Primary,
                        useKaiTi = true,
                        textAlignCenter = true
                    )
                )
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.BgGray),
            contentAlignment = Alignment.Center
        ) {
            if (book.coverPath == null) {
                DefaultBookCoverTitle(
                    title = book.title,
                    fontSize = 10.sp
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverPath)
                        .memoryCacheKey(
                            bookCoverMemoryCacheKey(book.id, book.coverPath)
                        )
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(AppSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
            Text(
                text = "${(book.readingProgress * 100).toInt()}%",
                fontSize = AppType.Caption,
                color = AppColors.Accent
            )
        }
    }
}

// ─── 阅读目标卡片 ──────────────────────────────────────────────

@Composable
private fun ReadingGoalCard(
    readingTime: Long,
    dailyGoal: Int,
    weeklyData: List<DailyReading> = emptyList(),
    onCardClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val hasGoal = dailyGoal > 0
    val goalMs = if (hasGoal) dailyGoal * 60 * 1000L else 0L
    val progress = if (hasGoal) (readingTime.toFloat() / goalMs).coerceIn(0f, 1f) else 0f
    val hasReadToday = readingTime > 0L
    val todayStatusText = stringResource(
        if (hasReadToday) R.string.today_read_status_done else R.string.today_read_status_empty
    )
    val todayEmoji = if (hasReadToday) "📖 ✨ 🌿" else "🌙 ☕ 📚"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(RoundedCornerShape(AppRadius.lg))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onCardClick)
            .padding(AppSpace.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 半圆弧进度条
        if (hasGoal) {
        ArcProgressBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )

        Spacer(Modifier.height(AppSpace.sm))

        // 大数字
        Text(
            text = TimeUtils.formatDurationShort(readingTime),
            fontSize = AppType.Huge,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )
        Text(
            text = stringResource(R.string.goal_label, dailyGoal),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
        } else {
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                text = todayEmoji,
                fontSize = 34.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                text = todayStatusText,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(
                    if (hasReadToday) R.string.today_read_message_done else R.string.today_read_message_empty
                ),
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
        }

        Spacer(Modifier.height(AppSpace.lg))

        // 继续阅读按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(AppRadius.capsule))
                .background(Color.Black)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onContinueClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.continue_reading),
                fontSize = AppType.Body,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(AppSpace.lg))

        // 星期打卡
        WeeklyCheckIn(weeklyData = weeklyData, dailyGoal = dailyGoal)
    }
}

// ─── 半圆弧进度条 ──────────────────────────────────────────────

@Composable
private fun ArcProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val accentColor = AppColors.Accent
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val radius = (minOf(size.width, size.height * 2) - stroke) / 2
        val diameter = radius * 2
        val cx = size.width / 2
        // 圆心下移，让半圆弧更靠底部
        val cy = size.height + radius * 0.15f
        val topLeft = Offset(cx - radius, cy - radius)

        drawArc(
            color = Color(0xFFE5E5EA),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = accentColor,
            startAngle = 180f,
            sweepAngle = 180f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

// ─── 星期打卡 ──────────────────────────────────────────────────

@Composable
private fun WeeklyCheckIn(weeklyData: List<DailyReading> = emptyList(), dailyGoal: Int = 30) {
    // weeklyData 是固定日历周 [日, 一, 二, 三, 四, 五, 六]
    // todayIndex 基于今天是星期几
    val todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1  // 0=周日
    val hasGoal = dailyGoal > 0
    val goalMs = dailyGoal * 60 * 1000L
    val accentColor = AppColors.Accent

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weeklyData.forEachIndexed { index, data ->
            val isPast = index < todayIndex
            val isToday = index == todayIndex
            val isFuture = index > todayIndex
            val goalMet = if (hasGoal) data.duration >= goalMs else data.duration > 0L

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPast || isToday) {
                        if (goalMet || (hasGoal && isToday)) {
                            // 达标或今天：实心圆
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isToday) accentColor else accentColor.copy(alpha = 0.8f))
                            )
                        } else {
                            // 未达标：空心圆
                            Canvas(modifier = Modifier.size(30.dp)) {
                                val stroke = 1.5.dp.toPx()
                                val r = (size.minDimension - stroke) / 2
                                drawCircle(
                                    color = accentColor.copy(alpha = 0.4f),
                                    radius = r,
                                    style = Stroke(width = stroke)
                                )
                            }
                        }
                    }
                    Text(
                        text = data.dayLabel,
                        fontSize = 11.sp,
                        color = when {
                            isToday -> Color.White
                            goalMet && (isPast || !hasGoal) -> Color.White
                            isPast -> AppColors.TextSecondary
                            else -> AppColors.TextSecondary
                        },
                        fontWeight = if ((goalMet && (isPast || !hasGoal)) || (hasGoal && isToday)) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ─── 最近读过的图书 ────────────────────────────────────────────

@Composable
private fun BooksReadGrid(
    books: List<Book>,
    downloadStates: Map<String, BookDownloadState>,
    modifier: Modifier = Modifier,
    useWideLayout: Boolean = false,
    onBookClick: (Book, Rect?) -> Unit
) {
    val coverShape = RoundedCornerShape(
        if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.md
    )
    val coverRadiusDp = if (LocalAppTheme.current == "liquid_glass") {
        16f
    } else {
        AppRadius.md.value
    }
    val bookBounds = remember { mutableMapOf<String, Rect>() }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        // Keep the first card aligned with the sections above while allowing
        // the final card to scroll all the way to the screen edge.
        contentPadding = PaddingValues(start = AppSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        items(books, key = { it.id }) { book ->
            Box(
                modifier = Modifier
                    .width(if (useWideLayout) 120.dp else 96.dp)
                    .aspectRatio(0.75f)
                    .bookCoverTransitionAnchor(
                        bookId = book.id,
                        cornerRadiusDp = coverRadiusDp,
                        titleStyle = BookCoverTitleStyle(
                            fontSizeSp = AppType.BodySmall.value,
                            maxLines = 3,
                            paddingDp = AppSpace.sm.value,
                            color = BookCoverTitleColor.Primary,
                            useKaiTi = true,
                            textAlignCenter = true
                        )
                    )
                    .shadow(
                        elevation = 8.dp,
                        shape = coverShape,
                        ambientColor = AppColors.CardShadow,
                        spotColor = AppColors.CardShadow
                    )
                    .clip(coverShape)
                    .background(AppColors.BgGray)
                    .border(1.dp, AppColors.Divider, coverShape)
                    .cardPressEffect()
                    .onGloballyPositioned { coordinates ->
                        bookBounds[book.id] = coordinates.boundsInRoot()
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onBookClick(book, bookBounds[book.id]) },
                contentAlignment = Alignment.Center
            ) {
                if (book.coverPath == null) {
                    DefaultBookCoverTitle(
                        title = book.title,
                        fontSize = AppType.BodySmall
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverPath)
                            .memoryCacheKey(
                                bookCoverMemoryCacheKey(book.id, book.coverPath)
                            )
                            .build(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                BookCoverProgressOverlay(
                    book = book,
                    downloadState = downloadStates[book.id],
                    compact = true
                )
            }
        }
    }
}

private const val HOME_SCREEN_TAG = "home_screen"

@Composable
private fun DefaultBookCoverTitle(
    title: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier.padding(AppSpace.sm),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = resolveAppFontFamily(KaiTi),
        color = AppColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

package com.huangder.lumibooks.ui.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import com.huangder.lumibooks.ui.components.StatusGradientOverlay
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.FileUtils
import androidx.compose.ui.res.stringResource
import java.io.File
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    playEntranceAnimation: Boolean = false,
    onNavigateToReader: (bookId: String, coverPath: String?, title: String) -> Unit,
    onOverlayProgressChange: (Float) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val contextMenuState = rememberBookContextMenuState()
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val bookshelfBackdrop = rememberLayerBackdrop()

    val filterTabs = listOf(
        stringResource(R.string.filter_all),
        stringResource(R.string.filter_downloaded),
        stringResource(R.string.format_pdf),
        stringResource(R.string.filter_favorites)
    )

    LaunchedEffect(uiState.importMessage) {
        uiState.importMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearImportMessage()
        }
    }

    // 通知 NavGraph 隐藏/显示底部 TabBar
    // 编辑书本信息对话框状态
    var showEditDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<Book?>(null) }

    // 自定义封面：记录正在操作的书本
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }

    // 删除动画：记录正在删除的书本 ID
    var deletingBookId by remember { mutableStateOf<String?>(null) }

    // 图片选择器（自定义封面）
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val book = coverTargetBook ?: return@let
            val newCoverPath = FileUtils.copyCoverImage(context, it, book.id)
            if (newCoverPath != null) {
                viewModel.updateBook(book.copy(coverPath = newCoverPath))
            }
            coverTargetBook = null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(context, it) }
    }

    val filteredBooks = when (selectedFilter) {
        1 -> uiState.books // 下载内容
        2 -> uiState.books.filter { it.format == BookFormat.PDF }
        3 -> uiState.books.filter { it.isFavorite }
        else -> uiState.books
    }

    val overlayProgress = contextMenuState.scrimAlpha.value
    SideEffect {
        onOverlayProgressChange(overlayProgress)
    }
    DisposableEffect(Unit) {
        onDispose { onOverlayProgressChange(0f) }
    }

    ProvideLiquidGlassBackdrop(bookshelfBackdrop.takeIf { isLiquidGlass }) {
    Box(modifier = Modifier.fillMaxSize().background(AppColors.WindowBg)) {
        // ── 内容层（高斯模糊） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(bookshelfBackdrop) else Modifier
                )
                .graphicsLayer {
                    renderEffect = if (
                        overlayProgress > 0.01f && android.os.Build.VERSION.SDK_INT >= 31
                    ) {
                        android.graphics.RenderEffect.createBlurEffect(
                            20f * overlayProgress,
                            20f * overlayProgress,
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
                .background(AppColors.WindowBg)
        ) {
            OverscrollBounce(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(AppSpace.md))
                    PageEntranceItem(
                        play = playEntranceAnimation,
                        index = 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.bookshelf_title),
                            fontSize = AppType.Display,
                            fontWeight = FontWeight.Bold,
                            fontFamily = KaiTi,
                            letterSpacing = (-0.02).sp,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
                        )
                    }

                    // ── 筛选标签（可横向滚动） ──
                    PageEntranceItem(
                        play = playEntranceAnimation,
                        index = 1,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                            horizontalArrangement = Arrangement.spacedBy(AppSpace.lg)
                        ) {
                            filterTabs.forEachIndexed { index, label ->
                                val isSelected = index == selectedFilter
                                Text(
                                    text = label,
                                    fontSize = AppType.Body,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                                    modifier = Modifier.clickable { selectedFilter = index }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(AppSpace.md))

                    // ── 书架网格 ──
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = AppSpace.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(filteredBooks, key = { _, book -> book.id }) { index, book ->
                            PageEntranceItem(play = playEntranceAnimation, index = index + 2) {
                                AnimatedBookGridItem(
                                    book = book,
                                    isDeleting = deletingBookId == book.id,
                                    onDeleteFinished = {
                                        viewModel.deleteBook(book)
                                        deletingBookId = null
                                    },
                                    contextMenuState = contextMenuState,
                                    onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onClick = { onNavigateToReader(book.id, book.coverPath, book.title) }
                                )
                            }
                        }
                        // 添加按钮
                        item(key = "add_book") {
                            PageEntranceItem(
                                play = playEntranceAnimation,
                                index = filteredBooks.size + 2
                            ) {
                                AddBookItem(onClick = { launcher.launch("*/*") })
                            }
                        }
                    }
                } // Column 结束
            } // OverscrollBounce 结束
        } // 内容层 Box 结束（renderEffect 模糊作用于此）

        // ── 长按上下文菜单覆盖层（在内容层之外，不被模糊） ──
        BookContextMenuOverlay(
            state = contextMenuState,
            onDelete = { book -> deletingBookId = book.id },
            onFavorite = { book -> viewModel.updateBook(book.copy(isFavorite = !book.isFavorite)) },
            onCustomCover = { book ->
                coverTargetBook = book
                coverPickerLauncher.launch("image/*")
            },
            onRemoveCustomCover = { book ->
                FileUtils.deleteCustomCover(context, book.id)
                viewModel.reExtractCover(context, book)
            },
            onBookmarksNotes = { book ->
                val intent = android.content.Intent(context, BookNotesActivity::class.java)
                intent.putExtra("bookId", book.id)
                context.startActivity(intent)
            },
            onEditInfo = { book ->
                editingBook = book
                showEditDialog = true
            }
        )

        // ── 编辑书本信息对话框（卡片风格） ──
        if (showEditDialog && editingBook != null) {
            androidx.compose.material3.BasicAlertDialog(
                onDismissRequest = { showEditDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { showEditDialog = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpace.lg)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {} // 拦截点击，防止穿透关闭
                            )
                    ) {
                        com.huangder.lumibooks.ui.components.EditInputDialog(
                            title = stringResource(R.string.edit_book_info),
                            fields = listOf(
                                Triple(stringResource(R.string.book_title_label), "显示原始书名", editingBook!!.title),
                                Triple(stringResource(R.string.book_author_label), "显示原始作者", editingBook!!.author)
                            ),
                            onBack = { showEditDialog = false },
                            onConfirm = { values ->
                                viewModel.updateBook(editingBook!!.copy(title = values[0], author = values[1]))
                                showEditDialog = false
                            }
                        )
                    }
                }
            }
        }

    }
    }
}

// ─── 带删除动画的书籍网格项 ──────────────────────────────────────

@Composable
private fun AnimatedBookGridItem(
    book: Book,
    isDeleting: Boolean,
    onDeleteFinished: () -> Unit,
    contextMenuState: BookContextMenuState,
    onHaptic: () -> Unit,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isDeleting) 0.8f else 1f,
        animationSpec = tween(300, easing = AppEasing.Accelerate),
        label = "deleteScale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(300, easing = AppEasing.Accelerate),
        label = "deleteAlpha"
    )

    // 等动画完成后执行实际删除（delay 期间动画持续播放）
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            kotlinx.coroutines.delay(350)
            onDeleteFinished()
        }
    }

    // 在组合期间读取值 → graphicsLayer 拿到最新值
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = alphaAnim
            }
    ) {
        BookGridItem(
            book = book,
            contextMenuState = contextMenuState,
            onHaptic = onHaptic,
            onClick = onClick
        )
    }
}

// ─── 书籍网格项 ────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: Book,
    contextMenuState: BookContextMenuState,
    onHaptic: () -> Unit,
    onClick: () -> Unit
) {
    val coverCorner = if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.sm
    // 是否为当前操作的目标书本（在组合期间读取，确保触发重组）
    val isTarget = contextMenuState.selectedBook?.id == book.id
    val isOverlayActive = contextMenuState.phase != ContextMenuPhase.Idle && isTarget
    // 在组合期间读取 pressScale → 值变化时触发重组 → graphicsLayer 拿到最新值
    val pressScaleValue = if (isTarget) contextMenuState.pressScale.value else 1f
    val overlayAlpha = if (isOverlayActive) contextMenuState.itemAlpha.value else 1f

    // 只保存坐标引用，避免滚动时把每帧变化的窗口坐标写入 Compose State。
    val coverCoordinates = remember {
        arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1)
    }

    // 监听 combinedClickable 的按下/抬起事件，驱动封面缩小动画
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> isPressed = true
                is androidx.compose.foundation.interaction.PressInteraction.Release -> isPressed = false
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> isPressed = false
            }
        }
    }
    // 按下时缩小到 0.95，否则用 contextMenuState 的 pressScale
    val coverScale = if (isPressed) 0.95f else pressScaleValue

    Column(
        modifier = Modifier
            .graphicsLayer { alpha = overlayAlpha }
            .combinedClickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = {
                    onHaptic()
                    contextMenuState.onLongPressConfirmed(
                        book = book,
                        bounds = coverCoordinates[0]?.boundsInRoot()
                            ?: androidx.compose.ui.geometry.Rect.Zero,
                        onHaptic = onHaptic
                    )
                }
            )
    ) {
        // 封面（3:4 比例）— 按下缩小 + overlay 状态控制
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .onGloballyPositioned { coordinates ->
                    coverCoordinates[0] = coordinates
                }
                .graphicsLayer {
                    scaleX = coverScale
                    scaleY = coverScale
                }
                .shadow(12.dp, RoundedCornerShape(coverCorner), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
                .clip(RoundedCornerShape(coverCorner))
                .background(AppColors.BgGray)
        ) {
            if (book.coverPath != null) {
                val imgContext = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(imgContext)
                        .data(book.coverPath)
                        .memoryCacheKey("${book.id}_${book.coverPath}") // book.id 区分同名书，coverPath 变化时刷新
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 无封面：灰色背景 + 书名
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.BgGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            // 进度指示
            if (book.readingProgress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${(book.readingProgress * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
                // 底部进度条
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.readingProgress)
                            .height(3.dp)
                            .background(AppColors.Accent)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppSpace.sm))

        // 书名（收藏时加心形）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.title,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (book.isFavorite) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.favorite),
                    tint = AppColors.Accent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // 作者
        Text(
            text = book.author,
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── 添加书籍 ──────────────────────────────────────────────────

@Composable
private fun AddBookItem(onClick: () -> Unit) {
    val coverCorner = if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.sm
    Column(
        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(coverCorner))
                .background(AppColors.WindowBg),
            contentAlignment = Alignment.Center
        ) {
            // 虚线边框（用 Canvas 绘制）
            val dividerColor = AppColors.Divider
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pw = 2.dp.toPx()
                val r = coverCorner.toPx()
                val dashW = 8.dp.toPx()
                val dashGap = 6.dp.toPx()
                drawRoundRect(
                    color = dividerColor,
                    cornerRadius = CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = pw,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashW, dashGap))
                    )
                )
            }
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.import_books),
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = stringResource(R.string.import_book_label),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
    }
}

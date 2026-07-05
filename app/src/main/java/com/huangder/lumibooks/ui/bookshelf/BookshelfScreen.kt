package com.huangder.lumibooks.ui.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.components.StatusGradientOverlay
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.util.FileUtils
import java.io.File

private val filterTabs = listOf("全部图书", "下载内容", "PDF", "收藏")

@Composable
fun BookshelfScreen(
    onNavigateToReader: (bookId: String, coverPath: String?, title: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val contextMenuState = rememberBookContextMenuState()

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
            val coverPath = FileUtils.copyCoverImage(context, it, book.id)
            coverPath?.let { path ->
                viewModel.updateBook(book.copy(coverPath = path))
            }
            coverTargetBook = null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = FileUtils.getFileNameFromUri(context, it) ?: "unknown.epub"
            val extension = FileUtils.getFileExtension(fileName)
            if (extension in listOf("epub", "pdf", "txt")) {
                val file = FileUtils.copyFileToInternal(context, it, fileName)
                file?.let { bookFile ->
                    val format = when (extension) {
                        "epub" -> BookFormat.EPUB
                        "pdf" -> BookFormat.PDF
                        else -> BookFormat.TXT
                    }
                    val coverPath = try {
                        val parser = BookParserFactory.createParser(format, context)
                        val content = parser.parse(bookFile.absolutePath)
                        content.coverPath
                    } catch (_: Exception) { null }

                    val book = Book(
                        id = FileUtils.generateBookId(),
                        title = fileName.substringBeforeLast('.'),
                        author = "未知作者",
                        filePath = bookFile.absolutePath,
                        coverPath = coverPath,
                        format = format,
                        lastReadTime = System.currentTimeMillis(),
                        readingProgress = 0f,
                        createdAt = System.currentTimeMillis()
                    )
                    viewModel.insertBook(book)
                }
            }
        }
    }

    val filteredBooks = when (selectedFilter) {
        1 -> uiState.books // 下载内容
        2 -> uiState.books.filter { it.format == BookFormat.PDF }
        3 -> uiState.books.filter { it.isFavorite }
        else -> uiState.books
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.WindowBg)) {
        // ── 内容层（长按时整体高斯模糊） ──
        val blurRadius = (20 * contextMenuState.scrimAlpha.value).dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            OverscrollBounce(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(AppSpace.md))
                    Text(
                        text = "书库",
                        fontSize = AppType.Display,
                        fontWeight = FontWeight.Bold,
                        fontFamily = KaiTi,
                        letterSpacing = (-0.02).sp,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
                    )

                    // ── 筛选标签 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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

                    Spacer(Modifier.height(AppSpace.md))

                    // ── 书架网格 ──
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = AppSpace.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredBooks, key = { it.id }) { book ->
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
                        // 添加按钮
                        item {
                            AddBookItem(onClick = { launcher.launch("*/*") })
                        }
                    }
                } // Column 结束
            } // OverscrollBounce 结束

            StatusGradientOverlay()
        } // 内容层 Box 结束

        // ── 长按上下文菜单覆盖层（在模糊内容之上） ──
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
                // 重新从书本文件提取原始封面
                val originalCoverPath = try {
                    val parser = BookParserFactory.createParser(book.format, context)
                    val content = parser.parse(book.filePath)
                    content.coverPath
                } catch (_: Exception) { null }
                viewModel.updateBook(book.copy(coverPath = originalCoverPath))
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

        // ── 编辑书本信息对话框 ──
        if (showEditDialog && editingBook != null) {
            EditBookInfoDialog(
                book = editingBook!!,
                onDismiss = { showEditDialog = false },
                onConfirm = { updatedBook ->
                    viewModel.updateBook(updatedBook)
                    showEditDialog = false
                }
            )
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
    val deleteTransition = updateTransition(targetState = isDeleting, label = "delete")

    val itemScale by deleteTransition.animateFloat(
        transitionSpec = { tween(300, easing = AppEasing.Accelerate) },
        label = "scale"
    ) { deleting -> if (deleting) 0.8f else 1f }

    val itemAlpha by deleteTransition.animateFloat(
        transitionSpec = { tween(300, easing = AppEasing.Accelerate) },
        label = "alpha"
    ) { deleting -> if (deleting) 0f else 1f }

    // 动画结束后执行实际删除
    LaunchedEffect(deleteTransition.currentState, deleteTransition.isRunning) {
        if (!deleteTransition.isRunning && deleteTransition.currentState && isDeleting) {
            onDeleteFinished()
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
                alpha = itemAlpha
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

@Composable
private fun BookGridItem(
    book: Book,
    contextMenuState: BookContextMenuState,
    onHaptic: () -> Unit,
    onClick: () -> Unit
) {
    // 是否为当前操作的目标书本
    val isTarget = contextMenuState.selectedBook?.id == book.id
    val isOverlayActive = contextMenuState.phase != ContextMenuPhase.Idle && isTarget

    Column(
        modifier = Modifier
            .graphicsLayer {
                // alpha：使用 itemAlpha 实现平滑过渡（dismiss 时渐显）
                alpha = if (isOverlayActive) contextMenuState.itemAlpha.value else 1f
                // 按下缩小效果：仅目标书缩小
                if (isTarget) {
                    scaleX = contextMenuState.pressScale.value
                    scaleY = contextMenuState.pressScale.value
                }
            }
            .longPressBookEffect(
                state = contextMenuState,
                book = { book },
                onClick = onClick,
                onCoverBounds = { bounds -> contextMenuState.updateCoverBounds(bounds) },
                onHaptic = onHaptic
            )
    ) {
        // 封面（3:4 比例）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .onGloballyPositioned { coordinates ->
                    // 缓存原始大小的 bounds（Idle 阶段 = 未缩放）
                    if (contextMenuState.phase == ContextMenuPhase.Idle) {
                        contextMenuState.updateCoverBounds(coordinates.boundsInWindow())
                    }
                }
                .shadow(12.dp, RoundedCornerShape(AppRadius.sm), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AppColors.BgGray)
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = book.coverPath,
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
                    contentDescription = "收藏",
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
    Column(
        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AppColors.WindowBg),
            contentAlignment = Alignment.Center
        ) {
            // 虚线边框（用 Canvas 绘制）
            val dividerColor = AppColors.Divider
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pw = 2.dp.toPx()
                val r = AppRadius.sm.toPx()
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
                contentDescription = "添加书籍",
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = "导入图书",
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
    }
}

// ─── 编辑书本信息对话框 ──────────────────────────────────────────

@Composable
private fun EditBookInfoDialog(
    book: Book,
    onDismiss: () -> Unit,
    onConfirm: (Book) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑书本信息",
                fontWeight = FontWeight.Bold,
                fontFamily = KaiTi
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(AppSpace.md))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Text(
                text = "保存",
                color = AppColors.Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    onConfirm(book.copy(title = title, author = author))
                }
            )
        },
        dismissButton = {
            Text(
                text = "取消",
                color = AppColors.TextSecondary,
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }
    )
}

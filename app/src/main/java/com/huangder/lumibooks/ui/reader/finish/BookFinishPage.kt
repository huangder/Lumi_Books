package com.huangder.lumibooks.ui.reader.finish

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.bookshare.BookShareActivity
import com.huangder.lumibooks.ui.bookshelf.BookNotesActivity
import com.huangder.lumibooks.ui.bookshelf.BookReviewActivity
import com.huangder.lumibooks.ui.bookshelf.ReviewRatingBar
import com.huangder.lumibooks.ui.bookshelf.ReviewStar
import com.huangder.lumibooks.ui.bookshelf.compactReadingDuration
import com.huangder.lumibooks.ui.bookshelf.reviewRatingLabel
import com.huangder.lumibooks.ui.bookshelf.formatReviewScore
import com.huangder.lumibooks.ui.bookshelf.reviewStarFillColor
import com.huangder.lumibooks.ui.components.AppDialogAction
import com.huangder.lumibooks.ui.components.AppDialogActionRole
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.ControlIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.Backdrop
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Where the finish page comes from, matching the reader's page-turn direction. */
enum class BookFinishEntrance { FROM_RIGHT, FROM_LEFT, FROM_BOTTOM, FADE }

/** Reader-theme colors so the finish page reads as the book's last leaf, not an app screen. */
data class BookFinishColors(val paper: Color, val ink: Color)

/** Navigation actions that belong to the reader rather than to the finish page. */
class BookFinishActions(
    val onBackToLastPage: () -> Unit,
    val onRestart: () -> Unit,
    val onExitReader: () -> Unit
)

/**
 * Full-screen finish page for paged readers. It slides in from the page-turn direction,
 * can be swiped back the same way, and hides the reader underneath once it fully covers it.
 */
@Composable
fun BookFinishOverlay(
    bookId: String,
    entrance: BookFinishEntrance,
    colors: BookFinishColors,
    actions: BookFinishActions,
    dialogBackdrop: Backdrop?,
    pull: BookFinishPullState? = null,
    dismissRequest: Int = 0,
    onCoveringChanged: (Boolean) -> Unit
) {
    val viewModel: BookFinishViewModel = hiltViewModel(key = "book_finish_$bookId")
    LaunchedEffect(bookId) { viewModel.bind(bookId) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    // Opened by a finger on the last page: follow it instead of auto-playing the entrance.
    val followsFinger = remember { pull?.dragging == true && entrance != BookFinishEntrance.FADE }
    val progress = remember { Animatable(if (motionEnabled || followsFinger) 0f else 1f) }
    val dragOffset = remember { Animatable(0f) }
    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var dismissing by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    val latestCovering by rememberUpdatedState(onCoveringChanged)
    val latestActions by rememberUpdatedState(actions)
    val horizontal = entrance == BookFinishEntrance.FROM_RIGHT || entrance == BookFinishEntrance.FROM_LEFT
    // +1 means "towards where the page came from"; dragging that way returns to the book.
    val dismissSign = when (entrance) {
        BookFinishEntrance.FROM_LEFT -> -1f
        else -> 1f
    }
    fun extentPx(): Float = if (horizontal) sizePx.width.toFloat() else sizePx.height.toFloat()

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        settled = false
        latestCovering(false)
        scope.launch {
            if (motionEnabled) {
                val extent = extentPx()
                val start = if (extent > 0f) progress.value - abs(dragOffset.value) / extent else progress.value
                progress.snapTo(start.coerceIn(0f, 1f))
                dragOffset.snapTo(0f)
                progress.animateTo(0f, tween(PAGE_SETTLE_MS, easing = FastOutSlowInEasing))
            }
            latestActions.onBackToLastPage()
        }
    }

    suspend fun settleOpen() {
        if (progress.value < 1f) {
            val remaining = 1f - progress.value
            progress.animateTo(
                1f,
                tween((PAGE_SETTLE_MS * remaining).toInt().coerceAtLeast(120), easing = FastOutSlowInEasing)
            )
        }
        settled = true
        latestCovering(true)
        viewModel.onFinishPageShown()
    }

    if (followsFinger && pull != null) {
        // Track the finger 1:1, exactly like a page being dragged.
        LaunchedEffect(pull) {
            snapshotFlow { pull.distancePx }.collect { distance ->
                val extent = extentPx()
                if (pull.dragging && extent > 0f && !dismissing) {
                    progress.snapTo((distance / extent).coerceIn(0f, 1f))
                }
            }
        }
        val openedSerial = remember { pull.releaseSerial }
        LaunchedEffect(pull.releaseSerial) {
            if (pull.releaseSerial == openedSerial) return@LaunchedEffect
            val flingPx = with(density) { 400.dp.toPx() }
            val flingForward = pull.releaseVelocity > flingPx
            val flingBack = pull.releaseVelocity < -flingPx
            val commit = !pull.releaseCancelled && !flingBack && (flingForward || progress.value > 0.25f)
            if (commit) {
                settleOpen()
            } else {
                dismissing = true
                progress.animateTo(
                    0f,
                    tween((PAGE_SETTLE_MS * progress.value).toInt().coerceAtLeast(80), easing = FastOutSlowInEasing)
                )
                latestActions.onBackToLastPage()
            }
        }
    } else {
        LaunchedEffect(Unit) { settleOpen() }
    }
    BackHandler(enabled = !dismissing) { dismiss() }
    // Requests made before this page opened belong to an earlier visit.
    val openedDismissRequest = remember { dismissRequest }
    LaunchedEffect(dismissRequest) { if (dismissRequest != openedDismissRequest) dismiss() }

    val nestedDismiss = remember(entrance) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (entrance != BookFinishEntrance.FROM_BOTTOM || source != NestedScrollSource.UserInput) return Offset.Zero
                // Only the overscroll left after the content reached its top pulls the page down.
                val next = (dragOffset.value + available.y).coerceAtLeast(0f)
                if (next == dragOffset.value) return Offset.Zero
                scope.launch { dragOffset.snapTo(next) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (entrance != BookFinishEntrance.FROM_BOTTOM || dragOffset.value <= 0f) return Velocity.Zero
                if (dragOffset.value > sizePx.height * 0.25f || available.y > 1800f) dismiss()
                else dragOffset.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                return available
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        val next = dragOffset.value + delta
        val clamped = if (dismissSign > 0) next.coerceAtLeast(0f) else next.coerceAtMost(0f)
        scope.launch { dragOffset.snapTo(clamped) }
    }
    val shadowWidthPx = with(density) { EDGE_SHADOW_WIDTH.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it }
            .graphicsLayer {
                val p = progress.value
                when (entrance) {
                    BookFinishEntrance.FROM_RIGHT -> translationX = (1f - p) * size.width + dragOffset.value
                    BookFinishEntrance.FROM_LEFT -> translationX = -(1f - p) * size.width + dragOffset.value
                    BookFinishEntrance.FROM_BOTTOM -> translationY = (1f - p) * size.height + dragOffset.value
                    BookFinishEntrance.FADE -> alpha = p
                }
            }
            .drawBehind {
                if (entrance == BookFinishEntrance.FADE) return@drawBehind
                val extent = if (horizontal) size.width else size.height
                val shown = if (extent > 0f) progress.value - abs(dragOffset.value) / extent else 1f
                // Feather grows with the first stretch of travel and fades over the last stretch,
                // so it never pops in at the start or vanishes at the end of either direction.
                val fraction = shown.coerceIn(0f, 1f)
                val strength = minOf(fraction / 0.15f, (1f - fraction) / 0.2f).coerceIn(0f, 1f)
                if (strength <= 0f) return@drawBehind
                val shade = Color.Black.copy(alpha = EDGE_SHADOW_ALPHA * strength)
                when (entrance) {
                    BookFinishEntrance.FROM_RIGHT -> drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, shade), startX = -shadowWidthPx, endX = 0f),
                        topLeft = Offset(-shadowWidthPx, 0f),
                        size = Size(shadowWidthPx, size.height)
                    )
                    BookFinishEntrance.FROM_LEFT -> drawRect(
                        Brush.horizontalGradient(
                            listOf(shade, Color.Transparent),
                            startX = size.width,
                            endX = size.width + shadowWidthPx
                        ),
                        topLeft = Offset(size.width, 0f),
                        size = Size(shadowWidthPx, size.height)
                    )
                    BookFinishEntrance.FROM_BOTTOM -> drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, shade), startY = -shadowWidthPx, endY = 0f),
                        topLeft = Offset(0f, -shadowWidthPx),
                        size = Size(size.width, shadowWidthPx)
                    )
                    BookFinishEntrance.FADE -> Unit
                }
            }
            .background(colors.paper)
            .then(
                if (horizontal) Modifier.draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !dismissing && settled,
                    onDragStopped = { velocity ->
                        val distance = abs(dragOffset.value)
                        if (distance > sizePx.width * 0.25f || velocity * dismissSign > 1200f) dismiss()
                        else dragOffset.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                    }
                ) else Modifier.nestedScroll(nestedDismiss)
            )
            // Taps on empty space must not fall through to the reader's menu toggle.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        BookFinishContent(
            viewModel = viewModel,
            colors = colors,
            actions = actions,
            onBackToLastPage = ::dismiss,
            dialogBackdrop = dialogBackdrop,
            celebrateWhen = settled,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

/** Matches the reader's page settle duration so the finish page feels like one more leaf. */
private const val PAGE_SETTLE_MS = 300
private const val EDGE_SHADOW_ALPHA = 0.10f
private val EDGE_SHADOW_WIDTH = 40.dp

/** Finish page embedded as the final item of continuous scrolling. */
@Composable
fun BookFinishInlinePage(
    bookId: String,
    colors: BookFinishColors,
    actions: BookFinishActions,
    dialogBackdrop: Backdrop?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val viewModel: BookFinishViewModel = hiltViewModel(key = "book_finish_$bookId")
    LaunchedEffect(bookId) { viewModel.bind(bookId) }
    LaunchedEffect(visible) { if (visible) viewModel.onFinishPageShown() }
    Box(modifier.pointerInput(Unit) { detectTapGestures { } }) {
        BookFinishContent(
            viewModel = viewModel,
            colors = colors,
            actions = actions,
            onBackToLastPage = null,
            dialogBackdrop = dialogBackdrop,
            celebrateWhen = visible
        )
    }
}

@Composable
private fun BookFinishContent(
    viewModel: BookFinishViewModel,
    colors: BookFinishColors,
    actions: BookFinishActions,
    onBackToLastPage: (() -> Unit)?,
    dialogBackdrop: Backdrop?,
    celebrateWhen: Boolean,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val eInk = LocalEInkMode.current
    val motionEnabled = LocalMotionEnabled.current && !eInk
    // Each value starts one show; 0 means no fireworks on screen.
    var fireworksSeed by remember { mutableIntStateOf(0) }
    var showArchiveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(celebrateWhen) {
        if (!celebrateWhen || !motionEnabled || !fireworksAllowed(context)) return@LaunchedEffect
        if (viewModel.claimCelebration()) fireworksSeed = (System.nanoTime() and 0x7fffffff).toInt() or 1
    }
    val replay: () -> Unit = {
        if (motionEnabled) fireworksSeed = (System.nanoTime() and 0x7fffffff).toInt() or 1
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        val book = state.book
        val summary: @Composable ColumnScope.() -> Unit = {
            FinishHeader(state, colors, onReplay = replay)
            Spacer(Modifier.height(20.dp))
            FinishStats(state, colors)
        }
        val controls: @Composable ColumnScope.() -> Unit = {
            FinishRating(state, colors, onRatingChange = viewModel::setRating)
            Spacer(Modifier.height(14.dp))
            FinishReadFlag(state, colors, onSetRead = viewModel::setMarkedRead)
            Spacer(Modifier.height(16.dp))
            FinishActionGrid(
                colors = colors,
                hasReview = state.review.isNotBlank(),
                onReview = {
                    book?.let { BookReviewActivity.start(context, it.id, it.title, it.author, it.coverPath) }
                },
                onShare = { book?.let { BookShareActivity.start(context, it.id) } },
                onNotes = {
                    book?.let {
                        context.startActivity(
                            Intent(context, BookNotesActivity::class.java)
                                .putExtra(BookNotesActivity.EXTRA_BOOK_ID, it.id)
                        )
                    }
                },
                onArchive = { showArchiveConfirm = true }
            )
            Spacer(Modifier.height(18.dp))
            FinishNavigationRow(colors, onBackToLastPage, actions)
        }
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = summary
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = controls
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    Modifier.widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    summary()
                    Spacer(Modifier.height(24.dp))
                    controls()
                }
            }
        }
        if (fireworksSeed != 0) {
            FireworksOverlay(
                seed = fireworksSeed,
                modifier = Modifier.fillMaxSize(),
                onFinished = { fireworksSeed = 0 }
            )
        }
    }

    if (showArchiveConfirm) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            backdrop = dialogBackdrop,
            titleText = stringResource(R.string.book_finish_archive_title),
            text = { Text(stringResource(R.string.book_finish_archive_body), color = AppColors.TextSecondary, lineHeight = 22.sp) },
            actions = listOf(
                AppDialogAction(
                    text = stringResource(R.string.book_finish_archive),
                    onClick = {
                        showArchiveConfirm = false
                        viewModel.archive(onDone = actions.onExitReader)
                    },
                    role = AppDialogActionRole.Primary,
                    tintedColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent
                ),
                AppDialogAction(
                    text = stringResource(R.string.cancel),
                    onClick = { showArchiveConfirm = false },
                    role = AppDialogActionRole.Cancel,
                    contentColor = AppColors.TextPrimary
                )
            )
        )
    }
}

@Composable
private fun FinishHeader(state: BookFinishUiState, colors: BookFinishColors, onReplay: () -> Unit) {
    val book = state.book
    val replayLabel = stringResource(R.string.book_finish_replay)
    Box(
        Modifier
            .width(112.dp)
            .height(160.dp)
            .shadow(10.dp, RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.ink.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (book?.coverPath != null) {
            AsyncImage(
                model = book.coverPath,
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = book?.title?.take(1).orEmpty(),
                color = colors.ink.copy(alpha = 0.6f),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.book_finish_title),
            color = colors.ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClick = onReplay)
                .semantics { contentDescription = replayLabel },
            contentAlignment = Alignment.Center
        ) {
            ReviewStar(
                fill = 1f,
                emptyColor = Color.Transparent,
                fillColor = reviewStarFillColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    if (book != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.book_finish_subtitle, book.title),
            color = colors.ink.copy(alpha = 0.78f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = book.author,
                color = colors.ink.copy(alpha = 0.55f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FinishStats(state: BookFinishUiState, colors: BookFinishColors) {
    val span = remember(state.firstDate, state.lastDate) { formatSpan(state.firstDate, state.lastDate) }
    val unitColor = colors.ink.copy(alpha = 0.55f)
    val cells = listOf(
        stringResource(R.string.book_detail_reading_time) to compactReadingDuration(state.totalDurationMs, unitColor),
        stringResource(R.string.book_detail_active_days) to AnnotatedString(state.activeDays.toString()),
        stringResource(R.string.book_detail_notes) to AnnotatedString(state.noteCount.toString()),
        stringResource(R.string.book_detail_bookmarks) to AnnotatedString(state.bookmarkCount.toString())
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.ink.copy(alpha = 0.05f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            cells.forEach { (label, value) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, color = colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(label, color = colors.ink.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (span != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.book_finish_span) + "  " +
                    if (span.second == null) span.first
                    else stringResource(R.string.book_finish_span_value, span.first, span.second!!),
                color = colors.ink.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FinishRating(state: BookFinishUiState, colors: BookFinishColors, onRatingChange: (Float) -> Unit) {
    Text(stringResource(R.string.book_finish_rate), color = colors.ink.copy(alpha = 0.7f), fontSize = 13.sp)
    ReviewRatingBar(rating = state.rating, onRatingChange = onRatingChange)
    val label = reviewRatingLabel(state.rating)
    Text(
        text = label?.let { "${formatReviewScore(state.rating)} · $it" } ?: " ",
        color = colors.ink.copy(alpha = 0.6f),
        fontSize = 12.sp
    )
}

@Composable
private fun FinishReadFlag(
    state: BookFinishUiState,
    colors: BookFinishColors,
    onSetRead: (Boolean) -> Unit
) {
    if (state.book == null) return
    if (state.isMarkedRead) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.ink.copy(alpha = 0.06f))
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AppIcons.CheckCircle, null, tint = colors.ink.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.book_finish_marked), color = colors.ink.copy(alpha = 0.8f), fontSize = 13.sp)
            if (state.markedByFinishPage) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.book_finish_undo),
                    color = colors.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button) { onSetRead(false) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    } else {
        FinishPill(
            icon = AppIcons.CheckCircle,
            text = stringResource(R.string.book_mark_read),
            colors = colors,
            onClick = { onSetRead(true) }
        )
    }
}

@Composable
private fun FinishActionGrid(
    colors: BookFinishColors,
    hasReview: Boolean,
    onReview: () -> Unit,
    onShare: () -> Unit,
    onNotes: () -> Unit,
    onArchive: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinishActionTile(
                AppIcons.PencilSimple,
                stringResource(if (hasReview) R.string.book_finish_edit_review else R.string.book_finish_write_review),
                colors, onReview, Modifier.weight(1f)
            )
            FinishActionTile(AppIcons.ShareNetwork, stringResource(R.string.book_finish_share), colors, onShare, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinishActionTile(AppIcons.FileText, stringResource(R.string.book_finish_notes), colors, onNotes, Modifier.weight(1f))
            FinishActionTile(AppIcons.Archive, stringResource(R.string.book_finish_archive), colors, onArchive, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FinishActionTile(
    icon: ImageVector,
    text: String,
    colors: BookFinishColors,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.ink.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlIcon(icon, null, Modifier.size(18.dp), tint = colors.ink.copy(alpha = 0.85f))
        Spacer(Modifier.width(10.dp))
        Text(text, color = colors.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FinishPill(icon: ImageVector, text: String, colors: BookFinishColors, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.ink.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlIcon(icon, null, Modifier.size(16.dp), tint = colors.ink.copy(alpha = 0.85f))
        Spacer(Modifier.width(6.dp))
        Text(text, color = colors.ink, fontSize = 13.sp)
    }
}

@Composable
private fun FinishNavigationRow(
    colors: BookFinishColors,
    onBackToLastPage: (() -> Unit)?,
    actions: BookFinishActions
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackToLastPage != null) {
            FinishTextButton(stringResource(R.string.book_finish_back_last), colors, onBackToLastPage)
            FinishDot(colors)
        }
        FinishTextButton(stringResource(R.string.book_finish_restart), colors, actions.onRestart)
        FinishDot(colors)
        FinishTextButton(stringResource(R.string.book_finish_back_shelf), colors, actions.onExitReader)
    }
}

@Composable
private fun FinishTextButton(text: String, colors: BookFinishColors, onClick: () -> Unit) {
    Text(
        text = text,
        color = colors.ink.copy(alpha = 0.72f),
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    )
}

@Composable
private fun FinishDot(colors: BookFinishColors) {
    Text("·", color = colors.ink.copy(alpha = 0.35f), fontSize = 13.sp)
}

/** First and last active day as localized dates; second is null when both are the same day. */
private fun formatSpan(first: String?, last: String?): Pair<String, String?>? {
    if (first == null || last == null) return null
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = runCatching { LocalDate.parse(first).format(formatter) }.getOrNull() ?: return null
    val end = runCatching { LocalDate.parse(last).format(formatter) }.getOrNull() ?: return null
    return if (first == last) start to null else start to end
}

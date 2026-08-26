package com.huangder.lumibooks.ui.reader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.systemGestureExclusion
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import android.text.Selection
import android.text.SpanWatcher
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import com.huangder.lumibooks.ui.navigation.Screen
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.EditInputDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.LiquidGlassSheetContainer
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.ReaderSystemBarStyle
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.ui.reader.engine.ReaderImageHit
import com.huangder.lumibooks.ui.reader.engine.ReaderHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.ReaderSearchHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.TtsHighlightRange
import com.huangder.lumibooks.ui.reader.engine.TtsSentenceHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.WaveUnderlineSpan
import com.huangder.lumibooks.ui.reader.engine.RoundedHighlightTextView
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.ReaderPageDirection
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.util.DownloadedFonts
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.util.parser.TxtEncoding
import com.huangder.lumibooks.tts.TtsPageContent
import com.huangder.lumibooks.tts.TtsPageLocation
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.kyant.backdrop.Backdrop
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import coil.load
import android.text.Spanned
import androidx.compose.ui.layout.ContentScale

private data class ReaderLinkLocation(
    val chapterIndex: Int,
    val pageIndex: Int
)

private data class ReaderMenuSnapshot(
    val chapterIndex: Int,
    val chapterTitle: String,
    val pageIndex: Int,
    val pageCount: Int,
    val bookProgressPercent: Float,
    val rightPageIndex: Int? = null
)

private fun formatReaderPageLabel(
    currentPage: Int,
    rightPageIndex: Int?,
    chapterPageCount: Int
): String {
    val total = chapterPageCount.coerceAtLeast(1)
    return if (rightPageIndex != null && rightPageIndex >= 0) {
        "$currentPage–${rightPageIndex + 1} / $total"
    } else {
        "$currentPage / $total"
    }
}

private data class ContinuousSearchHighlight(
    val chapterIndex: Int,
    val start: Int,
    val end: Int
)

private data class ContinuousTextSelection(
    val start: Int,
    val end: Int,
    val selectedText: String,
    val startX: Float,
    val endX: Float,
    val topY: Float,
    val bottomY: Float
)

/** Canvas 引擎注释气泡状态：注释正文 + 锚点在窗口中的坐标（像素）。 */
private data class ReaderFootnoteBubble(
    val text: String,
    val anchorWindowX: Float,
    val anchorWindowY: Float
)

internal enum class AnnotationColorTarget(val noteType: String) {
    HIGHLIGHT("highlight"),
    UNDERLINE("underline")
}

private class ContinuousSelectionController {
    var activeView: ContinuousSelectableTextView? = null

    fun clear() {
        activeView?.clearReaderSelection()
        activeView = null
    }
}

private class ContinuousSelectableTextView(context: Context) : RoundedHighlightTextView(context) {
    var onReaderTap: (() -> Unit)? = null
    var onLinkTap: ((String, Float, Float) -> Unit)? = null
    var onImageLongPress: ((ReaderImageHit) -> Unit)? = null
    var onSelectionChanging: (() -> Unit)? = null
    var onReaderSelection: ((ContinuousTextSelection) -> Unit)? = null

    private var sourceText: CharSequence? = null
    private var replacingText = false
    private var lastTapX = 0f
    private var lastTapY = 0f
    private val selectionDispatch = Runnable { dispatchReaderSelection() }

    init {
        includeFontPadding = false
        gravity = android.view.Gravity.TOP
        setTextIsSelectable(true)
        highlightColor = 0x40007AFF
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
        }
        setOnClickListener {
            val spannable = text as? Spannable
            val start = spannable?.let(Selection::getSelectionStart) ?: -1
            val end = spannable?.let(Selection::getSelectionEnd) ?: -1
            if (start < 0 || end <= start) {
                val image = readerImageAt(lastTapX, lastTapY)
                when {
                    image?.link != null -> onLinkTap?.invoke(image.link, lastTapX, lastTapY)
                    image?.hasAction == true -> Unit
                    image != null -> Unit
                    else -> readerLinkAt(lastTapX, lastTapY)
                        ?.let { onLinkTap?.invoke(it, lastTapX, lastTapY) }
                        ?: onReaderTap?.invoke()
                }
            }
        }
        setOnLongClickListener {
            val image = readerImageAt(lastTapX, lastTapY)
            if (image != null && image.link == null && !image.hasAction && image.source.isNotBlank()) {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                clearReaderSelection()
                onImageLongPress?.invoke(image)
                true
            } else {
                false
            }
        }
        customSelectionActionModeCallback = hiddenSelectionToolbarCallback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            customInsertionActionModeCallback = hiddenSelectionToolbarCallback()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE ||
            event.actionMasked == MotionEvent.ACTION_UP
        ) {
            lastTapX = event.x
            lastTapY = event.y
        }
        return super.onTouchEvent(event)
    }

    private fun readerLinkAt(x: Float, y: Float): String? {
        val spannable = text as? Spannable ?: return null
        val textLayout = layout ?: return null
        if (spannable.isEmpty()) return null
        val localX = x - totalPaddingLeft + scrollX
        val localY = y - totalPaddingTop + scrollY
        if (localX < 0f || localY < 0f || localY >= textLayout.height) return null
        val line = textLayout.getLineForVertical(localY.toInt())
        val offset = textLayout.getOffsetForHorizontal(line, localX)
            .coerceIn(0, spannable.length - 1)
        return spannable.getSpans(offset, (offset + 1).coerceAtMost(spannable.length), URLSpan::class.java)
            .firstOrNull()?.url
    }

    private fun readerImageAt(x: Float, y: Float): ReaderImageHit? {
        val spannable = text as? Spannable ?: return null
        val textLayout = layout ?: return null
        val localY = y - totalPaddingTop + scrollY
        if (localY < 0f || localY >= textLayout.height) return null
        val line = textLayout.getLineForVertical(localY.toInt())
        val lineStart = textLayout.getLineStart(line)
        val lineEnd = textLayout.getLineEnd(line)
        val images = spannable.getSpans(lineStart, lineEnd, ImageSpan::class.java)
        if (images.isEmpty()) return null
        val location = IntArray(2)
        getLocationOnScreen(location)
        for (image in images) {
            val spanStart = spannable.getSpanStart(image).coerceAtLeast(0)
            val spanEnd = spannable.getSpanEnd(image).coerceAtLeast(spanStart + 1)
            val drawable = image.drawable
            val width = drawable.bounds.width().toFloat().coerceAtLeast(1f)
            val height = drawable.bounds.height().toFloat().coerceAtLeast(1f)
            val left = totalPaddingLeft + textLayout.getPrimaryHorizontal(spanStart) - scrollX
            val bottom = (totalPaddingTop + textLayout.getLineBottom(line) - scrollY).toFloat()
            val top = bottom - height
            if (x in left..(left + width) && y in top..bottom) {
                val url = spannable.getSpans(spanStart, spanEnd, URLSpan::class.java)
                    .firstOrNull()?.url
                val hasAction = spannable.getSpans(spanStart, spanEnd, ClickableSpan::class.java)
                    .isNotEmpty()
                return ReaderImageHit(
                    source = image.source.orEmpty(),
                    leftPx = location[0].toFloat() + left,
                    topPx = location[1].toFloat() + top,
                    rightPx = location[0].toFloat() + left + width,
                    bottomPx = location[1].toFloat() + bottom,
                    naturalWidth = drawable.intrinsicWidth.coerceAtLeast(drawable.bounds.width()),
                    naturalHeight = drawable.intrinsicHeight.coerceAtLeast(drawable.bounds.height()),
                    link = url,
                    hasAction = hasAction
                )
            }
        }
        return null
    }

    fun setReaderText(value: CharSequence) {
        if (sourceText === value) return
        sourceText = value
        replacingText = true
        setText(value, TextView.BufferType.SPANNABLE)
        replacingText = false
    }

    fun clearReaderSelection() {
        removeCallbacks(selectionDispatch)
        (text as? Spannable)?.let(Selection::removeSelection)
        clearFocus()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (replacingText || selStart < 0 || selEnd <= selStart) return
        removeCallbacks(selectionDispatch)
        onSelectionChanging?.invoke()
        postDelayed(selectionDispatch, 240L)
    }

    private fun dispatchReaderSelection() {
        val spannable = text as? Spannable ?: return
        val rawStart = Selection.getSelectionStart(spannable)
        val rawEnd = Selection.getSelectionEnd(spannable)
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        if (start < 0 || end <= start || end > spannable.length) return
        val textLayout = layout ?: return
        val endOffset = (end - 1).coerceAtLeast(start)
        val startLine = textLayout.getLineForOffset(start)
        val endLine = textLayout.getLineForOffset(endOffset)
        val location = IntArray(2)
        getLocationOnScreen(location)
        val originX = location[0] + totalPaddingLeft
        val originY = location[1] + totalPaddingTop
        onReaderSelection?.invoke(
            ContinuousTextSelection(
                start = start,
                end = end,
                selectedText = spannable.subSequence(start, end).toString(),
                startX = originX + textLayout.getPrimaryHorizontal(start),
                endX = originX + textLayout.getPrimaryHorizontal(end),
                topY = (originY + textLayout.getLineTop(startLine)).toFloat(),
                bottomY = (originY + textLayout.getLineBottom(endLine)).toFloat()
            )
        )
    }

    private fun hiddenSelectionToolbarCallback() = object : android.view.ActionMode.Callback {
        override fun onCreateActionMode(
            mode: android.view.ActionMode?,
            menu: android.view.Menu?
        ): Boolean {
            menu?.clear()
            mode?.hide(Long.MAX_VALUE)
            post {
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
            }
            return true
        }

        override fun onPrepareActionMode(
            mode: android.view.ActionMode?,
            menu: android.view.Menu?
        ): Boolean {
            menu?.clear()
            mode?.hide(Long.MAX_VALUE)
            post {
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
            }
            return true
        }

        override fun onActionItemClicked(
            mode: android.view.ActionMode?,
            item: android.view.MenuItem?
        ): Boolean = false

        override fun onDestroyActionMode(mode: android.view.ActionMode?) = Unit
    }
}

/**
 * 深色化自定义纯色背景：保留色相/饱和度，把亮度压到 ≤ 0.22，
 * 使自定义纯色主题在深色模式下有统一的深色观感。
 */
private fun darkenReaderSolidColor(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] = minOf(hsl[2], 0.22f)
    return ColorUtils.HSLToColor(hsl)
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReaderScreen(bookId: String, onNavigateBack: () -> Unit, onPageReady: () -> Unit = {}, onLoadingComplete: () -> Unit = {}, viewModel: ReaderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val eInkMode = uiState.eInkModeEnabled
    val motionEnabled = LocalMotionEnabled.current
    val basePageTransition = if (eInkMode) "none" else uiState.pageTransition
    val effectiveReaderTheme = if (eInkMode) "day" else uiState.readerTheme
    val effectiveReaderBackgroundSelection = if (eInkMode) "day" else uiState.readerBackgroundSelection
    val effectivePreserveEpubBackground = if (eInkMode) false else uiState.preserveEpubBackground
    val effectiveBionicReadingEnabled = if (eInkMode) false else uiState.bionicReadingEnabled
    val effectiveReaderTextColor = if (eInkMode) 0xFF111111.toInt() else uiState.readerTextColor
    val selectedReaderBackgroundForTheme = uiState.customReaderBackgrounds.firstOrNull {
        it.selectionKey == effectiveReaderBackgroundSelection
    }
    val appIsDark = LocalIsDarkTheme.current
    val nightDisplay = if (eInkMode) false else when (uiState.readerDisplayMode) {
        "day" -> false
        "night" -> true
        else -> appIsDark
    }
    val renderingTheme = if (nightDisplay && selectedReaderBackgroundForTheme?.type != ReaderBackgroundType.IMAGE) {
        when (effectiveReaderTheme) {
            "day" -> "night"
            "sepia" -> "sepia_dark"
            "green" -> "green_dark"
            else -> effectiveReaderTheme
        }
    } else {
        effectiveReaderTheme
    }
    val notes by viewModel.notes.collectAsState()
    val readerNotes by viewModel.readerNotes.collectAsState()
    val activeHighlightPalette = ReaderHighlightPalette
    val renderedNotes = remember(notes, activeHighlightPalette) {
        notes.map { note -> note.copy(color = resolveReaderHighlightColor(note.color)) }
    }
    val renderedReaderNotes = remember(readerNotes, activeHighlightPalette) {
        readerNotes.map { note -> note.copy(color = resolveReaderHighlightColor(note.color)) }
    }
    val bookmarks by viewModel.bookmarks.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPhone = configuration.smallestScreenWidthDp < 600
    val isTablet = !isPhone
    val density = LocalDensity.current
    val readerScreenWidthPx = with(density) {
        configuration.screenWidthDp.dp.toPx().toInt()
    }

    // ReadView 引用
    val readViewRef = remember { mutableStateOf<ReadView?>(null) }
    // Canvas 引擎注释气泡 + 根布局在窗口中的位置（用于把窗口坐标换算为气泡偏移）
    var footnoteBubble by remember { mutableStateOf<ReaderFootnoteBubble?>(null) }
    // 实际渲染的气泡：目标为 null 时先播放退出动画再移除
    var renderedFootnote by remember { mutableStateOf<ReaderFootnoteBubble?>(null) }
    val footnoteProgress = remember { Animatable(0f) }
    val readerRootWindowPosition = remember { mutableStateOf(Offset.Zero) }
    val readerRootSize = remember { mutableStateOf(IntSize.Zero) }
    val continuousScrollRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val continuousSelectionController = remember { ContinuousSelectionController() }
    val isEpub = uiState.book?.format?.name == "EPUB"
    val supportsBookLayout = isEpub || uiState.book?.format?.name == "MOBI"
    val isBookLayout = supportsBookLayout && uiState.renderMode == EpubRenderMode.BOOK_LAYOUT
    val isVerticalWriting = uiState.readerWritingMode == ReaderWritingMode.VERTICAL_RL &&
        uiState.useNewEngine && !isBookLayout
    // 只判断“是否允许双页”（设备/设置/模式），实际是否启用由 ReadView 按自身宽高（横屏）决定
    val twoPageSpreadEligible = uiState.twoPageSpreadEnabled && isTablet &&
        uiState.useNewEngine && !isBookLayout && !eInkMode &&
        uiState.readerWritingMode == ReaderWritingMode.HORIZONTAL &&
        basePageTransition != "continuous"
    val isBookLayoutContinuousScroll = isBookLayout &&
        uiState.readerWritingMode.usesContinuousScroll(basePageTransition, eInkMode)
    val effectivePageTransition = if (isBookLayout &&
        basePageTransition == "continuous" &&
        !isBookLayoutContinuousScroll
    ) {
        "slide"
    } else if (isVerticalWriting) {
        uiState.readerWritingMode.effectivePageTransition(basePageTransition)
    } else {
        basePageTransition
    }
    val isContinuousScrollMode = !isBookLayout && uiState.useNewEngine &&
        uiState.readerWritingMode.usesContinuousScroll(basePageTransition, eInkMode)

    val toggleBookmarkForCurrentPage: () -> Unit = {
        val chapterIndex = uiState.currentChapterIndex
        val pageIndex = uiState.currentPageIndex
        val characterOffset = if (isContinuousScrollMode) {
            0
        } else {
            readViewRef.value?.getCurrentPageStartCharacterOffset()
        }
        val existing = bookmarks.firstOrNull { bookmark ->
            bookmark.chapterIndex == chapterIndex &&
                (bookmark.characterOffset == characterOffset ||
                    (bookmark.characterOffset == null && bookmark.position.toInt() == pageIndex))
        }
        if (existing != null) {
            viewModel.deleteBookmark(existing)
            Toast.makeText(context, R.string.bookmark_removed_toast, Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addBookmark(
                characterOffset = characterOffset,
                title = readViewRef.value?.getCurrentPageBookmarkTitle()
            )
            Toast.makeText(context, R.string.bookmark_added_toast, Toast.LENGTH_SHORT).show()
        }
    }
    // 鍒嗛〉妯″紡涓嬶細褰撳墠鍙ュ彞鍙樺寲鏃堕噸鏂板簲鐢ㄩ珮浜?
    // 鍒嗛〉妯″紡涓嬶細褰撳墠鍙ュ彞鍙樺寲鏃堕噸鏂板簲鐢ㄩ珮浜?
    LaunchedEffect(uiState.ttsCurrentSentence) {
        if (!isContinuousScrollMode) {
            readViewRef.value?.refreshCurrentPage()
        }
    }

    var renderedContinuousScrollMode by remember(bookId) {
        mutableStateOf(isContinuousScrollMode)
    }
    val readerModeTransitionProgress = remember(bookId) { Animatable(1f) }
    var lastPagedChapter by remember(bookId) { mutableIntStateOf(uiState.currentChapterIndex) }
    var lastPagedPage by remember(bookId) { mutableIntStateOf(uiState.currentPageIndex) }
    var lastPagedTransition by remember(bookId) {
        mutableStateOf(effectivePageTransition.takeUnless { it == "continuous" } ?: "slide")
    }
    SideEffect {
        if (uiState.useNewEngine && !isBookLayout && !isContinuousScrollMode) {
            lastPagedChapter = uiState.currentChapterIndex
            lastPagedPage = uiState.currentPageIndex
            if (effectivePageTransition != "continuous") {
                lastPagedTransition = effectivePageTransition
            }
        }
    }
    LaunchedEffect(isContinuousScrollMode, eInkMode, uiState.useNewEngine, isBookLayout) {
        if (renderedContinuousScrollMode == isContinuousScrollMode) {
            readerModeTransitionProgress.snapTo(1f)
            return@LaunchedEffect
        }
        if (eInkMode || !uiState.useNewEngine || isBookLayout) {
            renderedContinuousScrollMode = isContinuousScrollMode
            readerModeTransitionProgress.snapTo(1f)
            return@LaunchedEffect
        }

        // Fade and shrink the outgoing reader completely before swapping engines. Keeping only one
        // Android-backed reader composed at a time avoids stale callbacks and texture overlap.
        readerModeTransitionProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
        )
        renderedContinuousScrollMode = isContinuousScrollMode
        // Give the incoming reader one frame to attach and measure while it is still transparent.
        withFrameNanos { }
        readerModeTransitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        )
    }
    var epubPageTextProvider by remember(bookId) {
        mutableStateOf<(suspend (Int, Int) -> EpubPageText?)?>(null)
    }
    var epubPageTurnHandler by remember(bookId) { mutableStateOf<((Int) -> Boolean)?>(null) }
    var epubPageRequest by remember(bookId) { mutableStateOf<EpubPageRequest?>(null) }
    var epubPageRequestToken by remember(bookId) { mutableIntStateOf(0) }
    var epubSearchRequest by remember(bookId) { mutableStateOf<EpubSearchRequest?>(null) }
    var epubSearchRequestToken by remember(bookId) { mutableIntStateOf(0) }
    var epubLocatorRequest by remember(bookId) { mutableStateOf<EpubLocatorRequest?>(null) }
    var epubLocatorRequestToken by remember(bookId) { mutableIntStateOf(0) }
    var pendingExternalLink by remember(bookId) { mutableStateOf<String?>(null) }
    var epubSelectionClearToken by remember(bookId) { mutableIntStateOf(0) }
    var readerImagePreview by remember(bookId) { mutableStateOf<EpubImagePreviewRequest?>(null) }
    val readerImagePreviewProgress = remember(bookId) { Animatable(0f) }
    var readerImagePreviewJob by remember(bookId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val showReaderImagePreview: (EpubImagePreviewRequest) -> Unit = { request ->
        viewModel.hideMenu()
        readerImagePreviewJob?.cancel()
        readerImagePreview = request
        readerImagePreviewJob = scope.launch {
            readerImagePreviewProgress.snapTo(0f)
            if (eInkMode) {
                readerImagePreviewProgress.snapTo(1f)
            } else {
                readerImagePreviewProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                )
            }
        }
    }
    val dismissReaderImagePreview: () -> Unit = {
        readerImagePreviewJob?.cancel()
        readerImagePreviewJob = scope.launch {
            if (eInkMode) {
                readerImagePreviewProgress.snapTo(0f)
            } else {
                readerImagePreviewProgress.animateTo(
                    0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
            readerImagePreview = null
        }
    }
    val clearActiveTextSelection = {
        when {
            isBookLayout -> epubSelectionClearToken++
            isContinuousScrollMode -> continuousSelectionController.clear()
            else -> readViewRef.value?.curPageView?.clearSelection()
        }
    }
    val jumpToContinuousChapter: (Int) -> Unit = { chapterIndex ->
        val target = chapterIndex.coerceIn(0, (uiState.chapterCount - 1).coerceAtLeast(0))
        viewModel.setChapter(target)
        continuousScrollRequests.tryEmit(target)
    }

    LaunchedEffect(isContinuousScrollMode) {
        if (isContinuousScrollMode) {
            // AndroidView is detached in this mode. Do not route later jumps to its stale instance.
            readViewRef.value = null
        }
    }

    val startTtsFromCurrentPage: () -> Unit = {
        if (isBookLayout) {
            val webProvider = epubPageTextProvider
            if (webProvider == null) {
                Toast.makeText(context, R.string.tts_page_not_ready, Toast.LENGTH_SHORT).show()
            } else {
                val chapterCount = uiState.chapterCount
                viewModel.startTts { requestedChapter, requestedPage ->
                    val webPage = webProvider(requestedChapter, requestedPage)
                    if (webPage != null) {
                        TtsPageContent(
                            location = TtsPageLocation(webPage.chapterIndex, webPage.pageIndex),
                            text = webPage.text,
                            previous = when {
                                webPage.pageIndex > 0 -> TtsPageLocation(webPage.chapterIndex, webPage.pageIndex - 1)
                                webPage.chapterIndex > 0 -> TtsPageLocation(webPage.chapterIndex - 1, 0)
                                else -> null
                            },
                            next = when {
                                webPage.pageIndex + 1 < webPage.pageCount ->
                                    TtsPageLocation(webPage.chapterIndex, webPage.pageIndex + 1)
                                webPage.chapterIndex + 1 < chapterCount ->
                                    TtsPageLocation(webPage.chapterIndex + 1, 0)
                                else -> null
                            }
                        )
                    } else {
                        val chapterText = viewModel.getChapterText(requestedChapter)
                            ?.toString()
                            ?.replace('\uFFFC', ' ')
                            ?.trim()
                            ?: return@startTts null
                        TtsPageContent(
                            location = TtsPageLocation(requestedChapter, 0),
                            text = chapterText,
                            previous = (requestedChapter - 1).takeIf { it >= 0 }
                                ?.let { TtsPageLocation(it, 0) },
                            next = (requestedChapter + 1).takeIf { it < chapterCount }
                                ?.let { TtsPageLocation(it, 0) }
                        )
                    }
                }
            }
        } else {
            val readView = readViewRef.value
            if (readView == null) {
                Toast.makeText(context, R.string.tts_page_not_ready, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.startTts(readView::getTtsPageContent)
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 通知权限被拒绝时，Android 仍允许前台媒体播放，只是不展示普通通知。
        startTtsFromCurrentPage()
    }
    val requestTtsStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }.onFailure {
                startTtsFromCurrentPage()
            }
        } else {
            startTtsFromCurrentPage()
        }
    }

    LaunchedEffect(bookId, uiState.ttsActiveBookId, uiState.ttsPlaybackState) {
        viewModel.ttsPageTurnRequests.collect { request ->
            if (request.bookId != bookId ||
                uiState.ttsActiveBookId != bookId ||
                uiState.ttsPlaybackState == TtsPlaybackState.IDLE
            ) return@collect
            if (isBookLayout) {
                epubLocatorRequest = null
                epubPageRequestToken++
                epubPageRequest = EpubPageRequest(
                    token = epubPageRequestToken,
                    chapterIndex = request.location.chapterIndex,
                    pageIndex = request.location.pageIndex
                )
                if (request.location.chapterIndex != uiState.currentChapterIndex) {
                    viewModel.setChapter(request.location.chapterIndex)
                }
                return@collect
            }
            var readView = readViewRef.value
            repeat(60) {
                if (readView != null) return@repeat
                kotlinx.coroutines.delay(16L)
                readView = readViewRef.value
            }
            val activeReadView = readView ?: return@collect
            val current = activeReadView.getCurrentLocation()
            val target = request.location.chapterIndex to request.location.pageIndex
            if (current == target) return@collect

            if (eInkMode) {
                activeReadView.jumpToChapter(target.first, target.second)
            } else {
                val movedWithAnimation = when (target) {
                    activeReadView.getNextPageLocation() -> activeReadView.turnToNextPage()
                    activeReadView.getPrevPageLocation() -> activeReadView.turnToPreviousPage()
                    else -> false
                }
                if (!movedWithAnimation) {
                    activeReadView.jumpToChapter(target.first, target.second)
                }
            }
        }
    }

    // MainActivity 引用（用于注册 ActionMode 拦截回调）
    val activity = context as? MainActivity

    // 手机阅读页始终保持竖屏；离开阅读页后恢复进入前的方向策略。
    DisposableEffect(activity, isPhone) {
        if (activity == null || !isPhone) {
            return@DisposableEffect onDispose { }
        }

        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    // TOC 跳转标记（区分用户点击 TOC 和正常翻页带来的章节变化）

    // 亮度控制：保存系统原始亮度，退出时恢复
    val window = (context as? android.app.Activity)?.window
    val savedBrightness = remember { mutableFloatStateOf(-1f) }
    val originalSystemScreenTimeoutMs = remember(context) {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            60_000
        )
    }
    var screenSleepApplyToken by remember { mutableIntStateOf(0) }
    var hasRequestedWriteSettings by remember { mutableStateOf(false) }
    val screenSleepTimeoutSecondsState = rememberUpdatedState(uiState.screenSleepTimeoutSeconds)
    val restoreSystemScreenTimeout: () -> Unit = {
        if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    originalSystemScreenTimeoutMs
                )
            }
        }
    }
    val applySelectedSystemScreenTimeout: () -> Unit = {
        val seconds = screenSleepTimeoutSecondsState.value
        if (Settings.System.canWrite(context)) {
            val timeoutMs = if (seconds == DataStoreManager.SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM) {
                originalSystemScreenTimeoutMs
            } else {
                seconds * 1_000
            }
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    timeoutMs
                )
            }
        }
    }
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        screenSleepApplyToken += 1
        if (!Settings.System.canWrite(context) &&
            screenSleepTimeoutSecondsState.value != DataStoreManager.SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM
        ) {
            Toast.makeText(
                context,
                R.string.screen_sleep_timeout_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val requestWriteSettingsPermission: () -> Unit = {
        hasRequestedWriteSettings = true
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        runCatching {
            writeSettingsLauncher.launch(intent)
        }.onFailure {
            Toast.makeText(
                context,
                R.string.screen_sleep_timeout_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }
    LaunchedEffect(uiState.screenSleepTimeoutSeconds) {
        if (uiState.screenSleepTimeoutSeconds != DataStoreManager.SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM &&
            !Settings.System.canWrite(context) &&
            !hasRequestedWriteSettings
        ) {
            requestWriteSettingsPermission()
        }
    }

    DisposableEffect(Unit) {
        activity?.isInReaderScreen = true
        // 保存系统原始亮度
        savedBrightness.floatValue = window?.attributes?.screenBrightness ?: -1f
        onDispose {
            activity?.isInReaderScreen = false
            window?.decorView?.keepScreenOn = false
            restoreSystemScreenTimeout()
            readViewRef.value?.preloadForExit()  // 退出前预缓存当前章节 layout，供重入直接命中
            viewModel.saveAndPause()
            viewModel.clearError()
            // 恢复系统亮度
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = savedBrightness.floatValue
                w.attributes = attrs
            }
        }
    }

    // 自定义时长通过系统 SCREEN_OFF_TIMEOUT 实现真熄屏；离开阅读页时恢复原值。
    LaunchedEffect(window, uiState.screenSleepTimeoutSeconds, screenSleepApplyToken) {
        window?.decorView?.keepScreenOn = false
        applySelectedSystemScreenTimeout()
    }

    SideEffect {
        window?.let { w ->
            val targetBrightness = uiState.brightness
            val attrs = w.attributes
            attrs.screenBrightness = if (targetBrightness < 0f) {
                savedBrightness.floatValue  // 跟随系统
            } else {
                targetBrightness.coerceIn(0.01f, 1f)  // 自定义亮度，最低 1% 防全黑
            }
            w.attributes = attrs
        }
    }

    // 生命周期感知：进入后台暂停计时，回到前台恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    restoreSystemScreenTimeout()
                    viewModel.onAppBackgrounded()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onAppForegrounded()
                    screenSleepApplyToken += 1
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 监听 loading 状态，完成后通知 NavGraph 关闭过渡页
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) onLoadingComplete()
    }

    LaunchedEffect(uiState.ttsErrorMessage) {
        val message = uiState.ttsErrorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearTtsError()
    }

    // 恢复阅读进度：pendingPageFraction > 0 时跳转到目标页
    LaunchedEffect(uiState.pageReady, uiState.pendingPageFraction, isContinuousScrollMode) {
        // Continuous scroll owns pendingPageFraction while crossing the mode boundary. Letting the
        // detached paged reader consume it first resets single-chapter TXT books to their first page.
        if (isContinuousScrollMode || !uiState.pageReady || uiState.pendingPageFraction <= 0f) {
            return@LaunchedEffect
        }
        val readView = readViewRef.value ?: return@LaunchedEffect
        val totalPages = readView.getChapterPageCount(uiState.currentChapterIndex)
        if (totalPages > 0) {
            val targetPage = (totalPages * uiState.pendingPageFraction).toInt()
                .coerceIn(0, totalPages - 1)
            if (targetPage > 0) {
                readView.jumpToChapter(uiState.currentChapterIndex, targetPage)
            }
            viewModel.clearPendingPageFraction()
        }
    }

    var showNotesList by remember { mutableStateOf(false) }
    var linkReturnLocation by remember(bookId) { mutableStateOf<ReaderLinkLocation?>(null) }
    var catalogDragReturnLocation by remember(bookId) {
        mutableStateOf<ReaderLinkLocation?>(null)
    }
    var epubPendingFragment by remember(bookId) { mutableStateOf<String?>(null) }
    var linkReturnToken by remember(bookId) { mutableStateOf(0) }
    var linkNavigationJob by remember(bookId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 每次书内链接跳转成功后重新计时，30 秒后自动隐藏原页返回按钮。
    LaunchedEffect(linkReturnLocation, linkReturnToken, uiState.isMenuVisible) {
        if (linkReturnLocation != null && !uiState.isMenuVisible) {
            val activeToken = linkReturnToken
            kotlinx.coroutines.delay(30_000L)
            if (activeToken == linkReturnToken) {
                linkReturnLocation = null
            }
        }
    }

    // 🔥 原生选择 ActionMode 回调 → 等待笔记输入
    var pendingSelection by remember { mutableStateOf<PendingSelection?>(null) }
    var showNoteInput by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }

    // 自定义选择菜单状态（null = 不显示）
    var selectionState by remember { mutableStateOf<SelectionState?>(null) }
    LaunchedEffect(isContinuousScrollMode) {
        if (isContinuousScrollMode) selectionState = null
    }
    // 手柄拖拽中：true → 菜单立即隐藏；false → 以新坐标重新弹出
    var isSelectionDragging by remember { mutableStateOf(false) }
    // 每次拖拽结束后自增，触发 SelectionMenuOverlay 重置入场动画
    var menuReappearKey by remember { mutableStateOf(0) }
    // 高亮颜色选择器：true → 菜单从操作按钮切换为6色圆点
    var showHighlightColorPicker by remember { mutableStateOf(false) }
    var pendingAnnotationColorTarget by remember {
        mutableStateOf<AnnotationColorTarget?>(null)
    }
    // Dictionary app picker: after tapping Dictionary, switch from action chips to PROCESS_TEXT app chips.
    var showDictionaryAppPicker by remember { mutableStateOf(false) }
    var dictionaryLookupText by remember { mutableStateOf("") }
    var dictionaryAppOptions by remember { mutableStateOf<List<DictionaryAppOption>>(emptyList()) }
    var showMenuSettings by remember { mutableStateOf(false) }
    var showReplaceInput by remember { mutableStateOf(false) }
    var replaceSelection by remember { mutableStateOf<ReplaceSelectionInfo?>(null) }
    fun resetSelectionSubmenus() {
        showHighlightColorPicker = false
        pendingAnnotationColorTarget = null
        showDictionaryAppPicker = false
        showMenuSettings = false
        dictionaryLookupText = ""
        dictionaryAppOptions = emptyList()
    }
    LaunchedEffect(
        selectionState?.chapterIndex,
        selectionState?.charStart,
        selectionState?.charEnd,
        selectionState?.selectedText
    ) {
        resetSelectionSubmenus()
    }
    // 编辑笔记模式：非null时打开 NoteInputSheet 预填原笔记文字
    var editingNote by remember { mutableStateOf<com.huangder.lumibooks.domain.model.Note?>(null) }

    // 拖拽检测：SpanWatcher + 防抖重弹（在 onSelectionStarted 中延迟注册）
    val dragHandler = remember { Handler(Looper.getMainLooper()) }
    var dragHideRunnable by remember { mutableStateOf<Runnable?>(null) }
    var dragWatcher by remember { mutableStateOf<SpanWatcher?>(null) }

    // TOC 跳转：当 currentChapterIndex 变化且是 TOC 触发时，跳转 ReadView
    var showToc by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showAdvancedSheet by remember { mutableStateOf(false) }
    var openAdvancedAfterThemeClose by remember { mutableStateOf(false) }
    var showTxtEncodingDialog by remember(bookId) { mutableStateOf(false) }

    // 搜索状态
    var showSearch by remember(bookId) { mutableStateOf(false) }
    // 请求关闭状态（用于触发退出动画）
    var requestCloseNotesList by remember { mutableStateOf(false) }
    var requestCloseNoteInput by remember { mutableStateOf(false) }
    var requestCloseToc by remember { mutableStateOf(false) }
    var requestCloseTheme by remember { mutableStateOf(false) }
    var requestCloseAdvanced by remember { mutableStateOf(false) }
    var requestCloseSearch by remember { mutableStateOf(false) }

    // 处理返回键：触发退出动画，而不是直接关闭
    val isAnySheetOpen = showNotesList || showNoteInput || showToc || showThemeSheet ||
        showAdvancedSheet || showSearch || showTxtEncodingDialog || showReplaceInput
    val exitReader: () -> Unit = {
        viewModel.stopTts()
        onNavigateBack()
    }
    ConfigurableBackHandler(
        enabled = !isAnySheetOpen && linkReturnLocation == null,
        onBack = exitReader
    )

    // TxtEditor Activity 返回后刷新内容
    val txtEditorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.reloadContent()
        }
    }

    val shouldHandleVolumePageTurn = uiState.volumeKeyPageTurnEnabled &&
        !uiState.isMenuVisible &&
        !isAnySheetOpen &&
        selectionState == null

    DisposableEffect(
        activity,
        shouldHandleVolumePageTurn,
        isBookLayout,
        epubPageTurnHandler
    ) {
        if (!shouldHandleVolumePageTurn || activity == null) {
            return@DisposableEffect onDispose { }
        }

        val handler: (ReaderPageDirection) -> Unit = { direction ->
            if (isBookLayout) {
                val delta = if (direction == ReaderPageDirection.NEXT) 1 else -1
                epubPageTurnHandler?.invoke(delta)
            } else {
                when (direction) {
                    ReaderPageDirection.PREVIOUS -> readViewRef.value?.turnToPreviousPage()
                    ReaderPageDirection.NEXT -> readViewRef.value?.turnToNextPage()
                }
            }
        }
        activity.readerVolumeKeyHandler = handler
        onDispose {
            if (activity.readerVolumeKeyHandler === handler) {
                activity.readerVolumeKeyHandler = null
            }
        }
    }

    val returnToLinkedSource = {
        linkReturnLocation?.let { source ->
            linkReturnLocation = null
            if (isBookLayout) {
                epubLocatorRequest = null
                epubPageRequestToken++
                epubPageRequest = EpubPageRequest(
                    token = epubPageRequestToken,
                    chapterIndex = source.chapterIndex,
                    pageIndex = source.pageIndex
                )
                if (source.chapterIndex != uiState.currentChapterIndex) {
                    viewModel.setChapter(source.chapterIndex)
                }
            } else {
                readViewRef.value?.jumpToChapter(source.chapterIndex, source.pageIndex)
            }
        }
        Unit
    }
    ConfigurableBackHandler(enabled = !isAnySheetOpen && linkReturnLocation != null) {
        returnToLinkedSource()
    }
    var searchQuery by remember(bookId) { mutableStateOf("") }
    var searchResults by remember(bookId) {
        mutableStateOf<List<ReaderViewModel.SearchResult>>(emptyList())
    }
    var isSearching by remember(bookId) { mutableStateOf(false) }
    var hasSearched by remember(bookId) { mutableStateOf(false) }
    var searchResultQuery by remember(bookId) { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    val latestSearchJob = rememberUpdatedState(searchJob)
    var continuousSearchHighlight by remember(bookId) {
        mutableStateOf<ContinuousSearchHighlight?>(null)
    }

    val cancelActiveSearch: () -> Unit = {
        searchGeneration++
        searchJob?.cancel()
        searchJob = null
        isSearching = false
    }
    val submitSearch: (String) -> Unit = submit@{ querySnapshot ->
        cancelActiveSearch()
        searchResults = emptyList()
        searchResultQuery = querySnapshot
        hasSearched = true
        if (querySnapshot.isBlank()) return@submit
        isSearching = true
        val generation = searchGeneration
        searchJob = scope.launch {
            try {
                val results = viewModel.searchAllChapters(querySnapshot)
                if (generation == searchGeneration && searchResultQuery == querySnapshot) {
                    searchResults = results
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == searchGeneration) {
                    Log.e("ReaderSearch", "Failed to search book", error)
                    Toast.makeText(context, R.string.reader_search_failed, Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (generation == searchGeneration) {
                    isSearching = false
                    searchJob = null
                }
            }
        }
    }
    DisposableEffect(bookId) {
        onDispose {
            searchGeneration++
            latestSearchJob.value?.cancel()
        }
    }

    val selectedCustomBackground = if (eInkMode) null else selectedReaderBackgroundForTheme
    val readerBackgroundColorInt = when {
        selectedCustomBackground?.type == ReaderBackgroundType.COLOR -> {
            val base = runCatching { android.graphics.Color.parseColor(selectedCustomBackground.value) }
                .getOrDefault(0xFFFBFBFC.toInt())
            if (nightDisplay) darkenReaderSolidColor(base) else base
        }
        effectiveReaderBackgroundSelection == "night" -> 0xFF1a1a1a.toInt()
        effectiveReaderBackgroundSelection == "sepia" ->
            if (nightDisplay) 0xFF2b2118.toInt() else 0xFFf5e6d3.toInt()
        effectiveReaderBackgroundSelection == "green" ->
            if (nightDisplay) 0xFF142a1a.toInt() else 0xFFe8f5e9.toInt()
        effectiveReaderBackgroundSelection == "day" ->
            if (nightDisplay) 0xFF1a1a1a.toInt() else 0xFFFBFBFC.toInt()
        else -> 0xFFFBFBFC.toInt()
    }
    val readerBackgroundImagePath = selectedCustomBackground
        ?.takeIf { it.type == ReaderBackgroundType.IMAGE }
        ?.value
    val customBackgroundThemeColorInt = when {
        nightDisplay && selectedCustomBackground?.type == ReaderBackgroundType.COLOR -> readerBackgroundColorInt
        selectedCustomBackground != null -> selectedCustomBackground.dominantColor ?: readerBackgroundColorInt
        else -> readerBackgroundColorInt
    }
    val automaticReaderTextColorInt = when {
        selectedCustomBackground != null -> {
            if (ColorUtils.calculateLuminance(customBackgroundThemeColorInt) < 0.42) {
                0xFFE8E8EA.toInt()
            } else {
                0xFF333333.toInt()
            }
        }
        effectiveReaderBackgroundSelection == "night" -> 0xFFCCCCCC.toInt()
        effectiveReaderBackgroundSelection == "sepia" ->
            if (nightDisplay) 0xFFE8D5BC.toInt() else 0xFF4a3728.toInt()
        effectiveReaderBackgroundSelection == "green" ->
            if (nightDisplay) 0xFFC8E6C9.toInt() else 0xFF2e7d32.toInt()
        effectiveReaderBackgroundSelection == "day" ->
            if (nightDisplay) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
        else -> 0xFF333333.toInt()
    }
    val readerTextColorInt = effectiveReaderTextColor ?: automaticReaderTextColorInt
    val menuBgColorInt = customBackgroundThemeColorInt
    val menuBgColor = Color(menuBgColorInt)
    val menuContentColor = if (ColorUtils.calculateLuminance(menuBgColorInt) < 0.4) {
        Color.White
    } else {
        Color(0xFF1C1C1E)
    }
    // 胶囊按钮背景色：基于阅读主题渲染效果而非系统深色模式
    val capsuleBgColor = when (renderingTheme) {
        "night" -> Color(0xFF3A3A3C)
        "sepia_dark" -> Color(0xFF3A312A)
        "green_dark" -> Color(0xFF1E3527)
        "sepia" -> Color(0xFFE8D5C4)
        "green" -> Color(0xFFC8E6C9)
        else -> Color(0xFFEEEEEE)
    }
    val capsuleContentColor = if (ColorUtils.calculateLuminance(capsuleBgColor.toArgb()) < 0.4) {
        Color.White
    } else {
        Color(0xFF1C1C1E)
    }
    // 目录进度条颜色：比文字深，跟随阅读主题渲染效果
    val catalogProgressColor = when (renderingTheme) {
        "night" -> Color(0xFF555555)
        "sepia_dark" -> Color(0xFF8A6F55)
        "green_dark" -> Color(0xFF5E8F63)
        "sepia" -> Color(0xFFC4A88C)
        "green" -> Color(0xFFA5D6A7)
        else -> Color(0xFFD0D0D0)
    }

    val loadError = uiState.error
    if (!uiState.isLoading && loadError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.WindowBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.reader_load_failed),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = loadError,
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = exitReader) {
                    Text(stringResource(R.string.back), color = AppColors.Accent)
                }
            }
        }
        return
    }

    // 主题背景色
    val composeBgColor = Color(customBackgroundThemeColorInt)
    val epubSessionState = produceState<com.huangder.lumibooks.util.epub.BookRenderSession?>(
        initialValue = null,
        bookId,
        supportsBookLayout
    ) {
        value = if (supportsBookLayout) {
            withContext(Dispatchers.IO) { viewModel.getRenderSession() }
        } else {
            null
        }
    }
    val epubSession = epubSessionState.value
    val epubFontFilePath by produceState<String?>(
        initialValue = null,
        isBookLayout,
        uiState.fontType,
        uiState.customFontPath
    ) {
        value = if (isBookLayout) {
            prepareEpubReaderFontPath(context.applicationContext, uiState.fontType, uiState.customFontPath)
        } else {
            null
        }
    }
    val continuousTypeface = remember(uiState.fontType, uiState.customFontPath) {
        when {
            uiState.fontType == "serif" -> android.graphics.Typeface.SERIF
            uiState.fontType == "fangsong" -> DownloadedFonts.typeface(context, "fangsong")
            uiState.fontType == "kaiti" -> runCatching {
                androidx.core.content.res.ResourcesCompat.getFont(context, R.font.lxgw_wenkai)
            }.getOrNull()
            uiState.fontType.startsWith("custom") -> uiState.customFontPath
                ?.let { path -> runCatching { android.graphics.Typeface.createFromFile(path) }.getOrNull() }
            else -> android.graphics.Typeface.DEFAULT
        }
    } ?: android.graphics.Typeface.DEFAULT
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    val readerGlassContentScrim = if (isBookLayout) {
        // Compose cannot reliably sample a WebView into the liquid-glass backdrop. Use a
        // restrained contrast tint so the complete capsule remains visible over book CSS.
        if (ColorUtils.calculateLuminance(menuBgColorInt) < 0.4) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.Black.copy(alpha = 0.10f)
        }
    } else {
        menuBgColor.copy(alpha = 0.18f)
    }
    val readerGlassBackdrop = rememberLayerBackdrop()
    val activeReaderGlassBackdrop = readerGlassBackdrop.takeIf { isLiquidGlass && !isBookLayout }
    val readerGlassOverlayVisible = uiState.isMenuVisible ||
        isAnySheetOpen ||
        selectionState != null ||
        linkReturnLocation != null ||
        footnoteBubble != null ||
        uiState.showEpubLayoutHint ||
        uiState.showMobiLayoutHint ||
        uiState.showTxtEncodingHint ||
        pendingExternalLink != null ||
        uiState.ttsPlaybackState != TtsPlaybackState.IDLE
    ReaderSystemBarStyle(
        backgroundColor = composeBgColor,
        useDarkIcons = ColorUtils.calculateLuminance(customBackgroundThemeColorInt) >= 0.42
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(composeBgColor)
            .onGloballyPositioned { coordinates ->
                readerRootWindowPosition.value = coordinates.positionInWindow()
                readerRootSize.value = coordinates.size
            }
    ) {
        // Canvas 引擎（阅读器排版）的注释气泡：进出动画（180ms 进入 / 140ms 退出，见动效规范 7.1）
        LaunchedEffect(footnoteBubble) {
            if (footnoteBubble != null) {
                renderedFootnote = footnoteBubble
                if (motionEnabled) {
                    footnoteProgress.snapTo(0f)
                    footnoteProgress.animateTo(
                        1f,
                        tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Decelerate)
                    )
                } else {
                    footnoteProgress.snapTo(1f)
                }
            } else if (renderedFootnote != null) {
                if (motionEnabled) {
                    footnoteProgress.animateTo(
                        0f,
                        tween(LumiMotion.MenuExitMillis, easing = AppEasing.Accelerate)
                    )
                } else {
                    footnoteProgress.snapTo(0f)
                }
                renderedFootnote = null
            }
        }
        // 章节切换时关闭注释气泡（分页翻页与菜单在各自回调中关闭）
        LaunchedEffect(uiState.currentChapterIndex, uiState.renderMode, renderedContinuousScrollMode) {
            footnoteBubble = null
        }
        val modeTransitionActive = readerModeTransitionProgress.value < 0.999f
        val imagePreviewBlurActive = !eInkMode && readerImagePreviewProgress.value > 0.001f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (modeTransitionActive) {
                        Modifier.graphicsLayer {
                            val progress = readerModeTransitionProgress.value
                            alpha = progress
                            val scale = 0.96f + (0.04f * progress)
                            scaleX = scale
                            scaleY = scale
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (imagePreviewBlurActive) {
                        Modifier.blur((12f * readerImagePreviewProgress.value).dp)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (readerGlassOverlayVisible) {
                        activeReaderGlassBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
                    } else {
                        Modifier
                    }
                )
        ) {
            // ── 新 Canvas 引擎（TXT/EPUB） ──
            val activeEpubSession = epubSession
            if (isBookLayout && activeEpubSession != null) {
                EpubWebViewReader(
                    session = activeEpubSession,
                    chapterIndex = uiState.currentChapterIndex,
                    fontSizeSp = uiState.fontSize,
                    fontType = uiState.fontType,
                    fontFilePath = epubFontFilePath,
                    textColorOverride = effectiveReaderTextColor,
                    theme = renderingTheme,
                    textAlignment = uiState.textAlignment,
                    preservePublisherBackground = effectivePreserveEpubBackground,
                    bionicReadingEnabled = effectiveBionicReadingEnabled,
                    chineseMode = uiState.chineseMode,
                    restoreLocatorJson = uiState.epubLocatorJson,
                    restoreProgression = uiState.pendingPageFraction,
                    initialFragment = epubPendingFragment,
                    continuousScroll = isBookLayoutContinuousScroll,
                    pageTransition = if (isBookLayoutContinuousScroll) "none" else effectivePageTransition,
                    marginTopDp = uiState.marginTopDp,
                    marginRightDp = uiState.marginRightDp,
                    marginBottomDp = uiState.marginBottomDp,
                    marginLeftDp = uiState.marginLeftDp,
                    edgeTapMode = uiState.readerEdgeTapMode,
                    notes = renderedNotes.filter { it.chapterIndex == uiState.currentChapterIndex },
                    searchRequest = epubSearchRequest,
                    locatorRequest = epubLocatorRequest,
                    pageRequest = epubPageRequest,
                    selectionClearToken = epubSelectionClearToken,
                    onPageTextProviderReady = { epubPageTextProvider = it },
                    onPageTurnHandlerReady = { epubPageTurnHandler = it },
                    onPageChanged = { pageIndex, pageCount, locatorJson ->
                        viewModel.onEpubPageReady(pageIndex, pageCount, locatorJson)
                        epubPageRequest?.let { request ->
                            val expectedPage = request.chapterFraction?.let { fraction ->
                                pageIndexForChapterFraction(fraction, pageCount)
                            } ?: request.pageIndex
                            if (request.chapterIndex == uiState.currentChapterIndex &&
                                expectedPage == pageIndex
                            ) {
                                epubPageRequest = null
                            }
                        }
                        if (epubLocatorRequest?.chapterIndex == uiState.currentChapterIndex) {
                            epubLocatorRequest = null
                        }
                        epubPendingFragment = null
                    },
                    onCenterTap = viewModel::toggleMenu,
                    onImagePreviewOpen = viewModel::hideMenu,
                    onChapterTurn = { direction ->
                        epubPendingFragment = null
                        epubSearchRequest = null
                        epubLocatorRequest = null
                        epubPageRequest = null
                        viewModel.onEpubChapterTurn(direction)
                    },
                    onInternalLink = { targetChapter, fragment ->
                        // 书内链接跳转前捕获来源位置，用于左上角“返回到刚才页”按钮
                        val source = ReaderLinkLocation(
                            uiState.currentChapterIndex,
                            uiState.currentPageIndex
                        )
                        if (targetChapter != uiState.currentChapterIndex) {
                            epubSearchRequest = null
                            epubLocatorRequest = null
                            epubPageRequest = null
                            linkReturnLocation = source
                            linkReturnToken += 1
                            epubPendingFragment = fragment
                            viewModel.setChapter(targetChapter)
                        } else {
                            // 同章节片段跳转由 WebView 直接完成，这里只记录返回位置
                            linkReturnLocation = source
                            linkReturnToken += 1
                        }
                    },
                    onExternalLink = { href ->
                        if (isExternalBookLink(href)) pendingExternalLink = href
                    },
                    onSelection = { selection ->
                        if (selection.text.isNotBlank()) {
                            val isNewSelection = selectionState == null
                            val resolvedSelection = viewModel.resolveAnnotationSelection(
                                chapterIndex = uiState.currentChapterIndex,
                                startPosition = selection.startPosition,
                                endPosition = selection.endPosition,
                                selectedText = selection.text,
                                startLocatorJson = selection.startLocatorJson,
                                endLocatorJson = selection.endLocatorJson
                            ) ?: return@EpubWebViewReader
                            val overlapping = viewModel.findOverlappingReaderNotes(
                                chapterIndex = uiState.currentChapterIndex,
                                startPosition = selection.startPosition,
                                endPosition = selection.endPosition,
                                selectedText = selection.text,
                                startLocatorJson = selection.startLocatorJson,
                                endLocatorJson = selection.endLocatorJson
                            )
                            selectionState = SelectionState(
                                chapterIndex = uiState.currentChapterIndex,
                                pageInChapter = uiState.currentPageIndex,
                                charStart = resolvedSelection.start,
                                charEnd = resolvedSelection.end,
                                selectedText = selection.text,
                                touchX = selection.centerX,
                                touchY = selection.centerY,
                                overlappingHighlights = overlapping.filter { it.type != "underline" },
                                overlappingUnderlines = overlapping.filter { it.type == "underline" },
                                selTopY = selection.top,
                                selBottomY = selection.bottom,
                                selStartX = selection.left,
                                selEndX = selection.right,
                                startLocatorJson = selection.startLocatorJson,
                                endLocatorJson = selection.endLocatorJson
                            )
                            if (isNewSelection) menuReappearKey++
                        }
                    },
                    onSelectionCleared = {
                        selectionState = null
                        showHighlightColorPicker = false
                    },
                    onSearchResolved = { token, found ->
                        val request = epubSearchRequest
                        if (request?.token == token &&
                            request.chapterIndex == uiState.currentChapterIndex
                        ) {
                            epubSearchRequest = null
                            if (!found) {
                                Toast.makeText(
                                    context,
                                    R.string.epub_search_location_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onRenderUnavailable = {
                        cancelActiveSearch()
                        epubSearchRequest = null
                        epubLocatorRequest = null
                        epubPageRequest = null
                        viewModel.fallbackFromUnsupportedEpubWebView()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.useNewEngine && renderedContinuousScrollMode) {
                ContinuousScrollReader(
                    chapterCount = uiState.chapterCount,
                    currentChapter = uiState.currentChapterIndex,
                    initialChapterFraction = uiState.pendingPageFraction,
                    fontSize = uiState.fontSize,
                    lineHeight = uiState.lineHeight,
                    letterSpacingDp = uiState.letterSpacing,
                    textAlignment = uiState.textAlignment,
                    typeface = continuousTypeface,
                    textColor = readerTextColorInt,
                    backgroundColor = readerBackgroundColorInt,
                    backgroundImagePath = readerBackgroundImagePath,
                    marginLeft = uiState.marginLeftDp,
                    marginRight = uiState.marginRightDp,
                    marginTop = uiState.marginTopDp,
                    marginBottom = uiState.marginBottomDp,
                    paragraphSpacing = uiState.paragraphSpacing,
                    firstLineIndent = uiState.firstLineIndent,
                    bionicReadingEnabled = effectiveBionicReadingEnabled,
                    contentRevision = uiState.contentRevision,
                    viewModel = viewModel,
                    notes = renderedReaderNotes,
                    searchHighlight = continuousSearchHighlight,
                    scrollRequests = continuousScrollRequests,
                    onSearchHighlightFinished = { continuousSearchHighlight = null },
                    onMenuToggle = viewModel::toggleMenu,
                    onLinkClick = { sourceChapterIndex, href, anchorWindowX, anchorWindowY ->
                        if (isExternalBookLink(href)) {
                            pendingExternalLink = href
                        } else {
                            linkNavigationJob?.cancel()
                            linkNavigationJob = scope.launch {
                                if (viewModel.isFootnoteHref(sourceChapterIndex, href)) {
                                    val noteText = viewModel.resolveFootnoteText(sourceChapterIndex, href)
                                    if (noteText != null) {
                                        footnoteBubble = ReaderFootnoteBubble(
                                            text = noteText,
                                            anchorWindowX = anchorWindowX,
                                            anchorWindowY = anchorWindowY
                                        )
                                        return@launch
                                    }
                                }
                                footnoteBubble = null
                                val target = viewModel.resolveBookLink(sourceChapterIndex, href)
                                    ?: return@launch
                                jumpToContinuousChapter(target.chapterIndex)
                            }
                        }
                    },
                    onImageLongPress = { chapterIndex, image ->
                        epubSessionState.value?.imageUrl(chapterIndex, image.source)?.let { source ->
                            showReaderImagePreview(
                                EpubImagePreviewRequest(
                                    source = source,
                                    altText = "",
                                    leftPx = image.leftPx,
                                    topPx = image.topPx,
                                    rightPx = image.rightPx,
                                    bottomPx = image.bottomPx,
                                    naturalWidth = image.naturalWidth,
                                    naturalHeight = image.naturalHeight
                                )
                            )
                        }
                    },
                    selectionController = continuousSelectionController,
                    onSelectionChanging = {
                        selectionState = null
                        isSelectionDragging = true
                    },
                    onSelection = { chapterIndex, selection ->
                        val overlappingHighlights = findOverlappingNotes(
                            readerNotes, chapterIndex, selection.start, selection.end, "highlight"
                        )
                        val overlappingUnderlines = findOverlappingNotes(
                            readerNotes, chapterIndex, selection.start, selection.end, "underline"
                        )
                        selectionState = SelectionState(
                            chapterIndex = chapterIndex,
                            pageInChapter = 0,
                            charStart = selection.start,
                            charEnd = selection.end,
                            selectedText = selection.selectedText,
                            touchX = selection.startX,
                            touchY = selection.topY,
                            overlappingHighlights = overlappingHighlights,
                            overlappingUnderlines = overlappingUnderlines,
                            selTopY = selection.topY,
                            selBottomY = selection.bottomY,
                            selStartX = selection.startX,
                            selEndX = selection.endX
                        )
                        isSelectionDragging = false
                        menuReappearKey++
                    },
                    onChapterVisible = viewModel::onContinuousScrollPosition,
                    onRestoreComplete = viewModel::clearPendingPageFraction,
                    chineseMode = uiState.chineseMode,
                    ttsCurrentSentence = uiState.ttsCurrentSentence,
                    comicModeEnabled = uiState.comicModeEnabled,
                    boldTextEnabled = uiState.bodyFontWeight >= 600
                )
            } else if (uiState.useNewEngine) {
            AndroidView(
                factory = { ctx ->
                    ReadView(ctx, viewModel.pageLayoutEngine).apply {
                        setCallbacks(object : ReadViewCallbacks {
                            override fun onPageChanged(
                                globalPage: Int,
                                chapterIndex: Int,
                                pageInChapter: Int,
                                chapterTotalPages: Int
                            ) {
                                // 翻页时关闭选择菜单（选区已随页面切换失效）
                                selectionState = null
                                isSelectionDragging = false
                                footnoteBubble = null
                                viewModel.onNewEnginePageChanged(
                                    globalPage, chapterIndex, pageInChapter, chapterTotalPages
                                )
                            }

                            override fun onSpreadPageChanged(
                                rightGlobalPage: Int,
                                rightChapterIndex: Int,
                                rightPageInChapter: Int
                            ) {
                                viewModel.onSpreadPageChanged(
                                    rightGlobalPage,
                                    rightChapterIndex,
                                    rightPageInChapter
                                )
                            }

                            override fun onMenuToggle() {
                                // 用户点击屏幕中心区域，关闭选择菜单
                                selectionState = null
                                isSelectionDragging = false
                                footnoteBubble = null
                                viewModel.toggleMenu()
                            }

                            override fun onBookmarkSwipe() {
                                toggleBookmarkForCurrentPage()
                            }

                            override fun onLinkClick(href: String, tapX: Float, tapY: Float) {
                                if (isExternalBookLink(href)) {
                                    pendingExternalLink = href
                                    return
                                }
                                val source = readViewRef.value?.getCurrentLocation()
                                    ?.let { ReaderLinkLocation(it.first, it.second) }
                                    ?: return
                                val anchorWindow = readViewRef.value?.let { view ->
                                    val location = IntArray(2)
                                    view.getLocationInWindow(location)
                                    Offset(location[0] + tapX, location[1] + tapY)
                                }

                                linkNavigationJob?.cancel()
                                linkNavigationJob = scope.launch {
                                    if (anchorWindow != null &&
                                        viewModel.isFootnoteHref(source.chapterIndex, href)
                                    ) {
                                        val noteText = viewModel.resolveFootnoteText(source.chapterIndex, href)
                                        if (noteText != null) {
                                            footnoteBubble = ReaderFootnoteBubble(
                                                text = noteText,
                                                anchorWindowX = anchorWindow.x,
                                                anchorWindowY = anchorWindow.y
                                            )
                                            return@launch
                                        }
                                    }
                                    footnoteBubble = null
                                    val target = viewModel.resolveBookLink(source.chapterIndex, href)
                                        ?: return@launch
                                    linkReturnLocation = source
                                    linkReturnToken += 1
                                    readViewRef.value?.jumpToCharacter(
                                        target.chapterIndex,
                                        target.characterOffset
                                    )
                                }
                            }

                            override fun onImageLongPress(chapterIndex: Int, image: ReaderImageHit) {
                                epubSessionState.value?.imageUrl(chapterIndex, image.source)?.let { source ->
                                    showReaderImagePreview(
                                        EpubImagePreviewRequest(
                                            source = source,
                                            altText = "",
                                            leftPx = image.leftPx,
                                            topPx = image.topPx,
                                            rightPx = image.rightPx,
                                            bottomPx = image.bottomPx,
                                            naturalWidth = image.naturalWidth,
                                            naturalHeight = image.naturalHeight
                                        )
                                    )
                                }
                            }

                            override fun onLoadingChanged(isLoading: Boolean) {}

                            override fun onSelectionStarted(sourceView: com.huangder.lumibooks.ui.reader.engine.PageContentView?) {
                                // 🔥 拖拽进行中时跳过：primary SpanWatcher 每次 span 变化都触发此回调，
                                // 若不 guard，会取消 dragHideRunnable（300ms 重弹计时器），导致菜单永不重弹
                                if (isSelectionDragging) return
                                showHighlightColorPicker = false
                                val info = readViewRef.value?.getSelectionInfo(sourceView)
                                    ?: return
                                val cStart = info.chapterStartOffset + info.pageStart
                                val cEnd = info.chapterStartOffset + info.pageEnd
                                val overlappingHighlights = findOverlappingNotes(
                                    readerNotes, info.chapterIndex, cStart, cEnd, "highlight"
                                )
                                val overlappingUnderlines = findOverlappingNotes(
                                    readerNotes, info.chapterIndex, cStart, cEnd, "underline"
                                )
                                selectionState = SelectionState(
                                    chapterIndex = info.chapterIndex,
                                    pageInChapter = 0,
                                    charStart = cStart,
                                    charEnd = cEnd,
                                    selectedText = info.selectedText,
                                    touchX = info.selStartX,
                                    touchY = info.selTopY,
                                    overlappingHighlights = overlappingHighlights,
                                    overlappingUnderlines = overlappingUnderlines,
                                    selTopY = info.selTopY,
                                    selBottomY = info.selBottomY,
                                    selStartX = info.selStartX,
                                    selEndX = info.selEndX
                                )
                                // 延迟注册拖拽检测 SpanWatcher
                                dragHideRunnable?.let { dragHandler.removeCallbacks(it) }
                                dragHandler.postDelayed({
                                    val tv = sourceView?.textView
                                    val sp = tv?.text as? Spannable ?: return@postDelayed
                                    dragWatcher?.let { old ->
                                        sp.getSpans(0, sp.length, SpanWatcher::class.java)
                                            .filter { it === old }
                                            .forEach { sp.removeSpan(it) }
                                    }
                                    val watcher = object : SpanWatcher {
                                        override fun onSpanChanged(s: Spannable, what: Any, ostart: Int, oend: Int, nstart: Int, nend: Int) {
                                            if (what !== Selection.SELECTION_START && what !== Selection.SELECTION_END) return
                                            // 🔥 移除 "if (selectionState == null) return" 保护：
                                            // 拖拽中第一次触发后 selectionState 被清空，后续每次 span 变化都会命中该保护
                                            // 导致防抖计时器无法在持续拖拽时正确重置
                                            if (selectionState != null) selectionState = null
                                            isSelectionDragging = true
                                            dragHideRunnable?.let { dragHandler.removeCallbacks(it) }
                                            val r = Runnable {
                                                val fresh = readViewRef.value?.getSelectionInfo(sourceView)
                                                if (fresh != null) {
                                                    val cs = fresh.chapterStartOffset + fresh.pageStart
                                                    val ce = fresh.chapterStartOffset + fresh.pageEnd
                                                    val overlappingHighlights = findOverlappingNotes(
                                                        readerNotes, fresh.chapterIndex, cs, ce, "highlight"
                                                    )
                                                    val overlappingUnderlines = findOverlappingNotes(
                                                        readerNotes, fresh.chapterIndex, cs, ce, "underline"
                                                    )
                                                    selectionState = SelectionState(
                                                        chapterIndex = fresh.chapterIndex,
                                                        pageInChapter = 0,
                                                        charStart = cs,
                                                        charEnd = ce,
                                                        selectedText = fresh.selectedText,
                                                        touchX = fresh.selStartX,
                                                        touchY = fresh.selTopY,
                                                        overlappingHighlights = overlappingHighlights,
                                                        overlappingUnderlines = overlappingUnderlines,
                                                        selTopY = fresh.selTopY,
                                                        selBottomY = fresh.selBottomY,
                                                        selStartX = fresh.selStartX,
                                                        selEndX = fresh.selEndX
                                                    )
                                                    menuReappearKey++
                                                }
                                                isSelectionDragging = false
                                            }
                                            dragHideRunnable = r
                                            dragHandler.postDelayed(r, 300L)
                                        }
                                        override fun onSpanAdded(s: Spannable, what: Any, start: Int, end: Int) {}
                                        override fun onSpanRemoved(s: Spannable, what: Any, start: Int, end: Int) {}
                                    }
                                    dragWatcher = watcher
                                    sp.setSpan(watcher, 0, sp.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                                }, 100L)
                            }

                            override fun onSelectionAction(
                                action: String,
                                selectedText: String,
                                chapterIndex: Int,
                                startPosition: Int,
                                endPosition: Int,
                                pageStart: Int,
                                pageEnd: Int
                            ) {
                                when (action) {
                                    "highlight" -> {
                                        viewModel.addNote(
                                            selectedText = selectedText,
                                            noteText = "",
                                            chapterIndex = chapterIndex,
                                            startPosition = startPosition,
                                            endPosition = endPosition,
                                            color = DefaultReaderHighlightColorWithAlpha
                                        )
                                    }
                                    "note" -> {
                                        // 保存当前选区信息，打开笔记输入
                                        pendingSelection = PendingSelection(
                                            selectedText, chapterIndex, startPosition, endPosition
                                        )
                                        showNoteInput = true
                                    }
                                    "search" -> {
                                        showSearch = true
                                        searchQuery = selectedText
                                        submitSearch(selectedText)
                                    }
                                    "dismiss" -> {
                                        // 选区被清除 → 隐藏自定义菜单
                                        selectionState = null
                                    }
                                }
                            }
                        })
                        setContentProvider { chapterIndex ->
                            viewModel.getChapterText(chapterIndex)
                        }
                        readViewRef.value = this
                    }
                },
                update = { readView ->
                    val fontSizePx = uiState.fontSize * density.density
                    val measuredWidth = readView.width.takeIf { it > 0 } ?: readerScreenWidthPx
                    val contentWidthPx = if (readView.isTwoPageSpreadActive) {
                        val gutterPx = (16f * density.density).toInt()
                        val halfWidth = ((measuredWidth - gutterPx) / 2).coerceAtLeast(1)
                        val gutterMargin = (uiState.marginLeftDp.coerceAtMost(uiState.marginRightDp) / 2f)
                            .coerceAtLeast(12f) * density.density
                        (halfWidth - ((uiState.marginLeftDp + gutterMargin) * density.density).toInt())
                            .coerceAtLeast(1)
                    } else {
                        (
                            measuredWidth - (
                                (uiState.marginLeftDp + uiState.marginRightDp) * density.density
                            ).toInt()
                        ).coerceAtLeast(1)
                    }
                    viewModel.updateReaderContentWidth(contentWidthPx)
                    readView.configure(
                        fontSizePx = fontSizePx,
                        theme = renderingTheme,
                        chapterCount = uiState.chapterCount,
                        startChapter = if (isContinuousScrollMode) lastPagedChapter else uiState.currentChapterIndex,
                        startPage = if (isContinuousScrollMode) lastPagedPage else uiState.currentPageIndex,
                        lineHeightMult = uiState.lineHeight,
                        letterSpacingDp = uiState.letterSpacing,
                        textAlignment = uiState.textAlignment,
                        fontType = uiState.fontType,
                        customFontPath = uiState.customFontPath,
                        marginLeftDp = uiState.marginLeftDp,
                        marginRightDp = uiState.marginRightDp,
                        marginTopDp = uiState.marginTopDp,
                        marginBottomDp = uiState.marginBottomDp,
                        // 角落状态/进度信息允许与正文重叠（用户要求边距 0 即真 0，不再为信息区预留）
                        topOverlayInsetDp = 0f,
                        bottomOverlayInsetDp = 0f,
                        paragraphSpacingDp = uiState.paragraphSpacing,
                        bionicReadingEnabled = effectiveBionicReadingEnabled,
                        useDisplayDensityForSpans = uiState.book?.format?.name == "TXT",
                        writingMode = uiState.readerWritingMode,
                        twoPageSpread = twoPageSpreadEligible
                    )
                    readView.setReaderBackground(
                        backgroundColor = readerBackgroundColorInt,
                        textColor = readerTextColorInt,
                        imagePath = readerBackgroundImagePath
                    )
                    readView.setSavedNotes(renderedReaderNotes)
                    readView.ttsHighlightRange = uiState.ttsCurrentSentence?.let {
                        TtsHighlightRange(it.chapterIndex, it.startOffset, it.endOffset)
                    }
                    // 简繁转换
                    readView.setChineseMode(uiState.chineseMode)
                    // 正文字重（PR #19 #24）
                    readView.setBoldText(uiState.bodyFontWeight >= 600)
                    // 翻页效果
                    readView.setPageTransition(if (isContinuousScrollMode) lastPagedTransition else effectivePageTransition)
                    // 左右边缘点击翻页方向（不影响滑动手势）
                    readView.setEdgeTapMode(uiState.readerEdgeTapMode)
                },
                modifier = Modifier.fillMaxSize()
            )

            // 段间距/首行缩进变化时，强制重新分页
            LaunchedEffect(uiState.paragraphSpacing, uiState.firstLineIndent) {
                readViewRef.value?.forceRelayout()
            }

            // 字重变化影响行宽，需要重新分页
            LaunchedEffect(uiState.bodyFontWeight) {
                readViewRef.value?.forceRelayout()
            }

            LaunchedEffect(uiState.contentRevision) {
                if (uiState.contentRevision > 0L) {
                    readViewRef.value?.forceRelayout()
                }
            }
        }

        // ── 旧 WebView 路径（PDF） ──
            if (!uiState.useNewEngine) {
                LegacyWebViewContent(uiState, viewModel, composeBgColor)
            }
        }

        // 底部系统手势排除：正文边距为 0 时最后几行会伸进系统手势导航预留区，
        // 且下滑唤出的临时导航栏显示期间会吃掉该区域的触摸（表现为"最后几行时灵时不灵"）。
        // 此处声明排除（系统上限 200dp），左右边缘不动，保留返回手势。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp)
                    .systemGestureExclusion()
            )
        }

        // ── Canvas 引擎注释气泡（同窗口覆盖层：玻璃折射对位正确，且无 Popup 窗口首帧闪现） ──
        renderedFootnote?.let { bubble ->
            ReaderFootnoteBubbleOverlay(
                footnote = bubble,
                progress = footnoteProgress.value,
                rootWindowPosition = readerRootWindowPosition.value,
                rootSize = readerRootSize.value,
                isLiquidGlass = isLiquidGlass,
                glassBackdrop = activeReaderGlassBackdrop,
                backgroundColor = menuBgColor,
                contentColor = menuContentColor,
                fontSizeSp = uiState.fontSize,
                onDismiss = { footnoteBubble = null }
            )
        }

        // ── 覆盖层 UI（新旧引擎共享） ──
        ProvideLiquidGlassBackdrop(
            backdrop = activeReaderGlassBackdrop
        ) {
        if (!uiState.isLoading || isAnySheetOpen || uiState.isEpubChapterHandoffInProgress) {
            val liveChapterTitle = uiState.chapterTitles
                .getOrNull(uiState.currentChapterIndex)
                ?.trim()
                .orEmpty()
                .ifBlank {
                    stringResource(
                        R.string.reader_chapter_fallback,
                        uiState.currentChapterIndex + 1
                    )
                }
            val liveMenuSnapshot = ReaderMenuSnapshot(
                chapterIndex = uiState.currentChapterIndex,
                chapterTitle = liveChapterTitle,
                pageIndex = uiState.currentPageIndex,
                pageCount = uiState.totalPages,
                bookProgressPercent = calculateBookProgressPercent(
                    chapterIndex = uiState.currentChapterIndex,
                    chapterCount = uiState.chapterCount,
                    pageIndex = uiState.currentPageIndex,
                    chapterPageCount = uiState.totalPages
                ),
                rightPageIndex = uiState.rightPageIndex
            )
            var lastReadyMenuSnapshot by remember(bookId) {
                mutableStateOf<ReaderMenuSnapshot?>(null)
            }
            SideEffect {
                if (uiState.pageReady && !uiState.isEpubChapterHandoffInProgress) {
                    lastReadyMenuSnapshot = liveMenuSnapshot
                }
            }
            val displayedMenuSnapshot = if (
                isBookLayout && uiState.isEpubChapterHandoffInProgress
            ) {
                lastReadyMenuSnapshot ?: liveMenuSnapshot
            } else {
                liveMenuSnapshot
            }
            // 书籍原排版双页对开：页码按物理页显示（跨页 k → 2k–2k+1，章首单独右页显示 1）
            val spreadDisplay = isBookLayout && twoPageSpreadEligible
            val displayCurrentPage = if (spreadDisplay) {
                if (uiState.currentPageIndex <= 0) 1 else uiState.currentPageIndex * 2
            } else {
                displayedMenuSnapshot.pageIndex + 1
            }
            val displayRightPageIndex = if (spreadDisplay) {
                if (uiState.currentPageIndex <= 0) null else uiState.currentPageIndex * 2
            } else {
                displayedMenuSnapshot.rightPageIndex
            }
            val displayPageCount = if (spreadDisplay) {
                (displayedMenuSnapshot.pageCount * 2 - 1).coerceAtLeast(1)
            } else {
                displayedMenuSnapshot.pageCount
            }

            AnimatedVisibility(
                visible = linkReturnLocation != null && !uiState.isMenuVisible && !isAnySheetOpen,
                enter = if (eInkMode) EnterTransition.None else fadeIn(animationSpec = tween(200)),
                exit = if (eInkMode) ExitTransition.None else fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 24.dp, top = 20.dp)
            ) {
                LinkReturnButton(
                    backgroundColor = capsuleBgColor,
                    contentColor = if (isLiquidGlass && !isBookLayout) {
                        menuContentColor
                    } else {
                        capsuleContentColor
                    },
                    glassContentScrimColor = readerGlassContentScrim,
                    forceSolid = isBookLayout,
                    onClick = returnToLinkedSource
                )
            }

            val currentBookmarkOffset = if (isContinuousScrollMode) {
                0
            } else {
                readViewRef.value?.getCurrentPageStartCharacterOffset()
            }
            val isCurrentPageBookmarked = bookmarks.any {
                it.chapterIndex == displayedMenuSnapshot.chapterIndex &&
                    (it.characterOffset == currentBookmarkOffset ||
                        (it.characterOffset == null &&
                            it.position.toInt() == displayedMenuSnapshot.pageIndex))
            }

            // 顶部栏
            AnimatedVisibility(
                visible = uiState.isMenuVisible,
                enter = when {
                    eInkMode -> EnterTransition.None
                    !motionEnabled -> fadeIn(animationSpec = tween(120))
                    else -> slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)
                    ) + fadeIn(animationSpec = tween(LumiMotion.MenuEnterMillis))
                },
                exit = when {
                    eInkMode -> ExitTransition.None
                    !motionEnabled -> fadeOut(animationSpec = tween(100))
                    else -> slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(LumiMotion.MenuExitMillis, easing = AppEasing.Accelerate)
                    ) + fadeOut(animationSpec = tween(LumiMotion.MenuExitMillis))
                },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val bookTitle = uiState.book?.title ?: ""
                val isTxtBook = uiState.book?.format?.name == "TXT"
                ReaderTopBar(
                    title = bookTitle,
                    onBack = exitReader,
                    bgColor = menuBgColor,
                    contentColor = menuContentColor,
                    glassContentScrimColor = readerGlassContentScrim,
                    forceSolidButtons = isBookLayout,
                    isTtsActive = uiState.ttsActiveBookId == uiState.book?.id &&
                        uiState.ttsPlaybackState != TtsPlaybackState.IDLE,
                    onTtsClick = {
                        if (uiState.ttsActiveBookId == uiState.book?.id &&
                            uiState.ttsPlaybackState != TtsPlaybackState.IDLE
                        ) {
                            viewModel.toggleTtsPlayPause()
                        } else {
                            requestTtsStart()
                        }
                    },
                    isBookmarked = isCurrentPageBookmarked,
                    onBookmarkToggle = { toggleBookmarkForCurrentPage() },
                    isTxtBook = isTxtBook,
                    onEditClick = {
                        viewModel.hideMenu()
                        val readerAnchor = readViewRef.value?.getCurrentPageTextAnchor()
                        val chapterIndex = readerAnchor?.chapterIndex
                            ?: uiState.currentChapterIndex
                        val charOffset = viewModel.resolveTxtEditorCharOffset(
                            chapterIndex = chapterIndex,
                            readerOffset = readerAnchor?.characterOffset ?: 0
                        )
                        val intent = Intent(context, TxtEditorActivity::class.java).apply {
                            putExtra(TxtEditorActivity.EXTRA_BOOK_ID, bookId)
                            putExtra(TxtEditorActivity.EXTRA_CHAPTER_INDEX, chapterIndex)
                            putExtra(TxtEditorActivity.EXTRA_CHAR_OFFSET, charOffset)
                            putExtra(TxtEditorActivity.EXTRA_REVEAL_READING_POSITION, true)
                        }
                        runCatching {
                            txtEditorLauncher.launch(intent)
                        }.onFailure { error ->
                            Log.w("ReaderScreen", "Failed to open TXT editor", error)
                            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEncodingClick = {
                        viewModel.hideMenu()
                        showTxtEncodingDialog = true
                    }
                )
            }

            if (uiState.totalPages > 0 || uiState.useNewEngine ||
                uiState.isEpubChapterHandoffInProgress
            ) {
                val chapterTitle = displayedMenuSnapshot.chapterTitle
                val bookProgressPercent = displayedMenuSnapshot.bookProgressPercent

                // 底部渐变遮罩
                val menuAlpha = remember { Animatable(0f) }
                val menuOffset = remember { Animatable(60f) }
                val menuScope = rememberCoroutineScope()
                LaunchedEffect(uiState.isMenuVisible, eInkMode, motionEnabled) {
                    if (eInkMode) {
                        menuAlpha.snapTo(if (uiState.isMenuVisible) 1f else 0f)
                        menuOffset.snapTo(if (uiState.isMenuVisible) 0f else 60f)
                    } else if (!motionEnabled) {
                        menuOffset.snapTo(0f)
                        menuAlpha.animateTo(
                            if (uiState.isMenuVisible) 1f else 0f,
                            tween(if (uiState.isMenuVisible) 120 else 100)
                        )
                    } else if (uiState.isMenuVisible) {
                        menuOffset.snapTo(60f)
                        menuScope.launch { menuAlpha.animateTo(1f, tween(LumiMotion.MenuEnterMillis)) }
                        menuScope.launch { menuOffset.animateTo(0f, tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)) }
                    } else {
                        menuScope.launch { menuAlpha.animateTo(0f, tween(LumiMotion.MenuExitMillis)) }
                        menuScope.launch { menuOffset.animateTo(60f, tween(LumiMotion.MenuExitMillis, easing = AppEasing.Accelerate)) }
                    }
                }

                if (!isLiquidGlass) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = menuAlpha.value }
                            .then(
                                if (eInkMode) {
                                    Modifier.background(menuBgColor)
                                } else {
                                    Modifier.background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to menuBgColor.copy(alpha = 0f),
                                                0.2f to menuBgColor.copy(alpha = 0.4f),
                                                0.5f to menuBgColor.copy(alpha = 0.8f),
                                                0.8f to menuBgColor.copy(alpha = 0.95f),
                                                1.0f to menuBgColor
                                            )
                                        )
                                    )
                                }
                            )
                    )
                }

                // 胶囊菜单
                Box(modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = menuAlpha.value; translationY = menuOffset.value }
                ) {
                    FloatingReaderMenu(
                        visible = uiState.isMenuVisible,
                        chapterTitle = chapterTitle,
                        chapterTitles = uiState.chapterTitles,
                        chapterCount = uiState.chapterCount,
                        bookProgressPercent = bookProgressPercent,
                        currentPage = displayCurrentPage,
                        chapterPageCount = displayPageCount,
                        rightPageIndex = displayRightPageIndex,
                        capsuleBgColor = capsuleBgColor,
                        capsuleContentColor = if (isLiquidGlass && !isBookLayout) menuContentColor else capsuleContentColor,
                        readerContentColor = menuContentColor,
                        catalogProgressColor = catalogProgressColor,
                        glassContentScrimColor = readerGlassContentScrim,
                        forceSolidCapsules = isBookLayout,
                        canGoToPreviousChapter = uiState.currentChapterIndex > 0,
                        canGoToNextChapter = uiState.currentChapterIndex < uiState.chapterCount - 1,
                        onCatalogClick = {
                            viewModel.hideMenu()
                            showToc = true
                        },
                        onPreviousChapterClick = {
                            val targetChapter = uiState.currentChapterIndex - 1
                            when {
                                targetChapter < 0 -> Unit
                                isContinuousScrollMode -> jumpToContinuousChapter(targetChapter)
                                !isBookLayout && uiState.useNewEngine -> {
                                    val readView = readViewRef.value
                                    if (readView != null) {
                                        readView.jumpToChapter(targetChapter)
                                    } else {
                                        viewModel.setChapter(targetChapter)
                                    }
                                }
                                else -> viewModel.setChapter(targetChapter)
                            }
                        },
                        onNextChapterClick = {
                            val targetChapter = uiState.currentChapterIndex + 1
                            when {
                                targetChapter >= uiState.chapterCount -> Unit
                                isContinuousScrollMode -> jumpToContinuousChapter(targetChapter)
                                !isBookLayout && uiState.useNewEngine -> {
                                    val readView = readViewRef.value
                                    if (readView != null) {
                                        readView.jumpToChapter(targetChapter)
                                    } else {
                                        viewModel.setChapter(targetChapter)
                                    }
                                }
                                else -> viewModel.setChapter(targetChapter)
                            }
                        },
                        onBookmarkClick = {
                            viewModel.hideMenu()
                            showNotesList = true
                        },
                        onSearchClick = {
                            viewModel.hideMenu()
                            showSearch = true
                        },
                        onThemeClick = {
                            viewModel.hideMenu()
                            openAdvancedAfterThemeClose = false
                            requestCloseTheme = false
                            showThemeSheet = true
                        },
                        onCatalogProgressDragStart = {
                            catalogDragReturnLocation = if (isBookLayout) {
                                ReaderLinkLocation(
                                    chapterIndex = uiState.currentChapterIndex,
                                    pageIndex = uiState.currentPageIndex
                                )
                            } else {
                                readViewRef.value
                                    ?.getCurrentLocation()
                                    ?.let { ReaderLinkLocation(it.first, it.second) }
                            }
                        },
                        onCatalogProgressDragEnd = { finalProgress ->
                            if (isContinuousScrollMode) {
                                mapGlobalProgress(finalProgress, uiState.chapterCount)?.let { target ->
                                    continuousScrollRequests.tryEmit(target.chapterIndex)
                                    viewModel.onContinuousScrollPosition(target.chapterIndex, 0f)
                                }
                            } else if (isBookLayout) {
                                mapGlobalProgress(finalProgress, uiState.chapterCount)?.let { target ->
                                    val source = catalogDragReturnLocation
                                    val expectedPage = if (target.chapterIndex == uiState.currentChapterIndex) {
                                        pageIndexForChapterFraction(
                                            target.chapterFraction,
                                            uiState.totalPages
                                        )
                                    } else {
                                        0
                                    }
                                    val destinationDiffers = source != null &&
                                        (source.chapterIndex != target.chapterIndex ||
                                            source.pageIndex != expectedPage)

                                    epubPendingFragment = null
                                    epubSearchRequest = null
                                    epubLocatorRequest = null
                                    epubPageRequestToken++
                                    epubPageRequest = EpubPageRequest(
                                        token = epubPageRequestToken,
                                        chapterIndex = target.chapterIndex,
                                        pageIndex = 0,
                                        chapterFraction = target.chapterFraction
                                    )
                                    if (target.chapterIndex != uiState.currentChapterIndex) {
                                        viewModel.setChapter(target.chapterIndex)
                                    }
                                    if (destinationDiffers) {
                                        linkReturnLocation = source
                                        linkReturnToken += 1
                                    }
                                }
                            } else {
                                val readView = readViewRef.value
                                readView?.jumpToGlobalProgress(finalProgress)
                                val source = catalogDragReturnLocation
                                val destination = readView?.getCurrentLocation()
                                    ?.let { ReaderLinkLocation(it.first, it.second) }
                                if (source != null && destination != null && source != destination) {
                                    linkReturnLocation = source
                                    linkReturnToken += 1
                                }
                            }
                            catalogDragReturnLocation = null
                        },
                        onCatalogProgressDragCancel = {
                            catalogDragReturnLocation = null
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // 底部阅读状态
                if (!uiState.isMenuVisible) {
                    ReaderPageCornerOverlay(
                        chapterTitle = chapterTitle,
                        bookProgressPercent = bookProgressPercent,
                        currentPage = displayCurrentPage,
                        chapterPageCount = displayPageCount,
                        rightPageIndex = displayRightPageIndex,
                        leftMarginDp = uiState.marginLeftDp,
                        rightMarginDp = uiState.marginRightDp,
                        topLeft = if (linkReturnLocation == null) {
                            uiState.readerTopLeftContent
                        } else {
                            ReaderCornerContent.NONE
                        },
                        topRight = uiState.readerTopRightContent,
                        bottomLeft = uiState.readerBottomLeftContent,
                        bottomRight = uiState.readerBottomRightContent,
                        contentColor = Color(readerTextColorInt).copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            val ttsBottomPadding by animateDpAsState(
                targetValue = if (uiState.isMenuVisible) 204.dp else 44.dp,
                animationSpec = if (eInkMode || !motionEnabled) snap() else spring(dampingRatio = 0.82f, stiffness = 360f),
                label = "ttsBottomPadding"
            )
            AnimatedVisibility(
                visible = uiState.ttsActiveBookId == uiState.book?.id &&
                    uiState.ttsPlaybackState != TtsPlaybackState.IDLE &&
                    !isAnySheetOpen,
                enter = if (eInkMode || !motionEnabled) fadeIn(tween(LumiMotion.MenuEnterMillis)) else slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = if (eInkMode || !motionEnabled) fadeOut(tween(LumiMotion.MenuExitMillis)) else slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = ttsBottomPadding)
            ) {
                TtsPlayerPanel(
                    playbackState = uiState.ttsPlaybackState,
                    speechRate = uiState.ttsSpeechRate,
                    sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                    onPlayPause = viewModel::toggleTtsPlayPause,
                    onStop = viewModel::stopTts,
                    onSkipForward = viewModel::ttsSkipForward,
                    onSkipBackward = viewModel::ttsSkipBackward,
                    onRateChange = viewModel::setTtsSpeechRate,
                    onSetSleepTimer = viewModel::setSleepTimer,
                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                    readerBackgroundColor = composeBgColor,
                    readerContentColor = Color(readerTextColorInt),
                    forceSolidSurface = isBookLayout
                )
            }

            // 目录底部弹出
            TocSheet(
                visible = showToc,
                requestClose = requestCloseToc,
                tocEntries = uiState.tocEntries,
                currentChapter = uiState.currentChapterIndex,
                bookmarks = bookmarks,
                chapterTitles = uiState.chapterTitles,
                onChapterSelected = { entry ->
                    scope.launch {
                        val target = viewModel.resolveTocTarget(
                            chapterIndex = entry.chapterIndex,
                            anchor = entry.anchor
                        ) ?: return@launch
                        val readView = readViewRef.value
                        if (isContinuousScrollMode) {
                            jumpToContinuousChapter(target.chapterIndex)
                        } else if (!isBookLayout && uiState.useNewEngine && readView != null) {
                            // Reload even when state already reports the selected chapter.
                            if (entry.anchor.isNullOrBlank()) {
                                readView.jumpToChapter(target.chapterIndex)
                            } else {
                                readView.jumpToCharacter(
                                    target.chapterIndex,
                                    target.characterOffset
                                )
                            }
                        } else {
                            viewModel.setChapter(target.chapterIndex)
                        }
                    }
                    showToc = false
                    requestCloseToc = false
                },
                onBookmarkClick = { bm ->
                    if (isBookLayout) {
                        val chapterHref = epubSession?.chapterHref(bm.chapterIndex).orEmpty()
                        val locatorJson = bm.locatorJson
                            ?.takeIf { isEpubLocatorForChapter(it, chapterHref) }
                            ?: bm.characterOffset?.let { characterOffset ->
                                createEpubFallbackLocator(
                                    href = chapterHref,
                                    charOffset = characterOffset,
                                    chapterTextLength = viewModel.getChapterTextLength(bm.chapterIndex).coerceAtLeast(1)
                                )
                            }
                        epubPendingFragment = null
                        epubSearchRequest = null
                        if (locatorJson != null) {
                            epubPageRequest = null
                            epubLocatorRequestToken++
                            epubLocatorRequest = EpubLocatorRequest(
                                token = epubLocatorRequestToken,
                                chapterIndex = bm.chapterIndex,
                                locatorJson = locatorJson
                            )
                        } else {
                            epubLocatorRequest = null
                            epubPageRequestToken++
                            epubPageRequest = EpubPageRequest(
                                token = epubPageRequestToken,
                                chapterIndex = bm.chapterIndex,
                                pageIndex = bm.position.toInt().coerceAtLeast(0)
                            )
                        }
                        if (bm.chapterIndex != uiState.currentChapterIndex) {
                            viewModel.setChapter(bm.chapterIndex)
                        }
                    } else if (isContinuousScrollMode) {
                        jumpToContinuousChapter(bm.chapterIndex)
                    } else {
                        bm.characterOffset?.let { readViewRef.value?.jumpToCharacter(bm.chapterIndex, it) }
                            ?: readViewRef.value?.jumpToChapter(bm.chapterIndex, bm.position.toInt())
                    }
                    showToc = false
                    requestCloseToc = false
                },
                onDeleteBookmark = { bm -> viewModel.deleteBookmark(bm) },
                onEditBookmark = { bm, newTitle -> viewModel.updateBookmarkTitle(bm, newTitle) },
                onDismiss = { showToc = false; requestCloseToc = false }
            )

            // 主题设置弹窗
            ThemeSettingsSheet(
                visible = showThemeSheet,
                requestClose = requestCloseTheme,
                currentFontSize = uiState.fontSize,
                currentTheme = effectiveReaderTheme,
                currentBackgroundSelection = effectiveReaderBackgroundSelection,
                customBackgrounds = uiState.customReaderBackgrounds,
                readerThemeSuites = uiState.readerThemeSuites,
                activeReaderThemeSuiteId = uiState.activeReaderThemeSuiteId,
                customFonts = uiState.customFonts,
                currentPreserveEpubBackground = effectivePreserveEpubBackground,
                currentBrightness = uiState.brightness,
                currentOptimizeLayout = uiState.optimizeLayout,
                currentUseEpubCss = uiState.useEpubCss,
                supportsBookLayout = supportsBookLayout,
                currentRenderMode = uiState.renderMode,
                currentWritingMode = uiState.readerWritingMode,
                supportsWritingMode = uiState.useNewEngine && !isBookLayout,
                currentChineseMode = uiState.chineseMode,
                currentPageTransition = effectivePageTransition,
                currentDisplayMode = uiState.readerDisplayMode,
                onFontSizeChange = { viewModel.saveFontSize(it) },
                onThemeChange = { viewModel.saveReaderTheme(it) },
                onBackgroundSelect = { viewModel.selectReaderBackground(it) },
                onAddBackgroundColor = { viewModel.addCustomReaderBackgroundColor(it) },
                onAddBackgroundImage = { viewModel.addCustomReaderBackgroundImage(it) },
                onDeleteBackground = { viewModel.deleteCustomReaderBackground(it) },
                onThemeSuiteSelect = viewModel::selectReaderThemeSuite,
                onThemeSuiteCreate = viewModel::createReaderThemeSuite,
                onThemeSuiteDelete = viewModel::deleteReaderThemeSuite,
                onThemeSuitesReorder = viewModel::reorderReaderThemeSuites,
                onPreserveEpubBackgroundChange = viewModel::savePreserveEpubBackground,
                onBrightnessChange = { viewModel.saveBrightness(it) },
                onOptimizeLayoutChange = { viewModel.saveOptimizeLayout(it) },
                onUseEpubCssChange = { viewModel.saveUseEpubCss(it) },
                onRenderModeChange = viewModel::saveRenderMode,
                onWritingModeChange = viewModel::saveReaderWritingMode,
                onChineseModeChange = { viewModel.saveChineseMode(it) },
                onPageTransitionChange = { viewModel.savePageTransition(it) },
                onDisplayModeChange = viewModel::saveReaderDisplayMode,
                onOpenAdvanced = {
                    if (!openAdvancedAfterThemeClose) {
                        openAdvancedAfterThemeClose = true
                        requestCloseTheme = true
                        scope.launch {
                            delay(if (eInkMode || !motionEnabled) 0L else 90L)
                            if (openAdvancedAfterThemeClose) {
                                showAdvancedSheet = true
                                openAdvancedAfterThemeClose = false
                            }
                        }
                    }
                },
                eInkModeEnabled = eInkMode,
                onDismiss = {
                    showThemeSheet = false
                    requestCloseTheme = false
                }
            )

            // 搜索弹窗
            SearchSheet(
                visible = showSearch,
                requestClose = requestCloseSearch,
                query = searchQuery,
                results = searchResults,
                isSearching = isSearching,
                hasSearched = hasSearched,
                onQueryChange = { value ->
                    if (value != searchQuery) {
                        cancelActiveSearch()
                        searchResults = emptyList()
                        searchResultQuery = ""
                        hasSearched = false
                    }
                    searchQuery = value
                },
                onSearch = {
                    submitSearch(searchQuery)
                },
                onResultClick = resultClick@{ result ->
                    if (searchResultQuery != searchQuery) return@resultClick
                    if (isBookLayout) {
                        val locator = result.epubLocator
                        if (locator == null) {
                            Toast.makeText(
                                context,
                                R.string.epub_search_location_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@resultClick
                        }
                        epubSearchRequestToken++
                        epubPendingFragment = null
                        epubLocatorRequest = null
                        epubPageRequest = null
                        epubSearchRequest = EpubSearchRequest(
                            token = epubSearchRequestToken,
                            chapterIndex = result.chapterIndex,
                            locator = locator
                        )
                        if (result.chapterIndex != uiState.currentChapterIndex) {
                            viewModel.setChapter(result.chapterIndex)
                        }
                    } else if (isContinuousScrollMode) {
                        continuousSearchHighlight = ContinuousSearchHighlight(
                            chapterIndex = result.chapterIndex,
                            start = result.charOffset,
                            end = result.charOffset + result.matchLength
                        )
                        jumpToContinuousChapter(result.chapterIndex)
                    } else {
                        readViewRef.value?.jumpToSearchResult(
                            result.chapterIndex,
                            result.charOffset,
                            result.matchLength
                        )
                    }
                    showSearch = false
                    cancelActiveSearch()
                    requestCloseSearch = false
                    searchQuery = ""
                    searchResultQuery = ""
                    searchResults = emptyList()
                    hasSearched = false
                },
                onDismiss = {
                    showSearch = false
                    cancelActiveSearch()
                    requestCloseSearch = false
                    searchQuery = ""
                    searchResultQuery = ""
                    searchResults = emptyList()
                    hasSearched = false
                }
            )

            // 高级排版设置弹窗
            val previewText = remember(uiState.currentChapterIndex) {
                viewModel.getChapterText(uiState.currentChapterIndex)
                    ?.toString()
                    ?.replace('\uFFFC', ' ')
                    ?.take(420) ?: ""
            }
            AdvancedSettingsSheet(
                visible = showAdvancedSheet,
                requestClose = requestCloseAdvanced,
                previewText = previewText,
                currentLineHeight = uiState.lineHeight,
                currentLetterSpacing = uiState.letterSpacing,
                currentTextAlignment = uiState.textAlignment,
                currentFontType = uiState.fontType,
                customFontPath = uiState.customFontPath,
                customFonts = uiState.customFonts,
                currentBackgroundSelection = effectiveReaderBackgroundSelection,
                customBackgrounds = uiState.customReaderBackgrounds,
                currentPreserveEpubBackground = effectivePreserveEpubBackground,
                showPreserveEpubBackground = uiState.book?.format?.name == "EPUB" &&
                    uiState.renderMode == EpubRenderMode.BOOK_LAYOUT,
                currentMarginLeft = uiState.marginLeftDp,
                currentMarginRight = uiState.marginRightDp,
                currentMarginTop = uiState.marginTopDp,
                currentMarginBottom = uiState.marginBottomDp,
                currentBgColor = Color(readerBackgroundColorInt),
                currentBackgroundImagePath = readerBackgroundImagePath,
                currentTextColor = Color(readerTextColorInt),
                currentTextColorOverride = effectiveReaderTextColor,
                currentFontSizeSp = uiState.fontSize,
                preservePublisherLayout = isBookLayout,
                currentWritingMode = uiState.readerWritingMode,
                fontDownloadKey = uiState.fontDownloadKey,
                fontDownloadFailed = uiState.fontDownloadFailed,
                onLineHeightChange = { viewModel.saveLineHeight(it) },
                onLetterSpacingChange = { viewModel.saveLetterSpacing(it) },
                onTextAlignmentChange = viewModel::saveTextAlignment,
                onFontTypeChange = { viewModel.saveFontType(it) },
                onImportFont = { uri ->
                    scope.launch {
                        val preset = viewModel.importFont(context, uri)
                        if (preset != null) {
                            viewModel.saveCustomFontPath(preset.path)
                            viewModel.saveFontType(preset.fontTypeKey)
                        }
                    }
                },
                onDeleteCustomFont = { id -> viewModel.deleteCustomFont(id) },
                onBackgroundSelect = viewModel::selectReaderBackground,
                onAddBackgroundColor = viewModel::addCustomReaderBackgroundColor,
                onAddBackgroundImage = viewModel::addCustomReaderBackgroundImage,
                onDeleteBackground = viewModel::deleteCustomReaderBackground,
                onPreserveEpubBackgroundChange = viewModel::savePreserveEpubBackground,
                onMarginLeftChange = { viewModel.saveMarginLeft(it) },
                onMarginRightChange = { viewModel.saveMarginRight(it) },
                onMarginTopChange = { viewModel.saveMarginTop(it) },
                onMarginBottomChange = { viewModel.saveMarginBottom(it) },
                currentParagraphSpacing = uiState.paragraphSpacing,
                currentFirstLineIndent = uiState.firstLineIndent,
                onParagraphSpacingChange = { viewModel.saveParagraphSpacing(it) },
                onFirstLineIndentChange = { viewModel.saveFirstLineIndent(it) },
                readerTopLeftContent = uiState.readerTopLeftContent,
                readerTopRightContent = uiState.readerTopRightContent,
                readerBottomLeftContent = uiState.readerBottomLeftContent,
                readerBottomRightContent = uiState.readerBottomRightContent,
                volumeKeyPageTurnEnabled = uiState.volumeKeyPageTurnEnabled,
                bionicReadingEnabled = uiState.bionicReadingEnabled,
                comicModeEnabled = uiState.comicModeEnabled,
                screenSleepTimeoutSeconds = uiState.screenSleepTimeoutSeconds,
                readerEdgeTapMode = uiState.readerEdgeTapMode,
                onReaderCornerContentChange = viewModel::saveReaderCornerContent,
                onVolumeKeyPageTurnEnabledChange = { viewModel.saveVolumeKeyPageTurnEnabled(it) },
                onBionicReadingEnabledChange = viewModel::saveBionicReadingEnabled,
                onComicModeChange = viewModel::saveComicMode,
                onScreenSleepTimeoutChange = { seconds ->
                    viewModel.saveScreenSleepTimeoutSeconds(seconds)
                    if (seconds != DataStoreManager.SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM &&
                        !Settings.System.canWrite(context)
                    ) {
                        requestWriteSettingsPermission()
                    }
                },
                onReaderEdgeTapModeChange = viewModel::saveReaderEdgeTapMode,
                onTextColorChange = { viewModel.saveReaderTextColor(it) },
                onResetSettings = {
                    if (isBookLayout) viewModel.resetBookLayoutReaderSettings()
                    else viewModel.resetAdvancedReaderSettings()
                },
                eInkModeEnabled = eInkMode,
                onDismiss = { showAdvancedSheet = false; requestCloseAdvanced = false }
            )
        }
    }

    // ── 笔记/高亮列表 ──
    NotesListSheet(
        visible = showNotesList,
        requestClose = requestCloseNotesList,
        glassBackdrop = activeReaderGlassBackdrop,
        notes = viewModel.notes.collectAsState().value,
        onNoteClick = noteClick@ { note ->
            if (isBookLayout) {
                val chapterTextLength = viewModel.getChapterTextLength(note.chapterIndex).coerceAtLeast(1)
                val chapterHref = epubSession?.chapterHref(note.chapterIndex).orEmpty()
                val locatorJson = note.startLocatorJson
                    ?.takeIf { isEpubLocatorForChapter(it, chapterHref) }
                    ?: createEpubFallbackLocator(
                        href = chapterHref,
                        charOffset = note.startPosition,
                        chapterTextLength = chapterTextLength,
                        exact = note.selectedText,
                        chapterText = viewModel.getChapterText(note.chapterIndex)
                    )
                epubPendingFragment = null
                epubSearchRequest = null
                epubPageRequest = null
                epubLocatorRequestToken++
                epubLocatorRequest = locatorJson?.let {
                    EpubLocatorRequest(
                        token = epubLocatorRequestToken,
                        chapterIndex = note.chapterIndex,
                        locatorJson = it
                    )
                }
                if (note.chapterIndex != uiState.currentChapterIndex) {
                    viewModel.setChapter(note.chapterIndex)
                }
            } else if (isContinuousScrollMode) {
                val resolved = viewModel.resolvedReaderNote(note) ?: return@noteClick
                jumpToContinuousChapter(resolved.chapterIndex)
            } else {
                val resolved = viewModel.resolvedReaderNote(note) ?: return@noteClick
                readViewRef.value?.jumpToCharacter(resolved.chapterIndex, resolved.startPosition)
            }
            showNotesList = false
            requestCloseNotesList = false
        },
        onDeleteNote = { note -> viewModel.deleteNote(note) },
        onDismiss = { showNotesList = false; requestCloseNotesList = false }
    )

    // ── 文字选择自定义菜单 ──
    val replaceSelectedAnnotationColor: (String, Int) -> Unit = { type, slot ->
        selectionState?.let { selection ->
            viewModel.replaceAnnotationRange(
                chapterIndex = selection.chapterIndex,
                startPosition = selection.charStart,
                endPosition = selection.charEnd,
                type = type,
                color = readerHighlightColorReference(slot, type)
            )
        }
        selectionState = null
        clearActiveTextSelection()
    }
    val removeSelectedAnnotation: (String) -> Unit = { type ->
        selectionState?.let { selection ->
            viewModel.removeAnnotationRange(
                chapterIndex = selection.chapterIndex,
                startPosition = selection.charStart,
                endPosition = selection.charEnd,
                type = type
            )
        }
        selectionState = null
        clearActiveTextSelection()
    }
    SelectionMenuOverlay(
        state = selectionState,
        readerTheme = renderingTheme,
        glassBackdrop = activeReaderGlassBackdrop,
        forceSolidSurface = isBookLayout,
        isDragging = isSelectionDragging,
        dismissOnBackgroundTap = !isVerticalWriting,
        reappearKey = menuReappearKey,
        showColorPicker = showHighlightColorPicker,
        showDictionaryAppPicker = showDictionaryAppPicker,
        showSettings = showMenuSettings,
        dictionaryAppOptions = dictionaryAppOptions,
        isTxtBook = uiState.book?.format?.name == "TXT",
        selectionMenuItems = uiState.selectionMenuItems,
        onDismiss = {
            selectionState = null
            resetSelectionSubmenus()
            clearActiveTextSelection()
        },
        onColorPicked = { slot ->
            pendingAnnotationColorTarget?.let { target ->
                val colorReference = readerHighlightColorReference(slot, target.noteType)
                val fresh = if (isBookLayout) null else readViewRef.value?.getSelectionInfo()
                if (fresh != null) {
                    viewModel.replaceAnnotationRange(
                        chapterIndex = fresh.chapterIndex,
                        startPosition = fresh.chapterStartOffset + fresh.pageStart,
                        endPosition = fresh.chapterStartOffset + fresh.pageEnd,
                        type = target.noteType,
                        color = colorReference
                    )
                } else {
                    selectionState?.let { selection ->
                        viewModel.replaceAnnotationRange(
                            chapterIndex = selection.chapterIndex,
                            startPosition = selection.charStart,
                            endPosition = selection.charEnd,
                            type = target.noteType,
                            color = colorReference
                        )
                    }
                }
            }
            selectionState = null
            resetSelectionSubmenus()
            clearActiveTextSelection()
        },
        onHighlight = {
            // 切换到颜色选择子菜单
            pendingAnnotationColorTarget = AnnotationColorTarget.HIGHLIGHT
            showHighlightColorPicker = true
        },
        onUnderline = {
            pendingAnnotationColorTarget = AnnotationColorTarget.UNDERLINE
            showHighlightColorPicker = true
        },
        onNote = {
            val fresh = if (isBookLayout) null else readViewRef.value?.getSelectionInfo()
            if (fresh != null) {
                pendingSelection = PendingSelection(
                    fresh.selectedText, fresh.chapterIndex,
                    fresh.chapterStartOffset + fresh.pageStart,
                    fresh.chapterStartOffset + fresh.pageEnd
                )
                showNoteInput = true
            } else {
                selectionState?.let { selection ->
                    pendingSelection = PendingSelection(
                        selection.selectedText,
                        selection.chapterIndex,
                        selection.charStart,
                        selection.charEnd,
                        selection.startLocatorJson,
                        selection.endLocatorJson
                    )
                    showNoteInput = true
                }
            }
            selectionState = null
            showHighlightColorPicker = false
            clearActiveTextSelection()
        },
        onSearch = {
            val query = if (isBookLayout) {
                selectionState?.selectedText
            } else {
                readViewRef.value?.getSelectionInfo()?.selectedText ?: selectionState?.selectedText
            }
            if (query != null) {
                showSearch = true
                searchQuery = query
                submitSearch(query)
            }
            selectionState = null
            showHighlightColorPicker = false
            clearActiveTextSelection()
        },
        onDictionary = {
            try {
                val fresh = if (isBookLayout) null else readViewRef.value?.getSelectionInfo()
                val text = fresh?.selectedText ?: selectionState?.selectedText
                val request = text?.let { prepareDictionaryLookup(context, it) }
                if (request == null || request.apps.isEmpty()) {
                    Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
                } else {
                    showHighlightColorPicker = false
                    dictionaryLookupText = request.normalizedText
                    dictionaryAppOptions = request.apps
                    showDictionaryAppPicker = true
                }
            } catch (throwable: Throwable) {
                Log.w(DICTIONARY_LOOKUP_TAG, "Failed to open dictionary app picker", throwable)
                Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
            }
        },
        onDictionaryAppSelected = { appOption ->
            if (launchDictionaryLookup(context, dictionaryLookupText, appOption)) {
                selectionState = null
                resetSelectionSubmenus()
                clearActiveTextSelection()
            }
        },
        onCopy = {
            val fresh = if (isBookLayout) null else readViewRef.value?.getSelectionInfo()
            val text = fresh?.selectedText ?: selectionState?.selectedText ?: return@SelectionMenuOverlay
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("selected", text))
            selectionState = null
            showHighlightColorPicker = false
            clearActiveTextSelection()
        },
        onViewNote = {
            // 🔥 查看/修改笔记：打开 NoteInputSheet 预填原笔记文字
            val existing = selectionState?.existingNote
            if (existing != null) {
                editingNote = existing
                noteInputText = existing.note
                showNoteInput = true
            }
            selectionState = null
            showHighlightColorPicker = false
            clearActiveTextSelection()
        },
        onChangeHighlightColor = { slot ->
            replaceSelectedAnnotationColor("highlight", slot)
        },
        onChangeUnderlineColor = { slot ->
            replaceSelectedAnnotationColor("underline", slot)
        },
        onDeleteHighlight = {
            removeSelectedAnnotation("highlight")
        },
        onDeleteUnderline = {
            removeSelectedAnnotation("underline")
        },
        onReplace = {
            // 替换功能：仅TXT书籍支持
            if (uiState.book?.format?.name != "TXT") {
                Toast.makeText(context, R.string.replace_not_available, Toast.LENGTH_SHORT).show()
                return@SelectionMenuOverlay
            }
            val fresh = if (isBookLayout) null else readViewRef.value?.getSelectionInfo()
            val text = fresh?.selectedText ?: selectionState?.selectedText
            if (text != null) {
                replaceSelection = ReplaceSelectionInfo(
                    selectedText = text,
                    chapterIndex = fresh?.chapterIndex ?: selectionState?.chapterIndex,
                    charStart = fresh?.let { it.chapterStartOffset + it.pageStart }
                        ?: selectionState?.charStart,
                    charEnd = fresh?.let { it.chapterStartOffset + it.pageEnd }
                        ?: selectionState?.charEnd
                )
            }
            selectionState = null
            showHighlightColorPicker = false
            clearActiveTextSelection()
            if (text != null) {
                scope.launch {
                    // The selection glass must leave the layout tree before the replacement
                    // overlay starts sampling the reader backdrop.
                    withFrameNanos { }
                    withFrameNanos { }
                    showReplaceInput = true
                }
            }
        },
        onMenuSettings = {
            showMenuSettings = true
        }
    )

    // ── 浮动菜单设置 Dialog ──
    SelectionMenuSettingsDialog(
        visible = showMenuSettings,
        currentItems = uiState.selectionMenuItems,
        onDismiss = { showMenuSettings = false },
        onSave = { items ->
            viewModel.saveSelectionMenuItems(items)
            showMenuSettings = false
        }
    )

    // ── 替换输入 Sheet ──
    ReplaceInputSheet(
        visible = showReplaceInput,
        glassBackdrop = activeReaderGlassBackdrop,
        selectedText = replaceSelection?.selectedText.orEmpty(),
        canReplaceCurrent = replaceSelection?.let {
            it.chapterIndex != null && it.charStart != null && it.charEnd != null
        } == true,
        onReplaceAll = { replacement ->
            val selection = replaceSelection
            if (selection != null) {
                viewModel.replaceTxtText(
                    searchText = selection.selectedText,
                    replaceWith = replacement,
                    onResult = { replaced ->
                        Toast.makeText(
                            context,
                            if (replaced) R.string.replace_success else R.string.replace_no_match,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        },
        onReplaceCurrent = { replacement ->
            val selection = replaceSelection
            if (selection?.chapterIndex != null && selection.charStart != null && selection.charEnd != null) {
                viewModel.replaceTxtRange(
                    chapterIndex = selection.chapterIndex,
                    start = selection.charStart,
                    endExclusive = selection.charEnd,
                    replaceWith = replacement,
                    onResult = { replaced ->
                        Toast.makeText(
                            context,
                            if (replaced) R.string.replace_success else R.string.replace_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        },
        onDismiss = {
            showReplaceInput = false
            replaceSelection = null
        }
    )

    if (uiState.showEpubLayoutHint) {
        ReaderFirstOpenHintDialog(
            title = stringResource(R.string.epub_layout_first_open_title),
            message = stringResource(R.string.epub_layout_first_open_message),
            confirmText = stringResource(R.string.epub_layout_first_open_confirm),
            backdrop = activeReaderGlassBackdrop,
            onDismiss = viewModel::dismissEpubLayoutHint
        )
    }

    if (uiState.showMobiLayoutHint) {
        ReaderFirstOpenHintDialog(
            title = stringResource(R.string.mobi_layout_first_open_title),
            message = stringResource(R.string.mobi_layout_first_open_message),
            confirmText = stringResource(R.string.mobi_layout_first_open_confirm),
            backdrop = activeReaderGlassBackdrop,
            onDismiss = viewModel::dismissMobiLayoutHint
        )
    }

    if (uiState.showTxtEncodingHint) {
        ReaderFirstOpenHintDialog(
            title = stringResource(R.string.txt_encoding_first_open_title),
            message = stringResource(R.string.txt_encoding_first_open_message),
            confirmText = stringResource(R.string.txt_encoding_first_open_confirm),
            backdrop = activeReaderGlassBackdrop,
            onDismiss = viewModel::dismissTxtEncodingHint
        )
    }

    if (showTxtEncodingDialog) {
        TxtEncodingDialog(
            currentEncoding = uiState.txtEncoding,
            activeCharsetName = uiState.txtActiveCharsetName,
            isEncodingChanging = uiState.isTxtEncodingChanging,
            backdrop = activeReaderGlassBackdrop,
            onEncodingSelected = viewModel::saveTxtEncoding,
            onDismiss = { showTxtEncodingDialog = false }
        )
    }

    // 🔥 笔记输入弹窗（自定义菜单触发"笔记"时弹出）
    pendingExternalLink?.let { href ->
        AlertDialog(
            onDismissRequest = { pendingExternalLink = null },
            title = { Text(stringResource(R.string.reader_external_link_title)) },
            text = { Text(stringResource(R.string.reader_external_link_message, href)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExternalLink = null
                        openExternalBookLink(context, href)
                    }
                ) { Text(stringResource(R.string.reader_external_link_open)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalLink = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    NoteInputSheet(
        visible = showNoteInput,
        requestClose = requestCloseNoteInput,
        glassBackdrop = activeReaderGlassBackdrop,
        initialText = noteInputText,
        onTextChange = { noteInputText = it },
        onConfirm = {
            val editing = editingNote
            if (editing != null) {
                // 编辑模式：更新已有笔记
                viewModel.updateNote(editing.copy(note = noteInputText))
                editingNote = null
            } else {
                // 新建模式
                val ps = pendingSelection ?: return@NoteInputSheet
                viewModel.addNote(
                    selectedText = ps.selectedText,
                    noteText = noteInputText,
                    chapterIndex = ps.chapterIndex,
                    startPosition = ps.startPosition,
                    endPosition = ps.endPosition,
                    color = DefaultReaderHighlightColorWithAlpha,
                    startLocatorJson = ps.startLocatorJson,
                    endLocatorJson = ps.endLocatorJson
                )
                pendingSelection = null
            }
            noteInputText = ""
            clearActiveTextSelection()
        },
        onDismiss = {
            showNoteInput = false
            requestCloseNoteInput = false
            pendingSelection = null
            editingNote = null
            noteInputText = ""
        }
    )

    if (!isBookLayout) {
        val preview = readerImagePreview
        val session = epubSession
        if (preview != null && session != null) {
            EpubImagePreviewOverlay(
                session = session,
                request = preview,
                progress = readerImagePreviewProgress.value,
                onDismissRequest = dismissReaderImagePreview
            )
        }
    }
}
}

@Composable
private fun ReaderPageCornerOverlay(
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    rightPageIndex: Int? = null,
    leftMarginDp: Float,
    rightMarginDp: Float,
    topLeft: ReaderCornerContent,
    topRight: ReaderCornerContent,
    bottomLeft: ReaderCornerContent,
    bottomRight: ReaderCornerContent,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (topLeft != ReaderCornerContent.NONE || topRight != ReaderCornerContent.NONE) {
            ReaderCornerStatusRow(
                left = topLeft,
                right = topRight,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                rightPageIndex = rightPageIndex,
                contentColor = contentColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        start = leftMarginDp.coerceAtLeast(0f).dp,
                        top = 20.dp,
                        end = rightMarginDp.coerceAtLeast(0f).dp
                    )
            )
        }
        if (bottomLeft != ReaderCornerContent.NONE || bottomRight != ReaderCornerContent.NONE) {
            ReaderCornerStatusRow(
                left = bottomLeft,
                right = bottomRight,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                rightPageIndex = rightPageIndex,
                contentColor = contentColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = leftMarginDp.coerceAtLeast(0f).dp,
                        end = rightMarginDp.coerceAtLeast(0f).dp,
                        bottom = 20.dp
                    )
            )
        }
        }
    }

@Composable
private fun ReaderCornerStatusRow(
    left: ReaderCornerContent,
    right: ReaderCornerContent,
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    rightPageIndex: Int? = null,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            ReaderCornerContentValue(
                content = left,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                rightPageIndex = rightPageIndex,
                contentColor = contentColor,
                alignEnd = false
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            ReaderCornerContentValue(
                content = right,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                rightPageIndex = rightPageIndex,
                contentColor = contentColor,
                alignEnd = true
            )
        }
    }
}

@Composable
private fun ReaderCornerContentValue(
    content: ReaderCornerContent,
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    rightPageIndex: Int? = null,
    contentColor: Color,
    alignEnd: Boolean
) {
    when (content) {
        ReaderCornerContent.NONE -> Unit
        ReaderCornerContent.BATTERY -> ReaderBatteryStatus(contentColor)
        ReaderCornerContent.TIME -> ReaderTimeStatus(contentColor)
        else -> Text(
            text = when (content) {
                ReaderCornerContent.CHAPTER_INFO -> chapterTitle
                ReaderCornerContent.BOOK_PROGRESS -> formatReadingProgressPercent(bookProgressPercent)
                ReaderCornerContent.PAGE_NUMBER -> formatReaderPageLabel(
                    currentPage, rightPageIndex, chapterPageCount
                )
                else -> ""
            },
            color = contentColor,
            fontSize = AppType.Caption,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReaderTimeStatus(contentColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var timeText by remember { mutableStateOf(formatReaderClock(context, System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            timeText = formatReaderClock(context, now)
            delay(60_000L - now % 60_000L)
        }
    }
    Text(
        text = timeText,
        color = contentColor,
        fontSize = AppType.Caption,
        lineHeight = AppType.Caption,
        maxLines = 1,
        modifier = modifier
    )
}

private fun formatReaderClock(context: Context, timestampMillis: Long): String {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .format(java.util.Date(timestampMillis))
}

@Composable
private fun ReaderBatteryStatus(contentColor: Color, modifier: Modifier = Modifier) {
    val batteryPercent = rememberBatteryPercentage()
    val batteryDescription = stringResource(R.string.reader_battery_level, batteryPercent)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = batteryDescription
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalBatteryIcon(
            batteryPercent = batteryPercent,
            color = contentColor,
            modifier = Modifier
                .alignBy { it.measuredHeight }
                .offset(y = 1.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$batteryPercent%",
            color = contentColor,
            fontSize = AppType.Caption,
            lineHeight = AppType.Caption,
            maxLines = 1,
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
private fun HorizontalBatteryIcon(
    batteryPercent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.width(21.dp).height(11.dp)) {
        val strokeWidth = 1.dp.toPx()
        val terminalWidth = 1.5.dp.toPx()
        val terminalGap = 0.75.dp.toPx()
        val bodyWidth = size.width - terminalWidth - terminalGap
        val radius = 3.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.62f),
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokeWidth)
        )
        val innerPadding = 1.75.dp.toPx()
        val fillWidth = ((bodyWidth - innerPadding * 2f) *
            (batteryPercent.coerceIn(0, 100) / 100f)).coerceAtLeast(0f)
        if (fillWidth > 0f) {
            drawRoundRect(
                color = if (batteryPercent <= 20) Color(0xFFFF453A) else color,
                topLeft = Offset(innerPadding, innerPadding),
                size = Size(fillWidth, (size.height - innerPadding * 2f).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
        drawRoundRect(
            color = color.copy(alpha = 0.62f),
            topLeft = Offset(bodyWidth + terminalGap, size.height * 0.32f),
            size = Size(terminalWidth, size.height * 0.4f),
            cornerRadius = CornerRadius(terminalWidth / 2f, terminalWidth / 2f)
        )
    }
}

@Composable
private fun rememberBatteryPercentage(): Int {
    val context = LocalContext.current.applicationContext
    val batteryManager = remember(context) {
        context.getSystemService(BatteryManager::class.java)
    }
    var batteryPercent by remember(context) {
        mutableIntStateOf(
            batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
                ?: 0
        )
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    batteryPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return batteryPercent
}

private fun isEpubLocatorForChapter(locatorJson: String, chapterHref: String): Boolean {
    if (chapterHref.isBlank()) return false
    return runCatching { JSONObject(locatorJson).optString("href") == chapterHref }.getOrDefault(false)
}

private fun createEpubFallbackLocator(
    href: String,
    charOffset: Int,
    chapterTextLength: Int,
    exact: String = "",
    chapterText: CharSequence? = null
): String? {
    if (href.isBlank()) return null
    val progression = charOffset.coerceAtLeast(0).toDouble() / chapterTextLength.coerceAtLeast(1).toDouble()
    val safeOffset = charOffset.coerceIn(0, chapterText?.length ?: chapterTextLength)
    val safeEnd = (safeOffset + exact.length).coerceAtMost(chapterText?.length ?: safeOffset)
    return JSONObject()
        .put("version", 2)
        .put("href", href)
        .put("textPosition", safeOffset)
        .put("textLength", chapterText?.length ?: chapterTextLength)
        .put("textOffset", 0)
        .put("exact", exact)
        .put(
            "prefix",
            chapterText?.subSequence(maxOf(0, safeOffset - 32), safeOffset)?.toString().orEmpty()
        )
        .put(
            "suffix",
            chapterText?.subSequence(safeEnd, minOf(chapterText.length, safeEnd + 32))?.toString().orEmpty()
        )
        .put("progression", progression.coerceIn(0.0, 1.0))
        .toString()
}

private fun isExternalBookLink(href: String): Boolean {
    val uri = runCatching { android.net.Uri.parse(href.trim()) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https", "mailto", "tel")
}

private fun openExternalBookLink(context: Context, href: String): Boolean {
    val uri = runCatching { android.net.Uri.parse(href.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme !in setOf("http", "https", "mailto", "tel")) return false

    return runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
    }.isSuccess
}

/**
 * 旧 WebView 路径（PDF 格式保留使用）。
 * 简化版：单 WebView，无跨章 conveyor。
 */
@Composable
private fun LegacyWebViewContent(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    bgColor: Color
) {
    if (uiState.chapterHtml.isEmpty()) return

    val context = LocalContext.current
    val currentFontSize = remember { mutableFloatStateOf(uiState.fontSize) }
    val currentTheme = remember { mutableStateOf(uiState.readerTheme) }
    var prevFontSize by remember { mutableFloatStateOf(uiState.fontSize) }
    var prevTheme by remember { mutableStateOf(uiState.readerTheme) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // JS bridge for PDF (simplified)
    val bridge = remember {
        object {
            @android.webkit.JavascriptInterface
            fun onPageChanged(page: Int, total: Int) {
                viewModel.onPageChanged(page, total)
            }
            @android.webkit.JavascriptInterface
            fun onCenterTap() { viewModel.toggleMenu() }
            @android.webkit.JavascriptInterface
            fun onPaginationComplete() { viewModel.onPaginationDone() }
            @android.webkit.JavascriptInterface
            fun onPageFlip(dir: Int) {}
            @android.webkit.JavascriptInterface
            fun onChapterFlipReady(dir: Int) {
                if (dir > 0) viewModel.nextChapter() else viewModel.previousChapter()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                addJavascriptInterface(bridge, "AndroidBridge")
                val bgJs = when (uiState.readerTheme) {
                    "night" -> "#1a1a1a"; "sepia" -> "#f5e6d3"; "green" -> "#e8f5e9"; else -> "#ffffff"
                }
                val textJs = when (uiState.readerTheme) {
                    "night" -> "#e0e0e0"; "sepia" -> "#3e2723"; "green" -> "#1b5e20"; else -> "#333333"
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val fs = currentFontSize.floatValue
                        view?.postDelayed({
                            val js = """
(function(){
var vw=innerWidth,vh=innerHeight;
var b=document.body;b.style.margin='0';b.style.padding='0';b.style.overflow='hidden';
b.style.width=vw+'px';b.style.height=vh+'px';
b.style.visibility='visible';
try{AndroidBridge.onPaginationComplete();}catch(e){}
try{AndroidBridge.onPageChanged(0,1);}catch(e){}
})();
""".trimIndent()
                            view.evaluateJavascript(js) {}
                        }, 300)
                    }
                }
                setBackgroundColor(android.graphics.Color.parseColor(bgJs))
                webViewRef.value = this
            }
        },
        update = { webView ->
            currentFontSize.floatValue = uiState.fontSize
            currentTheme.value = uiState.readerTheme
            val html = viewModel.getChapterHtml(uiState.currentChapterIndex)
            val tag = webView.tag as? String
            if (html.isNotEmpty() && tag != html.hashCode().toString()) {
                webView.tag = html.hashCode().toString()
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            val bgJs = when (uiState.readerTheme) {
                "night" -> "#1a1a1a"; "sepia" -> "#f5e6d3"; "green" -> "#e8f5e9"; else -> "#ffffff"
            }
            val textJs = when (uiState.readerTheme) {
                "night" -> "#e0e0e0"; "sepia" -> "#3e2723"; "green" -> "#1b5e20"; else -> "#333333"
            }
            webView.evaluateJavascript("document.body.style.background='$bgJs';document.body.style.color='$textJs';document.body.style.fontSize='${uiState.fontSize}px';") {}
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun LinkReturnButton(
    backgroundColor: Color,
    contentColor: Color,
    glassContentScrimColor: Color,
    forceSolid: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(AppRadius.capsule),
        fallbackColor = backgroundColor,
        contentScrimColor = glassContentScrimColor,
        forceFallback = forceSolid,
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 72.dp),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.reader_link_return),
                color = contentColor,
                fontSize = AppType.Caption,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReaderFirstOpenHintDialog(
    title: String,
    message: String,
    confirmText: String,
    backdrop: Backdrop?,
    onDismiss: () -> Unit
) {
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.78f),
        backgroundScrimColor = Color.Black.copy(alpha = 0.10f),
        backgroundBlurRadius = 0.dp,
        transparencyOverride = 0.28f,
        title = {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message,
                color = AppColors.TextSecondary,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = confirmText,
                onClick = onDismiss,
                tintedColor = AppColors.Accent,
                contentColor = AppColors.OnAccent
            )
        }
    )
}

@Composable
private fun TxtEncodingDialog(
    currentEncoding: TxtEncoding,
    activeCharsetName: String,
    isEncodingChanging: Boolean,
    backdrop: Backdrop?,
    onEncodingSelected: (TxtEncoding) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEncoding by remember { mutableStateOf(currentEncoding) }
    LaunchedEffect(currentEncoding, isEncodingChanging) {
        if (!isEncodingChanging) selectedEncoding = currentEncoding
    }

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
        backgroundScrimColor = Color.Black.copy(alpha = 0.10f),
        backgroundBlurRadius = 0.dp,
        transparencyOverride = 0.24f,
        title = {
            Text(
                text = stringResource(R.string.txt_encoding_dialog_title),
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.txt_encoding_dialog_message, activeCharsetName),
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    if (isEncodingChanging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = AppColors.Accent,
                            strokeWidth = 2.dp
                        )
                    }
                }
                TxtEncoding.entries.chunked(2).forEach { rowEncodings ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowEncodings.forEach { encoding ->
                            TxtEncodingCapsule(
                                encoding = encoding,
                                activeCharsetName = activeCharsetName,
                                selected = encoding == selectedEncoding,
                                enabled = !isEncodingChanging,
                                onClick = {
                                    if (encoding != selectedEncoding) {
                                        selectedEncoding = encoding
                                        onEncodingSelected(encoding)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowEncodings.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.confirm),
                onClick = onDismiss,
                tintedColor = AppColors.Accent,
                contentColor = AppColors.OnAccent
            )
        }
    )
}

@Composable
private fun TxtEncodingCapsule(
    encoding: TxtEncoding,
    activeCharsetName: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) AppColors.Accent else AppColors.BgGray
    val contentColor = if (selected) AppColors.OnAccent else AppColors.TextPrimary
    val label = when (encoding) {
        TxtEncoding.AUTO -> stringResource(R.string.txt_encoding_auto, activeCharsetName)
        TxtEncoding.UTF_8 -> stringResource(R.string.txt_encoding_utf8)
        TxtEncoding.GB18030 -> stringResource(R.string.txt_encoding_gb18030)
        TxtEncoding.BIG5 -> stringResource(R.string.txt_encoding_big5)
        TxtEncoding.UTF_16LE -> stringResource(R.string.txt_encoding_utf16le)
        TxtEncoding.UTF_16BE -> stringResource(R.string.txt_encoding_utf16be)
        TxtEncoding.SHIFT_JIS -> stringResource(R.string.txt_encoding_shift_jis)
        TxtEncoding.EUC_KR -> stringResource(R.string.txt_encoding_euc_kr)
        TxtEncoding.WINDOWS_1252 -> stringResource(R.string.txt_encoding_windows_1252)
    }

    LiquidGlassSurface(
        shape = RoundedCornerShape(50),
        fallbackColor = backgroundColor,
        contentScrimColor = backgroundColor.copy(alpha = if (selected) 0.86f else 0.52f),
        transparencyOverride = if (selected) 0.12f else 0.34f,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    bgColor: Color = Color.White,
    contentColor: Color = AppColors.TextPrimary,
    glassContentScrimColor: Color = Color.Transparent,
    forceSolidButtons: Boolean = false,
    isTtsActive: Boolean = false,
    onTtsClick: () -> Unit = {},
    isBookmarked: Boolean = false,
    onBookmarkToggle: () -> Unit = {},
    isTxtBook: Boolean = false,
    onEditClick: () -> Unit = {},
    onEncodingClick: () -> Unit = {}
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    val controlBackground = if (forceSolidButtons) {
        if (contentColor == Color.White) Color(0xFF3A3A3C) else Color(0xFFF2F2F7)
    } else if (contentColor == Color.White) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        Color(0xFFF2F2F7).copy(alpha = 0.8f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTxtBook) 250.dp else 140.dp)
    ) {
        if (!isLiquidGlass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to bgColor,
                                0.3f to bgColor,
                                0.6f to bgColor.copy(alpha = 0.85f),
                                0.85f to bgColor.copy(alpha = 0.3f),
                                1.0f to bgColor.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 28.dp, top = 42.dp, end = 28.dp, bottom = 0.dp),
            verticalAlignment = Alignment.Top
        ) {
            ReaderTopBarButton(
                icon = Icons.Default.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.reader_back),
                tint = contentColor,
                backgroundColor = controlBackground,
                contentScrimColor = glassContentScrimColor,
                    forceSolid = forceSolidButtons,
                onClick = onBack
            )
            ReaderTitleCapsule(
                title = title,
                contentColor = contentColor.copy(alpha = if (isLiquidGlass) 0.88f else 0.7f),
                fallbackColor = controlBackground,
                glassContentScrimColor = glassContentScrimColor,
                isLiquidGlass = isLiquidGlass,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.Top)
                    .padding(horizontal = 8.dp)
            )
            // 右侧按钮竖向排列
            Column(
                modifier = Modifier.width(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top)
            ) {
                ReaderTopBarButton(
                    icon = Icons.Default.Headphones,
                    contentDescription = stringResource(R.string.tts_listen),
                    tint = if (isTtsActive) AppColors.Accent else contentColor,
                    backgroundColor = controlBackground,
                    contentScrimColor = glassContentScrimColor,
                    forceSolid = forceSolidButtons,
                    onClick = onTtsClick
                )
                ReaderTopBarButton(
                        icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.reader_bookmark),
                        tint = if (isBookmarked) AppColors.Accent else contentColor,
                        backgroundColor = controlBackground,
                        contentScrimColor = glassContentScrimColor,
                    forceSolid = forceSolidButtons,
                        onClick = onBookmarkToggle
                    )
                    if (isTxtBook) {
                        ReaderTopBarButton(
                            icon = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.reader_edit),
                            tint = contentColor,
                            backgroundColor = controlBackground,
                            contentScrimColor = glassContentScrimColor,
                            forceSolid = forceSolidButtons,
                            onClick = onEditClick
                        )
                        ReaderTopBarButton(
                            icon = Icons.Default.TextFields,
                            contentDescription = stringResource(R.string.reader_switch_encoding),
                            tint = contentColor,
                            backgroundColor = controlBackground,
                            contentScrimColor = glassContentScrimColor,
                            forceSolid = forceSolidButtons,
                            onClick = onEncodingClick
                        )
                    }
            }
        }
    }
}

@Composable
private fun ReaderTopBarButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    backgroundColor: Color,
    contentScrimColor: Color,
    forceSolid: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        shape = CircleShape,
        fallbackColor = backgroundColor,
        contentScrimColor = contentScrimColor,
        forceFallback = forceSolid,
        modifier = Modifier
            .size(36.dp),
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FloatingReaderMenu(
    visible: Boolean,
    chapterTitle: String,
    chapterTitles: List<String>,
    chapterCount: Int,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    rightPageIndex: Int? = null,
    capsuleBgColor: Color,
    capsuleContentColor: Color,
    readerContentColor: Color,
    catalogProgressColor: Color,
    glassContentScrimColor: Color,
    forceSolidCapsules: Boolean,
    canGoToPreviousChapter: Boolean,
    canGoToNextChapter: Boolean,
    onCatalogClick: () -> Unit,
    onPreviousChapterClick: () -> Unit,
    onNextChapterClick: () -> Unit,
    onCatalogProgressDragStart: (() -> Unit)? = null,
    onCatalogProgressDragEnd: ((Float) -> Unit)? = null,
    onCatalogProgressDragCancel: (() -> Unit)? = null,
    onBookmarkClick: () -> Unit,
    onSearchClick: () -> Unit,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eInkMode = LocalEInkMode.current
    val alpha0 = remember { Animatable(0f) }
    val offset0 = remember { Animatable(40f) }
    val alpha1 = remember { Animatable(0f) }
    val offset1 = remember { Animatable(40f) }
    val alpha2 = remember { Animatable(0f) }
    val offset2 = remember { Animatable(40f) }
    val alpha3 = remember { Animatable(0f) }
    val offset3 = remember { Animatable(40f) }

    LaunchedEffect(visible, eInkMode) {
        if (eInkMode) {
            val alpha = if (visible) 1f else 0f
            val offset = if (visible) 0f else 40f
            alpha0.snapTo(alpha); offset0.snapTo(offset)
            alpha1.snapTo(alpha); offset1.snapTo(offset)
            alpha2.snapTo(alpha); offset2.snapTo(offset)
            alpha3.snapTo(alpha); offset3.snapTo(offset)
        } else if (visible) {
            alpha0.snapTo(0f); offset0.snapTo(40f)
            alpha1.snapTo(0f); offset1.snapTo(40f)
            alpha2.snapTo(0f); offset2.snapTo(40f)
            alpha3.snapTo(0f); offset3.snapTo(40f)
            launch { alpha0.animateTo(1f, tween(250)); offset0.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
            kotlinx.coroutines.delay(100)
            launch { alpha1.animateTo(1f, tween(250)); offset1.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
            kotlinx.coroutines.delay(100)
            launch { alpha2.animateTo(1f, tween(250)); offset2.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
            kotlinx.coroutines.delay(100)
            launch { alpha3.animateTo(1f, tween(250)); offset3.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
        } else {
            alpha0.snapTo(0f); offset0.snapTo(40f)
            alpha1.snapTo(0f); offset1.snapTo(40f)
            alpha2.snapTo(0f); offset2.snapTo(40f)
            alpha3.snapTo(0f); offset3.snapTo(40f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.graphicsLayer {
            alpha = alpha0.value
            translationY = offset0.value
        }) {
            CatalogCapsule(
                title = chapterTitle,
                chapterTitles = chapterTitles,
                chapterCount = chapterCount,
                progress = bookProgressPercent,
                bgColor = capsuleBgColor,
                contentColor = capsuleContentColor,
                progressColor = catalogProgressColor,
                glassContentScrimColor = glassContentScrimColor,
                forceSolid = forceSolidCapsules,
                enabled = visible,
                canGoToPreviousChapter = canGoToPreviousChapter,
                canGoToNextChapter = canGoToNextChapter,
                onClick = onCatalogClick,
                onPreviousChapterClick = onPreviousChapterClick,
                onNextChapterClick = onNextChapterClick,
                onProgressDragStart = onCatalogProgressDragStart,
                onProgressDragEnd = onCatalogProgressDragEnd,
                onProgressDragCancel = onCatalogProgressDragCancel
            )
        }
        ReaderMenuStatus(
            chapterTitle = chapterTitle,
            bookProgressPercent = bookProgressPercent,
            currentPage = currentPage,
            chapterPageCount = chapterPageCount,
            rightPageIndex = rightPageIndex,
            contentColor = readerContentColor
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha1.value; translationY = offset1.value
            }) {
                ActionCapsule(Icons.Default.Bookmark, stringResource(R.string.reader_notes), capsuleBgColor, capsuleContentColor, glassContentScrimColor, forceSolidCapsules, Modifier.fillMaxWidth(), enabled = visible, onBookmarkClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha2.value; translationY = offset2.value
            }) {
                ActionCapsule(Icons.Default.Search, stringResource(R.string.reader_search), capsuleBgColor, capsuleContentColor, glassContentScrimColor, forceSolidCapsules, Modifier.fillMaxWidth(), enabled = visible, onSearchClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha3.value; translationY = offset3.value
            }) {
                ActionCapsule(Icons.Default.Settings, stringResource(R.string.reader_theme), capsuleBgColor, capsuleContentColor, glassContentScrimColor, forceSolidCapsules, Modifier.fillMaxWidth(), enabled = visible, onThemeClick)
            }
        }
    }
}

@Composable
private fun ReaderMenuStatus(
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    rightPageIndex: Int? = null,
    contentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapterTitle,
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatReadingProgressPercent(bookProgressPercent),
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatReaderPageLabel(currentPage, rightPageIndex, chapterPageCount),
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        ReaderBatteryStatus(contentColor.copy(alpha = 0.68f))
    }
}

@Composable
private fun CatalogCapsule(
    title: String,
    chapterTitles: List<String>,
    chapterCount: Int,
    progress: Float,
    bgColor: Color,
    contentColor: Color,
    progressColor: Color,
    glassContentScrimColor: Color,
    forceSolid: Boolean,
    enabled: Boolean = true,
    canGoToPreviousChapter: Boolean,
    canGoToNextChapter: Boolean,
    onClick: () -> Unit,
    onPreviousChapterClick: () -> Unit,
    onNextChapterClick: () -> Unit,
    onProgressDragStart: (() -> Unit)? = null,
    onProgressDragEnd: ((finalProgress: Float) -> Unit)? = null,
    onProgressDragCancel: (() -> Unit)? = null,
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current && !forceSolid
    val density = androidx.compose.ui.platform.LocalDensity.current

    var dragProgress by remember { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }
    val dragSession = remember { CatalogProgressDragSession() }
    LaunchedEffect(progress) { if (!isDragging) dragProgress = progress }

    val displayProgress = if (isDragging) dragProgress else progress

    // 关闭 LiquidGlassSurface 内部手势（interactive=false），
    // 统一在外层用 awaitEachGesture 处理：短按→onClick，横向滑动→改进度
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnDragStart by rememberUpdatedState(onProgressDragStart)
    val latestOnDragEnd by rememberUpdatedState(onProgressDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onProgressDragCancel)
    val latestExternalProgress by rememberUpdatedState(progress)

    val previewTarget = mapGlobalProgress(displayProgress, chapterCount)
    val previewChapterIndex = previewTarget?.chapterIndex ?: 0
    val previewFallbackTitle = if (previewTarget != null) {
        stringResource(R.string.reader_chapter_fallback, previewChapterIndex + 1)
    } else {
        ""
    }
    val previewChapterTitle = chapterTitles
        .getOrNull(previewChapterIndex)
        ?.trim()
        .orEmpty()
        .ifBlank { previewFallbackTitle }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-44).dp)
                .widthIn(max = 300.dp)
        ) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                fallbackColor = bgColor,
                contentScrimColor = glassContentScrimColor,
                forceFallback = !isLiquidGlass,
                modifier = Modifier.height(36.dp),
                onClick = null,
                interactive = false,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = previewChapterTitle,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        LiquidGlassSurface(
            shape = RoundedCornerShape(24.dp),
            fallbackColor = bgColor,
            contentScrimColor = glassContentScrimColor,
            forceFallback = forceSolid,
            modifier = Modifier.fillMaxSize(),
            onClick = null,
            interactive = false,
            contentAlignment = Alignment.TopStart
        ) {
            if (isLiquidGlass) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 5.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((displayProgress / 100f).coerceIn(0f, 1f))
                            .clip(CircleShape)
                            .background(AppColors.Accent.copy(alpha = 0.82f))
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((displayProgress / 100f).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(24.dp))
                            .background(progressColor)
                    )
                }
            }
            val leftColor = if (isLiquidGlass) {
                contentColor
            } else if (displayProgress > 5f) {
                Color.White
            } else {
                contentColor
            }
            val rightColor = if (isLiquidGlass) {
                contentColor.copy(alpha = 0.62f)
            } else if (displayProgress > 70f) {
                Color.White.copy(alpha = 0.9f)
            } else {
                contentColor.copy(alpha = 0.5f)
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = leftColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reader_toc), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = leftColor)
                Spacer(Modifier.weight(1f))
                Text(formatReadingProgressPercent(displayProgress), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rightColor)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
                .then(
                    // 菜单隐藏时整个 pointerInput 不挂载：父级用 graphicsLayer alpha=0 淡出，
                    // 但 graphicsLayer 不影响命中测试，挂着的空手势块仍会拦下正文的滑动/长按
                    if (enabled) Modifier.pointerInput(onProgressDragEnd != null) {
                        if (onProgressDragEnd == null) {
                            detectTapGestures(onTap = { latestOnClick() })
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var cumDrag = 0f
                            var dragging = false
                            var committed = false

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val dx = ch.positionChange().x

                                    if (!dragging) {
                                        cumDrag += dx
                                        if (kotlin.math.abs(cumDrag) >= viewConfiguration.touchSlop) {
                                            dragging = true
                                            isDragging = true
                                            dragSession.begin(latestExternalProgress)
                                            latestOnDragStart?.invoke()
                                            val dragDp = with(density) { cumDrag.toDp().value }
                                            dragProgress = dragSession.dragBy(dragDp * 0.25f)
                                            ch.consume()
                                        }
                                    } else {
                                        ch.consume()
                                        val dragDp = with(density) { dx.toDp().value }
                                        dragProgress = dragSession.dragBy(dragDp * 0.25f)
                                    }

                                    if (ch.changedToUpIgnoreConsumed()) {
                                        if (!dragging) {
                                            latestOnClick()
                                        } else {
                                            committed = true
                                            dragSession.finish { latestOnDragEnd?.invoke(it) }
                                        }
                                        break
                                    }
                                    if (!ch.pressed) break
                                }
                            } finally {
                                if (dragging && !committed) {
                                    dragSession.cancel()
                                    latestOnDragCancel?.invoke()
                                }
                                isDragging = false
                            }
                        }
                    } else Modifier
                )
        )

        CatalogChapterButton(
            icon = Icons.Default.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.reader_previous_chapter),
            contentColor = if (isLiquidGlass || displayProgress <= 5f) contentColor else Color.White,
            fallbackColor = contentColor.copy(alpha = 0.14f),
            glassContentScrimColor = glassContentScrimColor,
            glassHighlightColor = Color.White,
            forceSolid = !isLiquidGlass,
            enabled = enabled && canGoToPreviousChapter,
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onPreviousChapterClick,
            detachWhenDisabled = !enabled
        )
        CatalogChapterButton(
            icon = Icons.Default.KeyboardArrowRight,
            contentDescription = stringResource(R.string.reader_next_chapter),
            contentColor = if (isLiquidGlass || displayProgress <= 95f) contentColor else Color.White,
            fallbackColor = contentColor.copy(alpha = 0.14f),
            glassContentScrimColor = glassContentScrimColor,
            glassHighlightColor = Color.White,
            forceSolid = !isLiquidGlass,
            enabled = enabled && canGoToNextChapter,
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = onNextChapterClick,
            detachWhenDisabled = !enabled
        )
    }
}

@Composable
private fun CatalogChapterButton(
    icon: ImageVector,
    contentDescription: String,
    contentColor: Color,
    fallbackColor: Color,
    glassContentScrimColor: Color,
    glassHighlightColor: Color,
    forceSolid: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    // 菜单隐藏时不挂 clickable（disabled 的 clickable 仍参与命中测试，会挡住正文触摸）
    detachWhenDisabled: Boolean = false
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            shape = CircleShape,
            fallbackColor = fallbackColor,
            contentScrimColor = glassContentScrimColor,
            highlightColor = glassHighlightColor,
            forceFallback = forceSolid,
            onClick = if (enabled || !detachWhenDisabled) onClick else null,
            enabled = enabled,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor.copy(alpha = if (enabled) 1f else 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ActionCapsule(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    contentColor: Color,
    glassContentScrimColor: Color,
    forceSolid: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        fallbackColor = bgColor,
        contentScrimColor = glassContentScrimColor,
        forceFallback = forceSolid,
        modifier = modifier
            .height(44.dp),
        // 菜单隐藏时父级仅用 graphicsLayer alpha=0 淡出，节点仍在命中测试中；
        // 必须卸载 clickable 才能让触摸穿透回正文
        onClick = if (enabled) onClick else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = contentColor)
        }
    }
}

@Composable
private fun ContinuousScrollReader(
    chapterCount: Int,
    currentChapter: Int,
    initialChapterFraction: Float,
    fontSize: Float,
    lineHeight: Float,
    letterSpacingDp: Float,
    textAlignment: ReaderTextAlignment,
    typeface: android.graphics.Typeface,
    textColor: Int,
    backgroundColor: Int,
    backgroundImagePath: String?,
    marginLeft: Float,
    marginRight: Float,
    marginTop: Float,
    marginBottom: Float,
    paragraphSpacing: Float,
    firstLineIndent: Float,
    bionicReadingEnabled: Boolean,
    contentRevision: Long,
    viewModel: ReaderViewModel,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    searchHighlight: ContinuousSearchHighlight?,
    scrollRequests: MutableSharedFlow<Int>,
    onSearchHighlightFinished: () -> Unit,
    onMenuToggle: () -> Unit,
    onLinkClick: (chapterIndex: Int, href: String, anchorWindowX: Float, anchorWindowY: Float) -> Unit,
    onImageLongPress: (chapterIndex: Int, image: ReaderImageHit) -> Unit,
    selectionController: ContinuousSelectionController,
    onSelectionChanging: () -> Unit,
    onSelection: (chapterIndex: Int, selection: ContinuousTextSelection) -> Unit,
    onChapterVisible: (chapterIndex: Int, chapterFraction: Float) -> Unit,
    onRestoreComplete: () -> Unit,
    chineseMode: String = "original",
    ttsCurrentSentence: TtsSentencePosition? = null,
    comicModeEnabled: Boolean = false,
    boldTextEnabled: Boolean = false
) {
    if (chapterCount <= 0) return

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState(currentChapter.coerceIn(0, chapterCount - 1))
    val searchHighlightAlpha = remember { Animatable(0f) }
    val loadedChapters = remember(chapterCount, contentRevision) { mutableStateMapOf<Int, Boolean>() }
    // 原始章节文本缓存：相邻章节提前拉取，衔接处不再出现“只有标题/空白、松手后突然加载”
    val rawChapterTextCache = remember(chapterCount, contentRevision, textAlignment) {
        mutableStateMapOf<Int, CharSequence>()
    }
    // 跟踪各章节的实际测量高度，用于连续进度加权计算
    val chapterHeights = remember(chapterCount, contentRevision) { mutableStateMapOf<Int, Int>() }
    var lastTtsFollowScrollAt by remember { mutableLongStateOf(0L) }
    val restoreTarget = remember(chapterCount, contentRevision) {
        currentChapter.coerceIn(0, chapterCount - 1)
    }
    val restoreFraction = remember(chapterCount, contentRevision) {
        initialChapterFraction.coerceIn(0f, 0.9999f)
    }
    var isRestoringPosition by remember { mutableStateOf(true) }

    suspend fun awaitStableChapterMeasurement(
        target: Int
    ): androidx.compose.foundation.lazy.LazyListItemInfo? {
        var lastSize = -1
        var stableFrames = 0
        repeat(300) {
            withFrameNanos { }
            if (loadedChapters[target] != true) {
                lastSize = -1
                stableFrames = 0
                return@repeat
            }

            val item = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == target && it.size > 0 }
            if (item == null) {
                // Chapters before the target can expand after their placeholders load and push the
                // target out of the viewport. Re-anchor until its real, stable height is measurable.
                listState.scrollToItem(target)
                lastSize = -1
                stableFrames = 0
                return@repeat
            }
            if (item.size == lastSize) {
                stableFrames++
            } else {
                lastSize = item.size
                stableFrames = 1
            }
            if (stableFrames >= 3) return item
        }
        return null
    }

    LaunchedEffect(restoreTarget, restoreFraction, chapterCount, contentRevision) {
        isRestoringPosition = true
        listState.scrollToItem(restoreTarget)
        val restoredItem = awaitStableChapterMeasurement(restoreTarget)
        if (restoredItem != null) {
            // Re-anchor after the placeholder-to-content height change, then apply the saved ratio.
            listState.scrollToItem(restoreTarget)
            if (restoreFraction > 0f) {
                listState.scrollBy(restoredItem.size * restoreFraction)
            }
        }
        // The bounded measurement wait prevents a corrupt chapter from holding the loading page forever.
        onChapterVisible(restoreTarget, restoreFraction)
        onRestoreComplete()
        withFrameNanos { }
        isRestoringPosition = false
    }
    LaunchedEffect(restoreTarget, chapterCount, contentRevision) {
        // 进入连续滚动时立即预加载恢复章节附近的章节
        listOf(restoreTarget - 1, restoreTarget, restoreTarget + 1, restoreTarget + 2)
            .filter { it in 0 until chapterCount && it !in rawChapterTextCache }
            .forEach { neighbor ->
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    viewModel.getChapterText(neighbor)?.let { rawChapterTextCache[neighbor] = it }
                }
            }
    }
    LaunchedEffect(scrollRequests) {
        scrollRequests.collect { target ->
            val safeTarget = target.coerceIn(0, chapterCount - 1)
            isRestoringPosition = true
            listState.scrollToItem(safeTarget)
            awaitStableChapterMeasurement(safeTarget)
            listState.scrollToItem(safeTarget)
            onChapterVisible(safeTarget, 0f)
            withFrameNanos { }
            isRestoringPosition = false
        }
    }
    LaunchedEffect(ttsCurrentSentence) {
        val sentence = ttsCurrentSentence ?: return@LaunchedEffect
        if (sentence.chapterIndex !in 0 until chapterCount) return@LaunchedEffect
        if (rawChapterTextCache[sentence.chapterIndex] == null) {
            withContext(Dispatchers.IO) {
                viewModel.getChapterText(sentence.chapterIndex)
            }?.let { rawChapterTextCache[sentence.chapterIndex] = it }
        }
        val textLength = rawChapterTextCache[sentence.chapterIndex]?.length ?: return@LaunchedEffect
        val ratio = (sentence.startOffset.toFloat() / textLength.coerceAtLeast(1)).coerceIn(0f, 1f)
        isRestoringPosition = true
        try {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val knownHeight = chapterHeights[sentence.chapterIndex]
            val estimatedHeight = knownHeight?.toFloat()
                ?: (chapterHeights.values.takeIf { it.isNotEmpty() }?.average() ?: viewportHeight.toDouble()).toFloat()
            val estimatedOffset = ((estimatedHeight * ratio) - viewportHeight / 2).toInt().coerceAtLeast(0)
            // 一次动画到位（不再先停章顶再跳），item 高度未知时先用估算，测量后再校正
            listState.animateScrollToItem(sentence.chapterIndex, estimatedOffset)
            if (knownHeight == null) {
                val realHeight = snapshotFlow { chapterHeights[sentence.chapterIndex] }
                    .first { it != null && it > 0 } ?: return@LaunchedEffect
                val realOffset = ((realHeight * ratio) - viewportHeight / 2).toInt().coerceAtLeast(0)
                val delta = (realOffset - listState.firstVisibleItemScrollOffset).toFloat()
                if (kotlin.math.abs(delta) > 1f) listState.scrollBy(delta)
            }
            lastTtsFollowScrollAt = System.currentTimeMillis()
        } finally {
            isRestoringPosition = false
        }
    }
    LaunchedEffect(listState, chapterCount) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }?.let { item ->
                Triple(item.index, item.offset, item.size)
            }
        }.collect { item ->
            item ?: return@collect
            val (index, offset, size) = item
            if (
                !isRestoringPosition &&
                System.currentTimeMillis() - lastTtsFollowScrollAt > 500 &&
                index in 0 until chapterCount &&
                loadedChapters[index] == true &&
                size > 0
            ) {
                val fraction = (-offset).toFloat().div(size).coerceIn(0f, 0.9999f)
                onChapterVisible(index, fraction)
            }
            // 预加载当前可见章节之后的两章，保证章节衔接处内容已就绪
            listOf(index + 1, index + 2).forEach { neighbor ->
                if (neighbor in 0 until chapterCount && neighbor !in rawChapterTextCache) {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        viewModel.getChapterText(neighbor)?.let { rawChapterTextCache[neighbor] = it }
                    }
                }
            }
        }
    }
    LaunchedEffect(searchHighlight) {
        searchHighlightAlpha.snapTo(0f)
        if (searchHighlight != null) {
            repeat(2) {
                searchHighlightAlpha.animateTo(1f, tween(500))
                searchHighlightAlpha.animateTo(0f, tween(500))
            }
            onSearchHighlightFinished()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(backgroundColor))) {
        if (!backgroundImagePath.isNullOrBlank()) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        load(java.io.File(backgroundImagePath))
                    }
                },
                update = { imageView -> imageView.load(java.io.File(backgroundImagePath)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onMenuToggle() }) },
            contentPadding = PaddingValues(
                start = marginLeft.dp,
                end = marginRight.dp,
                top = marginTop.dp,
                bottom = marginBottom.dp
            )
        ) {
        items(chapterCount, key = { it }) { chapterIndex ->
            val itemTransitionProgress = remember(chapterIndex) { Animatable(0f) }
            val isLoaded = loadedChapters[chapterIndex] == true
            LaunchedEffect(isLoaded, chapterIndex) {
                if (isLoaded) {
                    itemTransitionProgress.snapTo(0f)
                    itemTransitionProgress.animateTo(
                        1f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                }
            }
            val chapterText by produceState<CharSequence?>(
                initialValue = rawChapterTextCache[chapterIndex]?.let {
                    com.huangder.lumibooks.util.ChineseConverter.convertPreservingSpans(it, chineseMode)
                },
                chapterIndex,
                chineseMode,
                contentRevision,
                textAlignment,
                fontSize,
                paragraphSpacing,
                firstLineIndent
            ) {
                val cached = rawChapterTextCache[chapterIndex]
                val rawText = if (cached != null) {
                    cached
                } else {
                    withContext(Dispatchers.IO) { viewModel.getChapterText(chapterIndex) }.also { loaded ->
                        if (loaded != null) rawChapterTextCache[chapterIndex] = loaded
                    }
                }
                value = rawText?.let {
                    // LeadingMarginSpan (first-line indent), image spans, and paragraph spacing must
                    // survive simplified/traditional conversion in the continuous reader.
                    com.huangder.lumibooks.util.ChineseConverter.convertPreservingSpans(it, chineseMode)
                }
                loadedChapters[chapterIndex] = true
            }
            val selectableText = remember(
                chapterText,
                notes,
                searchHighlight,
                searchHighlightAlpha.value,
                bionicReadingEnabled,
                ttsCurrentSentence,
                backgroundColor
            ) {
                continuousSpannableText(
                    text = chapterText,
                    bionicReadingEnabled = bionicReadingEnabled,
                    notes = notes.filter { it.chapterIndex == chapterIndex },
                    searchHighlight = searchHighlight?.takeIf { it.chapterIndex == chapterIndex },
                    searchHighlightAlpha = searchHighlightAlpha.value,
                    ttsCurrentSentence = ttsCurrentSentence?.takeIf { it.chapterIndex == chapterIndex },
                    backgroundColor = backgroundColor
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // 使用入场动画：平滑淡入 + 上移效果
                        val progress = itemTransitionProgress.value
                        alpha = progress.coerceIn(0.01f, 1f)
                        translationY = ((1f - progress) * 20f).dp.toPx()
                    }
                    // 绔犺妭闂撮殧锛氶槻姝㈠墠涓€绔犳湯灏句笌涓嬩竴绔犳爣棰樿创澶繎
                    .padding(bottom = 28.dp)
            ) {
                if (comicModeEnabled) {
                    // 漫画模式：提取章节内图片，按屏宽等比缩放、无缝上下拼接
                    ComicChapterImages(
                        chapterIndex = chapterIndex,
                        chapterText = chapterText,
                        backgroundColor = backgroundColor
                    )
                } else {
                AndroidView(
                    factory = { context ->
                        ContinuousSelectableTextView(context).apply {
                            breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
                            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                        }
                    },
                    update = { textView ->
                        textView.onReaderTap = onMenuToggle
                        textView.onLinkTap = { href, tapX, tapY ->
                            val location = IntArray(2)
                            textView.getLocationInWindow(location)
                            onLinkClick(chapterIndex, href, location[0] + tapX, location[1] + tapY)
                        }
                        textView.onImageLongPress = { image -> onImageLongPress(chapterIndex, image) }
                        textView.onSelectionChanging = onSelectionChanging
                        textView.onReaderSelection = { selection ->
                            selectionController.activeView = textView
                            onSelection(chapterIndex, selection)
                        }
                        textView.setTextColor(textColor)
                        textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)
                        if (textView.paint.isFakeBoldText != boldTextEnabled) {
                            textView.paint.isFakeBoldText = boldTextEnabled
                            textView.invalidate()
                        }
                        textView.setLineSpacing(0f, lineHeight)
                        textView.typeface = typeface
                        val fontSizePx = android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_SP,
                            fontSize,
                            textView.resources.displayMetrics
                        )
                        textView.letterSpacing = if (fontSizePx > 0f) {
                            (letterSpacingDp * textView.resources.displayMetrics.density / fontSizePx)
                                .coerceIn(-0.5f, 0.5f)
                        } else {
                            0f
                        }
                        textView.justificationMode = if (textAlignment == ReaderTextAlignment.JUSTIFY) {
                            android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                        } else {
                            android.text.Layout.JUSTIFICATION_MODE_NONE
                        }
                        textView.setReaderText(selectableText)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                }
            }
        }
    }
    }
}

/**
 * 漫画模式：渲染章节中的所有图片，按屏宽等比缩放，无缝上下拼接。
 */
@Composable
private fun ComicChapterImages(
    chapterIndex: Int,
    chapterText: CharSequence?,
    backgroundColor: Int
) {
    val imageSources = remember(chapterIndex, chapterText) {
        if (chapterText is Spanned) {
            chapterText.getSpans(0, chapterText.length, android.text.style.ImageSpan::class.java)
                .mapNotNull { it.source }
                .filter { !it.isNullOrBlank() }
        } else {
            emptyList()
        }
    }
    val context = LocalContext.current
    // 共享单个 ImageLoader，避免每张图重复构建请求队列/内存缓存
    val imageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }
    val hintColor = remember(backgroundColor) {
        val bg = Color(backgroundColor)
        val luminance = bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f
        if (luminance > 0.5f) Color(0xFF666666) else Color(0xFF999999)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        if (imageSources.isEmpty()) {
            Text(
                text = stringResource(R.string.comic_mode_no_images),
                color = hintColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            imageSources.forEach { source ->
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(source)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

private fun continuousSpannableText(
    text: CharSequence?,
    bionicReadingEnabled: Boolean,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    searchHighlight: ContinuousSearchHighlight?,
    searchHighlightAlpha: Float,
    ttsCurrentSentence: TtsSentencePosition? = null,
    backgroundColor: Int = 0xFFFBFBFC.toInt()
): SpannableStringBuilder {
    val content = SpannableStringBuilder(
        BionicReadingFormatter.format(text ?: "", bionicReadingEnabled)
    )
    notes.forEach { note ->
        val start = note.startPosition.coerceIn(0, content.length)
        val end = note.endPosition.coerceIn(0, content.length)
        if (start < end) {
            if (note.type == "underline") {
                val color = runCatching { android.graphics.Color.parseColor(note.color) }
                    .getOrDefault(0xFF333333.toInt())
                content.setSpan(
                    WaveUnderlineSpan(color),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                val color = runCatching { android.graphics.Color.parseColor(note.color) }
                    .getOrDefault(0x40FFEB3B)
                content.setSpan(
                    ReaderHighlightSpan(color),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
    // TTS 当前句淡高亮：低对比度标记，浅色主题比背景稍深，深色主题比背景稍浅
    ttsCurrentSentence?.let { sentence ->
        val start = sentence.startOffset.coerceIn(0, content.length)
        val end = sentence.endOffset.coerceIn(0, content.length)
        if (start < end) {
            val ttsHighlightColor = TtsSentenceHighlightSpan.computeHighlightColor(backgroundColor, 0.06f)
            content.setSpan(
                TtsSentenceHighlightSpan(ttsHighlightColor),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
    searchHighlight?.let { highlight ->
        val start = highlight.start.coerceIn(0, content.length)
        val end = highlight.end.coerceIn(0, content.length)
        if (start < end) {
            val alpha = (searchHighlightAlpha * 0.7f * 255f).toInt().coerceIn(0, 255)
            content.setSpan(
                ReaderSearchHighlightSpan(alpha),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
    return content
}

@Composable
private fun TocSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    tocEntries: List<com.huangder.lumibooks.util.parser.TocEntry>,
    currentChapter: Int,
    bookmarks: List<com.huangder.lumibooks.domain.model.Bookmark> = emptyList(),
    chapterTitles: List<String> = emptyList(),
    onChapterSelected: (com.huangder.lumibooks.util.parser.TocEntry) -> Unit,
    onBookmarkClick: (com.huangder.lumibooks.domain.model.Bookmark) -> Unit = {},
    onDeleteBookmark: (com.huangder.lumibooks.domain.model.Bookmark) -> Unit = {},
    onEditBookmark: (com.huangder.lumibooks.domain.model.Bookmark, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    // Keep fold choices while this book's reader remains open, including across sheet reopens.
    var collapsedGroups by remember(tocEntries) { mutableStateOf<Set<Int>>(emptySet()) }

    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val foldGroups = remember(tocEntries) { findTocFoldGroups(tocEntries) }
    val visibleEntries = remember(tocEntries, foldGroups, collapsedGroups) {
        visibleTocEntries(tocEntries, foldGroups, collapsedGroups)
    }
    val currentSourceIndex = remember(tocEntries, currentChapter) {
        tocEntries.indexOfFirst { !it.isGroup && it.chapterIndex == currentChapter }
    }

    val currentEntryIndex = remember(
        tocEntries,
        visibleEntries,
        foldGroups,
        collapsedGroups,
        currentChapter
    ) {
        currentTocVisibleIndex(
            entries = tocEntries,
            visibleEntries = visibleEntries,
            foldGroups = foldGroups,
            collapsedGroups = collapsedGroups,
            currentChapter = currentChapter
        )
    }
    val tocListState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentEntryIndex.coerceAtLeast(0)
    )
    val bookmarkListState = rememberLazyListState()
    var activeSection by remember { mutableStateOf("toc") }
    var editingBookmark by remember {
        mutableStateOf<com.huangder.lumibooks.domain.model.Bookmark?>(null)
    }
    val sortedBookmarks = remember(bookmarks) {
        bookmarks.sortedWith(
            compareBy<com.huangder.lumibooks.domain.model.Bookmark> { it.chapterIndex }
                .thenBy { it.position }
        )
    }

    // Center the reading position only when this sheet instance opens. Folding changes the
    // visible index, but the tapped group header must remain the visual anchor.
    LaunchedEffect(tocListState) {
        if (currentEntryIndex < 0) return@LaunchedEffect
        snapshotFlow { tocListState.layoutInfo.viewportSize.height }
            .first { it > 0 }

        val currentItem = tocListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentEntryIndex }
            ?: return@LaunchedEffect
        val layoutInfo = tocListState.layoutInfo
        val centeredItemOffset = layoutInfo.viewportStartOffset +
            (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset - currentItem.size) / 2
        tocListState.scrollBy((currentItem.offset - centeredItemOffset).toFloat())
    }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateBottomSheetIn()
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    var pendingJumpEntry by remember { mutableStateOf<com.huangder.lumibooks.util.parser.TocEntry?>(null) }
    var pendingJumpBookmark by remember {
        mutableStateOf<com.huangder.lumibooks.domain.model.Bookmark?>(null)
    }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateBottomSheetOut()
            pendingJumpEntry?.let(onChapterSelected)
            pendingJumpEntry = null
            pendingJumpBookmark?.let(onBookmarkClick)
            pendingJumpBookmark = null
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出（70% 屏幕高度）
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.reader_toc),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = if (activeSection == "toc") AppColors.TextPrimary else LightTextSecondary,
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            activeSection = "toc"
                        }
                        .padding(vertical = 4.dp)
                )
                Text(
                    text = "  ",
                    fontSize = 20.sp,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = LightTextSecondary
                )
                Text(
                    text = stringResource(R.string.tab_bookmark),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = if (activeSection == "bookmark") AppColors.TextPrimary else LightTextSecondary,
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            activeSection = "bookmark"
                        }
                        .padding(vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                LiquidGlassIconButton(
                    imageVector = Icons.Default.VerticalAlignTop,
                    contentDescription = stringResource(R.string.reader_toc_scroll_to_top),
                    onClick = {
                        val target = if (activeSection == "toc") visibleEntries else sortedBookmarks
                        if (target.isNotEmpty()) {
                            val state = if (activeSection == "toc") tocListState else bookmarkListState
                            scope.launch { state.scrollToItem(0) }
                        }
                    },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = LightBgGray,
                    enabled = if (activeSection == "toc") visibleEntries.isNotEmpty() else sortedBookmarks.isNotEmpty()
                )
                Spacer(Modifier.width(8.dp))
                LiquidGlassIconButton(
                    imageVector = Icons.Default.VerticalAlignBottom,
                    contentDescription = stringResource(R.string.reader_toc_scroll_to_bottom),
                    onClick = {
                        if (activeSection == "toc") {
                            if (visibleEntries.isNotEmpty()) {
                                scope.launch { tocListState.scrollToItem(visibleEntries.lastIndex) }
                            }
                        } else {
                            if (sortedBookmarks.isNotEmpty()) {
                                scope.launch { bookmarkListState.scrollToItem(sortedBookmarks.lastIndex) }
                            }
                        }
                    },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = LightBgGray,
                    enabled = if (activeSection == "toc") visibleEntries.isNotEmpty() else sortedBookmarks.isNotEmpty()
                )
                Spacer(Modifier.width(8.dp))
                // 关闭按钮
                LiquidGlassIconButton(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.reader_close),
                    onClick = { isClosing = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = LightBgGray
                )
            }

            Spacer(Modifier.height(16.dp))

            if (activeSection == "toc") {
                Box(Modifier.weight(1f)) {
                    // 目录列表（支持层级：可折叠分组标题 + 缩进章节）
                    LazyColumn(
                        state = tocListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            count = visibleEntries.size,
                            key = { index -> visibleEntries[index].sourceIndex }
                        ) { index ->
                            val (originalIndex, entry) = visibleEntries[index]

                            if (entry.isGroup || originalIndex in foldGroups) {
                                // 分组标题（如"第X卷"）：箭头折叠/展开该卷；
                                // 卷本身指向真实章节时（TXT 扁平目录），点标题仍可跳转
                                val isFoldable = originalIndex in foldGroups
                                val collapsed = originalIndex in collapsedGroups
                                val isCurrent =
                                    (entry.chapterIndex >= 0 && entry.chapterIndex == currentChapter) ||
                                        (collapsed && currentSourceIndex > originalIndex &&
                                            currentSourceIndex < (foldGroups[originalIndex] ?: originalIndex + 1))
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (collapsed) -90f else 0f,
                                    animationSpec = tween(160),
                                    label = "tocGroupArrow"
                                )
                                val toggleCollapse = {
                                    collapsedGroups = if (collapsed) collapsedGroups - originalIndex
                                    else collapsedGroups + originalIndex
                                }
                                val groupIndent = ((entry.level - 1).coerceAtLeast(0) * 20).dp
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            fadeInSpec = tween(180),
                                            placementSpec = tween(220, easing = FastOutSlowInEasing),
                                            fadeOutSpec = tween(140)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (entry.chapterIndex >= 0 || isFoldable) {
                                                Modifier.clickable(
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) {
                                                    if (entry.chapterIndex >= 0) {
                                                        pendingJumpEntry = entry
                                                        isClosing = true
                                                    } else {
                                                        toggleCollapse()
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(
                                            start = 4.dp + groupIndent,
                                            top = if (index > 0) 16.dp else 4.dp,
                                            bottom = 4.dp,
                                            end = 4.dp
                                        )
                                ) {
                                    if (isFoldable) {
                                        IconButton(
                                            onClick = toggleCollapse,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (collapsed) stringResource(R.string.reader_toc_group_expand)
                                                else stringResource(R.string.reader_toc_group_collapse),
                                                tint = Color.Gray,
                                                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.size(40.dp))
                                    }
                                    Text(
                                        text = entry.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) AccentColor else Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                // 实际章节：可点击，根据 level 缩进
                                val isCurrent = entry.chapterIndex == currentChapter
                                val indent = ((entry.level - 1) * 20).dp

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            fadeInSpec = tween(180),
                                            placementSpec = tween(220, easing = FastOutSlowInEasing),
                                            fadeOutSpec = tween(140)
                                        )
                                        .padding(start = indent, top = 2.dp, bottom = 2.dp, end = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isCurrent) AccentColor.copy(alpha = 0.1f) else LightBgGray)
                                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                            if (entry.chapterIndex >= 0) {
                                                pendingJumpEntry = entry
                                                isClosing = true
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = entry.title.ifBlank { stringResource(R.string.reader_chapter_fallback, entry.chapterIndex + 1) },
                                        fontSize = 15.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) AccentColor else AppColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    DraggableScrollbar(
                        listState = tocListState,
                        itemCount = visibleEntries.size,
                        hintText = { fraction ->
                            if (visibleEntries.isEmpty()) return@DraggableScrollbar null
                            val idx = (fraction * (visibleEntries.size - 1))
                                .toInt()
                                .coerceIn(0, visibleEntries.lastIndex)
                            val entry = visibleEntries[idx].entry
                            val title = entry.title.ifBlank {
                                stringResource(R.string.reader_chapter_fallback, entry.chapterIndex + 1)
                            }
                            "$title · ${(fraction * 100).toInt()}%"
                        },
                        modifier = Modifier
                            .fillMaxSize()
                    )
                }
            } else {
                Box(Modifier.weight(1f)) {
                    if (sortedBookmarks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.no_bookmarks_yet),
                                fontSize = 14.sp,
                                color = LightTextSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            state = bookmarkListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(sortedBookmarks.size, key = { sortedBookmarks[it].id }) { idx ->
                                val bm = sortedBookmarks[idx]
                                TocBookmarkItem(
                                    bookmark = bm,
                                    chapterTitle = chapterTitles.getOrNull(bm.chapterIndex).orEmpty(),
                                    onClick = {
                                        pendingJumpBookmark = bm
                                        isClosing = true
                                    },
                                    onEdit = { editingBookmark = bm },
                                    onDelete = { onDeleteBookmark(bm) }
                                )
                                if (idx < sortedBookmarks.size - 1) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }

                        DraggableScrollbar(
                            listState = bookmarkListState,
                            itemCount = sortedBookmarks.size,
                            hintText = { fraction ->
                                if (sortedBookmarks.isEmpty()) return@DraggableScrollbar null
                                val idx = (fraction * (sortedBookmarks.size - 1))
                                    .toInt()
                                    .coerceIn(0, sortedBookmarks.lastIndex)
                                val bm = sortedBookmarks[idx]
                                val chapterLabel = chapterTitles
                                    .getOrNull(bm.chapterIndex)
                                    ?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.chapter_number, bm.chapterIndex + 1)
                                "${bm.title} · $chapterLabel · ${(fraction * 100).toInt()}%"
                            },
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // 编辑书签名称
    editingBookmark?.let { target ->
        LiquidGlassDialog(
            onDismissRequest = { editingBookmark = null },
            modifier = Modifier.imePadding(),
            backgroundScrimColor = Color.Transparent,
            backgroundBlurRadius = 18.dp,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EditInputDialog(
                title = stringResource(R.string.bookmark_edit_title),
                fields = listOf(
                    Triple(
                        stringResource(R.string.bookmark_name_label),
                        stringResource(R.string.bookmark_name_placeholder),
                        target.title
                    )
                ),
                onBack = { editingBookmark = null },
                onConfirm = { values ->
                    onEditBookmark(target, values.getOrElse(0) { target.title })
                    editingBookmark = null
                }
            )
        }
    }
}

@Composable
private fun TocBookmarkItem(
    bookmark: com.huangder.lumibooks.domain.model.Bookmark,
    chapterTitle: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val chapterNumber = stringResource(R.string.chapter_number, bookmark.chapterIndex + 1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LightBgGray)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onClick()
            }
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Bookmark,
            contentDescription = stringResource(R.string.reader_bookmark),
            tint = Color(0xFFFFB300),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.title,
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (chapterTitle.isBlank()) {
                    chapterNumber
                } else {
                    "$chapterNumber · $chapterTitle"
                },
                fontSize = 12.sp,
                color = LightTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LiquidGlassIconButton(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(R.string.edit),
            onClick = onEdit,
            size = 36.dp,
            iconSize = 18.dp,
            contentColor = AppColors.TextSecondary,
            normalContainerColor = Color.Transparent
        )
        LiquidGlassIconButton(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.delete),
            onClick = onDelete,
            size = 36.dp,
            iconSize = 18.dp,
            contentColor = AppColors.TextSecondary,
            normalContainerColor = Color.Transparent
        )
    }
}

/**
 * 简约式右侧滚动条：圆柱形滑块 + 加粗触控区。
 * 拖动时显示当前章节名/进度提示，松手后列表滚动到对应位置。
 */
@Composable
private fun DraggableScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    hintText: @Composable (Float) -> String? = { null }
) {
    if (itemCount <= 0) return

    val density = LocalDensity.current
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val scope = rememberCoroutineScope()
    val minThumbPx = with(density) { 36.dp.toPx() }

    val layoutInfo = listState.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    val avgItemHeightPx = if (visible.isEmpty()) {
        with(density) { 52.dp.toPx() }
    } else {
        visible.sumOf { it.size.toLong() }.toFloat() / visible.size
    }
    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat()
    val contentHeightPx = avgItemHeightPx * itemCount
    val visibleRatio = if (viewportHeightPx > 0f && contentHeightPx > 0f) {
        (viewportHeightPx / contentHeightPx).coerceAtMost(1f)
    } else {
        1f
    }
    val thumbHeightPx = if (trackHeightPx > 0f) {
        (trackHeightPx * visibleRatio).coerceIn(minThumbPx, trackHeightPx)
    } else {
        minThumbPx
    }

    val total = layoutInfo.totalItemsCount
    val scrollFraction = if (total <= 1) {
        0f
    } else {
        val first = visible.firstOrNull()?.index ?: 0
        val offset = listState.firstVisibleItemScrollOffset
        ((first + offset / avgItemHeightPx) / (total - 1)).coerceIn(0f, 1f)
    }
    val displayFraction = if (dragFraction >= 0f) dragFraction else scrollFraction
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val currentContentHeightPx by rememberUpdatedState(contentHeightPx)
    val thumbColor = AppColors.TextSecondary.copy(alpha = 0.38f)

    Box(modifier = modifier) {
        // 右侧触控条：无轨道，只有圆柱滑块
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(28.dp)
                .fillMaxHeight()
                .offset(x = 20.dp)
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .pointerInput(itemCount) {
                    if (currentTrackHeightPx <= 0f || currentThumbHeightPx >= currentTrackHeightPx) {
                        return@pointerInput
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastY = down.position.y
                        fun fractionFor(y: Float): Float {
                            val track = currentTrackHeightPx
                            val thumb = currentThumbHeightPx
                            val range = (track - thumb).coerceAtLeast(1f)
                            return ((y - thumb / 2f) / range).coerceIn(0f, 1f)
                        }
                        dragFraction = fractionFor(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            if (change.positionChange() != Offset.Zero) {
                                change.consume()
                                val y = change.position.y
                                dragFraction = fractionFor(y)
                                val deltaY = y - lastY
                                lastY = y
                                if (deltaY != 0f && currentTrackHeightPx > 0f) {
                                    val scale = currentContentHeightPx / currentTrackHeightPx
                                    scope.launch { listState.scrollBy(deltaY * scale) }
                                }
                            }
                        }
                        val fraction = dragFraction.coerceIn(0f, 1f)
                        val targetIndex = (fraction * (itemCount - 1)).toInt()
                        scope.launch { listState.scrollToItem(targetIndex.coerceIn(0, itemCount - 1)) }
                        dragFraction = -1f
                    }
                }
        ) {
            if (thumbHeightPx < trackHeightPx) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(5.dp)
                        .fillMaxHeight()
                ) {
                    val radius = size.width / 2f
                    val top = displayFraction.coerceIn(0f, 1f) * (size.height - thumbHeightPx)
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, thumbHeightPx),
                        cornerRadius = CornerRadius(radius)
                    )
                }
            }
        }

        // 拖动提示：列表区顶部居中的浅色胶囊，显示章节名/进度
        val hint = if (dragFraction >= 0f) hintText(dragFraction.coerceIn(0f, 1f)) else null
        if (hint != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.CardBg.copy(alpha = 0.96f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 全文搜索弹窗——底部弹出，可伸缩高度。
 */
@Composable
private fun SearchSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    query: String,
    results: List<ReaderViewModel.SearchResult>,
    isSearching: Boolean,
    hasSearched: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (ReaderViewModel.SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    val hasResults = results.isNotEmpty()

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateBottomSheetIn()
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出容器（自适应高度）
        LiquidGlassSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(24.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column {
                // 标题栏
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.reader_search),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = resolveAppFontFamily(KaiTi),
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    // 关闭按钮
                    LiquidGlassIconButton(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reader_close),
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = LightBgGray
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 搜索输入框 + 按钮
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(LightBgGray)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.material3.TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text(stringResource(R.string.search_placeholder), fontSize = 14.sp, color = LightTextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (query.isNotBlank()) AccentColor else LightBgGray)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (query.isNotBlank()) onSearch() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.OnAccent)
                        } else {
                            Text(stringResource(R.string.reader_search), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (query.isNotBlank()) AppColors.OnAccent else LightTextSecondary)
                        }
                    }
                }

                // 结果区域（有结果时显示，自适应高度）
                if (hasResults) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.search_results_found, results.size),
                        fontSize = 12.sp,
                        color = LightTextSecondary
                    )
                    Spacer(Modifier.height(8.dp))

                    // 结果列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        items(results.size) { idx ->
                            val r = results[idx]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LightBgGray)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                        onResultClick(r)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = r.chapterTitle,
                                        fontSize = 12.sp,
                                        color = AccentColor,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = r.context,
                                        fontSize = 14.sp,
                                        color = AppColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else if (!isSearching && hasSearched) {
                    // 已搜索但无结果
                    Spacer(Modifier.height(24.dp))
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.search_no_results), fontSize = 14.sp, color = LightTextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ── 文本选择数据 ──

/** 🔥 原生选择 ActionMode 触发的待处理操作 */
private data class PendingSelection(
    val selectedText: String,
    val chapterIndex: Int,
    val startPosition: Int,
    val endPosition: Int,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null
)

private data class SelectionState(
    val chapterIndex: Int,
    val pageInChapter: Int,
    val charStart: Int,
    val charEnd: Int,
    val selectedText: String,
    val touchX: Float,
    val touchY: Float,
    val overlappingHighlights: List<com.huangder.lumibooks.domain.model.Note> = emptyList(),
    val overlappingUnderlines: List<com.huangder.lumibooks.domain.model.Note> = emptyList(),
    // 选区边界框（屏幕像素坐标），用于菜单定位
    val selTopY: Float = 0f,
    val selBottomY: Float = 0f,
    val selStartX: Float = 0f,
    val selEndX: Float = 0f,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null
) {
    val hasHighlight: Boolean get() = overlappingHighlights.isNotEmpty()
    val hasUnderline: Boolean get() = overlappingUnderlines.isNotEmpty()
    val hasNote: Boolean get() = (overlappingHighlights + overlappingUnderlines).any { it.note.isNotEmpty() }
    val existingNote: com.huangder.lumibooks.domain.model.Note?
        get() = (overlappingHighlights + overlappingUnderlines).let { notes ->
            notes.firstOrNull { it.note.isNotEmpty() } ?: notes.firstOrNull()
        }
}

/** 查找与选区重叠的标注，按 type 分离高亮和划线。 */
private fun findOverlappingNotes(
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    chapterIndex: Int,
    selStart: Int,
    selEnd: Int,
    type: String
): List<com.huangder.lumibooks.domain.model.Note> = notes.filter { note ->
    note.chapterIndex == chapterIndex && note.type == type &&
        note.startPosition < selEnd && note.endPosition > selStart
}

// ── 选择菜单覆盖层 ──


private const val DICTIONARY_LOOKUP_TAG = "DictionaryLookup"

private data class DictionaryAppOption(
    val label: String,
    val packageName: String,
    val activityName: String
)

private data class DictionaryLookupRequest(
    val normalizedText: String,
    val apps: List<DictionaryAppOption>
)

private fun normalizeDictionaryText(selectedText: String): String =
    selectedText.trim().replace(Regex("\\s+"), " ")

private fun buildDictionaryLookupIntent(normalizedText: String): Intent =
    Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, normalizedText)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }

private fun prepareDictionaryLookup(context: Context, selectedText: String): DictionaryLookupRequest? {
    val normalizedText = normalizeDictionaryText(selectedText)
    if (normalizedText.isBlank()) return null

    val packageManager = context.packageManager
    val apps = try {
        packageManager
            .queryIntentActivities(buildDictionaryLookupIntent(normalizedText), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val activityName = activityInfo.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // Only show entries that can actually be launched from Lumibooks. Some OEMs return
                // disabled/hidden PROCESS_TEXT handlers; launching those caused crash-like failures.
                if (!activityInfo.enabled || !activityInfo.exported || !activityInfo.applicationInfo.enabled) {
                    return@mapNotNull null
                }
                if (packageName == context.packageName) return@mapNotNull null
                val label = try {
                    resolveInfo.loadLabel(packageManager).toString().takeIf { it.isNotBlank() }
                        ?: activityInfo.loadLabel(packageManager).toString().takeIf { it.isNotBlank() }
                        ?: packageName
                } catch (throwable: Throwable) {
                    Log.w(DICTIONARY_LOOKUP_TAG, "Failed to load PROCESS_TEXT app label", throwable)
                    packageName
                }
                DictionaryAppOption(
                    label = label,
                    packageName = packageName,
                    activityName = activityName
                )
            }
            .distinctBy { it.packageName to it.activityName }
            .sortedBy { it.label.lowercase() }
    } catch (throwable: Throwable) {
        Log.w(DICTIONARY_LOOKUP_TAG, "Failed to query PROCESS_TEXT apps", throwable)
        emptyList()
    }

    return DictionaryLookupRequest(normalizedText, apps)
}

private fun launchDictionaryLookup(
    context: Context,
    normalizedText: String,
    appOption: DictionaryAppOption
): Boolean {
    if (normalizedText.isBlank()) return false

    val lookupIntent = buildDictionaryLookupIntent(normalizedText).apply {
        setClassName(appOption.packageName, appOption.activityName)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        context.startActivity(lookupIntent)
        true
    } catch (throwable: ActivityNotFoundException) {
        Log.w(DICTIONARY_LOOKUP_TAG, "PROCESS_TEXT app not found", throwable)
        Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
        false
    } catch (throwable: SecurityException) {
        Log.w(DICTIONARY_LOOKUP_TAG, "PROCESS_TEXT app is not accessible", throwable)
        Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
        false
    } catch (throwable: Throwable) {
        Log.w(DICTIONARY_LOOKUP_TAG, "Failed to launch PROCESS_TEXT app", throwable)
        Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
        false
    }
}
private enum class SelectionMenuMode {
    Actions,
    ColorPicker,
    DictionaryApps,
    Settings
}

private const val MENU_KEY_HIGHLIGHT = "highlight"
private const val MENU_KEY_UNDERLINE = "underline"
private const val MENU_KEY_NOTE = "note"
private const val MENU_KEY_DICTIONARY = "dictionary"
private const val MENU_KEY_SEARCH = "search"
private const val MENU_KEY_COPY = "copy"
private const val MENU_KEY_REPLACE = "replace"

/** 多胶囊菜单依次弹出的级联间隔（毫秒） */
private const val SELECTION_PILL_STAGGER_MILLIS = 90L

private fun isMenuEnabled(items: Map<String, Boolean>, key: String): Boolean {
    return items.isEmpty() || items[key] != false
}

@Composable
private fun SelectionMenuOverlay(
    state: SelectionState?,
    readerTheme: String,
    glassBackdrop: Backdrop? = null,
    forceSolidSurface: Boolean = false,
    isDragging: Boolean,
    dismissOnBackgroundTap: Boolean = true,
    reappearKey: Int,
    showColorPicker: Boolean = false,
    showDictionaryAppPicker: Boolean = false,
    showSettings: Boolean = false,
    dictionaryAppOptions: List<DictionaryAppOption> = emptyList(),
    isTxtBook: Boolean = false,
    selectionMenuItems: Map<String, Boolean> = emptyMap(),
    onDismiss: () -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit = {},
    onNote: () -> Unit,
    onSearch: () -> Unit,
    onDictionary: () -> Unit,
    onDictionaryAppSelected: (DictionaryAppOption) -> Unit,
    onCopy: () -> Unit,
    onViewNote: () -> Unit,
    onReplace: () -> Unit = {},
    onMenuSettings: () -> Unit = {},
    onColorPicked: (Int) -> Unit = {},
    onChangeHighlightColor: (Int) -> Unit = {},
    onChangeUnderlineColor: (Int) -> Unit = {},
    onDeleteHighlight: () -> Unit = {},
    onDeleteUnderline: () -> Unit = {}
) {
    if (state == null) return
    // Hide the menu while selection handles are being dragged; re-enter at the updated position.
    if (isDragging) return

    val menuMode = when {
        showDictionaryAppPicker -> SelectionMenuMode.DictionaryApps
        showColorPicker -> SelectionMenuMode.ColorPicker
        showSettings -> SelectionMenuMode.Settings
        else -> SelectionMenuMode.Actions
    }

    val highlightColors = ReaderHighlightPalette

    // Match menu colors to the reader background.
    val menuBg = when (readerTheme) {
        "night", "sepia_dark", "green_dark" -> Color.Black
        else -> Color.White
    }
    val menuText = when (readerTheme) {
        "night", "sepia_dark", "green_dark" -> Color.White
        else -> Color.Black
    }
    val dividerColor = menuText.copy(alpha = 0.15f)

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    val maxMenuWidth = (configuration.screenWidthDp.dp - 24.dp).coerceAtLeast(280.dp)
    val textMeasurer = rememberTextMeasurer()
    // 普通行菜单项：隐藏选区已存在的标注类型；带笔记时"笔记"换成"查看笔记"
    val actionLabels = buildList {
        if (!state.hasHighlight && isMenuEnabled(selectionMenuItems, MENU_KEY_HIGHLIGHT)) add(stringResource(R.string.menu_highlight))
        if (!state.hasUnderline && isMenuEnabled(selectionMenuItems, MENU_KEY_UNDERLINE)) add(stringResource(R.string.menu_underline))
        if (isMenuEnabled(selectionMenuItems, MENU_KEY_NOTE)) add(stringResource(if (state.hasNote) R.string.menu_view_note else R.string.menu_note))
        if (isMenuEnabled(selectionMenuItems, MENU_KEY_DICTIONARY)) add(stringResource(R.string.menu_dictionary))
        if (isMenuEnabled(selectionMenuItems, MENU_KEY_SEARCH)) add(stringResource(R.string.menu_search))
        if (isMenuEnabled(selectionMenuItems, MENU_KEY_COPY)) add(stringResource(R.string.menu_copy))
        if (isTxtBook && isMenuEnabled(selectionMenuItems, MENU_KEY_REPLACE)) add(stringResource(R.string.menu_replace))
    }
    val actionChipHorizontalPadding = if (isLiquidGlass) 10.dp else 16.dp
    fun measuredLabelWidth(label: String): Dp = with(density) {
        textMeasurer.measure(
            text = label,
            style = TextStyle(fontSize = 13.sp),
            maxLines = 1
        ).size.width.toDp()
    }
    val measuredActionLabelsWidth = actionLabels.fold(0.dp) { width, label ->
        width + measuredLabelWidth(label) + actionChipHorizontalPadding * 2
    }
    // 普通菜单：文字 chip + 分隔线 + 行内边距；上方带标注菜单时行尾追加齿轮入口
    val normalPillWidth = (
        measuredActionLabelsWidth +
            0.5.dp * (actionLabels.size - 1).coerceAtLeast(0) +
            20.dp +
            if (state.hasHighlight || state.hasUnderline) 32.5.dp else 0.dp
        ).coerceIn(180.dp, maxMenuWidth)
    // 标注菜单：6 个色点（6×22 + 5×10 间距）+ 分隔与间距 + 移除 chip + 行内边距
    val annotationRemoveLabels = buildList {
        if (state.hasHighlight) add(stringResource(R.string.menu_remove_highlight))
        if (state.hasUnderline) add(stringResource(R.string.menu_remove_underline))
    }
    val annotationPillWidth = if (annotationRemoveLabels.isEmpty()) 0.dp else {
        val maxRemoveChipWidth = annotationRemoveLabels.maxOf { measuredLabelWidth(it) + actionChipHorizontalPadding * 2 }
        (182.dp + 12.5.dp + 20.dp + maxRemoveChipWidth).coerceAtMost(maxMenuWidth)
    }
    val desiredActionMenuWidth = maxOf(normalPillWidth, annotationPillWidth)
    val actionMenuWidth = desiredActionMenuWidth.coerceAtMost(maxMenuWidth)
    val colorPickerWidth = (if (isLiquidGlass) 260.dp else 380.dp).coerceAtMost(maxMenuWidth)
    val dictionaryMenuWidth = when (dictionaryAppOptions.size) {
        0 -> 180.dp
        1 -> 220.dp
        2 -> 320.dp
        else -> 430.dp
    }.coerceAtMost(maxMenuWidth)

    val targetMenuWidth = when (menuMode) {
        SelectionMenuMode.Actions -> actionMenuWidth
        SelectionMenuMode.ColorPicker -> colorPickerWidth
        SelectionMenuMode.DictionaryApps -> dictionaryMenuWidth
        SelectionMenuMode.Settings -> colorPickerWidth
    }

    // Actions 模式由多个独立胶囊菜单堆叠（普通菜单 + 高亮菜单 + 划线菜单），其余模式单胶囊
    val menuPillGap = 8.dp
    val menuRowCount = 1 + (if (state.hasHighlight) 1 else 0) + (if (state.hasUnderline) 1 else 0)
    val targetMenuHeight = if (menuMode == SelectionMenuMode.Actions) {
        52.dp * menuRowCount + menuPillGap * (menuRowCount - 1)
    } else {
        52.dp
    }

    // Keep the menu within screen bounds; allow horizontal scroll when actions or app names exceed width.
    val animMenuWidthDp by animateDpAsState(
        targetValue = targetMenuWidth,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 380f),
        label = "menuWidth"
    )
    val animMenuHeightDp by animateDpAsState(
        targetValue = targetMenuHeight,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 380f),
        label = "menuHeight"
    )

    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val menuWidthPx    = with(density) { animMenuWidthDp.toPx() }
    val menuHeightPx   = with(density) { targetMenuHeight.toPx() }
    val menuGapPx      = with(density) { 14.dp.toPx() }
    val screenEdgePx   = with(density) { 12.dp.toPx() }

    // Position the menu centered on the selection, above or below based on available space.
    val selCenterX = (state.selStartX + state.selEndX) / 2f
    val menuX = (selCenterX - menuWidthPx / 2f)
        .coerceIn(screenEdgePx, (screenWidthPx - menuWidthPx - screenEdgePx).coerceAtLeast(screenEdgePx))
    val selCenterY = (state.selTopY + state.selBottomY) / 2f
    val aboveY = state.selTopY - menuHeightPx - menuGapPx
    val belowY = state.selBottomY + menuGapPx
    val maxMenuY = (screenHeightPx - menuHeightPx - screenEdgePx).coerceAtLeast(screenEdgePx)
    val menuY = when {
        aboveY >= screenEdgePx -> aboveY
        belowY <= maxMenuY -> belowY
        selCenterY > screenHeightPx * 0.5f -> aboveY.coerceIn(screenEdgePx, maxMenuY)
        else -> belowY.coerceIn(screenEdgePx, maxMenuY)
    }

    Box(Modifier.fillMaxSize()) {
        if (dismissOnBackgroundTap) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }
        AnimatedContent(
            targetState = menuMode,
            transitionSpec = {
                (fadeIn(tween(durationMillis = 170, delayMillis = 55)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f)
                    )).togetherWith(
                    fadeOut(tween(durationMillis = 140)) +
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                        )
                )
            },
            modifier = Modifier
                .offset { IntOffset(menuX.toInt(), menuY.toInt()) }
                .width(animMenuWidthDp)
                .height(animMenuHeightDp),
            contentAlignment = Alignment.Center,
            label = "selectionMenuMode"
        ) { mode ->
            when (mode) {
                SelectionMenuMode.ColorPicker -> SelectionMenuPill(
                    width = colorPickerWidth,
                    reappearKey = reappearKey,
                    menuBg = menuBg,
                    glassBackdrop = glassBackdrop,
                    forceSolidSurface = forceSolidSurface
                ) {
                    // Color picker submenu: six color dots with manual spacing.
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        highlightColors.forEachIndexed { index, (_, color) ->
                            if (index > 0) Spacer(Modifier.width(14.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onColorPicked(index) }
                            )
                        }
                    }
                }

                SelectionMenuMode.DictionaryApps -> SelectionMenuPill(
                    width = dictionaryMenuWidth,
                    reappearKey = reappearKey,
                    menuBg = menuBg,
                    glassBackdrop = glassBackdrop,
                    forceSolidSurface = forceSolidSurface
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dictionaryAppOptions.forEachIndexed { index, appOption ->
                            if (index > 0) MenuDivider(dividerColor)
                            MenuChip(appOption.label, menuText) { onDictionaryAppSelected(appOption) }
                        }
                    }
                }

                SelectionMenuMode.Settings -> SelectionMenuPill(
                    width = colorPickerWidth,
                    reappearKey = reappearKey,
                    menuBg = menuBg,
                    glassBackdrop = glassBackdrop,
                    forceSolidSurface = forceSolidSurface
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MenuChip(stringResource(R.string.selection_menu_settings), menuText) {
                            onMenuSettings()
                        }
                    }
                }

                SelectionMenuMode.Actions -> {
                    // 多个独立胶囊菜单依次弹出：高亮菜单 → 划线菜单 → 普通菜单；无标注时仅普通菜单
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(menuPillGap),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var pillIndex = 0
                        if (state.hasHighlight) {
                            SelectionMenuPill(
                                width = annotationPillWidth,
                                reappearKey = reappearKey,
                                enterDelayMillis = pillIndex * SELECTION_PILL_STAGGER_MILLIS,
                                menuBg = menuBg,
                                glassBackdrop = glassBackdrop,
                                forceSolidSurface = forceSolidSurface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SelectionAnnotationRow(
                                        currentColor = state.overlappingHighlights.firstOrNull()?.color,
                                        removeLabel = stringResource(R.string.menu_remove_highlight),
                                        menuText = menuText,
                                        dividerColor = dividerColor,
                                        onColorChange = onChangeHighlightColor,
                                        onRemove = onDeleteHighlight
                                    )
                                }
                            }
                            pillIndex++
                        }
                        if (state.hasUnderline) {
                            SelectionMenuPill(
                                width = annotationPillWidth,
                                reappearKey = reappearKey,
                                enterDelayMillis = pillIndex * SELECTION_PILL_STAGGER_MILLIS,
                                menuBg = menuBg,
                                glassBackdrop = glassBackdrop,
                                forceSolidSurface = forceSolidSurface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SelectionAnnotationRow(
                                        currentColor = state.overlappingUnderlines.firstOrNull()?.color,
                                        removeLabel = stringResource(R.string.menu_remove_underline),
                                        menuText = menuText,
                                        dividerColor = dividerColor,
                                        onColorChange = onChangeUnderlineColor,
                                        onRemove = onDeleteUnderline
                                    )
                                }
                            }
                            pillIndex++
                        }
                        val actionItems = buildList {
                            if (!state.hasHighlight && isMenuEnabled(selectionMenuItems, MENU_KEY_HIGHLIGHT)) add(Pair(stringResource(R.string.menu_highlight), onHighlight))
                            if (!state.hasUnderline && isMenuEnabled(selectionMenuItems, MENU_KEY_UNDERLINE)) add(Pair(stringResource(R.string.menu_underline), onUnderline))
                            if (isMenuEnabled(selectionMenuItems, MENU_KEY_NOTE)) {
                                add(Pair(
                                    stringResource(if (state.hasNote) R.string.menu_view_note else R.string.menu_note),
                                    if (state.hasNote) onViewNote else onNote
                                ))
                            }
                            if (isMenuEnabled(selectionMenuItems, MENU_KEY_DICTIONARY)) add(Pair(stringResource(R.string.menu_dictionary), onDictionary))
                            if (isMenuEnabled(selectionMenuItems, MENU_KEY_SEARCH)) add(Pair(stringResource(R.string.menu_search), onSearch))
                            if (isMenuEnabled(selectionMenuItems, MENU_KEY_COPY)) add(Pair(stringResource(R.string.menu_copy), onCopy))
                            if (isTxtBook && isMenuEnabled(selectionMenuItems, MENU_KEY_REPLACE)) add(Pair(stringResource(R.string.menu_replace), onReplace))
                        }
                        SelectionMenuPill(
                            width = normalPillWidth,
                            reappearKey = reappearKey,
                            enterDelayMillis = pillIndex * SELECTION_PILL_STAGGER_MILLIS,
                            menuBg = menuBg,
                            glassBackdrop = glassBackdrop,
                            forceSolidSurface = forceSolidSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                actionItems.forEachIndexed { index, (label, action) ->
                                    if (index > 0) MenuDivider(dividerColor)
                                    MenuChip(label, menuText, action)
                                }
                                if (state.hasHighlight || state.hasUnderline) {
                                    Spacer(Modifier.width(6.dp))
                                    MenuDivider(dividerColor)
                                    Spacer(Modifier.width(4.dp))
                                    SelectionMenuSettingsButton(menuText = menuText, onClick = onMenuSettings)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionMenuSettingsDialog(
    visible: Boolean,
    currentItems: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Boolean>) -> Unit
) {
    if (!visible) return

    val allMenuItems = listOf(
        MENU_KEY_HIGHLIGHT to stringResource(R.string.menu_highlight),
        MENU_KEY_UNDERLINE to stringResource(R.string.menu_underline),
        MENU_KEY_NOTE to stringResource(R.string.menu_note),
        MENU_KEY_DICTIONARY to stringResource(R.string.menu_dictionary),
        MENU_KEY_SEARCH to stringResource(R.string.menu_search),
        MENU_KEY_COPY to stringResource(R.string.menu_copy),
        MENU_KEY_REPLACE to stringResource(R.string.menu_replace)
    )
    var localItems by remember { mutableStateOf(currentItems) }
    LaunchedEffect(visible) {
        if (visible) localItems = currentItems
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.selection_menu_settings),
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                allMenuItems.forEach { (key, label) ->
                    val enabled = isMenuEnabled(localItems, key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                localItems = if (localItems.isEmpty()) {
                                    allMenuItems.associate { it.first to (it.first != key) }
                                } else {
                                    localItems.toMutableMap().apply { put(key, !enabled) }
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                localItems = if (localItems.isEmpty()) {
                                    allMenuItems.associate { it.first to (it.first != key) }
                                } else {
                                    localItems.toMutableMap().apply { put(key, checked) }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(localItems) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private data class ReplaceSelectionInfo(
    val selectedText: String,
    val chapterIndex: Int? = null,
    val charStart: Int? = null,
    val charEnd: Int? = null
)

@Composable
private fun ReplaceInputSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    glassBackdrop: Backdrop? = null,
    selectedText: String,
    canReplaceCurrent: Boolean,
    onReplaceAll: (String) -> Unit,
    onReplaceCurrent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    var replacement by remember { mutableStateOf("") }
    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateBottomSheetIn()
        }
    }

    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) isClosing = true
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出容器（自适应高度）
        LiquidGlassSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(24.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = glassBackdrop
        ) {
            val replaceAccent = Color(0xFFFF6268)
            Column {
                // 标题栏
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.menu_replace),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = resolveAppFontFamily(KaiTi),
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    LiquidGlassIconButton(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reader_close),
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = LightBgGray
                    )
                }

                Spacer(Modifier.height(18.dp))

                // 原文展示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = stringResource(R.string.menu_replace),
                        modifier = Modifier.width(64.dp),
                        fontSize = 15.sp,
                        color = AppColors.TextSecondary.copy(alpha = 0.72f)
                    )
                    Text(
                        text = selectedText,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(18.dp))

                // 替换为输入框
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.replace_with_label),
                        modifier = Modifier.width(64.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = replaceAccent
                    )
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(26.dp),
                        fallbackColor = AppColors.BgGray,
                        contentScrimColor = AppColors.BgGray.copy(alpha = 0.22f),
                        transparencyOverride = 0.78f,
                        interactive = false,
                        outlineWidth = 0.9.dp,
                        highlightColor = AppColors.CardBg,
                        highlightAlpha = 0.24f,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = replacement,
                            onValueChange = { replacement = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = AppColors.TextPrimary,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(replaceAccent),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (replacement.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.replace_input_hint),
                                            fontSize = 15.sp,
                                            color = AppColors.TextSecondary.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 双确认按钮：替换全部（白）/ 替换本处（主题色）
                val replaceAllTextColor = Color(0xFF262626)
                Row(Modifier.fillMaxWidth()) {
                    LiquidGlassButton(
                        onClick = {
                            onReplaceAll(replacement)
                            isClosing = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        tintedColor = Color.White,
                        prominentShadow = true,
                        contentColor = replaceAllTextColor
                    ) {
                        Text(
                            text = stringResource(R.string.replace_all),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = replaceAllTextColor
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    LiquidGlassButton(
                        onClick = {
                            onReplaceCurrent(replacement)
                            isClosing = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        tintedColor = AppColors.Accent,
                        prominentShadow = true,
                        contentColor = AppColors.OnAccent,
                        enabled = canReplaceCurrent
                    ) {
                        Text(
                            text = stringResource(R.string.replace_this),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.OnAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(18.dp)
            .background(color)
    )
}

@Composable
private fun MenuChip(label: String, textColor: Color, onClick: () -> Unit) {
    val horizontalPadding = if (LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current) 10.dp else 16.dp
    Text(
        text = label,
        fontSize = 13.sp,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    )
}

/** 独立胶囊菜单：自带入场动画，enterDelayMillis 用于多菜单级联依次弹出 */
@Composable
private fun SelectionMenuPill(
    width: Dp,
    reappearKey: Int,
    menuBg: Color,
    glassBackdrop: Backdrop?,
    forceSolidSurface: Boolean,
    enterDelayMillis: Long = 0L,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    // Entry animation for initial display and after handle dragging ends.
    val enterAlpha = remember(reappearKey) { Animatable(0f) }
    val enterScale = remember(reappearKey) { Animatable(0.75f) }
    // Float upward from 12dp below the final position.
    val enterTranslateY = remember(reappearKey) { Animatable(12f) }
    LaunchedEffect(reappearKey) {
        if (enterDelayMillis > 0) delay(enterDelayMillis)
        launch { enterAlpha.animateTo(1f, tween(250)) }
        launch { enterScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        launch { enterTranslateY.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
    }
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        fallbackColor = menuBg,
        backdrop = glassBackdrop,
        contentScrimColor = menuBg.copy(alpha = 0.18f),
        forceFallback = forceSolidSurface,
        modifier = Modifier
            .width(width)
            .height(52.dp)
            .graphicsLayer {
                scaleX = enterScale.value
                scaleY = enterScale.value
                translationY = enterTranslateY.value
                alpha = enterAlpha.value
                shape = RoundedCornerShape(22.dp)
                shadowElevation = with(density) { 20.dp.toPx() }
                ambientShadowColor = Color.Black.copy(alpha = 0.08f)
                spotShadowColor = Color.Black.copy(alpha = 0.13f)
                clip = false
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 标注行：改色色点 + 移除高亮/划线文字按钮 */
@Composable
private fun SelectionAnnotationRow(
    currentColor: String?,
    removeLabel: String,
    menuText: Color,
    dividerColor: Color,
    onColorChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ReaderHighlightPalette.forEachIndexed { index, (_, color) ->
            if (index > 0) Spacer(Modifier.width(10.dp))
            val isCurrentColor = readerHighlightSlotForColor(currentColor) == index
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(if (isCurrentColor) Modifier.border(2.dp, menuText, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onColorChange(index) }
            )
        }
        Spacer(Modifier.width(6.dp))
        MenuDivider(dividerColor)
        Spacer(Modifier.width(6.dp))
        MenuChip(removeLabel, menuText, onRemove)
    }
}

/** 普通行行尾的浮动菜单设置齿轮入口 */
@Composable
private fun SelectionMenuSettingsButton(
    menuText: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.menu_settings),
            tint = menuText.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── 笔记输入弹窗 ──

@Composable
private fun NoteInputSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    glassBackdrop: Backdrop? = null,
    initialText: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateBottomSheetIn()
        }
    }

    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) isClosing = true
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        LiquidGlassSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .materialBottomSheetMotion(
                    entryOffset = sheetOffset.value,
                    predictiveBackProgress = predictiveBackProgress
                ),
            contentModifier = Modifier
                .padding(bottom = 16.dp)
                .padding(AppSpace.lg),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = glassBackdrop
        ) {
            Column(Modifier.padding(top = 2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LiquidGlassIconButton(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = AppColors.BgGray
                    )
                    Text(stringResource(R.string.reader_notes), fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = resolveAppFontFamily(KaiTi), color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    LiquidGlassIconButton(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "确认",
                        onClick = {
                            onConfirm()
                            isClosing = true
                        },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.OnAccent,
                        normalContainerColor = AppColors.Accent,
                        liquidContainerColor = AppColors.Accent,
                        liquidScrimColor = AppColors.Accent.copy(alpha = 0.72f)
                    )
                }

                Spacer(Modifier.height(AppSpace.md))

                androidx.compose.material3.TextField(
                    value = initialText,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(R.string.note_input_placeholder), fontSize = 14.sp, color = AppColors.TextSecondary) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = AppColors.BgGray,
                        unfocusedContainerColor = AppColors.BgGray,
                        focusedIndicatorColor = AppColors.Accent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                    maxLines = 10
                )
            }
        }
    }
}

// ── 笔记/高亮列表弹窗（Page5 设计规范）──

// 设计规范颜色
private val AccentColor: Color @Composable get() = AppColors.Accent
private val HighlightYellow = Color(0xFFFFEB3B)
private val HighlightBg = Color(0xFFFFFBF0)
private val LightTextSecondary: Color @Composable get() = AppColors.TextSecondary
private val LightBgGray: Color @Composable get() = AppColors.BgGray
private val LightCardBg: Color @Composable get() = AppColors.CardBg

@Composable
private fun NotesListSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    glassBackdrop: Backdrop? = null,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    onNoteClick: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onDeleteNote: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateBottomSheetIn()
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    var pendingJumpNote by remember { mutableStateOf<com.huangder.lumibooks.domain.model.Note?>(null) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateBottomSheetOut()
            pendingJumpNote?.let { onNoteClick(it) }
            pendingJumpNote = null
            onDismiss()
        }
    }

    var activeTag by remember { mutableStateOf("highlight") }
    val highlights = notes.filter { it.note.isEmpty() && it.type != "underline" }
    val underlines = notes.filter { it.note.isEmpty() && it.type == "underline" }
    val noteList = notes.filter { it.note.isNotEmpty() }
    // 追踪是否有笔记项处于"已滑开"状态，点空白处时先关闭滑开项而非关闭整个弹窗
    var anyItemRevealed by remember { mutableStateOf(false) }
    var resetRevealedKey by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        // 遮罩层：有滑开项时先关闭滑开项，否则关闭整个弹窗
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (anyItemRevealed) resetRevealedKey = resetRevealedKey + 1 else isClosing = true
                }
        )

        // 容器层（60% 屏幕高度）
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp),
            fallbackColor = LightCardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = glassBackdrop
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.highlights_notes_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                // 关闭按钮
                LiquidGlassIconButton(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.reader_close),
                    onClick = { isClosing = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = LightBgGray
                )
            }

            Spacer(Modifier.height(16.dp))

            // Tab 切换器（平滑动画）
            HighlightNoteTabSwitcher(
                activeTag = activeTag,
                onTagChange = { activeTag = it }
            )

            Spacer(Modifier.height(16.dp))

            // 列表
            val items = when (activeTag) {
                "highlight" -> highlights
                "underline" -> underlines
                else -> noteList
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            when (activeTag) {
                                "highlight" -> R.string.no_highlights
                                "underline" -> R.string.no_underlines
                                else -> R.string.no_notes
                            }
                        ),
                        fontSize = 14.sp,
                        color = LightTextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(items.size, key = { items[it].id }) { idx ->
                        val item = items[idx]
                        HighlightNoteItem(
                            item = item,
                            onClick = {
                                pendingJumpNote = item
                                isClosing = true
                            },
                            onDelete = { onDeleteNote(item) },
                            resetRevealedKey = resetRevealedKey,
                            onRevealedChanged = { revealed -> anyItemRevealed = revealed },
                            modifier = Modifier.animateItem()
                        )
                        if (idx < items.size - 1) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightNoteTabSwitcher(
    activeTag: String,
    onTagChange: (String) -> Unit
) {
    // 动画：白色背景指示器的位置（0=高亮，1=划线，2=笔记）
    val tabs = listOf(
        "highlight" to R.string.tab_highlight,
        "underline" to R.string.tab_underline,
        "note" to R.string.tab_note
    )
    val tabIndex = when (activeTag) {
        "highlight" -> 0f
        "underline" -> 1f
        else -> 2f
    }
    val indicatorProgress by animateFloatAsState(
        targetValue = tabIndex,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "tabIndicator"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(LightBgGray)
            .padding(2.dp)
    ) {
        val tabCount = tabs.size
        val tabWidth = maxWidth / tabCount
        val indicatorOffset = tabWidth * indicatorProgress

        // 白色背景指示器（平滑移动）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(tabWidth)
                .offset(x = indicatorOffset)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.CardBg)
        )

        // Tab 文字
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { (tag, labelRes) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTagChange(tag) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(labelRes),
                        fontSize = 14.sp,
                        fontWeight = if (activeTag == tag) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (activeTag == tag) AppColors.TextPrimary else LightTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightNoteItem(
    item: com.huangder.lumibooks.domain.model.Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    resetRevealedKey: Int = 0,
    onRevealedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 从 note.color 解析高亮颜色，生成浅色背景版本
    val activePalette = ReaderHighlightPalette
    val highlightColor = remember(item.color, activePalette) {
        try {
            Color(android.graphics.Color.parseColor(resolveReaderHighlightColor(item.color)))
        } catch (_: Exception) {
            Color(0xFFFFEB3B)
        }
    }
    val highlightBg = remember(highlightColor) { highlightColor.copy(alpha = 0.12f) }

    val density = LocalDensity.current
    val revealPx = with(density) { 72.dp.toPx() }   // 目标滑开距离（露出删除键）
    val deletePx = with(density) { 500.dp.toPx() }   // 删除动画滑出距离

    // 状态
    var isRevealed by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var rawOffset by remember { mutableFloatStateOf(0f) }
    val animOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 🔥 父级信号：点空白处时收起已滑开的卡片
    LaunchedEffect(resetRevealedKey) {
        if (resetRevealedKey > 0 && isRevealed) {
            isRevealed = false
            animOffset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 280f))
            rawOffset = 0f
        }
    }
    // 通知父级当前展开状态
    LaunchedEffect(isRevealed) { onRevealedChanged(isRevealed) }

    var isDragging by remember { mutableStateOf(false) }
    val displayOffset = if (isDragging) rawOffset else animOffset.value

    // 进度 0→1（到达 revealPx 时为 1，可超出）
    val progress = remember(displayOffset) { (-displayOffset / revealPx).coerceAtLeast(0f) }
    // 删除图标：从右侧 24dp 滑入 + 淡入
    val deleteIconAlpha = remember(progress) { progress.coerceIn(0f, 1f) }
    val deleteIconTranslationX = remember(progress) { (1f - progress.coerceAtMost(1f)) * 24f }

    /** 阻尼函数：超出部分按对数衰减 */
    fun dampedOverScroll(excess: Float): Float {
        if (excess == 0f) return 0f
        val d = density.density
        val sign = if (excess > 0f) 1f else -1f
        return 40f * d * (1f - Math.exp((-kotlin.math.abs(excess) / (80f * d)).toDouble())).toFloat() * sign
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ── 底层：删除按钮（固定右侧，滑入 + 淡入）──
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = deleteIconAlpha
                        translationX = deleteIconTranslationX
                    }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable(enabled = isRevealed && !isDeleting) {
                        isDeleting = true
                        scope.launch {
                            animOffset.animateTo(-deletePx, tween(250, easing = FastOutSlowInEasing))
                            onDelete()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // ── 顶层：笔记卡片 ──
        Row(
            modifier = Modifier
                .offset { IntOffset(displayOffset.toInt(), 0) }
                .graphicsLayer {
                    // 删除动画中卡片淡出
                    if (isDeleting) alpha = 1f - (-displayOffset / deletePx).coerceIn(0f, 1f)
                    // 超出时微缩，增加弹性手感
                    if (progress > 1f) scaleX = 1f - (progress - 1f) * 0.01f
                }
                .background(highlightBg, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // 🔥 对齐到动画当前位置，消除动画→拖拽切换时的跳变
                            rawOffset = animOffset.value
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                val from = rawOffset
                                animOffset.snapTo(from)
                                if (isRevealed) {
                                    // 已展开状态：根据位置决定
                                    if (-from < revealPx * 0.3f) {
                                        // 滑回超过 70% → 关闭
                                        animOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                        isRevealed = false
                                    } else {
                                        // 还在删除区 → 弹回露出位置
                                        animOffset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    }
                                } else {
                                    // 未展开状态
                                    if (-from > revealPx * 0.4f) {
                                        animOffset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f))
                                        isRevealed = true
                                    } else {
                                        animOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    }
                                }
                                rawOffset = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                animOffset.snapTo(rawOffset)
                                animOffset.animateTo(
                                    if (isRevealed) -revealPx else 0f,
                                    spring(dampingRatio = 0.6f, stiffness = 300f)
                                )
                                rawOffset = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newRaw = rawOffset + dragAmount
                            rawOffset = when {
                                // 向左拖拽：超出 revealPx 后施加阻尼
                                newRaw < -revealPx -> {
                                    val excess = (-newRaw) - revealPx
                                    -revealPx - dampedOverScroll(excess)
                                }
                                // 向右拖拽超过原位（从展开状态滑回 + 超出）：施加阻尼
                                newRaw > 0f -> dampedOverScroll(newRaw)
                                // 正常范围：跟随手指
                                else -> newRaw
                            }
                        }
                    )
                }
                .clickable(enabled = !isRevealed, indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
                .padding(16.dp)
        ) {
            // 左侧高亮色竖条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(highlightColor)
            )

            Spacer(Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.selectedText.replace('\n', ' '),
                    fontSize = 14.sp,
                    color = AppColors.TextPrimary,
                    maxLines = 2
                )
                if (item.note.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.note,
                        fontSize = 13.sp,
                        color = LightTextSecondary,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.reader_chapter_fallback, item.chapterIndex + 1), fontSize = 12.sp, color = AccentColor)
                    Text(
                        java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt)),
                        fontSize = 12.sp,
                        color = AccentColor
                    )
                }
            }
        }
    }
}

private fun parseNoteColor(colorString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (_: IllegalArgumentException) {
        Color(0xFFEBB700)
    }
}

// ── 选择手柄 Composable ──

/**
 * 文本选择手柄（圆形，深红棕色 + 白色边框）。
 * 在 Compose 层渲染，通过 [ReadView.moveSelectionHandle] 驱动选择范围变更。
 */
@Composable
private fun SelectionHandle(
    centerX: Float,
    centerY: Float,
    handleColor: Color = Color(0xFF6C231D),
    onDrag: (newCenterX: Float, newCenterY: Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    val density = LocalDensity.current
    val handleSizeDp = 24.dp
    val handleRadiusPx = with(density) { handleSizeDp.toPx() / 2f }

    // 🔥 确保 pointerInput 内部捕获最新的值（避免 recompose 后使用旧 lambda）
    val currentCenterX by rememberUpdatedState(centerX)
    val currentCenterY by rememberUpdatedState(centerY)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        Modifier
            .offset {
                IntOffset(
                    (currentCenterX - handleRadiusPx).toInt(),
                    (currentCenterY - handleRadiusPx).toInt()
                )
            }
            .size(handleSizeDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(
                            currentCenterX + dragAmount.x,
                            currentCenterY + dragAmount.y
                        )
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f
            drawCircle(handleColor, r, Offset(cx, cy))
            drawCircle(Color.White, r, Offset(cx, cy), style = Stroke(3.dp.toPx()))
        }
    }
}

/**
 * Canvas 引擎（阅读器排版）的注释气泡（同窗口覆盖层，非 Popup 窗口）：
 * - 避免独立窗口首帧定位造成的闪现；
 * - 与阅读内容同一坐标空间，液态玻璃折射的正是气泡正下方的内容。
 * 锚定在注释链接的点击位置附近，点击外部关闭，正文过长时内部滚动。
 * 进出动画遵循动效规范（进入 180ms Decelerate / 退出 140ms Accelerate），
 * 阴影保持大羽化但有足够对比度以分辨气泡边缘。
 */
@Composable
private fun ReaderFootnoteBubbleOverlay(
    footnote: ReaderFootnoteBubble,
    progress: Float,
    rootWindowPosition: Offset,
    rootSize: IntSize,
    isLiquidGlass: Boolean,
    glassBackdrop: Backdrop?,
    backgroundColor: Color,
    contentColor: Color,
    fontSizeSp: Float,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val anchorX = footnote.anchorWindowX - rootWindowPosition.x
    val anchorY = footnote.anchorWindowY - rootWindowPosition.y
    // 首帧用估算尺寸占位，测量后（onSizeChanged）按真实尺寸重新定位
    var bubbleSize by remember { mutableStateOf(IntSize(320, 180)) }
    val gapPx = with(density) { 14.dp.toPx() }
    val edgePx = with(density) { 10.dp.toPx() }
    val maxWidthPx = minOf((rootSize.width * 0.84f).toInt(), with(density) { 420.dp.roundToPx() })
        .coerceAtLeast(200)
    val maxHeightPx = (rootSize.height * 0.4f).toInt().coerceAtLeast(200)
    val maxWidthDp = with(density) { maxWidthPx.toDp() }
    val maxHeightDp = with(density) { maxHeightPx.toDp() }
    val bubbleShape = RoundedCornerShape(16.dp)

    val bubbleWidth = bubbleSize.width.coerceIn(200, maxWidthPx)
    val left = (anchorX - bubbleWidth / 2f)
        .roundToInt()
        .coerceIn(edgePx.roundToInt(), (rootSize.width - bubbleWidth - edgePx).roundToInt())
    val placeBelow = anchorY < rootSize.height / 2f
    val top = if (placeBelow) {
        (anchorY + gapPx).roundToInt()
    } else {
        (anchorY - gapPx - bubbleSize.height).roundToInt()
    }.coerceIn(edgePx.roundToInt(), (rootSize.height - bubbleSize.height - edgePx).roundToInt().coerceAtLeast(0))

    Box(modifier = Modifier.fillMaxSize()) {
        // 点击气泡外任意处关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(left, top) }
                // 真实尺寸测得前保持透明，避免用估算尺寸定位导致的首帧位置闪跳
                .graphicsLayer {
                    alpha = progress
                    val scale = 0.96f + 0.04f * progress
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            val sizeModifier = Modifier.onSizeChanged { bubbleSize = it }
            if (isLiquidGlass) {
                LiquidGlassSurface(
                    shape = bubbleShape,
                    fallbackColor = backgroundColor,
                    modifier = sizeModifier
                        .widthIn(min = 200.dp, max = maxWidthDp)
                        .heightIn(max = maxHeightDp)
                        .shadow(
                            elevation = 20.dp,
                            shape = bubbleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.14f),
                            spotColor = Color.Black.copy(alpha = 0.20f)
                        ),
                    backdrop = glassBackdrop,
                    contentScrimColor = backgroundColor.copy(alpha = 0.52f)
                ) {
                    FootnoteBubbleText(
                        footnote = footnote,
                        contentColor = contentColor,
                        fontSizeSp = fontSizeSp
                    )
                }
            } else {
                Surface(
                    shape = bubbleShape,
                    color = backgroundColor,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.16f)),
                    modifier = sizeModifier
                        .shadow(
                            elevation = 20.dp,
                            shape = bubbleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.14f),
                            spotColor = Color.Black.copy(alpha = 0.20f)
                        )
                        .widthIn(min = 200.dp, max = maxWidthDp)
                        .heightIn(max = maxHeightDp)
                ) {
                    FootnoteBubbleText(
                        footnote = footnote,
                        contentColor = contentColor,
                        fontSizeSp = fontSizeSp
                    )
                }
            }
        }
    }
}

@Composable
private fun FootnoteBubbleText(
    footnote: ReaderFootnoteBubble,
    contentColor: Color,
    fontSizeSp: Float
) {
    Text(
        text = footnote.text,
        color = contentColor,
        fontSize = (fontSizeSp * 0.92f).sp,
        lineHeight = (fontSizeSp * 0.92f * 1.6f).sp,
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

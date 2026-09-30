package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.ui.icons.AppIcons

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
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import com.huangder.lumibooks.ui.components.AnnotationTagSheet
import com.huangder.lumibooks.ui.components.AnnotationTagChips
import com.huangder.lumibooks.ui.components.AnnotationListFilters
import com.huangder.lumibooks.ui.bookshelf.ReadingMarkFilter
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
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.domain.model.resolveImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalConfiguration
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.layout.currentAdaptiveWindowInfo
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LocalLiquidGlassContrastEnabled
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.EditInputDialog
import com.huangder.lumibooks.ui.components.SwipeRevealItem
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LiquidGlassSegmentedControl
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.LiquidGlassSheetContainer
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.ReaderSystemBarStyle
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.bookshelf.CoverSearchEngine
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.ui.reader.engine.BookmarkPullGestureTracker
import com.huangder.lumibooks.ui.reader.engine.resolveReaderTypeface
import com.huangder.lumibooks.ui.reader.engine.ResolvedReaderTypeface
import com.huangder.lumibooks.ui.reader.engine.ReaderImageHit
import com.huangder.lumibooks.ui.reader.engine.ReaderHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.ReaderSearchHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.TtsHighlightRange
import com.huangder.lumibooks.ui.reader.engine.TtsSentenceHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.WaveUnderlineSpan
import com.huangder.lumibooks.ui.reader.engine.RoundedHighlightTextView
import com.huangder.lumibooks.ui.reader.engine.SentenceJumpDoubleTapGate
import com.huangder.lumibooks.ui.reader.engine.ReaderLineGeometry
import com.huangder.lumibooks.ui.reader.engine.ReaderGuideLine
import com.huangder.lumibooks.ui.reader.engine.readableGuideLines
import com.huangder.lumibooks.ui.reader.engine.readerGuideShadeColor
import com.huangder.lumibooks.ui.reader.engine.readerGuideStepIndex
import com.huangder.lumibooks.ui.reader.engine.readerGuideScrollDistance
import com.huangder.lumibooks.ui.reader.engine.readerGuideFocusedIndex
import com.huangder.lumibooks.ui.reader.engine.drawReaderGuideOverlay
import com.huangder.lumibooks.ui.reader.engine.ReaderBackgroundConfig
import com.huangder.lumibooks.ui.reader.engine.ReaderLayoutConfig
import com.huangder.lumibooks.ui.reader.engine.ReaderRenderConfig
import com.huangder.lumibooks.util.performance.ReaderOpenPerformance
import com.huangder.lumibooks.util.performance.ReaderOpenStage
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.parser.TxtTocRule
import com.huangder.lumibooks.util.parser.TxtTocRuleBuiltIns
import com.huangder.lumibooks.util.parser.TxtTocRuleCompiler
import com.huangder.lumibooks.util.parser.TxtTocRuleDiagnostics
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.ReaderPageDirection
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.RuleStyle
import com.huangder.lumibooks.highlight.RuleStyleJson
import com.huangder.lumibooks.util.DownloadedFonts
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.util.parser.TxtEncoding
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsPageChangeOrigin
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
    val pageIndex: Int,
    val chapterFraction: Float? = null
)

internal data class ContinuousScrollRequest(
    val chapterIndex: Int,
    val chapterFraction: Float = 0f,
    val origin: TtsPageChangeOrigin = TtsPageChangeOrigin.USER,
    val characterOffset: Int? = null,
    val requestId: Long = System.nanoTime()
)

private data class ReaderMenuSnapshot(
    val chapterIndex: Int,
    val chapterTitle: String,
    val pageIndex: Int,
    val pageCount: Int,
    val bookProgressPercent: Float,
    val rightPageIndex: Int? = null,
    val rightChapterIndex: Int? = null
)

private data class ReaderBookmarkPageKey(
    val chapterIndex: Int,
    val pageIndex: Int,
    val characterOffset: Int?
)

private enum class BookmarkPullSettleMode {
    ADD_COMMIT,
    ADD_CANCEL,
    REMOVE_COMMIT,
    REMOVE_CANCEL
}

private fun formatReaderPageLabel(
    currentPage: Int,
    rightPageIndex: Int?,
    chapterPageCount: Int,
    rightChapterIndex: Int? = null,
    currentChapterIndex: Int? = null
): String {
    val total = chapterPageCount.coerceAtLeast(1)
    return if (rightPageIndex != null && rightPageIndex >= 0) {
        if (rightChapterIndex != null && currentChapterIndex != null &&
            rightChapterIndex >= 0 && rightChapterIndex != currentChapterIndex
        ) {
            "$currentPage / $total · 第${rightChapterIndex + 1}章 ${rightPageIndex + 1}"
        } else {
            "$currentPage–${rightPageIndex + 1} / $total"
        }
    } else {
        "$currentPage / $total"
    }
}

internal data class ContinuousSearchHighlight(
    val chapterIndex: Int,
    val start: Int,
    val end: Int
)

internal data class ContinuousTextSelection(
    val start: Int,
    val end: Int,
    val selectedText: String,
    val startX: Float,
    val endX: Float,
    val topY: Float,
    val bottomY: Float,
    val annotationOnly: Boolean = false
)

internal fun isContinuousSingleImageChapter(
    text: CharSequence,
    imageSpanCount: Int
): Boolean = imageSpanCount == 1 && text.all { character ->
    character.isWhitespace() || character == '\uFFFC'
}

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

internal class ContinuousSelectionController {
    var activeView: ContinuousSelectableTextView? = null
    var selection: com.huangder.lumibooks.ui.reader.engine.ReaderContentSelection? = null
        private set

    fun update(view: ContinuousSelectableTextView, chapter: Int, start: Int, end: Int) {
        if (activeView !== view) clear()
        activeView = view
        selection = com.huangder.lumibooks.ui.reader.engine.ReaderContentSelection(
            chapter, view.text.length, minOf(start, end), maxOf(start, end)
        )
    }

    fun currentSelection(): com.huangder.lumibooks.ui.reader.engine.ReaderContentSelection? {
        val owner = activeView?.takeIf { it.isAttachedToWindow } ?: return null
        val saved = selection ?: return null
        val start = Selection.getSelectionStart(owner.text)
        val end = Selection.getSelectionEnd(owner.text)
        if (start < 0 || end < 0 || start == end || maxOf(start, end) > owner.text.length) return null
        return saved.copy(chapterLength = owner.text.length, anchor = start, focus = end)
    }

    fun released(view: ContinuousSelectableTextView) {
        if (activeView !== view) return
        activeView = null
        selection = null
    }

    fun clear() {
        val previous = activeView
        previous?.clearReaderSelection()
        activeView = null
        selection = null
    }
}

internal class ContinuousSelectableTextView(context: Context) : RoundedHighlightTextView(context) {
    public override var readerImageBleed: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }
    private val drawingWindow = ContinuousReaderDrawingWindow()
    private val drawingViewport = android.graphics.Rect()
    private var drawingGeometry: ReaderLineGeometry? = null
    private var drawingLayout: android.text.Layout? = null
    private var drawingLayoutWidth = -1
    private var drawingPaint: android.text.TextPaint? = null
    private var drawingJustification = -1
    private var drawingForceLastLine = false
    private val drawingGeometryWatcher = object : android.text.SpanWatcher, android.text.NoCopySpan {
        private fun changed(span: Any) {
            if (span is android.text.style.MetricAffectingSpan || span is android.text.style.ParagraphStyle) {
                drawingGeometry = null
            }
        }
        override fun onSpanAdded(text: Spannable, what: Any, start: Int, end: Int) = changed(what)
        override fun onSpanRemoved(text: Spannable, what: Any, start: Int, end: Int) = changed(what)
        override fun onSpanChanged(text: Spannable, what: Any, oldStart: Int, oldEnd: Int,
            newStart: Int, newEnd: Int) = changed(what)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // LazyColumn can measure this chapter on every scroll. Geometry is
        // invalidated by text/metric changes below and by layout/paint checks.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        drawingGeometry = null
        super.onTextChanged(text, start, before, count)
    }

    override fun readerDrawingGeometry(textLayout: android.text.Layout, spanned: Spanned): ReaderLineGeometry {
        if (spanned is Spannable && spanned.getSpanStart(drawingGeometryWatcher) < 0) {
            spanned.setSpan(drawingGeometryWatcher, 0, spanned.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        val samePaint = android.os.Build.VERSION.SDK_INT >= 28 &&
            drawingPaint?.equalsForTextMeasurement(textLayout.paint) == true
        if (drawingGeometry == null || drawingLayout !== textLayout ||
            drawingLayoutWidth != textLayout.width || !samePaint ||
            drawingJustification != readerJustificationMode ||
            drawingForceLastLine != readerForceLastLineJustification) {
            drawingGeometry = super.readerDrawingGeometry(textLayout, spanned)
            drawingLayout = textLayout
            drawingLayoutWidth = textLayout.width
            drawingPaint = android.text.TextPaint(textLayout.paint)
            drawingJustification = readerJustificationMode
            drawingForceLastLine = readerForceLastLineJustification
        }
        return checkNotNull(drawingGeometry)
    }

    /** Compose placement can move this chapter without invalidating its RenderNode. */
    internal fun updateReaderDrawingViewport() {
        val viewport = onSelectionViewport ?: return
        if (!viewport(drawingViewport)) drawingViewport.setEmpty()
        if (drawingWindow.update(drawingViewport, width, height)) invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        if (onSelectionViewport == null) {
            super.onDraw(canvas)
            return
        }
        updateReaderDrawingViewport()
        if (drawingWindow.bounds.isEmpty) return
        // A hardware RecordingCanvas is sized to this entire chapter; its clip
        // does not inherit LazyColumn's viewport. Limit recording explicitly.
        val saved = canvas.save()
        try {
            canvas.clipRect(drawingWindow.bounds)
            super.onDraw(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    var lineGuideMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) clearReaderSelection()
        }
    var onReaderTap: (() -> Unit)? = null
    var savedAnnotations: List<com.huangder.lumibooks.domain.model.Note> = emptyList()
    var onLinkTap: ((String, Float, Float) -> Unit)? = null
    /** 听书进行中双击正文：回调章节级字符偏移，交由上层跳转朗读。 */
    var onSentenceDoubleTap: ((Int) -> Unit)? = null
    /** 听书进行中为区分双击，单击动作需延后一个双击超时。 */
    var ttsJumpEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            sentenceJumpGate.reset()
            if (!value) cancelPendingTapAction()
        }
    var onImageLongPress: ((ReaderImageHit) -> Unit)? = null
    var onSelectionChanging: (() -> Unit)? = null
    var onReaderSelection: ((ContinuousTextSelection) -> Unit)? = null
    var onReaderSelectionRangeChanged: ((Int, Int) -> Unit)? = null
    var onReaderSelectionCleared: (() -> Unit)? = null
    var onExplicitReveal: ((android.graphics.Rect) -> Boolean)? = null
    var onAccessibilityScroll: ((Boolean) -> Boolean)? = null
    var onSelectionEdgeScroll: ((Float) -> Unit)? = null
    var onSelectionViewport: ((android.graphics.Rect) -> Boolean)? = null
    private var selectionEdgeDirection = 0
    private var selectionPointerWindowX = 0f
    private var selectionPointerWindowY = 0f
    private var explicitReveal = false
    private var annotationKey: Any? = null

    private var sourceText: CharSequence? = null
    private var replacingText = false
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapDownTime = 0L
    private var tapMoved = false
    private val tapSlopPx =
        android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val tapDurationLimitMs =
        android.view.ViewConfiguration.getLongPressTimeout().toLong()
    private var pendingTapAction: Runnable? = null
    private val sentenceJumpGate = SentenceJumpDoubleTapGate(
        timeoutMs = android.view.ViewConfiguration.getDoubleTapTimeout().toLong(),
        slopPx = android.view.ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
    )
    private val selectionDispatch = Runnable { dispatchReaderSelection() }
    private val selectionClearDispatch = Runnable {
        if (!replacingText && readerDraggingStartHandle == null && !hasReaderSelection()) {
            endReaderSelectionSession()
            onReaderSelectionCleared?.invoke()
        }
    }
    private val selectionEdgeScroll = object : Runnable {
        override fun run() {
            if (readerDraggingStartHandle == null || !isAttachedToWindow) return
            val visible = android.graphics.Rect()
            if (!selectionVisibleRect(visible)) return
            val direction = selectionDirectionAtPointer(visible)
            selectionEdgeDirection = direction
            if (direction == 0 || !updateSelectionAtPointer(visible)) return
            // Keep the selection in its owning chapter and stop at its actual edge.
            val remaining = if (direction < 0) visible.top else height - visible.bottom
            if (remaining <= 0) return
            val scroll = onSelectionEdgeScroll ?: return
            scroll(direction * minOf(12f * resources.displayMetrics.density, remaining.toFloat()))
            postDelayed(this, 16L)
        }
    }

    private fun selectionVisibleRect(out: android.graphics.Rect): Boolean =
        (onSelectionViewport?.invoke(out) ?: getLocalVisibleRect(out)) && out.height() > 1

    private fun selectionDirectionAtPointer(visible: android.graphics.Rect): Int {
        val location = IntArray(2).also(::getLocationInWindow)
        val y = selectionPointerWindowY - location[1]
        val edge = minOf(24f * resources.displayMetrics.density, visible.height() / 2f)
        return when {
            y < visible.top + edge -> -1
            y > visible.bottom - edge -> 1
            else -> 0
        }
    }

    private fun updateSelectionAtPointer(visible: android.graphics.Rect): Boolean {
        val location = IntArray(2).also(::getLocationInWindow)
        // Keep a stationary finger in window coordinates while LazyColumn moves
        // the chapter underneath it. Native and custom selection share geometry.
        return updateReaderSelectionHandlePosition(
            selectionPointerWindowX - location[0],
            (selectionPointerWindowY - location[1]).coerceIn(visible.top + 1f, visible.bottom - 1f)
        )
    }

    init {
        includeFontPadding = false
        gravity = android.view.Gravity.TOP
        // TextView's default factory converts the chapter to SpannableString,
        // whose span queries scan every word in the chapter. Keep an indexed
        // buffer for dense bionic spans, with an independent copy for selection
        // and live annotations (never mutate the cached chapter).
        setSpannableFactory(object : Spannable.Factory() {
            override fun newSpannable(source: CharSequence): Spannable = SpannableStringBuilder(source)
        })
        setTextIsSelectable(true)
        readerSelectionColor = 0x40007AFF
        highlightColor = android.graphics.Color.TRANSPARENT
        configureReaderSelectionHandles(0xFF448AFF.toInt())
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
        }
        setOnClickListener {
            // 听书进行中，单击/双击统一由 onTouchEvent 的手势判定处理。
            if (ttsJumpEnabled) return@setOnClickListener
            val spannable = text as? Spannable
            val start = spannable?.let(Selection::getSelectionStart) ?: -1
            val end = spannable?.let(Selection::getSelectionEnd) ?: -1
            if (start < 0 || end <= start) {
                performReaderTap(lastTapX, lastTapY)
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
        if (lineGuideMode) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTapX = event.x
                lastTapY = event.y
                tapDownX = event.x
                tapDownY = event.y
                tapDownTime = event.eventTime
                tapMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                lastTapX = event.x
                lastTapY = event.y
                if (kotlin.math.abs(event.x - tapDownX) > tapSlopPx ||
                    kotlin.math.abs(event.y - tapDownY) > tapSlopPx
                ) {
                    tapMoved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                lastTapX = event.x
                lastTapY = event.y
            }
        }
        if (ttsJumpEnabled && event.actionMasked == MotionEvent.ACTION_UP) {
            val isShortTap = !tapMoved &&
                (event.eventTime - tapDownTime).coerceAtLeast(0L) <= tapDurationLimitMs
            if (isShortTap) {
                val hadSelection = hasReaderSelection()
                when (sentenceJumpGate.classify(event.eventTime, tapDownX, tapDownY)) {
                    SentenceJumpDoubleTapGate.TapDecision.DOUBLE -> {
                        cancelPendingTapAction()
                        handleSentenceDoubleTap(tapDownX, tapDownY)
                        // 中止子视图的原生触摸序列，避免系统把第二次点击当成选词双击。
                        abortNativeTouchStream(event)
                        return true
                    }
                    SentenceJumpDoubleTapGate.TapDecision.SINGLE ->
                        scheduleTapAction {
                            if (!hadSelection) performReaderTap(tapDownX, tapDownY)
                        }
                }
            }
        }
        if (event.actionMasked != MotionEvent.ACTION_CANCEL) {
            val location = IntArray(2).also(::getLocationInWindow)
            selectionPointerWindowX = location[0] + event.x
            selectionPointerWindowY = location[1] + event.y
        }
        if (event.actionMasked == MotionEvent.ACTION_UP && readerDraggingStartHandle != null) {
            val visible = android.graphics.Rect()
            if (selectionVisibleRect(visible)) updateSelectionAtPointer(visible)
        }
        val wasDraggingHandle = readerDraggingStartHandle != null
        val handled = super.onTouchEvent(event)
        if ((event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) &&
            readerDraggingStartHandle != null) {
            removeCallbacks(selectionDispatch)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onSelectionChanging?.invoke()
            val visible = android.graphics.Rect()
            if (selectionVisibleRect(visible)) {
                selectionEdgeDirection = selectionDirectionAtPointer(visible)
                removeCallbacks(selectionEdgeScroll)
                if (selectionEdgeDirection != 0) post(selectionEdgeScroll)
            }
        } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            selectionEdgeDirection = 0
            removeCallbacks(selectionEdgeScroll)
            if (!hasReaderSelection()) post(selectionClearDispatch)
            else if (wasDraggingHandle) post(selectionDispatch)
        }
        return handled
    }

    private fun hasReaderSelection(): Boolean {
        val spannable = text as? Spannable ?: return false
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        return start >= 0 && end >= 0 && end != start
    }

    private fun abortNativeTouchStream(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.onTouchEvent(cancel)
        cancel.recycle()
    }

    /** Linked images retain navigation; ordinary images use the reader menu. */
    private fun performReaderTap(x: Float, y: Float) {
        val offset = characterOffsetAt(x, y)
        val annotation = offset?.let { tappedOffset ->
            savedAnnotations.firstOrNull { note ->
                (note.type == "highlight" || note.type == "underline") &&
                    tappedOffset >= note.startPosition && tappedOffset < note.endPosition
            }
        }
        if (annotation != null) {
            val location = IntArray(2)
            getLocationOnScreen(location)
            onReaderSelection?.invoke(
                ContinuousTextSelection(
                    start = annotation.startPosition,
                    end = annotation.endPosition,
                    selectedText = annotation.selectedText,
                    startX = location[0] + x - 1f,
                    endX = location[0] + x + 1f,
                    topY = location[1] + y - 1f,
                    bottomY = location[1] + y + 1f,
                    annotationOnly = true
                )
            )
            return
        }
        val image = readerImageAt(x, y)
        when {
            image?.link != null -> onLinkTap?.invoke(image.link, x, y)
            image?.hasAction == true -> Unit
            image != null -> onReaderTap?.invoke()
            else -> readerLinkAt(x, y)
                ?.let { onLinkTap?.invoke(it, x, y) }
                ?: onReaderTap?.invoke()
        }
    }

    private fun scheduleTapAction(action: () -> Unit) {
        cancelPendingTapAction()
        val runnable = Runnable {
            pendingTapAction = null
            action()
        }
        pendingTapAction = runnable
        postDelayed(runnable, sentenceJumpGate.timeout)
    }

    private fun cancelPendingTapAction() {
        pendingTapAction?.let { removeCallbacks(it) }
        pendingTapAction = null
    }

    private fun handleSentenceDoubleTap(x: Float, y: Float) {
        val offset = characterOffsetAt(x, y) ?: return
        clearReaderSelection()
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        onSentenceDoubleTap?.invoke(offset)
    }

    /** 返回该点对应的章节级字符偏移（与 TTS 淡高亮使用同一坐标空间）。 */
    private fun characterOffsetAt(x: Float, y: Float): Int? {
        val spannable = text as? Spannable ?: return null
        val textLayout = layout ?: return null
        if (spannable.isEmpty()) return null
        val localX = x - totalPaddingLeft + scrollX
        val localY = y - totalPaddingTop + scrollY
        if (localX < 0f || localY < 0f || localY >= textLayout.height) return null
        val line = textLayout.getLineForVertical(localY.toInt())
        return (readerOffsetForHorizontal(line, localX)
            ?: textLayout.getOffsetForHorizontal(line, localX))
            .coerceIn(0, spannable.length - 1)
    }

    private fun readerLinkAt(x: Float, y: Float): String? {
        val spannable = text as? Spannable ?: return null
        val textLayout = layout ?: return null
        if (spannable.isEmpty()) return null
        val localX = x - totalPaddingLeft + scrollX
        val localY = y - totalPaddingTop + scrollY
        if (localX < 0f || localY < 0f || localY >= textLayout.height) return null
        val line = textLayout.getLineForVertical(localY.toInt())
        val offset = (readerOffsetForHorizontal(line, localX)
            ?: textLayout.getOffsetForHorizontal(line, localX))
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
            val bounds = continuousImageBounds(textLayout, image, readerJustificationMode) ?: continue
            val left = totalPaddingLeft + bounds.left - scrollX
            val width = bounds.width()
            val bottom = totalPaddingTop + bounds.bottom - scrollY
            val top = totalPaddingTop + bounds.top - scrollY
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
        val oldStart = Selection.getSelectionStart(text)
        val oldEnd = Selection.getSelectionEnd(text)
        val hadSelection = oldStart >= 0 && oldEnd >= 0 && oldEnd != oldStart
        val preserveSelection = hadSelection &&
            android.text.TextUtils.equals(text, value)
        sourceText = value
        annotationKey = null
        replacingText = true
        try {
            setText(value, TextView.BufferType.SPANNABLE)
            if (preserveSelection) Selection.setSelection(text as Spannable, oldStart, oldEnd)
        } finally {
            replacingText = false
        }
        if (preserveSelection) {
            removeCallbacks(selectionDispatch)
            post(selectionDispatch)
        } else if (hadSelection) {
            clearReaderSelection()
        }
    }

    fun updateReaderAnnotations(key: Any, update: (Spannable) -> Unit) {
        if (annotationKey == key) return
        (text as? Spannable)?.let(update)
        annotationKey = key
        invalidate()
    }

    fun clearReaderSelection() {
        selectionEdgeDirection = 0
        removeCallbacks(selectionEdgeScroll)
        removeCallbacks(selectionDispatch)
        removeCallbacks(selectionClearDispatch)
        replacingText = true
        (text as? Spannable)?.let(Selection::removeSelection)
        replacingText = false
        endReaderSelectionSession()
        clearFocus()
        onReaderSelectionCleared?.invoke()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (replacingText) return
        removeCallbacks(selectionDispatch)
        removeCallbacks(selectionClearDispatch)
        if (selStart < 0 || selEnd < 0 || selEnd == selStart) {
            if (readerDraggingStartHandle != null) onSelectionChanging?.invoke()
            // The native controller temporarily removes Selection while it replaces its
            // buffer. Observe the completed change, not that intermediate empty range.
            post(selectionClearDispatch)
            return
        }
        onReaderSelectionRangeChanged?.invoke(selStart, selEnd)
        onSelectionChanging?.invoke()
        if (readerDraggingStartHandle == null) postDelayed(selectionDispatch, 240L)
    }

    // A selectable TextView asks its parent to reveal the cursor after focus/setText.
    // LazyColumn owns the viewport; replaying that request can pull it to offset zero.
    override fun bringPointIntoView(offset: Int): Boolean = revealReaderOffset(offset)

    // The entire chapter is measured inside LazyColumn. TextView/Editor must not
    // introduce another (sometimes negative) scroll offset when focus changes.
    override fun scrollTo(x: Int, y: Int) = super.scrollTo(0, 0)

    override fun bringPointIntoView(offset: Int, requestRectWithoutFocus: Boolean): Boolean =
        revealReaderOffset(offset)

    private fun revealReaderOffset(offset: Int): Boolean {
        if (!explicitReveal) return false
        val current = layout ?: return false
        val line = current.getLineForOffset(offset.coerceIn(0, text.length))
        return onExplicitReveal?.invoke(android.graphics.Rect(0, current.getLineTop(line),
            width, current.getLineBottom(line))) == true
    }

    override fun requestRectangleOnScreen(rectangle: android.graphics.Rect, immediate: Boolean): Boolean =
        explicitReveal && onExplicitReveal?.invoke(rectangle) == true

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
            action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            return onAccessibilityScroll?.invoke(
                action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ) == true
        }
        explicitReveal = true
        return try { super.performAccessibilityAction(action, arguments) } finally { explicitReveal = false }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(selectionDispatch)
        removeCallbacks(selectionClearDispatch)
        cancelPendingTapAction()
        clearReaderSelection()
        super.onDetachedFromWindow()
    }

    private fun dispatchReaderSelection() {
        if (readerDraggingStartHandle != null) return
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
                startX = originX + (readerHorizontalPosition(start)
                    ?: textLayout.getPrimaryHorizontal(start)),
                endX = originX + (readerHorizontalPosition(end, trailing = true)
                    ?: textLayout.getPrimaryHorizontal(end)),
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
 * 这次渲染实际使用的主题。
 *
 * 墨水屏与原书排版（「原排版」套装）都固定日间：原排版要让原书自己的底色与文字颜色
 * 原样呈现，跟随深浅模式注入夜间主题会把整页反色，浅色底上的深色字被翻成白字而看不清。
 */
internal fun resolveReaderRenderingTheme(
    eInkMode: Boolean,
    publisherPaintSuiteActive: Boolean,
    nightDisplay: Boolean,
    readerTheme: String,
    hasImageBackground: Boolean
): String {
    if (eInkMode || publisherPaintSuiteActive) return "day"
    if (!nightDisplay || hasImageBackground) return readerTheme
    return when (readerTheme) {
        "day" -> "night"
        "sepia" -> "sepia_dark"
        "green" -> "green_dark"
        else -> readerTheme
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    onFirstContentDrawn: () -> Unit = {},
    onInteractive: () -> Unit = {},
    readerActive: Boolean = true,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val backgroundPreparationEnabled = LocalReaderOpeningComplete.current
    val rawUiState by viewModel.uiState.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val ttsCurrentPage by viewModel.ttsCurrentPage.collectAsState()
    val ttsCurrentSentence by viewModel.ttsSentencePosition.collectAsState()
    val eInkMode = rawUiState.eInkModeEnabled
    val motionEnabled = LocalMotionEnabled.current
    val basePageTransition = if (eInkMode) "none" else rawUiState.pageTransition
    val appIsDark = LocalIsDarkTheme.current
    val nightDisplay = if (eInkMode) false else when (rawUiState.readerDisplayMode) {
        "day" -> false
        "night" -> true
        else -> appIsDark
    }
    val uiState = rawUiState.withActiveThemeSettingsForMode(nightDisplay)
    val activeThemeSettings = uiState.activeThemeSettingsForMode(nightDisplay)
    val editorThemeSettings = uiState.activeThemeSettingsForMode(uiState.themeEditingDark)
    val effectiveReaderTheme = if (eInkMode) "day" else activeThemeSettings.backgroundSelection
    val effectiveReaderBackgroundSelection = if (eInkMode) "day" else activeThemeSettings.backgroundSelection
    // 「原排版」套装：阅读器不参与配色，原书自己的底色/背景图/文字颜色照原样渲染。
    // 墨水屏模式保持既有行为（强制日间、不渲染原书背景），不受该套装影响。
    val publisherPaintSuiteActive = uiState.keepsPublisherPaint() && !eInkMode
    val publisherFallbackPaperColor = 0xFFFBFBFC.toInt()
    val effectivePreserveEpubBackground = when {
        publisherPaintSuiteActive -> true
        eInkMode -> false
        else -> uiState.preserveEpubBackground
    }
    val effectiveBionicReadingEnabled = if (eInkMode) false else uiState.bionicReadingEnabled
    val effectiveReaderTextColor = when {
        publisherPaintSuiteActive -> null
        eInkMode -> 0xFF111111.toInt()
        else -> activeThemeSettings.textColor
    }
    val selectedReaderBackgroundForTheme = uiState.customReaderBackgrounds.firstOrNull {
        it.selectionKey == effectiveReaderBackgroundSelection
    }
    val renderingTheme = resolveReaderRenderingTheme(
        eInkMode = eInkMode,
        publisherPaintSuiteActive = publisherPaintSuiteActive,
        nightDisplay = nightDisplay,
        readerTheme = effectiveReaderTheme,
        hasImageBackground = selectedReaderBackgroundForTheme?.type == ReaderBackgroundType.IMAGE
    )
    val storedNotes by viewModel.notes.collectAsState()
    val notes = remember(storedNotes) { storedNotes.filterNot { isEpubComicInk(it.type) } }
    val readerNotes by viewModel.readerNotes.collectAsState()
    val highlightRules by viewModel.highlightRules.collectAsState()
    val highlightSettings by viewModel.highlightSettings.collectAsState()
    val highlightScanState by viewModel.highlightScanState.collectAsState()
    val knownHighlightRuleIds = remember(highlightRules) {
        highlightRules.mapTo(mutableSetOf()) { it.id }
    }
    val activeHighlightPalette = ReaderHighlightPalette
    val renderedNotes = remember(notes, activeHighlightPalette) {
        notes.map { note -> note.copy(color = resolveReaderHighlightColor(note.color)) }
    }
    var currentRulePreviewNotes by remember(
        bookId,
        uiState.currentChapterIndex,
        uiState.contentRevision,
        highlightRules,
        activeHighlightPalette
    ) {
        mutableStateOf(emptyList<com.huangder.lumibooks.domain.model.Note>())
    }
    LaunchedEffect(bookId, uiState.currentChapterIndex, uiState.contentRevision, highlightRules, activeHighlightPalette) {
        currentRulePreviewNotes = viewModel.highlightRulePreviewNotes(uiState.currentChapterIndex, highlightRules)
            .map { note -> note.copy(color = resolveReaderHighlightColor(note.color)) }
    }
    val renderedEpubNotes = remember(renderedNotes, currentRulePreviewNotes, knownHighlightRuleIds) {
        renderedNotes.filterNot { note ->
            note.isGeneratedByHighlightRule && note.sourceRuleId in knownHighlightRuleIds
        } + currentRulePreviewNotes
    }
    val storedBookmarks by viewModel.bookmarks.collectAsState()
    val bookmarks = remember(storedBookmarks, uiState.renderMode) {
        storedBookmarks.map { bookmark ->
            com.huangder.lumibooks.util.epub.EpubComicPosition.decode(bookmark.locatorJson)?.let { position ->
                bookmark.copy(chapterIndex = position.chapterIndex, position = 0f,
                    locatorJson = epubTextLocator(position, uiState.renderMode))
            } ?: bookmark
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingExportThemeId by remember { mutableStateOf<String?>(null) }
    val exportThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val suiteId = pendingExportThemeId
        pendingExportThemeId = null
        if (uri != null && suiteId != null) {
            viewModel.exportReaderThemeBundle(uri, suiteId) { result ->
                Toast.makeText(
                    context,
                    if (result.isSuccess) R.string.theme_bundle_export_success else R.string.theme_bundle_export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val importThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importReaderThemeBundle(it) { result ->
                val message = result.fold(
                    onSuccess = { report ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_bundle_import_success, report.importedCount),
                            Toast.LENGTH_LONG
                        ).show()
                        if (report.missingBackgroundImages.isNotEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.theme_bundle_missing_background_fallback,
                                    report.missingBackgroundImages.joinToString("、")
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        null
                    },
                    onFailure = { error -> context.getString(R.string.theme_bundle_import_failed, error.message ?: "invalid file") }
                )
                message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
        }
    }
    LaunchedEffect(nightDisplay, uiState.activeThemeSuiteIdFor(uiState.readerLayoutTarget())) {
        viewModel.applyReaderThemeMode(nightDisplay)
    }
    val adaptiveWindowInfo = currentAdaptiveWindowInfo()
    val useWideLayout = adaptiveWindowInfo.isMediumWidthOrLarger
    val useCompactLayout = !useWideLayout
    val density = LocalDensity.current

    // ReadView 引用
    val readViewRef = remember { mutableStateOf<ReadView?>(null) }
    // Canvas 引擎注释气泡 + 根布局在窗口中的位置（用于把窗口坐标换算为气泡偏移）
    var footnoteBubble by remember { mutableStateOf<ReaderFootnoteBubble?>(null) }
    // 实际渲染的气泡：目标为 null 时先播放退出动画再移除
    var renderedFootnote by remember { mutableStateOf<ReaderFootnoteBubble?>(null) }
    val footnoteProgress = remember { Animatable(0f) }
    val readerRootWindowPosition = remember { mutableStateOf(Offset.Zero) }
    val readerRootSize = remember { mutableStateOf(IntSize.Zero) }
    val continuousScrollRequests = remember(bookId) {
        MutableStateFlow<ContinuousScrollRequest?>(null)
    }
    val continuousSelectionController = remember { ContinuousSelectionController() }
    val isEpub = uiState.book?.format?.name == "EPUB"
    val supportsBookLayout = isEpub || uiState.book?.format?.name == "MOBI"
    val isBookLayout = supportsBookLayout && uiState.renderMode == EpubRenderMode.BOOK_LAYOUT
    val isVerticalWriting = uiState.readerWritingMode == ReaderWritingMode.VERTICAL_RL &&
        uiState.useNewEngine && !isBookLayout
    // 只判断“是否允许双页”（设备/设置/模式），实际是否启用由 ReadView 按自身宽高（横屏）决定
    val twoPageSpreadEligible = uiState.twoPageSpreadEnabled && useWideLayout &&
        uiState.useNewEngine && !isBookLayout && !eInkMode &&
        uiState.readerWritingMode == ReaderWritingMode.HORIZONTAL &&
        basePageTransition !in setOf("continuous", "scroll")
    val isBookLayoutContinuousScroll = isBookLayout &&
        uiState.readerWritingMode.usesContinuousScroll(basePageTransition, eInkMode)
    val usesTransactionalEpubNavigation = isBookLayout
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
    val lineGuideAvailable = uiState.book?.format?.name in setOf("TXT", "EPUB", "MOBI") &&
        uiState.useNewEngine && !isBookLayout && !isVerticalWriting
    val effectiveLineGuide = lineGuideAvailable && uiState.lineGuideEnabled

    val currentBookmarkCharacterOffset = when {
        isBookLayout -> null
        isContinuousScrollMode -> 0
        else -> readViewRef.value?.getCurrentPageStartCharacterOffset()
    }
    val currentBookmarkPageKey = ReaderBookmarkPageKey(
        chapterIndex = uiState.currentChapterIndex,
        pageIndex = uiState.currentPageIndex,
        characterOffset = currentBookmarkCharacterOffset
    )
    val repositoryCurrentPageBookmarked = bookmarks.any { bookmark ->
        bookmark.chapterIndex == currentBookmarkPageKey.chapterIndex &&
            ((currentBookmarkPageKey.characterOffset != null &&
                bookmark.characterOffset == currentBookmarkPageKey.characterOffset) ||
                (bookmark.characterOffset == null &&
                    bookmark.position.toInt() == currentBookmarkPageKey.pageIndex))
    }
    var optimisticBookmark by remember(bookId) {
        mutableStateOf<Pair<ReaderBookmarkPageKey, Boolean>?>(null)
    }
    val isCurrentPageBookmarked = optimisticBookmark
        ?.takeIf { it.first == currentBookmarkPageKey }
        ?.second
        ?: repositoryCurrentPageBookmarked
    var pendingBookmarkRemarkId by remember(bookId) { mutableStateOf<String?>(null) }
    var bookmarkRemarkText by remember(bookId) { mutableStateOf("") }
    var bookmarkRemarkTags by remember(bookId) { mutableStateOf<List<String>>(emptyList()) }
    val annotationTagState = rememberReaderAnnotationTagState(bookId)
    val availableAnnotationTags by viewModel.annotationTags.collectAsState(initial = emptyList())

    LaunchedEffect(
        currentBookmarkPageKey,
        repositoryCurrentPageBookmarked,
        optimisticBookmark
    ) {
        val optimistic = optimisticBookmark ?: return@LaunchedEffect
        if (optimistic.first != currentBookmarkPageKey ||
            optimistic.second == repositoryCurrentPageBookmarked
        ) {
            optimisticBookmark = null
        }
    }

    val toggleBookmarkForCurrentPage: (showToast: Boolean) -> Unit = { showToast ->
        toggleReaderBookmark(
            uiState, bookmarks, readViewRef.value, isBookLayout, isContinuousScrollMode,
            viewModel, context, showToast
        ) { addedId ->
            if (addedId != null) annotationTagState.quickTarget = addedId to true
            if (uiState.bookmarkRemarkPromptEnabled) {
                pendingBookmarkRemarkId = addedId
                bookmarkRemarkText = ""
                bookmarkRemarkTags = emptyList()
            }
        }
    }
    var bookmarkPullActive by remember(bookId) { mutableStateOf(false) }
    var bookmarkPullStartedBookmarked by remember(bookId) { mutableStateOf(false) }
    var bookmarkPullDistancePx by remember(bookId) { mutableFloatStateOf(0f) }
    var bookmarkPullSettleMode by remember(bookId) {
        mutableStateOf<BookmarkPullSettleMode?>(null)
    }
    var bookmarkSettleAnimation by remember(bookId) {
        mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
    }
    var bookmarkExitAlphaAnimation by remember(bookId) {
        mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
    }
    var bookmarkExitOffsetAnimation by remember(bookId) {
        mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
    }
    var bookmarkSettleJob by remember(bookId) { mutableStateOf<Job?>(null) }
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
    var epubNavigationRequest by remember(bookId) {
        mutableStateOf<EpubNavigationRequest?>(null)
    }
    var epubNavigationStage by remember(bookId) {
        mutableStateOf(EpubNavigationStage.STARTING)
    }
    var failedEpubNavigation by remember(bookId) {
        mutableStateOf<Pair<EpubNavigationRequest, EpubNavigationFailureReason>?>(null)
    }
    var epubNavigationOperationId by remember(bookId) { mutableLongStateOf(0L) }
    var epubRenderFailure by remember(bookId) { mutableStateOf<EpubRenderFailure?>(null) }
    var epubRetryToken by remember(bookId) { mutableIntStateOf(0) }
    var epubNavigationRetryCount by remember(bookId) { mutableIntStateOf(0) }
    var visibleEpubNavigationId by remember(bookId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(epubNavigationRequest?.operationId) {
        visibleEpubNavigationId = null
        val operationId = epubNavigationRequest?.operationId ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250L)
        visibleEpubNavigationId = operationId
    }
    var epubPendingFragment by remember(bookId) { mutableStateOf<String?>(null) }
    var pendingExternalLink by remember(bookId) { mutableStateOf<String?>(null) }
    var epubSelectionClearToken by remember(bookId) { mutableIntStateOf(0) }
    var readerImagePreview by remember(bookId) { mutableStateOf<EpubImagePreviewRequest?>(null) }
    val readerImagePreviewProgress = remember(bookId) { Animatable(0f) }
    var readerImagePreviewJob by remember(bookId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val submitEpubNavigation: (
        EpubNavigationOrigin,
        Int,
        EpubNavigationDestination,
        Boolean
    ) -> Unit = { origin, targetChapter, destination, showLoadingPage ->
        epubNavigationOperationId += 1L
        if (origin != EpubNavigationOrigin.SEARCH) {
            epubSearchRequest = null
        }
        epubPendingFragment = null
        epubLocatorRequest = null
        epubPageRequest = null
        failedEpubNavigation = null
        epubRenderFailure = null
        epubNavigationRetryCount = 0
        epubNavigationStage = EpubNavigationStage.STARTING
        epubNavigationRequest = EpubNavigationRequest(
            operationId = epubNavigationOperationId,
            origin = origin,
            sourceChapterIndex = uiState.currentChapterIndex,
            sourcePageIndex = uiState.currentPageIndex,
            targetChapterIndex = targetChapter,
            destination = destination,
            showLoadingPage = showLoadingPage &&
                !(isBookLayoutContinuousScroll && origin == EpubNavigationOrigin.CHAPTER_CONTROL)
        )
    }
    val navigateEpub: (
        EpubNavigationOrigin,
        Int,
        EpubNavigationDestination,
        Boolean
    ) -> Unit = { origin, targetChapter, destination, showLoadingPage ->
        if (usesTransactionalEpubNavigation) {
            submitEpubNavigation(origin, targetChapter, destination, showLoadingPage)
        } else {
            if (origin != EpubNavigationOrigin.SEARCH) {
                epubSearchRequest = null
            }
            when (destination) {
                EpubNavigationDestination.ChapterStart -> {
                    epubPendingFragment = null
                    epubLocatorRequest = null
                    epubPageRequestToken++
                    epubPageRequest = EpubPageRequest(
                        epubPageRequestToken,
                        targetChapter,
                        0
                    )
                }
                is EpubNavigationDestination.Fragment -> {
                    epubPendingFragment = destination.value
                    epubLocatorRequest = null
                    epubPageRequest = null
                }
                is EpubNavigationDestination.Locator -> {
                    epubPendingFragment = null
                    epubPageRequest = null
                    epubLocatorRequestToken++
                    epubLocatorRequest = EpubLocatorRequest(
                        epubLocatorRequestToken,
                        targetChapter,
                        destination.json
                    )
                }
                is EpubNavigationDestination.Page -> {
                    epubPendingFragment = null
                    epubLocatorRequest = null
                    epubPageRequestToken++
                    epubPageRequest = EpubPageRequest(
                        epubPageRequestToken,
                        targetChapter,
                        destination.index,
                        destination.chapterFraction
                    )
                }
            }
            if (targetChapter != uiState.currentChapterIndex) {
                viewModel.setChapter(targetChapter)
            }
        }
    }
    val latestNavigateEpub = rememberUpdatedState(navigateEpub)
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
            else -> readViewRef.value?.clearActiveTextSelection()
        }
    }
    fun jumpToContinuousChapter(chapterIndex: Int, chapterFraction: Float = 0f) {
        val target = chapterIndex.coerceIn(0, (uiState.chapterCount - 1).coerceAtLeast(0))
        viewModel.setChapter(target)
        continuousScrollRequests.tryEmit(
            ContinuousScrollRequest(target, chapterFraction.coerceIn(0f, 0.9999f))
        )
    }

    LaunchedEffect(isContinuousScrollMode) {
        if (isContinuousScrollMode) {
            // AndroidView is detached in this mode. Do not route later jumps to its stale instance.
            readViewRef.value = null
        }
    }

    val startTtsFromCurrentPage: (Boolean) -> Unit = { restoreSavedPosition ->
        if (isBookLayout) {
            val webProvider = epubPageTextProvider
            if (webProvider == null) {
                Toast.makeText(context, R.string.tts_page_not_ready, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.startBookLayoutTts(webProvider, restoreSavedPosition)
            }
        } else {
            viewModel.startTts(restoreSavedPosition)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 通知权限被拒绝时，Android 仍允许前台媒体播放，只是不展示普通通知。
        startTtsFromCurrentPage(true)
    }
    val requestTtsStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }.onFailure {
                startTtsFromCurrentPage(true)
            }
        } else {
            startTtsFromCurrentPage(true)
        }
    }

    val latestTtsUiState = rememberUpdatedState(uiState)
    val latestTtsBookLayout = rememberUpdatedState(isBookLayout)
    val latestTtsContinuousScroll = rememberUpdatedState(isContinuousScrollMode)
    val latestTtsEInkMode = rememberUpdatedState(eInkMode)
    var lastHandledTtsPageRequestId by remember(bookId) { mutableLongStateOf(0L) }
    var lastHandledTtsSessionId by remember(bookId) { mutableLongStateOf(0L) }
    LaunchedEffect(bookId) {
        viewModel.ttsPageTurnRequests.collect { request ->
            val currentUiState = latestTtsUiState.value
            if (request.bookId != bookId ||
                !viewModel.isTtsPageTurnRequestActive(request)
            ) return@collect
            if (request.sessionId < lastHandledTtsSessionId ||
                (request.sessionId == lastHandledTtsSessionId &&
                    request.requestId <= lastHandledTtsPageRequestId)
            ) return@collect
            lastHandledTtsSessionId = request.sessionId
            lastHandledTtsPageRequestId = request.requestId
            if (latestTtsBookLayout.value) {
                if (request.location.chapterIndex == currentUiState.currentChapterIndex &&
                    request.location.pageIndex == currentUiState.currentPageIndex
                ) {
                    viewModel.acknowledgeTtsPageTurnRequest(request)
                    return@collect
                }
                epubSearchRequest = null
                epubLocatorRequest = null
                epubPageRequest = null
                latestNavigateEpub.value(
                    EpubNavigationOrigin.TTS,
                    request.location.chapterIndex,
                    EpubNavigationDestination.Page(request.location.pageIndex),
                    false
                )
                return@collect
            }
            if (latestTtsContinuousScroll.value) {
                viewModel.ttsPageFractionForContinuousScroll(
                    request.location.chapterIndex,
                    request.location.pageIndex
                )?.let { targetFraction ->
                    continuousScrollRequests.emit(
                        ContinuousScrollRequest(
                            chapterIndex = request.location.chapterIndex,
                            chapterFraction = targetFraction,
                            origin = TtsPageChangeOrigin.TTS_FOLLOW
                        )
                    )
                }
                return@collect
            }
            var readView = readViewRef.value
            while (readView == null && viewModel.isTtsPageTurnRequestActive(request)) {
                kotlinx.coroutines.delay(16L)
                readView = readViewRef.value
            }
            val activeReadView = readView ?: return@collect
            if (!viewModel.isTtsPageTurnRequestActive(request)) return@collect
            val current = activeReadView.getCurrentLocation()
            val target = request.location.chapterIndex to request.location.pageIndex
            if (current == target) {
                viewModel.acknowledgeTtsPageTurnRequest(request)
                return@collect
            }

            if (latestTtsEInkMode.value) {
                activeReadView.jumpToChapter(
                    target.first,
                    target.second,
                    TtsPageChangeOrigin.TTS_FOLLOW
                )
            } else {
                val movedWithAnimation = when (target) {
                    activeReadView.getNextPageLocation() ->
                        activeReadView.turnToNextPage(TtsPageChangeOrigin.TTS_FOLLOW)
                    activeReadView.getPrevPageLocation() ->
                        activeReadView.turnToPreviousPage(TtsPageChangeOrigin.TTS_FOLLOW)
                    else -> false
                }
                if (!movedWithAnimation) {
                    activeReadView.jumpToChapter(
                        target.first,
                        target.second,
                        TtsPageChangeOrigin.TTS_FOLLOW
                    )
                }
            }
        }
    }

    // MainActivity 引用（用于注册 ActionMode 拦截回调）
    val activity = context as? MainActivity

    // 手机阅读页始终保持竖屏；离开阅读页后恢复进入前的方向策略。
    DisposableEffect(activity, useCompactLayout) {
        if (activity == null || !useCompactLayout) {
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

    val requestWriteSettingsPermission = rememberReaderWindowLifecycle(uiState, viewModel, readViewRef)

    var firstContentReported by remember(bookId) { mutableStateOf(false) }
    // pageReady is emitted after the renderer has populated its current page. Waiting for the
    // following frame makes the navigation transition follow visible content instead of parsing.
    LaunchedEffect(uiState.pageReady, uiState.isLoading, uiState.error, epubRenderFailure, bookId) {
        if (!firstContentReported && (epubRenderFailure != null || (!uiState.isLoading &&
            (uiState.pageReady || uiState.error != null)))
        ) {
            ReaderOpenPerformance.updateState(
                bookId = bookId,
                phase = if (uiState.error == null) "awaiting_first_frame" else "error_surface",
                isLoading = uiState.isLoading,
                pageReady = uiState.pageReady,
                chapterIndex = uiState.currentChapterIndex,
                pageIndex = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                details = mapOf("hasError" to (uiState.error != null)),
                event = "first_frame_scheduled"
            )
            ReaderOpenPerformance.beginStage(bookId, ReaderOpenStage.FIRST_FRAME)
            withFrameNanos { }
            firstContentReported = true
            onFirstContentDrawn()
            if (eInkMode) onInteractive()
        }
    }

    LaunchedEffect(ttsState.errorMessage) {
        val message = ttsState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearTtsError()
    }

    // 恢复阅读进度：精确字符锚点优先，旧记录再按章节比例换算页码。
    LaunchedEffect(
        uiState.pageReady,
        uiState.pendingPageFraction,
        uiState.pendingReaderPosition,
        uiState.currentChapterIndex,
        uiState.totalPages,
        uiState.contentRevision,
        readViewRef.value,
        isContinuousScrollMode
    ) {
        // Continuous scroll owns pendingPageFraction while crossing the mode boundary. Letting the
        // detached paged reader consume it first resets single-chapter TXT books to their first page.
        if (isContinuousScrollMode || !uiState.pageReady) {
            return@LaunchedEffect
        }
        val readView = readViewRef.value ?: return@LaunchedEffect
        val readerPosition = uiState.pendingReaderPosition
        if (readerPosition != null) {
            if (readerPosition.chapterIndex !in 0 until uiState.chapterCount) {
                viewModel.clearPendingPageFraction()
                return@LaunchedEffect
            }
            val characterOffset = readerPosition.characterOffset
            if (characterOffset != null) {
                readView.jumpToCharacter(readerPosition.chapterIndex, characterOffset)
            } else {
                val totalPages = readView.getChapterPageCount(readerPosition.chapterIndex)
                if (totalPages > 0) {
                    readView.jumpToChapter(
                        readerPosition.chapterIndex,
                        restoredPagedPageIndex(
                            readerPosition.chapterFraction,
                            totalPages,
                            ReaderPageFractionSemantics.START
                        )
                    )
                }
            }
            return@LaunchedEffect
        }
        if (uiState.pendingPageFraction <= 0f) return@LaunchedEffect
        val totalPages = readView.getChapterPageCount(uiState.currentChapterIndex)
        if (totalPages > 0) {
            val targetPage = restoredPagedPageIndex(
                uiState.pendingPageFraction,
                totalPages,
                uiState.pendingPageFractionSemantics
            )
            if (targetPage > 0) {
                readView.jumpToChapter(uiState.currentChapterIndex, targetPage)
            }
            viewModel.clearPendingPageFraction()
        }
    }

    var showNotesList by remember { mutableStateOf(false) }
    var linkReturnLocation by remember(bookId) { mutableStateOf<ReaderLinkLocation?>(null) }
    var lastVisibleReaderLocation by remember(bookId) {
        mutableStateOf(ReaderLinkLocation(uiState.currentChapterIndex, uiState.currentPageIndex))
    }
    var catalogDragReturnLocation by remember(bookId) {
        mutableStateOf<ReaderLinkLocation?>(null)
    }
    var linkReturnToken by remember(bookId) { mutableStateOf(0) }
    var linkNavigationJob by remember(bookId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val ttsSessionActive = ttsState.activeBookId == uiState.book?.id &&
        ttsState.playbackState != TtsPlaybackState.IDLE
    fun rememberTtsReturnLocation(chapterIndex: Int, pageIndex: Int) {
        val next = ReaderLinkLocation(chapterIndex, pageIndex)
        val spoken = ttsCurrentPage?.location
        if (ttsSessionActive && spoken != null &&
            (spoken.chapterIndex != next.chapterIndex || spoken.pageIndex != next.pageIndex) &&
            lastVisibleReaderLocation != next && linkReturnLocation == null
        ) {
            // Keep the page the user left visible above the reader while they browse away from
            // the page currently being spoken. The existing return capsule owns the interaction.
            linkReturnLocation = lastVisibleReaderLocation
            linkReturnToken += 1
        }
        lastVisibleReaderLocation = next
    }
    fun clearCancelledEpubNavigation(request: EpubNavigationRequest?) {
        when (request?.origin) {
            EpubNavigationOrigin.SEARCH -> {
                epubSearchRequest = null
                linkReturnLocation = null
            }
            EpubNavigationOrigin.INTERNAL_LINK,
            EpubNavigationOrigin.PROGRESS -> linkReturnLocation = null
            else -> Unit
        }
        epubNavigationRequest = null
    }
    LaunchedEffect(usesTransactionalEpubNavigation) {
        if (!usesTransactionalEpubNavigation) {
            clearCancelledEpubNavigation(epubNavigationRequest)
            failedEpubNavigation = null
        }
    }

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
    SideEffect {
        readViewRef.value?.setSelectionMenuVisible(selectionState != null && !isSelectionDragging)
    }
    // 选区是否已经拖到别的页面（跨页选择成立）→ 底部提示改成「单击目标结尾」
    var selectionCrossPage by remember(bookId) { mutableStateOf(false) }
    // 每次拖拽结束后自增，触发 SelectionMenuOverlay 重置入场动画
    var menuReappearKey by remember { mutableStateOf(0) }
    // Dictionary app picker: after tapping Dictionary, switch from action chips to PROCESS_TEXT app chips.
    var showDictionaryAppPicker by remember { mutableStateOf(false) }
    var dictionaryLookupText by remember { mutableStateOf("") }
    var localDictionaryQuery by remember(bookId) { mutableStateOf<String?>(null) }
    var epubDictionarySelection by remember(bookId) { mutableStateOf<EpubDictionarySelection?>(null) }
    var dictionaryAppOptions by remember { mutableStateOf<List<DictionaryAppOption>>(emptyList()) }
    var showMenuSettings by remember { mutableStateOf(false) }
    var showReplaceInput by remember { mutableStateOf(false) }
    var replaceSelection by remember { mutableStateOf<ReplaceSelectionInfo?>(null) }
    fun resetSelectionSubmenus() {
        showDictionaryAppPicker = false
        showMenuSettings = false
        dictionaryLookupText = ""
        dictionaryAppOptions = emptyList()
    }
    fun dismissSelectionMenu() {
        selectionState = null
        epubDictionarySelection = null
        isSelectionDragging = false
        resetSelectionSubmenus()
        clearActiveTextSelection()
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
    var showHighlightRules by remember { mutableStateOf(false) }
    var showAdvancedSheet by remember { mutableStateOf(false) }
    var openAdvancedAfterThemeClose by remember { mutableStateOf(false) }
    var showTxtEncodingDialog by remember(bookId) { mutableStateOf(false) }
    var showTxtTocDialog by remember(bookId) { mutableStateOf(false) }

    // 搜索状态
    var showSearch by remember(bookId) { mutableStateOf(false) }
    var showWebSearch by remember(bookId) { mutableStateOf(false) }
    // 请求关闭状态（用于触发退出动画）
    var requestCloseNotesList by remember { mutableStateOf(false) }
    var requestCloseNoteInput by remember { mutableStateOf(false) }
    var requestCloseToc by remember { mutableStateOf(false) }
    var requestCloseTheme by remember { mutableStateOf(false) }
    var requestCloseHighlightRules by remember { mutableStateOf(false) }
    var requestCloseAdvanced by remember { mutableStateOf(false) }
    var requestCloseSearch by remember { mutableStateOf(false) }

    // 处理返回键：触发退出动画，而不是直接关闭
    val isAnySheetOpen = localDictionaryQuery != null || annotationTagState.editingTarget != null || showNotesList || showNoteInput || pendingBookmarkRemarkId != null || showToc || showThemeSheet || showHighlightRules ||
        showAdvancedSheet || showSearch || showWebSearch || showTxtEncodingDialog || showTxtTocDialog || showReplaceInput
    val bookmarkPullSupported = (isBookLayout && !isBookLayoutContinuousScroll) ||
        (uiState.useNewEngine && !renderedContinuousScrollMode && !isVerticalWriting)
    val bookmarkPullEnabled = bookmarkPullSupported &&
        !uiState.isMenuVisible &&
        !isAnySheetOpen &&
        selectionState == null &&
        !isSelectionDragging

    val onBookmarkPullStart: () -> Unit = {
        bookmarkSettleJob?.cancel()
        bookmarkSettleAnimation = null
        bookmarkExitAlphaAnimation = null
        bookmarkExitOffsetAnimation = null
        bookmarkPullSettleMode = null
        bookmarkPullStartedBookmarked = isCurrentPageBookmarked
        bookmarkPullDistancePx = 0f
        bookmarkPullActive = true
    }
    val onBookmarkPullProgress: (Float, Boolean) -> Unit = { distancePx, _ ->
        if (bookmarkPullActive) {
            bookmarkPullDistancePx = distancePx
        }
    }
    val onBookmarkPullFinished: (Boolean) -> Unit = finish@{ commit ->
        if (!bookmarkPullActive) return@finish

        val releasedDistance = bookmarkPullDistancePx
        val startedBookmarked = bookmarkPullStartedBookmarked
        val settleMode = when {
            startedBookmarked && commit -> BookmarkPullSettleMode.REMOVE_COMMIT
            startedBookmarked -> BookmarkPullSettleMode.REMOVE_CANCEL
            commit -> BookmarkPullSettleMode.ADD_COMMIT
            else -> BookmarkPullSettleMode.ADD_CANCEL
        }
        bookmarkPullActive = false
        bookmarkPullSettleMode = settleMode

        if (commit) {
            optimisticBookmark = currentBookmarkPageKey to !startedBookmarked
            toggleBookmarkForCurrentPage(false)
        }

        if (eInkMode || !motionEnabled) {
            bookmarkSettleAnimation = null
            bookmarkExitAlphaAnimation = null
            bookmarkExitOffsetAnimation = null
            bookmarkPullSettleMode = null
            bookmarkPullDistancePx = 0f
            return@finish
        }

        val settleAnimation = Animatable(releasedDistance)
        bookmarkSettleAnimation = settleAnimation
        val exitAlpha = if (settleMode == BookmarkPullSettleMode.REMOVE_COMMIT) {
            Animatable(1f)
        } else {
            null
        }
        val exitOffset = if (settleMode == BookmarkPullSettleMode.REMOVE_COMMIT) {
            Animatable(releasedDistance * BOOKMARK_REMOVE_DRAG_RATIO)
        } else {
            null
        }
        bookmarkExitAlphaAnimation = exitAlpha
        bookmarkExitOffsetAnimation = exitOffset

        bookmarkSettleJob?.cancel()
        bookmarkSettleJob = scope.launch {
            coroutineScope {
                launch {
                    settleAnimation.animateTo(0f, LumiMotion.GestureSpring)
                }
                if (exitAlpha != null && exitOffset != null) {
                    launch {
                        exitAlpha.animateTo(0f, tween(LumiMotion.MenuExitMillis))
                    }
                    launch {
                        exitOffset.animateTo(
                            with(density) { (-64).dp.toPx() },
                            tween(
                                LumiMotion.MenuExitMillis,
                                easing = AppEasing.Accelerate
                            )
                        )
                    }
                }
            }
            if (bookmarkSettleAnimation === settleAnimation) {
                bookmarkSettleAnimation = null
                bookmarkExitAlphaAnimation = null
                bookmarkExitOffsetAnimation = null
                bookmarkPullSettleMode = null
                bookmarkPullDistancePx = 0f
            }
        }
    }
    val latestBookmarkPullStart = rememberUpdatedState(onBookmarkPullStart)
    val latestBookmarkPullProgress = rememberUpdatedState(onBookmarkPullProgress)
    val latestBookmarkPullFinished = rememberUpdatedState(onBookmarkPullFinished)
    val bookmarkContentOffsetPx = if (bookmarkPullActive) {
        bookmarkPullDistancePx
    } else {
        bookmarkSettleAnimation?.value ?: 0f
    }

    LaunchedEffect(currentBookmarkPageKey, bookmarkPullEnabled) {
        if (!bookmarkPullEnabled || bookmarkPullSettleMode != null || bookmarkPullActive) {
            bookmarkSettleJob?.cancel()
            bookmarkSettleAnimation = null
            bookmarkExitAlphaAnimation = null
            bookmarkExitOffsetAnimation = null
            bookmarkPullSettleMode = null
            bookmarkPullActive = false
            bookmarkPullDistancePx = 0f
        }
    }
    var exitRequested by remember(bookId) { mutableStateOf(false) }
    var epubReleaseHandler by remember(bookId) { mutableStateOf<((() -> Unit) -> Unit)?>(null) }
    val exitReader: () -> Unit = {
        if (!exitRequested) {
            exitRequested = true
            viewModel.stopTts()
            val release = epubReleaseHandler.takeIf { isBookLayout }
            if (release != null) release(onNavigateBack) else onNavigateBack()
        }
    }
    ConfigurableBackHandler(
        enabled = epubNavigationRequest != null,
        onBack = { clearCancelledEpubNavigation(epubNavigationRequest) }
    )
    ConfigurableBackHandler(
        enabled = !isAnySheetOpen && linkReturnLocation == null &&
            epubNavigationRequest == null,
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
        epubNavigationRequest == null &&
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
                epubSearchRequest = null
                epubLocatorRequest = null
                epubPageRequest = null
                navigateEpub(
                    EpubNavigationOrigin.RETURN_TO_SOURCE,
                    source.chapterIndex,
                    EpubNavigationDestination.Page(source.pageIndex),
                    true
                )
            } else if (isContinuousScrollMode) {
                jumpToContinuousChapter(
                    source.chapterIndex,
                    source.chapterFraction ?: 0f
                )
            } else {
                readViewRef.value?.jumpToChapter(source.chapterIndex, source.pageIndex)
            }
        }
        Unit
    }
    val returnToTtsProgress = {
        val target = ttsCurrentPage?.location
        if (target != null) {
            linkReturnLocation = null
            when {
                isContinuousScrollMode -> {
                    viewModel.ttsPageFractionForContinuousScroll(
                        target.chapterIndex,
                        target.pageIndex
                    )?.let { fraction ->
                        jumpToContinuousChapter(target.chapterIndex, fraction)
                    }
                }
                isBookLayout -> {
                    navigateEpub(
                        EpubNavigationOrigin.RETURN_TO_SOURCE,
                        target.chapterIndex,
                        EpubNavigationDestination.Page(target.pageIndex),
                        true
                    )
                }
                else -> {
                    readViewRef.value?.jumpToChapter(target.chapterIndex, target.pageIndex)
                        ?: viewModel.setChapter(target.chapterIndex)
                }
            }
        }
    }
    ConfigurableBackHandler(
        enabled = !isAnySheetOpen && linkReturnLocation != null &&
            epubNavigationRequest == null
    ) {
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
    val selectedImageBaseBackground = uiState.customReaderBackgrounds.firstOrNull {
        it.selectionKey == uiState.readerBackgroundColorSelection &&
            it.type == ReaderBackgroundType.COLOR
    }
    val imageBaseColor = when {
        selectedImageBaseBackground != null -> runCatching {
            android.graphics.Color.parseColor(selectedImageBaseBackground.value)
        }.getOrDefault(0xFFFBFBFC.toInt())
        uiState.readerBackgroundColorSelection == "night" -> 0xFF1a1a1a.toInt()
        uiState.readerBackgroundColorSelection == "sepia" -> 0xFFf5e6d3.toInt()
        uiState.readerBackgroundColorSelection == "green" -> 0xFFe8f5e9.toInt()
        else -> 0xFFFBFBFC.toInt()
    }
    val readerBackgroundColorInt = when {
        publisherPaintSuiteActive -> publisherFallbackPaperColor
        selectedCustomBackground?.type == ReaderBackgroundType.IMAGE -> imageBaseColor
        selectedCustomBackground?.type == ReaderBackgroundType.COLOR -> {
            val base = runCatching { android.graphics.Color.parseColor(selectedCustomBackground.value) }
                .getOrDefault(0xFFFBFBFC.toInt())
            if (nightDisplay) darkenReaderSolidColor(base) else base
        }
        effectiveReaderBackgroundSelection in ReaderThemeSuites.THEME_IDS ->
            readerPresetBackgroundColor(effectiveReaderBackgroundSelection, nightDisplay)
        else -> 0xFFFBFBFC.toInt()
    }
    val readerBackgroundImageSource = selectedCustomBackground
        ?.resolveImageSource(uiState.readerBackgroundImageBlurDp)
    val readerBackgroundImagePath =
        if (publisherPaintSuiteActive) null else readerBackgroundImageSource?.path
    val readerBackgroundImageBlurDp = readerBackgroundImageSource?.runtimeBlurDp ?: 0f
    val customBackgroundThemeColorInt = when {
        publisherPaintSuiteActive -> publisherFallbackPaperColor
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
        effectiveReaderBackgroundSelection in ReaderThemeSuites.THEME_IDS ->
            readerPresetTextColor(effectiveReaderBackgroundSelection, nightDisplay)
        else -> 0xFF333333.toInt()
    }
    val readerTextColorInt = effectiveReaderTextColor ?: automaticReaderTextColorInt

    // The advanced settings preview follows the mode selected inside the sheet,
    // which can differ from the mode currently applied to the reading page.
    val editorSelectedCustomBackground = if (eInkMode) null else uiState.customReaderBackgrounds.firstOrNull {
        it.selectionKey == editorThemeSettings.backgroundSelection
    }
    val editorImageBaseBackground = uiState.customReaderBackgrounds.firstOrNull {
        it.selectionKey == editorThemeSettings.backgroundColorSelection &&
            it.type == ReaderBackgroundType.COLOR
    }
    val editorImageBaseColor = when {
        editorImageBaseBackground != null -> runCatching {
            android.graphics.Color.parseColor(editorImageBaseBackground.value)
        }.getOrDefault(0xFFFBFBFC.toInt())
        editorThemeSettings.backgroundColorSelection == "night" -> 0xFF1a1a1a.toInt()
        editorThemeSettings.backgroundColorSelection == "sepia" -> 0xFFf5e6d3.toInt()
        editorThemeSettings.backgroundColorSelection == "green" -> 0xFFe8f5e9.toInt()
        else -> 0xFFFBFBFC.toInt()
    }
    val editorBackgroundColorInt = when {
        publisherPaintSuiteActive -> publisherFallbackPaperColor
        editorSelectedCustomBackground?.type == ReaderBackgroundType.IMAGE -> editorImageBaseColor
        editorSelectedCustomBackground?.type == ReaderBackgroundType.COLOR -> {
            val base = runCatching {
                android.graphics.Color.parseColor(editorSelectedCustomBackground.value)
            }.getOrDefault(0xFFFBFBFC.toInt())
            if (uiState.themeEditingDark) darkenReaderSolidColor(base) else base
        }
        editorThemeSettings.backgroundSelection in ReaderThemeSuites.THEME_IDS ->
            readerPresetBackgroundColor(editorThemeSettings.backgroundSelection, uiState.themeEditingDark)
        else -> 0xFFFBFBFC.toInt()
    }
    val editorBackgroundImageSource = editorSelectedCustomBackground
        ?.resolveImageSource(editorThemeSettings.backgroundImageBlurDp)
    val editorBackgroundImagePath =
        if (publisherPaintSuiteActive) null else editorBackgroundImageSource?.path
    val editorCustomBackgroundThemeColorInt = when {
        publisherPaintSuiteActive -> publisherFallbackPaperColor
        uiState.themeEditingDark && editorSelectedCustomBackground?.type == ReaderBackgroundType.COLOR ->
            editorBackgroundColorInt
        editorSelectedCustomBackground != null ->
            editorSelectedCustomBackground.dominantColor ?: editorBackgroundColorInt
        else -> editorBackgroundColorInt
    }
    val editorAutomaticTextColorInt = when {
        editorSelectedCustomBackground != null -> {
            if (ColorUtils.calculateLuminance(editorCustomBackgroundThemeColorInt) < 0.42) {
                0xFFE8E8EA.toInt()
            } else {
                0xFF333333.toInt()
            }
        }
        editorThemeSettings.backgroundSelection in ReaderThemeSuites.THEME_IDS ->
            readerPresetTextColor(editorThemeSettings.backgroundSelection, uiState.themeEditingDark)
        else -> 0xFF333333.toInt()
    }
    val menuBgColorInt = customBackgroundThemeColorInt
    val menuBgColor = Color(menuBgColorInt)
    val menuContentColor = if (ColorUtils.calculateLuminance(menuBgColorInt) < 0.4) {
        Color.White
    } else {
        Color(0xFF1C1C1E)
    }
    // 胶囊按钮背景色：基于阅读主题渲染效果而非系统深色模式
    // 自定义背景（尤其背景图）时，胶囊取背景主题色，让菜单和画面同一色调；
    // 亮度按阅读底色明暗调整，保证胶囊上的文字对比度。
    val capsuleBgColor = if (selectedCustomBackground != null) {
        val base = customBackgroundThemeColorInt
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base, hsl)
        if (hsl[2] < 0.35f) {
            hsl[2] = (hsl[2] + 0.22f).coerceAtMost(0.5f)
        } else {
            hsl[2] = (hsl[2] - 0.12f).coerceAtLeast(0.42f)
        }
        hsl[1] = (hsl[1] * 0.85f).coerceIn(0f, 1f)
        Color(ColorUtils.HSLToColor(hsl))
    } else {
        when (renderingTheme) {
            "night" -> Color(0xFF3A3A3C)
            "sepia_dark" -> Color(0xFF3A312A)
            "green_dark" -> Color(0xFF1E3527)
            "sepia" -> Color(0xFFE8D5C4)
            "green" -> Color(0xFFC8E6C9)
            else -> Color(0xFFEEEEEE)
        }
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
    val coverEdgeColor by produceState<Int?>(
        initialValue = null,
        uiState.book?.coverPath
    ) {
        value = withContext(Dispatchers.IO) {
            extractCoverEdgeColor(uiState.book?.coverPath)
        }
    }
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
    val preparedEpubFont by produceState<PreparedEpubReaderFont?>(
        initialValue = null,
        isBookLayout,
        uiState.fontType,
        uiState.customFontPath
    ) {
        value = if (isBookLayout) {
            PreparedEpubReaderFont(uiState.fontType, uiState.customFontPath,
                prepareEpubReaderFontPath(context.applicationContext, uiState.fontType, uiState.customFontPath))
        } else {
            null
        }
    }
    val epubFontReady = preparedEpubFont?.let {
        it.fontType == uiState.fontType && it.customFontPath == uiState.customFontPath
    } == true
    val epubFontFilePath = preparedEpubFont?.resolvedPath
    var epubInitialFontReady by remember(bookId, isBookLayout) { mutableStateOf(false) }
    SideEffect { if (epubFontReady) epubInitialFontReady = true }
    val continuousTypeface = remember(
        isBookLayout,
        uiState.fontType,
        uiState.customFontPath,
        uiState.bodyFontWeight
    ) {
        if (isBookLayout) com.huangder.lumibooks.ui.reader.engine.ResolvedReaderTypeface(
            android.graphics.Typeface.DEFAULT, false) else resolveReaderTypeface(
            context = context,
            fontType = uiState.fontType,
            customFontPath = uiState.customFontPath,
            weight = uiState.bodyFontWeight
        )
    }
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
    val readerGlassBackdrop = rememberLayerBackdrop(onDraw = {
        // The lens must sample the paper as well as the transparent reader content.
        drawRect(composeBgColor)
        drawContent()
    })
    val activeReaderGlassBackdrop = readerGlassBackdrop.takeIf { isLiquidGlass && !isBookLayout }
    // NavHost keeps the outgoing reader composed during pop animations; it must not
    // overwrite the home screen's system-bar style after navigation has switched.
    if (readerActive) {
        ReaderSystemBarStyle(
            backgroundColor = composeBgColor,
            useDarkIcons = ColorUtils.calculateLuminance(customBackgroundThemeColorInt) >= 0.42
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiquidGlassContrastEnabled provides (uiState.readerButtonContrastEnabled && !eInkMode)
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(composeBgColor)
            .semantics { testTagsAsResourceId = true }
            .testTag(
                if (firstContentReported) READER_CONTENT_READY_TAG else READER_CONTENT_LOADING_TAG
            )
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
                .graphicsLayer { translationY = bookmarkContentOffsetPx }
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
                // Keep the reader capture layer mounted for the whole Canvas/TXT session.
                // Mounting it only after a dialog opens leaves the first glass frame with
                // the app-level theme backdrop, so encoding and TXT TOC dialogs can sample
                // the hidden theme instead of the page that is actually being read.
                .then(
                    activeReaderGlassBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
                )
        ) {
            // ── 新 Canvas 引擎（TXT/EPUB） ──
            val activeEpubSession = epubSession
            if (isBookLayout) {
                // Mount with the real font configuration so the first page is not laid out twice.
                if (activeEpubSession != null && (epubFontReady || epubInitialFontReady)) {
                androidx.compose.runtime.key(epubRetryToken) {
                EpubWebViewReader(
                    session = activeEpubSession,
                    onReleaseHandlerReady = { epubReleaseHandler = it },
                    imageAdjustments = uiState.imageAdjustments.forDisplay(eInkMode),
                    chapterIndex = uiState.currentChapterIndex,
                    fontSizeSp = uiState.fontSize,
                    letterSpacingDp = uiState.letterSpacing,
                    fontType = preparedEpubFont?.fontType ?: uiState.fontType,
                    fontFilePath = epubFontFilePath,
                    customFonts = uiState.customFonts,
                    bodyFontWeight = uiState.bodyFontWeight,
                    textColorOverride = effectiveReaderTextColor,
                    theme = renderingTheme,
                    coverBackgroundColor = coverEdgeColor,
                    readerBackgroundColorOverride = if (publisherPaintSuiteActive) {
                        // 只作为翻页快照/透明页的兜底纸色；文档层不会铺这个底色。
                        publisherFallbackPaperColor
                    } else if (selectedCustomBackground != null) {
                        readerBackgroundColorInt
                    } else {
                        null
                    },
                    readerBackgroundImagePath = readerBackgroundImagePath,
                    readerBackgroundImageOpacity = uiState.readerBackgroundImageOpacity,
                    readerBackgroundImageBlurDp = readerBackgroundImageBlurDp,
                    autoTextColor = if (publisherPaintSuiteActive) {
                        null
                    } else if (selectedCustomBackground != null) {
                        automaticReaderTextColorInt
                    } else {
                        null
                    },
                    textAlignment = uiState.textAlignment,
                    preservePublisherBackground = effectivePreserveEpubBackground,
                    imagePageCrop = uiState.pageImageCrop,
                    publisherPaintOnly = publisherPaintSuiteActive,
                    bionicReadingEnabled = effectiveBionicReadingEnabled,
                    chineseMode = uiState.chineseMode,
                    restoreLocatorJson = uiState.epubLocatorJson,
                    restoreProgression = uiState.pendingPageFraction,
                    restoreProgressionInclusive =
                        uiState.pendingPageFractionSemantics ==
                            ReaderPageFractionSemantics.INCLUSIVE_PAGE_END,
                    initialFragment = epubPendingFragment,
                    continuousScroll = isBookLayoutContinuousScroll,
                    pageTransition = if (isBookLayoutContinuousScroll) "none" else effectivePageTransition,
                    pageTransitionDurationMs = uiState.pageAnimationSettings.durationFor(
                        effectivePageTransition
                    ),
                    marginTopDp = uiState.marginTopDp,
                    marginRightDp = uiState.marginRightDp,
                    marginBottomDp = uiState.marginBottomDp,
                    marginLeftDp = uiState.marginLeftDp,
                    edgeTapMode = uiState.readerEdgeTapMode,
                    // Keep all notes available; each EPUB WebView routes them by loaded chapter.
                    notes = renderedEpubNotes,
                    ttsCurrentSentence = ttsCurrentSentence,
                    ttsHighlightColor = TtsSentenceHighlightSpan.computeHighlightColor(
                        readerBackgroundColorInt
                    ),
                    searchRequest = epubSearchRequest,
                    locatorRequest = epubLocatorRequest,
                    pageRequest = epubPageRequest,
                    navigationRequest = epubNavigationRequest.takeIf {
                        usesTransactionalEpubNavigation
                    },
                    bookFormat = uiState.book?.format?.name ?: "EPUB",
                    selectionClearToken = epubSelectionClearToken,
                    dictionarySelection = epubDictionarySelection,
                    selectionMenuVisible = selectionState != null && !isSelectionDragging,
                    bookmarkPullEnabled = bookmarkPullEnabled,
                    onPageTextProviderReady = { epubPageTextProvider = it },
                    onPageTurnHandlerReady = { epubPageTurnHandler = it },
                    onPageChanged = { chapterIndex, pageIndex, pageCount, locatorJson ->
                        if (viewModel.ttsPageChangeOriginFor(chapterIndex, pageIndex) == TtsPageChangeOrigin.USER) {
                            rememberTtsReturnLocation(chapterIndex, pageIndex)
                        } else {
                            lastVisibleReaderLocation = ReaderLinkLocation(chapterIndex, pageIndex)
                        }
                        viewModel.onEpubPageCommitted(
                            chapterIndex,
                            pageIndex,
                            pageCount,
                            locatorJson
                        )
                        epubPageRequest?.let { request ->
                            val expectedPage = request.chapterFraction?.let { fraction ->
                                pageIndexForChapterFraction(fraction, pageCount)
                            } ?: request.pageIndex
                            if (request.chapterIndex == chapterIndex &&
                                expectedPage == pageIndex
                            ) {
                                epubPageRequest = null
                            }
                        }
                        if (epubLocatorRequest?.chapterIndex == chapterIndex) {
                            epubLocatorRequest = null
                        }
                        epubPendingFragment = null
                    },
                    onBookmarkPullStart = { latestBookmarkPullStart.value() },
                    onBookmarkPullProgress = { distancePx, armed ->
                        latestBookmarkPullProgress.value(distancePx, armed)
                    },
                    onBookmarkPullFinished = { commit ->
                        latestBookmarkPullFinished.value(commit)
                    },
                    onCenterTap = {
                        if (selectionState != null) dismissSelectionMenu() else viewModel.toggleMenu()
                    },
                    onSelectionMenuDismiss = {
                        dismissSelectionMenu()
                    },
                    onImagePreviewOpen = viewModel::hideMenu,
                    onChapterTurn = { direction ->
                        epubSearchRequest = null
                        epubLocatorRequest = null
                        epubPageRequest = null
                        run {
                            val targetChapter = uiState.currentChapterIndex + direction
                            if (targetChapter !in 0 until uiState.chapterCount) return@EpubWebViewReader
                            navigateEpub(
                                EpubNavigationOrigin.CHAPTER_CONTROL,
                                targetChapter,
                                EpubNavigationDestination.Page(
                                    if (direction < 0) Int.MAX_VALUE else 0
                                ),
                                !isBookLayoutContinuousScroll
                            )
                        }
                    },
                    onInternalLink = { targetChapter, fragment ->
                        // 书内链接跳转前捕获来源位置，用于左上角“返回到刚才页”按钮
                        val source = ReaderLinkLocation(
                            uiState.currentChapterIndex,
                            uiState.currentPageIndex
                        )
                        epubSearchRequest = null
                        epubLocatorRequest = null
                        epubPageRequest = null
                        linkReturnLocation = source
                        linkReturnToken += 1
                        navigateEpub(
                            EpubNavigationOrigin.INTERNAL_LINK,
                            targetChapter,
                            fragment?.takeIf { it.isNotBlank() }?.let {
                                EpubNavigationDestination.Fragment(it)
                            } ?: EpubNavigationDestination.ChapterStart,
                            true
                        )
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
                                annotationOnly = selection.annotationOnly,
                                startLocatorJson = selection.startLocatorJson,
                                endLocatorJson = selection.endLocatorJson
                            )
                            if (isNewSelection) menuReappearKey++
                        }
                    },
                    onSelectionCleared = {
                        if (epubDictionarySelection == null) {
                            selectionState = null
                        }
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
                    onNavigationStage = { operationId, stage ->
                        if (epubNavigationRequest?.operationId == operationId) {
                            epubNavigationStage = stage
                        }
                    },
                    onNavigationResult = { result ->
                        val request = epubNavigationRequest
                        if (request?.operationId != result.operationId) {
                            return@EpubWebViewReader
                        }
                        when (result) {
                            is EpubNavigationResult.Committed -> {
                                epubNavigationRequest = null
                                failedEpubNavigation = null
                                epubRenderFailure = null
                                epubNavigationRetryCount = 0
                            }
                            is EpubNavigationResult.Cancelled -> {
                                epubNavigationRequest = null
                            }
                            is EpubNavigationResult.Failed -> {
                                epubNavigationRequest = null
                                if (epubNavigationRetryCount == 0 && result.reason in setOf(
                                        EpubNavigationFailureReason.TIMEOUT,
                                        EpubNavigationFailureReason.DOCUMENT_ERROR,
                                        EpubNavigationFailureReason.HTTP_ERROR,
                                        EpubNavigationFailureReason.SCRIPT_NOT_READY,
                                        EpubNavigationFailureReason.SCRIPT_EXECUTION_ERROR)) {
                                    epubNavigationRetryCount = 1
                                    epubNavigationOperationId++
                                    epubNavigationRequest = request.copy(operationId = epubNavigationOperationId)
                                } else if (result.reason != EpubNavigationFailureReason.RENDERER_GONE) {
                                    failedEpubNavigation = request to result.reason
                                }
                            }
                        }
                    },
                    onRenderUnavailable = { failure ->
                        cancelActiveSearch()
                        epubSearchRequest = null
                        epubLocatorRequest = null
                        epubPageRequest = null
                        epubNavigationRequest = null
                        linkReturnLocation = null
                        epubRenderFailure = failure
                    },
                    modifier = Modifier.fillMaxSize()
                )
                }
                }
            } else if (uiState.useNewEngine && renderedContinuousScrollMode) {
                ContinuousScrollReader(
                    chapterCount = uiState.chapterCount,
                    currentChapter = uiState.currentChapterIndex,
                    initialChapterFraction = uiState.pendingReaderPosition?.chapterFraction
                        ?: uiState.pendingPageFraction.takeIf { it > 0f }
                        ?: (uiState.currentPageIndex.toFloat() / uiState.totalPages.coerceAtLeast(1)),
                    initialCharacterOffset = uiState.pendingReaderPosition?.characterOffset,
                    fontSize = uiState.fontSize,
                    lineHeight = uiState.lineHeight,
                    letterSpacingDp = uiState.letterSpacing,
                    textAlignment = uiState.textAlignment,
                    readerTypeface = continuousTypeface,
                    textColor = readerTextColorInt,
                    backgroundColor = readerBackgroundColorInt,
                    backgroundImagePath = readerBackgroundImagePath,
                    backgroundImageOpacity = uiState.readerBackgroundImageOpacity,
                    backgroundImageBlurDp = readerBackgroundImageBlurDp,
                    marginLeft = uiState.marginLeftDp,
                    marginRight = uiState.marginRightDp,
                    marginTop = uiState.marginTopDp,
                    marginBottom = uiState.marginBottomDp,
                    paragraphSpacing = uiState.paragraphSpacing,
                    firstLineIndent = uiState.firstLineIndent,
                    bionicReadingEnabled = effectiveBionicReadingEnabled,
                    lineGuideEnabled = effectiveLineGuide,
                    lineGuideDimLevel = uiState.lineGuideDimLevel,
                    contentRevision = uiState.contentRevision,
                    loadChapterText = viewModel::getFrameworkDrawnChapterText,
                    loadChapterPreviewText = viewModel::getFrameworkDrawnChapterPreview,
                    onContentSizeChanged = { width, height ->
                        if (width > 0) viewModel.updateReaderContentWidth(width)
                        viewModel.updateReaderContentHeight(height)
                    },
                    notes = renderedNotes,
                    searchHighlight = continuousSearchHighlight,
                    scrollRequests = continuousScrollRequests,
                    onSearchHighlightFinished = { continuousSearchHighlight = null },
                    onMenuToggle = {
                        viewModel.toggleMenu()
                    },
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
                    onSelectionCleared = {
                        selectionState = null
                        isSelectionDragging = false
                        resetSelectionSubmenus()
                    },
                    onSelection = { chapterIndex, selection ->
                        // 长按选中后菜单马上出现，这里主动收起放大镜，
                        // 否则放大镜会和菜单叠在一起（系统不保证把 ACTION_UP 送进来）。
                        readViewRef.value?.curPageView?.endSelectionMagnifier()
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
                            selEndX = selection.endX,
                            annotationOnly = selection.annotationOnly
                        )
                        isSelectionDragging = false
                        menuReappearKey++
                    },
                    onChapterVisible = { chapterIndex, chapterFraction, origin ->
                        val pageCount = viewModel.pageLayoutEngine.getChapterPageCount(chapterIndex)
                        val visiblePage = if (pageCount > 0) {
                            (chapterFraction.coerceIn(0f, 0.9999f) * pageCount)
                                .toInt()
                                .coerceIn(0, pageCount - 1)
                        } else {
                            0
                        }
                        if (origin == TtsPageChangeOrigin.USER) {
                            rememberTtsReturnLocation(chapterIndex, visiblePage)
                        } else {
                            lastVisibleReaderLocation = ReaderLinkLocation(chapterIndex, visiblePage)
                        }
                        viewModel.onContinuousScrollPosition(chapterIndex, chapterFraction, origin)
                    },
                    onViewportAnchor = { viewModel.onNativeComicAnchor(it, uiState.contentRevision) },
                    onRestoreComplete = viewModel::clearPendingPageFraction,
                    onSentenceDoubleTap = { chapterIndex, characterOffset ->
                        viewModel.seekTtsToSentence(chapterIndex, characterOffset)
                    },
                    ttsSentenceJumpEnabled = ttsState.activeBookId == uiState.book?.id &&
                        ttsState.playbackState != TtsPlaybackState.IDLE,
                    chineseMode = uiState.chineseMode,
                    ttsCurrentSentence = ttsCurrentSentence,
                    comicModeEnabled = uiState.comicModeEnabled,
                    imageAdjustments = uiState.imageAdjustments.forDisplay(eInkMode),
                    bodyFontWeight = uiState.bodyFontWeight
                )
            } else if (uiState.useNewEngine) {
            AndroidView(
                factory = { ctx ->
                    ReadView(ctx, viewModel.pageLayoutEngine).apply {
                        slotManager.backgroundPreparationEnabled = backgroundPreparationEnabled
                        setCallbacks(object : ReadViewCallbacks {
                            override fun onPageChanged(
                                globalPage: Int,
                                chapterIndex: Int,
                                pageInChapter: Int,
                                chapterTotalPages: Int,
                                origin: TtsPageChangeOrigin
                            ) {
                                if (origin == TtsPageChangeOrigin.USER) {
                                    rememberTtsReturnLocation(chapterIndex, pageInChapter)
                                } else {
                                    lastVisibleReaderLocation = ReaderLinkLocation(chapterIndex, pageInChapter)
                                }
                                // 翻页时关闭选择菜单（选区已随页面切换失效）
                                selectionState = null
                                isSelectionDragging = false
                                footnoteBubble = null
                                viewModel.onNewEnginePageChanged(
                                    globalPage, chapterIndex, pageInChapter, chapterTotalPages, origin
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

                            override fun onSelectionMenuDismiss() {
                                dismissSelectionMenu()
                            }

                            override fun onTtsSentenceDoubleTap(
                                chapterIndex: Int,
                                characterOffset: Int
                            ) {
                                viewModel.seekTtsToSentence(chapterIndex, characterOffset)
                            }

                            override fun onBookmarkPullStart() {
                                latestBookmarkPullStart.value()
                            }

                            override fun onBookmarkPullProgress(
                                distancePx: Float,
                                armed: Boolean
                            ) {
                                latestBookmarkPullProgress.value(distancePx, armed)
                            }

                            override fun onBookmarkPullFinished(commit: Boolean) {
                                latestBookmarkPullFinished.value(commit)
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
                                selectionCrossPage = false
                                val info = readViewRef.value?.getSelectionInfo(sourceView)
                                    ?: return
                                selectionState = selectionStateForReaderInfo(readerNotes, info)
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
                                                    selectionState = selectionStateForReaderInfo(readerNotes, fresh)
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

                            override fun onReaderSelectionChanged(
                                info: com.huangder.lumibooks.ui.reader.engine.SelectionInfo
                            ) {
                                // 跨页自持选区：拖拽期间 ReadView 只在落地（松手/吸附）后回调，
                                // 因此这里直接刷新菜单状态并按新坐标重弹。
                                isSelectionDragging = false
                                selectionState = selectionStateForReaderInfo(readerNotes, info)
                                menuReappearKey++
                            }

                            override fun onReaderSelectionCrossPageExtended() {
                                // 已经翻到新页：提示改为「单击目标结尾以完成选择」
                                selectionCrossPage = true
                            }

                            override fun onReaderSelectionCleared() {
                                selectionState = null
                                isSelectionDragging = false
                                selectionCrossPage = false
                            }

                            override fun onReaderSelectionDragStarted() {
                                // 跨页选区手柄被重新抓住：先收起菜单，松手后按新坐标重弹。
                                selectionState = null
                                isSelectionDragging = true
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
                                            color = readerHighlightColorReference(0, "highlight")
                                        )?.let { annotationTagState.quickTarget = it to false }
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
                            viewModel.getChapterText(
                                index = chapterIndex,
                                contentWidthPx = viewModel.pageLayoutEngine.visibleWidth,
                                contentHeightPx = viewModel.pageLayoutEngine.visibleHeight
                            )
                        }
                        readViewRef.value = this
                    }
                },
                update = { readView ->
                    readView.slotManager.backgroundPreparationEnabled = backgroundPreparationEnabled
                    readView.setLineGuide(effectiveLineGuide && !renderedContinuousScrollMode,
                        uiState.lineGuideDimLevel)
                    readView.setImageAdjustments(uiState.imageAdjustments.forDisplay(eInkMode))
                    readView.setBookmarkPullEnabled(bookmarkPullEnabled)
                    val fontSizePx = uiState.fontSize * density.density
                    val pageTransition = if (isContinuousScrollMode) lastPagedTransition else effectivePageTransition
                    readView.applyRenderConfig(
                        ReaderRenderConfig(
                            layout = ReaderLayoutConfig(
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
                                paragraphSpacingDp = uiState.paragraphSpacing,
                                firstLineIndent = uiState.firstLineIndent,
                                bodyFontWeight = uiState.bodyFontWeight,
                                bionicReadingEnabled = effectiveBionicReadingEnabled,
                                useDisplayDensityForSpans = uiState.book?.format?.name == "TXT",
                                writingMode = uiState.readerWritingMode,
                                twoPageSpread = twoPageSpreadEligible
                            ),
                            background = ReaderBackgroundConfig(
                                color = readerBackgroundColorInt,
                                textColor = readerTextColorInt,
                                imagePath = readerBackgroundImagePath,
                                imageOpacity = uiState.readerBackgroundImageOpacity,
                                imageBlurDp = readerBackgroundImageBlurDp
                            ),
                            chineseMode = uiState.chineseMode,
                            pageTransition = pageTransition,
                            pageTransitionDurationMs = uiState.pageAnimationSettings.durationFor(effectivePageTransition),
                            edgeTapMode = uiState.readerEdgeTapMode
                        )
                    )
                    // 正文盒子直接取排版引擎量出来的可见区域（含它对上下边距、角落信息区的调整），
                    // 整页图按这个盒子等比适配，才不会超出一页被拆开或挤到页面下方。
                    viewModel.updateReaderContentSize(
                        widthPx = viewModel.pageLayoutEngine.visibleWidth,
                        heightPx = viewModel.pageLayoutEngine.visibleHeight
                    )
                    // Keep styled annotations in the hit-test list as well. Their visual
                    // spans are already applied by ReaderViewModel, so buildHighlights
                    // skips the duplicate background/underline layer below.
                    readView.setSavedNotes(renderedNotes)
                    readView.ttsHighlightRange = ttsCurrentSentence?.let {
                        TtsHighlightRange(it.chapterIndex, it.startOffset, it.endOffset)
                    }
                    readView.setTtsSentenceJumpEnabled(
                        ttsState.activeBookId == uiState.book?.id &&
                            ttsState.playbackState != TtsPlaybackState.IDLE
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(uiState.contentRevision, readViewRef.value) {
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

        val bookmarkCommitThresholdPx = with(density) {
            BookmarkPullGestureTracker.COMMIT_THRESHOLD_DP.dp.toPx()
        }
        val bookmarkAddProgress = when {
            bookmarkPullActive && !bookmarkPullStartedBookmarked ->
                bookmarkPullDistancePx / bookmarkCommitThresholdPx
            bookmarkPullSettleMode == BookmarkPullSettleMode.ADD_CANCEL ->
                (bookmarkSettleAnimation?.value ?: 0f) / bookmarkCommitThresholdPx
            bookmarkPullSettleMode == BookmarkPullSettleMode.ADD_COMMIT -> 1f
            else -> 0f
        }.coerceIn(0f, 1f)
        val bookmarkIndicatorAlpha = when {
            bookmarkPullActive && bookmarkPullStartedBookmarked -> 1f
            bookmarkPullActive -> bookmarkAddProgress
            bookmarkPullSettleMode == BookmarkPullSettleMode.ADD_CANCEL -> bookmarkAddProgress
            bookmarkPullSettleMode == BookmarkPullSettleMode.ADD_COMMIT -> 1f
            bookmarkPullSettleMode == BookmarkPullSettleMode.REMOVE_COMMIT ->
                bookmarkExitAlphaAnimation?.value ?: 0f
            bookmarkPullSettleMode == BookmarkPullSettleMode.REMOVE_CANCEL -> 1f
            isCurrentPageBookmarked -> 1f
            else -> 0f
        }
        val bookmarkIndicatorOffsetPx = when {
            bookmarkPullActive && bookmarkPullStartedBookmarked ->
                bookmarkPullDistancePx * BOOKMARK_REMOVE_DRAG_RATIO
            bookmarkPullActive -> with(density) { (-64).dp.toPx() } *
                (1f - bookmarkAddProgress)
            bookmarkPullSettleMode == BookmarkPullSettleMode.ADD_CANCEL ->
                with(density) { (-64).dp.toPx() } * (1f - bookmarkAddProgress)
            bookmarkPullSettleMode == BookmarkPullSettleMode.REMOVE_COMMIT ->
                bookmarkExitOffsetAnimation?.value ?: with(density) { (-64).dp.toPx() }
            bookmarkPullSettleMode == BookmarkPullSettleMode.REMOVE_CANCEL ->
                (bookmarkSettleAnimation?.value ?: 0f) * BOOKMARK_REMOVE_DRAG_RATIO
            else -> 0f
        }
        val showBookmarkIndicator = bookmarkPullSupported &&
            !uiState.isMenuVisible &&
            (bookmarkPullActive || bookmarkPullSettleMode != null || isCurrentPageBookmarked)
        if (showBookmarkIndicator) {
            Icon(
                imageVector = AppIcons.Bookmark.regular,
                contentDescription = stringResource(R.string.reader_bookmark),
                tint = Color(readerTextColorInt),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp)
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = bookmarkIndicatorAlpha
                        translationY = bookmarkIndicatorOffsetPx
                    }
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
        com.huangder.lumibooks.ui.components.LiquidGlassMenuHost(
            modifier = Modifier.fillMaxSize(),
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
                rightPageIndex = uiState.rightPageIndex,
                rightChapterIndex = uiState.rightChapterIndex
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
            val displayRightChapterIndex = if (spreadDisplay) {
                null
            } else {
                displayedMenuSnapshot.rightChapterIndex
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

            if (uiState.isMenuVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = viewModel::hideMenu
                        )
                )
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
                val supportsHighlightRules = uiState.book?.format?.name in setOf("TXT", "EPUB", "MOBI")
                ReaderTopBar(
                    title = bookTitle,
                    onBack = exitReader,
                    bgColor = menuBgColor,
                    contentColor = menuContentColor,
                    glassContentScrimColor = readerGlassContentScrim,
                    forceSolidButtons = isBookLayout,
                    isTtsActive = ttsState.activeBookId == uiState.book?.id &&
                        ttsState.playbackState != TtsPlaybackState.IDLE,
                    onTtsClick = {
                        if (ttsState.activeBookId == uiState.book?.id &&
                            ttsState.playbackState != TtsPlaybackState.IDLE
                        ) {
                            viewModel.toggleTtsPlayPause()
                        } else {
                            requestTtsStart()
                        }
                    },
                    isBookmarked = isCurrentPageBookmarked,
                    onBookmarkToggle = { toggleBookmarkForCurrentPage(true) },
                    supportsHighlightRules = supportsHighlightRules,
                    onHighlightRulesClick = {
                        viewModel.hideMenu()
                        showHighlightRules = true
                    },
                    isTxtBook = isTxtBook,
                    bookFormat = uiState.book?.format?.name.orEmpty(),
                    lineGuideAvailable = lineGuideAvailable,
                    lineGuideEnabled = uiState.lineGuideEnabled,
                    onLineGuideToggle = {
                        clearActiveTextSelection()
                        viewModel.saveLineGuideEnabled(!viewModel.uiState.value.lineGuideEnabled)
                        viewModel.hideMenu()
                    },
                    bionicReadingEnabled = uiState.bionicReadingEnabled,
                    onBionicReadingToggle = {
                        viewModel.saveBionicReadingEnabled(!viewModel.uiState.value.bionicReadingEnabled)
                    },
                    comicModeEnabled = uiState.comicModeEnabled,
                    onComicModeToggle = { viewModel.saveComicMode(!viewModel.uiState.value.comicModeEnabled) },
                    onSwitchComicReader = viewModel::requestEpubComicReader,
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
                    },
                    onTocRuleClick = {
                        viewModel.hideMenu()
                        showTxtTocDialog = true
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
                        rightChapterIndex = displayRightChapterIndex,
                        currentChapterIndex = displayedMenuSnapshot.chapterIndex,
                        capsuleBgColor = capsuleBgColor,
                        capsuleContentColor = if (isLiquidGlass && !isBookLayout) menuContentColor else capsuleContentColor,
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
                                isBookLayout -> navigateEpub(
                                    EpubNavigationOrigin.CHAPTER_CONTROL,
                                    targetChapter,
                                    EpubNavigationDestination.ChapterStart,
                                    true
                                )
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
                                isBookLayout -> navigateEpub(
                                    EpubNavigationOrigin.CHAPTER_CONTROL,
                                    targetChapter,
                                    EpubNavigationDestination.ChapterStart,
                                    true
                                )
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
                                    continuousScrollRequests.tryEmit(
                                        ContinuousScrollRequest(
                                            target.chapterIndex,
                                            target.chapterFraction
                                        )
                                    )
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

                                    epubSearchRequest = null
                                    epubLocatorRequest = null
                                    epubPageRequest = null
                                    navigateEpub(
                                        EpubNavigationOrigin.PROGRESS,
                                        target.chapterIndex,
                                        EpubNavigationDestination.Page(
                                            index = 0,
                                            chapterFraction = target.chapterFraction
                                        ),
                                        true
                                    )
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
                    // 选字过程中的引导提示：开始选字 → 拖到页角翻页；翻到新页 → 单击目标结尾。
                    // 拖拽期间 selectionState 会被清空（菜单收起），所以同时看 isSelectionDragging，
                    // 保证拖动过程中提示常驻。
                    val selectionHintText = when {
                        isBookLayout || isContinuousScrollMode -> null
                        selectionState == null && !isSelectionDragging -> null
                        selectionCrossPage ->
                            stringResource(R.string.reader_selection_hint_tap_target)
                        else -> stringResource(R.string.reader_selection_hint_switch_page)
                    }
                    ReaderPageCornerOverlay(
                        chapterTitle = chapterTitle,
                        bookProgressPercent = bookProgressPercent,
                        currentPage = displayCurrentPage,
                        chapterPageCount = displayPageCount,
                        rightPageIndex = displayRightPageIndex,
                        rightChapterIndex = displayRightChapterIndex,
                        currentChapterIndex = displayedMenuSnapshot.chapterIndex,
                        // 四角信息区边距独立可调；未设置过的边沿用正文边距 / 旧版默认位置。
                        leftMarginDp = uiState.readerCornerMargins.resolvedLeftDp(uiState.marginLeftDp),
                        rightMarginDp = uiState.readerCornerMargins.resolvedRightDp(uiState.marginRightDp),
                        topMarginDp = uiState.readerCornerMargins.resolvedTopDp(),
                        bottomMarginDp = uiState.readerCornerMargins.resolvedBottomDp(),
                        topLeft = if (linkReturnLocation == null) {
                            uiState.readerTopLeftContent
                        } else {
                            ReaderCornerContent.NONE
                        },
                        topRight = uiState.readerTopRightContent,
                        bottomLeft = uiState.readerBottomLeftContent,
                        bottomRight = uiState.readerBottomRightContent,
                        contentColor = Color(readerTextColorInt).copy(alpha = 0.45f),
                        selectionHintText = selectionHintText,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (effectiveLineGuide && !isAnySheetOpen) {
                ReaderLineGuideControl(
                    level = uiState.lineGuideDimLevel,
                    backgroundColor = menuBgColor,
                    contentColor = menuContentColor,
                    onLevelSelected = viewModel::saveLineGuideDimLevel,
                    onClose = { viewModel.saveLineGuideEnabled(false) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 20.dp, bottom = 18.dp)
                )
            }

            val ttsBottomPadding by animateDpAsState(
                targetValue = if (uiState.isMenuVisible) 204.dp else 44.dp,
                animationSpec = if (eInkMode || !motionEnabled) snap() else spring(dampingRatio = 0.82f, stiffness = 360f),
                label = "ttsBottomPadding"
            )
            AnimatedVisibility(
                visible = ttsState.activeBookId == uiState.book?.id &&
                    ttsState.playbackState != TtsPlaybackState.IDLE &&
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
                    chapterTitle = ttsCurrentPage?.location?.chapterIndex
                        ?.let { ttsChapter ->
                            uiState.chapterTitles.getOrNull(ttsChapter)
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                        }
                        ?: liveChapterTitle,
                    playbackState = ttsState.playbackState,
                    speechRate = ttsState.speechRate,
                    speechRateMode = ttsState.speechRateMode,
                    pitch = ttsState.pitch,
                    pitchMode = ttsState.pitchMode,
                    usesAndroidTts = ttsState.usesAndroidTts,
                    sleepTimerRemainingMs = ttsState.sleepTimerRemainingMs,
                    onPlayPause = viewModel::toggleTtsPlayPause,
                    onStop = viewModel::stopTts,
                    onSkipForward = viewModel::ttsSkipForward,
                    onSkipBackward = viewModel::ttsSkipBackward,
                    onRateChange = viewModel::setTtsSpeechRate,
                    onRateModeChange = viewModel::setTtsSpeechRateMode,
                    onPitchChange = viewModel::setTtsPitch,
                    onPitchModeChange = viewModel::setTtsPitchMode,
                    onSetSleepTimer = viewModel::setSleepTimer,
                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                    onReturnToProgress = returnToTtsProgress,
                    onStartFromCurrentPage = { startTtsFromCurrentPage(false) },
                    canReturnToProgress = ttsCurrentPage?.location?.let { location ->
                        location.chapterIndex != uiState.currentChapterIndex ||
                            location.pageIndex != uiState.currentPageIndex
                    } == true,
                    readerMenuVisible = uiState.isMenuVisible,
                    readerBackgroundColor = composeBgColor,
                    readerContentColor = Color(readerTextColorInt),
                    forceSolidSurface = isBookLayout
                )
            }

            // 目录底部弹出
            TocSheet(
                visible = showToc,
                requestClose = requestCloseToc,
                glassBackdrop = activeReaderGlassBackdrop,
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
                        } else if (isBookLayout) {
                            epubSearchRequest = null
                            epubLocatorRequest = null
                            epubPageRequest = null
                            navigateEpub(
                                EpubNavigationOrigin.TOC,
                                target.chapterIndex,
                                entry.anchor?.takeIf { it.isNotBlank() }?.let {
                                    EpubNavigationDestination.Fragment(it)
                                } ?: EpubNavigationDestination.ChapterStart,
                                true
                            )
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
                        val locatorJson = bm.locatorJson?.let { json ->
                            if (comicPositionFromLocator(json) != null) JSONObject(json).put("href", chapterHref).toString() else json
                        }
                            ?.takeIf { isEpubLocatorForChapter(it, chapterHref) }
                            ?: bm.characterOffset?.let { characterOffset ->
                                createEpubFallbackLocator(
                                    href = chapterHref,
                                    charOffset = characterOffset,
                                    chapterTextLength = viewModel.getChapterTextLength(bm.chapterIndex).coerceAtLeast(1)
                                )
                            }
                        epubSearchRequest = null
                        epubLocatorRequest = null
                        epubPageRequest = null
                        navigateEpub(
                            EpubNavigationOrigin.BOOKMARK,
                            bm.chapterIndex,
                            locatorJson?.let { EpubNavigationDestination.Locator(it) }
                                ?: EpubNavigationDestination.Page(
                                    bm.position.toInt().coerceAtLeast(0)
                                ),
                            true
                        )
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
                availableTags = availableAnnotationTags,
                onEditBookmarkTags = annotationTagState::editBookmark,
                onEditBookmarkRemark = { bm, remark, tags -> viewModel.updateBookmarkRemarkAndTags(bm.syncId, remark, tags) },
                onDismiss = { showToc = false; requestCloseToc = false }
            )

            // 主题设置弹窗
            ThemeSettingsSheet(
                visible = showThemeSheet,
                requestClose = requestCloseTheme,
                currentFontSize = uiState.activeThemeSettingsForMode(uiState.themeEditingDark).fontSize,
                currentFontType = uiState.activeThemeSettingsForMode(uiState.themeEditingDark).fontType,
                currentCustomFontPath = uiState.customFonts.firstOrNull {
                    "custom:${it.id}" == uiState.activeThemeSettingsForMode(uiState.themeEditingDark).fontType
                }?.path,
                currentBodyFontWeight = uiState.activeThemeSettingsForMode(uiState.themeEditingDark).bodyFontWeight,
                onBodyFontWeightChange = viewModel::saveBodyFontWeight,
                currentTheme = uiState.activeThemeSettingsForMode(uiState.themeEditingDark).backgroundSelection,
                currentBackgroundSelection = uiState.activeThemeSettingsForMode(uiState.themeEditingDark).backgroundSelection,
                customBackgrounds = uiState.customReaderBackgrounds,
                readerThemeSuites = uiState.readerThemeSuites,
                activeReaderThemeSuiteId = uiState.activeReaderThemeSuiteId,
                isAppDark = appIsDark,
                readerThemeSuiteBookScoped = uiState.readerThemeSuiteBookScoped,
                readerButtonContrastEnabled = uiState.readerButtonContrastEnabled,
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
                editingDark = uiState.themeEditingDark,
                onFontSizeChange = { viewModel.saveFontSize(it) },
                onThemeChange = { viewModel.saveReaderTheme(it) },
                onModeThemeChange = viewModel::saveReaderThemeForMode,
                onModeChange = viewModel::selectThemeEditMode,
                onExportThemeBundle = { suiteId ->
                    pendingExportThemeId = suiteId
                    exportThemeLauncher.launch("lumi-theme.lumi-theme.json")
                },
                onImportThemeBundle = { importThemeLauncher.launch(arrayOf("application/json", "application/zip", "text/plain", "application/octet-stream")) },
                onBackgroundSelect = { viewModel.selectReaderBackground(it) },
                onAddBackgroundColor = { color, name -> viewModel.addCustomReaderBackgroundColor(color, name) },
                onAddBackgroundImage = { uri, name -> viewModel.addCustomReaderBackgroundImage(uri, name) },
                onDeleteBackground = { viewModel.deleteCustomReaderBackground(it) },
                onThemeSuiteSelect = viewModel::selectReaderThemeSuite,
                onThemeSuiteCreate = viewModel::createReaderThemeSuite,
                onThemeSuiteDelete = viewModel::deleteReaderThemeSuite,
                onThemeSuitesReorder = viewModel::reorderReaderThemeSuites,
                onThemeSuiteBookScopedChange = viewModel::setApplyThemeSuiteToBook,
                onReaderButtonContrastChange = viewModel::saveReaderButtonContrastEnabled,
                onPreserveEpubBackgroundChange = viewModel::savePreserveEpubBackground,
                onBrightnessChange = { viewModel.saveBrightness(it) },
                onOptimizeLayoutChange = { viewModel.saveOptimizeLayout(it) },
                onUseEpubCssChange = { viewModel.saveUseEpubCss(it) },
                onRenderModeChange = viewModel::saveRenderMode,
                onWritingModeChange = viewModel::saveReaderWritingMode,
                onChineseModeChange = { viewModel.saveChineseMode(it) },
                onPageTransitionChange = { mode ->
                    // Capture from the attached page before changing modes detaches it.
                    val anchor = if (!isBookLayout && !isContinuousScrollMode) {
                        readViewRef.value?.getCurrentPageTextAnchor()
                    } else null
                    viewModel.savePageTransition(mode, anchor)
                },
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
                    if (!openAdvancedAfterThemeClose) {
                        viewModel.selectThemeEditMode(nightDisplay)
                    }
                    showThemeSheet = false
                    requestCloseTheme = false
                }
            )

            HighlightRulesSheet(
                visible = showHighlightRules,
                requestClose = requestCloseHighlightRules,
                rules = highlightRules,
                customFonts = uiState.customFonts,
                materializeNotes = highlightSettings.materializeNotes,
                scanState = highlightScanState,
                eInkModeEnabled = eInkMode,
                backdrop = activeReaderGlassBackdrop,
                onMaterializeChange = viewModel::setHighlightRulesMaterialized,
                onSaveRule = viewModel::saveHighlightRule,
                onDuplicateRule = viewModel::duplicateHighlightRule,
                onDeleteRule = viewModel::deleteHighlightRule,
                onRuleEnabledChange = viewModel::setHighlightRuleEnabled,
                onMoveRule = viewModel::moveHighlightRule,
                onImportRules = viewModel::importHighlightRules,
                onExportRules = viewModel::exportHighlightRules,
                onImportFont = { uri, displayName ->
                    scope.launch { viewModel.importFont(context, uri, displayName) }
                },
                onCancelScan = viewModel::cancelHighlightRuleScan,
                onRebuild = viewModel::rebuildHighlightRuleNotes,
                onDismiss = {
                    showHighlightRules = false
                    requestCloseHighlightRules = false
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
                    val epubSearchLocator = if (isBookLayout) {
                        result.epubLocator ?: run {
                            Toast.makeText(
                                context,
                                R.string.epub_search_location_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@resultClick
                        }
                    } else {
                        null
                    }
                    val source = when {
                        isBookLayout -> ReaderLinkLocation(
                            chapterIndex = uiState.currentChapterIndex,
                            pageIndex = uiState.currentPageIndex
                        )
                        isContinuousScrollMode -> ReaderLinkLocation(
                            chapterIndex = uiState.currentChapterIndex,
                            pageIndex = uiState.currentPageIndex,
                            chapterFraction = uiState.currentPageIndex.toFloat()
                                .div(uiState.totalPages.coerceAtLeast(1))
                                .coerceIn(0f, 0.9999f)
                        )
                        else -> readViewRef.value?.getCurrentLocation()?.let { (chapter, page) ->
                            ReaderLinkLocation(chapterIndex = chapter, pageIndex = page)
                        } ?: ReaderLinkLocation(
                            chapterIndex = uiState.currentChapterIndex,
                            pageIndex = uiState.currentPageIndex
                        )
                    }
                    linkReturnLocation = source
                    linkReturnToken += 1

                    if (isBookLayout) {
                        epubSearchRequestToken++
                        epubLocatorRequest = null
                        epubPageRequest = null
                        epubSearchRequest = EpubSearchRequest(
                            token = epubSearchRequestToken,
                            chapterIndex = result.chapterIndex,
                            locator = epubSearchLocator!!
                        )
                        navigateEpub(
                            EpubNavigationOrigin.SEARCH,
                            result.chapterIndex,
                            EpubNavigationDestination.Locator(
                                epubSearchLocator.toJson().toString()
                            ),
                            true
                        )
                    } else if (isContinuousScrollMode) {
                        continuousSearchHighlight = ContinuousSearchHighlight(
                            chapterIndex = result.chapterIndex,
                            start = result.charOffset,
                            end = result.charOffset + result.matchLength
                        )
                        val destinationFraction = result.charOffset.toFloat()
                            .div(viewModel.getChapterTextLength(result.chapterIndex).coerceAtLeast(1))
                            .coerceIn(0f, 0.9999f)
                        jumpToContinuousChapter(result.chapterIndex, destinationFraction)
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

            NetworkSearchSheet(
                visible = showWebSearch,
                initialQuery = searchQuery,
                onDismiss = {
                    showWebSearch = false
                    searchQuery = ""
                }
            )

            // 高级排版设置弹窗。不要在组合阶段同步读取/格式化整章文本：
            // 大型 EPUB 的 getChapterText() 可能需要数十秒，并且会阻塞主线程导致 ANR。
            var previewText by remember(bookId) { mutableStateOf("") }
            var previewImage by remember(bookId) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
            LaunchedEffect(showAdvancedSheet, uiState.currentChapterIndex, uiState.contentRevision) {
                if (!showAdvancedSheet) {
                    previewText = ""
                    previewImage = null
                    return@LaunchedEffect
                }
                val chapterIndex = uiState.currentChapterIndex
                val chapter = withContext(Dispatchers.IO) { viewModel.getChapterText(chapterIndex) }
                previewText = chapter?.toString()?.replace('\uFFFC', ' ')?.take(420).orEmpty()
                previewImage = (chapter as? android.text.Spanned)?.let {
                    it.getSpans(0, it.length, ImageSpan::class.java).firstOrNull()?.drawable
                }
            }
            AdvancedSettingsSheet(
                visible = showAdvancedSheet,
                requestClose = requestCloseAdvanced,
                previewText = previewText,
                previewImage = previewImage,
                currentLineHeight = editorThemeSettings.lineHeight,
                currentLetterSpacing = editorThemeSettings.letterSpacing,
                currentTextAlignment = editorThemeSettings.textAlignment,
                currentFontType = editorThemeSettings.fontType,
                customFontPath = uiState.customFontPath,
                customFonts = uiState.customFonts,
                currentBackgroundSelection = editorThemeSettings.backgroundSelection,
                customBackgrounds = uiState.customReaderBackgrounds,
                currentPreserveEpubBackground = effectivePreserveEpubBackground,
                showPreserveEpubBackground = supportsBookLayout,
                currentPageImageCrop = uiState.pageImageCrop,
                showPageImageCrop = supportsBookLayout &&
                    uiState.renderMode == EpubRenderMode.BOOK_LAYOUT,
                imageAdjustmentsEnabled = uiState.book?.format?.name in setOf("PDF", "CBZ") ||
                    (isEpub && (uiState.comicModeEnabled ||
                        epubSession?.isMediaOnlyPage(uiState.currentChapterIndex) == true)),
                currentImageBrightness = uiState.imageBrightness,
                currentImageContrast = uiState.imageContrast,
                currentImageSharpen = uiState.imageSharpen,
                onImageBrightnessChange = viewModel::saveImageBrightness,
                onImageContrastChange = viewModel::saveImageContrast,
                onImageSharpenChange = viewModel::saveImageSharpen,
                publisherSuiteActive = publisherPaintSuiteActive,
                currentMarginLeft = editorThemeSettings.marginLeft,
                currentMarginRight = editorThemeSettings.marginRight,
                currentMarginTop = editorThemeSettings.marginTop,
                currentMarginBottom = editorThemeSettings.marginBottom,
                currentCornerMargins = uiState.readerCornerMargins,
                currentBgColor = Color(editorBackgroundColorInt),
                currentBackgroundImagePath = editorBackgroundImagePath,
                currentTextColor = Color(editorThemeSettings.textColor ?: editorAutomaticTextColorInt),
                currentTextColorOverride = editorThemeSettings.textColor,
                currentFontSizeSp = editorThemeSettings.fontSize,
                editingDark = uiState.themeEditingDark,
                preservePublisherLayout = isBookLayout,
                currentWritingMode = uiState.readerWritingMode,
                fontDownloadKey = uiState.fontDownloadKey,
                fontDownloadFailed = uiState.fontDownloadFailed,
                onLineHeightChange = { viewModel.saveLineHeight(it) },
                onLetterSpacingChange = { viewModel.saveLetterSpacing(it) },
                onTextAlignmentChange = viewModel::saveTextAlignment,
                onFontTypeChange = { viewModel.saveFontType(it) },
                onImportFont = { uri, displayName ->
                    scope.launch {
                        val preset = viewModel.importFont(context, uri, displayName)
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
                onThemeEditModeChange = viewModel::selectThemeEditMode,
                onPreserveEpubBackgroundChange = viewModel::savePreserveEpubBackground,
                onPageImageCropChange = viewModel::savePageImageCrop,
                onMarginLeftChange = { viewModel.saveMarginLeft(it) },
                onMarginRightChange = { viewModel.saveMarginRight(it) },
                onMarginTopChange = { viewModel.saveMarginTop(it) },
                onMarginBottomChange = { viewModel.saveMarginBottom(it) },
                onMarginLeftPreview = viewModel::previewMarginLeft,
                onMarginRightPreview = viewModel::previewMarginRight,
                onMarginTopPreview = viewModel::previewMarginTop,
                onMarginBottomPreview = viewModel::previewMarginBottom,
                onCornerMarginsChange = viewModel::saveReaderCornerMargins,
                onCornerMarginsPreview = viewModel::previewReaderCornerMargins,
                currentParagraphSpacing = editorThemeSettings.paragraphSpacing,
                currentFirstLineIndent = editorThemeSettings.firstLineIndent,
                onParagraphSpacingChange = { viewModel.saveParagraphSpacing(it) },
                onFirstLineIndentChange = { viewModel.saveFirstLineIndent(it) },
                readerTopLeftContent = uiState.readerTopLeftContent,
                readerTopRightContent = uiState.readerTopRightContent,
                readerBottomLeftContent = uiState.readerBottomLeftContent,
                readerBottomRightContent = uiState.readerBottomRightContent,
                volumeKeyPageTurnEnabled = uiState.volumeKeyPageTurnEnabled,
                bookmarkRemarkPromptEnabled = uiState.bookmarkRemarkPromptEnabled,
                bionicReadingEnabled = uiState.bionicReadingEnabled,
                comicModeEnabled = uiState.comicModeEnabled,
                screenSleepTimeoutSeconds = uiState.screenSleepTimeoutSeconds,
                readerEdgeTapMode = uiState.readerEdgeTapMode,
                onReaderCornerContentChange = viewModel::saveReaderCornerContent,
                onVolumeKeyPageTurnEnabledChange = { viewModel.saveVolumeKeyPageTurnEnabled(it) },
                onBookmarkRemarkPromptEnabledChange = viewModel::saveBookmarkRemarkPromptEnabled,
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
                onDismiss = {
                    viewModel.selectThemeEditMode(nightDisplay)
                    showAdvancedSheet = false
                    requestCloseAdvanced = false
                }
            )
        }

        epubNavigationRequest?.takeIf {
            it.operationId == visibleEpubNavigationId && (it.showLoadingPage || isBookLayoutContinuousScroll)
        }?.let { request ->
            EpubNavigationLoadingOverlay(
                chapterTitle = uiState.chapterTitles
                    .getOrNull(request.targetChapterIndex)
                    .orEmpty()
                    .ifBlank {
                        context.getString(
                            R.string.reader_chapter_fallback,
                            request.targetChapterIndex + 1
                        )
                    },
                stage = epubNavigationStage,
                backgroundColor = composeBgColor,
                contentColor = Color(readerTextColorInt),
                onCancel = { clearCancelledEpubNavigation(epubNavigationRequest) },
                compact = isBookLayoutContinuousScroll,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        failedEpubNavigation?.let { (request, reason) ->
            EpubNavigationFailureBar(
                reason = reason,
                backgroundColor = composeBgColor,
                contentColor = Color(readerTextColorInt),
                onRetry = {
                    failedEpubNavigation = null
                    submitEpubNavigation(
                        request.origin,
                        request.targetChapterIndex,
                        request.destination,
                        request.showLoadingPage
                    )
                },
                onDismiss = {
                    failedEpubNavigation = null
                    clearCancelledEpubNavigation(request)
                },
                onSwitchLayout = {
                    failedEpubNavigation = null
                    viewModel.fallbackFromUnsupportedEpubWebView()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        epubRenderFailure?.takeIf { isBookLayout }?.let { failure ->
            EpubRenderFailureOverlay(
                failure = failure,
                backgroundColor = composeBgColor,
                contentColor = Color(readerTextColorInt),
                onRetry = { epubRenderFailure = null; epubRetryToken++ },
                onSwitchLayout = {
                    failure.lastSuccessfulPosition?.let {
                        viewModel.onEpubPageCommitted(it.chapterIndex, it.pageIndex, it.pageCount, it.locatorJson)
                    }
                    epubRenderFailure = null
                    viewModel.fallbackFromUnsupportedEpubWebView()
                },
                onClose = exitReader
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
                epubSearchRequest = null
                epubPageRequest = null
                epubLocatorRequest = null
                navigateEpub(
                    EpubNavigationOrigin.NOTE,
                    note.chapterIndex,
                    locatorJson?.let { EpubNavigationDestination.Locator(it) }
                        ?: EpubNavigationDestination.ChapterStart,
                    true
                )
            } else if (isContinuousScrollMode) {
                scope.launch {
                    val resolved = viewModel.resolveReaderNoteForNavigation(note) ?: return@launch
                    continuousScrollRequests.value = ContinuousScrollRequest(
                        resolved.chapterIndex, characterOffset = resolved.startPosition
                    )
                }
            } else {
                scope.launch {
                    val resolved = viewModel.resolveReaderNoteForNavigation(note) ?: return@launch
                    readViewRef.value?.jumpToCharacter(resolved.chapterIndex, resolved.startPosition)
                }
            }
            showNotesList = false
            requestCloseNotesList = false
        },
        onEditTags = annotationTagState::edit,
        onDeleteNote = { note -> viewModel.deleteNote(note) },
        onDismiss = { showNotesList = false; requestCloseNotesList = false }
    )

    LocalDictionarySheet(
        query = localDictionaryQuery,
        glassBackdrop = activeReaderGlassBackdrop,
        forceSolid = isBookLayout || eInkMode,
        onDismiss = {
            localDictionaryQuery = null
            dismissSelectionMenu()
        },
        onExternal = {
            val request = localDictionaryQuery?.let { prepareDictionaryLookup(context, it) }
            if (request == null || request.apps.isEmpty()) {
                Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
            } else {
                dictionaryLookupText = request.normalizedText
                dictionaryAppOptions = request.apps
                showDictionaryAppPicker = true
                localDictionaryQuery = null
            }
        },
        onWebSearch = {
            val query = localDictionaryQuery.orEmpty()
            localDictionaryQuery = null
            dismissSelectionMenu()
            searchQuery = query
            showWebSearch = true
        }
    )

    // ── 文字选择自定义菜单 ──
    fun selectionForAction(): SelectionState? {
        val menu = selectionState ?: return null
        if (menu.annotationOnly) return menu
        if (!isContinuousScrollMode) return menu
        val current = continuousSelectionController.currentSelection()
        val text = continuousSelectionController.activeView?.text
        if (current == null || text == null || current.chapterIndex != menu.chapterIndex) {
            dismissSelectionMenu()
            return null
        }
        return menu.copy(charStart = current.start, charEnd = current.end,
            selectedText = text.subSequence(current.start, current.end).toString())
    }
    val replaceSelectedAnnotationColor: (String, Int) -> Unit = { type, slot ->
        selectionForAction()?.let { selection ->
            applyAnnotationColor(viewModel, selection, type, slot)
        }
        selectionState = null
        clearActiveTextSelection()
    }
    val removeSelectedAnnotation: (String) -> Unit = { type ->
        selectionForAction()?.let { selection ->
            removeAnnotation(viewModel, selection, type)
        }
        selectionState = null
        clearActiveTextSelection()
    }
    SelectionMenuOverlay(
        state = selectionState.takeIf { localDictionaryQuery == null },
        readerTheme = renderingTheme,
        glassBackdrop = activeReaderGlassBackdrop,
        forceSolidSurface = isBookLayout,
        isDragging = isSelectionDragging,
        dismissOnBackgroundTap = !isVerticalWriting,
        reappearKey = menuReappearKey,
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
        onHighlight = {
            selectionForAction()?.let { selection ->
                createSelectionAnnotation(viewModel, selection, "highlight")?.let { annotationTagState.quickTarget = it to false }
            }
            selectionState = null
            resetSelectionSubmenus()
            clearActiveTextSelection()
        },
        onUnderline = {
            selectionForAction()?.let { selection ->
                createSelectionAnnotation(viewModel, selection, "underline")?.let { annotationTagState.quickTarget = it to false }
            }
            selectionState = null
            resetSelectionSubmenus()
            clearActiveTextSelection()
        },
        onNote = {
            val fresh = if (isBookLayout || isContinuousScrollMode) null else readViewRef.value?.getSelectionInfo()
            if (fresh != null) {
                pendingSelection = PendingSelection(
                    fresh.selectedText, fresh.chapterIndex,
                    fresh.startPosition,
                    fresh.endPosition
                )
                showNoteInput = true
            } else {
                selectionForAction()?.let { selection ->
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
            clearActiveTextSelection()
        },
        onSearch = {
            val query = if (isBookLayout || isContinuousScrollMode) {
                selectionForAction()?.selectedText
            } else {
                readViewRef.value?.getSelectionInfo()?.selectedText ?: selectionForAction()?.selectedText
            }
            if (query != null) {
                showSearch = true
                searchQuery = query
                submitSearch(query)
            }
            selectionState = null
            clearActiveTextSelection()
        },
        onWebSearch = {
            val query = if (isBookLayout || isContinuousScrollMode) {
                selectionForAction()?.selectedText
            } else {
                readViewRef.value?.getSelectionInfo()?.selectedText ?: selectionForAction()?.selectedText
            }
            if (!query.isNullOrBlank()) {
                searchQuery = query
                showWebSearch = true
            }
            selectionState = null
            clearActiveTextSelection()
        },
        onDictionary = {
            try {
                val fresh = if (isBookLayout || isContinuousScrollMode) null else readViewRef.value?.getSelectionInfo()
                val text = fresh?.selectedText ?: selectionForAction()?.selectedText
                if (!text.isNullOrBlank()) {
                    showDictionaryAppPicker = false
                    epubDictionarySelection = selectionForAction()?.takeIf { isBookLayout }?.let { selection ->
                        val start = selection.startLocatorJson
                        val end = selection.endLocatorJson
                        if (start != null && end != null) {
                            EpubDictionarySelection(start, end, selection.selectedText)
                        } else null
                    }
                    localDictionaryQuery = text
                }
            } catch (throwable: Throwable) {
                Log.w(DICTIONARY_LOOKUP_TAG, "Failed to open dictionary app picker", throwable)
                Toast.makeText(context, R.string.dictionary_no_app, Toast.LENGTH_SHORT).show()
            }
        },
        onDictionaryAppSelected = { appOption ->
            if (selectionForAction() == null) return@SelectionMenuOverlay
            if (launchDictionaryLookup(context, dictionaryLookupText, appOption)) {
                selectionState = null
                resetSelectionSubmenus()
                clearActiveTextSelection()
            }
        },
        onCopy = {
            val fresh = if (isBookLayout || isContinuousScrollMode) null else readViewRef.value?.getSelectionInfo()
            val text = fresh?.selectedText ?: selectionForAction()?.selectedText ?: return@SelectionMenuOverlay
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("selected", text))
            selectionState = null
            clearActiveTextSelection()
        },
        onViewNote = {
            // 🔥 查看/修改笔记：打开 NoteInputSheet 预填原笔记文字
            val existing = selectionForAction()?.existingNote
            if (existing != null) {
                editingNote = existing
                noteInputText = existing.note
                showNoteInput = true
            }
            selectionState = null
            clearActiveTextSelection()
        },
        onEditHighlightTags = {
            selectionForAction()?.overlappingHighlights?.firstOrNull()?.syncId?.takeIf { it.isNotEmpty() }?.let { annotationTagState.editingTarget = it to false }
            selectionState = null
        },
        onEditUnderlineTags = {
            selectionForAction()?.overlappingUnderlines?.firstOrNull()?.syncId?.takeIf { it.isNotEmpty() }?.let { annotationTagState.editingTarget = it to false }
            selectionState = null
        },
        onChangeHighlightColor = { slot ->
            replaceSelectedAnnotationColor("highlight", slot)
        },
        onChangeUnderlineColor = { slot ->
            replaceSelectedAnnotationColor("underline", slot)
        },
        onChangeUnderlineStyle = { mode ->
            selectionForAction()?.let { selection ->
                updateUnderlineStyle(viewModel, selection, mode)
            }
            selectionState = null
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
            val fresh = if (isBookLayout || isContinuousScrollMode) null else readViewRef.value?.getSelectionInfo()
            val text = fresh?.selectedText ?: selectionForAction()?.selectedText
            if (text != null) {
                val chapterIndex = fresh?.chapterIndex ?: selectionForAction()?.chapterIndex
                val readerStart = fresh?.startPosition ?: selectionForAction()?.charStart
                val readerEnd = fresh?.endPosition ?: selectionForAction()?.charEnd
                val sourceRange = if (chapterIndex != null && readerStart != null && readerEnd != null) {
                    viewModel.resolveTxtSourceRange(chapterIndex, readerStart, readerEnd)
                } else {
                    null
                }
                replaceSelection = ReplaceSelectionInfo(
                    selectedText = text,
                    chapterIndex = chapterIndex,
                    charStart = sourceRange?.start,
                    charEnd = sourceRange?.endExclusive
                )
            }
            selectionState = null
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
            if (selectionForAction() == null) return@SelectionMenuOverlay
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
    ReaderReplaceInputSheet(
        visible = showReplaceInput,
        glassBackdrop = activeReaderGlassBackdrop,
        selection = replaceSelection,
        viewModel = viewModel,
        onDismiss = {
            showReplaceInput = false
            replaceSelection = null
        }
    )

    ReaderFirstOpenHints(
        epub = uiState.showEpubLayoutHint,
        mobi = uiState.showMobiLayoutHint,
        txtEncoding = uiState.showTxtEncodingHint,
        backdrop = activeReaderGlassBackdrop,
        viewModel = viewModel
    )

    ReaderTxtConfigurationDialogs(
        uiState = uiState,
        showEncoding = showTxtEncodingDialog,
        showToc = showTxtTocDialog,
        backdrop = activeReaderGlassBackdrop,
        viewModel = viewModel,
        onEncodingDismiss = { showTxtEncodingDialog = false },
        onTocDismiss = { showTxtTocDialog = false }
    )

    ReaderExternalLinkDialog(
        href = pendingExternalLink,
        onDismiss = { pendingExternalLink = null },
        onOpen = { href ->
            pendingExternalLink = null
            openExternalBookLink(context, href)
        }
    )

    ReaderSelectionNoteSheet(
        visible = showNoteInput,
        requestClose = requestCloseNoteInput,
        glassBackdrop = activeReaderGlassBackdrop,
        noteText = noteInputText,
        onTextChange = { noteInputText = it },
        editingNote = editingNote,
        availableTags = availableAnnotationTags,
        pendingSelection = pendingSelection,
        viewModel = viewModel,
        onConfirmed = {
            editingNote = null
            pendingSelection = null
            noteInputText = ""
            clearActiveTextSelection()
        },
        onDismissed = {
            showNoteInput = false
            requestCloseNoteInput = false
            pendingSelection = null
            editingNote = null
            noteInputText = ""
        }
    )

    ReaderBookmarkRemarkSheet(
        bookmarkId = pendingBookmarkRemarkId,
        text = bookmarkRemarkText,
        backdrop = activeReaderGlassBackdrop,
        onTextChange = { bookmarkRemarkText = it },
        tags = bookmarkRemarkTags,
        availableTags = availableAnnotationTags,
        onTagsChange = { bookmarkRemarkTags = it },
        onConfirm = { id, remark ->
            viewModel.updateBookmarkRemarkAndTags(id, remark, bookmarkRemarkTags)
            annotationTagState.quickTarget = null
        },
        onDismiss = { pendingBookmarkRemarkId = null; bookmarkRemarkText = "" }
    )
    ReaderAnnotationTagControls(
        backdrop = activeReaderGlassBackdrop,
        quickTarget = annotationTagState.quickTarget.takeIf { pendingBookmarkRemarkId == null },
        editingTarget = annotationTagState.editingTarget,
        bookmarks = bookmarks,
        notes = notes,
        availableTags = availableAnnotationTags,
        viewModel = viewModel,
        onQuickClick = annotationTagState::openQuick,
        onEditorDismiss = annotationTagState::dismissEditor
    )

    if (!isBookLayout) {
        val preview = readerImagePreview
        val session = epubSession
        if (preview != null && session != null) {
            EpubImagePreviewOverlay(
                session = session,
                imageAdjustments = uiState.imageAdjustments.forDisplay(eInkMode),
                request = preview,
                progress = readerImagePreviewProgress.value,
                onDismissRequest = dismissReaderImagePreview
            )
        }
    }
}
}
}

@Composable
private fun ReaderExternalLinkDialog(
    href: String?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    if (href == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_external_link_title)) },
        text = { Text(stringResource(R.string.reader_external_link_message, href)) },
        confirmButton = {
            TextButton(onClick = { onOpen(href) }) {
                Text(stringResource(R.string.reader_external_link_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ReaderTxtConfigurationDialogs(
    uiState: ReaderUiState,
    showEncoding: Boolean,
    showToc: Boolean,
    backdrop: Backdrop?,
    viewModel: ReaderViewModel,
    onEncodingDismiss: () -> Unit,
    onTocDismiss: () -> Unit
) {
    val context = LocalContext.current
    if (showEncoding) {
        TxtEncodingDialog(
            currentEncoding = uiState.txtEncoding,
            activeCharsetName = uiState.txtActiveCharsetName,
            isEncodingChanging = uiState.isTxtEncodingChanging,
            backdrop = backdrop,
            onEncodingSelected = viewModel::saveTxtEncoding,
            onDismiss = onEncodingDismiss
        )
    }
    if (showToc) {
        TxtTocRuleDialog(
            currentRuleId = uiState.txtTocRuleId,
            customRules = uiState.txtTocCustomRules,
            thirdPartyRules = uiState.txtTocThirdPartyRules,
            diagnostics = uiState.txtTocDiagnostics,
            isChanging = uiState.isTxtTocChanging,
            backdrop = backdrop,
            onHelp = {
                context.startActivity(Intent(context, TxtTocRuleHelpActivity::class.java))
            },
            onApply = { ruleId ->
                onTocDismiss()
                viewModel.saveTxtTocRuleSelection(ruleId)
            },
            onSaveCustom = { rule ->
                onTocDismiss()
                viewModel.saveTxtTocRule(rule)
            },
            onDismiss = onTocDismiss
        )
    }
}

@Composable
private fun ReaderPageCornerOverlay(
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    rightPageIndex: Int? = null,
    rightChapterIndex: Int? = null,
    currentChapterIndex: Int? = null,
    leftMarginDp: Float,
    rightMarginDp: Float,
    topMarginDp: Float,
    bottomMarginDp: Float,
    topLeft: ReaderCornerContent,
    topRight: ReaderCornerContent,
    bottomLeft: ReaderCornerContent,
    bottomRight: ReaderCornerContent,
    contentColor: Color,
    /** 选字引导提示：与底部两个角信息同一水平线居中显示；null 表示不显示。 */
    selectionHintText: String? = null,
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
                rightChapterIndex = rightChapterIndex,
                currentChapterIndex = currentChapterIndex,
                contentColor = contentColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        start = leftMarginDp.coerceAtLeast(0f).dp,
                        top = topMarginDp.coerceAtLeast(0f).dp,
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
                rightChapterIndex = rightChapterIndex,
                currentChapterIndex = currentChapterIndex,
                contentColor = contentColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = leftMarginDp.coerceAtLeast(0f).dp,
                        end = rightMarginDp.coerceAtLeast(0f).dp,
                        bottom = bottomMarginDp.coerceAtLeast(0f).dp
                    )
            )
        }
        if (selectionHintText != null) {
            // 与底部两个角信息同一水平线（同样的底边距 + 导航栏内边距），水平居中。
            Text(
                text = selectionHintText,
                color = contentColor.copy(alpha = 0.75f),
                fontSize = AppType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = leftMarginDp.coerceAtLeast(0f).dp,
                        end = rightMarginDp.coerceAtLeast(0f).dp,
                        bottom = bottomMarginDp.coerceAtLeast(0f).dp
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
    rightChapterIndex: Int? = null,
    currentChapterIndex: Int? = null,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val leftUsesFullRow = readerLeftCornerUsesFullRow(left, right)
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
                rightChapterIndex = rightChapterIndex,
                currentChapterIndex = currentChapterIndex,
                contentColor = contentColor,
                alignEnd = false
            )
        }
        if (!leftUsesFullRow) {
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
                    rightChapterIndex = rightChapterIndex,
                    currentChapterIndex = currentChapterIndex,
                    contentColor = contentColor,
                    alignEnd = true
                )
            }
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
    rightChapterIndex: Int? = null,
    currentChapterIndex: Int? = null,
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
                    currentPage,
                    rightPageIndex,
                    chapterPageCount,
                    rightChapterIndex,
                    currentChapterIndex
                )
                else -> ""
            },
            color = contentColor,
            fontSize = AppType.Caption,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            // 章节标题可能很长，角落信息区只留一行，超出部分用省略号而非换行。
            maxLines = readerCornerContentMaxLines(content),
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
        controlEdge = true,
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
                imageVector = AppIcons.CaretLeft,
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
    onDismissRequest: () -> Unit,
    onConfirm: (doNotShowAgain: Boolean) -> Unit
) {
    var doNotShowAgain by remember(title) { mutableStateOf(false) }

    LiquidGlassAlertDialog(
        onDismissRequest = onDismissRequest,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = message,
                    color = AppColors.TextSecondary,
                    lineHeight = 22.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { doNotShowAgain = !doNotShowAgain },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = null
                    )
                    Text(
                        text = stringResource(R.string.reader_first_open_hint_do_not_show_again),
                        color = AppColors.TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = confirmText,
                onClick = { onConfirm(doNotShowAgain) },
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
        controlEdge = true,
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
                    imageVector = AppIcons.Check,
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
    supportsHighlightRules: Boolean = false,
    onHighlightRulesClick: () -> Unit = {},
    isTxtBook: Boolean = false,
    bookFormat: String = "",
    lineGuideAvailable: Boolean = false,
    lineGuideEnabled: Boolean = false,
    onLineGuideToggle: () -> Unit = {},
    bionicReadingEnabled: Boolean = false,
    onBionicReadingToggle: () -> Unit = {},
    comicModeEnabled: Boolean = false,
    onComicModeToggle: () -> Unit = {},
    onSwitchComicReader: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onEncodingClick: () -> Unit = {},
    onTocRuleClick: () -> Unit = {}
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    val controlBackground = if (forceSolidButtons) {
        if (contentColor == Color.White) Color(0xFF3A3A3C) else Color(0xFFF2F2F7)
    } else if (contentColor == Color.White) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        Color(0xFFF2F2F7).copy(alpha = 0.8f)
    }
    val menuHost = LocalLiquidGlassMenuHost.current
    val moreMenuId = remember { Any() }
    val isMoreMenuExpanded = menuHost?.activeMenu?.sourceId == moreMenuId
    val currentLineGuideEnabled by rememberUpdatedState(lineGuideEnabled)
    val currentBionicReadingEnabled by rememberUpdatedState(bionicReadingEnabled)
    val currentComicModeEnabled by rememberUpdatedState(comicModeEnabled)
    val currentOnLineGuideToggle by rememberUpdatedState(onLineGuideToggle)
    val currentOnBionicReadingToggle by rememberUpdatedState(onBionicReadingToggle)
    val currentOnComicModeToggle by rememberUpdatedState(onComicModeToggle)
    var moreAnchorBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val moreMenuItems = buildList {
        if (supportsHighlightRules) add(LiquidGlassMenuItem(stringResource(R.string.highlight_rules_title), AppIcons.Code) { onHighlightRulesClick() })
        if (lineGuideAvailable) add(LiquidGlassMenuItem(stringResource(R.string.line_guide), AppIcons.Rows,
            selectedState = { currentLineGuideEnabled }) { currentOnLineGuideToggle() })
        if (bookFormat == "EPUB" || bookFormat == "MOBI") {
            add(LiquidGlassMenuItem(stringResource(R.string.comic_mode), AppIcons.Image,
                selectedState = { currentComicModeEnabled }) { currentOnComicModeToggle() })
        }
        if (bookFormat == "EPUB") {
            add(LiquidGlassMenuItem(stringResource(R.string.epub_comic_switch), AppIcons.Image) { onSwitchComicReader() })
        }
        if (bookFormat in setOf("TXT", "EPUB", "MOBI")) {
            add(LiquidGlassMenuItem(stringResource(R.string.bionic_reading), AppIcons.TextB,
                selectedState = { currentBionicReadingEnabled }) { currentOnBionicReadingToggle() })
        }
        if (isTxtBook) {
            add(LiquidGlassMenuItem(stringResource(R.string.reader_edit), AppIcons.PencilSimple) { onEditClick() })
            add(LiquidGlassMenuItem(stringResource(R.string.reader_switch_encoding), AppIcons.TextAa) { onEncodingClick() })
            add(LiquidGlassMenuItem(stringResource(R.string.reader_txt_toc_rule), AppIcons.Gear) { onTocRuleClick() })
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((82 + 3 * 46).dp)
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
                icon = AppIcons.CaretLeft,
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
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReaderTopBarButton(
                        icon = AppIcons.Headphones,
                        contentDescription = stringResource(R.string.tts_listen),
                        tint = if (isTtsActive) AppColors.Accent else contentColor,
                        backgroundColor = controlBackground,
                        contentScrimColor = glassContentScrimColor,
                        forceSolid = forceSolidButtons,
                        onClick = onTtsClick
                    )
                    ReaderTopBarButton(
                        icon = AppIcons.Bookmark.resolve(isBookmarked),
                        contentDescription = stringResource(R.string.reader_bookmark),
                        tint = if (isBookmarked) AppColors.Accent else contentColor,
                        backgroundColor = controlBackground,
                        contentScrimColor = glassContentScrimColor,
                    forceSolid = forceSolidButtons,
                        onClick = onBookmarkToggle
                    )
                    if (bookFormat in setOf("TXT", "EPUB", "MOBI")) {
                        ReaderTopBarButton(
                            icon = AppIcons.DotsThreeVertical,
                            contentDescription = stringResource(R.string.more_options),
                            tint = if (isMoreMenuExpanded) AppColors.Accent else contentColor,
                            backgroundColor = controlBackground,
                            contentScrimColor = glassContentScrimColor,
                            forceSolid = forceSolidButtons,
                            onClick = {
                                if (menuHost != null && moreAnchorBounds != androidx.compose.ui.geometry.Rect.Zero) {
                                    menuHost.toggle(
                                        LiquidGlassMenuSpec(
                                            anchorBounds = moreAnchorBounds,
                                            sourceId = moreMenuId,
                                            width = 176.dp,
                                            items = moreMenuItems,
                                            anchorCornerRadius = 18.dp,
                                            surfaceColor = bgColor,
                                            contentColor = contentColor,
                                            forceSolid = forceSolidButtons
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .liquidGlassMenuAnchor(moreMenuId)
                                .onGloballyPositioned { moreAnchorBounds = it.boundsInWindow() }
                        )
                    }
                }
                // TXT overflow is rendered by the shared LiquidGlassMenuHost.
            }
        }
    }
}

@Composable
private fun ReaderMoreMenu(
    backgroundColor: Color,
    contentColor: Color,
    glassContentScrimColor: Color,
    forceSolid: Boolean,
    items: List<Triple<ImageVector, String, () -> Unit>>,
    onSelect: ((() -> Unit)) -> Unit
) {
    LiquidGlassSurface(
        controlEdge = true,
        shape = RoundedCornerShape(16.dp),
        fallbackColor = backgroundColor,
        contentScrimColor = glassContentScrimColor,
        forceFallback = forceSolid,
        modifier = Modifier
            .widthIn(min = 190.dp, max = 240.dp)
            .padding(top = 44.dp, end = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            items.forEach { (icon, label, action) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(action) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(
        controlEdge = true,
        shape = CircleShape,
        fallbackColor = backgroundColor,
        contentScrimColor = contentScrimColor,
        forceFallback = forceSolid,
        modifier = modifier
            .requiredSize(36.dp),
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
private fun ReaderLineGuideControl(
    level: Int,
    backgroundColor: Color,
    contentColor: Color,
    onLevelSelected: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuHost = LocalLiquidGlassMenuHost.current
    val menuId = remember { Any() }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    val dimTitle = stringResource(R.string.line_guide_dim)
    val dimLabels = listOf(
        stringResource(R.string.line_guide_dim_none),
        stringResource(R.string.line_guide_dim_low),
        stringResource(R.string.line_guide_dim_medium),
        stringResource(R.string.line_guide_dim_high)
    )
    val closeLabel = stringResource(R.string.line_guide_close)
    Box(modifier = modifier) {
        LiquidGlassSurface(
            controlEdge = true,
            shape = CircleShape,
            fallbackColor = backgroundColor,
            modifier = Modifier.size(44.dp)
                .liquidGlassMenuAnchor(menuId)
                .onGloballyPositioned { anchorBounds = it.boundsInWindow() },
            onClick = {
                if (anchorBounds != Rect.Zero) {
                    menuHost?.toggle(LiquidGlassMenuSpec(
                        anchorBounds = anchorBounds,
                        sourceId = menuId,
                        width = 180.dp,
                        alignEnd = false,
                        preferAbove = true,
                        anchorCornerRadius = 22.dp,
                        items = dimLabels.mapIndexed { index, label ->
                            LiquidGlassMenuItem(label, selected = level == index,
                                groupTitle = if (index == 0) dimTitle else null) {
                                onLevelSelected(index)
                            }
                        } + LiquidGlassMenuItem(closeLabel, AppIcons.X,
                            dividerBefore = true, onClick = onClose),
                        surfaceColor = backgroundColor,
                        contentColor = contentColor
                    ))
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Rows, contentDescription = stringResource(R.string.line_guide),
                tint = contentColor, modifier = Modifier.size(21.dp))
        }
    }
}

/** 底部菜单胶囊入场时长（毫秒） */
private const val READER_MENU_CAPSULE_ENTER_MILLIS = 250

/** 底部三个功能胶囊之间的入场错位（毫秒），只做轻微错开，不串行等待 */
private const val READER_MENU_CAPSULE_STAGGER_MILLIS = 45L

/**
 * 底部菜单单个胶囊入场：淡入与上移同时进行，整体更利落。
 *
 * @param enterDelayMillis 用于胶囊之间的小错位
 */
private suspend fun animateReaderMenuCapsuleIn(
    alpha: Animatable<Float, AnimationVector1D>,
    offset: Animatable<Float, AnimationVector1D>,
    enterDelayMillis: Long = 0L
) {
    if (enterDelayMillis > 0L) delay(enterDelayMillis)
    coroutineScope {
        launch { alpha.animateTo(1f, tween(READER_MENU_CAPSULE_ENTER_MILLIS)) }
        launch {
            offset.animateTo(
                0f,
                tween(READER_MENU_CAPSULE_ENTER_MILLIS, easing = AppEasing.Smooth)
            )
        }
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
    rightChapterIndex: Int? = null,
    currentChapterIndex: Int? = null,
    capsuleBgColor: Color,
    capsuleContentColor: Color,
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
            // 目录胶囊与底部三个功能胶囊同时起步，只在三个功能胶囊之间留一点错位，
            // 避免"上一个出现完下一个才出现"的串行等待。
            launch { animateReaderMenuCapsuleIn(alpha0, offset0) }
            launch { animateReaderMenuCapsuleIn(alpha1, offset1) }
            launch { animateReaderMenuCapsuleIn(alpha2, offset2, READER_MENU_CAPSULE_STAGGER_MILLIS) }
            launch { animateReaderMenuCapsuleIn(alpha3, offset3, READER_MENU_CAPSULE_STAGGER_MILLIS * 2) }
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
            rightChapterIndex = rightChapterIndex,
            currentChapterIndex = currentChapterIndex,
            backgroundColor = capsuleBgColor,
            contentColor = capsuleContentColor,
            glassContentScrimColor = glassContentScrimColor,
            forceSolid = forceSolidCapsules
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha1.value; translationY = offset1.value
            }) {
                ActionCapsule(AppIcons.Bookmark.filled, stringResource(R.string.reader_notes), capsuleBgColor, capsuleContentColor, glassContentScrimColor, forceSolidCapsules, Modifier.fillMaxWidth(), enabled = visible, onBookmarkClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha2.value; translationY = offset2.value
            }) {
                ActionCapsule(AppIcons.MagnifyingGlass, stringResource(R.string.reader_search), capsuleBgColor, capsuleContentColor, glassContentScrimColor, forceSolidCapsules, Modifier.fillMaxWidth(), enabled = visible, onSearchClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha3.value; translationY = offset3.value
            }) {
                ActionCapsule(AppIcons.Gear, stringResource(R.string.reader_theme), capsuleBgColor, capsuleContentColor, glassContentScrimColor, forceSolidCapsules, Modifier.fillMaxWidth(), enabled = visible, onThemeClick)
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
    rightChapterIndex: Int? = null,
    currentChapterIndex: Int? = null,
    backgroundColor: Color,
    contentColor: Color,
    glassContentScrimColor: Color,
    forceSolid: Boolean
) {
    LiquidGlassSurface(
        controlEdge = true,
        shape = RoundedCornerShape(18.dp),
        fallbackColor = backgroundColor,
        contentScrimColor = glassContentScrimColor,
        forceFallback = forceSolid,
        interactive = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chapterTitle,
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatReadingProgressPercent(bookProgressPercent),
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatReaderPageLabel(
                    currentPage,
                    rightPageIndex,
                    chapterPageCount,
                    rightChapterIndex,
                    currentChapterIndex
                ),
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(10.dp))
            ReaderBatteryStatus(contentColor.copy(alpha = 0.72f))
        }
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
    var isPressed by remember { mutableStateOf(false) }
    val capsuleScale by animateFloatAsState(
        targetValue = if (LocalMotionEnabled.current && isPressed) 0.96f else 1f,
        animationSpec = if (LocalMotionEnabled.current) tween(durationMillis = 90) else snap(),
        label = "catalogCapsulePressScale"
    )
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
                controlEdge = true,
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
            controlEdge = true,
            shape = RoundedCornerShape(24.dp),
            fallbackColor = bgColor,
            contentScrimColor = glassContentScrimColor,
            forceFallback = forceSolid,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = capsuleScale
                    scaleY = capsuleScale
                },
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
                Icon(AppIcons.List, contentDescription = null, tint = leftColor, modifier = Modifier.size(18.dp))
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
                            isPressed = true
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
                                isPressed = false
                            }
                        }
                    } else Modifier
                )
        )

        CatalogChapterButton(
            icon = AppIcons.CaretLeft,
            contentDescription = stringResource(R.string.reader_previous_chapter),
            contentColor = if (isLiquidGlass || displayProgress <= 5f) contentColor else Color.White,
            fallbackColor = contentColor.copy(alpha = 0.14f),
            glassContentScrimColor = glassContentScrimColor,
            forceSolid = !isLiquidGlass,
            enabled = enabled && canGoToPreviousChapter,
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onPreviousChapterClick,
            detachWhenDisabled = !enabled
        )
        CatalogChapterButton(
            icon = AppIcons.CaretRight,
            contentDescription = stringResource(R.string.reader_next_chapter),
            contentColor = if (isLiquidGlass || displayProgress <= 95f) contentColor else Color.White,
            fallbackColor = contentColor.copy(alpha = 0.14f),
            glassContentScrimColor = glassContentScrimColor,
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
            controlEdge = true,
            shape = CircleShape,
            fallbackColor = fallbackColor,
            contentScrimColor = glassContentScrimColor,
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
        controlEdge = true,
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

/** 连续滚动列表里章节之间的固定间距（dp）。占位高度要按它换算成正文字高。 */
private const val CONTINUOUS_CHAPTER_GAP_DP = 28f

/** 恢复定位的等待预算：帧预算与时间预算取先到者，避免把列表钉在目标章上。 */
internal const val CONTINUOUS_RESTORE_WAIT_MAX_FRAMES = 45
internal const val CONTINUOUS_RESTORE_WAIT_BUDGET_MS = 1_200L

/** 恢复定位期间允许的补锚次数（目标章被前面变高的章节顶出视口时）。 */
private const val CONTINUOUS_RESTORE_MAX_REANCHORS = 2

/** 判定「量稳了」需要的连续稳定帧数。 */
private const val CONTINUOUS_RESTORE_STABLE_FRAMES = 3

/** 上报当前章节前的稳定等待上限（帧）。 */
private const val CONTINUOUS_REPORT_STABILITY_MAX_FRAMES = 30
private const val CONTINUOUS_REPORT_STABLE_FRAMES = 2

/** 同一章解码失败的重试上限（按章节数 / 排版参数分组，排版变化时重新计数）。 */
private const val CONTINUOUS_CHAPTER_MAX_LOAD_ATTEMPTS = 16

/** 无实测样本时，未解码章节占位高度按视口高度的这个倍数估算。 */
private const val CONTINUOUS_PLACEHOLDER_VIEWPORT_FACTOR = 1.4f
private const val CONTINUOUS_PLACEHOLDER_MIN_VIEWPORT_FACTOR = 0.5f
private const val CONTINUOUS_PLACEHOLDER_MAX_VIEWPORT_FACTOR = 6f

/** 典型章节高度（EMA）的新样本权重，以及写回状态的最小变化量。 */
private const val CONTINUOUS_TYPICAL_HEIGHT_SAMPLE_WEIGHT = 0.35f
private const val CONTINUOUS_TYPICAL_HEIGHT_MIN_DELTA_PX = 24

/**
 * 未解码章节的占位正文高度（不含章间距）。
 *
 * 空条目只有 [CONTINUOUS_CHAPTER_GAP_DP] 高时，一屏能塞下二十多个空章节，任何小幅滚动都会
 * 跨过四五章 —— 章节标题、进度和正文一起乱跳。这里优先用本章上次实测高度（减去章间距），
 * 否则用全书典型高度，再退回按视口高度估算，并做上下限收敛。
 */
internal fun continuousPlaceholderContentHeightPx(
    rememberedTotalHeightPx: Int?,
    typicalChapterHeightPx: Int,
    viewportHeightPx: Int,
    chapterGapPx: Int
): Int {
    val viewport = viewportHeightPx.coerceAtLeast(0)
    if (viewport <= 0) return 0
    val gap = chapterGapPx.coerceAtLeast(0)
    val lowerBound = (viewport * CONTINUOUS_PLACEHOLDER_MIN_VIEWPORT_FACTOR).roundToInt()
    val upperBound = (viewport * CONTINUOUS_PLACEHOLDER_MAX_VIEWPORT_FACTOR).roundToInt()
    val remembered = rememberedTotalHeightPx?.takeIf { it > 0 }?.minus(gap)
    val candidate = when {
        remembered != null && remembered > 0 -> remembered
        typicalChapterHeightPx > 0 -> typicalChapterHeightPx - gap
        else -> (viewport * CONTINUOUS_PLACEHOLDER_VIEWPORT_FACTOR).roundToInt() - gap
    }
    return candidate
        .coerceIn(lowerBound.coerceAtLeast(1), upperBound.coerceAtLeast(lowerBound + 1))
        .coerceAtLeast(1)
}

/** 全书典型章节高度：新实测高度按权重并入，避免单章（整页图）把估算拉跑偏。 */
internal fun continuousTypicalChapterHeight(previousPx: Int, measuredPx: Int): Int {
    if (measuredPx <= 0) return previousPx
    if (previousPx <= 0) return measuredPx
    return (previousPx + (measuredPx - previousPx) * CONTINUOUS_TYPICAL_HEIGHT_SAMPLE_WEIGHT)
        .roundToInt()
}

/** 典型高度写回状态前的最小变化量，避免 ±1 抖动引起无意义的重复重组。 */
internal fun continuousTypicalChapterHeightChangedEnough(previousPx: Int, nextPx: Int): Boolean =
    nextPx != previousPx &&
        kotlin.math.abs(nextPx - previousPx) >= CONTINUOUS_TYPICAL_HEIGHT_MIN_DELTA_PX

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ContinuousScrollReader(
    chapterCount: Int,
    currentChapter: Int,
    initialChapterFraction: Float,
    initialCharacterOffset: Int?,
    fontSize: Float,
    lineHeight: Float,
    letterSpacingDp: Float,
    textAlignment: ReaderTextAlignment,
    readerTypeface: ResolvedReaderTypeface,
    textColor: Int,
    backgroundColor: Int,
    backgroundImagePath: String?,
    backgroundImageOpacity: Float,
    backgroundImageBlurDp: Float,
    marginLeft: Float,
    marginRight: Float,
    marginTop: Float,
    marginBottom: Float,
    paragraphSpacing: Float,
    firstLineIndent: Float,
    bionicReadingEnabled: Boolean,
    lineGuideEnabled: Boolean = false,
    lineGuideDimLevel: Int = 2,
    contentRevision: Long,
    loadChapterText: (Int, Int?) -> CharSequence?,
    loadChapterPreviewText: ((Int, Int?) -> CharSequence?)? = null,
    onContentSizeChanged: (Int, Int) -> Unit,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    searchHighlight: ContinuousSearchHighlight?,
    scrollRequests: MutableStateFlow<ContinuousScrollRequest?>,
    onSearchHighlightFinished: () -> Unit,
    onMenuToggle: () -> Unit,
    onLinkClick: (chapterIndex: Int, href: String, anchorWindowX: Float, anchorWindowY: Float) -> Unit,
    onImageLongPress: (chapterIndex: Int, image: ReaderImageHit) -> Unit,
    selectionController: ContinuousSelectionController,
    onSelectionChanging: () -> Unit,
    onSelection: (chapterIndex: Int, selection: ContinuousTextSelection) -> Unit,
    onChapterVisible: (
        chapterIndex: Int,
        chapterFraction: Float,
        origin: TtsPageChangeOrigin
    ) -> Unit,
    onRestoreComplete: () -> Unit,
    onSentenceDoubleTap: (chapterIndex: Int, characterOffset: Int) -> Unit,
    ttsSentenceJumpEnabled: Boolean,
    chineseMode: String = "original",
    ttsCurrentSentence: TtsSentencePosition? = null,
    comicModeEnabled: Boolean = false,
    imageAdjustments: ReaderImageAdjustments = ReaderImageAdjustments(),
    onSelectionCleared: () -> Unit = {},
    onViewportAnchor: (ContinuousViewportAnchor?) -> Unit = {},
    bodyFontWeight: Int = 400,
    listState: androidx.compose.foundation.lazy.LazyListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = currentChapter.coerceAtLeast(0),
            // Each item is a whole chapter. Item-count prefetch eagerly measures
            // the next chapter on the first drag, even when it is screens away.
            cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 0.35f, behindFraction = 0.25f) }
        )
) {
    if (chapterCount <= 0) return

    val searchHighlightAlpha = remember { Animatable(0f) }
    // 漫画开关只允许纯图片章节铺满，不能让同一本书或下一本书的正文也丢失页边距。
    val chapterGap = if (comicModeEnabled) 0.dp else CONTINUOUS_CHAPTER_GAP_DP.dp
    val imageGap = if (comicModeEnabled) 0.dp else 8.dp
    // 连续滚动的章节正文宽度：漫画取完整视口，普通正文减去左右边距。
    // 章节内插图（ImageSpan）按这个宽度排版，否则解析器会退回“整屏减 44dp”的兜底值，
    // 导致拖动左右边距时图片尺寸不跟随、甚至被边距裁切。
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val selectionViewportBounds = remember { android.graphics.Rect() }
    val readerDensity = LocalDensity.current.density
    val contentWidthPx = if (viewportWidthPx <= 0) {
        0
    } else {
        val horizontalMarginPx = if (comicModeEnabled) 0 else
            ((marginLeft + marginRight) * readerDensity).roundToInt()
        (viewportWidthPx - horizontalMarginPx)
            .coerceAtLeast(1)
    }
    LaunchedEffect(contentWidthPx) {
        onContentSizeChanged(contentWidthPx, 0)
    }
    val textContentWidthPx = (viewportWidthPx - with(LocalDensity.current) {
        marginLeft.dp.roundToPx() + marginRight.dp.roundToPx()
    }).coerceAtLeast(1)
    // 原始章节文本缓存：相邻章节提前拉取，衔接处不再出现“只有标题/空白、松手后突然加载”。
    // 连续模式保留独立章节缓存和图片尺寸规则，文字由共享的 ReaderTextPainter 绘制。
    // 解码失败（null / 空）绝不写入缓存：写空串会让该章整场会话都渲染成空条目、且不再重试。
    val rawChapterTextCache = remember(chapterCount) {
        mutableStateMapOf<Int, CharSequence>()
    }
    // Formatting a chapter adds thousands of bionic spans. Publish it together
    // with the decoded source so lazy composition never does that work on Main.
    val preparedChapterTextCache = remember(chapterCount) {
        mutableStateMapOf<Int, CharSequence>()
    }
    // Keep the last rendered text while a setting change asks the parser for the same
    // chapter at a new width. This avoids replacing the visible list with placeholders.
    val chapterTextLayoutKeys = remember(chapterCount) {
        mutableStateMapOf<Int, ContinuousLayoutKey>()
    }
    val chapterTextComplete = remember(chapterCount) {
        mutableStateMapOf<Int, Boolean>()
    }
    val chapterLayoutKey = ContinuousLayoutKey(contentRevision, contentWidthPx, textContentWidthPx,
        comicModeEnabled, fontSize, lineHeight, letterSpacingDp, paragraphSpacing, firstLineIndent,
        textAlignment, readerTypeface, chineseMode, bionicReadingEnabled, marginTop, marginBottom,
        readerDensity, LocalDensity.current.fontScale)
    val currentChapterLayoutKey by rememberUpdatedState(chapterLayoutKey)
    // Keep the viewport mode stable while the visible chapter is being re-decoded after a
    // setting change. Deriving this directly from a cache that is being rebuilt briefly adds
    // margins, changes the viewport height, and leaves a blank strip until the next scroll.
    var fullBleedViewport by remember { mutableStateOf(false) }
    LaunchedEffect(comicModeEnabled, listState.firstVisibleItemIndex, rawChapterTextCache[listState.firstVisibleItemIndex]) {
        rawChapterTextCache[listState.firstVisibleItemIndex]?.let { text ->
            fullBleedViewport = continuousChapterImages(text).isNotEmpty() &&
                (comicModeEnabled || continuousChapterIsCover(text))
        }
    }
    val chapterTextViews = remember(chapterCount) {
        mutableMapOf<Int, java.lang.ref.WeakReference<ContinuousSelectableTextView>>()
    }
    var guideViewportOrigin by remember { mutableStateOf(Offset.Zero) }
    fun guideTargets(visibleOnly: Boolean): List<Pair<Int, ReaderGuideLine>> {
        val viewport = selectionViewportBounds
        if (viewport.isEmpty) return emptyList()
        return listState.layoutInfo.visibleItemsInfo.flatMap { item ->
            val chapter = item.index
            val view = chapterTextViews[chapter]?.get()?.takeIf { it.isAttachedToWindow }
                ?: return@flatMap emptyList()
            val layout = view.layout ?: return@flatMap emptyList()
            val location = IntArray(2).also(view::getLocationInWindow)
            readableGuideLines(
                view.text, layout,
                location[0] + view.totalPaddingLeft.toFloat(),
                location[0] + view.width - view.totalPaddingRight.toFloat(),
                location[1] + view.totalPaddingTop.toFloat(),
                if (visibleOnly) viewport.top.toFloat() else Float.NEGATIVE_INFINITY,
                if (visibleOnly) viewport.bottom.toFloat() else Float.POSITIVE_INFINITY,
                density = view.resources.displayMetrics.density
            ).map { chapter to it }
        }
    }
    fun focusedGuideLine(): Pair<Int, ReaderGuideLine>? {
        val viewport = selectionViewportBounds
        val anchorY = viewport.top + viewport.height() / 3f
        val lines = guideTargets(true)
        val index = readerGuideFocusedIndex(lines.map { it.second }, anchorY, viewport.height().toFloat())
            ?: return null
        return lines[index]
    }
    // 各章节的实测高度：未解码章节按它预留占位高度，避免空条目只有 28dp 导致一滚跨四五章。
    // 只按章节数重建：改字号 / 改宽度时保留旧高度，列表不会瞬间塌成占位。
    val chapterHeights = remember(chapterCount) { mutableStateMapOf<Int, Int>() }
    var typicalChapterHeightPx by remember(chapterCount) { mutableIntStateOf(0) }
    val chapterGapPx = with(LocalDensity.current) { chapterGap.roundToPx() }
    val imageGapPx = with(LocalDensity.current) { imageGap.roundToPx() }
    val chapterLoadScope = rememberCoroutineScope()
    // EpubParser owns mutable width and ZIP state. Decode requests can be queued in
    // parallel, but the parser call itself must stay serialized so an old width cannot
    // race a new setting and produce a mismatched chapter span.
    val chapterDecodeMutex = remember { Mutex() }
    // ZIP reads are serialized by [chapterDecodeMutex]; cap the surrounding jobs so formatting
    // and cache publication cannot build an unbounded backlog during a fast fling.
    val chapterDecodePermits = remember { Semaphore(2) }
    // 同一章会被「恢复定位 / 预加载 / 条目自身」同时请求；没有去重时 EPUB 会被解析三遍，
    // 先到的那次还可能被 updateReaderContentWidth 清掉解析缓存而返回空章节。
    val inFlightChapterLoads = remember(chapterCount) {
        mutableMapOf<Pair<Int, ContinuousLayoutKey>, Deferred<CharSequence?>>()
    }
    val inFlightChapterUpgrades = remember(chapterCount) {
        mutableMapOf<Pair<Int, ContinuousLayoutKey>, Deferred<Unit>>()
    }
    val failedChapterLoadCounts = remember(chapterCount) {
        mutableMapOf<Pair<Int, ContinuousLayoutKey>, Int>()
    }
    var startFullChapterUpgrade: ((Int, ContinuousLayoutKey) -> Unit)? = null

    /**
     * 解码一章正文并写入缓存，同一 (chapter, 排版参数) 的并发请求共用一次解码。
     *
     * 返回 null 表示这一章暂时没有正文（失败或空章节）；失败次数有上限，
     * 避免不可读的章节在每次视口变化时都重新解码一遍。
     */
    suspend fun loadChapterOnce(index: Int): CharSequence? {
        val layoutKey = chapterLayoutKey
        val requestKey = index to layoutKey
        rawChapterTextCache[index]?.let { cached ->
            if (cached.isNotEmpty() && chapterTextLayoutKeys[index] == layoutKey) {
                if (chapterTextComplete[index] != true) {
                    startFullChapterUpgrade?.invoke(index, layoutKey)
                }
                return cached
            }
        }
        if ((failedChapterLoadCounts[requestKey] ?: 0) >= CONTINUOUS_CHAPTER_MAX_LOAD_ATTEMPTS) return null
        val started = inFlightChapterLoads[requestKey]
            ?: chapterLoadScope.async {
                chapterDecodePermits.withPermit {
                    var returnedPreview = false
                    val loaded = withContext(Dispatchers.IO) {
                        chapterDecodeMutex.withLock {
                            val requestedPreview = loadChapterPreviewText?.invoke(
                                index,
                                contentWidthPx.takeIf { it > 0 }
                            )
                            var loaded = if (!requestedPreview.isNullOrEmpty()) {
                                returnedPreview = true
                                requestedPreview
                            } else {
                                loadChapterText(index, contentWidthPx.takeIf { it > 0 })
                            }
                            // 纯图片仍按整屏宽解码；含正文的插图要按正文列宽重排，避免撑出左右边距。
                            val spanned = loaded as? android.text.Spanned
                            if (comicModeEnabled && textContentWidthPx != contentWidthPx &&
                                spanned != null && continuousChapterImages(spanned).isEmpty() &&
                                spanned.getSpans(0, spanned.length, ImageSpan::class.java).isNotEmpty()
                            ) {
                                val alternatePreview = loadChapterPreviewText?.invoke(index, textContentWidthPx)
                                loaded = if (!alternatePreview.isNullOrEmpty()) {
                                    returnedPreview = true
                                    alternatePreview
                                } else {
                                    loadChapterText(index, textContentWidthPx)
                                }
                            }
                            loaded
                        }
                    }
                    val prepared = loaded?.takeIf { it.isNotEmpty() }?.let { source ->
                        withContext(Dispatchers.Default) {
                            val converted = com.huangder.lumibooks.util.ChineseConverter
                                .convertPreservingSpans(source, chineseMode)
                            wrapReaderImages(continuousSpannableText(converted,
                                bionicReadingEnabled, lineHeight, comicModeEnabled))!!.also {
                                if (comicModeEnabled) sizeContinuousComicImages(it, contentWidthPx,
                                    (marginLeft * readerDensity).roundToInt(), (marginRight * readerDensity).roundToInt())
                            }
                        }
                    }
                    // Publication belongs to the shared job, not a lazily composed item that
                    // can be disposed before await() returns. All state writes stay on Main.
                    if (currentChapterLayoutKey == layoutKey) {
                        if (loaded.isNullOrEmpty()) {
                            failedChapterLoadCounts[requestKey] = (failedChapterLoadCounts[requestKey] ?: 0) + 1
                        } else {
                            failedChapterLoadCounts.remove(requestKey)
                            rawChapterTextCache[index] = loaded
                            preparedChapterTextCache[index] = checkNotNull(prepared)
                            chapterTextLayoutKeys[index] = layoutKey
                            chapterTextComplete[index] = !returnedPreview
                        }
                    }
                    if (returnedPreview && !loaded.isNullOrEmpty() && currentChapterLayoutKey == layoutKey) {
                        startFullChapterUpgrade?.invoke(index, layoutKey)
                    }
                    loaded
                }
            }.also { pending ->
                inFlightChapterLoads[requestKey] = pending
                pending.invokeOnCompletion {
                    chapterLoadScope.launch {
                        if (inFlightChapterLoads[requestKey] === pending) inFlightChapterLoads.remove(requestKey)
                    }
                }
            }
        return try { started.await() } finally {
            if (started.isCompleted && inFlightChapterLoads[requestKey] === started) {
                inFlightChapterLoads.remove(requestKey)
            }
        }
    }

    startFullChapterUpgrade = { index, layoutKey ->
        val requestKey = index to layoutKey
        if (inFlightChapterUpgrades[requestKey] == null) {
          val pending = chapterLoadScope.async {
            chapterDecodePermits.withPermit {
                val loaded = withContext(Dispatchers.IO) {
                    chapterDecodeMutex.withLock {
                        var text = loadChapterText(index, contentWidthPx.takeIf { it > 0 })
                        val spanned = text as? android.text.Spanned
                        if (comicModeEnabled && textContentWidthPx != contentWidthPx &&
                            spanned != null && continuousChapterImages(spanned).isEmpty() &&
                            spanned.getSpans(0, spanned.length, ImageSpan::class.java).isNotEmpty()
                        ) {
                            text = loadChapterText(index, textContentWidthPx)
                        }
                        text
                    }
                }
                val prepared = loaded?.takeIf { it.isNotEmpty() }?.let { source ->
                    withContext(Dispatchers.Default) {
                        val converted = com.huangder.lumibooks.util.ChineseConverter
                            .convertPreservingSpans(source, chineseMode)
                        wrapReaderImages(continuousSpannableText(converted,
                            bionicReadingEnabled, lineHeight, comicModeEnabled))!!.also {
                            if (comicModeEnabled) sizeContinuousComicImages(it, contentWidthPx,
                                (marginLeft * readerDensity).roundToInt(), (marginRight * readerDensity).roundToInt())
                        }
                    }
                }
                if (currentChapterLayoutKey == layoutKey && !loaded.isNullOrEmpty() && prepared != null) {
                    rawChapterTextCache[index] = loaded
                    preparedChapterTextCache[index] = prepared
                    chapterTextLayoutKeys[index] = layoutKey
                    chapterTextComplete[index] = true
                }
            }
          }.also { pending ->
              inFlightChapterUpgrades[requestKey] = pending
              pending.invokeOnCompletion {
                  chapterLoadScope.launch {
                      if (inFlightChapterUpgrades[requestKey] === pending) inFlightChapterUpgrades.remove(requestKey)
                  }
                }
            }
        }
    }

    val restoreTarget = remember(chapterCount) {
        currentChapter.coerceIn(0, chapterCount - 1)
    }
    val restoreFraction = remember(chapterCount) {
        initialChapterFraction.coerceIn(0f, 0.9999f)
    }
    val restoreCharacterOffset = remember(chapterCount) { initialCharacterOffset }

    // Decode the restored chapter first. Starting the neighbors in the same frame lets them
    // occupy the decode permits while the only chapter that can paint the first viewport is
    // still parsing, which makes the reader look empty on a cold open.
    LaunchedEffect(chapterLayoutKey, contentWidthPx, restoreTarget) {
        if (contentWidthPx <= 0) return@LaunchedEffect
        loadChapterOnce(restoreTarget)
        sequenceOf(
            restoreTarget - 1,
            restoreTarget + 1,
            restoreTarget + 2,
        ).filter { it in 0 until chapterCount }.forEach { chapter ->
            chapterLoadScope.launch { loadChapterOnce(chapter) }
        }
    }

    var initialRestoreCompleted by remember(chapterCount) { mutableStateOf(false) }
    var isRestoringPosition by remember { mutableStateOf(false) }
    var restoredLayoutKey by remember { mutableStateOf<ContinuousLayoutKey?>(null) }
    var consumedRequestId by remember { mutableStateOf<Long?>(null) }
    var userScrollPending by remember { mutableStateOf(false) }
    // 用户拖动计数（开始 / 结束都计数）：恢复定位一旦发现它变化就让位给用户，
    // 不再把位置拉回去；纯滚动模式下也用它决定是否还允许把位置强拉回恢复点。
    var userScrollGeneration by remember { mutableIntStateOf(0) }
    var userDragging by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    selectionController.clear()
                    userDragging = true
                    userScrollPending = true
                    userScrollGeneration++
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    userDragging = false
                    userScrollGeneration++
                }
                else -> Unit
            }
        }
    }
    val requestedPosition by scrollRequests.collectAsState()

    /** 首个跨过视口顶边的章节条目（index, offset, size）。 */
    fun firstVisibleItem(): Triple<Int, Int, Int>? =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.offset + item.size > 0 }
            ?.let { item -> Triple(item.index, item.offset, item.size) }

    val latestOnViewportAnchor by rememberUpdatedState(onViewportAnchor)
    LaunchedEffect(listState, chapterLayoutKey) {
        snapshotFlow {
            if (isRestoringPosition) null else firstVisibleItem()?.let { (index, offset, _) ->
                val text = rawChapterTextCache[index] ?: return@let null
                val view = chapterTextViews[index]?.get()
                val fullWidth = continuousChapterImages(text).isNotEmpty() &&
                    (comicModeEnabled || continuousChapterIsCover(text))
                captureContinuousViewportAnchor(index, offset, text, view?.layout,
                    if (fullWidth) viewportWidthPx else contentWidthPx, imageGapPx, if (comicModeEnabled) 0 else viewportHeightPx)
            }
        }.distinctUntilChanged().collect { latestOnViewportAnchor(it) }
    }

    // Captured during composition, before AndroidView receives the new font/text/width.
    // A setting reflow may only restore this anchor while the user has not moved again.
    val reflowSnapshot = remember(chapterLayoutKey) {
        val anchor = firstVisibleItem()?.let { (index, offset, _) ->
            val view = chapterTextViews[index]?.get()
            val text = view?.text ?: rawChapterTextCache[index] ?: return@let null
            val fullWidth = continuousChapterImages(text).isNotEmpty() &&
                (restoredLayoutKey?.comic == true || continuousChapterIsCover(text))
            captureContinuousViewportAnchor(index, offset, text, view?.layout,
                if (fullWidth) viewportWidthPx else restoredLayoutKey?.imageWidth ?: contentWidthPx,
                if (restoredLayoutKey?.comic == true) 0 else (8 * readerDensity).roundToInt(),
                if (restoredLayoutKey?.comic == true) 0 else viewportHeightPx)
        }
        anchor to userScrollGeneration
    }

    /**
     * 等待目标章节量出真实高度。
     *
     * 旧实现在等待期间每帧都 `scrollToItem(target)`，会把列表钉在目标章上最长 300 帧（约 5 秒），
     * 用户表现为“完全滚不动”、松手后又被拉回去。现在是有界等待（帧预算 + 时间预算），
     * 补锚最多两次，且用户一开始拖动就立刻放弃。
     */
    suspend fun awaitStableChapterMeasurement(
        target: Int,
        startUserScrollGeneration: Int
    ): androidx.compose.foundation.lazy.LazyListItemInfo? {
        var lastSize = -1
        var stableFrames = 0
        var reAnchors = 0
        var frames = 0
        val deadlineNanos = System.nanoTime() + CONTINUOUS_RESTORE_WAIT_BUDGET_MS * 1_000_000L
        while (frames < CONTINUOUS_RESTORE_WAIT_MAX_FRAMES && System.nanoTime() < deadlineNanos) {
            withFrameNanos { }
            frames++
            if (userScrollGeneration != startUserScrollGeneration) return null
            val item = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == target && it.size > 0 }
            if (item == null) {
                // Chapters before the target can expand after their placeholders load and push the
                // target out of the viewport. Re-anchor a bounded number of times, and never while
                // the user is dragging — otherwise the list fights the finger.
                if (!userDragging && reAnchors < CONTINUOUS_RESTORE_MAX_REANCHORS) {
                    reAnchors++
                    listState.scrollToItem(target)
                }
                lastSize = -1
                stableFrames = 0
                continue
            }
            // Prefetch and visible items publish into the same cache; no item-local
            // loaded flag may hold up restoration after its producing view was disposed.
            if (rawChapterTextCache[target].isNullOrEmpty()) {
                lastSize = -1
                stableFrames = 0
                continue
            }
            if (item.size == lastSize) {
                stableFrames++
            } else {
                lastSize = item.size
                stableFrames = 1
            }
            val textView = chapterTextViews[target]?.get()
            val textMeasured = textView != null && textView.layout != null && !textView.isLayoutRequested
            val imagesMeasured = rawChapterTextCache[target]?.let { continuousChapterImages(it).isNotEmpty() } == true
            if (stableFrames >= CONTINUOUS_RESTORE_STABLE_FRAMES && (textMeasured || imagesMeasured)) {
                return item
            }
        }
        // Leave the coarse placement alone, but never report an unconfirmed position.
        return null
    }

    /** 把目标章节锚到 TOP，再按字符锚点 / 章内比例落到具体位置，返回落点比例。 */
    suspend fun scrollToPosition(
        target: Int,
        fraction: Float,
        characterOffset: Int?,
        startUserScrollGeneration: Int
    ): Float? {
        // Decode before waiting for frames: slow chapters must not consume the restore while they
        // still show a placeholder, or lose their load when preceding chapters push them offscreen.
        // 等待仍然有界（帧预算）：解码在共享 scope 里继续跑，稍后照样会落进缓存，
        // 卡住 / 极慢的章节不能把恢复流程挂死。
        if (rawChapterTextCache[target].isNullOrEmpty()) {
            chapterLoadScope.launch { loadChapterOnce(target) }
            var decodeFrames = 0
            while (decodeFrames < CONTINUOUS_RESTORE_WAIT_MAX_FRAMES &&
                rawChapterTextCache[target].isNullOrEmpty()
            ) {
                if (userScrollGeneration != startUserScrollGeneration) return null
                withFrameNanos { }
                decodeFrames++
            }
        }
        // 用户在解码期间自己滚了：立刻让位，不再把位置拉到目标章。
        if (userScrollGeneration != startUserScrollGeneration) return null
        listState.scrollToItem(target)
        val item = awaitStableChapterMeasurement(target, startUserScrollGeneration) ?: return null
        val textOffset = characterOffset?.let { offset ->
            chapterTextViews[target]?.get()?.layout?.let { continuousCharacterTop(it, offset) }
                ?: rawChapterTextCache[target]?.let { text ->
                    continuousImageCharacterTop(text, contentWidthPx, imageGapPx, offset)
                }
        }
        val scrollOffset = (textOffset?.toFloat() ?: (item.size * fraction))
            .coerceIn(0f, (item.size - 1).coerceAtLeast(0).toFloat())
        // A single absolute placement also works for a target several viewports into a chapter.
        listState.scrollToItem(target, scrollOffset.roundToInt())
        return (scrollOffset / item.size.coerceAtLeast(1)).coerceIn(0f, 0.9999f)
    }

    LaunchedEffect(requestedPosition, chapterCount, contentWidthPx) {
        if (contentWidthPx <= 0) return@LaunchedEffect
        val request = requestedPosition
        if (request != null && request.requestId == consumedRequestId) {
            scrollRequests.compareAndSet(request, null)
            return@LaunchedEffect
        }
        if (request == null && initialRestoreCompleted) return@LaunchedEffect
        if (request != null) consumedRequestId = request.requestId
        val target = (request?.chapterIndex ?: restoreTarget).coerceIn(0, chapterCount - 1)
        val fraction = (request?.chapterFraction ?: restoreFraction).coerceIn(0f, 0.9999f)
        // 初始恢复到进度时，用户已经自己滚过就不再强拉回恢复点（改排版、改字号会重跑本 effect）。
        val canForceScroll = request != null || userScrollGeneration == 0
        val startUserScrollGeneration = userScrollGeneration
        var reached: Float? = null
        try {
            if (canForceScroll) {
                isRestoringPosition = true
                reached = scrollToPosition(
                    target,
                    fraction,
                    if (request != null) request.characterOffset else restoreCharacterOffset,
                    startUserScrollGeneration
                )
            }
            reached?.let { value ->
                onChapterVisible(target, value, request?.origin ?: TtsPageChangeOrigin.LAYOUT)
            }
        } finally {
            isRestoringPosition = false
            // Also consume on cancellation (a width change or a newer request), otherwise
            // a stopped navigation can be replayed as an initial restore after finger-up.
            initialRestoreCompleted = true
            restoredLayoutKey = chapterLayoutKey
            onRestoreComplete()
            if (request != null) scrollRequests.compareAndSet(request, null)
        }
    }

    val reflowAnchor = reflowSnapshot.first
    LaunchedEffect(chapterLayoutKey, reflowAnchor?.let { chapterTextLayoutKeys[it.chapter] }, initialRestoreCompleted) {
        if (!initialRestoreCompleted || restoredLayoutKey == chapterLayoutKey) return@LaunchedEffect
        val anchor = reflowAnchor ?: return@LaunchedEffect
        if (requestedPosition != null || userScrollGeneration != reflowSnapshot.second || listState.isScrollInProgress) {
            restoredLayoutKey = chapterLayoutKey
            return@LaunchedEffect
        }
        // Keep old pixels and position until this exact layout is decoded. A late result
        // re-enters this effect; the generation check gives any intervening drag priority.
        if (chapterTextLayoutKeys[anchor.chapter] != chapterLayoutKey) return@LaunchedEffect
        isRestoringPosition = true
        try {
            var previousSize = -1
            var stableFrames = 0
            var reAnchors = 0
            for (frame in 0 until CONTINUOUS_RESTORE_WAIT_MAX_FRAMES) {
                withFrameNanos { }
                if (userScrollGeneration != reflowSnapshot.second || requestedPosition != null) return@LaunchedEffect
                val view = chapterTextViews[anchor.chapter]?.get()
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor.chapter }
                if (item == null) {
                    // A large font/image shrink can move the anchor's chapter completely
                    // out of view. Compose it again before resolving its character geometry.
                    if (reAnchors >= CONTINUOUS_RESTORE_MAX_REANCHORS) return@LaunchedEffect
                    reAnchors++
                    listState.scrollToItem(anchor.chapter)
                    previousSize = -1
                    stableFrames = 0
                    continue
                }
                if (view?.isLayoutRequested == true || item.size != previousSize) stableFrames = 0 else stableFrames++
                previousSize = item.size
                if (stableFrames < 2) continue
                val text = view?.text ?: rawChapterTextCache[anchor.chapter] ?: return@LaunchedEffect
                val fullWidth = continuousChapterImages(text).isNotEmpty() &&
                    (comicModeEnabled || continuousChapterIsCover(text))
                val offset = continuousViewportAnchorOffset(anchor, text, view?.layout,
                    if (fullWidth) viewportWidthPx else contentWidthPx, imageGapPx, if (comicModeEnabled) 0 else viewportHeightPx)
                    ?: return@LaunchedEffect
                listState.scrollToItem(anchor.chapter, offset.coerceAtLeast(0))
                restoredLayoutKey = chapterLayoutKey
                break
            }
        } finally { isRestoringPosition = false }
    }

    LaunchedEffect(chapterLayoutKey) {
        if (contentWidthPx <= 0) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            listOf(index, index - 1, index + 1, index + 2)
                .filter { it in 0 until chapterCount }
                .forEach { neighbor -> chapterLoadScope.launch { loadChapterOnce(neighbor) } }
        }
    }

    /**
     * 等视口静下来（首个可见条目连续 [CONTINUOUS_REPORT_STABLE_FRAMES] 帧不变）再取位置。
     * 章节陆续解码时条目高度会跳变，直接上报会把“正在加载的邻居”报成当前章节。
     */
    suspend fun awaitStableViewportItem(): Triple<Int, Int, Int>? {
        var lastIndex = -1
        var lastOffset = Int.MIN_VALUE
        var lastSize = -1
        var stableFrames = 0
        repeat(CONTINUOUS_REPORT_STABILITY_MAX_FRAMES) {
            withFrameNanos { }
            val item = firstVisibleItem()
            if (item == null) {
                lastIndex = -1
                lastOffset = Int.MIN_VALUE
                lastSize = -1
                stableFrames = 0
                return@repeat
            }
            // A finger-up event is emitted before LazyColumn's fling settles. Do not
            // publish a position while the list is still moving, or the outer state can
            // immediately feed the old chapter-start position back into the reader.
            if (listState.isScrollInProgress) {
                lastIndex = item.first
                lastOffset = item.second
                lastSize = item.third
                stableFrames = 0
                return@repeat
            }
            val (index, offset, size) = item
            if (index == lastIndex && offset == lastOffset && size == lastSize) {
                stableFrames++
            } else {
                lastIndex = index
                lastOffset = offset
                lastSize = size
                stableFrames = 1
            }
            if (stableFrames >= CONTINUOUS_REPORT_STABLE_FRAMES) return item
        }
        return null
    }

    LaunchedEffect(listState, chapterCount, contentRevision, contentWidthPx, textContentWidthPx) {
        if (contentWidthPx <= 0) return@LaunchedEffect
        snapshotFlow {
            Triple(
                userDragging,
                isRestoringPosition,
                listState.isScrollInProgress to firstVisibleItem()
            )
        }.distinctUntilChanged().collectLatest { viewport ->
            val (dragging, restoring, scrollingAndItem) = viewport
            val (scrolling, item) = scrollingAndItem
            item ?: return@collectLatest
            if (dragging) {
                userScrollPending = true
                return@collectLatest
            }
            // DragInteraction.Stop arrives before the inertial fling finishes. Keep the
            // pending user scroll alive until LazyColumn reports that scrolling stopped.
            if (scrolling) return@collectLatest
            // 恢复定位还没收尾时不上报：否则恢复过程本身会被记成“用户翻了章节”。
            if (!userScrollPending || restoring) return@collectLatest
            val stable = awaitStableViewportItem() ?: return@collectLatest
            val (stableIndex, stableOffset, stableSize) = stable
            if (stableIndex !in 0 until chapterCount ||
                rawChapterTextCache[stableIndex].isNullOrEmpty() ||
                stableSize <= 0
            ) {
                return@collectLatest
            }
            userScrollPending = false
            val fraction = (-stableOffset).toFloat().div(stableSize).coerceIn(0f, 0.9999f)
            onChapterVisible(stableIndex, fraction, TtsPageChangeOrigin.USER)
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

    Box(Modifier.fillMaxSize().background(Color(backgroundColor))
        .onGloballyPositioned { guideViewportOrigin = it.positionInWindow() }) {
        if (!backgroundImagePath.isNullOrBlank()) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        alpha = backgroundImageOpacity.coerceIn(0f, 1f)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val radius = backgroundImageBlurDp.coerceIn(0f, 40f) *
                                resources.displayMetrics.density
                            setRenderEffect(
                                if (radius >= 0.5f) android.graphics.RenderEffect.createBlurEffect(
                                    radius,
                                    radius,
                                    android.graphics.Shader.TileMode.CLAMP
                                ) else null
                            )
                        }
                        load(java.io.File(backgroundImagePath))
                    }
                },
                update = { imageView ->
                    imageView.alpha = backgroundImageOpacity.coerceIn(0f, 1f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = backgroundImageBlurDp.coerceIn(0f, 40f) *
                            imageView.resources.displayMetrics.density
                        imageView.setRenderEffect(
                            if (radius >= 0.5f) android.graphics.RenderEffect.createBlurEffect(
                                radius,
                                radius,
                                android.graphics.Shader.TileMode.CLAMP
                            ) else null
                        )
                    }
                    imageView.load(java.io.File(backgroundImagePath))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewportWidthPx = size.width
                    viewportHeightPx = size.height
                }
                // 上下边距属于可视区域：contentPadding 只在整份列表的首尾留白，
                // 滚进章节中段后正文仍会覆盖页眉/页脚。缩小并裁剪视口才能始终保留它们。
                .padding(
                    top = if (fullBleedViewport) 0.dp else marginTop.dp,
                    bottom = if (fullBleedViewport) 0.dp else marginBottom.dp
                )
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    selectionViewportBounds.set(bounds.left.roundToInt(), bounds.top.roundToInt(),
                        bounds.right.roundToInt(), bounds.bottom.roundToInt())
                }
                .clipToBounds()
                .pointerInput(lineGuideEnabled) {
                    detectTapGestures(onTap = { tap ->
                        if (!lineGuideEnabled) {
                            onMenuToggle()
                        } else {
                            val current = focusedGuideLine() ?: return@detectTapGestures
                            val all = guideTargets(false)
                            val index = all.indexOfFirst {
                                it.first == current.first &&
                                    it.second.startOffset == current.second.startOffset
                            }
                            val tapWindowY = selectionViewportBounds.top + tap.y
                            val direction = if (tapWindowY < current.second.bounds.centerY()) -1 else 1
                            val next = readerGuideStepIndex(index, all.size, direction)
                                ?.let(all::get)
                            val distance = readerGuideScrollDistance(
                                current.second.bounds.centerY(),
                                next?.second?.bounds?.centerY(),
                                current.second.bounds.height(), direction
                            )
                            chapterLoadScope.launch { listState.scrollBy(distance) }
                        }
                    })
                }
        ) {
        items(chapterCount, key = { it }) { chapterIndex ->
            val isLoaded = !preparedChapterTextCache[chapterIndex].isNullOrEmpty()
            LaunchedEffect(chapterIndex, chapterLayoutKey) {
                if (contentWidthPx <= 0) return@LaunchedEffect
                repeat(CONTINUOUS_CHAPTER_MAX_LOAD_ATTEMPTS) { attempt ->
                    if (!loadChapterOnce(chapterIndex).isNullOrEmpty()) return@LaunchedEffect
                    delay(250L * (attempt + 1).coerceAtMost(4))
                }
            }
            // The shared cache is the only display source, including late prefetch results.
            val selectableText = preparedChapterTextCache[chapterIndex] ?: ""
            val continuousImages = remember(selectableText) {
                continuousChapterImages(selectableText)
            }
            val coverChapter = continuousImages.isNotEmpty() && continuousChapterIsCover(selectableText)
            val mixedComicChapter = comicModeEnabled && continuousImages.isEmpty() &&
                (selectableText as? Spanned)?.getSpans(0, selectableText.length, ContinuousComicImageSpan::class.java)?.isNotEmpty() == true
            val fullBleedChapter = continuousImages.isNotEmpty() &&
                (comicModeEnabled || coverChapter) || mixedComicChapter
            val itemChapterGap = if (fullBleedChapter) 0.dp else CONTINUOUS_CHAPTER_GAP_DP.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 只记录真正渲染出正文的高度：占位高度不能反过来当成测量结果，否则会自我放大。
                    .onSizeChanged { size ->
                        if (!isLoaded || size.height <= 0) return@onSizeChanged
                        if (chapterHeights[chapterIndex] != size.height) {
                            chapterHeights[chapterIndex] = size.height
                        }
                        val nextTypical = continuousTypicalChapterHeight(
                            previousPx = typicalChapterHeightPx,
                            measuredPx = size.height
                        )
                        if (continuousTypicalChapterHeightChangedEnough(
                                typicalChapterHeightPx,
                                nextTypical
                            )
                        ) {
                            typicalChapterHeightPx = nextTypical
                        }
                    }
                    // 逐章决定左右边距：纯图片接缝保持连续，文字与图文混排遵守正文设置。
                    .padding(
                        start = if (fullBleedChapter) 0.dp else marginLeft.dp,
                        end = if (fullBleedChapter) 0.dp else marginRight.dp,
                        bottom = itemChapterGap
                    )
            ) {
                if (!isLoaded) {
                    // 未解码章节按典型高度占位。空条目只有 28dp 时，一屏能塞下二十多章，
                    // 任何小幅滚动都会跨过四五章：章节标题 / 进度与正文一起乱跳；
                    // 可见区域全是空条目时列表总高度接近视口，还会表现为“完全滚不动”。
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(
                                with(LocalDensity.current) {
                                    continuousPlaceholderContentHeightPx(
                                        rememberedTotalHeightPx = chapterHeights[chapterIndex],
                                        typicalChapterHeightPx = typicalChapterHeightPx,
                                        viewportHeightPx = viewportHeightPx,
                                        chapterGapPx = chapterGapPx
                                    ).toDp()
                                }
                            )
                    )
                } else if (continuousImages.isNotEmpty()) {
                    // Reuse EPUB-resolved drawables, including SVG and failure placeholders.
                    // Never discard novel text merely because comic mode was enabled globally.
                    if (chapterIndex == 0 && coverChapter && !comicModeEnabled && continuousImages.size == 1 && viewportHeightPx > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = with(LocalDensity.current) { viewportHeightPx.toDp() }),
                            contentAlignment = Alignment.Center
                        ) {
                            ContinuousSingleImage(
                                chapterIndex = chapterIndex,
                                imageSpan = continuousImages.single(),
                                imageAdjustments = imageAdjustments,
                                onReaderTap = { if (!lineGuideEnabled) onMenuToggle() },
                                onImageLongPress = { chapter, image ->
                                    if (!lineGuideEnabled) onImageLongPress(chapter, image)
                                }
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(imageGap)) {
                            continuousImages.forEach { imageSpan ->
                                ContinuousSingleImage(
                                    chapterIndex = chapterIndex,
                                    imageSpan = imageSpan,
                                    imageAdjustments = imageAdjustments,
                                    onReaderTap = { if (!lineGuideEnabled) onMenuToggle() },
                                    onImageLongPress = { chapter, image ->
                                        if (!lineGuideEnabled) onImageLongPress(chapter, image)
                                    }
                                )
                            }
                        }
                    }
                } else {
                AndroidView(
                    factory = { context ->
                        ContinuousSelectableTextView(context).apply {
                            breakStrategy = textAlignment.readerBreakStrategy()
                            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                        }
                    },
                    update = { textView ->
                        chapterTextViews[chapterIndex] = java.lang.ref.WeakReference(textView)
                        textView.readerImageBleed = mixedComicChapter
                        val textLeft = if (mixedComicChapter) (marginLeft * readerDensity).roundToInt() else 0
                        val textRight = if (mixedComicChapter) (marginRight * readerDensity).roundToInt() else 0
                        if (textView.paddingLeft != textLeft || textView.paddingRight != textRight) {
                            textView.setPadding(textLeft, 0, textRight, 0)
                        }
                        textView.lineGuideMode = lineGuideEnabled
                        textView.onReaderTap = onMenuToggle
                        textView.savedAnnotations = notes.filter { it.chapterIndex == chapterIndex }
                        textView.ttsJumpEnabled = ttsSentenceJumpEnabled
                        textView.onSentenceDoubleTap = { characterOffset ->
                            onSentenceDoubleTap(chapterIndex, characterOffset)
                        }
                        textView.onLinkTap = { href, tapX, tapY ->
                            val location = IntArray(2)
                            textView.getLocationInWindow(location)
                            onLinkClick(chapterIndex, href, location[0] + tapX, location[1] + tapY)
                        }
                        textView.onImageLongPress = { image -> onImageLongPress(chapterIndex, image) }
                        textView.onSelectionChanging = onSelectionChanging
                        textView.onReaderSelectionRangeChanged = { start, end ->
                            selectionController.update(textView, chapterIndex, start, end)
                        }
                        textView.onReaderSelectionCleared = {
                            if (selectionController.activeView === textView) {
                                selectionController.released(textView)
                                onSelectionCleared()
                            }
                        }
                        textView.onExplicitReveal = { rect ->
                            val visible = android.graphics.Rect()
                            if (textView.getLocalVisibleRect(visible)) {
                                val delta = when {
                                    rect.top < visible.top -> rect.top - visible.top
                                    rect.bottom > visible.bottom -> rect.bottom - visible.bottom
                                    else -> 0
                                }
                                if (delta != 0) chapterLoadScope.launch { listState.scrollBy(delta.toFloat()) }
                            }
                            true
                        }
                        textView.onAccessibilityScroll = { forward ->
                            chapterLoadScope.launch { listState.scrollBy(viewportHeightPx * if (forward) 0.8f else -0.8f) }
                            true
                        }
                        textView.onSelectionEdgeScroll = { delta ->
                            userScrollPending = true
                            userScrollGeneration++
                            chapterLoadScope.launch {
                                if (textView.readerDraggingStartHandle != null) listState.scrollBy(delta)
                            }
                        }
                        textView.onSelectionViewport = { rect ->
                            val location = IntArray(2).also(textView::getLocationInWindow)
                            rect.set(selectionViewportBounds)
                            rect.offset(-location[0], -location[1])
                            rect.intersect(0, 0, textView.width, textView.height)
                        }
                        textView.onReaderSelection = { selection ->
                            selectionController.update(textView, chapterIndex, selection.start, selection.end)
                            onSelection(chapterIndex, selection)
                        }
                        if (textView.currentTextColor != textColor) textView.setTextColor(textColor)
                        if (textView.paint.isFakeBoldText != readerTypeface.fakeBold) {
                            textView.paint.isFakeBoldText = readerTypeface.fakeBold
                            textView.invalidate()
                        }
                        if (textView.lineSpacingMultiplier != 1f || textView.lineSpacingExtra != 0f) {
                            textView.setLineSpacing(0f, 1f)
                        }
                        if (textView.typeface != readerTypeface.typeface) textView.typeface = readerTypeface.typeface
                        val fontSizePx = android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_SP,
                            fontSize,
                            textView.resources.displayMetrics
                        )
                        if (textView.textSize != fontSizePx) {
                            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, fontSizePx)
                        }
                        val letterSpacing = if (fontSizePx > 0f) {
                            (letterSpacingDp * textView.resources.displayMetrics.density / fontSizePx)
                                // Keep the full reader setting range effective even
                                // at small sizes; the slider tops out below 1em.
                                .coerceIn(-1f, 1f)
                        } else {
                            0f
                        }
                        if (textView.letterSpacing != letterSpacing) textView.letterSpacing = letterSpacing
                        val breakStrategy = textAlignment.readerBreakStrategyForText(selectableText)
                        if (textView.breakStrategy != breakStrategy) {
                            textView.breakStrategy = breakStrategy
                        }
                        val hyphenation = readerHyphenationFrequency(selectableText)
                        if (textView.hyphenationFrequency != hyphenation) {
                            textView.hyphenationFrequency = hyphenation
                        }
                        val justification = textAlignment.readerJustificationForText(selectableText)
                        if (textView.justificationMode != justification) {
                            textView.justificationMode = justification
                        }
                        textView.readerJustificationMode = justification
                        textView.setReaderText(selectableText)
                        val chapterNotes = notes.filter { it.chapterIndex == chapterIndex }
                        val highlight = searchHighlight?.takeIf { it.chapterIndex == chapterIndex }
                        val sentence = ttsCurrentSentence?.takeIf { it.chapterIndex == chapterIndex }
                        textView.updateReaderAnnotations(listOf(chapterNotes, highlight, searchHighlightAlpha.value, sentence, backgroundColor)) { liveText ->
                            updateContinuousAnnotations(liveText, chapterNotes, highlight, searchHighlightAlpha.value,
                                sentence, backgroundColor)
                        }
                        updateReaderImages(textView.text, imageAdjustments, chapterLoadScope) { textView.invalidate() }
                    },
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                        chapterTextViews[chapterIndex]?.get()?.updateReaderDrawingViewport()
                    }
                )
                }
            }
        }
        }
        if (lineGuideEnabled) {
            Canvas(Modifier.fillMaxSize()) {
                val focused = focusedGuideLine() ?: return@Canvas
                val band = focused.second.bounds
                val sideInset = 8.dp.toPx()
                val left = (selectionViewportBounds.left + sideInset - guideViewportOrigin.x)
                    .coerceIn(0f, size.width)
                val right = (selectionViewportBounds.right - sideInset - guideViewportOrigin.x)
                    .coerceIn(left, size.width)
                val top = (band.top - guideViewportOrigin.y).coerceIn(0f, size.height)
                val bottom = (band.bottom - guideViewportOrigin.y).coerceIn(top, size.height)
                drawIntoCanvas { canvas ->
                    drawReaderGuideOverlay(canvas.nativeCanvas, size.width, size.height,
                        android.graphics.RectF(left, top, right, bottom),
                        readerGuideShadeColor(backgroundColor, lineGuideDimLevel), density)
                }
            }
        }
    }
}

/** 单图章节不用 TextView 的一行高度测量，按图片真实宽高比参与连续列表布局。 */
@Composable
private fun ContinuousSingleImage(
    chapterIndex: Int,
    imageSpan: ImageSpan,
    imageAdjustments: ReaderImageAdjustments,
    onReaderTap: () -> Unit,
    onImageLongPress: (chapterIndex: Int, image: ReaderImageHit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawable = remember(imageSpan.drawable) {
        imageSpan.drawable as? AdjustedReaderDrawable ?: AdjustedReaderDrawable(imageSpan.drawable)
    }
    val imageWidth = drawable.bounds.width().takeIf { it > 0 }
        ?: drawable.intrinsicWidth.coerceAtLeast(1)
    val imageHeight = drawable.bounds.height().takeIf { it > 0 }
        ?: drawable.intrinsicHeight.coerceAtLeast(1)
    val ratio = imageWidth.toFloat() / imageHeight.toFloat()

    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                adjustViewBounds = false
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { imageView ->
            if (imageView.drawable !== drawable) imageView.setImageDrawable(drawable)
            drawable.update(imageAdjustments, scope) { imageView.invalidate() }
            imageView.setOnClickListener { onReaderTap() }
            imageView.setOnLongClickListener {
                val location = IntArray(2)
                imageView.getLocationInWindow(location)
                onImageLongPress(
                    chapterIndex,
                    ReaderImageHit(
                        source = imageSpan.source.orEmpty(),
                        leftPx = location[0].toFloat(),
                        topPx = location[1].toFloat(),
                        rightPx = location[0] + imageView.width.toFloat(),
                        bottomPx = location[1] + imageView.height.toFloat(),
                        naturalWidth = drawable.intrinsicWidth.coerceAtLeast(imageWidth),
                        naturalHeight = drawable.intrinsicHeight.coerceAtLeast(imageHeight),
                        link = null,
                        hasAction = false
                    )
                )
                true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
    )
}

private fun continuousSpannableText(
    text: CharSequence?,
    bionicReadingEnabled: Boolean,
    lineHeight: Float,
    comicModeEnabled: Boolean = false
): SpannableStringBuilder {
    val content = SpannableStringBuilder(
        BionicReadingFormatter.format(prepareReaderEnglishHyphenation(text ?: ""), bionicReadingEnabled)
    )
    protectContinuousImageHeights(content, lineHeight, comicModeEnabled)
    return content
}

internal fun updateContinuousAnnotations(
    content: Spannable,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    searchHighlight: ContinuousSearchHighlight?,
    searchHighlightAlpha: Float,
    ttsCurrentSentence: TtsSentencePosition? = null,
    backgroundColor: Int = 0xFFFBFBFC.toInt()
) {
    content.getSpans(0, content.length, ReaderHighlightSpan::class.java).forEach(content::removeSpan)
    content.getSpans(0, content.length, WaveUnderlineSpan::class.java)
        .filter { it.fromSavedAnnotation }.forEach(content::removeSpan)
    content.getSpans(0, content.length, ReaderSearchHighlightSpan::class.java).forEach(content::removeSpan)
    content.getSpans(0, content.length, TtsSentenceHighlightSpan::class.java).forEach(content::removeSpan)
    notes.forEach { note ->
        val start = note.startPosition.coerceIn(0, content.length)
        val end = note.endPosition.coerceIn(0, content.length)
        if (start < end) {
            if (note.type == "underline") {
                // Rule spans belong to the chapter content; refresh only the saved manual layer.
                if (note.isGeneratedByHighlightRule) return@forEach
                val color = runCatching { android.graphics.Color.parseColor(note.color) }
                    .getOrDefault(0xFF333333.toInt())
                val mode = noteUnderlineMode(note)
                content.setSpan(WaveUnderlineSpan(color, mode, fromSavedAnnotation = true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            val ttsHighlightColor = TtsSentenceHighlightSpan.computeHighlightColor(backgroundColor)
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
}

@Composable
private fun TocSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    tocEntries: List<com.huangder.lumibooks.util.parser.TocEntry>,
    currentChapter: Int,
    bookmarks: List<com.huangder.lumibooks.domain.model.Bookmark> = emptyList(),
    chapterTitles: List<String> = emptyList(),
    glassBackdrop: Backdrop? = null,
    onChapterSelected: (com.huangder.lumibooks.util.parser.TocEntry) -> Unit,
    onBookmarkClick: (com.huangder.lumibooks.domain.model.Bookmark) -> Unit = {},
    onDeleteBookmark: (com.huangder.lumibooks.domain.model.Bookmark) -> Unit = {},
    availableTags: List<String> = emptyList(),
    onEditBookmarkRemark: (com.huangder.lumibooks.domain.model.Bookmark, String, List<String>) -> Unit = { _, _, _ -> },
    onEditBookmarkTags: (com.huangder.lumibooks.domain.model.Bookmark) -> Unit,
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
        tocEntries.indexOfFirst { it.chapterIndex == currentChapter }
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
    val currentDirectVisibleIndex = remember(visibleEntries, currentSourceIndex) {
        visibleEntries.indexOfFirst { it.sourceIndex == currentSourceIndex }
    }
    val bookmarkListState = rememberLazyListState()
    var activeSection by remember { mutableStateOf("toc") }
    var editingRemark by remember { mutableStateOf<com.huangder.lumibooks.domain.model.Bookmark?>(null) }
    var remarkText by remember { mutableStateOf("") }
    var remarkTags by remember { mutableStateOf<List<String>>(emptyList()) }
    val sortedBookmarks = remember(bookmarks) {
        bookmarks.sortedWith(
            compareBy<com.huangder.lumibooks.domain.model.Bookmark> { it.chapterIndex }
                .thenBy { it.position }
        )
    }
    val motionEnabled = LocalMotionEnabled.current
    val showReturnToCurrent by remember(
        tocListState,
        currentSourceIndex,
        currentDirectVisibleIndex,
        activeSection
    ) {
        derivedStateOf {
            if (activeSection != "toc" || currentSourceIndex < 0) return@derivedStateOf false
            val layout = tocListState.layoutInfo
            !isTocItemVisible(
                itemIndex = currentDirectVisibleIndex,
                viewportStartOffset = layout.viewportStartOffset,
                viewportEndOffset = layout.viewportEndOffset,
                visibleItems = layout.visibleItemsInfo.map {
                    TocViewportItem(it.index, it.offset, it.size)
                }
            )
        }
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
                    imageVector = AppIcons.AlignTop,
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
                    imageVector = AppIcons.AlignBottom,
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
                    imageVector = AppIcons.X,
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
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(
                            count = visibleEntries.size,
                            key = { index -> visibleEntries[index].sourceIndex }
                        ) { index ->
                            val (originalIndex, entry) = visibleEntries[index]

                            if (entry.isGroup || originalIndex in foldGroups) {
                                // 有子级条目的父项：箭头折叠/展开后代条目；
                                // 父项本身指向真实章节时，点标题仍可跳转
                                val isFoldable = originalIndex in foldGroups
                                val collapsed = originalIndex in collapsedGroups
                                val isCurrent =
                                    (entry.chapterIndex >= 0 && originalIndex == currentSourceIndex) ||
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
                                                imageVector = AppIcons.CaretDown,
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
                                val isCurrent = originalIndex == currentSourceIndex
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

                    TocReturnToCurrentButton(
                        visible = showReturnToCurrent,
                        motionEnabled = motionEnabled,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 24.dp),
                        onClick = {
                            if (currentSourceIndex >= 0) {
                                scope.launch {
                                    val nextCollapsed = collapsedGroups - collapsedTocAncestors(
                                        currentSourceIndex, foldGroups, collapsedGroups
                                    )
                                    collapsedGroups = nextCollapsed
                                    val targetIndex = visibleTocEntries(
                                        tocEntries, foldGroups, nextCollapsed
                                    ).indexOfFirst { it.sourceIndex == currentSourceIndex }
                                    if (targetIndex >= 0) {
                                        withFrameNanos { }
                                        tocListState.scrollToItem(targetIndex)
                                        withFrameNanos { }
                                        val layout = tocListState.layoutInfo
                                        val targetItem = layout.visibleItemsInfo
                                            .firstOrNull { it.index == targetIndex }
                                        if (targetItem != null) {
                                            val centeredOffset = layout.viewportStartOffset +
                                                (layout.viewportEndOffset - layout.viewportStartOffset - targetItem.size) / 2
                                            tocListState.scrollBy(
                                                (targetItem.offset - centeredOffset).toFloat()
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                                .fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(sortedBookmarks.size, key = { sortedBookmarks[it].id }) { idx ->
                                val bm = sortedBookmarks[idx]
                                TocBookmarkItem(
                                    bookmark = bm,
                                    onEditTags = { onEditBookmarkTags(bm) },
                                    chapterTitle = chapterTitles.getOrNull(bm.chapterIndex).orEmpty(),
                                    onClick = {
                                        pendingJumpBookmark = bm
                                        isClosing = true
                                    },
                                    onEditRemark = {
                                        editingRemark = bm
                                        remarkText = bm.remark
                                        remarkTags = bm.tags
                                    },
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

    NoteInputSheet(
        visible = editingRemark != null,
        glassBackdrop = glassBackdrop,
        initialText = remarkText,
        onTextChange = { remarkText = it },
        tags = remarkTags,
        availableTags = availableTags,
        onTagsChange = { remarkTags = it },
        onConfirm = { editingRemark?.let { onEditBookmarkRemark(it, remarkText, remarkTags) } },
        onDismiss = { editingRemark = null; remarkText = "" },
        title = stringResource(R.string.bookmark_remark_title),
        placeholder = stringResource(R.string.bookmark_remark_placeholder)
    )
}

@Composable
internal fun TocReturnToCurrentButton(
    visible: Boolean,
    motionEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motionEnabled) {
            fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.72f)
        } else {
            EnterTransition.None
        },
        exit = if (motionEnabled) {
            fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.72f)
        } else {
            ExitTransition.None
        }
    ) {
        LiquidGlassIconButton(
            imageVector = AppIcons.ArrowClockwise,
            contentDescription = stringResource(R.string.reader_toc_return_to_current),
            onClick = onClick,
            size = 48.dp,
            iconSize = 22.dp,
            contentColor = AppColors.OnAccent,
            normalContainerColor = AppColors.Accent,
            liquidContainerColor = AppColors.Accent,
            liquidScrimColor = AppColors.Accent.copy(alpha = 0.82f)
        )
    }

}

@Composable
private fun TxtTocRuleDialog(
    currentRuleId: String,
    customRules: List<TxtTocRule>,
    thirdPartyRules: List<TxtTocRule>,
    diagnostics: List<TxtTocRuleDiagnostics>,
    isChanging: Boolean,
    backdrop: Backdrop?,
    onHelp: () -> Unit,
    onApply: (String?) -> Unit,
    onSaveCustom: (TxtTocRule) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultRuleName = stringResource(R.string.txt_toc_rule_default_name)
    val defaultChapterRegex = stringResource(R.string.txt_toc_rule_default_chapter_regex)
    var chapterRegex by remember { mutableStateOf(defaultChapterRegex) }
    var volumeRegex by remember { mutableStateOf("") }
    var titleTemplate by remember { mutableStateOf("") }
    var volumeTemplate by remember { mutableStateOf("") }
    var ruleName by remember { mutableStateOf(defaultRuleName) }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val invalidRuleMessage = stringResource(R.string.txt_toc_rule_invalid)

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
        backgroundScrimColor = Color.Black.copy(alpha = 0.10f),
        backgroundBlurRadius = 0.dp,
        transparencyOverride = 0.24f,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.txt_toc_rule_dialog_title),
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(
                    imageVector = AppIcons.Info,
                    contentDescription = stringResource(R.string.txt_toc_rule_help),
                    onClick = onHelp,
                    size = 36.dp,
                    iconSize = 19.dp,
                    contentColor = AppColors.TextSecondary,
                    normalContainerColor = Color.Transparent,
                    liquidContainerColor = AppColors.CardBg,
                    liquidScrimColor = AppColors.BgGray.copy(alpha = 0.62f)
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(scrollState)) {
                Text(
                    stringResource(R.string.txt_toc_rule_auto_section),
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.txt_toc_rule_auto_message),
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                TxtTocRuleOption(
                    title = stringResource(R.string.txt_toc_rule_auto_title),
                    description = stringResource(R.string.txt_toc_rule_auto_description),
                    selected = currentRuleId == "auto",
                    enabled = !isChanging,
                    onClick = { onApply(null) }
                )
                Spacer(Modifier.height(8.dp))
                TxtTocRuleBuiltIns.all.forEach { rule ->
                    TxtTocRuleOption(
                        title = txtTocRuleTitle(rule),
                        description = txtTocRuleDescription(rule.id),
                        selected = currentRuleId == rule.id,
                        enabled = !isChanging,
                        onClick = { onApply(rule.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                diagnostics.firstOrNull { it.ruleId == currentRuleId }?.let { diagnostic ->
                    Text(
                        stringResource(
                            R.string.txt_toc_rule_current_matches,
                            diagnostic.chapterMatches + diagnostic.volumeMatches
                        ) +
                            (diagnostic.reason?.let {
                                " · ${stringResource(R.string.txt_toc_rule_match_note)}"
                            } ?: ""),
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                customRules.forEach { rule ->
                    TxtTocRuleOption(
                        title = stringResource(R.string.txt_toc_rule_custom_prefix, rule.name),
                        description = stringResource(R.string.txt_toc_rule_custom_description),
                        selected = currentRuleId == rule.id,
                        enabled = !isChanging,
                        onClick = { onApply(rule.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (thirdPartyRules.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.txt_toc_compat_section_title),
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.txt_toc_compat_description),
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    thirdPartyRules.forEach { rule ->
                        TxtTocRuleOption(
                            title = stringResource(R.string.txt_toc_rule_compat_prefix, rule.name),
                            description = txtTocThirdPartyDescription(rule),
                            selected = currentRuleId == rule.id,
                            enabled = !isChanging,
                            onClick = { onApply(rule.id) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.txt_toc_rule_custom_section),
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.txt_toc_rule_custom_help),
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
                TxtTocRuleExample()
                Spacer(Modifier.height(6.dp))
                TocRuleInput(stringResource(R.string.txt_toc_rule_name), ruleName) { ruleName = it }
                TocRuleInput(stringResource(R.string.txt_toc_rule_chapter_regex), chapterRegex) { chapterRegex = it }
                TocRuleInput(stringResource(R.string.txt_toc_rule_volume_regex), volumeRegex) { volumeRegex = it }
                TocRuleInput(stringResource(R.string.txt_toc_rule_chapter_template), titleTemplate) { titleTemplate = it }
                TocRuleInput(stringResource(R.string.txt_toc_rule_volume_template), volumeTemplate) { volumeTemplate = it }
                error?.let {
                    Text(
                        it,
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.txt_toc_rule_save_apply),
                onClick = {
                    val rule = TxtTocRule(
                        id = "custom-${ruleName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}-${System.currentTimeMillis()}",
                        name = ruleName.trim(),
                        chapterRegex = chapterRegex,
                        volumeRegex = volumeRegex.trim().takeIf { it.isNotEmpty() },
                        chapterTitleTemplate = titleTemplate.takeIf { it.isNotEmpty() },
                        volumeTitleTemplate = volumeTemplate.takeIf { it.isNotEmpty() },
                        example = "",
                        order = 100
                    )
                    val result = TxtTocRuleCompiler.compile(rule)
                    result.onSuccess { onSaveCustom(rule) }
                        .onFailure { error = it.message ?: invalidRuleMessage }
                },
                tintedColor = AppColors.Accent,
                contentColor = AppColors.OnAccent
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                tintedColor = AppColors.BgGray,
                contentColor = AppColors.TextPrimary
            )
        }
    )
}

@Composable
private fun txtTocRuleTitle(rule: TxtTocRule): String = when (rule.id) {
    "builtin-multilingual" -> stringResource(R.string.txt_toc_rule_multilingual_title)
    "builtin-decorated" -> stringResource(R.string.txt_toc_rule_decorated_title)
    "builtin-numbered" -> stringResource(R.string.txt_toc_rule_numbered_title)
    "builtin-symbol-prefixed" -> stringResource(R.string.txt_toc_rule_symbol_title)
    else -> rule.name
}

@Composable
private fun txtTocRuleDescription(ruleId: String): String = when (ruleId) {
    "builtin-multilingual" -> stringResource(R.string.txt_toc_rule_multilingual_description)
    "builtin-decorated" -> stringResource(R.string.txt_toc_rule_decorated_description)
    "builtin-numbered" -> stringResource(R.string.txt_toc_rule_numbered_description)
    "builtin-symbol-prefixed" -> stringResource(R.string.txt_toc_rule_symbol_description)
    else -> stringResource(R.string.txt_toc_rule_custom_description)
}

@Composable
private fun txtTocThirdPartyDescription(rule: TxtTocRule): String {
    val scriptBadge = if (rule.hasIgnoredScript) {
        " · " + stringResource(R.string.txt_toc_compat_badge_script)
    } else {
        ""
    }
    return rule.chapterRegex + scriptBadge
}

@Composable
private fun TxtTocRuleOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val accent = AppColors.Accent
    val selectedTextColor = Color.White.copy(alpha = if (enabled) 1f else 0.48f)
    val titleColor = if (selected) {
        selectedTextColor
    } else {
        AppColors.TextPrimary.copy(alpha = if (enabled) 1f else 0.48f)
    }
    val descriptionColor = if (selected) {
        Color.White.copy(alpha = if (enabled) 0.86f else 0.42f)
    } else {
        AppColors.TextSecondary.copy(alpha = if (enabled) 1f else 0.48f)
    }
    LiquidGlassSurface(
        shape = RoundedCornerShape(14.dp),
        fallbackColor = if (selected) accent else AppColors.BgGray,
        contentScrimColor = if (selected) accent.copy(alpha = 0.86f) else AppColors.CardBg.copy(alpha = 0.54f),
        tintColor = accent.takeIf { selected },
        transparencyOverride = if (selected) 0.18f else 0.36f,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (selected) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                    text = description,
                    color = descriptionColor,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = stringResource(R.string.txt_toc_rule_selected),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TxtTocRuleExample() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LightBgGray)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = stringResource(R.string.txt_toc_rule_example_title),
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.txt_toc_rule_example_lines),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            text = stringResource(R.string.txt_toc_rule_example_regex),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
        Text(
            text = stringResource(R.string.txt_toc_rule_example_template),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Text(
            text = stringResource(R.string.txt_toc_rule_example_blank),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = stringResource(R.string.txt_toc_rule_symbol_example_title),
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.txt_toc_rule_symbol_example_lines),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            text = stringResource(R.string.txt_toc_rule_symbol_example_regex),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun TocRuleInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().background(LightBgGray, RoundedCornerShape(8.dp)).padding(10.dp),
            textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 14.sp),
            singleLine = true
        )
    }
}

@Composable
private fun TocBookmarkItem(
    bookmark: com.huangder.lumibooks.domain.model.Bookmark,
    chapterTitle: String,
    onClick: () -> Unit,
    onEditRemark: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit
) {
    val chapterNumber = stringResource(R.string.chapter_number, bookmark.chapterIndex + 1)
    SwipeRevealItem(
        onEdit = onEditRemark,
        onEditTags = onEditTags,
        onDelete = onDelete,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LightBgGray)
                .border(1.dp, AppColors.TextSecondary.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AppIcons.Bookmark.filled,
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
                    text = if (chapterTitle.isBlank()) chapterNumber else "$chapterNumber · $chapterTitle",
                    fontSize = 12.sp,
                    color = LightTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (bookmark.remark.isNotBlank()) {
                    Text(bookmark.remark, fontSize = 12.sp, color = LightTextSecondary)
                }
                AnnotationTagChips(bookmark.tags, modifier = Modifier.padding(top = 4.dp))
            }
        }
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
    val scrollbarVisible = listState.isScrollInProgress || dragFraction >= 0f
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (scrollbarVisible) 1f else 0f,
        animationSpec = tween(if (scrollbarVisible) 120 else 420),
        label = "scrollbarVisibility"
    )
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val currentContentHeightPx by rememberUpdatedState(contentHeightPx)
    val thumbColor = AppColors.TextSecondary.copy(alpha = 0.38f)

    Box(
        modifier = modifier.graphicsLayer { alpha = scrollbarAlpha }
    ) {
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
                        imageVector = AppIcons.X,
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

@Composable
private fun NetworkSearchSheet(
    visible: Boolean,
    initialQuery: String,
    onDismiss: () -> Unit
) {
    val animationProgress = remember { Animatable(0f) }
    var isMounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            isMounted = true
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            )
        } else if (isMounted) {
            animationProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            )
            isMounted = false
        }
    }
    if (!isMounted) return

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = LocalIsDarkTheme.current
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var selectedEngine by remember { mutableStateOf(CoverSearchEngine.BING) }
    var pageProgress by remember { mutableIntStateOf(100) }
    var isLoading by remember { mutableStateOf(false) }
    // WebView derives prefers-color-scheme from the Android theme attached to its context.
    // The activity uses a fixed light platform theme, so give this view a matching light/dark
    // wrapper instead of relying on the Compose color scheme alone.
    val webView = remember(context, isDark) {
        val webViewTheme = if (isDark) {
            R.style.Theme_EBookReader_WebView_Dark
        } else {
            R.style.Theme_EBookReader_WebView_Light
        }
        WebView(ContextThemeWrapper(context, webViewTheme)).apply {
            setBackgroundColor(if (isDark) 0xFF000000.toInt() else 0xFFFBFBFC.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.defaultTextEncodingName = "UTF-8"
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    settings,
                    if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme != "http" && request.url.scheme != "https"

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    isLoading = false
                    CoverSearchEngine.fromUrl(url)?.let { engine ->
                        selectedEngine = engine
                        engine.queryFromUrl(url)?.let { query = it }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    pageProgress = newProgress
                }
            }
            loadUrl(selectedEngine.buildWebSearchUrl(initialQuery))
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    fun submitQuery() {
        val value = query.trim()
        if (value.isBlank()) return
        keyboardController?.hide()
        val hasScheme = value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        val looksLikeHost = value.matches(Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(:[0-9]+)?(/.*)?$"))
        val url = when {
            hasScheme -> value
            looksLikeHost -> "https://$value"
            else -> selectedEngine.buildWebSearchUrl(value)
        }
        webView.loadUrl(url)
    }

    fun goBackOrDismiss() {
        if (webView.canGoBack()) webView.goBack() else onDismiss()
    }

    BackHandler(enabled = true) {
        if (visible) goBackOrDismiss()
    }

    val panelShape = RoundedCornerShape(28.dp)
    val enterOffsetPx = with(LocalDensity.current) { 24.dp.toPx() }
    val engineLabels = mapOf(
        CoverSearchEngine.BING to stringResource(R.string.cover_search_engine_bing),
        CoverSearchEngine.BAIDU to stringResource(R.string.cover_search_engine_baidu),
        CoverSearchEngine.GOOGLE to stringResource(R.string.cover_search_engine_google)
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = animationProgress.value }
                .background(AppColors.Scrim.copy(alpha = 0.28f))
                .clickable(
                    enabled = visible,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )
        LiquidGlassSheetContainer(
            fallbackColor = AppColors.CardBg,
            shape = panelShape,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.9f)
                .graphicsLayer {
                    val progress = animationProgress.value
                    alpha = progress
                    scaleX = 0.94f + progress * 0.06f
                    scaleY = 0.94f + progress * 0.06f
                    translationY = (1f - progress) * enterOffsetPx
                }
                .imePadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { },
            contentModifier = Modifier.fillMaxSize().padding(12.dp),
            forceFallback = true
        ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassIconButton(
                            imageVector = AppIcons.X,
                            contentDescription = stringResource(R.string.close),
                            onClick = onDismiss,
                            forceFallback = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(AppColors.BgGray)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.MagnifyingGlass,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = AppType.BodySmall),
                                cursorBrush = SolidColor(AppColors.Accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submitQuery() }),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        LiquidGlassIconButton(
                            imageVector = AppIcons.ArrowClockwise,
                            contentDescription = stringResource(R.string.reload_page),
                            onClick = { webView.reload() },
                            forceFallback = true
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(AppColors.BgGray)
                            .border(1.dp, AppColors.Divider.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                    ) {
                        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                        if (isLoading) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = AppColors.Accent,
                                trackColor = Color.Transparent
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverSearchEngine.entries.forEach { engine ->
                            val selected = selectedEngine == engine
                            val shape = RoundedCornerShape(50)
                            LiquidGlassSurface(
                                shape = shape,
                                fallbackColor = if (selected) AppColors.Accent else AppColors.BgGray,
                                forceFallback = true,
                                onClick = {
                                    selectedEngine = engine
                                    val value = query.trim()
                                    if (value.isNotEmpty()) {
                                        keyboardController?.hide()
                                        webView.loadUrl(engine.buildWebSearchUrl(value))
                                    }
                                },
                                decorationModifier = Modifier.border(
                                    width = 1.dp,
                                    color = if (selected) Color.Transparent else AppColors.Divider.copy(alpha = 0.55f),
                                    shape = shape
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .semantics { this.selected = selected }
                            ) {
                                Text(
                                    text = engineLabels.getValue(engine),
                                    color = if (selected) AppColors.OnAccent else AppColors.TextPrimary,
                                    fontSize = AppType.BodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun ReaderFirstOpenHints(
    epub: Boolean,
    mobi: Boolean,
    txtEncoding: Boolean,
    backdrop: Backdrop?,
    viewModel: ReaderViewModel
) {
    if (epub) ReaderFirstOpenHintDialog(
        title = stringResource(R.string.epub_layout_first_open_title),
        message = stringResource(R.string.epub_layout_first_open_message),
        confirmText = stringResource(R.string.epub_layout_first_open_confirm),
        backdrop = backdrop,
        onDismissRequest = viewModel::hideEpubLayoutHint,
        onConfirm = viewModel::dismissEpubLayoutHint
    )
    if (mobi) ReaderFirstOpenHintDialog(
        title = stringResource(R.string.mobi_layout_first_open_title),
        message = stringResource(R.string.mobi_layout_first_open_message),
        confirmText = stringResource(R.string.mobi_layout_first_open_confirm),
        backdrop = backdrop,
        onDismissRequest = viewModel::hideMobiLayoutHint,
        onConfirm = viewModel::dismissMobiLayoutHint
    )
    if (txtEncoding) ReaderFirstOpenHintDialog(
        title = stringResource(R.string.txt_encoding_first_open_title),
        message = stringResource(R.string.txt_encoding_first_open_message),
        confirmText = stringResource(R.string.txt_encoding_first_open_confirm),
        backdrop = backdrop,
        onDismissRequest = viewModel::hideTxtEncodingHint,
        onConfirm = viewModel::dismissTxtEncodingHint
    )
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

@Composable
private fun ReaderSelectionNoteSheet(
    visible: Boolean,
    requestClose: Boolean,
    glassBackdrop: Backdrop?,
    noteText: String,
    onTextChange: (String) -> Unit,
    editingNote: com.huangder.lumibooks.domain.model.Note?,
    availableTags: List<String>,
    pendingSelection: PendingSelection?,
    viewModel: ReaderViewModel,
    onConfirmed: () -> Unit,
    onDismissed: () -> Unit
) {
    var tags by remember(visible, editingNote?.syncId) { mutableStateOf(editingNote?.tags.orEmpty()) }
    NoteInputSheet(
        visible = visible,
        requestClose = requestClose,
        glassBackdrop = glassBackdrop,
        initialText = noteText,
        onTextChange = onTextChange,
        tags = tags,
        availableTags = availableTags,
        onTagsChange = { tags = it },
        onConfirm = {
            if (editingNote != null) {
                if (editingNote.note != noteText) {
                    viewModel.updateNote(editingNote.copy(note = noteText, isNote = true, tags = tags))
                } else {
                    viewModel.updateNoteTags(editingNote.syncId, tags)
                }
            } else {
                val selection = pendingSelection ?: return@NoteInputSheet
                viewModel.addNote(
                    selectedText = selection.selectedText,
                    noteText = noteText,
                    chapterIndex = selection.chapterIndex,
                    startPosition = selection.startPosition,
                    endPosition = selection.endPosition,
                    color = DefaultReaderHighlightColorWithAlpha,
                    startLocatorJson = selection.startLocatorJson,
                    endLocatorJson = selection.endLocatorJson,
                    isNote = true,
                    tags = tags
                )
            }
            onConfirmed()
        },
        onDismiss = onDismissed
    )
}

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
    /** 普通单击命中已有标注时，仅显示该标注的颜色/删除操作。 */
    val annotationOnly: Boolean = false,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null
) {
    val hasHighlight: Boolean get() = overlappingHighlights.isNotEmpty()
    val hasUnderline: Boolean get() = overlappingUnderlines.isNotEmpty()
    val hasNote: Boolean get() = (overlappingHighlights + overlappingUnderlines).any { it.isNoteEntry }
    val existingNote: com.huangder.lumibooks.domain.model.Note?
        get() = (overlappingHighlights + overlappingUnderlines).let { notes ->
            notes.firstOrNull { it.isNoteEntry } ?: notes.firstOrNull()
        }
}

private fun selectionAnnotationNotes(
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    info: com.huangder.lumibooks.ui.reader.engine.SelectionInfo
): Pair<List<com.huangder.lumibooks.domain.model.Note>, List<com.huangder.lumibooks.domain.model.Note>> {
    val directAnnotation = info.annotation.takeIf { info.annotationOnly }
    if (directAnnotation != null) {
        return when (directAnnotation.type) {
            "highlight" -> listOf(directAnnotation) to emptyList()
            "underline" -> emptyList<com.huangder.lumibooks.domain.model.Note>() to listOf(directAnnotation)
            else -> emptyList<com.huangder.lumibooks.domain.model.Note>() to emptyList()
        }
    }
    return findOverlappingNotes(
        notes, info.chapterIndex, info.startPosition, info.endPosition, "highlight"
    ) to findOverlappingNotes(
        notes, info.chapterIndex, info.startPosition, info.endPosition, "underline"
    )
}

private fun selectionStateForReaderInfo(
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    info: com.huangder.lumibooks.ui.reader.engine.SelectionInfo
): SelectionState {
    val (overlappingHighlights, overlappingUnderlines) = selectionAnnotationNotes(notes, info)
    return SelectionState(
        chapterIndex = info.chapterIndex,
        pageInChapter = 0,
        charStart = info.startPosition,
        charEnd = info.endPosition,
        selectedText = info.selectedText,
        touchX = info.selStartX,
        touchY = info.selTopY,
        overlappingHighlights = overlappingHighlights,
        overlappingUnderlines = overlappingUnderlines,
        selTopY = info.selTopY,
        selBottomY = info.selBottomY,
        selStartX = info.selStartX,
        selEndX = info.selEndX,
        annotationOnly = info.annotationOnly
    )
}

private fun createSelectionAnnotation(viewModel: ReaderViewModel, selection: SelectionState, type: String): String? {
    return viewModel.addNote(
        selectedText = selection.selectedText,
        noteText = "",
        chapterIndex = selection.chapterIndex,
        startPosition = selection.charStart,
        endPosition = selection.charEnd,
        color = readerHighlightColorReference(0, type),
        startLocatorJson = selection.startLocatorJson,
        endLocatorJson = selection.endLocatorJson,
        type = type
    )
}

private fun applyAnnotationColor(
    viewModel: ReaderViewModel,
    selection: SelectionState,
    type: String,
    slot: Int
) {
    val target = if (type == "underline") {
        selection.overlappingUnderlines.firstOrNull()
    } else {
        selection.overlappingHighlights.firstOrNull()
    }
    if (target != null) {
        val colorReference = readerHighlightColorReference(slot, type)
        val styleSnapshot = RuleStyleJson.decode(target.styleSnapshotJson)?.let { style ->
            val resolvedColor = runCatching {
                android.graphics.Color.parseColor(resolveReaderHighlightColor(colorReference))
            }.getOrNull()
            RuleStyleJson.encode(style.copy(textColor = resolvedColor ?: style.textColor))
        }
        viewModel.updateNote(
            target.copy(
                color = colorReference,
                styleSnapshotJson = styleSnapshot ?: target.styleSnapshotJson,
                updatedAt = System.currentTimeMillis()
            )
        )
    } else {
        viewModel.replaceAnnotationRange(
            chapterIndex = selection.chapterIndex,
            startPosition = selection.charStart,
            endPosition = selection.charEnd,
            type = type,
            color = readerHighlightColorReference(slot, type)
        )
    }
}

private fun noteUnderlineMode(note: com.huangder.lumibooks.domain.model.Note?): Int =
    RuleStyleJson.decode(note?.styleSnapshotJson)?.underlineMode
        ?.takeIf { it in 1..4 }
        ?: HighlightRule.UNDERLINE_WAVE

private fun updateUnderlineStyle(
    viewModel: ReaderViewModel,
    selection: SelectionState,
    mode: Int
) {
    val target = selection.overlappingUnderlines.firstOrNull() ?: return
    val current = RuleStyleJson.decode(target.styleSnapshotJson)
    val color = runCatching { android.graphics.Color.parseColor(resolveReaderHighlightColor(target.color)) }
        .getOrNull()
    val style = (current ?: RuleStyle(
        textColor = color,
        underlineMode = HighlightRule.UNDERLINE_WAVE,
        underlineOffset = 2f,
        underlineWidth = 1f,
        fontWeight = 400,
        italic = false
    )).copy(
        textColor = current?.textColor ?: color,
        underlineMode = mode
    )
    viewModel.updateNote(
        target.copy(
            styleSnapshotJson = RuleStyleJson.encode(style),
            updatedAt = System.currentTimeMillis()
        )
    )
}

private fun removeAnnotation(
    viewModel: ReaderViewModel,
    selection: SelectionState,
    type: String
) {
    val target = if (type == "underline") {
        selection.overlappingUnderlines.firstOrNull()
    } else {
        selection.overlappingHighlights.firstOrNull()
    }
    if (target != null) {
        viewModel.deleteNote(target)
    } else {
        viewModel.removeAnnotationRange(
            chapterIndex = selection.chapterIndex,
            startPosition = selection.charStart,
            endPosition = selection.charEnd,
            type = type
        )
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
private const val MENU_KEY_WEB_SEARCH = "web_search"
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
    onWebSearch: () -> Unit,
    onDictionary: () -> Unit,
    onDictionaryAppSelected: (DictionaryAppOption) -> Unit,
    onCopy: () -> Unit,
    onViewNote: () -> Unit,
    onEditHighlightTags: () -> Unit = {},
    onEditUnderlineTags: () -> Unit = {},
    onReplace: () -> Unit = {},
    onMenuSettings: () -> Unit = {},
    onColorPicked: (Int) -> Unit = {},
    onChangeHighlightColor: (Int) -> Unit = {},
    onChangeUnderlineColor: (Int) -> Unit = {},
    onChangeUnderlineStyle: (Int) -> Unit = {},
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
    val maxMenuWidth = (configuration.screenWidthDp.dp - 24.dp).coerceAtLeast(180.dp)
    val textMeasurer = rememberTextMeasurer()
    val annotationRowCount = (if (state.hasHighlight) 1 else 0) + (if (state.hasUnderline) 1 else 0)
    // A stale or unresolved annotation must never collapse the overlay to 0 x 0.
    val showAnnotationActionsOnly = state.annotationOnly && annotationRowCount > 0
    // 普通行菜单项：隐藏选区已存在的标注类型；带笔记时"笔记"换成"查看笔记"
    val actionLabels = buildList {
        if (!showAnnotationActionsOnly) {
            if (!state.hasHighlight && isMenuEnabled(selectionMenuItems, MENU_KEY_HIGHLIGHT)) add(stringResource(R.string.menu_highlight))
            if (!state.hasUnderline && isMenuEnabled(selectionMenuItems, MENU_KEY_UNDERLINE)) add(stringResource(R.string.menu_underline))
            if (isMenuEnabled(selectionMenuItems, MENU_KEY_NOTE)) add(stringResource(if (state.hasNote) R.string.menu_view_note else R.string.menu_note))
            if (isMenuEnabled(selectionMenuItems, MENU_KEY_DICTIONARY)) add(stringResource(R.string.menu_dictionary))
            if (isMenuEnabled(selectionMenuItems, MENU_KEY_SEARCH)) add(stringResource(R.string.menu_search))
            if (isMenuEnabled(selectionMenuItems, MENU_KEY_WEB_SEARCH)) add(stringResource(R.string.menu_web_search))
            if (isMenuEnabled(selectionMenuItems, MENU_KEY_COPY)) add(stringResource(R.string.menu_copy))
            if (isTxtBook && isMenuEnabled(selectionMenuItems, MENU_KEY_REPLACE)) add(stringResource(R.string.menu_replace))
        }
    }
    val actionChipHorizontalPadding = if (isLiquidGlass) 10.dp else 16.dp
    // chip 实际渲染样式（MenuChip 用 fontSize + 继承的 LocalTextStyle），测量时保持一致
    val menuChipTextStyle = LocalTextStyle.current.merge(
        TextStyle(fontSize = SELECTION_MENU_CHIP_FONT_SIZE_SP.sp)
    )
    fun measuredLabelWidth(label: String): Dp = with(density) {
        textMeasurer.measure(
            text = label,
            style = menuChipTextStyle,
            maxLines = 1
        ).size.width.toDp()
    }
    fun measuredLabelHeight(label: String): Dp = with(density) {
        textMeasurer.measure(
            text = label,
            style = menuChipTextStyle,
            maxLines = 1
        ).size.height.toDp()
    }
    val measuredActionLabelsWidth = actionLabels.fold(0.dp) { width, label ->
        width + measuredLabelWidth(label) + actionChipHorizontalPadding * 2
    }
    // 普通菜单：文字 chip + 分隔线 + 行内边距；上方带标注菜单时行尾追加齿轮入口
    val normalPillWidth = (
        measuredActionLabelsWidth +
            0.5.dp * (actionLabels.size - 1).coerceAtLeast(0) +
            20.dp +
            if (!showAnnotationActionsOnly && (state.hasHighlight || state.hasUnderline)) 32.5.dp else 0.dp
        ).coerceIn(180.dp, maxMenuWidth)
    // 标注菜单：划线额外包含直线/波浪线选择，再放颜色点和移除按钮。
    val annotationRemoveLabels = buildList {
        if (state.hasHighlight) add(stringResource(R.string.menu_remove_highlight))
        if (state.hasUnderline) add(stringResource(R.string.menu_remove_underline))
    }
    val annotationPillWidth = if (annotationRemoveLabels.isEmpty()) 0.dp else {
        val maxRemoveChipWidth = annotationRemoveLabels.maxOf { measuredLabelWidth(it) + actionChipHorizontalPadding * 2 }
        val stylePickerWidth = if (state.hasUnderline) 78.dp else 0.dp
        (182.dp + stylePickerWidth + 12.5.dp + 20.dp + maxRemoveChipWidth + measuredLabelWidth(stringResource(R.string.annotation_tags)) + 24.dp).coerceAtMost(maxMenuWidth)
    }
    // 菜单行高跟随系统字体缩放：固定 52dp 会把大字体下的 chip 文字裁掉下半截
    val menuChipLabels = buildList {
        addAll(actionLabels)
        addAll(annotationRemoveLabels)
        add(stringResource(R.string.selection_menu_settings))
        dictionaryAppOptions.forEach { add(it.label) }
    }
    val menuRowHeight = selectionMenuRowHeightDp(
        menuChipLabels.maxOf { measuredLabelHeight(it).value }
    ).dp
    val desiredActionMenuWidth = if (showAnnotationActionsOnly) annotationPillWidth else {
        maxOf(normalPillWidth, annotationPillWidth)
    }
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
    val menuRowCount = annotationRowCount + if (showAnnotationActionsOnly) 0 else 1
    val targetMenuHeight = if (menuMode == SelectionMenuMode.Actions) {
        menuRowHeight * menuRowCount + menuPillGap * (menuRowCount - 1)
    } else {
        menuRowHeight
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
                    .zIndex(-1f)
                    // Do not install a pointer handler here. AndroidView children
                    // can still win/lose hit testing against a negative-z Compose
                    // sibling, which would swallow selection-handle DOWN events.
                    // The reader surface dismisses the menu on an outside tap;
                    // action/menu children below remain interactive.
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
                // 用 heightIn 而不是固定 height：万一实测行高略大于估算值，菜单也能自然撑开
                .heightIn(min = animMenuHeightDp),
            contentAlignment = Alignment.Center,
            label = "selectionMenuMode"
        ) { mode ->
            when (mode) {
                SelectionMenuMode.ColorPicker -> SelectionMenuPill(
                    width = colorPickerWidth,
                    height = menuRowHeight,
                    reappearKey = reappearKey,
                    menuBg = menuBg,
                    glassBackdrop = glassBackdrop,
                    forceSolidSurface = forceSolidSurface
                ) {
                    // Color picker submenu: six color dots with manual spacing.
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = SELECTION_MENU_ROW_VERTICAL_PADDING_DP.dp),
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
                    height = menuRowHeight,
                    reappearKey = reappearKey,
                    menuBg = menuBg,
                    glassBackdrop = glassBackdrop,
                    forceSolidSurface = forceSolidSurface
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = SELECTION_MENU_ROW_VERTICAL_PADDING_DP.dp),
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
                    height = menuRowHeight,
                    reappearKey = reappearKey,
                    menuBg = menuBg,
                    glassBackdrop = glassBackdrop,
                    forceSolidSurface = forceSolidSurface
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = SELECTION_MENU_ROW_VERTICAL_PADDING_DP.dp),
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
                                height = menuRowHeight,
                                reappearKey = reappearKey,
                                enterDelayMillis = pillIndex * SELECTION_PILL_STAGGER_MILLIS,
                                menuBg = menuBg,
                                glassBackdrop = glassBackdrop,
                                forceSolidSurface = forceSolidSurface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = SELECTION_MENU_ROW_VERTICAL_PADDING_DP.dp),
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
                                    MenuChip(stringResource(R.string.annotation_tags), menuText, onEditHighlightTags)
                                }
                            }
                            pillIndex++
                        }
                        if (state.hasUnderline) {
                            SelectionMenuPill(
                                width = annotationPillWidth,
                                height = menuRowHeight,
                                reappearKey = reappearKey,
                                enterDelayMillis = pillIndex * SELECTION_PILL_STAGGER_MILLIS,
                                menuBg = menuBg,
                                glassBackdrop = glassBackdrop,
                                forceSolidSurface = forceSolidSurface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = SELECTION_MENU_ROW_VERTICAL_PADDING_DP.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SelectionAnnotationRow(
                                        currentColor = state.overlappingUnderlines.firstOrNull()?.color,
                                        underlineMode = noteUnderlineMode(state.overlappingUnderlines.firstOrNull()),
                                        removeLabel = stringResource(R.string.menu_remove_underline),
                                        menuText = menuText,
                                        dividerColor = dividerColor,
                                        onColorChange = onChangeUnderlineColor,
                                        onUnderlineStyleChange = onChangeUnderlineStyle,
                                        onRemove = onDeleteUnderline
                                    )
                                    MenuChip(stringResource(R.string.annotation_tags), menuText, onEditUnderlineTags)
                                }
                            }
                            pillIndex++
                        }
                        if (!showAnnotationActionsOnly) {
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
                                if (isMenuEnabled(selectionMenuItems, MENU_KEY_WEB_SEARCH)) add(Pair(stringResource(R.string.menu_web_search), onWebSearch))
                                if (isMenuEnabled(selectionMenuItems, MENU_KEY_COPY)) add(Pair(stringResource(R.string.menu_copy), onCopy))
                                if (isTxtBook && isMenuEnabled(selectionMenuItems, MENU_KEY_REPLACE)) add(Pair(stringResource(R.string.menu_replace), onReplace))
                            }
                            SelectionMenuPill(
                                width = normalPillWidth,
                                height = menuRowHeight,
                                reappearKey = reappearKey,
                                enterDelayMillis = pillIndex * SELECTION_PILL_STAGGER_MILLIS,
                                menuBg = menuBg,
                                glassBackdrop = glassBackdrop,
                                forceSolidSurface = forceSolidSurface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = SELECTION_MENU_ROW_VERTICAL_PADDING_DP.dp),
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
        MENU_KEY_WEB_SEARCH to stringResource(R.string.menu_web_search),
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
private fun ReaderReplaceInputSheet(
    visible: Boolean,
    glassBackdrop: Backdrop?,
    selection: ReplaceSelectionInfo?,
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ReplaceInputSheet(
        visible = visible,
        glassBackdrop = glassBackdrop,
        selectedText = selection?.selectedText.orEmpty(),
        canReplaceCurrent = selection?.let {
            it.chapterIndex != null && it.charStart != null && it.charEnd != null
        } == true,
        onReplaceAll = { replacement ->
            if (selection != null) {
                viewModel.replaceTxtText(
                    searchText = selection.selectedText,
                    replaceWith = replacement,
                    onResult = { replaced ->
                        Toast.makeText(context,
                            if (replaced) R.string.replace_success else R.string.replace_no_match,
                            Toast.LENGTH_SHORT).show()
                    }
                )
            }
        },
        onReplaceCurrent = { replacement ->
            if (selection?.chapterIndex != null && selection.charStart != null && selection.charEnd != null) {
                viewModel.replaceTxtRange(
                    chapterIndex = selection.chapterIndex,
                    start = selection.charStart,
                    endExclusive = selection.charEnd,
                    replaceWith = replacement,
                    onResult = { replaced ->
                        Toast.makeText(context,
                            if (replaced) R.string.replace_success else R.string.replace_failed,
                            Toast.LENGTH_SHORT).show()
                    }
                )
            }
        },
        onDismiss = onDismiss
    )
}

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
                        imageVector = AppIcons.X,
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
                        controlEdge = true,
                        shape = RoundedCornerShape(26.dp),
                        fallbackColor = AppColors.BgGray,
                        contentScrimColor = AppColors.BgGray.copy(alpha = 0.22f),
                        transparencyOverride = 0.78f,
                        interactive = false,
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
    // 字号与 SelectionMenuMetrics 的行高推算共用同一常量，避免菜单高度与文字脱节
    Text(
        text = label,
        fontSize = SELECTION_MENU_CHIP_FONT_SIZE_SP.sp,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = horizontalPadding, vertical = SELECTION_MENU_CHIP_VERTICAL_PADDING_DP.dp)
    )
}

/** 独立胶囊菜单：自带入场动画，enterDelayMillis 用于多菜单级联依次弹出 */
@Composable
private fun SelectionMenuPill(
    width: Dp,
    height: Dp,
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
            // 行高按系统字体缩放推导，避免大字体下 chip 文字被固定高度裁掉
            .heightIn(min = height)
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
    underlineMode: Int? = null,
    removeLabel: String,
    menuText: Color,
    dividerColor: Color,
    onColorChange: (Int) -> Unit,
    onUnderlineStyleChange: (Int) -> Unit = {},
    onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (underlineMode != null) {
            listOf(HighlightRule.UNDERLINE_STRAIGHT, HighlightRule.UNDERLINE_DOUBLE,
                HighlightRule.UNDERLINE_WAVE, HighlightRule.UNDERLINE_DASHED).forEach { mode ->
                UnderlineStyleButton(mode, underlineMode == mode, menuText) {
                    onUnderlineStyleChange(mode)
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(8.dp))
            MenuDivider(dividerColor)
            Spacer(Modifier.width(8.dp))
        }
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

@Composable
private fun UnderlineStyleButton(
    mode: Int,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .then(if (selected) Modifier.border(1.5.dp, color, RoundedCornerShape(6.dp)) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .semantics {
                contentDescription = when (mode) {
                    HighlightRule.UNDERLINE_STRAIGHT -> "Straight underline"
                    HighlightRule.UNDERLINE_DOUBLE -> "Double underline"
                    HighlightRule.UNDERLINE_DASHED -> "Dashed underline"
                    else -> "Wavy underline"
                }
                this.selected = selected
            }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(width = 17.dp, height = 12.dp)) {
            val stroke = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            if (mode == HighlightRule.UNDERLINE_STRAIGHT || mode == HighlightRule.UNDERLINE_DOUBLE) {
                drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke.width, StrokeCap.Round)
                if (mode == HighlightRule.UNDERLINE_DOUBLE) {
                    drawLine(color, Offset(0f, size.height / 2f + 3.dp.toPx()),
                        Offset(size.width, size.height / 2f + 3.dp.toPx()), stroke.width, StrokeCap.Round)
                }
            } else if (mode == HighlightRule.UNDERLINE_DASHED) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height / 2f)
                    lineTo(size.width, size.height / 2f)
                }
                drawPath(path, color, style = Stroke(width = stroke.width,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            } else {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height / 2f)
                    cubicTo(size.width * .2f, 0f, size.width * .3f, size.height, size.width * .5f, size.height / 2f)
                    cubicTo(size.width * .7f, 0f, size.width * .8f, size.height, size.width, size.height / 2f)
                }
                drawPath(path, color, style = stroke)
            }
        }
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
            AppIcons.Gear,
            contentDescription = stringResource(R.string.menu_settings),
            tint = menuText.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── 笔记输入弹窗 ──

private fun toggleReaderBookmark(
    state: ReaderUiState,
    bookmarks: List<com.huangder.lumibooks.domain.model.Bookmark>,
    readView: ReadView?,
    isBookLayout: Boolean,
    isContinuousScrollMode: Boolean,
    viewModel: ReaderViewModel,
    context: Context,
    showToast: Boolean,
    onAdded: (String?) -> Unit
) {
    val characterOffset = when {
        isBookLayout -> null
        isContinuousScrollMode -> 0
        else -> readView?.getCurrentPageStartCharacterOffset()
    }
    val existing = bookmarks.firstOrNull { bookmark ->
        bookmark.chapterIndex == state.currentChapterIndex &&
            ((characterOffset != null && bookmark.characterOffset == characterOffset) ||
                (bookmark.characterOffset == null && bookmark.position.toInt() == state.currentPageIndex))
    }
    if (existing != null) {
        viewModel.deleteBookmark(existing)
        if (showToast) Toast.makeText(context, R.string.bookmark_removed_toast, Toast.LENGTH_SHORT).show()
    } else {
        val addedId = viewModel.addBookmark(
            characterOffset = characterOffset,
            title = if (isBookLayout) null else readView?.getCurrentPageBookmarkTitle()
        )
        onAdded(addedId)
        if (showToast) Toast.makeText(context, R.string.bookmark_added_toast, Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun ReaderBookmarkRemarkSheet(
    bookmarkId: String?,
    text: String,
    tags: List<String> = emptyList(),
    availableTags: List<String> = emptyList(),
    onTagsChange: (List<String>) -> Unit = {},
    backdrop: Backdrop? = null,
    onTextChange: (String) -> Unit,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    NoteInputSheet(
        visible = bookmarkId != null,
        glassBackdrop = backdrop,
        initialText = text,
        onTextChange = onTextChange,
        tags = tags,
        availableTags = availableTags,
        onTagsChange = onTagsChange,
        onConfirm = { bookmarkId?.let { onConfirm(it, text) } },
        onDismiss = onDismiss,
        title = stringResource(R.string.bookmark_remark_title),
        placeholder = stringResource(R.string.bookmark_remark_placeholder),
        footer = stringResource(R.string.bookmark_remark_setting_hint)
    )
}

@Composable
internal fun NoteInputSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    glassBackdrop: Backdrop? = null,
    initialText: String,
    onTextChange: (String) -> Unit,
    tags: List<String> = emptyList(),
    availableTags: List<String> = emptyList(),
    onTagsChange: (List<String>) -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    placeholder: String? = null,
    footer: String? = null
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var isClosing by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateBottomSheetIn()
            focusRequester.requestFocus()
            keyboard?.show()
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

    Box(Modifier.fillMaxSize().imePadding()) {
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
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .fillMaxHeight(0.9f)
                .materialBottomSheetMotion(
                    entryOffset = sheetOffset.value,
                    predictiveBackProgress = predictiveBackProgress
                ),
            contentModifier = Modifier
                .navigationBarsPadding()
                .padding(AppSpace.lg),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backdrop = glassBackdrop
        ) {
            Column(Modifier.fillMaxSize().padding(top = 2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LiquidGlassIconButton(
                        imageVector = AppIcons.X,
                        contentDescription = stringResource(R.string.cancel),
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = AppColors.BgGray
                    )
                    Text(title ?: stringResource(R.string.reader_notes), fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = resolveAppFontFamily(KaiTi), color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    LiquidGlassIconButton(
                        imageVector = AppIcons.Check,
                        contentDescription = stringResource(R.string.confirm),
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

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = initialText,
                        onValueChange = onTextChange,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                        cursorBrush = SolidColor(AppColors.Accent),
                        decorationBox = { inner -> Box {
                            if (initialText.isEmpty()) Text(placeholder ?: stringResource(R.string.note_input_placeholder), fontSize = 14.sp, color = AppColors.TextSecondary)
                            inner()
                        } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp)
                            .clip(RoundedCornerShape(12.dp)).background(AppColors.BgGray)
                            .padding(14.dp).focusRequester(focusRequester),
                        maxLines = 10
                    )
                    AnnotationTagChips(tags, onEdit = { keyboard?.hide(); showTagSheet = true }, modifier = Modifier.padding(top = 8.dp))
                    if (footer != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(footer, fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                }
            }
        }
        if (showTagSheet) {
            AnnotationTagSheet(
                selected = tags,
                backdrop = glassBackdrop,
                available = availableTags,
                onSave = { onTagsChange(it); showTagSheet = false },
                onDismiss = { showTagSheet = false }
            )
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
internal fun NotesListSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    glassBackdrop: Backdrop? = null,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    onNoteClick: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onEditTags: (com.huangder.lumibooks.domain.model.Note) -> Unit,
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
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var selectedLine by remember { mutableStateOf<Int?>(null) }
    val markFilter = ReadingMarkFilter(selectedTag, selectedColor, selectedLine)
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val tabHeight = (40f * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)).dp
    val highlights = notes.filter { !it.isNoteEntry && it.type != "underline" }
    val underlines = notes.filter { !it.isNoteEntry && it.type == "underline" }
    val noteList = notes.filter { it.isNoteEntry }
    // 追踪是否有笔记项处于"已滑开"状态，点空白处时先关闭滑开项而非关闭整个弹窗
    var anyItemRevealed by remember { mutableStateOf(false) }
    var resetRevealedKey by remember { mutableStateOf(0) }

    com.huangder.lumibooks.ui.components.LiquidGlassMenuHost(Modifier.fillMaxSize(), backdrop = glassBackdrop) {
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

        // Leave space for tags and filters; use almost all available height in landscape.
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(if (compactHeight) 0.94f else 0.82f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 20.dp, top = if (compactHeight) 12.dp else 20.dp, end = 20.dp),
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
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 关闭按钮
                LiquidGlassIconButton(
                    imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.reader_close),
                    onClick = { isClosing = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = LightBgGray
                )
            }

            Spacer(Modifier.height(if (compactHeight) 8.dp else 12.dp))

            // Tab 切换器（平滑动画）
            HighlightNoteTabSwitcher(
                activeTag = activeTag,
                onTagChange = { activeTag = it; selectedColor = null; selectedLine = null },
                height = tabHeight
            )
            val unfilteredItems = when (activeTag) {
                "highlight" -> highlights
                "underline" -> underlines
                else -> noteList
            }
            AnnotationListFilters(
                tags = notes.flatMap { it.tags }.distinct().sorted(),
                selectedTag = selectedTag, onTagSelected = { selectedTag = it },
                colors = if (activeTag == "note") emptyList() else unfilteredItems.map { it.color }.distinct(),
                selectedColor = selectedColor, onColorSelected = { selectedColor = it },
                selectedLine = selectedLine, onLineSelected = { selectedLine = it },
                showLines = activeTag == "underline"
            )

            // 列表
            val items = unfilteredItems.filter(markFilter::matches)
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
                            onEditTags = { onEditTags(item) },
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
    onTagChange: (String) -> Unit,
    height: androidx.compose.ui.unit.Dp = 40.dp
) {
    val tabs = listOf(
        "highlight" to R.string.tab_highlight,
        "underline" to R.string.tab_underline,
        "note" to R.string.tab_note
    )
    if (LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current) {
        val selectedIndex = tabs.indexOfFirst { it.first == activeTag }.coerceAtLeast(0)
        LiquidGlassSegmentedControl(
            itemCount = tabs.size,
            selectedIndex = selectedIndex,
            onSelected = { onTagChange(tabs[it].first) },
            modifier = Modifier.fillMaxWidth(),
            trackHeight = height,
            trackPadding = 2.dp
        ) { index, isSelected ->
            Text(
                text = stringResource(tabs[index].second),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) AppColors.TextPrimary.copy(alpha = 0.86f) else LightTextSecondary
            )
        }
        return
    }

    // 动画：白色背景指示器的位置（0=高亮，1=划线，2=笔记）
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
            .height(height)
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
internal fun HighlightNoteItem(
    item: com.huangder.lumibooks.domain.model.Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
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
    SwipeRevealItem(
        onEditTags = onEditTags,
        onDelete = onDelete,
        onClick = onClick,
        resetRevealedKey = resetRevealedKey,
        onRevealedChanged = onRevealedChanged,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .lumiCardSurface(shape = RoundedCornerShape(12.dp))
                .border(1.dp, AppColors.TextSecondary.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            // 左侧高亮色竖条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
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
                AnnotationTagChips(item.tags, modifier = Modifier.padding(top = 6.dp))
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

private const val READER_CONTENT_LOADING_TAG = "reader_content_loading"
private const val READER_CONTENT_READY_TAG = "reader_content_ready"
private const val BOOKMARK_REMOVE_DRAG_RATIO = 0.35f

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

// Keep window/lifecycle effects in a separate Compose group: ReaderScreen is near the JVM method size limit.
@Composable
private fun rememberReaderWindowLifecycle(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    readViewRef: androidx.compose.runtime.MutableState<ReadView?>
): () -> Unit {
    val context = LocalContext.current
    val activity = context as? MainActivity
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
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.onAppForegrounded()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return requestWriteSettingsPermission
}

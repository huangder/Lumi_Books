package com.huangder.lumibooks.service

import android.content.Context
import android.content.Intent
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.huangder.lumibooks.R
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.widget.decodeWidgetCover
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.tts.FloatingSubtitleSettings
import com.huangder.lumibooks.tts.SUBTITLE_SCROLL_MIN_DURATION_MS
import com.huangder.lumibooks.tts.SUBTITLE_SCROLL_SPEED_DP_PER_SECOND
import com.huangder.lumibooks.tts.SUBTITLE_SCROLL_START_DELAY_MS
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsListeningFrame
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsTextSegment
import com.huangder.lumibooks.tts.subtitleScrollDistancePx
import com.huangder.lumibooks.tts.subtitleScrollDurationMs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Singleton
class FloatingSubtitleOverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val ttsController: TtsController,
    private val bookRepository: BookRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private var started = false
    private var appInForeground = false
    private var readerVisible = false
    private var previewActive = false
    private var persistedSettings = FloatingSubtitleSettings()
    private var previewSettings: FloatingSubtitleSettings? = null
    private var playbackState = TtsPlaybackState.IDLE
    private var subtitleSegment: TtsTextSegment? = null
    private var subtitleText: String? = null
    private var motionReduced = false
    private var scrollRestartPending = false
    private var scrollAnimator: ValueAnimator? = null

    private var subtitleView: TextView? = null
    private var overlayView: FloatingSubtitleView? = null
    private var mode = FloatingSubtitleMode.SUBTITLE
    private var geometryAnimator: ValueAnimator? = null
    private var appliedSettings: FloatingSubtitleSettings? = null
    private var dragging = false
    private var moved = false
    private var coverBitmap: android.graphics.Bitmap? = null
    private var bookTitle: String? = null
    private var bookIsPdf = false
    private var listeningFrame = TtsListeningFrame()
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayRegistered = false

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartWindowX = 0
    private var dragStartWindowY = 0

    fun start() {
        if (started) return
        started = true
        scope.launch {
            ttsController.activeBookId.collectLatest { bookId ->
                coverBitmap = null
                bookTitle = null
                overlayView?.setCover(null)
                updateInfo()
                if (bookId != null) {
                    val book = withContext(Dispatchers.IO) { bookRepository.getBookById(bookId) }
                    bookTitle = book?.title
                    bookIsPdf = book?.format == BookFormat.PDF
                    updateInfo()
                    coverBitmap = withContext(Dispatchers.IO) { decodeWidgetCover(book?.coverPath, dp(96f)) }
                    overlayView?.setCover(coverBitmap)
                }
            }
        }
        scope.launch {
            ttsController.listeningFrame.collect { frame ->
                listeningFrame = frame
                updateInfo()
            }
        }
        scope.launch {
            combine(
                dataStoreManager.floatingSubtitleSettings,
                ttsController.playbackState,
                ttsController.currentClause,
                dataStoreManager.motionPreference
            ) { settings, state, utterance, motionPreference ->
                OverlaySnapshot(
                    settings = settings,
                    playbackState = state,
                    utterance = utterance,
                    motionReduced = motionPreference == REDUCED_MOTION_VALUE
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val textChanged = snapshot.utterance != subtitleSegment
                    val motionChanged = snapshot.motionReduced != motionReduced
                    persistedSettings = snapshot.settings
                    playbackState = snapshot.playbackState
                    subtitleSegment = snapshot.utterance
                    subtitleText = snapshot.utterance?.text
                    motionReduced = snapshot.motionReduced
                    if (textChanged || motionChanged) scrollRestartPending = true
                    reconcile()
                }
        }
    }

    private data class OverlaySnapshot(
        val settings: FloatingSubtitleSettings,
        val playbackState: TtsPlaybackState,
        val utterance: TtsTextSegment?,
        val motionReduced: Boolean
    )

    fun setAppInForeground(inForeground: Boolean) {
        if (appInForeground == inForeground) return
        appInForeground = inForeground
        if (inForeground) {
            scrollRestartPending = true
        } else {
            cancelSubtitleScroll(reset = false)
        }
        reconcile()
    }

    fun setReaderVisible(visible: Boolean) {
        readerVisible = visible
        reconcile()
    }

    fun setPreviewActive(active: Boolean) {
        previewActive = active
        if (!active) previewSettings = null
        reconcile()
    }

    fun preview(settings: FloatingSubtitleSettings) {
        previewSettings = settings.normalized()
        reconcile()
    }

    fun refreshPermission() {
        reconcile()
    }

    fun onDisplayConfigurationChanged() {
        geometryAnimator?.cancel()
        geometryAnimator = null
        appliedSettings = null
        scrollRestartPending = true
        reconcile()
    }

    private fun reconcile() {
        val settings = effectiveSettings()
        val canDraw = Settings.canDrawOverlays(context)
        val shouldShowForPlayback = (!appInForeground || !readerVisible) &&
            settings.enabled &&
            playbackState != TtsPlaybackState.IDLE
        val shouldShow = canDraw && (previewActive || shouldShowForPlayback)
        if (!shouldShow) {
            removeOverlay()
            return
        }

        val view = overlayView ?: createOverlayView().also {
            overlayView = it
            subtitleView = it.subtitle
            scrollRestartPending = true
        }
        val params = layoutParams ?: createLayoutParams(settings).also { layoutParams = it }
        updateView(view.subtitle)
        view.appearance(settings)
        view.setPlaying(playbackState == TtsPlaybackState.PLAYING)
        updateInfo()
        if (appliedSettings != settings && geometryAnimator == null && !dragging) {
            updateLayoutParams(params, settings)
            appliedSettings = settings
        }

        if (!overlayRegistered) {
            val registered = runCatching {
                windowManager.addView(view, params)
                overlayRegistered = true
            }.isSuccess
            if (!registered) {
                removeOverlay()
                return
            }
        } else {
            val updated = runCatching { windowManager.updateViewLayout(view, params) }.isSuccess
            if (!updated) {
                removeOverlay()
                return
            }
        }
        if (scrollRestartPending) {
            scrollRestartPending = false
            restartSubtitleScroll(view.subtitle)
        }
    }

    private fun updateInfo() {
        val view = overlayView ?: return
        val frame = listeningFrame
        val index = frame.page?.location?.chapterIndex
        val count = frame.chapter?.count ?: 0
        val chapter = if (index != null && count > 0) context.getString(
            if (bookIsPdf) R.string.tts_full_page else R.string.tts_full_chapter, index + 1, count) else null
        view.setInfo(bookTitle, chapter)
        view.setProgress(listeningProgress(frame))
    }

    private fun createOverlayView() = FloatingSubtitleView(
        context, ::handleTouch,
        onSubtitleClick = {
            transitionTo(if (mode == FloatingSubtitleMode.SUBTITLE) FloatingSubtitleMode.CONTROLS
                else FloatingSubtitleMode.SUBTITLE)
        },
        onToggle = {
            if (playbackState == TtsPlaybackState.PLAYING) ttsController.pause()
            else if (playbackState == TtsPlaybackState.PAUSED) ttsController.resume()
        },
        onPrevious = { ttsController.skip(false) }, onNext = { ttsController.skip(true) },
        onClose = { if (!previewActive) ttsController.stop() },
        onReturn = {
            ttsController.activeBookId.value?.let { bookId ->
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, bookId))
            }
        }
    ).also {
        mode = FloatingSubtitleMode.SUBTITLE
        it.showMode(mode, false)
        it.setCover(coverBitmap)
    }

    private fun createLayoutParams(settings: FloatingSubtitleSettings): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dp(settings.widthDp),
            dp(settings.heightDp),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun updateView(view: TextView) {
        val resolvedText = subtitleText?.takeIf(String::isNotBlank)
            ?: context.getString(
                if (previewActive) R.string.floating_subtitle_preview_text
                else R.string.tts_floating_loading
            )
        // 只在文本真正变化时赋值：重复 setText 会触发一次重新测量，看起来像闪一下。
        if (view.text?.toString() != resolvedText) {
            view.text = resolvedText
        }
        // 关闭动态效果时退回省略号显示，否则由水平滚动展示完整句子。
        view.ellipsize = if (motionReduced) TextUtils.TruncateAt.END else null
    }

    private fun updateLayoutParams(
        params: WindowManager.LayoutParams,
        settings: FloatingSubtitleSettings
    ) {
        val safeBounds = safeDisplayBounds()
        val (width, height) = dimensions(settings)
        if (width != params.width) scrollRestartPending = true
        params.width = width
        params.height = height
        val travelX = (safeBounds.width() - width).coerceAtLeast(0)
        val travelY = (safeBounds.height() - height).coerceAtLeast(0)
        params.x = when (mode) {
            FloatingSubtitleMode.LEFT -> safeBounds.left
            FloatingSubtitleMode.RIGHT -> safeBounds.right - width
            else -> safeBounds.left + (travelX * settings.xFraction).toInt()
        }
        params.y = safeBounds.top + (travelY * settings.yFraction).toInt()
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                geometryAnimator?.cancel()
                geometryAnimator = null
                dragging = true
                moved = false
                // 拖动窗口时停止滚动，避免和手指位移叠加。
                cancelSubtitleScroll(reset = true)
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartWindowX = params.x
                dragStartWindowY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (!moved && abs(event.rawX - dragStartRawX) < slop &&
                    abs(event.rawY - dragStartRawY) < slop) return true
                moved = true
                val bounds = safeDisplayBounds()
                val maxX = (bounds.right - params.width).coerceAtLeast(bounds.left)
                val maxY = (bounds.bottom - params.height).coerceAtLeast(bounds.top)
                params.x = (dragStartWindowX + (event.rawX - dragStartRawX).toInt())
                    .coerceIn(bounds.left, maxX)
                params.y = (dragStartWindowY + (event.rawY - dragStartRawY).toInt())
                    .coerceIn(bounds.top, maxY)
                overlayView?.let { view ->
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    persistDraggedPosition(params)
                } else if (moved) {
                    val bounds = safeDisplayBounds()
                    val target = when {
                        params.x <= bounds.left + dp(24f) -> FloatingSubtitleMode.LEFT
                        params.x + params.width >= bounds.right - dp(24f) -> FloatingSubtitleMode.RIGHT
                        mode == FloatingSubtitleMode.LEFT || mode == FloatingSubtitleMode.RIGHT -> FloatingSubtitleMode.SUBTITLE
                        else -> mode
                    }
                    transitionTo(target)
                } else {
                    overlayView?.subtitle?.performClick()
                }
                return true
            }
        }
        return false
    }

    private fun dimensions(settings: FloatingSubtitleSettings): Pair<Int, Int> {
        val bounds = safeDisplayBounds()
        val docked = mode == FloatingSubtitleMode.LEFT || mode == FloatingSubtitleMode.RIGHT
        val width = if (docked) dp(FloatingSubtitleView.DOCK_WIDTH_DP) else dp(if (mode == FloatingSubtitleMode.CONTROLS)
            maxOf(FloatingSubtitleView.CARD_MIN_WIDTH_DP, settings.widthDp) else settings.widthDp)
        val height = if (docked) dp(FloatingSubtitleView.DOCK_HEIGHT_DP) else dp(settings.heightDp) +
            if (mode == FloatingSubtitleMode.CONTROLS) dp(FloatingSubtitleView.DETAILS_HEIGHT_DP) else 0
        return width.coerceIn(1, bounds.width()) to height.coerceIn(1, bounds.height())
    }

    private fun transitionTo(target: FloatingSubtitleMode) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        geometryAnimator?.cancel()
        val oldMode = mode
        val start = intArrayOf(params.x, params.y, params.width, params.height)
        mode = target
        val settings = effectiveSettings()
        val bounds = safeDisplayBounds()
        val (width, height) = dimensions(settings)
        val x = when {
            target == FloatingSubtitleMode.LEFT -> bounds.left
            target == FloatingSubtitleMode.RIGHT -> bounds.right - width
            !moved && oldMode == FloatingSubtitleMode.LEFT -> bounds.left + dp(12f)
            !moved && oldMode == FloatingSubtitleMode.RIGHT -> bounds.right - width - dp(12f)
            else -> params.x + (params.width - width) / 2
        }.coerceIn(bounds.left, maxOf(bounds.left, bounds.right - width))
        val y = params.y.coerceIn(bounds.top, maxOf(bounds.top, bounds.bottom - height))
        val end = intArrayOf(x, y, width, height)
        view.showMode(target, !motionReduced)
        view.appearance(settings)
        cancelSubtitleScroll(true)
        fun applyFrame(fraction: Float) {
            params.x = (start[0] + (end[0] - start[0]) * fraction).toInt()
            params.y = (start[1] + (end[1] - start[1]) * fraction).toInt()
            params.width = (start[2] + (end[2] - start[2]) * fraction).toInt()
            params.height = (start[3] + (end[3] - start[3]) * fraction).toInt()
            if (overlayRegistered) runCatching { windowManager.updateViewLayout(view, params) }
        }
        if (motionReduced) {
            applyFrame(1f)
            persistDraggedPosition(params)
            restartSubtitleScroll(view.subtitle)
        } else {
            geometryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 280
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { applyFrame(it.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        geometryAnimator = null
                        if (!cancelled) {
                            persistDraggedPosition(params)
                            restartSubtitleScroll(view.subtitle)
                        }
                    }
                })
                start()
            }
        }
    }

    private fun persistDraggedPosition(params: WindowManager.LayoutParams) {
        val bounds = safeDisplayBounds()
        val travelX = (bounds.width() - params.width).coerceAtLeast(0)
        val travelY = (bounds.height() - params.height).coerceAtLeast(0)
        val x = if (travelX == 0) 0f else (params.x - bounds.left).toFloat() / travelX
        val y = if (travelY == 0) 0f else (params.y - bounds.top).toFloat() / travelY
        val normalizedX = x.coerceIn(0f, 1f)
        val normalizedY = y.coerceIn(0f, 1f)
        persistedSettings = persistedSettings.copy(
            xFraction = normalizedX,
            yFraction = normalizedY
        )
        previewSettings = previewSettings?.copy(
            xFraction = normalizedX,
            yFraction = normalizedY
        )
        appliedSettings = effectiveSettings()
        scope.launch {
            dataStoreManager.saveFloatingSubtitlePosition(normalizedX, normalizedY)
        }
    }

    private fun effectiveSettings(): FloatingSubtitleSettings =
        (previewSettings.takeIf { previewActive } ?: persistedSettings).normalized()

    private fun safeDisplayBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            val bounds = Rect(metrics.bounds)
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom
            )
        }
        @Suppress("DEPRECATION")
        val metrics = context.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun removeOverlay() {
        geometryAnimator?.cancel()
        geometryAnimator = null
        cancelSubtitleScroll(reset = false)
        overlayView?.let { view ->
            if (overlayRegistered) {
                runCatching { windowManager.removeViewImmediate(view) }
            }
        }
        overlayRegistered = false
        subtitleView = null
        overlayView = null
        layoutParams = null
        appliedSettings = null
        dragging = false
    }

    /** 重新从句子开头滚一次（只在文本变化、尺寸变化或拖动结束后触发）。 */
    private fun restartSubtitleScroll(view: TextView) {
        cancelSubtitleScroll(reset = true)
        if (motionReduced) return
        view.doOnLayout { laidOutView ->
            if (overlayRegistered && subtitleView === laidOutView) {
                startSubtitleScrollIfNeeded(laidOutView)
            }
        }
    }

    private fun startSubtitleScrollIfNeeded(view: TextView) {
        if (!appInForeground || motionReduced || subtitleView !== view) return
        scrollAnimator?.cancel()
        scrollAnimator = null
        view.scrollX = 0
        val textLayout = view.layout ?: return
        if (textLayout.lineCount <= 0 || view.width <= 0) return
        val distance = subtitleScrollDistancePx(
            textWidthPx = kotlin.math.ceil(textLayout.getLineWidth(0).toDouble()).toInt(),
            viewWidthPx = view.width,
            horizontalPaddingPx = view.paddingLeft + view.paddingRight
        )
        if (distance <= 0) return
        val duration = subtitleScrollDurationMs(
            distancePx = distance,
            speedPxPerSecond = SUBTITLE_SCROLL_SPEED_DP_PER_SECOND *
                context.resources.displayMetrics.density,
            minDurationMs = SUBTITLE_SCROLL_MIN_DURATION_MS
        )
        if (duration <= 0L) return
        scrollAnimator = ValueAnimator.ofInt(0, distance).apply {
            startDelay = com.huangder.lumibooks.ui.animation.VisualMotion.delayMillis(SUBTITLE_SCROLL_START_DELAY_MS)
            this.duration = com.huangder.lumibooks.ui.animation.VisualMotion.durationMillis(duration)
            interpolator = LinearInterpolator()
            addUpdateListener { animator -> view.scrollX = animator.animatedValue as Int }
            start()
        }
    }

    private fun cancelSubtitleScroll(reset: Boolean) {
        scrollAnimator?.cancel()
        scrollAnimator = null
        if (reset) subtitleView?.scrollX = 0
    }

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        const val REDUCED_MOTION_VALUE = "reduced"
    }
}

/** Position of the spoken sentence within its chapter (or PDF page), matching the full-screen player. */
internal fun listeningProgress(frame: TtsListeningFrame): Float {
    val page = frame.page ?: return 0f
    val offset = frame.utterance?.startCharacterOffset ?: page.startCharacterOffset
    val length = frame.chapter?.takeIf { it.index == page.location.chapterIndex }?.text?.length
        ?: page.endCharacterOffset
    return if (length > 0) (offset.toFloat() / length).coerceIn(0f, 1f) else 0f
}

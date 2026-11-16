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
import com.huangder.lumibooks.tts.FloatingSubtitleStyle
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsListeningFrame
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsTextSegment
import com.huangder.lumibooks.tts.lyricsTopLine
import com.huangder.lumibooks.tts.subtitleEstimatedDurationMs
import com.huangder.lumibooks.tts.subtitleScrollDistancePx
import com.huangder.lumibooks.tts.subtitleSpokenPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val bookRepository: BookRepository,
    private val notificationManager: TtsNotificationManager
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
    private var upcomingText: String? = null
    private var spokenRange: TtsTextSegment? = null
    private var speechRate = 1f
    private var motionReduced = false
    private var scrollRestartPending = false
    /** Advances the visible part of the sentence at an estimated pace until the engine reports ranges. */
    private var estimateAnimator: ValueAnimator? = null
    private var scrollAnimator: ValueAnimator? = null
    private var scrollTarget = -1
    /** True once the engine reported a range inside the displayed sentence. */
    private var progressDriven = false

    private val _overlayLocked = MutableStateFlow(false)
    /** True while a locked (touch-through) overlay is on screen; the notification offers to unlock it. */
    val overlayLocked: StateFlow<Boolean> = _overlayLocked.asStateFlow()

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
            // Whole sentences follow the engine's start callback, so the text can no longer lag
            // behind or skip the way word-range driven clauses did with coarse engine callbacks.
            combine(
                dataStoreManager.floatingSubtitleSettings,
                ttsController.playbackState,
                ttsController.currentUtterance,
                ttsController.upcomingUtterance,
                dataStoreManager.motionPreference
            ) { settings, state, utterance, upcoming, motionPreference ->
                OverlaySnapshot(
                    settings = settings,
                    playbackState = state,
                    utterance = utterance,
                    upcoming = upcoming,
                    motionReduced = motionPreference == REDUCED_MOTION_VALUE
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val textChanged = snapshot.utterance != subtitleSegment
                    val motionChanged = snapshot.motionReduced != motionReduced
                    val stateChanged = snapshot.playbackState != playbackState
                    persistedSettings = snapshot.settings
                    playbackState = snapshot.playbackState
                    subtitleSegment = snapshot.utterance
                    subtitleText = snapshot.utterance?.text
                    upcomingText = snapshot.upcoming?.text
                    motionReduced = snapshot.motionReduced
                    if (textChanged) progressDriven = false
                    if (textChanged || motionChanged) scrollRestartPending = true
                    reconcile(animateText = textChanged)
                    if (stateChanged && !scrollRestartPending) syncEstimateWithPlayback()
                }
        }
        scope.launch {
            ttsController.currentSentence.collect { range ->
                spokenRange = range
                applySpokenProgress()
            }
        }
        scope.launch {
            ttsController.speechRate.collect { speechRate = it }
        }
        scope.launch {
            // Locked overlays ignore every touch; keep a way back out in the notification shade.
            _overlayLocked.collect { locked ->
                if (locked) notificationManager.showFloatingLocked() else notificationManager.hideFloatingLocked()
            }
        }
    }

    private data class OverlaySnapshot(
        val settings: FloatingSubtitleSettings,
        val playbackState: TtsPlaybackState,
        val utterance: TtsTextSegment?,
        val upcoming: TtsTextSegment?,
        val motionReduced: Boolean
    )

    /** Locks or unlocks the overlay; locked overlays pass every touch through to the screen below. */
    fun setLocked(locked: Boolean) {
        if (effectiveSettings().locked == locked) return
        persistedSettings = persistedSettings.copy(locked = locked)
        previewSettings = previewSettings?.copy(locked = locked)
        if (locked && mode == FloatingSubtitleMode.CONTROLS) transitionTo(FloatingSubtitleMode.SUBTITLE)
        reconcile()
        scope.launch { dataStoreManager.saveFloatingSubtitleLocked(locked) }
    }

    fun setAppInForeground(inForeground: Boolean) {
        if (appInForeground == inForeground) return
        appInForeground = inForeground
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

    private fun reconcile(animateText: Boolean = false) {
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
        view.appearance(settings)
        updateView(view, animateText && overlayRegistered)
        view.setPlaying(playbackState == TtsPlaybackState.PLAYING)
        view.setLocked(settings.locked)
        updateInfo()
        if (appliedSettings != settings && geometryAnimator == null && !dragging) {
            updateLayoutParams(params, settings)
            appliedSettings = settings
        }
        applyTouchMode(params, settings.locked)

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
        _overlayLocked.value = settings.locked
        // Controls cannot be used through a touch-through window, so a lock always collapses them.
        if (settings.locked && mode == FloatingSubtitleMode.CONTROLS && geometryAnimator == null) {
            transitionTo(FloatingSubtitleMode.SUBTITLE)
        }
        if (scrollRestartPending) {
            scrollRestartPending = false
            restartSubtitleScroll(view.subtitle)
        }
    }

    private fun applyTouchMode(params: WindowManager.LayoutParams, locked: Boolean) {
        params.flags = if (locked) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        // Android 12+ blocks touches that pass through an overlay more opaque than this limit.
        params.alpha = if (locked) maxPassThroughAlpha() else 1f
    }

    private fun maxPassThroughAlpha(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.getSystemService(android.hardware.input.InputManager::class.java)
                    .maximumObscuringOpacityForTouch
            }.getOrDefault(LOCKED_ALPHA).coerceIn(0.2f, LOCKED_ALPHA)
        } else LOCKED_ALPHA

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
        },
        onLock = {
            setLocked(true)
            android.widget.Toast.makeText(context, R.string.floating_subtitle_locked_toast,
                android.widget.Toast.LENGTH_SHORT).show()
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

    private fun updateView(view: FloatingSubtitleView, animate: Boolean) {
        val spoken = subtitleText?.takeIf(String::isNotBlank)
        val resolvedText = spoken ?: context.getString(
            if (previewActive) R.string.floating_subtitle_preview_text
            else R.string.tts_floating_loading
        )
        val next = if (spoken != null) upcomingText
            else if (previewActive) context.getString(R.string.floating_subtitle_preview_next_text) else null
        val changed = view.subtitle.text?.toString() != resolvedText
        // setLines 只在文本真正变化时赋值：重复 setText 会触发一次重新测量，看起来像闪一下。
        view.setLines(resolvedText, next)
        if (changed && animate && !motionReduced && view.currentStyle == FloatingSubtitleStyle.LYRICS) {
            view.subtitle.alpha = 0f
            view.subtitle.translationY = dp(6f).toFloat()
            view.subtitle.animate().alpha(1f).translationY(0f).setDuration(180)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        // 关闭动态效果时条形样式退回省略号显示，否则跟随朗读位置滚动展示完整句子。
        view.subtitle.ellipsize = if (motionReduced && view.currentStyle == FloatingSubtitleStyle.BAR) {
            TextUtils.TruncateAt.END
        } else null
    }

    private fun updateLayoutParams(
        params: WindowManager.LayoutParams,
        settings: FloatingSubtitleSettings
    ) {
        val safeBounds = safeDisplayBounds()
        val (width, height) = dimensions(settings)
        if (width != params.width || height != params.height) scrollRestartPending = true
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
        val barHeight = overlayView?.barHeightPx(settings) ?: dp(settings.heightDp)
        val height = if (docked) dp(FloatingSubtitleView.DOCK_HEIGHT_DP) else barHeight +
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
        _overlayLocked.value = false
        subtitleView = null
        overlayView = null
        layoutParams = null
        appliedSettings = null
        dragging = false
    }

    /** 新句子、尺寸变化或拖动结束后，从句首重新开始跟随朗读位置。 */
    private fun restartSubtitleScroll(view: TextView) {
        cancelSubtitleScroll(reset = true)
        view.doOnLayout { laidOutView ->
            if (overlayRegistered && subtitleView === laidOutView) {
                if (progressDriven) applySpokenProgress() else startEstimate(laidOutView as TextView)
            }
        }
    }

    /** Keeps the position the engine reports in view (line paging in lyrics, sideways in the bar). */
    private fun applySpokenProgress() {
        val view = overlayView ?: return
        val position = subtitleSpokenPosition(subtitleSegment, spokenRange) ?: return
        if (!progressDriven) {
            progressDriven = true
            estimateAnimator?.cancel()
            estimateAnimator = null
        }
        followPosition(view.subtitle, position, animate = true)
    }

    /**
     * Engines without range callbacks still need long sentences to advance. Move through the text
     * at the speech rate's estimated pace; real ranges take over as soon as they arrive.
     */
    private fun startEstimate(view: TextView) {
        estimateAnimator?.cancel()
        estimateAnimator = null
        if (progressDriven || subtitleSegment == null || playbackState == TtsPlaybackState.IDLE) return
        val overlay = overlayView ?: return
        if (motionReduced && overlay.currentStyle == FloatingSubtitleStyle.BAR) return
        val length = view.text?.length ?: 0
        val duration = subtitleEstimatedDurationMs(view.text?.toString().orEmpty(), speechRate)
        if (length <= 0 || duration <= 0L || !needsFollowing(view)) return
        estimateAnimator = ValueAnimator.ofInt(0, length).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                if (subtitleView === view) {
                    followPosition(view, animator.animatedValue as Int,
                        animate = overlay.currentStyle == FloatingSubtitleStyle.LYRICS)
                }
            }
            start()
            if (playbackState != TtsPlaybackState.PLAYING) pause()
        }
    }

    private fun syncEstimateWithPlayback() {
        val animator = estimateAnimator ?: return
        when (playbackState) {
            TtsPlaybackState.PLAYING -> if (animator.isPaused) animator.resume()
            TtsPlaybackState.IDLE -> { animator.cancel(); estimateAnimator = null }
            else -> if (!animator.isPaused) animator.pause()
        }
    }

    private fun needsFollowing(view: TextView): Boolean {
        val textLayout = view.layout ?: return false
        return if (overlayView?.currentStyle == FloatingSubtitleStyle.LYRICS) {
            textLayout.lineCount > FloatingSubtitleView.LYRICS_VISIBLE_LINES
        } else textLayout.lineCount > 0 && subtitleScrollDistancePx(
            textWidthPx = kotlin.math.ceil(textLayout.getLineWidth(0).toDouble()).toInt(),
            viewWidthPx = view.width,
            horizontalPaddingPx = view.paddingLeft + view.paddingRight
        ) > 0
    }

    /** Scrolls so the character at [position] is visible: by line in lyrics, sideways in the bar. */
    private fun followPosition(view: TextView, position: Int, animate: Boolean) {
        val textLayout = view.layout ?: return
        if (textLayout.lineCount <= 0 || view.width <= 0) return
        val lyrics = overlayView?.currentStyle == FloatingSubtitleStyle.LYRICS
        val target = if (lyrics) {
            val line = textLayout.getLineForOffset(position.coerceIn(0, view.text.length))
            textLayout.getLineTop(lyricsTopLine(line, textLayout.lineCount, FloatingSubtitleView.LYRICS_VISIBLE_LINES))
        } else {
            if (motionReduced) return
            val maxScroll = subtitleScrollDistancePx(
                textWidthPx = kotlin.math.ceil(textLayout.getLineWidth(0).toDouble()).toInt(),
                viewWidthPx = view.width,
                horizontalPaddingPx = view.paddingLeft + view.paddingRight
            )
            if (maxScroll <= 0) return
            val visible = view.width - view.paddingLeft - view.paddingRight
            val x = textLayout.getPrimaryHorizontal(position.coerceIn(0, view.text.length))
            (x - visible * FOLLOW_ANCHOR).toInt().coerceIn(0, maxScroll)
        }
        val current = if (lyrics) view.scrollY else view.scrollX
        if (target == scrollTarget && scrollAnimator?.isRunning == true) return
        if (target == current) return
        scrollAnimator?.cancel()
        scrollAnimator = null
        scrollTarget = target
        fun apply(value: Int) = if (lyrics) view.scrollTo(0, value) else view.scrollTo(value, 0)
        if (!animate || motionReduced) {
            apply(target)
            return
        }
        scrollAnimator = ValueAnimator.ofInt(current, target).apply {
            duration = com.huangder.lumibooks.ui.animation.VisualMotion.durationMillis(FOLLOW_ANIMATION_MS)
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator -> if (subtitleView === view) apply(animator.animatedValue as Int) }
            start()
        }
    }

    private fun cancelSubtitleScroll(reset: Boolean) {
        estimateAnimator?.cancel()
        estimateAnimator = null
        scrollAnimator?.cancel()
        scrollAnimator = null
        scrollTarget = -1
        if (reset) subtitleView?.scrollTo(0, 0)
    }

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        const val REDUCED_MOTION_VALUE = "reduced"
        /** Keep the spoken position this far into the visible width so upcoming words show. */
        const val FOLLOW_ANCHOR = 0.6f
        const val FOLLOW_ANIMATION_MS = 220L
        /** Below Android 12's default maximum obscuring opacity, so touches pass through. */
        const val LOCKED_ALPHA = 0.8f
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

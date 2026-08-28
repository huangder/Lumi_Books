package com.huangder.lumibooks.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.tts.FloatingSubtitleSettings
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Singleton
class FloatingSubtitleOverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val ttsController: TtsController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private var started = false
    private var appInForeground = false
    private var previewActive = false
    private var persistedSettings = FloatingSubtitleSettings()
    private var previewSettings: FloatingSubtitleSettings? = null
    private var playbackState = TtsPlaybackState.IDLE
    private var subtitleText: String? = null

    private var subtitleView: TextView? = null
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
            combine(
                dataStoreManager.floatingSubtitleSettings,
                ttsController.playbackState,
                ttsController.currentSentence
            ) { settings, state, sentence -> Triple(settings, state, sentence?.text) }
                .collect { (settings, state, text) ->
                    persistedSettings = settings
                    playbackState = state
                    subtitleText = text
                    reconcile()
                }
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        appInForeground = inForeground
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
        reconcile()
    }

    private fun reconcile() {
        val settings = effectiveSettings()
        val canDraw = Settings.canDrawOverlays(context)
        val shouldShowForPlayback = !appInForeground &&
            settings.enabled &&
            playbackState != TtsPlaybackState.IDLE
        val shouldShow = canDraw && (previewActive || shouldShowForPlayback)
        if (!shouldShow) {
            removeOverlay()
            return
        }

        val view = subtitleView ?: createSubtitleView().also { subtitleView = it }
        val params = layoutParams ?: createLayoutParams(settings).also { layoutParams = it }
        updateView(view, settings)
        updateLayoutParams(params, settings)

        if (!overlayRegistered) {
            runCatching {
                windowManager.addView(view, params)
                overlayRegistered = true
            }.onFailure { removeOverlay() }
        } else {
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { removeOverlay() }
        }
    }

    private fun createSubtitleView(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        isSingleLine = true
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, TypedValue.COMPLEX_UNIT_SP)
        setPadding(dp(14f), 0, dp(14f), 0)
        elevation = dp(4f).toFloat()
        setOnTouchListener { _, event -> handleTouch(event) }
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

    private fun updateView(view: TextView, settings: FloatingSubtitleSettings) {
        view.text = subtitleText?.takeIf(String::isNotBlank)
            ?: context.getString(
                if (previewActive) R.string.floating_subtitle_preview_text
                else R.string.tts_floating_loading
            )
        val baseColor = Color.parseColor(settings.backgroundColorHex)
        val backgroundColor = ColorUtils.setAlphaComponent(
            baseColor,
            (settings.backgroundOpacity * 255f).toInt().coerceIn(0, 255)
        )
        view.setTextColor(if (ColorUtils.calculateLuminance(baseColor) < 0.46) Color.WHITE else Color.BLACK)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            color = android.content.res.ColorStateList.valueOf(backgroundColor)
            cornerRadius = settings.cornerRadiusDp * context.resources.displayMetrics.density
        }
    }

    private fun updateLayoutParams(
        params: WindowManager.LayoutParams,
        settings: FloatingSubtitleSettings
    ) {
        val safeBounds = safeDisplayBounds()
        val width = dp(settings.widthDp).coerceAtMost(safeBounds.width()).coerceAtLeast(1)
        val height = dp(settings.heightDp).coerceAtMost(safeBounds.height()).coerceAtLeast(1)
        params.width = width
        params.height = height
        val travelX = (safeBounds.width() - width).coerceAtLeast(0)
        val travelY = (safeBounds.height() - height).coerceAtLeast(0)
        params.x = safeBounds.left + (travelX * settings.xFraction).toInt()
        params.y = safeBounds.top + (travelY * settings.yFraction).toInt()
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartWindowX = params.x
                dragStartWindowY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val bounds = safeDisplayBounds()
                val maxX = (bounds.right - params.width).coerceAtLeast(bounds.left)
                val maxY = (bounds.bottom - params.height).coerceAtLeast(bounds.top)
                params.x = (dragStartWindowX + (event.rawX - dragStartRawX).toInt())
                    .coerceIn(bounds.left, maxX)
                params.y = (dragStartWindowY + (event.rawY - dragStartRawY).toInt())
                    .coerceIn(bounds.top, maxY)
                subtitleView?.let { view ->
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                persistDraggedPosition(params)
                return true
            }
        }
        return false
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
        subtitleView?.let { view ->
            if (overlayRegistered) {
                runCatching { windowManager.removeViewImmediate(view) }
            }
        }
        overlayRegistered = false
        subtitleView = null
        layoutParams = null
    }

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}

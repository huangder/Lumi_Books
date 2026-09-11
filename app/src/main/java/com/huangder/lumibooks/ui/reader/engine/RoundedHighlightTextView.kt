package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import android.text.style.UpdateAppearance
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ReaderGeometryTextView
import kotlin.math.roundToInt

/** A non-persistent highlight whose opacity is animated after navigating to a search result. */
internal class ReaderSearchHighlightSpan(var alpha: Int) : CharacterStyle(), UpdateAppearance {
    val color: Int
        get() = (alpha.coerceIn(0, 255) shl 24) or 0x00FFE082

    override fun updateDrawState(textPaint: TextPaint) = Unit
}

/** A saved highlight rendered by [RoundedHighlightTextView] instead of a square background span. */
internal class ReaderHighlightSpan(val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) = Unit
}

internal class TtsSentenceHighlightSpan(val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) = Unit

    companion object {
        /** Small theme-aware contrast boost used by every TTS renderer. */
        const val DEFAULT_CONTRAST_DELTA = 0.10f

        fun computeHighlightColor(bgColor: Int, delta: Float = DEFAULT_CONTRAST_DELTA): Int {
            val r = android.graphics.Color.red(bgColor)
            val g = android.graphics.Color.green(bgColor)
            val b = android.graphics.Color.blue(bgColor)
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            return if (luminance > 0.5f) {
                val factor = 1f - delta
                val nr = (r * factor).toInt().coerceIn(0, 255)
                val ng = (g * factor).toInt().coerceIn(0, 255)
                val nb = (b * factor).toInt().coerceIn(0, 255)
                android.graphics.Color.argb(0x66, nr, ng, nb)
            } else {
                val dr = (255 - r)
                val dg = (255 - g)
                val db = (255 - b)
                android.graphics.Color.argb(
                    0x66,
                    (r + dr * delta).toInt().coerceIn(0, 255),
                    (g + dg * delta).toInt().coerceIn(0, 255),
                    (b + db * delta).toInt().coerceIn(0, 255)
                )
            }
        }
    }
}
/** Marks a saved reader underline; rendering is owned by the reader views. */
internal class WaveUnderlineSpan(val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) = Unit
}

/**
 * Keeps Editor's selection controller alive without exposing the OEM popup.
 * ColorOS can tint/wrap a normal transparent drawable and still paint its own
 * handle. A true no-op drawable avoids that second set of pixels; the actual
 * handle is drawn and hit-tested in this view below.
 */
private class OffsetSelectionHandleDrawable(
    private val delegate: Drawable,
    private val extraWidthPx: Int,
    private val isStartHandle: Boolean
) : Drawable() {
    var offsetX: Float = 0f
    var isRtlRun: Boolean = false

    override fun draw(canvas: Canvas) = Unit

    override fun setAlpha(alpha: Int) { delegate.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { delegate.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = if (delegate.opacity == PixelFormat.OPAQUE) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
    // A 1px popup minimizes the chance that the OEM window intercepts touches
    // intended for the corrected in-view handle. The in-view hit radius is the
    // real touch target.
    override fun getIntrinsicWidth(): Int = 1
    override fun getIntrinsicHeight(): Int = 1
    override fun onBoundsChange(bounds: Rect) = Unit
    override fun setTint(tintColor: Int) = Unit
    override fun setTintList(tint: android.content.res.ColorStateList?) = Unit
    override fun setTintMode(tintMode: android.graphics.PorterDuff.Mode?) = Unit
}

internal open class RoundedHighlightTextView(context: Context) : ReaderGeometryTextView(context) {
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightBounds = RectF()
    private val selectionPath = Path()
    private var leftSelectionHandle: OffsetSelectionHandleDrawable? = null
    private var rightSelectionHandle: OffsetSelectionHandleDrawable? = null
    private var selectionHandleColor: Int? = null
    private var draggingSelectionHandle: Boolean? = null
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var nativeSelectionSuppressed = false
    private var internalSelectionMutation = false
    // Not `by lazy`: TextView constructors invoke onTextChanged/visibility callbacks
    // before Kotlin property initializers run, and a lazy delegate getter would NPE.
    private var selectionMagnifier: ReaderSelectionMagnifier? = null
    private val magnifierLongPressRunnable = Runnable { onMagnifierLongPressReady() }
    private val magnifierHandler = Handler(Looper.getMainLooper())
    private val magnifierLongPressTimeout =
        ViewConfiguration.getLongPressTimeout().toLong()
    private val magnifierTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var magnifierPointerDown = false
    private var magnifierLongPressReady = false
    private var magnifierDownX = 0f
    private var magnifierDownY = 0f
    private var magnifierLastTouchX = 0f
    private var magnifierLastTouchY = 0f
    private var draggingSelectionStartHandle = false
    private var draggingHandleMagnifierOffset: Int? = null
    private var draggingHandleMagnifierTrailing = false
    private var magnifierStateReady = false

    /** Selection-only layers keep the native layout/controller but do not paint glyphs. */
    var readerSelectionOnly: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Page slots may justify their final visible line when the page ends mid-paragraph. */
    var readerForceLastLineJustification: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateSelectionHandleOffsets()
            invalidate()
        }

    /** The mode used by the visible TextView's Layout while drawing. */
    var readerJustificationMode: Int = Layout.JUSTIFICATION_MODE_NONE
        set(value) {
            if (field == value) return
            field = value
            installReaderOffsetMapper()
            updateSelectionHandleOffsets()
            invalidate()
        }

    /** Color for the custom selection background; native Layout selection is disabled. */
    var readerSelectionColor: Int = 0x40007AFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        magnifierStateReady = true
        installReaderOffsetMapper()
    }

    /**
     * Editor's handle drag path uses getOffsetAtCoordinate(), which is not
     * exposed as an overridable public API from the app package. The superclass
     * bridge lives in android.widget and forwards this mapper to the same
     * geometry used by selection painting and hit testing.
     */
    private fun installReaderOffsetMapper() {
        readerOffsetMapper = { targetLayout, line, x ->
            val spanned = text as? Spanned
            if (spanned == null || readerJustificationMode == Layout.JUSTIFICATION_MODE_NONE ||
                line !in 0 until targetLayout.lineCount || !x.isFinite()
            ) {
                null
            } else {
                ReaderLineGeometry(
                    layout = targetLayout,
                    text = spanned,
                    justificationMode = readerJustificationMode,
                    forceLastLineJustification = readerForceLastLineJustification
                ).offsetForHorizontal(line, x)
            }
        }
    }

    /** Public counterpart used by framework/OEM code paths that skip the bridge. */
    override fun getOffsetForPosition(x: Float, y: Float): Int {
        if (readerJustificationMode == Layout.JUSTIFICATION_MODE_NONE) {
            return super.getOffsetForPosition(x, y)
        }
        val spanned = text as? Spanned ?: return super.getOffsetForPosition(x, y)
        val textLayout = layout ?: return super.getOffsetForPosition(x, y)
        val localX = x - totalPaddingLeft + scrollX
        val localY = y - totalPaddingTop + scrollY
        if (!localX.isFinite() || !localY.isFinite() || textLayout.lineCount == 0) {
            return super.getOffsetForPosition(x, y)
        }
        val line = textLayout.getLineForVertical(localY.toInt())
        val mapped = ReaderLineGeometry(
            layout = textLayout,
            text = spanned,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = readerForceLastLineJustification
        ).offsetForHorizontal(line, localX)
        return mapped ?: super.getOffsetForPosition(x, y)
    }

    internal fun configureReaderSelectionHandles(color: Int) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        if (selectionHandleColor == color &&
            leftSelectionHandle != null && rightSelectionHandle != null
        ) return
        val density = resources.displayMetrics.density
        // HandleView already guarantees a platform minimum touch target (normally
        // 48dp). Add only a modest transparent gutter so the correction does not
        // make the two selection popups overlap and steal each other's touches.
        val extraWidth = (24f * density).roundToInt()
        fun newHandle(isStart: Boolean): OffsetSelectionHandleDrawable {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                // The real handle is drawn in-view below. Keep the framework
                // Popup/controller alive, but do not expose its OEM-positioned
                // pixels or let its stale window be mistaken for the handle.
                setColor(Color.TRANSPARENT)
                setSize((14 * density).toInt(), (18 * density).toInt())
            }
            return OffsetSelectionHandleDrawable(drawable, extraWidth, isStart)
        }
        val left = newHandle(isStart = true)
        val right = newHandle(isStart = false)
        leftSelectionHandle = left
        rightSelectionHandle = right
        selectionHandleColor = color
        setTextSelectHandleLeft(left)
        setTextSelectHandleRight(right)
        setTextSelectHandle(right)
        updateSelectionHandleOffsets()
        invalidate()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // Editor may move its handle popup without causing another draw pass on
        // the TextView. Refresh the drawable offsets as soon as the range moves.
        updateSelectionHandleOffsets()
        invalidate()
        postInvalidateOnAnimation()
        if (!internalSelectionMutation) {
            updateMagnifierForCurrentSelection()
        }
        // Let Editor finish creating its action mode, then detach only its
        // selection controller. The Editable and Selection spans remain intact,
        // so copy/menu callbacks continue to read the exact same range.
        if (!internalSelectionMutation && selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
            post { suppressNativeSelectionController() }
        }
    }

    private fun suppressNativeSelectionController() {
        if (nativeSelectionSuppressed || internalSelectionMutation) return
        val source = text as? Spannable ?: return
        val start = Selection.getSelectionStart(source)
        val end = Selection.getSelectionEnd(source)
        if (start < 0 || end <= start) return
        nativeSelectionSuppressed = true
        internalSelectionMutation = true
        try {
            // setTextIsSelectable(false) detaches ColorOS' popup controller.
            // Restore a Spannable copy immediately because the platform method
            // otherwise changes the buffer to NORMAL and drops selection spans.
            val copy = android.text.SpannableStringBuilder(source)
            setTextIsSelectable(false)
            setText(copy, android.widget.TextView.BufferType.SPANNABLE)
            (text as? Spannable)?.let {
                Selection.setSelection(it, start.coerceIn(0, it.length), end.coerceIn(0, it.length))
                // setText() replaced the Editable, so listeners attached to the
                // previous buffer no longer observe subsequent handle movement.
                // Give the owning page a chance to re-register them on this live
                // buffer before the drag continues.
                onReaderTextReplaced?.invoke(it)
            }
        } finally {
            internalSelectionMutation = false
        }
        updateSelectionHandleOffsets()
        invalidate()
    }

    private fun restoreNativeSelectionController() {
        if (!nativeSelectionSuppressed || internalSelectionMutation) return
        val source = text as? Spannable ?: return
        val start = Selection.getSelectionStart(source)
        val end = Selection.getSelectionEnd(source)
        internalSelectionMutation = true
        try {
            setTextIsSelectable(true)
            setText(source, android.widget.TextView.BufferType.SPANNABLE)
            (text as? Spannable)?.let {
                if (start >= 0 && end >= 0) {
                    Selection.setSelection(it, start.coerceIn(0, it.length), end.coerceIn(0, it.length))
                }
            }
        } finally {
            internalSelectionMutation = false
            nativeSelectionSuppressed = false
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            updateSelectionHandleOffsets()
            invalidate()
        }
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        super.onTextChanged(text, start, before, count)
        if (!internalSelectionMutation) {
            nativeSelectionSuppressed = false
            endMagnifierPointerSession()
        }
        updateSelectionHandleOffsets()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        endMagnifierPointerSession()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) {
            endMagnifierPointerSession()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != View.VISIBLE) {
            endMagnifierPointerSession()
        }
    }

    override fun onDraw(canvas: Canvas) {
        updateSelectionHandleOffsets()
        if (readerSelectionOnly) {
            drawReaderSelection(canvas)
            drawCustomSelectionHandles(canvas)
            return
        }
        drawRoundedHighlights(canvas)
        drawWaveUnderlines(canvas)
        drawReaderSelection(canvas)
        super.onDraw(canvas)
        drawCustomSelectionHandles(canvas)
    }

    /**
     * ColorOS may anchor the native selection popup at the next line's x=0 when
     * the cursor is at a wrapped-line boundary. Keep that popup transparent and
     * draw the actual touch target in this view, using the corrected geometry.
     */
    private fun drawCustomSelectionHandles(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        val rawStart = Selection.getSelectionStart(spanned)
        val rawEnd = Selection.getSelectionEnd(spanned)
        if (rawStart < 0 || rawEnd < 0 || rawStart == rawEnd) return
        val geometry = ReaderLineGeometry(
            textLayout,
            spanned,
            readerJustificationMode,
            readerForceLastLineJustification
        )
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        handlePaint.color = selectionHandleColor ?: Color.TRANSPARENT
        if (handlePaint.color ushr 24 == 0) return
        // Layout coordinates are content-local. TextView's normal Layout.draw()
        // path applies this translation before painting; custom handles must do
        // the same or they drift by the page margins (and by scroll offset).
        val save = canvas.save()
        canvas.translate(totalPaddingLeft.toFloat() - scrollX, totalPaddingTop.toFloat() - scrollY)
        drawCustomHandle(canvas, geometry, textLayout, start, trailing = false)
        drawCustomHandle(canvas, geometry, textLayout, end, trailing = true)
        canvas.restoreToCount(save)
    }

    private fun drawCustomHandle(
        canvas: Canvas,
        geometry: ReaderLineGeometry,
        textLayout: Layout,
        offset: Int,
        trailing: Boolean
    ) {
        val x = geometry.horizontalPosition(offset, trailing) ?: return
        val line = readerHandleLine(textLayout, offset, trailing)
        val bottom = textLayout.getLineBottom(line).toFloat()
        val stemTop = bottom - 1f
        val circleY = bottom + resources.displayMetrics.density * 7f
        canvas.drawRect(x - 1.5f, stemTop, x + 1.5f, circleY, handlePaint)
        canvas.drawCircle(x, circleY, resources.displayMetrics.density * 8f, handlePaint)
    }

    private fun customHandleHit(eventX: Float, eventY: Float): Boolean {
        val spanned = text as? Spanned ?: return false
        val textLayout = layout ?: return false
        val rawStart = Selection.getSelectionStart(spanned)
        val rawEnd = Selection.getSelectionEnd(spanned)
        if (rawStart < 0 || rawEnd < 0 || rawStart == rawEnd) return false
        val geometry = ReaderLineGeometry(
            textLayout,
            spanned,
            readerJustificationMode,
            readerForceLastLineJustification
        )
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        val forwardSelection = rawStart < rawEnd
        val density = resources.displayMetrics.density
        val hitRadius = 26f * density
        fun near(offset: Int, trailing: Boolean): Boolean {
            val x = geometry.horizontalPosition(offset, trailing) ?: return false
            val safeOffset = offset.coerceIn(0, spanned.length)
            val line = if (trailing && safeOffset > 0 && safeOffset < spanned.length &&
                textLayout.getLineForOffset(safeOffset) > 0 &&
                textLayout.getLineStart(textLayout.getLineForOffset(safeOffset)) == safeOffset
            ) textLayout.getLineForOffset(safeOffset) - 1
            else textLayout.getLineForOffset(safeOffset.coerceAtMost(spanned.length - 1))
            val y = textLayout.getLineBottom(line).toFloat() + density * 7f
            return kotlin.math.abs(eventX - (x + totalPaddingLeft - scrollX)) <= hitRadius &&
                kotlin.math.abs(eventY - (y + totalPaddingTop - scrollY)) <= hitRadius
        }
        return when {
            near(start, false) -> {
                draggingSelectionHandle = forwardSelection
                draggingSelectionStartHandle = true
                draggingHandleMagnifierOffset = start
                draggingHandleMagnifierTrailing = false
                true
            }
            near(end, true) -> {
                draggingSelectionHandle = !forwardSelection
                draggingSelectionStartHandle = false
                draggingHandleMagnifierOffset = end
                draggingHandleMagnifierTrailing = true
                true
            }
            else -> {
                draggingHandleMagnifierOffset = null
                false
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val drag = draggingSelectionHandle
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginMagnifierPointerSession(event.x, event.y)
                if (customHandleHit(event.x, event.y)) {
                    // Touching a custom handle is an explicit selection gesture, so
                    // show the system magnifier immediately instead of waiting for
                    // the long-press timeout.
                    markMagnifierLongPressReady()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateMagnifierForCurrentSelection()
                    return true
                }
                if (nativeSelectionSuppressed) {
                    restoreNativeSelectionController()
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                updateMagnifierPointerPosition(event.x, event.y)
                if (drag != null) {
                    val spanned = text as? Spannable
                    val textLayout = layout
                    if (spanned != null && textLayout != null && spanned.isNotEmpty()) {
                        val localX = event.x - totalPaddingLeft + scrollX
                        val localY = event.y - totalPaddingTop + scrollY
                        val line = textLayout.getLineForVertical(localY.toInt().coerceIn(0, textLayout.height - 1))
                        val mapped = ReaderLineGeometry(
                            textLayout,
                            spanned,
                            readerJustificationMode,
                            readerForceLastLineJustification
                        ).offsetForHorizontal(line, localX)
                        if (mapped != null) {
                            // Anchor on the offset under the finger, not on the
                            // stored start/end: crossing the other handle flips
                            // the selection direction and the stored endpoint then
                            // points at the OTHER handle.
                            val otherEndpoint = if (drag) {
                                Selection.getSelectionEnd(spanned)
                            } else {
                                Selection.getSelectionStart(spanned)
                            }
                            draggingHandleMagnifierOffset = mapped
                            draggingHandleMagnifierTrailing = mapped > otherEndpoint
                            if (drag) Selection.setSelection(spanned, mapped, Selection.getSelectionEnd(spanned))
                            else Selection.setSelection(spanned, Selection.getSelectionStart(spanned), mapped)
                        }
                    }
                    updateMagnifierForCurrentSelection()
                    return true
                }
                val handled = super.onTouchEvent(event)
                updateMagnifierForCurrentSelection()
                return handled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = drag != null
                // End the magnifier session before Editor handles the terminal event:
                // a long-press release can still touch the selection and would
                // otherwise flash the magnifier for one frame.
                endMagnifierPointerSession()
                if (wasDragging) {
                    draggingSelectionHandle = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun beginMagnifierPointerSession(x: Float, y: Float) {
        selectionMagnifier?.dismiss()
        draggingHandleMagnifierOffset = null
        magnifierPointerDown = true
        magnifierLongPressReady = false
        magnifierDownX = x
        magnifierDownY = y
        magnifierLastTouchX = x
        magnifierLastTouchY = y
        magnifierHandler.removeCallbacks(magnifierLongPressRunnable)
        magnifierHandler.postDelayed(magnifierLongPressRunnable, magnifierLongPressTimeout)
    }

    private fun markMagnifierLongPressReady() {
        magnifierHandler.removeCallbacks(magnifierLongPressRunnable)
        magnifierLongPressReady = true
    }

    private fun updateMagnifierPointerPosition(x: Float, y: Float) {
        magnifierLastTouchX = x
        magnifierLastTouchY = y
        if (!magnifierPointerDown || magnifierLongPressReady) return
        if (kotlin.math.abs(x - magnifierDownX) > magnifierTouchSlop ||
            kotlin.math.abs(y - magnifierDownY) > magnifierTouchSlop
        ) {
            magnifierHandler.removeCallbacks(magnifierLongPressRunnable)
        }
    }

    private fun endMagnifierPointerSession() {
        // TextView constructors invoke onTextChanged before Kotlin property
        // initializers run; nothing is set up yet in that window.
        if (!magnifierStateReady) return
        magnifierHandler.removeCallbacks(magnifierLongPressRunnable)
        magnifierPointerDown = false
        magnifierLongPressReady = false
        draggingHandleMagnifierOffset = null
        selectionMagnifier?.dismiss()
    }

    private fun onMagnifierLongPressReady() {
        if (!magnifierPointerDown) return
        magnifierLongPressReady = true
        updateMagnifierForCurrentSelection()
    }

    /**
     * Shows the system magnifier only for real selection gestures: an active custom
     * handle drag, or a non-empty text selection while the finger is still down after
     * the platform long-press timeout. Double-tap word selection is intentionally
     * excluded so the magnifier does not flash on a short second tap.
     */
    private fun updateMagnifierForCurrentSelection() {
        val spanned = text as? Spanned ?: return
        val rawStart = Selection.getSelectionStart(spanned)
        val rawEnd = Selection.getSelectionEnd(spanned)
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        if (start < 0 || end <= start || selectionIsImageOnly(spanned, start, end)) {
            selectionMagnifier?.dismiss()
            return
        }
        val draggingHandle = draggingSelectionHandle != null
        if (!draggingHandle && (!magnifierPointerDown || !magnifierLongPressReady)) return
        val anchor = if (draggingHandle) {
            val offset = draggingHandleMagnifierOffset
            if (offset != null) {
                magnifierAnchorForOffset(offset, draggingHandleMagnifierTrailing)
            } else {
                magnifierAnchorForHandle(draggingSelectionStartHandle)
            }
        } else {
            magnifierAnchorForTouch(magnifierLastTouchX, magnifierLastTouchY)
        }
        if (anchor == null) {
            selectionMagnifier?.dismiss()
            return
        }
        selectionMagnifierOrCreate().showAt(anchor.x, anchor.y, anchor.lineHeight)
    }

    private fun magnifierAnchorForOffset(offset: Int, trailing: Boolean): MagnifierAnchor? {
        val spanned = text as? Spanned ?: return null
        val textLayout = layout ?: return null
        val safeOffset = offset.coerceIn(0, (text?.length ?: 0))
        val x = ReaderLineGeometry(
            textLayout,
            spanned,
            readerJustificationMode,
            readerForceLastLineJustification
        ).horizontalPosition(safeOffset, trailing) ?: return null
        val line = readerHandleLine(textLayout, safeOffset, trailing)
        val lineTop = textLayout.getLineTop(line).toFloat()
        val lineBottom = textLayout.getLineBottom(line).toFloat()
        val anchorX = x + totalPaddingLeft - scrollX
        if (!anchorX.isFinite()) return null
        val anchorY = (lineTop + lineBottom) / 2f + totalPaddingTop - scrollY
        return MagnifierAnchor(anchorX, anchorY, (lineBottom - lineTop).coerceAtLeast(0f))
    }

    private fun selectionMagnifierOrCreate(): ReaderSelectionMagnifier =
        selectionMagnifier ?: ReaderSelectionMagnifier(this).also { selectionMagnifier = it }

    private fun selectionIsImageOnly(spanned: Spanned, start: Int, end: Int): Boolean {
        val images = spanned.getSpans(start, end, ImageSpan::class.java)
        if (images.isEmpty()) return false
        return images.any { image ->
            spanned.getSpanStart(image) <= start && spanned.getSpanEnd(image) >= end
        }
    }

    private data class MagnifierAnchor(val x: Float, val y: Float, val lineHeight: Float)

    private fun magnifierAnchorForTouch(x: Float, y: Float): MagnifierAnchor? {
        val spanned = text as? Spanned ?: return null
        val textLayout = layout ?: return null
        if (textLayout.lineCount == 0) return null
        val localX = x - totalPaddingLeft + scrollX
        val localY = y - totalPaddingTop + scrollY
        if (!localX.isFinite() || !localY.isFinite()) return null
        val line = when {
            localY < 0f -> 0
            localY >= textLayout.height -> textLayout.lineCount - 1
            else -> textLayout.getLineForVertical(localY.toInt())
        }
        val geometry = ReaderLineGeometry(
            textLayout,
            spanned,
            readerJustificationMode,
            readerForceLastLineJustification
        )
        val lineStart = textLayout.getLineStart(line)
        val rawLineEnd = textLayout.getLineEnd(line)
        val contentEnd = readerLineContentEnd(spanned, lineStart, rawLineEnd)
        val range = if (contentEnd > lineStart) {
            geometry.horizontalRange(line, lineStart, contentEnd)
        } else {
            null
        } ?: ReaderLineGeometry.HorizontalRange(
            textLayout.getLineLeft(line),
            textLayout.getLineRight(line)
        )
        val left = range.left + totalPaddingLeft - scrollX
        val right = range.right + totalPaddingLeft - scrollX
        val minX = minOf(left, right)
        val maxX = maxOf(left, right)
        val anchorX = if (maxX - minX < 1f) minX else x.coerceIn(minX, maxX)
        val lineTop = textLayout.getLineTop(line).toFloat()
        val lineBottom = textLayout.getLineBottom(line).toFloat()
        val anchorY = (lineTop + lineBottom) / 2f + totalPaddingTop - scrollY
        return MagnifierAnchor(anchorX, anchorY, (lineBottom - lineTop).coerceAtLeast(0f))
    }

    private fun magnifierAnchorForHandle(isStartHandle: Boolean): MagnifierAnchor? {
        val spanned = text as? Spanned ?: return null
        val textLayout = layout ?: return null
        val rawStart = Selection.getSelectionStart(spanned)
        val rawEnd = Selection.getSelectionEnd(spanned)
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        if (start < 0 || end <= start) return null
        val offset = if (isStartHandle) start else end
        val trailing = !isStartHandle
        val x = ReaderLineGeometry(
            textLayout,
            spanned,
            readerJustificationMode,
            readerForceLastLineJustification
        ).horizontalPosition(offset, trailing) ?: return null
        val line = readerHandleLine(textLayout, offset, trailing)
        val lineTop = textLayout.getLineTop(line).toFloat()
        val lineBottom = textLayout.getLineBottom(line).toFloat()
        val anchorX = x + totalPaddingLeft - scrollX
        if (!anchorX.isFinite()) return null
        val anchorY = (lineTop + lineBottom) / 2f + totalPaddingTop - scrollY
        return MagnifierAnchor(anchorX, anchorY, (lineBottom - lineTop).coerceAtLeast(0f))
    }

    /** Line used by the drawn handle for [offset]; kept in sync with [drawCustomHandle]. */
    private fun readerHandleLine(textLayout: Layout, offset: Int, trailing: Boolean): Int {
        val length = text?.length ?: 0
        val safeOffset = offset.coerceIn(0, length)
        return if (trailing && safeOffset > 0 && safeOffset < length &&
            textLayout.getLineForOffset(safeOffset) > 0 &&
            textLayout.getLineStart(textLayout.getLineForOffset(safeOffset)) == safeOffset
        ) {
            textLayout.getLineForOffset(safeOffset) - 1
        } else {
            textLayout.getLineForOffset(safeOffset.coerceAtMost((length - 1).coerceAtLeast(0)))
        }
    }

    /** Test-only hook: replaces the platform magnifier with an observable fake sink. */
    internal fun setMagnifierSinkForTest(sink: ReaderMagnifierSink?) {
        selectionMagnifierOrCreate().sinkOverride = sink
    }

    /**
     * True after a custom handle accepted ACTION_DOWN and until the stream ends.
     * ReadView uses this to keep page-swipe classification from stealing the
     * subsequent MOVE/UP events from this TextView.
     */
    internal fun isReaderSelectionHandleDragActive(): Boolean =
        draggingSelectionHandle != null

    /** Called when the OEM-controller workaround replaces the Editable buffer. */
    internal var onReaderTextReplaced: ((Spannable) -> Unit)? = null

    private fun updateSelectionHandleOffsets() {
        val spanned = text as? Spanned
        val textLayout = layout
        if (spanned == null || textLayout == null) {
            leftSelectionHandle?.offsetX = 0f
            rightSelectionHandle?.offsetX = 0f
            leftSelectionHandle?.isRtlRun = false
            rightSelectionHandle?.isRtlRun = false
            return
        }
        val rawStart = Selection.getSelectionStart(spanned)
        val rawEnd = Selection.getSelectionEnd(spanned)
        if (rawStart < 0 || rawEnd < 0 || rawStart == rawEnd) {
            leftSelectionHandle?.offsetX = 0f
            rightSelectionHandle?.offsetX = 0f
            leftSelectionHandle?.isRtlRun = false
            rightSelectionHandle?.isRtlRun = false
            return
        }
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        val geometry = ReaderLineGeometry(
            textLayout,
            spanned,
            readerJustificationMode,
            readerForceLastLineJustification
        )
        val nativeStart = textLayout.getPrimaryHorizontal(start).takeIf { it.isFinite() } ?: 0f
        val nativeEnd = textLayout.getPrimaryHorizontal(end).takeIf { it.isFinite() } ?: nativeStart
        val correctedStart = geometry.horizontalPosition(start) ?: nativeStart
        val correctedEnd = geometry.horizontalPosition(end, trailing = true) ?: nativeEnd
        leftSelectionHandle?.isRtlRun = runCatching { textLayout.isRtlCharAt(start) }.getOrDefault(false)
        rightSelectionHandle?.isRtlRun = runCatching { textLayout.isRtlCharAt(end) }.getOrDefault(false)
        leftSelectionHandle?.offsetX = (correctedStart - nativeStart).takeIf { it.isFinite() } ?: 0f
        rightSelectionHandle?.offsetX = (correctedEnd - nativeEnd).takeIf { it.isFinite() } ?: 0f
    }

    private fun drawReaderSelection(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        val rawStart = Selection.getSelectionStart(spanned)
        val rawEnd = Selection.getSelectionEnd(spanned)
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        if (start < 0 || end <= start || readerSelectionColor ushr 24 == 0) return

        val geometry = ReaderLineGeometry(
            layout = textLayout,
            text = spanned,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = readerForceLastLineJustification
        )
        selectionPaint.color = readerSelectionColor
        val saveCount = canvas.save()
        canvas.translate(totalPaddingLeft.toFloat() - scrollX, totalPaddingTop.toFloat() - scrollY)
        val firstLine = textLayout.getLineForOffset(start)
        val lastLine = textLayout.getLineForOffset((end - 1).coerceAtLeast(start))
        for (line in firstLine..lastLine) {
            val range = geometry.horizontalRange(
                line,
                maxOf(start, textLayout.getLineStart(line)),
                minOf(end, readerLineContentEnd(spanned, textLayout.getLineStart(line), textLayout.getLineEnd(line)))
            ) ?: continue
            val top = textLayout.getLineTop(line).toFloat()
            val bottom = textLayout.getLineBottom(line).toFloat()
            canvas.drawRect(range.left, top, range.right, bottom, selectionPaint)
        }
        canvas.restoreToCount(saveCount)
    }

    /** Returns the same horizontal coordinate used by reader highlights. */
    internal fun readerHorizontalPosition(offset: Int, trailing: Boolean = false): Float? {
        val spanned = text as? Spanned ?: return null
        val textLayout = layout ?: return null
        return ReaderLineGeometry(
            layout = textLayout,
            text = spanned,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = readerForceLastLineJustification
        ).horizontalPosition(offset, trailing)
    }

    internal fun readerOffsetForHorizontal(line: Int, x: Float): Int? {
        val spanned = text as? Spanned ?: return null
        val textLayout = layout ?: return null
        return ReaderLineGeometry(
            layout = textLayout,
            text = spanned,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = readerForceLastLineJustification
        ).offsetForHorizontal(line, x)
    }

    /** Draws each non-blank line segment without changing the underlying text layout. */
    private fun drawWaveUnderlines(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        if (spanned.isEmpty() || textLayout.lineCount == 0) return
        val density = resources.displayMetrics.density
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
        }
        val saveCount = canvas.save()
        canvas.translate(
            totalPaddingLeft.toFloat() - scrollX,
            totalPaddingTop.toFloat() - scrollY
        )
        spanned.getSpans(0, spanned.length, WaveUnderlineSpan::class.java).forEach { span ->
            val spanStart = spanned.getSpanStart(span).coerceIn(0, spanned.length)
            val spanEnd = spanned.getSpanEnd(span).coerceIn(spanStart, spanned.length)
            if (spanStart >= spanEnd) return@forEach
            wavePaint.color = span.color
            val amplitude = 1.6f * density
            val wavelength = 5.5f * density
            for (line in textLayout.getLineForOffset(spanStart)..textLayout.getLineForOffset(spanEnd - 1)) {
                val layoutLineStart = textLayout.getLineStart(line)
                val rawLineEnd = textLayout.getLineEnd(line)
                val contentEnd = readerLineContentEnd(spanned, layoutLineStart, rawLineEnd)
                val lineStart = maxOf(spanStart, layoutLineStart)
                val lineEnd = minOf(spanEnd, contentEnd)
                if (lineStart >= lineEnd || !spanned.substring(lineStart, lineEnd).any { !it.isWhitespace() }) continue
                val geometry = ReaderLineGeometry(
                    layout = textLayout,
                    text = spanned,
                    justificationMode = readerJustificationMode,
                    forceLastLineJustification = readerForceLastLineJustification
                )
                val range = geometry.horizontalRange(line, lineStart, lineEnd) ?: continue
                val x0 = range.left
                val x1 = range.right
                if (x1 <= x0) continue
                val baseline = textLayout.getLineBaseline(line).toFloat()
                val underlineCenter = baseline + paint.fontMetrics.descent.coerceAtLeast(1f) + 1f * density
                val path = android.graphics.Path()
                var x = x0
                var first = true
                while (x <= x1) {
                    val y = underlineCenter + amplitude * kotlin.math.sin((x - x0) / wavelength * 2.0 * Math.PI)
                    if (first) { path.moveTo(x, y.toFloat()); first = false } else { path.lineTo(x, y.toFloat()) }
                    x += 1f
                }
                canvas.drawPath(path, wavePaint)
            }
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawRoundedHighlights(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        if (spanned.isEmpty() || textLayout.lineCount == 0) return

        val saveCount = canvas.save()
        canvas.translate(
            totalPaddingLeft.toFloat() - scrollX,
            totalPaddingTop.toFloat() - scrollY
        )
        spanned.getSpans(0, spanned.length, ReaderHighlightSpan::class.java).forEach { span ->
            drawRoundedHighlight(canvas, spanned, textLayout, span, span.color)
        }
        spanned.getSpans(0, spanned.length, ReaderSearchHighlightSpan::class.java).forEach { span ->
            drawRoundedHighlight(canvas, spanned, textLayout, span, span.color)
        }
        spanned.getSpans(0, spanned.length, TtsSentenceHighlightSpan::class.java).forEach { span ->
            drawTtsSentenceHighlight(canvas, textLayout, span, span.color)
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawRoundedHighlight(
        canvas: Canvas,
        spanned: Spanned,
        textLayout: Layout,
        span: Any,
        color: Int
    ) {
        if (color ushr 24 == 0) return
        val spanStart = spanned.getSpanStart(span).coerceIn(0, spanned.length)
        val spanEnd = spanned.getSpanEnd(span).coerceIn(spanStart, spanned.length)
        if (spanStart >= spanEnd) return

        val density = resources.displayMetrics.density
        val horizontalPadding = 3f * density
        val glyphPadding = 2f * density
        val minimumLineGap = 1.5f * density
        val cornerRadius = 6f * density
        val fontMetrics = paint.fontMetrics
        val firstLine = textLayout.getLineForOffset(spanStart)
        val lastLine = textLayout.getLineForOffset(spanEnd - 1)
        highlightPaint.color = color
        val geometry = ReaderLineGeometry(
            layout = textLayout,
            text = spanned,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = readerForceLastLineJustification
        )

        for (line in firstLine..lastLine) {
            val lineStart = textLayout.getLineStart(line)
            val rawLineEnd = textLayout.getLineEnd(line)
            val contentEnd = readerLineContentEnd(spanned, lineStart, rawLineEnd)
            val segmentStart = maxOf(spanStart, lineStart)
            val segmentEnd = minOf(spanEnd, contentEnd)
            if (segmentStart >= segmentEnd) continue

            val paragraphIsLtr = textLayout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT
            val geometryRange = geometry.horizontalRange(line, segmentStart, segmentEnd) ?: continue
            val segmentStartX = geometryRange.left
            val segmentEndX = geometryRange.right
            // In ordinary LTR text the endpoint metrics are the exact character
            // advances used by Layout.draw(). Selection-path bounds can include an
            // adjacent run at punctuation boundaries, so reserve them for RTL or
            // mixed-direction lines only.
            val hasRtlRun = !paragraphIsLtr || (segmentStart until segmentEnd)
                .any { offset -> textLayout.isRtlCharAt(offset) }
            val pathBounds = if (hasRtlRun) {
                selectionPath.reset()
                textLayout.getSelectionPath(segmentStart, segmentEnd, selectionPath)
                RectF().also { selectionPath.computeBounds(it, true) }
            } else {
                null
            }
            val segmentLeft = if (pathBounds != null && !pathBounds.isEmpty() && !paragraphIsLtr) {
                pathBounds.left
            } else {
                minOf(segmentStartX, segmentEndX)
            }
            val segmentRight = if (pathBounds != null && !pathBounds.isEmpty() && !paragraphIsLtr) {
                pathBounds.right
            } else {
                maxOf(segmentStartX, segmentEndX)
            }
            if (segmentRight <= segmentLeft) continue

            val lineTop = textLayout.getLineTop(line).toFloat() + minimumLineGap
            val lineBottom = textLayout.getLineBottom(line).toFloat() - minimumLineGap
            val baseline = textLayout.getLineBaseline(line).toFloat()
            val glyphTop = baseline + fontMetrics.ascent - glyphPadding
            val glyphBottom = baseline + fontMetrics.descent + glyphPadding
            val top = glyphTop.coerceAtLeast(lineTop)
            val bottom = glyphBottom.coerceAtMost(lineBottom)
            if (bottom <= top) continue

            highlightBounds.set(
                (segmentLeft - horizontalPadding).coerceAtLeast(-horizontalPadding),
                top,
                (segmentRight + horizontalPadding).coerceAtMost(textLayout.width + horizontalPadding),
                bottom
            )
            val radius = minOf(cornerRadius, highlightBounds.height() / 2f)
            canvas.drawRoundRect(highlightBounds, radius, radius, highlightPaint)
        }
    }

    /** 褰撳墠鍙ュ彞 TTS 楂樹寒锛氭暣涓彞瀛愬潡鍏辩敤涓€涓ぇ鍦嗚鐭╁舰锛堣法琛屾暣浣?*/
    private fun drawTtsSentenceHighlight(
        canvas: Canvas,
        textLayout: Layout,
        span: TtsSentenceHighlightSpan,
        color: Int
    ) {
        if (color ushr 24 == 0) return
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(span).coerceIn(0, spanned.length)
        val spanEnd = spanned.getSpanEnd(span).coerceIn(spanStart, spanned.length)
        if (spanStart >= spanEnd) return

        val density = resources.displayMetrics.density
        val horizontalPadding = 3f * density
        val cornerRadius = 12f * density
        val minimumLineGap = 1.5f * density
        val firstLine = textLayout.getLineForOffset(spanStart)
        val lastLine = textLayout.getLineForOffset(spanEnd - 1)
        val top = textLayout.getLineTop(firstLine).toFloat() + minimumLineGap
        val bottom = textLayout.getLineBottom(lastLine).toFloat() - minimumLineGap
        if (bottom <= top) return

        highlightPaint.color = color
        highlightBounds.set(
            -horizontalPadding,
            top,
            textLayout.width + horizontalPadding,
            bottom
        )
        val radius = minOf(cornerRadius, highlightBounds.height() / 2f)
        canvas.drawRoundRect(highlightBounds, radius, radius, highlightPaint)
    }
}

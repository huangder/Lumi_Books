package com.huangder.lumibooks.ui.reader.engine

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Magnifier
import androidx.annotation.RequiresApi

/**
 * Test seam for the platform magnifier. Production always uses
 * [PlatformReaderMagnifierSink]; reader instrumented tests inject a fake sink so the
 * trigger/geometry state machine can be observed without a real popup window.
 */
internal interface ReaderMagnifierSink {
    val windowHeightPx: Int

    fun show(sourceX: Float, sourceY: Float, windowCenterX: Float, windowCenterY: Float)

    fun update()

    fun dismiss()
}

/**
 * Drives the system text magnifier for reader selections.
 *
 * The ColorOS toolbar workaround in [RoundedHighlightTextView] detaches the framework
 * selection controller, which also removes the magnifier owned by that controller.
 * This helper calls the same public platform widget ([Magnifier]) again, so no magnified
 * content is drawn by the app itself: the system copies the window surface around the
 * anchor and renders its own floating window.
 */
internal class ReaderSelectionMagnifier(
    private val host: View,
    private val sinkFactory: (View) -> ReaderMagnifierSink? = { view -> createPlatformSink(view) }
) {
    private var sink: ReaderMagnifierSink? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private val updateRunnable = Runnable { refreshContent() }

    /** Test-only override. When set, it takes precedence over [sinkFactory]. */
    internal var sinkOverride: ReaderMagnifierSink? = null

    var isShowing: Boolean = false
        private set

    /**
     * Shows (or repositions) the system magnifier.
     *
     * [anchorX]/[anchorY] are host-view coordinates of the point that should appear in
     * the center of the magnified content. [lineHeightPx] is used to keep the floating
     * window above the anchored text line, matching the framework behavior.
     */
    fun showAt(anchorX: Float, anchorY: Float, lineHeightPx: Float) {
        val ready = sinkOverride != null || host.isAttachedToWindow
        if (!ready || host.width <= 0 || host.height <= 0) return
        if (!anchorX.isFinite() || !anchorY.isFinite()) return
        val activeSink = ensureSink() ?: return
        val density = host.resources.displayMetrics.density
        val windowCenterX = anchorX
        val windowCenterY = readerMagnifierWindowCenterY(
            anchorY = anchorY,
            windowHeightPx = activeSink.windowHeightPx,
            lineHeightPx = lineHeightPx,
            density = density
        )
        val shown = runCatching {
            activeSink.show(anchorX, anchorY, windowCenterX, windowCenterY)
        }.isSuccess
        isShowing = shown
        if (shown) attachDrawListener()
    }

    /** Refreshes the copied content, e.g. after the selection highlight itself changed. */
    fun update() {
        if (isShowing) runCatching { activeSink()?.update() }
    }

    fun dismiss() {
        detachDrawListener()
        host.removeCallbacks(updateRunnable)
        runCatching { activeSink()?.dismiss() }
        isShowing = false
    }

    private fun refreshContent() {
        if (!isShowing || !host.isAttachedToWindow) return
        runCatching { activeSink()?.update() }
    }

    private fun activeSink(): ReaderMagnifierSink? = sinkOverride ?: sink

    private fun ensureSink(): ReaderMagnifierSink? {
        sinkOverride?.let { return it }
        sink?.let { return it }
        val created = runCatching { sinkFactory(host) }.getOrNull() ?: return null
        sink = created
        return created
    }

    private fun attachDrawListener() {
        if (drawListener != null || !host.isAttachedToWindow) return
        val observer = host.viewTreeObserver
        if (!observer.isAlive) return
        val listener = ViewTreeObserver.OnDrawListener {
            if (!isShowing) return@OnDrawListener
            // update() copies the window surface, so it must run after this frame's
            // selection highlight and handles have been committed to the surface.
            host.removeCallbacks(updateRunnable)
            host.post(updateRunnable)
        }
        drawListener = listener
        observer.addOnDrawListener(listener)
    }

    private fun detachDrawListener() {
        val listener = drawListener ?: return
        drawListener = null
        val observer = host.viewTreeObserver
        if (observer.isAlive) observer.removeOnDrawListener(listener)
    }
}

/** Vertical window center that keeps the floating magnifier above the anchored line. */
internal fun readerMagnifierWindowCenterY(
    anchorY: Float,
    windowHeightPx: Int,
    lineHeightPx: Float,
    density: Float
): Float = anchorY -
    (windowHeightPx / 2f + lineHeightPx.coerceAtLeast(0f) / 2f + 8f * density)

private fun createPlatformSink(view: View): ReaderMagnifierSink? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { PlatformReaderMagnifierSink(view) }.getOrNull()
    } else {
        null
    }

/**
 * Public-API wrapper around the system magnifier with framework defaults (size, zoom,
 * corner radius, elevation and overlay untouched).
 */
@RequiresApi(Build.VERSION_CODES.Q)
private class PlatformReaderMagnifierSink(view: View) : ReaderMagnifierSink {
    private val magnifier: Magnifier = Magnifier.Builder(view).build()

    override val windowHeightPx: Int
        get() = magnifier.height

    override fun show(
        sourceX: Float,
        sourceY: Float,
        windowCenterX: Float,
        windowCenterY: Float
    ) {
        magnifier.show(sourceX, sourceY, windowCenterX, windowCenterY)
    }

    override fun update() = magnifier.update()

    override fun dismiss() = magnifier.dismiss()
}

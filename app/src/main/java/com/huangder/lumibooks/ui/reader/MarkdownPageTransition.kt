package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.huangder.lumibooks.ui.reader.engine.*

/** Two viewport snapshots only; uses the reader's existing slide/curl geometry. */
internal class MarkdownPageTransition(context: Context, private val before: Bitmap,
    private val after: Bitmap, private val paper: Int) : FrameLayout(context) {
    private class Page(context: Context, override val pageBitmap: Bitmap) : View(context), PageBitmapSource {
        override fun onDraw(canvas: Canvas) { if (!pageBitmap.isRecycled) canvas.drawBitmap(pageBitmap, 0f, 0f, null) }
    }
    private var controller: PageAnimationController? = null
    private var fade: android.animation.ValueAnimator? = null
    private var fadeProgress = 0f
    private var completed = false
    private var interactive = false
    private var forwardDirection = true
    var finished: () -> Unit = {}
    fun start(mode: String, duration: Int, forward: Boolean) {
        interactive = false
        forwardDirection = forward
        completed = false
        val old = Page(context, before)
        val target = Page(context, after)
        val unused = Page(context, before)
        if (mode == "scroll") listOf(unused, target, old).forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        val surface = PageAnimationSurface(this, if (forward) unused else target, old,
            if (forward) target else unused, { paper }, snapTranslationsToPixels = true)
        val direction = if (forward) PageAnimationController.Direction.NEXT else PageAnimationController.Direction.PREV
        if (mode == "fade") {
            fade = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration.toLong()
                addUpdateListener { fadeProgress = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { completed = true; invalidate(); finished() }
                })
                start()
            }
            return
        }
        controller = when (mode) {
            "curl" -> CurlPageAnim(surface, trackCornerTouchDirectly = false, baseDurationMs = duration)
            "scroll" -> ScrollPageAnim(surface, duration)
            else -> SlidePageAnim(surface, duration)
        }.also { anim ->
            anim.onCanFlip = { true }
            anim.onAnimationComplete = { completed = true; invalidate(); finished() }
            when (anim) {
                is CurlPageAnim -> anim.startFromTap(direction)
                is ScrollPageAnim -> anim.startFromTap(direction)
                is SlidePageAnim -> anim.startFromTap(direction)
            }
        }
    }

    /** Starts an interactive transition after the host has prepared both snapshots. */
    fun beginInteractive(mode: String, duration: Int, forward: Boolean, down: MotionEvent) {
        if (mode == "fade" || mode == "none") return
        interactive = true
        forwardDirection = forward
        completed = false
        val old = Page(context, before)
        val target = Page(context, after)
        val spare = Page(context, before)
        val surface = PageAnimationSurface(this, if (forward) spare else target, old,
            if (forward) target else spare, { paper }, snapTranslationsToPixels = true)
        val direction = if (forward) PageAnimationController.Direction.NEXT else PageAnimationController.Direction.PREV
        controller = when (mode) {
            "curl" -> CurlPageAnim(surface, trackCornerTouchDirectly = false, baseDurationMs = duration)
            "scroll" -> ScrollPageAnim(surface, duration)
            else -> SlidePageAnim(surface, duration)
        }.also { anim ->
            anim.onCanFlip = { true }
            anim.onAnimationComplete = { completed = true; interactive = false; invalidate(); finished() }
            addView(old, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(target, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            anim.onTouchEvent(down)
            anim.confirmPageGesture()
            // Keep direction available to animation implementations that initialize on MOVE.
            if (direction != PageAnimationController.Direction.NONE) invalidate()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val anim = controller ?: return false
        val handled = anim.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            interactive = false
        }
        postInvalidateOnAnimation()
        return handled
    }

    fun cancelInteractive() {
        controller?.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))
        interactive = false
    }

    val isInteractive: Boolean get() = interactive
    override fun dispatchDraw(canvas: Canvas) {
        if (before.isRecycled || after.isRecycled) return
        if (completed) {
            canvas.drawBitmap(after, 0f, 0f, null)
            return
        }
        if (fade != null) {
            canvas.drawColor(paper)
            val paint = android.graphics.Paint().apply { alpha = ((1 - fadeProgress) * 255).toInt() }
            canvas.drawBitmap(before, 0f, 0f, paint)
            paint.alpha = (fadeProgress * 255).toInt()
            canvas.drawBitmap(after, 0f, 0f, paint)
        } else {
            controller?.onDraw(canvas)
            if (controller is ScrollPageAnim) super.dispatchDraw(canvas)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = dispatchTouchEvent(event)
    override fun computeScroll() {
        if (controller?.computeScroll() == true) postInvalidateOnAnimation()
    }
    fun release() {
        finished = {}
        controller?.abortAnim(); controller = null
        fade?.cancel(); fade = null
        before.recycle(); after.recycle()
    }
}

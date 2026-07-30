package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.view.View

/**
 * Instant page turn controller used by e-ink mode.
 *
 * It preserves tap/drag/long-press handling from [PageAnimationController], but
 * commits a valid page flip immediately without drawing intermediate frames.
 */
class NoPageAnim(readView: PageAnimationSurface) : PageAnimationController(readView) {

    override fun onDraw(canvas: Canvas) {
        resetPageViews()
    }

    fun startFromTap(dir: Direction) {
        if (dir == Direction.NONE || isRunning) return
        direction = dir
        isFlipAnim = onCanFlip?.invoke(dir) == true
        if (isFlipAnim) {
            completeImmediately()
        } else {
            direction = Direction.NONE
            resetPageViews()
            readView.invalidate()
        }
    }

    override fun startAnim(fromDrag: Boolean) {
        if (direction == Direction.NONE) {
            resetPageViews()
            readView.invalidate()
            return
        }
        if (onCanFlip?.invoke(direction) == true) {
            isFlipAnim = true
            completeImmediately()
        } else {
            direction = Direction.NONE
            isFlipAnim = false
            isRunning = false
            isDragging = false
            resetPageViews()
            readView.invalidate()
        }
    }

    override fun computeScroll(): Boolean = false

    override fun abortAnim() {
        direction = Direction.NONE
        isFlipAnim = false
        isRunning = false
        isDragging = false
        resetPageViews()
        readView.invalidate()
    }

    override fun getOffsetX(): Float = 0f

    private fun completeImmediately() {
        isRunning = false
        isDragging = false
        resetPageViews()
        readView.invalidate()
        onAnimationComplete?.invoke()
        direction = Direction.NONE
        isFlipAnim = false
        readView.invalidate()
    }

    private fun resetPageViews() {
        readView.curPageView.visibility = View.VISIBLE
        readView.prevPageView.visibility = View.INVISIBLE
        readView.nextPageView.visibility = View.INVISIBLE
        readView.curPageView.translationX = 0f
        readView.curPageView.translationY = 0f
        readView.curPageView.alpha = 1f
        readView.curPageView.translationZ = 1f
        readView.prevPageView.translationX = idleTranslationX(Direction.PREV, readView.width.toFloat())
        readView.prevPageView.translationY = 0f
        readView.prevPageView.alpha = 0f
        readView.prevPageView.translationZ = 0f
        readView.nextPageView.translationX = idleTranslationX(Direction.NEXT, readView.width.toFloat())
        readView.nextPageView.translationY = 0f
        readView.nextPageView.alpha = 0f
        readView.nextPageView.translationZ = 0f
    }
}

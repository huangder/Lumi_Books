package com.huangder.lumibooks.ui.reader

import android.graphics.Canvas
import android.view.View
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import com.huangder.lumibooks.ui.reader.engine.PageAnimationSurface
import kotlin.math.abs
import kotlin.math.roundToInt

/** Uses the common drag/fling/cancel rules while fading two whole spread containers. */
internal class EpubSpreadFadeAnimation(
    surface: PageAnimationSurface,
    private val durationMillis: Int
) : PageAnimationController(surface) {
    override fun onDraw(canvas: Canvas) {
        val target = when (currentDirection) {
            Direction.NEXT -> readView.nextPageView
            Direction.PREV -> readView.prevPageView
            Direction.NONE -> null
        }
        val progress = (abs(getOffsetX()) / readView.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        listOf(readView.prevPageView, readView.nextPageView).forEach {
            it.visibility = if (it === target) View.VISIBLE else View.INVISIBLE
            it.alpha = if (it === target) progress else 0f
            it.translationZ = if (it === target) 1f else 0f
        }
        readView.curPageView.alpha = 1f - if (target != null) progress else 0f
        readView.curPageView.translationZ = 2f
    }

    override fun startAnim(fromDrag: Boolean) {
        val terminal = startX + horizontalTurnSign(currentDirection) * readView.width
        val remaining = abs(terminal - touchX) / readView.width.coerceAtLeast(1)
        isRunning = true
        scroller.startScroll(touchX.roundToInt(), 0, (terminal - touchX).roundToInt(), 0,
            (durationMillis * remaining).roundToInt().coerceAtLeast(1))
        readView.postInvalidateOnAnimation()
    }
}

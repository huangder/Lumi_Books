/*
 * Adapted from NovelReader (https://github.com/JustWayward/BookReader)
 * Original copyright (c) 2017 GuangXiang Chen, MIT License.
 */
package com.ebook.reader.ui.reader.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.Scroller

/** Direction of page turn */
enum class PageDirection { NONE, NEXT, PREV }

interface OnPageChangeListener {
    /** Whether there is a previous page/chapter */
    fun hasPrev(): Boolean
    /** Whether there is a next page/chapter */
    fun hasNext(): Boolean
    /** Called when page turn is cancelled (user dragged back) */
    fun pageCancel()
}

/**
 * Slide animation: current page + next page slide side by side.
 * Scroller-driven. Adapted from NovelReader SlidePageAnim.
 */
class SlidePageAnim(
    private val screenW: Int, screenH: Int,
    marginW: Int, marginH: Int,
    private val view: View,
    private val listener: OnPageChangeListener
) {
    private val viewW = screenW - marginW * 2
    private val viewH = screenH - marginH * 2

    // Two bitmaps for page compositing
    var curBitmap: Bitmap = Bitmap.createBitmap(viewW, viewH, Bitmap.Config.RGB_565)
    var nextBitmap: Bitmap = Bitmap.createBitmap(viewW, viewH, Bitmap.Config.RGB_565)
        private set

    val scroller = Scroller(view.context, LinearInterpolator())
    var isRunning = false; internal set
    var isCancel = false; internal set
    private var isNext = false
    private var noNext = false
    private var isMove = false

    private var startX = 0f; private var startY = 0f
    private var touchX = 0f; private var touchY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var moveX = 0; private var moveY = 0

    private val srcRect = Rect(0, 0, viewW, viewH)
    private val destRect = Rect(0, 0, viewW, viewH)
    private val nextSrcRect = Rect(0, 0, viewW, viewH)
    private val nextDestRect = Rect(0, 0, viewW, viewH)

    /** Swap cur/next bitmaps (called before rendering new next page) */
    fun changePage() {
        val tmp = curBitmap; curBitmap = nextBitmap; nextBitmap = tmp
    }

    /** Called by PageView.computeScroll */
    fun scrollAnim() {
        if (scroller.computeScrollOffset()) {
            touchX = scroller.currX.toFloat()
            touchY = scroller.currY.toFloat()
            if (scroller.finalX == scroller.currX && scroller.finalY == scroller.currY) {
                isRunning = false
            }
            view.postInvalidate()
        }
    }

    fun abortAnim() {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
            isRunning = false
            touchX = scroller.finalX.toFloat()
            touchY = scroller.finalY.toFloat()
            view.postInvalidate()
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt(); val y = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                moveX = 0; moveY = 0; isMove = false
                noNext = false; isNext = false
                isRunning = false; isCancel = false
                startX = x.toFloat(); startY = y.toFloat()
                lastX = startX; lastY = startY
                abortAnim()
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                if (!isMove) isMove = kotlin.math.abs(startX - x) > slop || kotlin.math.abs(startY - y) > slop

                if (isMove) {
                    if (moveX == 0 && moveY == 0) {
                        if (x - startX > 0) {
                            isNext = false
                            if (!listener.hasPrev()) { noNext = true; return true }
                        } else {
                            isNext = true
                            if (!listener.hasNext()) { noNext = true; return true }
                        }
                    } else {
                        isCancel = if (isNext) x - moveX > 0 else x - moveX < 0
                    }
                    moveX = x; moveY = y
                    isRunning = true
                    touchX = x.toFloat(); touchY = y.toFloat()
                    view.invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isMove) {
                    // Tap
                    isNext = x >= screenW / 2
                    if (isNext) {
                        if (!listener.hasNext()) return true
                    } else {
                        if (!listener.hasPrev()) return true
                    }
                }
                if (isCancel) listener.pageCancel()
                if (!noNext) startAnim()
                view.invalidate()
            }
        }
        return true
    }

    fun startAnim() {
        if (isRunning) return
        isRunning = true
        val dis = kotlin.math.abs((touchX - startX).toInt())
        val dx = when {
            isNext && !isCancel -> -(dis.coerceAtMost(screenW))
            isNext && isCancel -> screenW - dis.coerceAtMost(screenW)
            !isNext && !isCancel -> screenW - dis
            else -> -dis
        }
        val duration = (400 * kotlin.math.abs(dx)) / screenW
        scroller.startScroll(touchX.toInt(), 0, dx, 0, duration)
    }

    fun draw(canvas: Canvas) {
        if (isRunning) drawMove(canvas) else drawStatic(canvas)
    }

    private fun drawStatic(canvas: Canvas) {
        canvas.drawBitmap(if (isCancel) curBitmap else nextBitmap, 0f, 0f, null)
    }

    private fun drawMove(canvas: Canvas) {
        val dis: Int
        if (isNext) {
            // NEXT: curBitmap slides left, nextBitmap slides in from right
            dis = (screenW - startX + touchX).toInt().coerceAtMost(screenW)
            srcRect.left = screenW - dis; destRect.right = dis
            nextSrcRect.right = screenW - dis; nextDestRect.left = dis
            canvas.drawBitmap(nextBitmap, nextSrcRect, nextDestRect, null)
            canvas.drawBitmap(curBitmap, srcRect, destRect, null)
        } else {
            // PREV: nextBitmap slides in from left, curBitmap slides right
            dis = (touchX - startX).toInt()
            if (dis < 0) {
                // not started moving yet
                canvas.drawBitmap(curBitmap, 0f, 0f, null); return
            }
            srcRect.left = screenW - dis; destRect.right = dis
            nextSrcRect.right = screenW - dis; nextDestRect.left = dis
            canvas.drawBitmap(curBitmap, nextSrcRect, nextDestRect, null)
            canvas.drawBitmap(nextBitmap, srcRect, destRect, null)
        }
    }
}

package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import com.huangder.lumibooks.ui.reader.engine.CurlPageAnim
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController.Direction
import com.huangder.lumibooks.ui.reader.engine.PageAnimationSurface

/**
 * Debug-only reference scene for the paper curl. Never touches books or reader
 * preferences. Extras: scenario = slow | corner | fast | prev | cancel | tap |
 * taps | pose, dark, reverse_axis, duration, x / y (pose finger position).
 */
class CurlPreviewActivity : Activity() {
    private lateinit var preview: Preview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = Preview(this)
        setContentView(preview)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.decorView.post { window.insetsController?.hide(WindowInsets.Type.systemBars()) }
        }
    }

    override fun onDestroy() {
        preview.release()
        super.onDestroy()
    }

    private inner class Preview(context: Context) : FrameLayout(context) {
        /** Live-drawn like the reader's text pages, so turns measure real capture cost. */
        private inner class Page : View(context) {
            var number = 0
                set(value) { field = value; invalidate() }
            override fun onDraw(canvas: Canvas) = drawPage(canvas, number)
        }

        private val dark = intent.getBooleanExtra("dark", false)
        private val pages = List(3) { Page() }
        private val paper = if (dark) Color.rgb(31, 28, 25) else Color.rgb(239, 222, 190)
        private val surface = PageAnimationSurface(this, pages[0], pages[1], pages[2], { paper },
            { intent.getBooleanExtra("reverse_axis", false) }).apply {
            gpuRenderingEnabled = !intent.getBooleanExtra("mesh", false)
        }
        private val curl = CurlPageAnim(surface, baseDurationMs = intent.getIntExtra("duration", 800))
        private var initialized = false
        private var pageNumber = 70
        private var queuedTaps = 0
        private var lastFrameAt = 0L
        private val gaps = mutableListOf<Long>()

        init {
            pages.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
            curl.onCanFlip = { true }
            curl.onAnimationComplete = {
                pageNumber += if (curl.currentDirection == Direction.NEXT) 1 else -1
                preparePages()
                reportFrames()
                if (queuedTaps > 0) {
                    queuedTaps--
                    post { curl.startFromTap(Direction.NEXT, expedited = true) }
                }
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            curl.abortAnim()
            preparePages()
            if (!initialized) {
                initialized = true
                postDelayed({ replay() }, 1200)
            }
        }
        private fun preparePages() {
            pages.forEachIndexed { index, page -> page.number = pageNumber + index - 1 }
        }

        private val ink = if (dark) Color.rgb(200, 192, 180) else Color.rgb(46, 40, 34)
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; alpha = 150; textAlign = Paint.Align.CENTER
        }
        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }

        private fun drawPage(canvas: Canvas, number: Int) {
            canvas.drawColor(paper)
            small.textSize = width * 0.024f
            canvas.drawText("纸 页", width / 2f, height * 0.045f, small)
            canvas.drawText(number.toString(), width / 2f, height * 0.975f, small)
            body.textSize = width * 0.052f
            val cell = body.textSize
            val perLine = ((width * 0.86f) / cell).toInt()
            val left = (width - perLine * cell) / 2f
            var line = 0
            var y = height * 0.09f + cell
            var cursor = (number * 97).mod(SAMPLE.length)
            while (y < height * 0.94f) {
                val start = if (line % 9 == 0) 2 else 0 // paragraph indent
                for (column in start until perLine) {
                    val ch = SAMPLE[cursor]
                    cursor = (cursor + 1) % SAMPLE.length
                    canvas.drawText(ch.toString(), left + column * cell, y, body)
                }
                line++
                y += cell * 1.72f
            }
        }

        private fun mirrored(x: Float) = if (intent.getBooleanExtra("reverse_axis", false)) width - x else x

        private fun replay() {
            val w = width.toFloat()
            val h = height.toFloat()
            when (intent.getStringExtra("scenario") ?: "slow") {
                "slow" -> drag(listOf(.92f * w to .48f * h, .2f * w to .6f * h), 2600, hold = 500)
                "corner" -> drag(listOf(.95f * w to .93f * h, .32f * w to .56f * h), 2600, hold = 500)
                "fast" -> drag(listOf(.86f * w to .55f * h, .46f * w to .56f * h), 110, hold = 0)
                "prev" -> drag(listOf(.06f * w to .5f * h, .74f * w to .56f * h), 2200, hold = 400)
                "cancel" -> drag(listOf(.92f * w to .5f * h, .45f * w to .55f * h, .9f * w to .5f * h), 2400, hold = 300)
                "tap" -> tapTurn(Direction.NEXT)
                "taps" -> repeat(5) { postDelayed({ tapTurn(Direction.NEXT) }, it * 150L) }
                "pose" -> drag(listOf(.92f * w to .5f * h,
                    intent.getFloatExtra("x", .5f) * w to intent.getFloatExtra("y", .55f) * h), 400, hold = -1)
            }
        }

        /** Synthetic finger along [points] (linear segments); hold < 0 keeps the finger down. */
        private fun drag(points: List<Pair<Float, Float>>, durationMs: Long, hold: Long) {
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, mirrored(x), y, 0)
                curl.onTouchEvent(event)
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, points[0].first, points[0].second)
            val steps = (durationMs / 8).toInt().coerceAtLeast(2)
            val segments = points.size - 1
            for (step in 1..steps) {
                postDelayed({
                    val t = step.toFloat() / steps * segments
                    val i = t.toInt().coerceAtMost(segments - 1)
                    val f = t - i
                    val (x0, y0) = points[i]
                    val (x1, y1) = points[i + 1]
                    send(MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f)
                }, step * durationMs / steps)
            }
            if (hold < 0) return
            val (endX, endY) = points.last()
            // Hold still before lifting so the release speed reflects the pause.
            for (k in 1..(hold / 16).toInt()) {
                postDelayed({ send(MotionEvent.ACTION_MOVE, endX, endY) }, durationMs + k * 16L)
            }
            postDelayed({ send(MotionEvent.ACTION_UP, endX, endY) }, durationMs + hold + 8)
        }

        /** Mirrors ReadView: a turn finishing early queues the tap, a handed-over turn starts it now. */
        private fun tapTurn(direction: Direction) {
            if (curl.isRunning && direction == Direction.NEXT) {
                when (curl.completeRunningFlipForNewInput()) {
                    PageAnimationController.RunningFlipHandoff.COMPLETING_ASYNCHRONOUSLY -> {
                        queuedTaps++
                        return
                    }
                    PageAnimationController.RunningFlipHandoff.COMPLETED_SYNCHRONOUSLY -> Unit
                    PageAnimationController.RunningFlipHandoff.NOT_COMMITTED -> curl.abortAnim()
                }
            }
            if (curl.isRunning) return
            curl.startFromTap(direction, expedited = queuedTaps > 0)
        }

        private fun reportFrames() {
            if (gaps.isEmpty()) return
            val sorted = gaps.sorted()
            val budget = 1000f / (display?.refreshRate ?: 60f)
            Log.i("CurlPreview", "page=$pageNumber frames=${gaps.size} p50=${sorted[sorted.size / 2]} " +
                "p95=${sorted[(sorted.size * 95) / 100]} max=${sorted.last()} " +
                "janky=${gaps.count { it > budget * 1.5f }} budgetMs=${"%.1f".format(budget)}")
            gaps.clear()
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (curl.isRunning || curl.isDragging) {
                curl.onDraw(canvas)
                val now = SystemClock.elapsedRealtime()
                if (lastFrameAt > 0) gaps += now - lastFrameAt
                lastFrameAt = now
            } else {
                lastFrameAt = 0
                drawChild(canvas, pages[1], drawingTime)
            }
        }

        override fun computeScroll() {
            if (curl.computeScroll()) postInvalidateOnAnimation()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Like ReadView: a new finger hands the running page over, then owns the stream.
            if (event.actionMasked == MotionEvent.ACTION_DOWN && curl.isRunning) {
                curl.completeRunningFlipForGestureHandoff()
            }
            curl.onTouchEvent(event)
            return true
        }

        init {
            curl.onTapRight = { tapTurn(Direction.NEXT) }
            curl.onTapLeft = { tapTurn(Direction.PREV) }
        }

        fun release() = curl.destroy()
    }

    private companion object {
        const val SAMPLE = "窗外的光落在书页上，纸张泛着淡淡的暖色。翻过一页，旧日的字句在指尖轻轻弯起，" +
            "又在另一面安静地躺平。读书的人并不着急，他只是一行一行地走进别人的世界，看雨落在远山，" +
            "看河水绕过村庄，看一盏灯在深夜里亮了很久。纸页有它自己的重量，轻轻一拨便卷起一道弧线，" +
            "光从弧顶滑过，影子落在下一页的边缘。慢慢翻，文字会随着纸面弯曲；快快翻，整页像一阵风掠过。" +
            "我们把这一瞬间留在屏幕上，好让每一次翻页都像真的在读一本书。"
    }
}

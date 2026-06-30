package com.ebook.reader.ui.reader.engine

import android.graphics.Canvas
import android.view.animation.PathInterpolator
import android.view.MotionEvent
import android.view.animation.Interpolator
import android.widget.Scroller

/**
 * 翻页动画基类。
 *
 * 使用 Android [Scroller] 驱动动画帧，集成在 View 的 [computeScroll] 中。
 * 不经过 Compose 重组，消除绘制帧竞态。
 */
abstract class PageAnimationController(
    protected val readView: ReadView
) {
    companion object {
        /** FastOutSlowInEasing 等效插值器 */
        val FAST_OUT_SLOW_IN: Interpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
        /** 翻页动画时长 ms */
        const val ANIM_DURATION = 300
        /** 回弹动画时长 ms */
        const val BOUNCE_DURATION = 350
        /** 翻页触发阈值（占屏幕宽度的比例） */
        const val FLIP_THRESHOLD = 0.12f
        /** 阴影渐隐时长 ms */
        private const val SHADOW_FADE_DURATION = 200
    }

    enum class Direction { NONE, NEXT, PREV }

    protected val scroller: Scroller = Scroller(readView.context, FAST_OUT_SLOW_IN)

    var isRunning: Boolean = false
        protected set
    var isDragging: Boolean = false
        protected set
    protected var direction: Direction = Direction.NONE

    /** 公开读取当前动画方向 */
    val currentDirection: Direction get() = direction

    protected var startX: Float = 0f
    protected var startY: Float = 0f
    protected var touchX: Float = 0f
    protected var touchY: Float = 0f
    protected var lastX: Float = 0f

    /** 点击检测：是否移动过 */
    protected var hasMoved: Boolean = false
    /** 触摸按下时间 */
    protected var downTime: Long = 0L
    /** 本次动画是翻页（true）还是回弹（false） */
    private var isFlipAnim: Boolean = false
    /** 翻页完成后阴影渐隐 alpha（供子类 onDraw 读取） */
    protected var shadowFadeAlpha: Float = 0f
    private var isShadowFading: Boolean = false

    /** 动画完成回调 */
    var onAnimationComplete: (() -> Unit)? = null
    /** 点击回调 */
    var onTapLeft: (() -> Unit)? = null
    var onTapCenter: (() -> Unit)? = null
    var onTapRight: (() -> Unit)? = null
    /** 🔥 翻页前校验：目标方向是否允许翻页（目标槽位已加载？） */
    var onCanFlip: ((Direction) -> Boolean)? = null

    // ── 抽象方法 ──

    /** 在 ReadView.dispatchDraw 中调用，绘制动画帧 */
    abstract fun onDraw(canvas: Canvas)

    /** 启动动画（从当前手指位置开始或从0开始） */
    abstract fun startAnim(fromDrag: Boolean)

    // ── 触摸处理 ──

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                startX = event.x
                startY = event.y
                touchX = startX
                touchY = startY
                lastX = startX
                hasMoved = false
                downTime = System.currentTimeMillis()
                direction = Direction.NONE
                isDragging = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                val dx = event.x - lastX
                touchX = event.x
                touchY = event.y
                lastX = event.x

                if (!hasMoved && Math.abs(event.x - startX) > 8f) {
                    hasMoved = true
                }

                if (hasMoved) {
                    // 🔥 每次 MOVE 根据累计偏移实时更新方向（支持手势反悔）
                    val cumulativeDx = event.x - startX
                    direction = when {
                        cumulativeDx > 8f -> Direction.PREV
                        cumulativeDx < -8f -> Direction.NEXT
                        else -> Direction.NONE
                    }
                    readView.invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false

                val dx = event.x - startX
                val vy = Math.abs(event.y - startY)
                val dt = System.currentTimeMillis() - downTime

                if (!hasMoved && dt < 300L && vy < 50f) {
                    // 点击，非滑动
                    val viewWidth = readView.width.toFloat()
                    val relX = event.x / viewWidth
                    when {
                        relX < 0.3f -> onTapLeft?.invoke()
                        relX > 0.7f -> onTapRight?.invoke()
                        else -> onTapCenter?.invoke()
                    }
                    direction = Direction.NONE
                    readView.invalidate()
                    return true
                }

                if (hasMoved) {
                    val viewWidth = readView.width.toFloat()
                    val fraction = Math.abs(dx) / viewWidth

                    // 🔥 根据最终累计偏移重算方向（支持手势反悔）
                    direction = when {
                        dx > 8f -> Direction.PREV
                        dx < -8f -> Direction.NEXT
                        else -> Direction.NONE
                    }

                    if (fraction >= FLIP_THRESHOLD && direction != Direction.NONE) {
                        if (onCanFlip?.invoke(direction) == true) {
                            isFlipAnim = true
                            startAnim(fromDrag = true)
                        } else {
                            startBounceBack()
                        }
                    } else {
                        startBounceBack()
                    }
                } else {
                    direction = Direction.NONE
                    readView.invalidate()
                }
                return true
            }
        }
        return false
    }

    // ── computeScroll ──

    fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            touchX = scroller.currX.toFloat()
            touchY = scroller.currY.toFloat()
            readView.invalidate()
            return true
        }
        if (isRunning) {
            isRunning = false
            val wasFlip = isFlipAnim
            // 🔥 仅翻页动画（非回弹）才执行 slot shift
            if (isFlipAnim) {
                isFlipAnim = false
                onAnimationComplete?.invoke()
            }
            direction = Direction.NONE
            touchX = startX
            readView.invalidate()

            // 翻页完成后启动阴影渐隐
            if (wasFlip) {
                shadowFadeAlpha = 1f
                isShadowFading = true
            }
        }
        // 阴影渐隐帧循环
        if (isShadowFading) {
            shadowFadeAlpha -= (16f / SHADOW_FADE_DURATION)
            if (shadowFadeAlpha <= 0f) {
                shadowFadeAlpha = 0f
                isShadowFading = false
                return false
            }
            readView.invalidate()
            return true
        }
        return false
    }

    // ── Animation control ──

    open fun abortAnim() {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
        isRunning = false
        isDragging = false
        isFlipAnim = false
        isShadowFading = false
        shadowFadeAlpha = 0f
        direction = Direction.NONE
        // 🔥 确保 UI 刷新到空闲状态
        readView.invalidate()
    }

    protected fun startBounceBack() {
        // 🔥 回弹不是翻页，清除方向防止 onAnimationComplete 错误 shift
        direction = Direction.NONE
        isFlipAnim = false
        val fromX = touchX.toInt()
        val toX = startX.toInt()
        val dx = toX - fromX
        if (dx == 0) {
            readView.invalidate()
            return
        }
        isRunning = true
        scroller.startScroll(fromX, 0, dx, 0, BOUNCE_DURATION)
        readView.postInvalidateOnAnimation()
    }

    fun getOffsetX(): Float = touchX - startX

    /**
     * 创建一个带透明渐变阴影的 Paint。由子类在 onDraw 中使用。
     */
    protected fun createShadowPaint(width: Int, fromLeft: Boolean = true): android.graphics.Paint {
        val colors = if (fromLeft) {
            intArrayOf(0x33000000.toInt(), 0x00000000)
        } else {
            intArrayOf(0x00000000, 0x33000000.toInt())
        }
        return android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f,
                width.toFloat(), 0f,
                colors,
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
    }
}

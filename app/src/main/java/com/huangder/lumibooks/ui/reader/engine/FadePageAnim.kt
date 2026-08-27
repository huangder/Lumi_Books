package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup

/**
 * 渐变切换翻页动画。
 *
 * 顺序渐变：当前页文字先淡出（progress 0→0.5），下一页文字再淡入（progress 0.5→1）。
 * 两个 PageContentView 背景在动画期间均清为透明，ReadView 实心背景全程静止不动。
 */
class FadePageAnim(
    readView: PageAnimationSurface,
    private var baseDurationMs: Int = FADE_DURATION_MS
) : PageAnimationController(readView) {

    fun setBaseDuration(durationMs: Int) {
        baseDurationMs = durationMs.coerceIn(100, 1000)
    }

    /** 动画进度 0f..1f */
    private var fadeProgress: Float = 0f

    companion object {
        // 🔥 渐变专属时长：比翻页动画慢，避免闪烁感
        private const val FADE_DURATION_MS = 400
    }

    /** 动画期间被清背景的文字页，abort/complete 时恢复。 */
    private var fadingPageViews: List<PageContentView> = emptyList()

    /** 动画期间参与淡入淡出的旧页 / 新页内容 View（可能包含双页模式的两半）。 */
    private var outgoingViews: List<PageContentView> = emptyList()
    private var incomingViews: List<PageContentView> = emptyList()

    // ── 绘制 ──

    override fun onDraw(canvas: Canvas) {
        val vw = readView.width.toFloat()
        if (vw <= 0) return

        when {
            isRunning && direction != Direction.NONE -> {
                val outgoing = readView.curPageView
                val incoming = if (direction == Direction.NEXT) readView.nextPageView
                               else readView.prevPageView
                val hidden   = if (direction == Direction.NEXT) readView.prevPageView
                               else readView.nextPageView

                // 🔥 顺序渐变：旧页先淡出（0→0.55），新页后淡入（0.45→1.0）
                // 10% 交叠区让过渡更柔和，避免中间出现硬切感
                // 图片背景时 setFadeAlpha 只淡入淡出文字、整页保持不透明，背景不会露白
                val outAlpha = (1f - fadeProgress / 0.55f).coerceIn(0f, 1f)
                val inAlpha  = ((fadeProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                outgoingViews.forEach { it.setFadeAlpha(outAlpha) }
                incomingViews.forEach { it.setFadeAlpha(inAlpha) }
                outgoing.alpha = 1f; incoming.alpha = 1f
                hidden.alpha   = 0f

                outgoing.translationX = 0f; outgoing.translationY = 0f
                incoming.translationX = 0f; incoming.translationY = 0f
                hidden.translationX   = idleTranslationX(
                    if (direction == Direction.NEXT) Direction.PREV else Direction.NEXT,
                    vw
                )
                hidden.translationY   = 0f

                // z-order：新页始终在旧页之上（旧页淡出后新页淡入时不被遮挡）
                incoming.translationZ = 2f
                outgoing.translationZ = 1f
                hidden.translationZ   = 0f
            }
            else -> {
                // 空闲：只显示当前页
                readView.curPageView.alpha  = 1f
                readView.prevPageView.alpha = 0f
                readView.nextPageView.alpha = 0f
                readView.curPageView.translationX  = 0f;  readView.curPageView.translationY  = 0f
                readView.prevPageView.translationX = idleTranslationX(Direction.PREV, vw); readView.prevPageView.translationY = 0f
                readView.nextPageView.translationX = idleTranslationX(Direction.NEXT, vw);  readView.nextPageView.translationY = 0f
                readView.curPageView.translationZ  = 2f
                readView.prevPageView.translationZ = 0f
                readView.nextPageView.translationZ = 0f
            }
        }
    }

    fun drawOverlay(@Suppress("UNUSED_PARAMETER") canvas: Canvas) = Unit

    // ── 触摸 ──

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                abortAnim()
                startX = event.x; startY = event.y
                touchX = startX;  touchY = startY
                hasMoved = false
                downTime = System.currentTimeMillis()
                direction = Direction.NONE
                isDragging = true
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (Math.abs(event.x - startX) > 12f || Math.abs(event.y - startY) > 12f) hasMoved = true
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                val dx = event.x - startX
                val dt = System.currentTimeMillis() - downTime

                if (!hasMoved && dt < 300L) {
                    val relX = event.x / readView.width.toFloat()
                    when {
                        relX < 0.3f -> onTapLeft?.invoke()
                        relX > 0.7f -> onTapRight?.invoke()
                        else        -> onTapCenter?.invoke()
                    }
                    return true
                }
                if (hasMoved) {
                    direction = directionForHorizontalDelta(dx, 20f)
                    if (direction != Direction.NONE && onCanFlip?.invoke(direction) == true) {
                        isFlipAnim = true
                        startAnim(fromDrag = false)
                    } else {
                        direction = Direction.NONE
                    }
                }
                return true
            }
        }
        return false
    }

    // ── 动画控制 ──

    override fun startAnim(fromDrag: Boolean) {
        if (direction == Direction.NONE) return
        isRunning = true
        fadeProgress = 0f

        // 🔥 清除两页背景 → ReadView 底色静止不动，只有文字参与动画
        val incoming = if (direction == Direction.NEXT) readView.nextPageView else readView.prevPageView
        val outgoing = readView.curPageView
        outgoingViews = outgoing.pageContentViews()
        incomingViews = incoming.pageContentViews()
        fadingPageViews = (outgoingViews + incomingViews).distinct()
        fadingPageViews.forEach(PageContentView::stripBackgroundForFade)

        val capturedDurationMs = baseDurationMs
        scroller.startScroll(0, 0, 1000, 0, capturedDurationMs)
        readView.postInvalidateOnAnimation()
    }

    fun startFromTap(dir: Direction) {
        direction  = dir
        isFlipAnim = true
        startAnim(fromDrag = false)
    }

    override fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            fadeProgress = scroller.currX / 1000f
            readView.invalidate()
            return true
        }
        if (isRunning) {
            isRunning = false
            if (isFlipAnim) {
                isFlipAnim    = false
                fadeProgress  = 1f
                restoreBackgrounds()          // 先恢复背景，再触发槽位切换
                onAnimationComplete?.invoke() // 槽位切换后 configureCurrentPageView 完整重设背景
            }
            direction = Direction.NONE
            readView.invalidate()
        }
        return false
    }

    override fun abortAnim() {
        restoreBackgrounds()
        super.abortAnim()
    }

    private fun restoreBackgrounds() {
        val bg = readView.bgColor
        fadingPageViews.forEach { it.restoreBackgroundForFade(bg) }
        fadingPageViews = emptyList()
    }

    private fun View.pageContentViews(): List<PageContentView> = when (this) {
        is PageContentView -> listOf(this)
        is ViewGroup -> buildList {
            repeat(childCount) {
                addAll(getChildAt(it).pageContentViews())
            }
        }
        else -> emptyList()
    }
}

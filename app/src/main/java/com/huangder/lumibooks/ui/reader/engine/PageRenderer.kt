package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.LinkedList

/**
 * 将 [PageLayout] 渲染为 Bitmap。
 *
 * 内置 Bitmap 复用池（最多 6 个），减少 GC 压力。
 */
class PageRenderer {

    // ── 渲染参数 ──
    private var bgColor: Int = 0xFFFBFBFC.toInt()
    private var textColor: Int = 0xFF333333.toInt()
    internal var renderMarginLeft: Float = 48f   // ReadView 手柄坐标同步用
    internal var renderMarginTop: Float = 32f
    private var visibleHeight: Float = 1856f
    private var canvasWidth: Int = 1080
    private var canvasHeight: Int = 1920

    // ── Bitmap 复用池 ──
    private val bitmapPool = LinkedList<Bitmap>()
    private val maxPoolSize = 6

    fun configure(
        width: Int,
        height: Int,
        backgroundColor: Int,
        textColor: Int,
        marginLeftPx: Float = 48f,
        marginTopPx: Float = 32f,
        visibleHeightPx: Float = 1856f
    ) {
        val sizeChanged = canvasWidth != width || canvasHeight != height
        canvasWidth = width
        canvasHeight = height
        visibleHeight = visibleHeightPx
        bgColor = backgroundColor
        this.textColor = textColor
        renderMarginLeft = marginLeftPx
        renderMarginTop = marginTopPx

        if (sizeChanged) {
            clearPool()
        }
    }

    /**
     * 渲染一页到 Bitmap。
     * 会先尝试从复用池获取 Bitmap。
     */
    fun renderPage(
        chapterLayout: ChapterLayout,
        pageIndex: Int,
        targetBitmap: Bitmap? = null
    ): Bitmap {
        val pageLayout = chapterLayout.pages.getOrNull(pageIndex)
            ?: return createEmptyBitmap()

        val sl = chapterLayout.staticLayout
        val bitmap = targetBitmap ?: acquireBitmap(canvasWidth, canvasHeight)
        val canvas = Canvas(bitmap)

        // 背景
        canvas.drawColor(bgColor)

        // 绘制文本行：用 StaticLayout.draw() 比逐行 drawText 更可靠
        canvas.save()
        canvas.translate(renderMarginLeft, renderMarginTop)
        // 🔥 clipRect 必须在 T2（-pageStartY）之前执行！
        // 否则 T2 会把裁剪区域也偏移 -pageStartY，导致 page 1+ 的裁剪飞到 bitmap 上方变成空白页
        val pageStartY = sl.getLineTop(pageLayout.startLine)
        // 裁剪到本页最后一行实际底部（而非固定 visibleHeight），防止下一页首行泄漏
        val contentEndY = sl.getLineBottom(pageLayout.endLine - 1).toFloat()
        val clipHeight = (contentEndY - pageStartY).coerceAtMost(visibleHeight)
        canvas.clipRect(0f, 0f, sl.width.toFloat(), clipHeight)
        // 偏移到本页的起始行（仅影响 sl.draw，不影响 clip 位置）
        canvas.translate(0f, -pageStartY.toFloat())
        sl.draw(canvas)
        canvas.restore()

        return bitmap
    }

    /**
     * 渲染整章所有页到 Bitmap 列表。
     */
    fun renderChapter(chapterLayout: ChapterLayout): List<Bitmap> {
        return chapterLayout.pages.mapIndexed { idx, _ ->
            renderPage(chapterLayout, idx)
        }
    }

    // ── Bitmap 池 ──

    private fun acquireBitmap(width: Int, height: Int): Bitmap {
        // 找池中尺寸匹配的 Bitmap
        val iter = bitmapPool.iterator()
        while (iter.hasNext()) {
            val bm = iter.next()
            if (bm.width == width && bm.height == height && !bm.isRecycled) {
                iter.remove()
                bm.eraseColor(0)
                return bm
            }
        }
        // 没有则新建
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun releaseBitmap(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (bitmapPool.size >= maxPoolSize) {
            bitmap.recycle()
        } else {
            bitmapPool.add(bitmap)
        }
    }

    private fun createEmptyBitmap(): Bitmap {
        return Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(bgColor)
        }
    }

    fun clearPool() {
        bitmapPool.forEach { if (!it.isRecycled) it.recycle() }
        bitmapPool.clear()
    }

    /**
     * 在 bitmap 上绘制文本选择高亮。
     * @param highlightColor 高亮颜色（如 0x40FFEB3B）
     * @param cornerRadius 高亮矩形圆角半径
     */
    fun drawSelectionHighlight(
        bitmap: Bitmap,
        chapterLayout: ChapterLayout,
        pageIndex: Int,
        selectionStart: Int,
        selectionEnd: Int,
        highlightColor: Int = 0x40FFEB3B.toInt(),
        cornerRadius: Float = 6f
    ) {
        val pageLayout = chapterLayout.pages.getOrNull(pageIndex) ?: return
        val sl = chapterLayout.staticLayout

        // 计算选择范围与本页的交集
        val selStart = selectionStart.coerceIn(pageLayout.startCharOffset, pageLayout.endCharOffset)
        val selEnd = selectionEnd.coerceIn(pageLayout.startCharOffset, pageLayout.endCharOffset)
        if (selStart >= selEnd) return

        val startLine = sl.getLineForOffset(selStart)
        val endLine = sl.getLineForOffset(selEnd - 1)

        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlightColor
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x10FFFFFF
            style = Paint.Style.FILL
        }

        val pageStartY = sl.getLineTop(pageLayout.startLine)

        for (line in startLine..endLine) {
            val isFirstLine = line == startLine
            val isLastLine = line == endLine

            // 左右边界：首行从 selStart 开始，末行到 selEnd 结束，中间行横跨整行
            val left = if (isFirstLine) {
                renderMarginLeft + sl.getPrimaryHorizontal(selStart)
            } else {
                renderMarginLeft
            }
            val right = if (isLastLine) {
                renderMarginLeft + sl.getPrimaryHorizontal(selEnd)
            } else {
                renderMarginLeft + sl.width.toFloat()
            }
            val top = renderMarginTop + sl.getLineTop(line) - pageStartY
            val bottom = renderMarginTop + sl.getLineBottom(line) - pageStartY

            // 扩展左右边界确保可见
            val adjustedLeft = (left - 2f).coerceAtLeast(renderMarginLeft)
            val adjustedRight = (right + 2f).coerceAtMost((canvasWidth - renderMarginLeft * 2).toFloat() + renderMarginLeft)
            val adjustedTop = top - 1f
            val adjustedBottom = bottom + 1f

            canvas.drawRoundRect(RectF(adjustedLeft, adjustedTop, adjustedRight, adjustedBottom), cornerRadius, cornerRadius, paint)
            canvas.drawRoundRect(RectF(adjustedLeft + 1f, adjustedTop + 1f, adjustedRight - 1f, adjustedBottom - 1f), cornerRadius - 1f, cornerRadius - 1f, innerPaint)
        }
    }

    /**
     * 渲染标注层（高亮/选区）到透明 Bitmap。
     * 用于分层渲染：文字层不变，仅重绘标注层，提升拖动流畅性。
     */
    fun renderAnnotationLayer(
        chapterLayout: ChapterLayout,
        pageIndex: Int,
        annotations: List<AnnotationSpec>,
        selectionStart: Int = -1,
        selectionEnd: Int = -1
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        // 透明背景（不擦除）

        // 绘制已保存的高亮/笔记
        for (ann in annotations) {
            drawSelectionHighlight(
                bitmap = bitmap,
                chapterLayout = chapterLayout,
                pageIndex = pageIndex,
                selectionStart = ann.startOffset,
                selectionEnd = ann.endOffset,
                highlightColor = ann.color
            )
        }

        // 绘制临时选区
        if (selectionStart >= 0 && selectionEnd > selectionStart) {
            drawSelectionHighlight(
                bitmap = bitmap,
                chapterLayout = chapterLayout,
                pageIndex = pageIndex,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd
            )
        }

        return bitmap
    }

    /** 标注规格数据类 */
    data class AnnotationSpec(val startOffset: Int, val endOffset: Int, val color: Int)

    fun destroy() {
        clearPool()
    }
}

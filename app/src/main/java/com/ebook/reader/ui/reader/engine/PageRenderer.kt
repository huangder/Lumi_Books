package com.ebook.reader.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
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
    private var marginLeft: Float = 48f
    private var marginTop: Float = 32f
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
        marginTopPx: Float = 32f
    ) {
        val sizeChanged = canvasWidth != width || canvasHeight != height
        canvasWidth = width
        canvasHeight = height
        bgColor = backgroundColor
        this.textColor = textColor
        marginLeft = marginLeftPx
        marginTop = marginTopPx

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
        canvas.translate(marginLeft, marginTop)
        // 偏移到本页的起始行
        val pageStartY = sl.getLineTop(pageLayout.startLine)
        canvas.translate(0f, -pageStartY.toFloat())
        // 剪裁到可视区域（一页的高度）
        // endLine 是 exclusive，最后一行的索引是 endLine - 1
        // 用 getLineBottom 而非 getLineTop(endLine)，避免尾部最后一页的最后一行被裁掉
        val lastLine = (pageLayout.endLine - 1).coerceIn(0, sl.lineCount - 1)
        val clipBottom = sl.getLineBottom(lastLine)
        val clipHeight = (clipBottom - pageStartY).coerceAtLeast(1)
        canvas.clipRect(0f, 0f, sl.width.toFloat(), clipHeight.toFloat())
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

    fun destroy() {
        clearPool()
    }
}

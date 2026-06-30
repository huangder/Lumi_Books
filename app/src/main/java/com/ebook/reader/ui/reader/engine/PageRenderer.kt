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
        // 🔥 clipRect 必须在 T2（-pageStartY）之前执行！
        // 否则 T2 会把裁剪区域也偏移 -pageStartY，导致 page 1+ 的裁剪飞到 bitmap 上方变成空白页
        canvas.clipRect(0f, 0f, sl.width.toFloat(), visibleHeight)
        // 偏移到本页的起始行（仅影响 sl.draw，不影响 clip 位置）
        val pageStartY = sl.getLineTop(pageLayout.startLine)
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

    fun destroy() {
        clearPool()
    }
}

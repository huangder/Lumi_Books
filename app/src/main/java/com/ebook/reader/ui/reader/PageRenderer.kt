package com.ebook.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 页面渲染器接口：将章节文本/HTML 转换为 [PageData]（Bitmap）。
 * 每个格式（TXT/EPUB/PDF）各自实现。
 */
interface PageRenderer {
    /** 渲染指定页到 Bitmap。targetBitmap 可复用（避免重复分配）。 */
    fun renderPage(chapterIndex: Int, pageIndex: Int, targetBitmap: Bitmap? = null): PageData?

    /** 获取某章的总页数（需先排版过）。返回 -1 表示未排版/未知。 */
    fun getPageCount(chapterIndex: Int): Int

    /**
     * 配置渲染参数（字体大小/颜色/背景/页面尺寸变更时调用）。
     * 配置变更后之前缓存的排版失效，需要重新 renderPage。
     */
    fun configure(
        width: Int, height: Int,
        fontSizePx: Float, lineSpacingPx: Float,
        bgColor: Int = Color.WHITE, textColor: Int = Color.BLACK,
        isNightMode: Boolean = false,
        density: Float = 2.75f  // 用于 dp→px 转换
    )

    /** 获取页面宽度（configure 后可用） */
    val pageWidth: Int

    /** 获取页面高度（configure 后可用） */
    val pageHeight: Int
}

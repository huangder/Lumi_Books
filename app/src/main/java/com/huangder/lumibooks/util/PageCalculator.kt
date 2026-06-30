package com.huangder.lumibooks.util

import android.content.Context
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue

/**
 * 用 StaticLayout 预估每页行数和总页数。
 * 实际分页由 WebView CSS columns 处理。
 */
object PageCalculator {

    /**
     * 预估总页数。
     *
     * @param text 纯文本
     * @param contentWidthPx 内容区宽度
     * @param contentHeightPx 内容区高度
     * @param textSizePx 字号
     * @param lineHeightMul 行高倍数
     */
    fun estimatePageCount(
        text: String,
        contentWidthPx: Int,
        contentHeightPx: Int,
        textSizePx: Float,
        lineHeightMul: Float = 1.6f
    ): Int {
        val paint = TextPaint().apply {
            textSize = textSizePx
            isAntiAlias = true
        }
        val lineHeight = textSizePx * lineHeightMul
        val linesPerPage = (contentHeightPx / lineHeight).toInt().coerceAtLeast(1)

        // 用 StaticLayout 算总行数
        val layout = StaticLayout.Builder.obtain(
            text, 0, text.length, paint, contentWidthPx
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineHeight - paint.textSize, 1f)
            .setIncludePad(false)
            .build()

        val totalLines = layout.lineCount
        return maxOf(1, (totalLines + linesPerPage - 1) / linesPerPage)
    }
}

package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Typeface
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 单页内容 View，替代 PageSurfaceView（Bitmap 容器）。
 *
 * 内部持有 TextView，支持：
 * - 系统文字选择（setTextIsSelectable）
 * - BackgroundColorSpan 高亮
 * - 字体/字号/边距配置
 */
class PageContentView(context: Context) : FrameLayout(context) {

    val textView: TextView = TextView(context).apply {
        // 禁止 TextView 拦截任何触摸事件（翻页/点击手势由 ReadView 处理）
        movementMethod = null
        isClickable = false
        isFocusable = false
        isLongClickable = false
        gravity = Gravity.TOP
        includeFontPadding = false
        // 默认样式
        setTextColor(0xFF333333.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 56f)
    }

    init {
        // 确保 PageContentView 不拦截触摸事件，让事件传递到 ReadView
        isClickable = false
        isFocusable = false
        isLongClickable = false
        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }


    /**
     * 设置页面文本内容。
     * @param fullText 完整章节文本
     * @param startChar 起始字符偏移（含）
     * @param endChar 结束字符偏移（不含）
     * @param highlights 高亮列表 (start, end, color)，偏移相对于 fullText
     */
    fun setPageContent(
        fullText: CharSequence,
        startChar: Int,
        endChar: Int,
        highlights: List<Triple<Int, Int, Int>> = emptyList()
    ) {
        if (startChar < 0 || endChar > fullText.length || startChar >= endChar) {
            textView.text = ""
            return
        }

        val pageText = fullText.subSequence(startChar, endChar)
        val spannable = SpannableStringBuilder(pageText)

        // 应用高亮（将全局偏移转换为页内偏移）
        for ((hStart, hEnd, hColor) in highlights) {
            val localStart = (hStart - startChar).coerceIn(0, spannable.length)
            val localEnd = (hEnd - startChar).coerceIn(0, spannable.length)
            if (localStart < localEnd) {
                spannable.setSpan(
                    BackgroundColorSpan(hColor),
                    localStart, localEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        textView.text = spannable
    }

    /**
     * 配置 TextView 样式。
     */
    fun configure(
        fontSizePx: Float,
        textColor: Int,
        lineHeightMult: Float = 1.5f,
        letterSpacingPx: Float = 0f,
        typeface: Typeface = Typeface.DEFAULT,
        marginLeftPx: Float = 48f,
        marginTopPx: Float = 32f,
        marginRightPx: Float = 48f,
        marginBottomPx: Float = 32f
    ) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
        textView.setTextColor(textColor)
        textView.typeface = typeface
        textView.setLineSpacing(0f, lineHeightMult)
        textView.letterSpacing = if (fontSizePx > 0) letterSpacingPx / fontSizePx else 0f
        textView.setPadding(
            marginLeftPx.toInt(),
            marginTopPx.toInt(),
            marginRightPx.toInt(),
            marginBottomPx.toInt()
        )
    }

    /** 获取当前 TextView 的 Spannable（用于读取选区等） */
    fun getTextSpannable(): Spannable? = textView.text as? Spannable

    /**
     * 在指定屏幕坐标处选词并高亮。
     * @return Triple(start, end, selectedText) 或 null
     */
    fun selectWordAt(x: Float, y: Float): Triple<Int, Int, String>? {
        val spannable = textView.text as? Spannable ?: return null
        val layout = textView.layout ?: return null

        // 屏幕坐标 → TextView 内坐标
        val tx = x - textView.left - textView.paddingLeft
        val ty = y - textView.top - textView.paddingTop

        if (tx < 0 || ty < 0) return null

        val line = layout.getLineForVertical(ty.toInt())
        val offset = layout.getOffsetForHorizontal(line, tx).coerceIn(0, spannable.length - 1)

        // 扩词：CJK 左2右3，英文到词边界
        val text = spannable.toString()
        var start = offset
        var end = offset
        val isCJK = text[offset].code in 0x4E00..0x9FFF || text[offset].code in 0x3400..0x4DBF
        if (isCJK) {
            start = (offset - 2).coerceAtLeast(0)
            end = (offset + 3).coerceAtMost(text.length)
        } else {
            fun isWordSep(c: Char): Boolean = c.isWhitespace() || (!c.isLetterOrDigit() && c != '\'' && c != '-')
            while (start > 0 && !isWordSep(text[start - 1])) start--
            while (end < text.length && !isWordSep(text[end])) end++
        }

        if (end <= start) return null

        // 设置选区高亮
        Selection.setSelection(spannable, start, end)
        return Triple(start, end, text.substring(start, end))
    }

    /** 清除选区 */
    fun clearSelection() {
        val spannable = textView.text as? Spannable ?: return
        Selection.setSelection(spannable, 0, 0)
    }

    /** 获取选区范围，无选区返回 null */
    fun getSelectionRange(): Pair<Int, Int>? {
        val spannable = textView.text as? Spannable ?: return null
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        if (start < 0 || end < 0 || end <= start) return null
        return start to end
    }

    /** 清除内容 */
    fun clear() {
        textView.text = ""
    }
}

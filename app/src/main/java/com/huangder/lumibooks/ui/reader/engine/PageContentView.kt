package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 单页内容 View，替代 PageSurfaceView（Bitmap 容器）。
 *
 * 内部持有 TextView，支持：
 * - **系统原生文字选择**（setTextIsSelectable → 泪滴手柄 + 浮动工具栏）
 * - BackgroundColorSpan 高亮
 * - 字体/字号/边距配置
 * - **条件触摸拦截**：仅拦截水平滑动（翻页），点击/长按传递给 TextView 原生处理
 */
class PageContentView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "PageContentView"
    }

    val textView: TextView = TextView(context).apply {
        // 🔥 启用 Android 原生文字选择（泪滴手柄 + 系统浮动工具栏）
        setTextIsSelectable(true)
        gravity = Gravity.TOP
        includeFontPadding = true
        // 默认样式
        setTextColor(0xFF333333.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 56f)
    }

    init {
        // PageContentView 自身不消耗事件，让触摸穿透到 ReadView
        isClickable = false
        isFocusable = false
        isLongClickable = false
        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ── 触摸拦截策略 ──
    // 🔥 ReadView.onInterceptTouchEvent 统一处理所有触摸分类
    // PageContentView 不再拦截任何事件，全部穿透给 TextView 原生处理
    // （长按选词由 TextView setTextIsSelectable(true) 原生支持；
    //   翻页/菜单/点击由 ReadView 层统一拦截处理）

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false


    /**
     * 设置页面文本内容。
     * @param fullText 完整章节文本
     * @param startChar 起始字符偏移（含），即本章节中的全局起始位置
     * @param endChar 结束字符偏移（不含）
     * @param highlights 高亮列表 (start, end, color)，偏移相对于 fullText
     */
    /** 文本设置完成后的回调（ReadView 用于注册 SpanWatcher） */
    var onTextSet: ((Spannable) -> Unit)? = null

    fun setPageContent(
        fullText: CharSequence,
        startChar: Int,
        endChar: Int,
        highlights: List<Triple<Int, Int, Int>> = emptyList()
    ) {
        // 🔥 缓存章节级起始偏移，供 selectWordAt 返回值转换使用
        chapterStartOffset = startChar

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
        onTextSet?.invoke(spannable)
    }

    /**
     * 配置 TextView 样式。
     */
    fun configure(
        fontSizePx: Float,
        textColor: Int,
        lineHeightMult: Float = 1.5f,
        lineSpacingExtraPx: Float = 0f,
        letterSpacingPx: Float = 0f,
        typeface: Typeface = Typeface.DEFAULT,
        marginLeftPx: Float = 48f,
        marginTopPx: Float = 32f,
        marginRightPx: Float = 48f,
        marginBottomPx: Float = 32f,
        highlightColor: Int = 0x40007AFF.toInt(),
        accentColor: Int = 0xFF007AFF.toInt()
    ) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
        textView.setTextColor(textColor)
        textView.typeface = typeface
        textView.setLineSpacing(lineSpacingExtraPx, lineHeightMult)
        textView.letterSpacing = if (fontSizePx > 0) letterSpacingPx / fontSizePx else 0f
        // 断行策略：与 PageLayoutEngine 保持一致，防止分页边界与渲染不匹配
        textView.breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE  // CJK 文本不需要断字
        }
        textView.setPadding(
            marginLeftPx.toInt(),
            marginTopPx.toInt(),
            marginRightPx.toInt(),
            marginBottomPx.toInt()
        )
        // 🔥 选择高亮色跟随主题
        textView.highlightColor = highlightColor
        // 🔥 API 29+ 选择手柄颜色跟随主题
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val density = textView.resources.displayMetrics.density
            val handle = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(accentColor)
                setSize((14 * density).toInt(), (18 * density).toInt())
            }
            textView.setTextSelectHandle(handle)
            textView.setTextSelectHandleLeft(handle)
            textView.setTextSelectHandleRight(handle)
        }
    }

    /** 获取当前 TextView 的 Spannable（用于读取选区等） */
    fun getTextSpannable(): Spannable? = textView.text as? Spannable

    /** 缓存当前页在章节中的起始字符偏移（用于选区偏移转换） */
    var chapterStartOffset: Int = 0
        private set

    /**
     * 在指定屏幕坐标处选词并高亮。
     * @return Triple(start, end, selectedText) 或 null
     *   start/end 是**页面内偏移量**，需加 chapterStartOffset 转换为章节偏移量
     */
    fun selectWordAt(x: Float, y: Float): Triple<Int, Int, String>? {
        val spannable = textView.text as? Spannable
        if (spannable == null) {
            Log.w(TAG, "selectWordAt: textView.text is not Spannable, type=${textView.text?.javaClass?.simpleName}")
            return null
        }
        if (spannable.isEmpty()) {
            Log.w(TAG, "selectWordAt: spannable is empty")
            return null
        }

        val layout = textView.layout
        if (layout == null) {
            Log.w(TAG, "selectWordAt: textView.layout is null (view not laid out?)")
            return null
        }

        // 屏幕坐标（ReadView 坐标系）→ TextView 内坐标
        val tx = x - textView.left - textView.paddingLeft
        val ty = y - textView.top - textView.paddingTop

        if (tx < 0 || ty < 0) {
            Log.d(TAG, "selectWordAt: touch outside text area tx=$tx ty=$ty paddingLeft=${textView.paddingLeft} paddingTop=${textView.paddingTop}")
            return null
        }

        val line = layout.getLineForVertical(ty.toInt())
        val offset = layout.getOffsetForHorizontal(line, tx).coerceIn(0, spannable.length - 1)

        // 扩词：CJK 左2右3，英文到词边界
        val text = spannable.toString()
        var start = offset
        var end = offset
        val charCode = text[offset].code
        val isCJK = charCode in 0x4E00..0x9FFF || charCode in 0x3400..0x4DBF
        if (isCJK) {
            start = (offset - 2).coerceAtLeast(0)
            end = (offset + 3).coerceAtMost(text.length)
        } else {
            fun isWordSep(c: Char): Boolean = c.isWhitespace() || (!c.isLetterOrDigit() && c != '\'' && c != '-')
            while (start > 0 && !isWordSep(text[start - 1])) start--
            while (end < text.length && !isWordSep(text[end])) end++
        }

        if (end <= start) {
            Log.d(TAG, "selectWordAt: word range invalid start=$start end=$end")
            return null
        }

        // 设置选区高亮
        Selection.setSelection(spannable, start, end)
        val selected = text.substring(start, end)
        Log.d(TAG, "selectWordAt: success! text=\"$selected\" offset=$offset line=$line x=$tx y=$ty")
        return Triple(start, end, selected)
    }

    /** 清除选区 */
    fun clearSelection() {
        val spannable = textView.text as? Spannable
        if (spannable != null && Selection.getSelectionEnd(spannable) > Selection.getSelectionStart(spannable)) {
            Selection.removeSelection(spannable)
        }
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

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
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.load
import java.io.File
import kotlin.math.abs

/**
 * 单页内容 View，替代 PageSurfaceView（Bitmap 容器）。
 *
 * 双层架构：
 * - **JustifiedTextView**（可见）：中文两端对齐渲染，逐字绘制 + 行尾字间距自动填充
 * - **TextView**（隐藏）：计算 StaticLayout，处理文字选择（泪滴手柄 + 浮动工具栏）
 *
 * 选择手柄位置基于 TextView 的 layout 计算，
 * 实际文字渲染使用 JustifiedTextView 的逐字绘制，
 * 两者之间的微小偏差（< 5% 行宽）在视觉上可接受。
 */
class PageContentView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "PageContentView"
    }

    private val backgroundImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isClickable = false
        isFocusable = false
        visibility = View.GONE
    }

    /**
     * 隐藏文字的 TextView：计算 layout + 处理文字选择。
     *
     * 文字颜色设为透明 → 不绘制文字（由 JustifiedTextView 负责渲染）。
     * 但保留选区高亮（BackgroundColorSpan）和选择手柄的绘制。
     * 这样选词时可以看到高亮背景 + 手柄，文字由 JustifiedTextView 叠加绘制。
     */
    val textView: TextView = TextView(context).apply {
        setTextIsSelectable(true)
        gravity = Gravity.TOP
        includeFontPadding = false
        // 文字颜色透明：不绘制文字本身，但选区高亮（BackgroundColorSpan）仍然可见
        setTextColor(android.graphics.Color.TRANSPARENT)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 56f)
    }

    /** 可见的 JustifiedTextView：中文两端对齐渲染 */
    private val justifiedView = JustifiedTextView(context).apply {
        setDefaultTextColor(0xFF333333.toInt())
        setTextSize(56f)
    }

    init {
        isClickable = false
        isFocusable = false
        isLongClickable = false
        addView(backgroundImageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // 再添加隐藏的 TextView（处理触摸）
        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // 再添加可见的 JustifiedTextView（顶层，渲染文字）
        addView(justifiedView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private var readerBackgroundImagePath: String? = null

    fun setReaderBackground(color: Int, imagePath: String?) {
        setBackgroundColor(color)
        backgroundImageView.setBackgroundColor(color)
        if (readerBackgroundImagePath == imagePath) return

        readerBackgroundImagePath = imagePath
        val imageFile = imagePath?.let(::File)?.takeIf { it.exists() }
        if (imageFile == null) {
            backgroundImageView.load(null)
            backgroundImageView.visibility = View.GONE
        } else {
            backgroundImageView.visibility = View.VISIBLE
            backgroundImageView.load(imageFile) {
                allowHardware(false)
                crossfade(false)
                memoryCacheKey(imageFile.absolutePath)
                diskCacheKey(imageFile.absolutePath)
            }
        }
    }

    /**
     * 压制系统浮动工具栏：选词时使用自定义菜单而非系统菜单。
     * 必须在 onSelectionAction 回调设置之后调用，
     * 否则自定义菜单也会被压制。
     */
    fun suppressSystemToolbar() {
        textView.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    menu?.clear()
                    mode?.hide(Long.MAX_VALUE)
                }
                return true
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    menu?.clear()
                    mode?.hide(Long.MAX_VALUE)
                }
                return true
            }
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    /** 文本设置完成后的回调（ReadView 用于注册 SpanWatcher） */
    var onTextSet: ((Spannable) -> Unit)? = null

    /** 简繁转换模式，默认 "original"（不转换） */
    var chineseMode: String = "original"

    /** 原始 spannable（含真实 BitmapDrawable ImageSpan），供 syncText/moveSlot 使用 */
    private var originalSpannable: Spannable? = null

    fun setPageContent(
        fullText: CharSequence,
        startChar: Int,
        endChar: Int,
        highlights: List<Triple<Int, Int, Int>> = emptyList()
    ) {
        if (startChar < 0 || endChar > fullText.length || startChar >= endChar) {
            chapterStartOffset = startChar
            textView.text = ""
            justifiedView.text = null
            return
        }

        // 追踪 LeadingMarginSpan：检查 fullText 中是否包含 span
        val fullLms = if (fullText is Spannable) fullText.getSpans(0, fullText.length, android.text.style.LeadingMarginSpan::class.java) else emptyArray()
        Log.d("PageSlotManager", "setPageContent: fullText type=${fullText.javaClass.simpleName} LeadingMarginSpans=${fullLms.size} start=$startChar end=$endChar")

        // 🔥 跳过页面开头的空行（段间距的空白行落在页面顶部时不需要显示）
        var actualStart = startChar
        while (actualStart < endChar && fullText[actualStart] == '\n') {
            actualStart++
        }
        chapterStartOffset = actualStart

        if (actualStart >= endChar) {
            textView.text = ""
            justifiedView.text = null
            return
        }

        val subText = fullText.subSequence(actualStart, endChar)
        Log.d(TAG, "setPageContent: subText type=${subText.javaClass.simpleName} isSpanned=${subText is android.text.Spanned}")

        // 简繁转换（在切片后、应用高亮前）
        // 🔥 使用 convertPreservingSpans 保留所有 span（ImageSpan/LeadingMarginSpan 等）
        val pageText = if (chineseMode != "original") {
            com.huangder.lumibooks.util.ChineseConverter.convertPreservingSpans(subText, chineseMode)
        } else {
            subText
        }
        Log.d(TAG, "setPageContent: pageText type=${pageText.javaClass.simpleName} isSpanned=${pageText is android.text.Spanned}")

        // 🔥 保留 span：直接用 SpannableStringBuilder(Spanned) 保留所有 span（含 ImageSpan）
        // toString() 会把 ImageSpan 变成 U+FFFC "obj" 字符，图片丢失
        val spannable = if (pageText is android.text.Spanned) {
            SpannableStringBuilder(pageText)
        } else {
            SpannableStringBuilder(pageText)
        }
        if (subText is android.text.Spanned) {
            // 恢复 LeadingMarginSpan（首行缩进）
            // 如果页面从段落中间开始（前一个字符不是 \n），跳过开头到第一个 \n 的缩进
            val startsMidParagraph = actualStart > 0 && fullText[actualStart - 1] != '\n'
            val lms = subText.getSpans(0, subText.length, android.text.style.LeadingMarginSpan::class.java)
            for (lm in lms) {
                val start = subText.getSpanStart(lm).coerceIn(0, spannable.length)
                val end = subText.getSpanEnd(lm).coerceIn(0, spannable.length)
                if (start < end) {
                    if (startsMidParagraph && start == 0) {
                        // 段落跨页续行：去掉开头到第一个 \n 的缩进
                        val firstNl = spannable.indexOf('\n')
                        if (firstNl >= 0 && firstNl < end) {
                            spannable.setSpan(lm, firstNl + 1, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                        }
                        // 如果没有 \n 说明整个页面都是一个段落的续行，不添加缩进
                    } else {
                        spannable.setSpan(lm, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                    }
                }
            }
            // 恢复 ParagraphLineHeightSpan（段间距）
            val lhs = subText.getSpans(0, subText.length, com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan::class.java)
            for (lh in lhs) {
                val start = subText.getSpanStart(lh).coerceIn(0, spannable.length)
                val end = subText.getSpanEnd(lh).coerceIn(0, spannable.length)
                if (start < end) {
                    spannable.setSpan(lh, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                }
            }
            Log.d(TAG, "setPageContent: restored ${lms.size} LeadingMarginSpans, ${lhs.size} ParagraphLineHeightSpans")
        }

        // 应用高亮（将全局偏移转换为页内偏移，基于 actualStart 而非 startChar）
        for ((hStart, hEnd, hColor) in highlights) {
            val localStart = (hStart - actualStart).coerceIn(0, spannable.length)
            val localEnd = (hEnd - actualStart).coerceIn(0, spannable.length)
            if (localStart < localEnd) {
                spannable.setSpan(
                    BackgroundColorSpan(hColor),
                    localStart, localEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 验证 span 存活（调试用）
        val lms = spannable.getSpans(0, spannable.length, android.text.style.LeadingMarginSpan::class.java)
        val imgSpans = spannable.getSpans(0, spannable.length, android.text.style.ImageSpan::class.java)
        Log.d(TAG, "setPageContent: start=$actualStart end=$endChar len=${spannable.length} LMS=${lms.size} ImageSpan=${imgSpans.size}")

        // 🔥 诊断：检测 U+FFFC 但没有 ImageSpan 的字符位置（图片加载失败）
        var orphanFFFC = 0
        for (i in 0 until spannable.length) {
            if (spannable[i] == '￼' && spannable.getSpans(i, i + 1, android.text.style.ImageSpan::class.java).isEmpty()) {
                orphanFFFC++
            }
        }
        if (orphanFFFC > 0) {
            Log.w(TAG, "setPageContent: $orphanFFFC U+FFFC without ImageSpan — images failed to load at page offset")
        }

        // 🔥 创建 textView 副本：
        // - ImageSpan → 替换为透明占位（保持相同 bounds，确保行高一致，无需 requestLayout）
        // - ForegroundColorSpan → 移除（防止 textView 也绘制彩色文字导致重影）
        val textViewCopy = SpannableStringBuilder(spannable)
        for (span in textViewCopy.getSpans(0, textViewCopy.length, android.text.style.ImageSpan::class.java)) {
            val start = textViewCopy.getSpanStart(span)
            val end = textViewCopy.getSpanEnd(span)
            val flags = textViewCopy.getSpanFlags(span)
            val drawable = span.drawable
            if (drawable != null) {
                val bounds = drawable.copyBounds()
                // 🔥 使用 1x1 透明 BitmapDrawable（有 intrinsic size），而非 ColorDrawable（无 intrinsic size）
                // ColorDrawable.getIntrinsicHeight() 返回 -1，可能导致测量路径出错
                val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                val transparent = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                transparent.bounds = bounds
                textViewCopy.removeSpan(span)
                textViewCopy.setSpan(android.text.style.ImageSpan(transparent), start, end, flags)
            } else {
                textViewCopy.removeSpan(span)
            }
        }
        for (span in textViewCopy.getSpans(0, textViewCopy.length, android.text.style.ForegroundColorSpan::class.java)) {
            textViewCopy.removeSpan(span)
        }

        // 设置文本到两个 View
        textView.text = textViewCopy
        justifiedView.text = spannable
        // 保存原始 spannable（含真实 BitmapDrawable ImageSpan），供 moveSlot/syncText 使用
        this.originalSpannable = spannable

        // setTextIsSelectable(true) 时 Android 内部通过 Editable.Factory.newEditable() 创建副本
        // 必须从 textView.text 取实际存储的 Spannable，否则 SpanWatcher 注册在死对象上
        val actualSpannable = textView.text as? Spannable ?: spannable
        onTextSet?.invoke(actualSpannable)
    }

    /**
     * 配置 TextView 样式。
     * 同时配置隐藏的 TextView（用于 layout 计算）和可见的 JustifiedTextView（用于渲染）。
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
        val spacingRatio = if (fontSizePx > 0) letterSpacingPx / fontSizePx else 0f

        // 配置隐藏的 TextView（layout 计算 + 选择）
        // 文字颜色保持透明，不绘制文字（由 JustifiedTextView 负责渲染）
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
        textView.setTextColor(android.graphics.Color.TRANSPARENT)
        textView.typeface = typeface
        textView.setLineSpacing(lineSpacingExtraPx, lineHeightMult)
        textView.letterSpacing = spacingRatio
        // 🔥 守卫：仅在值变更时才设置，避免无条件触发 nullLayouts() + requestLayout()
        // Android 的 setBreakStrategy/setHyphenationFrequency 不检查相等性，即使值相同
        // 也会无效化已存在的 Layout，导致多余的 layout pass → 内容位移
        if (textView.breakStrategy != Layout.BREAK_STRATEGY_HIGH_QUALITY) {
            textView.breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (textView.hyphenationFrequency != Layout.HYPHENATION_FREQUENCY_NONE) {
                textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            }
        }
        // 🔥 守卫：仅在 padding 实际变更时调用 setPadding，避免无谓的 requestLayout()
        val ml = marginLeftPx.toInt()
        val mt = marginTopPx.toInt()
        val mr = marginRightPx.toInt()
        val mb = marginBottomPx.toInt()
        if (textView.paddingLeft != ml || textView.paddingTop != mt ||
            textView.paddingRight != mr || textView.paddingBottom != mb) {
            textView.setPadding(ml, mt, mr, mb)
        }
        textView.highlightColor = highlightColor
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

        // 配置可见的 JustifiedTextView（两端对齐渲染）
        justifiedView.setTextSize(fontSizePx)
        justifiedView.setDefaultTextColor(textColor)
        justifiedView.setTypeface(typeface)
        justifiedView.setLineSpacing(lineSpacingExtraPx, lineHeightMult)
        justifiedView.setLetterSpacing(spacingRatio)
        if (justifiedView.paddingLeft != ml || justifiedView.paddingTop != mt ||
            justifiedView.paddingRight != mr || justifiedView.paddingBottom != mb) {
            justifiedView.setPadding(ml, mt, mr, mb)
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
        justifiedView.text = null
        originalSpannable = null
    }

    /** 获取原始 spannable（含真实 ImageSpan），供 moveSlot 使用 */
    fun getJustifiedText(): Spannable? = originalSpannable

    /**
     * 同步设置文本到 textView 和 justifiedView。
     * 用于外部直接设置 textView.text 的场景（如 PageSlotManager.moveSlot）。
     *
     * @param textViewText 设置给隐藏 textView 的文本（可含透明 ImageSpan）
     * @param justifiedText 设置给可见 justifiedView 的文本（应含真实 ImageSpan），null 时回退到 textViewText
     */
    fun syncText(textViewText: CharSequence?, justifiedText: Spannable? = null) {
        // 🔥 先设置 justifiedView（只 invalidate，不触发父布局），再设置 textView（可能触发父布局）
        // 确保 textView 触发的 layout pass 中，justifiedView 已有正确内容供 onSizeChanged → rebuildLayout 使用
        justifiedView.text = justifiedText
            ?: (textViewText as? Spannable ?: textViewText?.let { SpannableStringBuilder(it) })
        textView.text = textViewText
        // 如果传入了 justifiedText，同步更新 originalSpannable
        if (justifiedText != null) {
            this.originalSpannable = justifiedText
        }
    }
}

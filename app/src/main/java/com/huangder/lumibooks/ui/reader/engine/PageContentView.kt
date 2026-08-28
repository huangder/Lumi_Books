package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.util.ReaderBackgroundBlurTransformation
import com.huangder.lumibooks.util.parser.EpubParser
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun pageStartsMidParagraph(text: CharSequence, start: Int): Boolean {
    return start > 0 && start <= text.length && text[start - 1] != '\n'
}

private class PagedSelectableTextView(context: Context) : RoundedHighlightTextView(context) {
    // NOTE: justification must stay NONE (the default).
    // PageLayoutEngine paginates with a plain ALIGN_NORMAL StaticLayout. When
    // justification is enabled, Android's line breaker reserves extra trailing
    // space per line (≈ letterSpacing), so the visible page wraps one character
    // earlier than the pagination. The page-final line then contains the extra
    // character and pokes out past the right margin (and the right margin
    // renders far wider than the setting when letterSpacing > 0).

    override fun scrollTo(x: Int, y: Int) {
        // A page is a fixed viewport. TextView may otherwise scroll horizontally
        // to reveal its selection after slot rotation, clipping the page until the next tap.
        super.scrollTo(0, 0)
    }

    override fun canScrollHorizontally(direction: Int): Boolean = false

    override fun canScrollVertically(direction: Int): Boolean = false

}

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
        private const val SEARCH_HIGHLIGHT_RGB = 0x00FFE082
        internal const val TTS_HIGHLIGHT_RGB = 0x00FF9E80
        internal const val UNDERLINE_FLAG = 0xFE
    }

    private val backgroundImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isClickable = false
        isFocusable = false
        visibility = View.GONE
    }

    private val coverImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = false
        isFocusable = false
        visibility = View.GONE
    }

    /** The single visible text, image, highlight, and native-selection renderer. */
    val textView: TextView = PagedSelectableTextView(context).apply {
        setTextIsSelectable(true)
        gravity = Gravity.TOP
        includeFontPadding = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // PageLayoutEngine's StaticLayout does not expand lines for fallback-font metrics.
            // Keep the visible page on the same line-height model so its last line fits.
            setFallbackLineSpacing(false)
        }
        breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        setTextColor(0xFF333333.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 56f)
    }

    /** 可见的 JustifiedTextView：中文两端对齐渲染 */
    private val justifiedView = JustifiedTextView(context).apply {
        setDefaultTextColor(0xFF333333.toInt())
        setTextSize(56f)
        visibility = View.INVISIBLE
    }

    private val verticalTextView = VerticalTextView(context).apply {
        visibility = View.GONE
    }

    init {
        isClickable = false
        isFocusable = false
        isLongClickable = false
        addView(backgroundImageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(coverImageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // 再添加隐藏的 TextView（处理触摸）
        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // 再添加可见的 JustifiedTextView（顶层，渲染文字）
        addView(justifiedView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(verticalTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private var readerBackgroundImagePath: String? = null
    private var readerBackgroundImageOpacity: Float = 1f
    private var readerBackgroundImageBlurDp: Float = 0f
    private var currentBgColor: Int = 0
    private var showingCoverPage = false
    private var coverImageSpan: ImageSpan? = null

    fun setReaderBackground(
        color: Int,
        imagePath: String?,
        imageOpacity: Float = 1f,
        imageBlurDp: Float = 0f
    ) {
        currentBgColor = color
        setBackgroundColor(color)
        backgroundImageView.setBackgroundColor(color)
        val opacity = imageOpacity.coerceIn(0f, 1f)
        val blurDp = imageBlurDp.coerceIn(0f, 40f)
        val imageChanged = readerBackgroundImagePath != imagePath
        val effectChanged = readerBackgroundImageOpacity != opacity ||
            readerBackgroundImageBlurDp != blurDp
        if (!imageChanged && !effectChanged) return

        readerBackgroundImagePath = imagePath
        readerBackgroundImageOpacity = opacity
        readerBackgroundImageBlurDp = blurDp
        backgroundImageView.alpha = opacity
        val imageFile = imagePath?.let(::File)?.takeIf { it.exists() }
        if (imageFile == null) {
            backgroundImageView.load(null)
            backgroundImageView.visibility = View.GONE
        } else {
            backgroundImageView.visibility = View.VISIBLE
            backgroundImageView.load(imageFile) {
                allowHardware(false)
                crossfade(false)
                val radiusPx = blurDp * resources.displayMetrics.density
                if (radiusPx >= 0.5f) {
                    transformations(ReaderBackgroundBlurTransformation(radiusPx.roundToInt()))
                }
            }
        }
    }

    /**
     * 渐变动画用：把背景清为透明，让 ReadView 的实心背景充当"静止底层"。
     * 这样只有文字内容会随 alpha 淡入，背景不会动。
     * 动画结束后由 [restoreBackgroundForFade] 或下一次 [setReaderBackground] 恢复。
     */
    internal fun stripBackgroundForFade() {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // 图片背景保持可见：淡入淡出期间作为静止底层，避免露出 ReadView 的白色底色
        backgroundImageView.alpha = if (backgroundImageView.visibility == View.VISIBLE) {
            readerBackgroundImageOpacity
        } else {
            0f
        }
    }

    /**
     * 渐变动画用：设置本页的淡入淡出透明度。
     * - 纯色背景：整页透明（ReadView 实心底色充当静止底层）
     * - 图片背景：整页保持不透明，仅文字淡入淡出，图片背景全程静止
     */
    internal fun setFadeAlpha(alpha: Float) {
        if (backgroundImageView.visibility == View.VISIBLE) {
            textView.alpha = alpha
            justifiedView.alpha = alpha
            verticalTextView.alpha = alpha
        } else {
            textView.alpha = 1f
            justifiedView.alpha = 1f
            verticalTextView.alpha = 1f
            this.alpha = alpha.coerceIn(0f, 1f)
        }
    }

    /** 渐变动画结束 / abort 时恢复背景色；图片背景由下一次 setReaderBackground 完整恢复。 */
    internal fun restoreBackgroundForFade(bgColor: Int) {
        setBackgroundColor(bgColor)
        backgroundImageView.alpha = readerBackgroundImageOpacity
        textView.alpha = 1f
        justifiedView.alpha = 1f
        verticalTextView.alpha = 1f
        this.alpha = 1f
    }

    /**
     * 压制系统浮动工具栏：选词时使用自定义菜单而非系统菜单。
     * 必须在 onSelectionAction 回调设置之后调用，
     * 否则自定义菜单也会被压制。
     */
    fun suppressSystemToolbar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
        }
        textView.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
                textView.post {
                    menu?.clear()
                    mode?.hide(Long.MAX_VALUE)
                }
                return true
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
                textView.post {
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
    private var justifyLastLine: Boolean = false
    private var writingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL
    private var verticalGeometry: VerticalPageGeometry? = null
    // A page can only contain complete lines, so the unused line-box space is split between
    // the two edges after layout. Translation keeps the configured padding (and line metrics)
    // unchanged while moving all visible/interactive renderers together.
    private var basePaddingLeft = 0
    private var basePaddingTop = 0
    private var basePaddingRight = 0
    private var basePaddingBottom = 0
    private var pageVerticalOffset = 0f

    fun setPageContent(
        fullText: CharSequence,
        startChar: Int,
        endChar: Int,
        highlights: List<Triple<Int, Int, Int>> = emptyList(),
        verticalGeometry: VerticalPageGeometry? = null
    ) {
        this.verticalGeometry = verticalGeometry
        pageVerticalOffset = 0f
        textView.translationY = 0f
        justifiedView.translationY = 0f
        if (startChar < 0 || endChar > fullText.length || startChar >= endChar) {
            chapterStartOffset = startChar
            textView.text = ""
            justifyLastLine = false
            justifiedView.justifyLastLine = false
            justifiedView.text = null
            verticalTextView.clearPage()
            clearCoverPage()
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
            justifyLastLine = false
            justifiedView.justifyLastLine = false
            justifiedView.text = null
            verticalTextView.clearPage()
            clearCoverPage()
            return
        }

        justifyLastLine = endChar < fullText.length &&
            endChar > actualStart &&
            fullText[endChar - 1] != '\n' &&
            fullText[endChar - 1] != '\r'
        justifiedView.justifyLastLine = justifyLastLine

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
            // SpannableStringBuilder(pageText) 已复制原段落的 LeadingMarginSpan。
            // 必须先移除，再按页面是否从段落中间开始重新挂载。
            spannable.getSpans(0, spannable.length, android.text.style.LeadingMarginSpan::class.java)
                .forEach(spannable::removeSpan)
            spannable.getSpans(
                0,
                spannable.length,
                com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan::class.java
            ).forEach(spannable::removeSpan)

            // 恢复 LeadingMarginSpan（首行缩进）
            // 如果页面从段落中间开始（前一个字符不是 \n），跳过开头到第一个 \n 的缩进
            val startsMidParagraph = pageStartsMidParagraph(fullText, actualStart)
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
                val coversOnlySpacerLine = start < end &&
                    (start until end).all { index ->
                        spannable[index] == '\n' || spannable[index] == '\r'
                    }
                if (coversOnlySpacerLine) {
                    spannable.setSpan(lh, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            Log.d(TAG, "setPageContent: restored ${lms.size} LeadingMarginSpans, ${lhs.size} ParagraphLineHeightSpans")
        }

        // 应用高亮（将全局偏移转换为页内偏移，基于 actualStart 而非 startChar）
        var ttsHighlightInfo: Triple<Int, Int, Int>? = null
        for ((hStart, hEnd, hColor) in highlights) {
            val localStart = (hStart - actualStart).coerceIn(0, spannable.length)
            val localEnd = (hEnd - actualStart).coerceIn(0, spannable.length)
            if (localStart < localEnd) {
                if ((hColor and 0x00FFFFFF) == SEARCH_HIGHLIGHT_RGB) {
                    spannable.setSpan(
                        ReaderSearchHighlightSpan(hColor ushr 24),
                        localStart, localEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else if ((hColor and 0x00FFFFFF) == TTS_HIGHLIGHT_RGB) {
                    val ttsColor = TtsSentenceHighlightSpan.computeHighlightColor(currentBgColor, 0.06f)
                    spannable.setSpan(
                        TtsSentenceHighlightSpan(ttsColor),
                        localStart, localEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    ttsHighlightInfo = Triple(localStart, localEnd, ttsColor)
                } else if (hColor ushr 24 == UNDERLINE_FLAG) {
                    // 涓嬪垝绾匡細浣跨敤鏂囧瓧棰滆壊 + 涓嬪垝绾匡紝涓嶇敾鑳屾櫙
                    val underlineColor = 0xFF000000.toInt() or (hColor and 0x00FFFFFF)
                    spannable.setSpan(WaveUnderlineSpan(underlineColor), localStart, localEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    spannable.setSpan(
                        ReaderHighlightSpan(hColor),
                        localStart, localEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
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

        // One layout now owns visible glyphs, images, highlights, and selection geometry.
        textView.text = spannable
        textView.scrollTo(0, 0)
        justifiedView.text = spannable
        // 保存原始 spannable（含真实 BitmapDrawable ImageSpan），供 moveSlot/syncText 使用
        this.originalSpannable = spannable
        updateCoverPage(spannable)

        // setTextIsSelectable(true) 时 Android 内部通过 Editable.Factory.newEditable() 创建副本
        // 必须从 textView.text 取实际存储的 Spannable，否则 SpanWatcher 注册在死对象上
        val actualSpannable = textView.text as? Spannable ?: spannable
        val requiresCustomTextDrawing =
            actualSpannable.getSpans(0, actualSpannable.length, ReaderHighlightSpan::class.java).isNotEmpty() ||
                actualSpannable.getSpans(0, actualSpannable.length, ReaderSearchHighlightSpan::class.java).isNotEmpty() ||
                actualSpannable.getSpans(0, actualSpannable.length, TtsSentenceHighlightSpan::class.java).isNotEmpty() ||
                actualSpannable.getSpans(0, actualSpannable.length, WaveUnderlineSpan::class.java).isNotEmpty()
        val requiredLayerType = if (requiresCustomTextDrawing) {
            View.LAYER_TYPE_SOFTWARE
        } else {
            View.LAYER_TYPE_NONE
        }
        if (textView.layerType != requiredLayerType) {
            textView.setLayerType(requiredLayerType, null)
        }
        val tts = ttsHighlightInfo
        if (tts != null) {
            justifiedView.setTtsHighlight(tts.first, tts.second, tts.third)
            verticalTextView.setTtsHighlight(tts.first, tts.second, tts.third)
        } else {
            justifiedView.clearTtsHighlight()
            verticalTextView.clearTtsHighlight()
        }
        verticalTextView.setPage(actualSpannable, verticalGeometry, actualStart)
        onTextSet?.invoke(actualSpannable)
        invalidateRenderers()
        balancePageVerticalPosition()
    }

    /** Update the sentence overlay without rebuilding text, images, or pagination. */
    fun updateTtsHighlight(chapterStart: Int?, chapterEnd: Int?) {
        val actualSpannable = textView.text as? Spannable
        val sourceSpannable = originalSpannable
        val targets = buildList {
            actualSpannable?.let(::add)
            if (sourceSpannable != null && sourceSpannable !== actualSpannable) add(sourceSpannable)
        }
        targets.forEach { text ->
            text.getSpans(0, text.length, TtsSentenceHighlightSpan::class.java)
                .forEach(text::removeSpan)
        }

        val localStart = chapterStart?.minus(chapterStartOffset)?.coerceIn(0, actualSpannable?.length ?: 0)
        val localEnd = chapterEnd?.minus(chapterStartOffset)?.coerceIn(0, actualSpannable?.length ?: 0)
        if (localStart != null && localEnd != null && localStart < localEnd) {
            val color = TtsSentenceHighlightSpan.computeHighlightColor(currentBgColor, 0.06f)
            targets.forEach { text ->
                text.setSpan(
                    TtsSentenceHighlightSpan(color),
                    localStart.coerceAtMost(text.length),
                    localEnd.coerceAtMost(text.length),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            justifiedView.setTtsHighlight(localStart, localEnd, color)
            verticalTextView.setTtsHighlight(localStart, localEnd, color)
            if (textView.layerType != View.LAYER_TYPE_SOFTWARE) {
                textView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
        } else {
            justifiedView.clearTtsHighlight()
            verticalTextView.clearTtsHighlight()
        }
        textView.invalidate()
        justifiedView.invalidate()
        verticalTextView.invalidate()
        invalidate()
    }

    private fun updateCoverPage(spannable: Spannable) {
        val isMarkedCover = spannable.getSpans(
            0,
            spannable.length,
            EpubParser.CoverPageSpan::class.java
        ).isNotEmpty()
        val image = spannable.getSpans(0, spannable.length, ImageSpan::class.java).singleOrNull()
        if (!isMarkedCover || image == null) {
            clearCoverPage()
            return
        }

        showingCoverPage = true
        coverImageSpan = image
        coverImageView.setImageDrawable(image.drawable.constantState?.newDrawable()?.mutate() ?: image.drawable)
        coverImageView.visibility = View.VISIBLE
        updateContentRendererVisibility()
    }

    private fun clearCoverPage() {
        showingCoverPage = false
        coverImageSpan = null
        coverImageView.setImageDrawable(null)
        coverImageView.visibility = View.GONE
        updateContentRendererVisibility()
    }

    private fun updateContentRendererVisibility() {
        val vertical = writingMode.isVertical
        textView.visibility = if (showingCoverPage || vertical) View.INVISIBLE else View.VISIBLE
        verticalTextView.visibility = if (!showingCoverPage && vertical) View.VISIBLE else View.GONE
    }

    fun setSearchHighlightAlpha(alpha: Int) {
        originalSpannable
            ?.getSpans(0, originalSpannable?.length ?: 0, ReaderSearchHighlightSpan::class.java)
            ?.forEach { it.alpha = alpha.coerceIn(0, 255) }
        textView.invalidate()
        justifiedView.invalidate()
        verticalTextView.invalidate()
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
        accentColor: Int = 0xFF007AFF.toInt(),
        textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
        writingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL,
        boldText: Boolean = false
    ) {
        this.writingMode = writingMode
        val spacingRatio = if (fontSizePx > 0) letterSpacingPx / fontSizePx else 0f

        // Native TextView is the visible renderer as well as the selection owner.
        if (textView.paint.textSize != fontSizePx) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
        }
        textView.setTextColor(textColor)
        if (textView.typeface !== typeface) {
            textView.typeface = typeface
        }
        // 正文字重（PR #19 #24）：选择层 paint 即分页引擎 sharedTextPaint 的来源，
        // 在此设置后 StaticLayout 行宽与渲染保持一致
        if (textView.paint.isFakeBoldText != boldText) {
            textView.paint.isFakeBoldText = boldText
            textView.invalidate()
        }
        justifiedView.setFakeBold(boldText)
        // TextView.setLineSpacing() always discards its internal Layout, even when
        // both values are unchanged. Page-turn completion configures every slot;
        // avoid clearing the freshly built destination layout immediately before
        // RoundedHighlightTextView draws its saved highlight background.
        if (textView.lineSpacingExtra != lineSpacingExtraPx ||
            textView.lineSpacingMultiplier != lineHeightMult
        ) {
            textView.setLineSpacing(lineSpacingExtraPx, lineHeightMult)
        }
        if (textView.letterSpacing != spacingRatio) {
            textView.letterSpacing = spacingRatio
        }
        // 🔥 守卫：仅在值变更时才设置，避免无条件触发 nullLayouts() + requestLayout()
        // Android 的 setBreakStrategy/setHyphenationFrequency 不检查相等性，即使值相同
        // 也会无效化已存在的 Layout，导致多余的 layout pass → 内容位移
        if (textView.breakStrategy != Layout.BREAK_STRATEGY_SIMPLE) {
            textView.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (textView.hyphenationFrequency != Layout.HYPHENATION_FREQUENCY_NONE) {
                textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            }
        }
        val justificationMode = if (textAlignment == ReaderTextAlignment.JUSTIFY) {
            Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            Layout.JUSTIFICATION_MODE_NONE
        }
        if (textView.justificationMode != justificationMode) {
            textView.justificationMode = justificationMode
        }
        // 🔥 守卫：仅在 padding 实际变更时调用 setPadding，避免无谓的 requestLayout()
        val ml = marginLeftPx.toInt()
        val mt = marginTopPx.toInt()
        val mr = marginRightPx.toInt()
        val mb = marginBottomPx.toInt()
        if (textView.paddingLeft != ml || textView.paddingTop != mt ||
            textView.paddingRight != mr || textView.paddingBottom != mb) {
            basePaddingLeft = ml
            basePaddingTop = mt
            basePaddingRight = mr
            basePaddingBottom = mb
            textView.setPadding(ml, mt, mr, mb)
        }
        if (textView.paddingLeft == ml && textView.paddingTop == mt &&
            textView.paddingRight == mr && textView.paddingBottom == mb
        ) {
            basePaddingLeft = ml
            basePaddingTop = mt
            basePaddingRight = mr
            basePaddingBottom = mb
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
        verticalTextView.configure(fontSizePx, textColor, typeface, accentColor)
        if (verticalTextView.paddingLeft != ml || verticalTextView.paddingTop != mt ||
            verticalTextView.paddingRight != mr || verticalTextView.paddingBottom != mb) {
            verticalTextView.setPadding(ml, mt, mr, mb)
        }
        updateContentRendererVisibility()
        balancePageVerticalPosition()
    }

    /**
     * Split the unused space left by complete-line pagination across the page edges.
     * TextView's line bottoms provide the actual line-box height, so this does not alter
     * wrapping or page boundaries. All interactive coordinate paths subtract the same offset.
     */
    private fun balancePageVerticalPosition() {
        if (writingMode.isVertical || showingCoverPage || width <= 0 || height <= 0) {
            pageVerticalOffset = 0f
            textView.translationY = 0f
            justifiedView.translationY = 0f
            return
        }
        val layout = textView.layout ?: return
        val contentHeight = if (layout.lineCount > 0) {
            layout.getLineBottom(layout.lineCount - 1)
        } else {
            0
        }
        val availableHeight = (height - basePaddingTop - basePaddingBottom).coerceAtLeast(0)
        val remainder = (availableHeight - contentHeight).coerceAtLeast(0)
        // Only balance the small quantization remainder left on a normal page. A short final
        // page is intentionally top-aligned; centering two lines in a full viewport is jarring.
        val firstLineHeight = if (layout.lineCount > 0) {
            (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1)
        } else {
            1
        }
        val lastLine = (layout.lineCount - 1).coerceAtLeast(0)
        val firstInkTop = layout.getLineBaseline(0) + layout.getLineAscent(0)
        val lastInkBottom = layout.getLineBaseline(lastLine) + layout.getLineDescent(lastLine)
        val topMetricGap = (firstInkTop - layout.getLineTop(0)).toFloat()
        val bottomMetricGap = (contentHeight - lastInkBottom).toFloat()
        val metricCorrection = (bottomMetricGap - topMetricGap) / 2f
        // Keep the first-line baseline stable on every page. Only a small remainder from a
        // nearly full page is distributed; a short final page receives the same font-metric
        // correction but is never centered in the viewport.
        val remainderCorrection = if (remainder <= firstLineHeight * 1.5f) {
            remainder / 2f
        } else {
            0f
        }
        val extra = (remainderCorrection + metricCorrection).coerceAtLeast(0f)
        pageVerticalOffset = extra
        textView.translationY = extra
        justifiedView.translationY = extra
    }

    /** Additional top padding applied to the current page after line pagination. */
    fun getPageVerticalOffset(): Float = pageVerticalOffset

    /** 获取当前 TextView 的 Spannable（用于读取选区等） */
    fun getTextSpannable(): Spannable? = textView.text as? Spannable

    fun getVisualLineInfo(offset: Int): Pair<Int, Int>? = justifiedView.getLineInfoForOffset(offset)

    /** 返回指定页面坐标处的 EPUB 链接；未命中链接时返回 null。 */
    fun getLinkAt(x: Float, y: Float): String? = if (writingMode.isVertical) {
        verticalTextView.getLinkAt(x, y)
    } else {
        justifiedView.getLinkAtPosition(x, y - pageVerticalOffset)
    }

    /**
     * Returns the EPUB image at the supplied page coordinate.
     *
     * The native [textView] is the actual visible renderer. Using its [Layout] keeps
     * hit testing aligned with Android's ImageSpan placement instead of the legacy
     * invisible justified renderer, whose line metrics can differ for large images.
     */
    fun getImageAt(x: Float, y: Float): ReaderImageHit? {
        coverImageHit(x, y)?.let { return it }
        if (writingMode.isVertical) return verticalTextView.getImageAt(x, y)
        val spannable = textView.text as? Spannable ?: return null
        val textLayout = textView.layout ?: return null
        val localX = x - textView.totalPaddingLeft + textView.scrollX
        val localY = y - textView.totalPaddingTop - pageVerticalOffset + textView.scrollY
        if (localX < 0f || localY < 0f || localY >= textLayout.height) return null

        val line = textLayout.getLineForVertical(localY.toInt())
        val lineStart = textLayout.getLineStart(line)
        val lineEnd = textLayout.getLineEnd(line)
        val images = spannable.getSpans(lineStart, lineEnd, ImageSpan::class.java)
        if (images.isEmpty()) return null

        for (image in images) {
            val spanStart = spannable.getSpanStart(image).coerceAtLeast(0)
            val spanEnd = spannable.getSpanEnd(image).coerceAtLeast(spanStart + 1)
            val drawable = image.drawable
            val imageWidth = drawable.bounds.width().toFloat().coerceAtLeast(1f)
            val imageHeight = drawable.bounds.height().toFloat().coerceAtLeast(1f)
            val imageLeft = textView.totalPaddingLeft +
                textLayout.getPrimaryHorizontal(spanStart) - textView.scrollX
            val lineTop = textLayout.getLineTop(line).toFloat()
            val lineBottom = textLayout.getLineBottom(line).toFloat()
            val imageTopInLayout = when (image.verticalAlignment) {
                DynamicDrawableSpan.ALIGN_BASELINE ->
                    textLayout.getLineBaseline(line).toFloat() - imageHeight
                DynamicDrawableSpan.ALIGN_CENTER ->
                    lineTop + (lineBottom - lineTop - imageHeight) / 2f
                else -> lineBottom - imageHeight
            }
            val imageTop = textView.totalPaddingTop + imageTopInLayout - textView.scrollY
            val imageRight = imageLeft + imageWidth
            val imageBottom = imageTop + imageHeight
            if (x !in imageLeft..imageRight || y !in imageTop..imageBottom) continue

            val url = spannable.getSpans(spanStart, spanEnd, URLSpan::class.java)
                .firstOrNull()?.url
            val hasAction = spannable.getSpans(spanStart, spanEnd, ClickableSpan::class.java)
                .isNotEmpty()
            return ReaderImageHit(
                source = image.source.orEmpty(),
                leftPx = imageLeft,
                topPx = imageTop,
                rightPx = imageRight,
                bottomPx = imageBottom,
                naturalWidth = drawable.intrinsicWidth.coerceAtLeast(drawable.bounds.width()),
                naturalHeight = drawable.intrinsicHeight.coerceAtLeast(drawable.bounds.height()),
                link = url,
                hasAction = hasAction
            )
        }
        return null
    }

    private fun coverImageHit(x: Float, y: Float): ReaderImageHit? {
        if (!showingCoverPage || x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) return null
        val image = coverImageSpan ?: return null
        val drawable = image.drawable
        val naturalWidth = drawable.intrinsicWidth.coerceAtLeast(drawable.bounds.width()).coerceAtLeast(1)
        val naturalHeight = drawable.intrinsicHeight.coerceAtLeast(drawable.bounds.height()).coerceAtLeast(1)
        val scale = minOf(width.toFloat() / naturalWidth, height.toFloat() / naturalHeight)
        val displayedWidth = naturalWidth * scale
        val displayedHeight = naturalHeight * scale
        val left = (width - displayedWidth) / 2f
        val top = (height - displayedHeight) / 2f
        val right = left + displayedWidth
        val bottom = top + displayedHeight
        if (x !in left..right || y !in top..bottom) return null

        val spannable = originalSpannable ?: (textView.text as? Spannable)
        val spanStart = spannable?.getSpanStart(image)?.coerceAtLeast(0) ?: 0
        val spanEnd = spannable?.getSpanEnd(image)?.coerceAtLeast(spanStart + 1) ?: 1
        val link = spannable?.getSpans(spanStart, spanEnd, URLSpan::class.java)?.firstOrNull()?.url
        val hasAction = spannable?.getSpans(spanStart, spanEnd, ClickableSpan::class.java)?.isNotEmpty() == true
        return ReaderImageHit(
            source = image.source.orEmpty(),
            leftPx = left,
            topPx = top,
            rightPx = right,
            bottomPx = bottom,
            naturalWidth = naturalWidth,
            naturalHeight = naturalHeight,
            link = link,
            hasAction = hasAction
        )
    }

    /** 缓存当前页在章节中的起始字符偏移（用于选区偏移转换） */
    var chapterStartOffset: Int = 0
        private set

    fun firstVisibleCharacterOffset(): Int? {
        val pageText = textView.text ?: return null
        if (pageText.isEmpty()) return null
        if (writingMode.isVertical) {
            val firstGlyph = verticalGeometry?.glyphs?.firstOrNull { glyph ->
                val localOffset = glyph.startOffset - chapterStartOffset
                localOffset in pageText.indices && !pageText[localOffset].isWhitespace()
            }
            if (firstGlyph != null) return firstGlyph.startOffset
        }
        val localOffset = pageText.indexOfFirst { !it.isWhitespace() }
        return if (localOffset >= 0) chapterStartOffset + localOffset else null
    }

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
        val ty = y - textView.top - textView.paddingTop - pageVerticalOffset

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
            verticalTextView.invalidate()
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
        justifyLastLine = false
        justifiedView.justifyLastLine = false
        justifiedView.text = null
        verticalTextView.clearPage()
        originalSpannable = null
        verticalGeometry = null
        pageVerticalOffset = 0f
        textView.translationY = 0f
        justifiedView.translationY = 0f
        clearCoverPage()
    }

    /** 获取原始 spannable（含真实 ImageSpan），供 moveSlot 使用 */
    fun getJustifiedText(): Spannable? = originalSpannable

    fun getVerticalGeometry(): VerticalPageGeometry? = verticalGeometry

    fun shouldJustifyLastLine(): Boolean = justifyLastLine

    /**
     * 同步设置文本到 textView 和 justifiedView。
     * 用于外部直接设置 textView.text 的场景（如 PageSlotManager.moveSlot）。
     *
     * @param textViewText 设置给隐藏 textView 的文本（可含透明 ImageSpan）
     * @param justifiedText 设置给可见 justifiedView 的文本（应含真实 ImageSpan），null 时回退到 textViewText
     */
    fun syncText(
        textViewText: CharSequence?,
        justifiedText: Spannable? = null,
        justifyLastLine: Boolean = false,
        chapterStartOffset: Int = 0,
        verticalGeometry: VerticalPageGeometry? = null
    ) {
        this.chapterStartOffset = chapterStartOffset
        this.justifyLastLine = justifyLastLine
        this.verticalGeometry = verticalGeometry
        pageVerticalOffset = 0f
        textView.translationY = 0f
        justifiedView.translationY = 0f
        justifiedView.justifyLastLine = justifyLastLine
        // 🔥 先设置 justifiedView（只 invalidate，不触发父布局），再设置 textView（可能触发父布局）
        // 确保 textView 触发的 layout pass 中，justifiedView 已有正确内容供 onSizeChanged → rebuildLayout 使用
        justifiedView.text = justifiedText
            ?: (textViewText as? Spannable ?: textViewText?.let { SpannableStringBuilder(it) })
        textView.text = textViewText
        textView.scrollTo(0, 0)
        // 如果传入了 justifiedText，同步更新 originalSpannable
        if (justifiedText != null) {
            this.originalSpannable = justifiedText
        }
        val syncedSpannable = justifiedText ?: (textView.text as? Spannable)
        if (syncedSpannable != null) {
            updateCoverPage(syncedSpannable)
        } else {
            clearCoverPage()
        }
        // Slot rotation replaces TextView's internal Editable. Re-register the selection
        // watcher on that new instance or selections stop producing menus after a page turn.
        (textView.text as? Spannable)?.let {
            verticalTextView.setPage(it, verticalGeometry, chapterStartOffset)
            onTextSet?.invoke(it)
        }
        invalidateRenderers()
        balancePageVerticalPosition()
    }

    /**
     * Saved highlights are drawn by the child renderers, not by this container.
     * Slot rotation can otherwise leave a child's hardware display list stale
     * until the next touch event invalidates it.
     */
    private fun invalidateRenderers() {
        // Slot rotation happens at the terminal animation frame. Rebuild the
        // fixed-size child layouts synchronously so that the first idle frame
        // already contains the new highlight spans instead of an old display list.
        if (width > 0 && height > 0) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            listOf(textView, justifiedView, verticalTextView).forEach { child ->
                child.forceLayout()
                child.measure(widthSpec, heightSpec)
                child.layout(0, 0, width, height)
            }
        } else {
            textView.requestLayout()
            justifiedView.requestLayout()
            verticalTextView.requestLayout()
            requestLayout()
        }
        textView.invalidate()
        justifiedView.invalidate()
        verticalTextView.invalidate()
        invalidate()
        postInvalidateOnAnimation()
    }

    fun getVerticalSelectionBounds(): Pair<VerticalRect, VerticalRect>? =
        verticalTextView.selectionScreenBounds()

    fun isVerticalSelectionHandleDragActive(): Boolean =
        writingMode.isVertical && verticalTextView.isSelectionHandleDragActive()
}

/*
 * Adapted from NovelReader (https://github.com/JustWayward/BookReader)
 * Original copyright (c) 2017 GuangXiang Chen, MIT License.
 */
package com.ebook.reader.ui.reader.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View that renders TXT content as pages and handles page-turn animation.
 * Uses StaticLayout for text layout and Scroller for animation.
 *
 * Adapted from NovelReader's PageView + PageLoader + SlidePageAnim.
 */
class TxtPageView(
    context: Context,
    private var textProvider: (chapterIndex: Int) -> String?,
    private var chapterCount: Int,
    private var onPageChanged: (chapterIndex: Int, pageIndex: Int, total: Int) -> Unit
) : View(context) {

    companion object {
        private const val TAG = "TxtPageView"
        private const val MARGIN_W_DP = 12  // horizontal margin (% of screen width)
        private const val MARGIN_H_PX = 70f // vertical margin (px, matching EPUB PAD_V)
        private const val LINE_SPACING_PX = 8f
    }

    // ── Dimensions ──
    private var viewW = 0; private var viewH = 0
    private var marginW = 0; private var marginH = 0

    // ── Animation ──
    private lateinit var anim: SlidePageAnim
    private val animListener = object : OnPageChangeListener {
        override fun hasPrev(): Boolean {
            if (curPageIdx > 0) return true
            return curChapterIdx > 0
        }
        override fun hasNext(): Boolean {
            if (curPageIdx < curPages.size - 1) return true
            return curChapterIdx < chapterCount - 1
        }
        override fun pageCancel() {
            // restore cancelled page
            if (cancelPage != null) {
                curPage = cancelPage; cancelPage = null
                curPageIdx = curPage?.position ?: 0
            }
        }
    }

    // ── Page data ──
    private var curChapterIdx = 0
    private var curPageIdx = 0
    private var curPage: TxtPage? = null
    private var curPages: List<TxtPage> = emptyList()
    private var prevPages: List<TxtPage> = emptyList()
    private var nextPages: List<TxtPage>? = null
    private var cancelPage: TxtPage? = null

    // ── Layout cache ──
    private val layoutCache = object : LinkedHashMap<Int, Pair<StaticLayout, List<TxtPage>>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Pair<StaticLayout, List<TxtPage>>>?): Boolean = size > 3
    }

    // ── Paints ──
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }

    private var textColor = android.graphics.Color.BLACK
    private var bgColor = android.graphics.Color.WHITE
    private var fontSizePx = 48f

    // ── Config ──
    fun configure(fontSizeSp: Float, bg: Int, text: Int, density: Float) {
        fontSizePx = fontSizeSp * density
        textPaint.textSize = fontSizePx; textPaint.color = text; bgPaint.color = bg
        textColor = text; bgColor = bg
        // Re-layout if dimensions ready
        if (viewW > 0 && viewH > 0) {
            layoutCache.clear()
            loadChapter(curChapterIdx, curPageIdx.coerceAtLeast(0))
        }
    }

    fun setChapterCount(count: Int) { chapterCount = count }

    fun jumpToChapter(chapterIdx: Int, pageIdx: Int) {
        layoutCache.clear()
        loadChapter(chapterIdx.coerceIn(0, chapterCount - 1), pageIdx.coerceAtLeast(0))
    }

    // ── Chapter/page loading ──
    private fun loadChapter(chapterIdx: Int, pageIdx: Int) {
        val pages = getPages(chapterIdx) ?: return
        curChapterIdx = chapterIdx; curPages = pages
        curPageIdx = pageIdx.coerceIn(0, pages.size - 1)
        curPage = pages[curPageIdx]
        cancelPage = null
        // Preload adjacent chapters
        prevPages = if (chapterIdx > 0) getPages(chapterIdx - 1) ?: emptyList() else emptyList()
        nextPages = if (chapterIdx < chapterCount - 1) getPages(chapterIdx + 1) else null
        // Render current page to anim.nextBitmap
        drawPage(anim.nextBitmap, pages[curPageIdx])
        Log.d(TAG, "loadChapter: ch=$chapterIdx pg=$curPageIdx total=${pages.size}")
        onPageChanged(chapterIdx, curPageIdx, pages.size)
    }

    private fun getPages(chapterIdx: Int): List<TxtPage>? {
        layoutCache[chapterIdx]?.let { return it.second }
        val text = textProvider(chapterIdx) ?: return null
        if (text.isEmpty()) return null
        val textW = viewW - marginW * 2
        if (textW <= 0) return null
        val textH = viewH - marginH * 2
        val sl = StaticLayout(text, 0, text.length, textPaint, textW, Layout.Alignment.ALIGN_NORMAL, 1f, LINE_SPACING_PX, false)
        val pages = splitPages(sl, textH)
        layoutCache[chapterIdx] = sl to pages
        return pages
    }

    private fun splitPages(sl: StaticLayout, pageH: Int): List<TxtPage> {
        val result = mutableListOf<TxtPage>()
        var lineStart = 0
        var pgIdx = 0
        while (lineStart < sl.lineCount) {
            var y = 0f; var line = lineStart
            while (line < sl.lineCount) {
                val lh = sl.getLineBottom(line) - sl.getLineTop(line) + LINE_SPACING_PX
                if (y + lh > pageH && line > lineStart) break
                y += lh; line++
            }
            val lines = (lineStart until line).map { i ->
                sl.text.substring(sl.getLineStart(i), sl.getLineEnd(i))
            }
            result.add(TxtPage(position = pgIdx, lines = lines))
            pgIdx++; lineStart = line
        }
        return result
    }

    // ── Rendering ──
    private fun drawPage(bitmap: Bitmap, page: TxtPage) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)
        canvas.save()
        canvas.translate(marginW.toFloat(), marginH.toFloat())
        var y = 0f
        for (line in page.lines) {
            canvas.drawText(line, 0f, y - textPaint.fontMetrics.ascent, textPaint)
            y += textPaint.fontSpacing + LINE_SPACING_PX
        }
        canvas.restore()
    }

    private fun drawPageBitmap(page: TxtPage, target: Bitmap?): Bitmap {
        val bmp = target ?: Bitmap.createBitmap(viewW, viewH, Bitmap.Config.RGB_565)
        drawPage(bmp, page); return bmp
    }

    // ── View lifecycle ──
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w; viewH = h
        marginW = (w * MARGIN_W_DP / 100).toInt()
        marginH = (MARGIN_H_PX * resources.displayMetrics.density).toInt()
        if (!::anim.isInitialized) {
            anim = SlidePageAnim(w, h, marginW, marginH, this, animListener)
            loadChapter(curChapterIdx, curPageIdx)
        } else {
            // Size change → re-layout
            layoutCache.clear()
            loadChapter(curChapterIdx, curPageIdx)
        }
    }

    override fun onDraw(canvas: Canvas) {
        anim.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::anim.isInitialized) return false
        val result = anim.onTouchEvent(event)
        if (::anim.isInitialized && !anim.isRunning && anim.scroller.isFinished) {
            // Animation just completed → commit page turn
            commitPageTurn()
        }
        return result
    }

    override fun computeScroll() {
        if (::anim.isInitialized && anim.isRunning) {
            anim.scrollAnim()
            if (!anim.isRunning) commitPageTurn()
        }
    }

    // ── Page turn commit ──
    private fun commitPageTurn() {
        val isNext = anim.isRunning // actually, need to check direction
        // Determine direction from scroller
        if (anim.scroller.finalX > anim.scroller.startX && !anim.isCancel) {
            // PREV — moved right
            if (curPageIdx > 0) {
                curPageIdx--
            } else if (curChapterIdx > 0) {
                // Cross chapter backward
                curChapterIdx--
                curPages = prevPages
                curPageIdx = (curPages.size - 1).coerceAtLeast(0)
                prevPages = if (curChapterIdx > 0) getPages(curChapterIdx - 1) ?: emptyList() else emptyList()
                nextPages = getPages(curChapterIdx + 1)
            }
            curPage = curPages.getOrNull(curPageIdx)
        } else if (anim.scroller.finalX < anim.scroller.startX && !anim.isCancel) {
            // NEXT — moved left
            if (curPageIdx < curPages.size - 1) {
                curPageIdx++
            } else if (curChapterIdx < chapterCount - 1) {
                // Cross chapter forward
                curChapterIdx++
                prevPages = curPages
                curPages = nextPages ?: getPages(curChapterIdx) ?: return
                curPageIdx = 0
                nextPages = if (curChapterIdx < chapterCount - 1) getPages(curChapterIdx + 1) else null
            }
            curPage = curPages.getOrNull(curPageIdx)
        }

        val cp = curPage
        if (cp != null) {
            // Render new current page to nextBitmap (which becomes curBitmap after changePage)
            anim.changePage()
            drawPage(anim.nextBitmap, cp)
            cancelPage = null
            Log.d(TAG, "commitPageTurn: ch=$curChapterIdx pg=$curPageIdx")
            onPageChanged(curChapterIdx, curPageIdx, curPages.size)
        }
        invalidate()
    }

    // ── Page pre-render ──
    private fun preRenderPrev() {
        if (curPageIdx > 0) {
            drawPageBitmap(curPages[curPageIdx - 1], null)
        } else if (prevPages.isNotEmpty()) {
            drawPageBitmap(prevPages.last(), null)
        }
    }

    private fun preRenderNext() {
        if (curPageIdx < curPages.size - 1) {
            drawPageBitmap(curPages[curPageIdx + 1], null)
        } else {
            nextPages?.firstOrNull()?.let { drawPageBitmap(it, null) }
        }
    }

    fun recycle() {
        layoutCache.clear()
        if (::anim.isInitialized) {
            anim.curBitmap.recycle(); anim.nextBitmap.recycle()
        }
    }
}

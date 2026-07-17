package com.huangder.lumibooks.ui.reader.engine

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 连续滚动模式的 RecyclerView Adapter。
 *
 * 每个 item = 一页内容（全屏 PageContentView）。
 * 按需加载章节：滚动到底部时加载下一章，通过 chapterLoadCallback 触发。
 */
class ContinuousPageAdapter(
    private val layoutEngine: PageLayoutEngine,
    private val contentProvider: (suspend (Int) -> CharSequence?)?,
    private val chapterLoadCallback: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ContinuousPageAdapter.PageViewHolder>() {

    companion object {
        private const val TAG = "ContinuousPageAdapter"
    }

    // 已布局的章节数量（决定了总页数）
    private var laidOutChapters: Int = 0

    // 总页数缓存
    private var totalPages: Int = 0

    // 样式参数（从 ReadView.configure 同步）
    var fontSizePx: Float = 56f
    var textColor: Int = 0xFF333333.toInt()
    var lineHeightMult: Float = 1.5f
    var letterSpacingPx: Float = 0f
    var typeface: android.graphics.Typeface = android.graphics.Typeface.DEFAULT
    var marginHorizPx: Float = 48f
    var marginVertPx: Float = 32f
    var highlightColor: Int = 0x40007AFF.toInt()
    var accentColor: Int = 0xFF007AFF.toInt()
    var chineseMode: String = "original"

    // RecyclerView 高度缓存（用于设置 item 高度）
    private var recyclerViewHeight: Int = 0

    /** 当新的章节布局完成后调用，更新总页数 */
    fun onChapterLaidOut(chapterIndex: Int) {
        if (chapterIndex >= laidOutChapters - 1) {
            laidOutChapters = chapterIndex + 1
            totalPages = layoutEngine.getTotalPages()
            Log.d(TAG, "Chapter $chapterIndex laid out, total pages: $totalPages")
            notifyDataSetChanged()
        }
    }

    /** 初始化：至少需要 1 章已布局 */
    fun initialize() {
        laidOutChapters = layoutEngine.getChapterCount().coerceAtLeast(1)
        totalPages = layoutEngine.getTotalPages()
        Log.d(TAG, "Initialize: chapters=${layoutEngine.getChapterCount()}, pages=$totalPages")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = totalPages.coerceAtLeast(1)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        // 记录 RecyclerView 高度
        if (recyclerViewHeight <= 0) recyclerViewHeight = parent.height

        val pageView = PageContentView(parent.context)
        pageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            recyclerViewHeight.coerceAtLeast(1)  // 全屏高度
        )
        return PageViewHolder(pageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val (chapterIndex, pageInChapter) = layoutEngine.globalToLocal(position)

        // 应用样式（左右边距与正常模式一致，上下边距为 0 使页面连贯）
        holder.pageView.configure(
            fontSizePx = fontSizePx,
            textColor = textColor,
            lineHeightMult = lineHeightMult,
            letterSpacingPx = letterSpacingPx,
            typeface = typeface,
            marginLeftPx = marginHorizPx,
            marginTopPx = 0f,  // 连续滚动：无顶部间距
            marginRightPx = marginHorizPx,
            marginBottomPx = 0f,  // 连续滚动：无底部间距，页面连贯
            highlightColor = highlightColor,
            accentColor = accentColor
        )
        holder.pageView.chineseMode = chineseMode

        // 检查章节是否已布局
        val chapterLayout = layoutEngine.getChapterLayout(chapterIndex)
        if (chapterLayout == null) {
            holder.pageView.textView.text = "加载中…"
            chapterLoadCallback?.invoke(chapterIndex)
            return
        }

        val pageLayout = chapterLayout.pages.getOrNull(pageInChapter)
        if (pageLayout == null) {
            holder.pageView.textView.text = ""
            return
        }

        // 异步加载内容
        val provider = contentProvider ?: return
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val text = withContext(Dispatchers.IO) { provider(chapterIndex) }
                if (text != null) {
                    holder.pageView.setPageContent(
                        text,
                        pageLayout.startCharOffset,
                        pageLayout.endCharOffset,
                        emptyList()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load failed: ch=$chapterIndex pg=$pageInChapter", e)
            }
        }
    }

    /** 根据当前章节和页码计算滚动位置 */
    fun getScrollPosition(chapterIndex: Int, pageInChapter: Int): Int {
        return layoutEngine.localToGlobal(chapterIndex, pageInChapter)
    }

    class PageViewHolder(val pageView: PageContentView) : RecyclerView.ViewHolder(pageView)
}

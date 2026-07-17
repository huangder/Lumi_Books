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

    /** 当新的章节布局完成后调用，更新总页数 */
    fun onChapterLaidOut(chapterIndex: Int) {
        if (chapterIndex >= laidOutChapters - 1) {
            laidOutChapters = chapterIndex + 1
            totalPages = layoutEngine.getTotalPages()
            Log.d(TAG, "Chapter $chapterIndex laid out, total pages: $totalPages, laidOutChapters: $laidOutChapters")
            notifyDataSetChanged()
        }
    }

    /** 初始化：至少需要 1 章已布局 */
    fun initialize() {
        laidOutChapters = layoutEngine.getChapterCount().coerceAtLeast(1)
        totalPages = layoutEngine.getTotalPages()
        Log.d(TAG, "Initialize: chapterCount=${layoutEngine.getChapterCount()}, totalPages=$totalPages")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        val count = totalPages.coerceAtLeast(1) // 至少 1 项，避免空列表
        return count
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val pageView = PageContentView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        return PageViewHolder(pageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val (chapterIndex, pageInChapter) = layoutEngine.globalToLocal(position)
        Log.d(TAG, "onBindViewHolder: position=$position → ch=$chapterIndex pg=$pageInChapter")

        // 检查章节是否已布局
        val chapterLayout = layoutEngine.getChapterLayout(chapterIndex)
        if (chapterLayout == null) {
            // 章节未布局，显示加载中
            holder.pageView.textView.text = "加载中…"
            // 触发加载该章节
            chapterLoadCallback?.invoke(chapterIndex)
            return
        }

        // 检查页码是否有效
        val pageLayout = chapterLayout.pages.getOrNull(pageInChapter)
        if (pageLayout == null) {
            holder.pageView.textView.text = ""
            return
        }

        // 异步加载内容
        val provider = contentProvider ?: return
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    provider(chapterIndex)
                }
                if (text != null) {
                    holder.pageView.setPageContent(
                        text,
                        pageLayout.startCharOffset,
                        pageLayout.endCharOffset,
                        emptyList() // 高亮暂不处理
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load content for ch=$chapterIndex pg=$pageInChapter", e)
            }
        }
    }

    /** 根据当前章节和页码计算滚动位置 */
    fun getScrollPosition(chapterIndex: Int, pageInChapter: Int): Int {
        return layoutEngine.localToGlobal(chapterIndex, pageInChapter)
    }

    class PageViewHolder(val pageView: PageContentView) : RecyclerView.ViewHolder(pageView)
}

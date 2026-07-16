package com.huangder.lumibooks.ui.reader.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 3 槽位页级 conveyor belt 管理器。
 *
 * 三个槽位：PREV(0)、CUR(1)、NEXT(2)
 * 槽位粒度是*页*，不是章。跨章翻页只是下一页刚好在下一章。
 */
class PageSlotManager(
    private val layoutEngine: PageLayoutEngine,
    private val prevView: PageContentView,
    private val curView: PageContentView,
    private val nextView: PageContentView
) {
    companion object {
        const val SLOT_PREV = 0
        const val SLOT_CUR = 1
        const val SLOT_NEXT = 2
        private const val TAG = "PageSlotManager"
    }

    private val slots = arrayOf(
        SlotState(-1, -1, -1, false, prevView),
        SlotState(-1, -1, -1, false, curView),
        SlotState(-1, -1, -1, false, nextView)
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadingJob: Job? = null
    private var chapterCount: Int = 0

    /** 字号变化时暂存当前页的字符起始偏移，供 loadSlot 搜索修正后的页码 */
    var pendingStartCharOffset: Int = -1

    /** 文本内容提供者：根据章节索引返回文本 */
    var contentProvider: (suspend (Int) -> CharSequence?)? = null

    /** 高亮数据提供者：根据章节索引返回 (start, end, color) 列表 */
    var highlightProvider: ((Int) -> List<Triple<Int, Int, Int>>)? = null

    /** 当前全局页码 */
    var currentGlobalPage: Int = 0
        private set

    /** 当前章节索引 */
    var currentChapterIndex: Int = 0
        private set

    /** 用户翻页回调 */
    var onPageChangedCallback: ((globalPage: Int, chapterIdx: Int, pageInChapter: Int, chapterTotal: Int) -> Unit)? = null

    fun setChapterCount(count: Int) {
        chapterCount = count
    }

    /**
     * 初始化：加载起始页到 CUR 槽位，预加载 PREV 和 NEXT。
     */
    fun initialize(startChapter: Int, startPageInChapter: Int) {
        for (i in 0..2) {
            slots[i].chapterIndex = -1
            slots[i].pageIndex = -1
            slots[i].globalPageIndex = -1
            slots[i].isLoaded = false
            slots[i].contentView.clear()
        }

        currentChapterIndex = startChapter
        currentGlobalPage = layoutEngine.localToGlobal(startChapter, startPageInChapter)

        loadSlot(SLOT_CUR, currentChapterIndex, startPageInChapter)
    }

    /**
     * 加载一个槽位。
     */
    fun loadSlot(slotIdx: Int, chapterIndex: Int, pageInChapter: Int) {
        if (chapterIndex < 0 || chapterIndex >= chapterCount) return
        if (pageInChapter < 0) return

        val slot = slots[slotIdx]
        if (slot.chapterIndex == chapterIndex && slot.pageIndex == pageInChapter && slot.isLoaded) {
            return
        }

        slot.chapterIndex = chapterIndex
        slot.pageIndex = pageInChapter
        slot.isLoaded = false
        slot.contentView.clear()

        val thisJob = scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { contentProvider?.invoke(chapterIndex) }
                if (text.isNullOrEmpty()) {
                    Log.w(TAG, "Empty text for slot $slotIdx ch=$chapterIndex")
                    slot.isLoaded = false
                    if (slotIdx == SLOT_CUR) notifyPageChanged()
                    return@launch
                }
                val chapterLayout = layoutEngine.layout(chapterIndex, text)

                var actualPage = pageInChapter

                // 字号变化后，根据字符偏移修正页码（保持阅读内容位置不变）
                if (slotIdx == SLOT_CUR && pendingStartCharOffset >= 0) {
                    val correctedPage = chapterLayout.pages.indexOfFirst { page ->
                        pendingStartCharOffset >= page.startCharOffset &&
                                pendingStartCharOffset < page.endCharOffset
                    }
                    if (correctedPage >= 0) {
                        actualPage = correctedPage
                        slot.pageIndex = correctedPage
                        Log.d(TAG, "Font-size correction: charOffset=$pendingStartCharOffset -> page $correctedPage")
                    }
                    pendingStartCharOffset = -1  // 消费一次
                }
                if (slotIdx == SLOT_PREV && actualPage == 0 && chapterIndex < currentChapterIndex) {
                    actualPage = chapterLayout.totalPages - 1
                    slot.pageIndex = actualPage
                }

                if (actualPage < 0 || actualPage >= chapterLayout.totalPages) {
                    val clampedPage = actualPage.coerceIn(0, chapterLayout.totalPages - 1)
                    Log.w(TAG, "Page clamped: slot=$slotIdx ch=$chapterIndex pg=$actualPage->$clampedPage")
                    slot.pageIndex = clampedPage
                    loadSlot(slotIdx, chapterIndex, clampedPage)
                    return@launch
                }

                // 设置页面文本内容
                val pageLayout = chapterLayout.pages[actualPage]
                val highlights = highlightProvider?.invoke(chapterIndex) ?: emptyList()
                slot.contentView.setPageContent(text, pageLayout.startCharOffset, pageLayout.endCharOffset, highlights)

                if (slot.chapterIndex == chapterIndex && slot.pageIndex == actualPage) {
                    slot.globalPageIndex = layoutEngine.localToGlobal(chapterIndex, actualPage)
                    slot.isLoaded = true
                    Log.d(TAG, "Slot $slotIdx loaded: ch=$chapterIndex pg=$actualPage")

                    if (slotIdx == SLOT_CUR) {
                        notifyPageChanged()
                        val (prevCh, prevPg) = resolvePrevPage()
                        if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
                        val (nextCh, nextPg) = resolveNextPage()
                        if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)
                    }
                } else {
                    slot.contentView.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load slot $slotIdx ch=$chapterIndex", e)
                slot.isLoaded = false
                if (slotIdx == SLOT_CUR) notifyPageChanged()
            }
        }
        loadingJob = thisJob
    }

    /**
     * 刷新当前槽位的高亮（笔记/书签变化后调用）。
     */
    fun refreshCurrentHighlights() {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return
        val cl = layoutEngine.getChapterLayout(cur.chapterIndex) ?: return
        val text = contentProvider?.let { kotlinx.coroutines.runBlocking(Dispatchers.IO) { it(cur.chapterIndex) } } ?: return
        val pageLayout = cl.pages.getOrNull(cur.pageIndex) ?: return
        val highlights = highlightProvider?.invoke(cur.chapterIndex) ?: emptyList()
        cur.contentView.setPageContent(text, pageLayout.startCharOffset, pageLayout.endCharOffset, highlights)
    }

    /**
     * 前进翻页后，传送带前移。
     */
    fun shiftForward() {
        val nextSlot = slots[SLOT_NEXT]
        if (!nextSlot.isLoaded) {
            Log.w(TAG, "shiftForward blocked: NEXT slot not loaded")
            val (nextCh, nextPg) = resolveNextPage()
            if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)
            notifyPageChanged()
            return
        }

        recycleSlot(SLOT_PREV)
        moveSlot(SLOT_CUR, SLOT_PREV)
        moveSlot(SLOT_NEXT, SLOT_CUR)

        val curSlot = slots[SLOT_CUR]
        currentChapterIndex = curSlot.chapterIndex
        currentGlobalPage = curSlot.globalPageIndex

        val (nextCh, nextPg) = resolveNextPage()
        if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)

        notifyPageChanged()
    }

    /**
     * 后退翻页后，传送带后移。
     */
    fun shiftBackward() {
        val prevSlot = slots[SLOT_PREV]
        if (!prevSlot.isLoaded) {
            Log.w(TAG, "shiftBackward blocked: PREV slot not loaded")
            val (prevCh, prevPg) = resolvePrevPage()
            if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
            notifyPageChanged()
            return
        }

        recycleSlot(SLOT_NEXT)
        moveSlot(SLOT_CUR, SLOT_NEXT)
        moveSlot(SLOT_PREV, SLOT_CUR)

        val curSlot = slots[SLOT_CUR]
        currentChapterIndex = curSlot.chapterIndex
        currentGlobalPage = curSlot.globalPageIndex

        val (prevCh, prevPg) = resolvePrevPage()
        if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)

        notifyPageChanged()
    }

    /**
     * 跳转到指定章节的指定页。
     */
    fun jumpTo(chapterIndex: Int, pageInChapter: Int) {
        for (i in 0..2) recycleSlot(i)

        currentChapterIndex = chapterIndex
        currentGlobalPage = layoutEngine.localToGlobal(chapterIndex, pageInChapter)

        loadSlot(SLOT_CUR, chapterIndex, pageInChapter)

        val (prevCh, prevPg) = resolvePrevPage()
        if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)

        val (nextCh, nextPg) = resolveNextPage()
        if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)

        val chapterLayout = layoutEngine.getChapterLayout(chapterIndex)
        val chapterTotal = chapterLayout?.totalPages ?: 0
        onPageChangedCallback?.invoke(currentGlobalPage, chapterIndex, pageInChapter, chapterTotal)
    }

    // ── 内部方法 ──

    private fun resolveNextPage(): Pair<Int, Int> {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return -1 to -1

        val ci = cur.chapterIndex
        val pi = cur.pageIndex

        val cl = layoutEngine.getChapterLayout(ci)
        if (cl != null && pi + 1 < cl.totalPages) {
            return ci to pi + 1
        }
        val nextCh = ci + 1
        if (nextCh < chapterCount) {
            return nextCh to 0
        }
        return -1 to -1
    }

    private fun resolvePrevPage(): Pair<Int, Int> {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return -1 to -1

        val ci = cur.chapterIndex
        val pi = cur.pageIndex

        if (pi - 1 >= 0) {
            return ci to pi - 1
        }
        val prevCh = ci - 1
        if (prevCh >= 0) {
            val cl = layoutEngine.getChapterLayout(prevCh)
            if (cl != null) {
                return prevCh to cl.totalPages - 1
            }
            return prevCh to 0
        }
        return -1 to -1
    }

    private fun moveSlot(from: Int, to: Int) {
        val fromSlot = slots[from]
        val toSlot = slots[to]

        toSlot.contentView.clear()

        toSlot.chapterIndex = fromSlot.chapterIndex
        toSlot.pageIndex = fromSlot.pageIndex
        toSlot.globalPageIndex = fromSlot.globalPageIndex
        toSlot.isLoaded = fromSlot.isLoaded
        // 复制文本内容（TextView 不能直接转移引用）
        toSlot.contentView.textView.text = fromSlot.contentView.textView.text

        fromSlot.chapterIndex = -1
        fromSlot.pageIndex = -1
        fromSlot.globalPageIndex = -1
        fromSlot.isLoaded = false
        fromSlot.contentView.clear()
    }

    private fun recycleSlot(slotIdx: Int) {
        val slot = slots[slotIdx]
        slot.chapterIndex = -1
        slot.pageIndex = -1
        slot.globalPageIndex = -1
        slot.isLoaded = false
        slot.contentView.clear()
    }

    private fun notifyPageChanged() {
        val cur = slots[SLOT_CUR]
        val chapterLayout = layoutEngine.getChapterLayout(cur.chapterIndex)
        val chapterTotal = chapterLayout?.totalPages ?: 0
        onPageChangedCallback?.invoke(cur.globalPageIndex, cur.chapterIndex, cur.pageIndex, chapterTotal)
    }

    fun getCurSlot(): SlotState = slots[SLOT_CUR]
    fun getPrevSlot(): SlotState = slots[SLOT_PREV]
    fun getNextSlot(): SlotState = slots[SLOT_NEXT]

    fun destroy() {
        for (i in 0..2) recycleSlot(i)
    }
}

package com.ebook.reader.ui.reader.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 3 槽位页级 conveyor belt 管理器。
 *
 * 三个槽位：PREV(0)、CUR(1)、NEXT(2)
 * 槽位粒度是*页*，不是章。跨章翻页只是下一页刚好在下一章。
 */
class PageSlotManager(
    private val layoutEngine: PageLayoutEngine,
    private val renderer: PageRenderer,
    private val prevView: PageSurfaceView,
    private val curView: PageSurfaceView,
    private val nextView: PageSurfaceView
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

    /** 文本内容提供者：根据章节索引返回纯文本 */
    var contentProvider: (suspend (Int) -> String?)? = null

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
     * 每次调用都强制清空旧槽位，确保主题/字体变更时重渲染。
     */
    fun initialize(startChapter: Int, startPageInChapter: Int) {
        // 🔥 关键：先清空所有槽位的 isLoaded 标志和旧 Bitmap
        // 否则 loadSlot 的 early-return（same chapter+page+isLoaded）会阻止重渲染
        for (i in 0..2) {
            slots[i].surfaceView.getPageBitmap()?.let { renderer.releaseBitmap(it) }
            slots[i].chapterIndex = -1
            slots[i].pageIndex = -1
            slots[i].globalPageIndex = -1
            slots[i].isLoaded = false
            slots[i].surfaceView.setPageBitmap(null)
        }

        currentChapterIndex = startChapter
        currentGlobalPage = layoutEngine.localToGlobal(startChapter, startPageInChapter)

        // 加载 CUR 槽位（现在 isLoaded=false，不会 early-return）
        loadSlot(SLOT_CUR, currentChapterIndex, startPageInChapter)

        // PREV / NEXT 由 CUR 加载完成后的回调自动预加载
    }

    /**
     * 加载一个槽位。
     */
    fun loadSlot(slotIdx: Int, chapterIndex: Int, pageInChapter: Int) {
        if (chapterIndex < 0 || chapterIndex >= chapterCount) return
        if (pageInChapter < 0) return

        val slot = slots[slotIdx]
        if (slot.chapterIndex == chapterIndex && slot.pageIndex == pageInChapter && slot.isLoaded) {
            return // 已加载，跳过
        }

        // 释放旧 Bitmap
        slot.surfaceView.getPageBitmap()?.let { renderer.releaseBitmap(it) }

        slot.chapterIndex = chapterIndex
        slot.pageIndex = pageInChapter
        slot.isLoaded = false

        // 先显示空内容（背景色占位）
        slot.surfaceView.setPageBitmap(null)

        // 异步加载（不 cancel 已有 job，用独立协程避免互相干扰）
        val thisJob = scope.launch {
            try {
                val text = contentProvider?.invoke(chapterIndex)
                if (text.isNullOrEmpty()) {
                    Log.w(TAG, "Empty text for slot $slotIdx ch=$chapterIndex")
                    // 🔥 不标记 isLoaded，避免空白 bitmap 被当作有效页
                    slot.isLoaded = false
                    if (slotIdx == SLOT_CUR) notifyPageChanged()
                    return@launch
                }
                val chapterLayout = layoutEngine.layout(chapterIndex, text)

                // 🔥 边界检查：目标页必须存在
                if (pageInChapter < 0 || pageInChapter >= chapterLayout.totalPages) {
                    Log.w(TAG, "Page out of bounds: slot=$slotIdx ch=$chapterIndex pg=$pageInChapter total=${chapterLayout.totalPages}")
                    slot.isLoaded = false
                    if (slotIdx == SLOT_CUR) notifyPageChanged()
                    return@launch
                }

                val bitmap = renderer.renderPage(chapterLayout, pageInChapter)

                // 确认该槽位没有被重新加载覆盖
                if (slot.chapterIndex == chapterIndex && slot.pageIndex == pageInChapter) {
                    slot.surfaceView.setPageBitmap(bitmap)
                    slot.globalPageIndex = layoutEngine.localToGlobal(chapterIndex, pageInChapter)
                    slot.isLoaded = true
                    Log.d(TAG, "Slot $slotIdx loaded: ch=$chapterIndex pg=$pageInChapter")

                    // 如果是当前槽位，通知页面变化（关键：初始加载时触发 isLoading=false）
                    if (slotIdx == SLOT_CUR) {
                        notifyPageChanged()
                        // CUR 加载完成后预加载 PREV/NEXT（初始化时 resolvePrev/Next 因 cur 未加载而跳过）
                        val (prevCh, prevPg) = resolvePrevPage()
                        if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
                        val (nextCh, nextPg) = resolveNextPage()
                        if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)
                    }
                } else {
                    // 槽位已过期，回收 Bitmap
                    renderer.releaseBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load slot $slotIdx ch=$chapterIndex pg=$pageInChapter", e)
                slot.isLoaded = false
                // 加载失败也通知页面变化，避免 loading 卡死
                if (slotIdx == SLOT_CUR) notifyPageChanged()
            }
        }
        // 仅跟踪最新的加载协程（用于 destroy 等场景）
        loadingJob = thisJob
    }

    /**
     * 前进翻页后，传送带前移。
     * CUR → PREV, NEXT → CUR, 加载新 NEXT。
     */
    fun shiftForward() {
        val nextSlot = slots[SLOT_NEXT]
        // 🔥 防护：NEXT 必须有有效 bitmap 才能转移，否则回退到当前页
        if (!nextSlot.isLoaded || nextSlot.surfaceView.getPageBitmap() == null) {
            Log.w(TAG, "shiftForward blocked: NEXT slot not loaded")
            // 尝试重新加载 NEXT，当前页保持不变
            val (nextCh, nextPg) = resolveNextPage()
            if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)
            notifyPageChanged()
            return
        }

        // PREV 回收
        recycleSlot(SLOT_PREV)

        // CUR → PREV
        moveSlot(SLOT_CUR, SLOT_PREV)

        // NEXT → CUR
        moveSlot(SLOT_NEXT, SLOT_CUR)

        // 更新当前页码
        val curSlot = slots[SLOT_CUR]
        currentChapterIndex = curSlot.chapterIndex
        currentGlobalPage = curSlot.globalPageIndex

        // 加载新 NEXT
        val (nextCh, nextPg) = resolveNextPage()
        if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)

        // 通知回调
        notifyPageChanged()
    }

    /**
     * 后退翻页后，传送带后移。
     * NEXT 回收, CUR → NEXT, PREV → CUR, 加载新 PREV。
     */
    fun shiftBackward() {
        val prevSlot = slots[SLOT_PREV]
        // 🔥 防护：PREV 必须有有效 bitmap 才能转移，否则回退到当前页
        if (!prevSlot.isLoaded || prevSlot.surfaceView.getPageBitmap() == null) {
            Log.w(TAG, "shiftBackward blocked: PREV slot not loaded")
            // 尝试重新加载 PREV，当前页保持不变
            val (prevCh, prevPg) = resolvePrevPage()
            if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
            notifyPageChanged()
            return
        }

        // NEXT 回收
        recycleSlot(SLOT_NEXT)

        // CUR → NEXT
        moveSlot(SLOT_CUR, SLOT_NEXT)

        // PREV → CUR
        moveSlot(SLOT_PREV, SLOT_CUR)

        // 更新当前页码
        val curSlot = slots[SLOT_CUR]
        currentChapterIndex = curSlot.chapterIndex
        currentGlobalPage = curSlot.globalPageIndex

        // 加载新 PREV
        val (prevCh, prevPg) = resolvePrevPage()
        if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)

        // 通知回调
        notifyPageChanged()
    }

    /**
     * 跳转到指定章节的指定页。
     */
    fun jumpTo(chapterIndex: Int, pageInChapter: Int) {
        // 回收所有槽位的旧 Bitmap
        for (i in 0..2) recycleSlot(i)

        currentChapterIndex = chapterIndex
        currentGlobalPage = layoutEngine.localToGlobal(chapterIndex, pageInChapter)

        // 重新加载三个槽位
        loadSlot(SLOT_CUR, chapterIndex, pageInChapter)

        val (prevCh, prevPg) = resolvePrevPage()
        if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)

        val (nextCh, nextPg) = resolveNextPage()
        if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)

        // 立即通知回调（不等 bitmap 加载完成）
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
        // 下一章第一页
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
        // 上一章最后一页
        val prevCh = ci - 1
        if (prevCh >= 0) {
            val cl = layoutEngine.getChapterLayout(prevCh)
            if (cl != null) {
                return prevCh to cl.totalPages - 1
            }
            // 上一章未布局，暂无法确定页数
            return prevCh to 0
        }
        return -1 to -1
    }

    private fun moveSlot(from: Int, to: Int) {
        val fromSlot = slots[from]
        val toSlot = slots[to]

        // 回收目标槽位旧 Bitmap
        toSlot.surfaceView.getPageBitmap()?.let { renderer.releaseBitmap(it) }

        // 转移引用（不复制 Bitmap）
        toSlot.chapterIndex = fromSlot.chapterIndex
        toSlot.pageIndex = fromSlot.pageIndex
        toSlot.globalPageIndex = fromSlot.globalPageIndex
        toSlot.isLoaded = fromSlot.isLoaded
        toSlot.surfaceView.setPageBitmap(fromSlot.surfaceView.getPageBitmap())

        // 清空源槽位
        fromSlot.chapterIndex = -1
        fromSlot.pageIndex = -1
        fromSlot.globalPageIndex = -1
        fromSlot.isLoaded = false
        fromSlot.surfaceView.setPageBitmap(null)
    }

    private fun recycleSlot(slotIdx: Int) {
        val slot = slots[slotIdx]
        slot.surfaceView.getPageBitmap()?.let { renderer.releaseBitmap(it) }
        slot.chapterIndex = -1
        slot.pageIndex = -1
        slot.globalPageIndex = -1
        slot.isLoaded = false
        slot.surfaceView.setPageBitmap(null)
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

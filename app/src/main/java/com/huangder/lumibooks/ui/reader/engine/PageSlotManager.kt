package com.huangder.lumibooks.ui.reader.engine

import android.util.Log
import com.huangder.lumibooks.ui.reader.pageIndexForChapterFraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

/**
 * 3 槽位页级 conveyor belt 管理器。
 *
 * 三个槽位：PREV(0)、CUR(1)、NEXT(2)。
 * 槽位粒度是「页」；双页对开模式下是「跨页单元」：
 * 每个槽位包含主页面（左页，或章首单独右页）+ 可选右半页。
 * 章内配对规则：章首第 1 页单独居右；后续按 (2,3)、(4,5)… 成对，
 * 即 0-based 奇数索引居左、偶数索引居右，跨页不跨章。
 */
class PageSlotManager(
    private val layoutEngine: PageLayoutEngine,
    private val prevView: PageContentView,
    private val curView: PageContentView,
    private val nextView: PageContentView,
    private val prevRightView: PageContentView,
    private val curRightView: PageContentView,
    private val nextRightView: PageContentView,
    private val spreadEnabled: () -> Boolean = { false }
) {
    internal enum class CurrentSlotLoadResult {
        LOADED,
        EMPTY,
        FAILED,
        CANCELLED
    }

    companion object {
        const val SLOT_PREV = 0
        const val SLOT_CUR = 1
        const val SLOT_NEXT = 2
        private const val TAG = "PageSlotManager"
    }

    private val slots = arrayOf(
        SlotState(-1, -1, -1, false, prevView, rightContentView = prevRightView),
        SlotState(-1, -1, -1, false, curView, rightContentView = curRightView),
        SlotState(-1, -1, -1, false, nextView, rightContentView = nextRightView)
    )

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main)
    private val slotJobs = arrayOfNulls<Job>(3)
    private val requestTokens = LongArray(3)
    private var chapterCount: Int = 0
    private val chapterTextCache = object : LinkedHashMap<Int, CharSequence>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CharSequence>?): Boolean =
            size > 4
    }

    /** 字号/尺寸/模式变化时暂存当前页的字符起始偏移，供 loadSlot 搜索修正后的页码 */
    var pendingStartCharOffset: Int = -1

    /** 目标章节尚未分页时暂存章内比例，待 CUR 槽布局完成后换算实际页码。 */
    internal var pendingStartPageFraction: Float? = null

    /** 文本内容提供者：根据章节索引返回文本 */
    var contentProvider: (suspend (Int) -> CharSequence?)? = null

    /** 高亮数据提供者：根据章节索引返回 (start, end, color) 列表 */
    var highlightProvider: ((Int) -> List<Triple<Int, Int, Int>>)? = null

    /** 当前全局页码（主页面，双页时取左页或单独右页） */
    var currentGlobalPage: Int = 0
        private set

    /** 当前章节索引 */
    var currentChapterIndex: Int = 0
        private set

    /** 用户翻页回调（主页面） */
    var onPageChangedCallback: ((globalPage: Int, chapterIdx: Int, pageInChapter: Int, chapterTotal: Int) -> Unit)? = null

    /** 双页模式右半页回调 */
    var onSpreadPageChangedCallback: ((rightGlobalPage: Int, rightChapterIdx: Int, rightPageInChapter: Int) -> Unit)? = null

    internal var onJumpFinishedCallback: ((generation: Long, result: CurrentSlotLoadResult) -> Unit)? = null
    private var activeJumpGeneration: Long? = null

    fun setChapterCount(count: Int) {
        chapterCount = count
    }

    /**
     * 初始化：加载起始页到 CUR 槽位，预加载 PREV 和 NEXT。
     */
    fun initialize(startChapter: Int, startPageInChapter: Int) {
        cancelActiveJump()
        for (i in 0..2) {
            recycleSlot(i)
        }

        currentChapterIndex = startChapter
        currentGlobalPage = layoutEngine.localToGlobal(startChapter, startPageInChapter)

        loadSlot(SLOT_CUR, currentChapterIndex, startPageInChapter)
    }

    /**
     * 加载一个槽位（单页模式加载单页，双页模式按配对规则加载跨页单元）。
     */
    fun loadSlot(slotIdx: Int, chapterIndex: Int, pageInChapter: Int) {
        if (chapterIndex < 0 || chapterIndex >= chapterCount) return
        if (pageInChapter < 0) return

        val slot = slots[slotIdx]
        if (!spreadEnabled() &&
            slot.chapterIndex == chapterIndex && slot.pageIndex == pageInChapter && slot.isLoaded
        ) {
            return
        }

        invalidateRequest(slotIdx)
        val requestToken = requestTokens[slotIdx]

        slot.chapterIndex = chapterIndex
        slot.pageIndex = pageInChapter
        slot.isLoaded = false
        slot.rightChapterIndex = -1
        slot.rightPageIndex = -1
        slot.rightGlobalPageIndex = -1
        slot.rightIsLoaded = false
        slot.primaryIsRight = false
        slot.contentView.clear()
        slot.rightContentView?.clear()

        val cachedText = chapterTextCache[chapterIndex]
        val cachedLayout = layoutEngine.getChapterLayout(chapterIndex)
        if (!cachedText.isNullOrEmpty() && cachedLayout != null) {
            populateSlot(slotIdx, chapterIndex, pageInChapter, requestToken, cachedText, cachedLayout)
            return
        }

        val thisJob = scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { contentProvider?.invoke(chapterIndex) }
                if (!isCurrentRequest(slotIdx, requestToken)) return@launch
                if (text.isNullOrEmpty()) {
                    Log.w(TAG, "Empty text for slot $slotIdx ch=$chapterIndex")
                    slot.isLoaded = false
                    if (slotIdx == SLOT_CUR) {
                        finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.EMPTY)
                    }
                    return@launch
                }
                chapterTextCache[chapterIndex] = text
                val chapterLayout = layoutEngine.layout(chapterIndex, text)
                if (!isCurrentRequest(slotIdx, requestToken)) return@launch
                populateSlot(slotIdx, chapterIndex, pageInChapter, requestToken, text, chapterLayout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load slot $slotIdx ch=$chapterIndex", e)
                if (isCurrentRequest(slotIdx, requestToken)) {
                    slot.isLoaded = false
                    if (slotIdx == SLOT_CUR) {
                        finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.FAILED)
                    }
                }
            } finally {
                if (isCurrentRequest(slotIdx, requestToken)) {
                    slotJobs[slotIdx] = null
                }
            }
        }
        slotJobs[slotIdx] = thisJob
    }

    private fun populateSlot(
        slotIdx: Int,
        chapterIndex: Int,
        requestedPage: Int,
        requestToken: Long,
        text: CharSequence,
        chapterLayout: ChapterLayout
    ) {
        val slot = slots[slotIdx]
        if (!isCurrentRequest(slotIdx, requestToken)) return
        if (chapterLayout.totalPages <= 0) {
            slot.isLoaded = false
            if (slotIdx == SLOT_CUR) {
                finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.EMPTY)
            }
            return
        }

        var actualPage = requestedPage
        if (slotIdx == SLOT_CUR && pendingStartCharOffset >= 0) {
            val correctedPage = chapterLayout.pages.indexOfFirst { page ->
                pendingStartCharOffset >= page.startCharOffset &&
                    pendingStartCharOffset < page.endCharOffset
            }
            if (correctedPage >= 0) {
                actualPage = correctedPage
                slot.pageIndex = correctedPage
            }
            pendingStartCharOffset = -1
        } else if (slotIdx == SLOT_CUR && pendingStartPageFraction != null) {
            actualPage = pageIndexForChapterFraction(
                pendingStartPageFraction ?: 0f,
                chapterLayout.totalPages
            )
            slot.pageIndex = actualPage
            pendingStartPageFraction = null
        }
        if (slotIdx == SLOT_PREV && actualPage == 0 && chapterIndex < currentChapterIndex) {
            actualPage = if (spreadEnabled()) {
                lastSpreadPrimary(chapterLayout.totalPages)
            } else {
                chapterLayout.totalPages - 1
            }
            slot.pageIndex = actualPage
        }
        if (actualPage !in 0 until chapterLayout.totalPages) {
            actualPage = actualPage.coerceIn(0, chapterLayout.totalPages - 1)
            slot.pageIndex = actualPage
        }

        val highlights = highlightProvider?.invoke(chapterIndex) ?: emptyList()
        if (!spreadEnabled()) {
            val pageLayout = chapterLayout.pages[actualPage]
            slot.contentView.setPageContent(
                text,
                pageLayout.startCharOffset,
                pageLayout.endCharOffset,
                highlights,
                pageLayout.verticalGeometry
            )
            slot.rightContentView?.clear()
            slot.rightChapterIndex = -1
            slot.rightPageIndex = -1
            slot.rightGlobalPageIndex = -1
            slot.rightIsLoaded = false
            slot.primaryIsRight = false
        } else {
            val (leftPage, rightPage) = spreadFor(actualPage, chapterLayout.totalPages)
            val primary = (leftPage ?: rightPage) ?: 0
            slot.pageIndex = primary
            slot.primaryIsRight = leftPage == null
            if (leftPage != null) {
                val pageLayout = chapterLayout.pages[leftPage]
                slot.contentView.setPageContent(
                    text,
                    pageLayout.startCharOffset,
                    pageLayout.endCharOffset,
                    highlights,
                    pageLayout.verticalGeometry
                )
            } else {
                slot.contentView.clear()
            }
            val rightView = slot.rightContentView
            if (rightPage != null && rightView != null) {
                val pageLayout = chapterLayout.pages[rightPage]
                rightView.setPageContent(
                    text,
                    pageLayout.startCharOffset,
                    pageLayout.endCharOffset,
                    highlights,
                    pageLayout.verticalGeometry
                )
                slot.rightChapterIndex = chapterIndex
                slot.rightPageIndex = rightPage
                slot.rightGlobalPageIndex = layoutEngine.localToGlobal(chapterIndex, rightPage)
                slot.rightIsLoaded = true
            } else {
                slot.rightContentView?.clear()
                slot.rightChapterIndex = -1
                slot.rightPageIndex = -1
                slot.rightGlobalPageIndex = -1
                slot.rightIsLoaded = false
            }
        }
        if (!isCurrentRequest(slotIdx, requestToken) ||
            slot.chapterIndex != chapterIndex
        ) return
        if (!spreadEnabled() && slot.pageIndex != actualPage) return

        slot.globalPageIndex = layoutEngine.localToGlobal(chapterIndex, slot.pageIndex)
        slot.isLoaded = true
        if (slotIdx == SLOT_CUR) {
            val (prevCh, prevPg) = resolvePrevPage()
            if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
            val (nextCh, nextPg) = resolveNextPage()
            if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)
            eagerPreloadUpcoming(chapterIndex)
            finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.LOADED)
        }
    }

    /**
     * 章内跨页配对：返回 (leftPage, rightPage)，均为章内 0-based 页码。
     * - 第 0 页（章首）单独居右：left=null, right=0
     * - 奇数 0-based（1-based 偶数，左页）：(t, t+1)，t+1 越界则为单独左页
     * - 偶数 0-based >0（1-based 奇数，右页）：(t-1, t)
     */
    private fun spreadFor(target: Int, totalPages: Int): Pair<Int?, Int?> {
        val t = target.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        return when {
            t == 0 -> null to 0
            t % 2 == 1 -> t to (if (t + 1 < totalPages) t + 1 else null)
            else -> (t - 1) to t
        }
    }

    /** 章节最后一跨页的主页面（左页或单独右页） */
    private fun lastSpreadPrimary(totalPages: Int): Int {
        if (totalPages <= 0) return 0
        if (totalPages == 1) return 0
        val (left, right) = spreadFor(totalPages - 1, totalPages)
        return left ?: right ?: 0
    }

    /**
     * 后台静默预加载当前章节之后的 2 章 layout，使翻章时几乎无等待。
     * 低优先级 fire-and-forget，不阻塞主流程，失败静默忽略。
     */
    private fun eagerPreloadUpcoming(currentChapter: Int) {
        val provider = contentProvider ?: return
        for (ahead in 1..2) {
            val target = currentChapter + ahead
            if (target >= chapterCount) break
            if (layoutEngine.getChapterLayout(target) != null) continue
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) { provider(target) }
                        ?.takeUnless { it.isEmpty() } ?: return@launch
                    chapterTextCache[target] = text
                    layoutEngine.layout(target, text)
                    Log.d(TAG, "EagerPreload: chapter $target layout cached")
                } catch (_: Exception) { }
            }
        }
    }

    /** 退出阅读时调用：预跑当前章节的 layout 存入 layoutCache。 */
    fun preloadCurrentChapter() {
        val curChapter = currentChapterIndex
        val provider = contentProvider ?: return
        if (layoutEngine.getChapterLayout(curChapter) != null) return
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { provider(curChapter) }
                    ?.takeUnless { it.isEmpty() } ?: return@launch
                layoutEngine.layout(curChapter, text)
                Log.d(TAG, "preloadCurrentChapter: chapter $curChapter cached for re-entry")
            } catch (_: Exception) { }
        }
    }

    /** 刷新当前页内容（简繁转换等设置变更后调用）。 */
    fun refreshCurrentPage() {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return
        val cl = layoutEngine.getChapterLayout(cur.chapterIndex) ?: return
        val text = contentProvider?.let { kotlinx.coroutines.runBlocking(Dispatchers.IO) { it(cur.chapterIndex) } } ?: return
        val highlights = highlightProvider?.invoke(cur.chapterIndex) ?: emptyList()
        if (spreadEnabled()) {
            val leftPage = if (cur.primaryIsRight) null else cur.pageIndex
            val rightPage = if (cur.rightIsLoaded) cur.rightPageIndex else null
            if (leftPage != null) {
                val pageLayout = cl.pages.getOrNull(leftPage)
                if (pageLayout != null) {
                    cur.contentView.setPageContent(
                        text,
                        pageLayout.startCharOffset,
                        pageLayout.endCharOffset,
                        highlights,
                        pageLayout.verticalGeometry
                    )
                }
            } else {
                cur.contentView.clear()
            }
            if (rightPage != null) {
                val pageLayout = cl.pages.getOrNull(rightPage)
                if (pageLayout != null) {
                    cur.rightContentView?.setPageContent(
                        text,
                        pageLayout.startCharOffset,
                        pageLayout.endCharOffset,
                        highlights,
                        pageLayout.verticalGeometry
                    )
                }
            } else {
                cur.rightContentView?.clear()
            }
        } else {
            val pageLayout = cl.pages.getOrNull(cur.pageIndex) ?: return
            cur.contentView.setPageContent(
                text,
                pageLayout.startCharOffset,
                pageLayout.endCharOffset,
                highlights,
                pageLayout.verticalGeometry
            )
        }
    }

    /** 刷新当前槽位的高亮（笔记/书签变化后调用）。 */
    fun refreshAllHighlights() {
        refreshCurrentHighlights()
        for (slotIdx in intArrayOf(SLOT_PREV, SLOT_NEXT)) {
            refreshAdjacentHighlights(slotIdx)
        }
    }

    private fun refreshAdjacentHighlights(slotIdx: Int) {
        val slot = slots[slotIdx]
        if (!slot.isLoaded) return
        val ci = slot.chapterIndex
        val pi = slot.pageIndex
        val cl = layoutEngine.getChapterLayout(ci) ?: return
        val requestToken = requestTokens[slotIdx]

        scope.launch {
            val text = withContext(Dispatchers.IO) { contentProvider?.invoke(ci) } ?: return@launch
            val highlights = highlightProvider?.invoke(ci) ?: emptyList()

            withContext(Dispatchers.Main) {
                val currentSlot = slots[slotIdx]
                if (isCurrentRequest(slotIdx, requestToken) &&
                    currentSlot.chapterIndex == ci && currentSlot.pageIndex == pi && currentSlot.isLoaded
                ) {
                    if (spreadEnabled()) {
                        val leftPage = if (currentSlot.primaryIsRight) null else pi
                        val rightPage = if (currentSlot.rightIsLoaded) currentSlot.rightPageIndex else null
                        if (leftPage != null) {
                            val pageLayout = cl.pages.getOrNull(leftPage)
                            if (pageLayout != null) {
                                currentSlot.contentView.setPageContent(text, pageLayout.startCharOffset, pageLayout.endCharOffset, highlights, pageLayout.verticalGeometry)
                            }
                        } else {
                            currentSlot.contentView.clear()
                        }
                        if (rightPage != null) {
                            val pageLayout = cl.pages.getOrNull(rightPage)
                            if (pageLayout != null) {
                                currentSlot.rightContentView?.setPageContent(text, pageLayout.startCharOffset, pageLayout.endCharOffset, highlights, pageLayout.verticalGeometry)
                            }
                        } else {
                            currentSlot.rightContentView?.clear()
                        }
                    } else {
                        val pageLayout = cl.pages.getOrNull(pi) ?: return@withContext
                        currentSlot.contentView.setPageContent(text, pageLayout.startCharOffset, pageLayout.endCharOffset, highlights, pageLayout.verticalGeometry)
                    }
                }
            }
        }
    }
    /** 寮哄埗閲嶆柊娴佺幇褰撳墠椤碉紙TTS 褰撳墠鍙ラ珮浜涘埛鏂扮敤锛?*/
    fun refreshCurrent() {
        val cur = slots[SLOT_CUR]
        if (cur.chapterIndex < 0 || cur.pageIndex < 0) return
        cur.isLoaded = false
        cur.rightIsLoaded = false
        loadSlot(SLOT_CUR, cur.chapterIndex, cur.pageIndex)
    }

    fun refreshCurrentHighlights() {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return
        val ci = cur.chapterIndex
        val pi = cur.pageIndex
        val cl = layoutEngine.getChapterLayout(ci) ?: return
        val requestToken = requestTokens[SLOT_CUR]

        scope.launch {
            val text = withContext(Dispatchers.IO) { contentProvider?.invoke(ci) } ?: return@launch
            val highlights = highlightProvider?.invoke(ci) ?: emptyList()

            withContext(Dispatchers.Main) {
                val currentCur = slots[SLOT_CUR]
                if (isCurrentRequest(SLOT_CUR, requestToken) &&
                    currentCur.chapterIndex == ci && currentCur.pageIndex == pi && currentCur.isLoaded
                ) {
                    if (spreadEnabled()) {
                        val leftPage = if (currentCur.primaryIsRight) null else pi
                        val rightPage = if (currentCur.rightIsLoaded) currentCur.rightPageIndex else null
                        if (leftPage != null) {
                            val pageLayout = cl.pages.getOrNull(leftPage)
                            if (pageLayout != null) {
                                currentCur.contentView.setPageContent(
                                    text,
                                    pageLayout.startCharOffset,
                                    pageLayout.endCharOffset,
                                    highlights,
                                    pageLayout.verticalGeometry
                                )
                            }
                        } else {
                            currentCur.contentView.clear()
                        }
                        if (rightPage != null) {
                            val pageLayout = cl.pages.getOrNull(rightPage)
                            if (pageLayout != null) {
                                currentCur.rightContentView?.setPageContent(
                                    text,
                                    pageLayout.startCharOffset,
                                    pageLayout.endCharOffset,
                                    highlights,
                                    pageLayout.verticalGeometry
                                )
                            }
                        } else {
                            currentCur.rightContentView?.clear()
                        }
                    } else {
                        val pageLayout = cl.pages.getOrNull(pi) ?: return@withContext
                        currentCur.contentView.setPageContent(
                            text,
                            pageLayout.startCharOffset,
                            pageLayout.endCharOffset,
                            highlights,
                            pageLayout.verticalGeometry
                        )
                    }
                }
            }
        }
    }

    /** 前进翻页后，传送带前移（双页模式整幅前进 2 页）。 */
    fun shiftForward() {
        val nextSlot = slots[SLOT_NEXT]
        if (!nextSlot.isLoaded) {
            Log.w(TAG, "shiftForward: NEXT not loaded, advancing anyway")
            val (nextCh, nextPg) = resolveNextPage()
            if (nextCh < 0 || nextPg < 0) {
                notifyPageChanged()
                return
            }
            recycleSlot(SLOT_PREV)
            moveSlot(SLOT_CUR, SLOT_PREV)
            val curSlot = slots[SLOT_CUR]
            curSlot.chapterIndex = nextCh
            curSlot.pageIndex = nextPg
            curSlot.globalPageIndex = layoutEngine.localToGlobal(nextCh, nextPg)
            curSlot.isLoaded = false
            curSlot.rightChapterIndex = -1
            curSlot.rightPageIndex = -1
            curSlot.rightGlobalPageIndex = -1
            curSlot.rightIsLoaded = false
            curSlot.primaryIsRight = false
            curSlot.contentView.clear()
            curSlot.rightContentView?.clear()
            currentChapterIndex = nextCh
            currentGlobalPage = curSlot.globalPageIndex
            loadSlot(SLOT_CUR, nextCh, nextPg)
            val (nnCh, nnPg) = resolveNextPage()
            if (nnCh >= 0 && nnPg >= 0) loadSlot(SLOT_NEXT, nnCh, nnPg)
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

    /** 后退翻页后，传送带后移（双页模式整幅后退 2 页）。 */
    fun shiftBackward() {
        val prevSlot = slots[SLOT_PREV]
        if (!prevSlot.isLoaded) {
            Log.w(TAG, "shiftBackward: PREV not loaded, advancing anyway")
            val (prevCh, prevPg) = resolvePrevPage()
            if (prevCh < 0 || prevPg < 0) {
                notifyPageChanged()
                return
            }
            recycleSlot(SLOT_NEXT)
            moveSlot(SLOT_CUR, SLOT_NEXT)
            val curSlot = slots[SLOT_CUR]
            curSlot.chapterIndex = prevCh
            curSlot.pageIndex = prevPg
            curSlot.globalPageIndex = layoutEngine.localToGlobal(prevCh, prevPg)
            curSlot.isLoaded = false
            curSlot.rightChapterIndex = -1
            curSlot.rightPageIndex = -1
            curSlot.rightGlobalPageIndex = -1
            curSlot.rightIsLoaded = false
            curSlot.primaryIsRight = false
            curSlot.contentView.clear()
            curSlot.rightContentView?.clear()
            currentChapterIndex = prevCh
            currentGlobalPage = curSlot.globalPageIndex
            loadSlot(SLOT_CUR, prevCh, prevPg)
            val (ppCh, ppPg) = resolvePrevPage()
            if (ppCh >= 0 && ppPg >= 0) loadSlot(SLOT_PREV, ppCh, ppPg)
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

    /** 跳转到指定章节的指定页（双页模式自动配对）。 */
    fun jumpTo(chapterIndex: Int, pageInChapter: Int) {
        jumpTo(chapterIndex, pageInChapter, generation = null)
    }

    internal fun jumpTo(chapterIndex: Int, pageInChapter: Int, generation: Long?) {
        cancelActiveJump()
        for (i in 0..2) recycleSlot(i)

        activeJumpGeneration = generation
        if (chapterIndex !in 0 until chapterCount || pageInChapter < 0) {
            finishInvalidJump()
            return
        }

        currentChapterIndex = chapterIndex
        currentGlobalPage = layoutEngine.localToGlobal(chapterIndex, pageInChapter)

        loadSlot(SLOT_CUR, chapterIndex, pageInChapter)
    }

    // ── 内部方法 ──

    private fun resolveNextPage(): Pair<Int, Int> {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return -1 to -1

        val ci = cur.chapterIndex
        val pi = cur.pageIndex

        val cl = layoutEngine.getChapterLayout(ci) ?: return -1 to -1
        if (spreadEnabled()) {
            val nextPrimary = if (pi == 0) 1 else pi + 2
            if (nextPrimary < cl.totalPages) {
                return ci to nextPrimary
            }
            val nextCh = ci + 1
            if (nextCh < chapterCount) {
                return nextCh to 0
            }
            return -1 to -1
        }
        return when {
            pi + 1 < cl.totalPages -> ci to pi + 1
            ci + 1 < chapterCount -> ci + 1 to 0
            else -> -1 to -1
        }
    }

    private fun resolvePrevPage(): Pair<Int, Int> {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return -1 to -1

        val ci = cur.chapterIndex
        val pi = cur.pageIndex

        if (spreadEnabled()) {
            if (pi > 0) {
                return ci to (pi - 2).coerceAtLeast(0)
            }
            val prevCh = ci - 1
            if (prevCh < 0) return -1 to -1
            val cl = layoutEngine.getChapterLayout(prevCh)
            if (cl != null && cl.totalPages > 0) {
                return prevCh to lastSpreadPrimary(cl.totalPages)
            }
            return prevCh to 0
        }
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
        invalidateRequest(from)
        invalidateRequest(to)
        val fromSlot = slots[from]
        val toSlot = slots[to]

        toSlot.chapterIndex = fromSlot.chapterIndex
        toSlot.pageIndex = fromSlot.pageIndex
        toSlot.globalPageIndex = fromSlot.globalPageIndex
        toSlot.isLoaded = fromSlot.isLoaded
        toSlot.rightChapterIndex = fromSlot.rightChapterIndex
        toSlot.rightPageIndex = fromSlot.rightPageIndex
        toSlot.rightGlobalPageIndex = fromSlot.rightGlobalPageIndex
        toSlot.rightIsLoaded = fromSlot.rightIsLoaded
        toSlot.primaryIsRight = fromSlot.primaryIsRight
        if (!renderSlotFromCache(toSlot)) {
            toSlot.contentView.syncText(
                textViewText = fromSlot.contentView.textView.text,
                justifiedText = fromSlot.contentView.getJustifiedText(),
                justifyLastLine = fromSlot.contentView.shouldJustifyLastLine(),
                chapterStartOffset = fromSlot.contentView.chapterStartOffset,
                verticalGeometry = fromSlot.contentView.getVerticalGeometry()
            )
            fromSlot.rightContentView?.let { fromRight ->
                toSlot.rightContentView?.syncText(
                    textViewText = fromRight.textView.text,
                    justifiedText = fromRight.getJustifiedText(),
                    justifyLastLine = fromRight.shouldJustifyLastLine(),
                    chapterStartOffset = fromRight.chapterStartOffset,
                    verticalGeometry = fromRight.getVerticalGeometry()
                )
            } ?: toSlot.rightContentView?.clear()
        }
        Log.d(TAG, "moveSlot $from→$to: invalidating ${toSlot.contentView}")
        toSlot.contentView.invalidate()
        toSlot.rightContentView?.invalidate()

        fromSlot.chapterIndex = -1
        fromSlot.pageIndex = -1
        fromSlot.globalPageIndex = -1
        fromSlot.isLoaded = false
        fromSlot.rightChapterIndex = -1
        fromSlot.rightPageIndex = -1
        fromSlot.rightGlobalPageIndex = -1
        fromSlot.rightIsLoaded = false
        fromSlot.primaryIsRight = false
        fromSlot.contentView.clear()
        fromSlot.rightContentView?.clear()
    }

    /**
     * TextView's selectable Editable copy does not reliably retain custom
     * CharacterStyle subclasses across slot rotation. Recreate the destination
     * page from the cached chapter source so saved highlight spans stay concrete.
     */
    private fun renderSlotFromCache(slot: SlotState): Boolean {
        if (!slot.isLoaded || slot.chapterIndex < 0 || slot.pageIndex < 0) return false
        val text = chapterTextCache[slot.chapterIndex] ?: return false
        val chapterLayout = layoutEngine.getChapterLayout(slot.chapterIndex) ?: return false
        val highlights = highlightProvider?.invoke(slot.chapterIndex) ?: emptyList()

        if (!spreadEnabled()) {
            val page = chapterLayout.pages.getOrNull(slot.pageIndex) ?: return false
            slot.contentView.setPageContent(
                text,
                page.startCharOffset,
                page.endCharOffset,
                highlights,
                page.verticalGeometry
            )
            slot.rightContentView?.clear()
            return true
        }

        if (slot.primaryIsRight) {
            slot.contentView.clear()
        } else {
            val leftPage = chapterLayout.pages.getOrNull(slot.pageIndex) ?: return false
            slot.contentView.setPageContent(
                text,
                leftPage.startCharOffset,
                leftPage.endCharOffset,
                highlights,
                leftPage.verticalGeometry
            )
        }

        if (slot.rightIsLoaded && slot.rightPageIndex >= 0) {
            val rightPage = chapterLayout.pages.getOrNull(slot.rightPageIndex) ?: return false
            val rightView = slot.rightContentView ?: return false
            rightView.setPageContent(
                text,
                rightPage.startCharOffset,
                rightPage.endCharOffset,
                highlights,
                rightPage.verticalGeometry
            )
        } else {
            slot.rightContentView?.clear()
        }
        return true
    }

    private fun recycleSlot(slotIdx: Int) {
        invalidateRequest(slotIdx)
        val slot = slots[slotIdx]
        slot.chapterIndex = -1
        slot.pageIndex = -1
        slot.globalPageIndex = -1
        slot.isLoaded = false
        slot.rightChapterIndex = -1
        slot.rightPageIndex = -1
        slot.rightGlobalPageIndex = -1
        slot.rightIsLoaded = false
        slot.primaryIsRight = false
        slot.contentView.clear()
        slot.rightContentView?.clear()
    }

    private fun invalidateRequest(slotIdx: Int) {
        requestTokens[slotIdx]++
        slotJobs[slotIdx]?.cancel()
        slotJobs[slotIdx] = null
    }

    private fun isCurrentRequest(slotIdx: Int, requestToken: Long): Boolean {
        return requestTokens[slotIdx] == requestToken
    }

    private fun finishCurrentSlotLoad(
        requestToken: Long,
        result: CurrentSlotLoadResult
    ) {
        if (!isCurrentRequest(SLOT_CUR, requestToken)) return
        val completedGeneration = activeJumpGeneration
        activeJumpGeneration = null
        pendingStartCharOffset = -1
        pendingStartPageFraction = null
        notifyPageChanged()
        if (completedGeneration != null) {
            onJumpFinishedCallback?.invoke(completedGeneration, result)
        }
    }

    private fun finishInvalidJump() {
        val completedGeneration = activeJumpGeneration
        activeJumpGeneration = null
        pendingStartCharOffset = -1
        pendingStartPageFraction = null
        if (completedGeneration != null) {
            onJumpFinishedCallback?.invoke(completedGeneration, CurrentSlotLoadResult.FAILED)
        }
    }

    private fun cancelActiveJump() {
        val cancelledGeneration = activeJumpGeneration
        activeJumpGeneration = null
        if (cancelledGeneration != null) {
            onJumpFinishedCallback?.invoke(cancelledGeneration, CurrentSlotLoadResult.CANCELLED)
        }
    }

    private fun notifyPageChanged() {
        val cur = slots[SLOT_CUR]
        val chapterLayout = layoutEngine.getChapterLayout(cur.chapterIndex)
        val chapterTotal = chapterLayout?.totalPages ?: 0
        onPageChangedCallback?.invoke(cur.globalPageIndex, cur.chapterIndex, cur.pageIndex, chapterTotal)
        if (cur.rightIsLoaded && cur.rightPageIndex >= 0) {
            onSpreadPageChangedCallback?.invoke(
                cur.rightGlobalPageIndex,
                cur.rightChapterIndex,
                cur.rightPageIndex
            )
        }
    }

    fun getCurSlot(): SlotState = slots[SLOT_CUR]
    fun getPrevSlot(): SlotState = slots[SLOT_PREV]
    fun getNextSlot(): SlotState = slots[SLOT_NEXT]

    /** 当前主内容视图：双页单独右页时返回右半页，否则返回左半页。 */
    fun getPrimaryContentView(): PageContentView =
        if (slots[SLOT_CUR].primaryIsRight) {
            slots[SLOT_CUR].rightContentView ?: slots[SLOT_CUR].contentView
        } else {
            slots[SLOT_CUR].contentView
        }

    fun getSlotForView(view: PageContentView): SlotState? =
        slots.firstOrNull { it.contentView === view || it.rightContentView === view }

    fun clearContentCache() {
        chapterTextCache.clear()
    }

    fun destroy() {
        cancelActiveJump()
        for (i in 0..2) recycleSlot(i)
        chapterTextCache.clear()
        scope.cancel()
    }
}

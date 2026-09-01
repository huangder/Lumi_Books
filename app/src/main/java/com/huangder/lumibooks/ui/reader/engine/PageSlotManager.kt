package com.huangder.lumibooks.ui.reader.engine

import android.util.Log
import com.huangder.lumibooks.ui.reader.pageIndexForChapterFraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

/**
 * 3 槽位页级 conveyor belt 管理器。
 *
 * 三个槽位：PREV(0)、CUR(1)、NEXT(2)。
 * 槽位粒度是「页」；双页对开模式下是「跨页单元」：
 * 每个槽位包含主页面（左页）+ 可选右半页。配对按整本书的连续页
 * 序列规划，因此章节末页可以和下一章首页组成同一跨页。
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
    private val chapterLoadJobs = mutableMapOf<Int, Deferred<ChapterMaterial?>>()
    private var refreshJob: Job? = null
    private var prefetchJob: Job? = null
    private var previousPrefetchJob: Job? = null
    private var nextPrefetchJob: Job? = null
    private val requestTokens = LongArray(3)
    private var chapterCount: Int = 0
    private val chapterTextCache = object : LinkedHashMap<Int, CharSequence>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CharSequence>?): Boolean =
            size > 4
    }
    /** Page counts retained independently of the LRU so cross-chapter parity stays stable. */
    private val knownPageCounts = mutableMapOf<Int, Int>()
    /** The chapter whose first page is the left anchor for the current run. */
    private var pairingAnchorChapter: Int = 0

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

    internal var onSlotReadyCallback: (() -> Unit)? = null

    internal var onJumpFinishedCallback: ((generation: Long, result: CurrentSlotLoadResult) -> Unit)? = null
    private var activeJumpGeneration: Long? = null

    private data class ChapterMaterial(
        val text: CharSequence,
        val layout: ChapterLayout
    )

    fun setChapterCount(count: Int) {
        chapterCount = count
        knownPageCounts.keys.removeIf { it !in 0 until count }
    }

    /**
     * 初始化：加载起始页到 CUR 槽位，预加载 PREV 和 NEXT。
     */
    fun initialize(startChapter: Int, startPageInChapter: Int) {
        cancelActiveJump()
        previousPrefetchJob?.cancel()
        previousPrefetchJob = null
        nextPrefetchJob?.cancel()
        nextPrefetchJob = null
        for (i in 0..2) {
            recycleSlot(i)
        }

        pairingAnchorChapter = startChapter.coerceIn(0, (chapterCount - 1).coerceAtLeast(0))
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
        slot.chapterTotalPages = 0
        slot.contentView.clear()
        slot.rightContentView?.clear()

        // Slot rotation asks for another page from the same already-laid-out
        // chapter on every turn. Populate that page in this MOVE dispatch so a
        // confirmed follow-up curl can keep the pointer stream instead of being
        // replayed after ACTION_UP on the next coroutine turn.
        val cachedText = chapterTextCache[chapterIndex]
        val cachedLayout = layoutEngine.getChapterLayout(chapterIndex)
        if (!spreadEnabled() && cachedText != null && cachedLayout != null) {
            rememberPageCount(ChapterMaterial(cachedText, cachedLayout))
            populateSlot(
                slotIdx,
                chapterIndex,
                pageInChapter,
                requestToken,
                cachedText,
                cachedLayout
            )
            return
        }

        val thisJob = scope.launch {
            try {
                val material = getOrStartChapterLoad(chapterIndex).await()
                if (!isCurrentRequest(slotIdx, requestToken)) return@launch
                if (material == null) {
                    Log.w(TAG, "Empty text for slot $slotIdx ch=$chapterIndex")
                    slot.isLoaded = false
                    if (slotIdx == SLOT_CUR) {
                        finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.EMPTY)
                    }
                    return@launch
                }
                if (!isCurrentRequest(slotIdx, requestToken)) return@launch
                rememberPageCount(material)
                if (spreadEnabled()) {
                    loadSpreadSlot(slotIdx, chapterIndex, pageInChapter, requestToken, material)
                } else {
                    populateSlot(
                        slotIdx,
                        chapterIndex,
                        pageInChapter,
                        requestToken,
                        material.text,
                        material.layout
                    )
                }
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

    /**
     * Chapter I/O and layout outlive an individual page slot request. Rotating the
     * three slots may cancel a stale UI write, but must not restart the same chapter
     * load while a rapid turn is waiting at the chapter boundary.
     */
    private fun getOrStartChapterLoad(chapterIndex: Int): Deferred<ChapterMaterial?> {
        chapterLoadJobs[chapterIndex]?.let { return it }

        val job = scope.async(start = CoroutineStart.LAZY) {
            try {
                val text = chapterTextCache[chapterIndex]
                    ?: withContext(Dispatchers.IO) { contentProvider?.invoke(chapterIndex) }
                        ?.takeUnless { it.isEmpty() }
                    ?: return@async null
                chapterTextCache[chapterIndex] = text
                val layout = layoutEngine.getChapterLayout(chapterIndex)
                    ?: layoutEngine.layout(chapterIndex, text)
                ChapterMaterial(text, layout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare chapter $chapterIndex", e)
                null
            }
        }
        chapterLoadJobs[chapterIndex] = job
        job.invokeOnCompletion {
            if (chapterLoadJobs[chapterIndex] === job) {
                chapterLoadJobs.remove(chapterIndex)
            }
        }
        job.start()
        return job
    }

    private fun rememberPageCount(material: ChapterMaterial) {
        knownPageCounts[material.layout.chapterIndex] = material.layout.totalPages
    }

    private fun pageCountsSnapshot(): List<Int> = List(chapterCount) { chapter ->
        knownPageCounts[chapter]
            ?: layoutEngine.getChapterLayout(chapter)?.totalPages
            ?: 0
    }

    private fun planSpread(target: PageLocation): SpreadTarget? {
        val anchor = if (target.chapterIndex < pairingAnchorChapter) {
            target.chapterIndex
        } else {
            pairingAnchorChapter
        }
        return ReaderSpreadPlanner.spreadFor(target, anchor, pageCountsSnapshot())
    }

    /** Loads every chapter needed by a spread before exposing the slot to animation. */
    private suspend fun loadSpreadSlot(
        slotIdx: Int,
        chapterIndex: Int,
        requestedPage: Int,
        requestToken: Long,
        primaryMaterial: ChapterMaterial
    ) {
        // Re-anchor the primary page after a relayout. The old page index is
        // only a hint; the character offset is stable across width/font changes.
        var resolvedPage = requestedPage
        if (slotIdx == SLOT_CUR) {
            if (pendingStartCharOffset >= 0) {
                primaryMaterial.layout.pages.indexOfFirst { page ->
                    pendingStartCharOffset >= page.startCharOffset &&
                        pendingStartCharOffset < page.endCharOffset
                }.takeIf { it >= 0 }?.let { resolvedPage = it }
                pendingStartCharOffset = -1
            } else if (pendingStartPageFraction != null) {
                resolvedPage = pageIndexForChapterFraction(
                    pendingStartPageFraction ?: 0f,
                    primaryMaterial.layout.totalPages
                )
                pendingStartPageFraction = null
            }
        }

        var target = planSpread(PageLocation(chapterIndex, resolvedPage))
            ?: SpreadTarget(PageLocation(chapterIndex, resolvedPage), null)

        // If the current chapter ends on a left page, its right half is the next
        // chapter's first page. Load that chapter before marking the slot ready.
        if (target.right == null && target.left == PageLocation(chapterIndex, resolvedPage)) {
            // Empty chapters are legal in parsed books. Walk past them until
            // the next real page is known, otherwise a short run of empty
            // chapters would expose an avoidable blank half-screen.
            var nextChapter = chapterIndex + 1
            while (target.right == null && nextChapter < chapterCount) {
                getOrStartChapterLoad(nextChapter).await()?.let(::rememberPageCount)
                target = planSpread(PageLocation(chapterIndex, resolvedPage)) ?: target
                nextChapter++
            }
        }

        val materials = linkedMapOf(chapterIndex to primaryMaterial)
        listOfNotNull(target.left, target.right)
            .map(PageLocation::chapterIndex)
            .distinct()
            .filter { it != chapterIndex }
            .forEach { otherChapter ->
                getOrStartChapterLoad(otherChapter).await()?.let {
                    rememberPageCount(it)
                    materials[otherChapter] = it
                }
            }

        if (!isCurrentRequest(slotIdx, requestToken)) return
        withContext(Dispatchers.Main) {
            populateSpreadSlot(slotIdx, target, requestToken, materials)
        }
    }

    private fun populateSpreadSlot(
        slotIdx: Int,
        target: SpreadTarget,
        requestToken: Long,
        materials: Map<Int, ChapterMaterial>
    ) {
        val slot = slots[slotIdx]
        if (!isCurrentRequest(slotIdx, requestToken)) return
        val left = target.left ?: target.right ?: return
        val leftMaterial = materials[left.chapterIndex] ?: return
        val right = target.right
        val rightMaterial = right?.let { materials[it.chapterIndex] }
        if (right != null && rightMaterial == null) return

        fun render(view: PageContentView, location: PageLocation, material: ChapterMaterial): Boolean {
            val page = material.layout.pages.getOrNull(location.pageIndex) ?: return false
            view.setPageContent(
                material.text,
                page.startCharOffset,
                page.endCharOffset,
                highlightProvider?.invoke(location.chapterIndex) ?: emptyList(),
                page.verticalGeometry
            )
            return true
        }

        if (!render(slot.contentView, left, leftMaterial)) {
            slot.isLoaded = false
            if (slotIdx == SLOT_CUR) finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.EMPTY)
            return
        }
        slot.chapterIndex = left.chapterIndex
        slot.pageIndex = left.pageIndex
        slot.globalPageIndex = layoutEngine.localToGlobal(left.chapterIndex, left.pageIndex)
        slot.chapterTotalPages = leftMaterial.layout.totalPages
        slot.primaryIsRight = false

        if (right != null && rightMaterial != null && slot.rightContentView != null) {
            if (!render(slot.rightContentView!!, right, rightMaterial)) {
                slot.rightContentView?.clear()
                slot.rightChapterIndex = -1
                slot.rightPageIndex = -1
                slot.rightGlobalPageIndex = -1
                slot.rightIsLoaded = false
                slot.isLoaded = false
                if (slotIdx == SLOT_CUR) finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.EMPTY)
                return
            }
            slot.rightChapterIndex = right.chapterIndex
            slot.rightPageIndex = right.pageIndex
            slot.rightGlobalPageIndex = layoutEngine.localToGlobal(right.chapterIndex, right.pageIndex)
            slot.rightIsLoaded = true
        } else {
            slot.rightContentView?.clear()
            slot.rightChapterIndex = -1
            slot.rightPageIndex = -1
            slot.rightGlobalPageIndex = -1
            slot.rightIsLoaded = false
        }

        slot.isLoaded = true
        onSlotReadyCallback?.invoke()
        if (slotIdx == SLOT_CUR) {
            currentChapterIndex = slot.chapterIndex
            currentGlobalPage = slot.globalPageIndex
            val (prevCh, prevPg) = resolvePrevPage()
            if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
            else ensurePreviousSlotLoaded()
            val (nextCh, nextPg) = resolveNextPage()
            if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)
            else ensureNextSlotLoaded()
            eagerPreloadUpcoming(slot.chapterIndex)
            finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.LOADED)
        }
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
            actualPage = chapterLayout.totalPages - 1
            slot.pageIndex = actualPage
        }
        if (actualPage !in 0 until chapterLayout.totalPages) {
            actualPage = actualPage.coerceIn(0, chapterLayout.totalPages - 1)
            slot.pageIndex = actualPage
        }

        val highlights = highlightProvider?.invoke(chapterIndex) ?: emptyList()
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
        if (!isCurrentRequest(slotIdx, requestToken) ||
            slot.chapterIndex != chapterIndex
        ) return
        if (slot.pageIndex != actualPage) return

        slot.globalPageIndex = layoutEngine.localToGlobal(chapterIndex, slot.pageIndex)
        slot.chapterTotalPages = chapterLayout.totalPages
        slot.isLoaded = true
        onSlotReadyCallback?.invoke()
        if (slotIdx == SLOT_CUR) {
            val (prevCh, prevPg) = resolvePrevPage()
            if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
            val (nextCh, nextPg) = resolveNextPage()
            if (nextCh >= 0 && nextPg >= 0) loadSlot(SLOT_NEXT, nextCh, nextPg)
            else ensureNextSlotLoaded()
            eagerPreloadUpcoming(chapterIndex)
            finishCurrentSlotLoad(requestToken, CurrentSlotLoadResult.LOADED)
        }
    }

    /**
     * 后台静默预加载当前章节之后的 2 章 layout，使翻章时几乎无等待。
     * 低优先级 fire-and-forget，不阻塞主流程，失败静默忽略。
     */
    private fun eagerPreloadUpcoming(currentChapter: Int) {
        if (contentProvider == null) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            for (ahead in 1..2) {
                val target = currentChapter + ahead
                if (target >= chapterCount) break
                if (layoutEngine.getChapterLayout(target) != null) continue
                getOrStartChapterLoad(target).await()
            }
        }
    }

    /** 退出阅读时调用：预跑当前章节的 layout 存入 layoutCache。 */
    fun preloadCurrentChapter() {
        val curChapter = currentChapterIndex
        if (contentProvider == null) return
        if (layoutEngine.getChapterLayout(curChapter) != null) return
        getOrStartChapterLoad(curChapter)
    }

    /** 刷新当前页内容（简繁转换等设置变更后调用）。 */
    fun refreshCurrentPage() {
        refreshJob?.cancel()
        refreshJob = refreshSlotContent(SLOT_CUR)
    }

    /** Resolve a previous spread after a direct jump when earlier page counts
     * are not cached yet. The current page remains visible while this runs. */
    private fun ensurePreviousSlotLoaded() {
        if (!spreadEnabled() || previousPrefetchJob?.isActive == true) return
        val current = slots[SLOT_CUR]
        if (!current.isLoaded || current.chapterIndex <= 0) return
        previousPrefetchJob = scope.launch {
            var chapter = current.chapterIndex - 1
            while (chapter >= 0) {
                val material = getOrStartChapterLoad(chapter).await()
                if (material != null) {
                    rememberPageCount(material)
                    withContext(Dispatchers.Main) {
                        val latest = slots[SLOT_CUR]
                        if (latest.isLoaded && latest.chapterIndex == current.chapterIndex &&
                            latest.pageIndex == current.pageIndex && !slots[SLOT_PREV].isLoaded
                        ) {
                            val (prevCh, prevPg) = resolvePrevPage()
                            if (prevCh >= 0 && prevPg >= 0) loadSlot(SLOT_PREV, prevCh, prevPg)
                        }
                    }
                    return@launch
                }
                chapter--
            }
        }
    }

    /**
     * Resolve the next spread after a page-count boundary when the following
     * chapter has not been laid out yet. Unknown chapters are represented as
     * zero pages by the planner, so load the first subsequent non-empty
     * chapter and retry the same target once its count is known.
     */
    private fun ensureNextSlotLoaded() {
        if (!spreadEnabled() || nextPrefetchJob?.isActive == true) return
        val current = slots[SLOT_CUR]
        if (!current.isLoaded || chapterCount <= 0) return
        val lastChapter = if (current.rightIsLoaded && current.rightChapterIndex >= 0) {
            current.rightChapterIndex
        } else {
            current.chapterIndex
        }
        if (lastChapter < 0 || lastChapter + 1 >= chapterCount) return

        val currentChapter = current.chapterIndex
        val currentPage = current.pageIndex
        val currentRightChapter = current.rightChapterIndex
        val currentRightPage = current.rightPageIndex
        nextPrefetchJob = scope.launch {
            var chapter = lastChapter + 1
            while (chapter < chapterCount) {
                val material = getOrStartChapterLoad(chapter).await()
                if (material != null) {
                    rememberPageCount(material)
                    withContext(Dispatchers.Main) {
                        val latest = slots[SLOT_CUR]
                        val stillCurrent = latest.isLoaded &&
                            latest.chapterIndex == currentChapter &&
                            latest.pageIndex == currentPage &&
                            latest.rightChapterIndex == currentRightChapter &&
                            latest.rightPageIndex == currentRightPage
                        if (stillCurrent && !slots[SLOT_NEXT].isLoaded) {
                            val (nextCh, nextPg) = resolveNextPage()
                            if (nextCh >= 0 && nextPg >= 0) {
                                loadSlot(SLOT_NEXT, nextCh, nextPg)
                            }
                        }
                    }
                    return@launch
                }
                // Remember an empty/failed chapter so a run of empty chapters
                // can eventually resolve to the book boundary instead of
                // leaving a queued gesture waiting forever.
                knownPageCounts[chapter] = 0
                chapter++
            }
            withContext(Dispatchers.Main) {
                val latest = slots[SLOT_CUR]
                if (latest.isLoaded && latest.chapterIndex == currentChapter &&
                    latest.pageIndex == currentPage &&
                    latest.rightChapterIndex == currentRightChapter &&
                    latest.rightPageIndex == currentRightPage
                ) {
                    onSlotReadyCallback?.invoke()
                }
            }
        }
    }

    private fun renderCurrentPage(cur: SlotState, cl: ChapterLayout, text: CharSequence) {
        if (!spreadEnabled()) {
            val page = cl.pages.getOrNull(cur.pageIndex) ?: return
            cur.contentView.setPageContent(
                text,
                page.startCharOffset,
                page.endCharOffset,
                highlightProvider?.invoke(cur.chapterIndex) ?: emptyList(),
                page.verticalGeometry
            )
            return
        }

        // The right page may belong to another chapter. Resolve each page
        // independently so a cross-chapter spread never reuses the left
        // chapter's offsets or highlight ranges.
        renderLocation(
            cur.contentView,
            PageLocation(cur.chapterIndex, cur.pageIndex),
            textOverride = text,
            layoutOverride = cl
        )
        val rightLocation = cur.rightPageIndex.takeIf {
            cur.rightIsLoaded && cur.rightChapterIndex >= 0
        }?.let { PageLocation(cur.rightChapterIndex, it) }
        if (rightLocation != null) {
            renderLocation(cur.rightContentView, rightLocation)
        } else {
            cur.rightContentView?.clear()
        }
    }

    private fun renderLocation(
        view: PageContentView?,
        location: PageLocation,
        textOverride: CharSequence? = null,
        layoutOverride: ChapterLayout? = null
    ): Boolean {
        if (view == null || location.chapterIndex < 0 || location.pageIndex < 0) return false
        val text = textOverride ?: chapterTextCache[location.chapterIndex] ?: return false
        val layout = layoutOverride ?: layoutEngine.getChapterLayout(location.chapterIndex) ?: return false
        val page = layout.pages.getOrNull(location.pageIndex) ?: return false
        view.setPageContent(
            text,
            page.startCharOffset,
            page.endCharOffset,
            highlightProvider?.invoke(location.chapterIndex) ?: emptyList(),
            page.verticalGeometry
        )
        return true
    }

    private fun slotLocations(slot: SlotState): List<PageLocation> = buildList {
        if (!slot.primaryIsRight && slot.chapterIndex >= 0 && slot.pageIndex >= 0) {
            add(PageLocation(slot.chapterIndex, slot.pageIndex))
        }
        if (slot.rightIsLoaded && slot.rightChapterIndex >= 0 && slot.rightPageIndex >= 0) {
            add(PageLocation(slot.rightChapterIndex, slot.rightPageIndex))
        }
    }

    fun updateTtsHighlight(range: TtsHighlightRange?) {
        slots.forEach { slot ->
            if (!slot.isLoaded) return@forEach
            val leftRange = range?.takeIf { it.chapterIndex == slot.chapterIndex }
            slot.contentView.updateTtsHighlight(leftRange?.start, leftRange?.end)
            val rightRange = range?.takeIf { slot.rightIsLoaded && it.chapterIndex == slot.rightChapterIndex }
            slot.rightContentView?.updateTtsHighlight(rightRange?.start, rightRange?.end)
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
        refreshSlotContent(slotIdx)
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
        refreshSlotContent(SLOT_CUR)
    }

    /** Re-render both halves using the chapter that owns each physical page. */
    private fun refreshSlotContent(slotIdx: Int): Job? {
        val snapshot = slots[slotIdx]
        if (!snapshot.isLoaded) return null
        val locations = slotLocations(snapshot)
        if (locations.isEmpty()) return null
        val token = requestTokens[slotIdx]
        val chapterIds = locations.map(PageLocation::chapterIndex).distinct()
        return scope.launch {
            val materials = linkedMapOf<Int, ChapterMaterial>()
            for (chapter in chapterIds) {
                getOrStartChapterLoad(chapter).await()?.let { materials[chapter] = it }
            }
            withContext(Dispatchers.Main) {
                val current = slots[slotIdx]
                if (!isCurrentRequest(slotIdx, token) || !current.isLoaded ||
                    slotLocations(current) != locations
                ) return@withContext
                if (!spreadEnabled()) {
                    val location = locations.first()
                    renderLocation(current.contentView, location, materials[location.chapterIndex]?.text, materials[location.chapterIndex]?.layout)
                } else {
                    val left = locations.firstOrNull()
                    val right = locations.getOrNull(1)
                    if (left == null || !renderLocation(current.contentView, left, materials[left.chapterIndex]?.text, materials[left.chapterIndex]?.layout)) {
                        current.contentView.clear()
                    }
                    if (right == null || !renderLocation(current.rightContentView, right, materials[right.chapterIndex]?.text, materials[right.chapterIndex]?.layout)) {
                        current.rightContentView?.clear()
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
            curSlot.chapterTotalPages = 0
            curSlot.contentView.clear()
            curSlot.rightContentView?.clear()
            currentChapterIndex = nextCh
            currentGlobalPage = curSlot.globalPageIndex
            loadSlot(SLOT_CUR, nextCh, nextPg)
            val (nnCh, nnPg) = resolveNextPage()
            if (nnCh >= 0 && nnPg >= 0) loadSlot(SLOT_NEXT, nnCh, nnPg)
            else ensureNextSlotLoaded()
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
        else ensureNextSlotLoaded()

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
            curSlot.chapterTotalPages = 0
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
        if (prevCh >= 0 && prevPg >= 0) {
            loadSlot(SLOT_PREV, prevCh, prevPg)
        } else {
            ensurePreviousSlotLoaded()
        }

        notifyPageChanged()
    }

    /** 跳转到指定章节的指定页（双页模式自动配对）。 */
    fun jumpTo(chapterIndex: Int, pageInChapter: Int) {
        jumpTo(chapterIndex, pageInChapter, generation = null)
    }

    internal fun jumpTo(chapterIndex: Int, pageInChapter: Int, generation: Long?) {
        cancelActiveJump()
        previousPrefetchJob?.cancel()
        previousPrefetchJob = null
        nextPrefetchJob?.cancel()
        nextPrefetchJob = null
        for (i in 0..2) recycleSlot(i)

        activeJumpGeneration = generation
        if (chapterIndex !in 0 until chapterCount || pageInChapter < 0) {
            finishInvalidJump()
            return
        }

        // A direct chapter jump starts a fresh pairing run so its first page
        // is guaranteed to occupy the left half of the new spread.
        pairingAnchorChapter = chapterIndex
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

        if (spreadEnabled()) {
            val next = ReaderSpreadPlanner.next(
                currentSpread(cur), pairingAnchorChapter, pageCountsSnapshot()
            )?.primary
            return (next?.chapterIndex ?: -1) to (next?.pageIndex ?: -1)
        }
        val cachedTotal = layoutEngine.getChapterLayout(ci)?.totalPages
        val chapterTotal = stableChapterPageCount(
            cachedPageCount = cachedTotal,
            slotPageCount = cur.chapterTotalPages,
            visiblePageIndex = pi,
            isLoaded = cur.isLoaded
        )
        return when {
            pi + 1 < chapterTotal -> ci to pi + 1
            ci + 1 < chapterCount -> ci + 1 to 0
            else -> -1 to -1
        }
    }

    private fun resolvePrevPage(): Pair<Int, Int> {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded) return -1 to -1

        if (spreadEnabled()) {
            val previous = ReaderSpreadPlanner.previous(
                currentSpread(cur), pairingAnchorChapter, pageCountsSnapshot()
            )?.primary
            return (previous?.chapterIndex ?: -1) to (previous?.pageIndex ?: -1)
        }
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

    private fun currentSpread(slot: SlotState): SpreadTarget = SpreadTarget(
        left = if (slot.primaryIsRight) null else PageLocation(slot.chapterIndex, slot.pageIndex),
        right = slot.rightPageIndex.takeIf { slot.rightIsLoaded && slot.rightChapterIndex >= 0 }
            ?.let { PageLocation(slot.rightChapterIndex, it) }
    )

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
        toSlot.chapterTotalPages = fromSlot.chapterTotalPages
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
        fromSlot.chapterTotalPages = 0
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
        if (!spreadEnabled()) {
            val rendered = renderLocation(
                slot.contentView,
                PageLocation(slot.chapterIndex, slot.pageIndex)
            )
            slot.rightContentView?.clear()
            return rendered
        }

        val leftRendered = if (slot.primaryIsRight) {
            slot.contentView.clear()
            true
        } else {
            renderLocation(
                slot.contentView,
                PageLocation(slot.chapterIndex, slot.pageIndex)
            )
        }
        val rightRendered = if (slot.rightIsLoaded && slot.rightPageIndex >= 0) {
            renderLocation(
                slot.rightContentView,
                PageLocation(slot.rightChapterIndex, slot.rightPageIndex)
            )
        } else {
            slot.rightContentView?.clear()
            true
        }
        return leftRendered && rightRendered
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
        slot.chapterTotalPages = 0
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
        val chapterTotal = stableChapterPageCount(
            cachedPageCount = chapterLayout?.totalPages,
            slotPageCount = cur.chapterTotalPages,
            visiblePageIndex = cur.pageIndex,
            isLoaded = cur.isLoaded
        )
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

    fun isAtBookStart(): Boolean {
        val cur = slots[SLOT_CUR]
        return isAbsoluteBookStart(cur.chapterIndex, cur.pageIndex, cur.isLoaded)
    }

    fun isAtBookEnd(): Boolean {
        val cur = slots[SLOT_CUR]
        if (!cur.isLoaded || chapterCount <= 0) return false
        fun isLastPage(chapter: Int, page: Int, slotCount: Int = 0): Boolean {
            if (chapter != chapterCount - 1 || page < 0) return false
            val count = maxOf(
                slotCount,
                knownPageCounts[chapter] ?: 0,
                layoutEngine.getChapterLayout(chapter)?.totalPages ?: 0
            )
            return count > 0 && page >= count - 1
        }
        if (isLastPage(cur.chapterIndex, cur.pageIndex, cur.chapterTotalPages)) return true
        return cur.rightIsLoaded && isLastPage(cur.rightChapterIndex, cur.rightPageIndex)
    }

    fun getNextPageLocation(): Pair<Int, Int> {
        val next = slots[SLOT_NEXT]
        return if (next.isLoaded) next.chapterIndex to next.pageIndex else resolveNextPage()
    }

    /** True when a following chapter may provide a page but its layout is unknown. */
    fun hasPotentialNextPage(): Boolean {
        if (!spreadEnabled()) return getNextPageLocation().first >= 0
        val resolved = resolveNextPage()
        if (resolved.first >= 0 && resolved.second >= 0) return true
        val current = slots[SLOT_CUR]
        if (!current.isLoaded || chapterCount <= 0) return false
        val lastChapter = if (current.rightIsLoaded && current.rightChapterIndex >= 0) {
            current.rightChapterIndex
        } else {
            current.chapterIndex
        }
        if (lastChapter + 1 >= chapterCount) return false

        var hasUnknownChapter = false
        for (chapter in (lastChapter + 1) until chapterCount) {
            val count = knownPageCounts[chapter]
                ?: layoutEngine.getChapterLayout(chapter)?.totalPages
            if (count == null) {
                hasUnknownChapter = true
            } else if (count > 0) {
                return true
            }
        }
        if (!hasUnknownChapter) return false
        ensureNextSlotLoaded()
        return true
    }

    fun getPrevPageLocation(): Pair<Int, Int> {
        val prev = slots[SLOT_PREV]
        return if (prev.isLoaded) prev.chapterIndex to prev.pageIndex else resolvePrevPage()
    }

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
        val loadingChapters = chapterLoadJobs.values.toList()
        chapterLoadJobs.clear()
        loadingChapters.forEach { it.cancel() }
        previousPrefetchJob?.cancel()
        previousPrefetchJob = null
        nextPrefetchJob?.cancel()
        nextPrefetchJob = null
        chapterTextCache.clear()
        knownPageCounts.clear()
    }

    fun destroy() {
        refreshJob?.cancel()
        prefetchJob?.cancel()
        cancelActiveJump()
        previousPrefetchJob?.cancel()
        previousPrefetchJob = null
        nextPrefetchJob?.cancel()
        nextPrefetchJob = null
        for (i in 0..2) recycleSlot(i)
        chapterLoadJobs.clear()
        chapterTextCache.clear()
        scope.cancel()
    }
}

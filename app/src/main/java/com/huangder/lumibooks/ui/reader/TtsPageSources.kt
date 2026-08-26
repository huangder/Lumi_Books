package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsPageContent
import com.huangder.lumibooks.tts.TtsPageLocation
import com.huangder.lumibooks.tts.TtsPageSource
import com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine
import com.huangder.lumibooks.util.ChineseConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class ReflowTtsPageSource(
    private val layoutEngine: PageLayoutEngine,
    private val chapterCount: Int,
    private val chineseMode: String,
    private var chapterProvider: (suspend (Int) -> CharSequence?)?
) : TtsPageSource {
    private val chapterCache = object : LinkedHashMap<Int, CharSequence>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CharSequence>?): Boolean =
            size > 3
    }
    private var closed = false

    override suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent? {
        if (closed || chapterIndex !in 0 until chapterCount || pageIndex < 0) return null
        val fullText = chapterText(chapterIndex)?.takeUnless { it.isEmpty() } ?: return null
        val chapterLayout = layoutEngine.layout(chapterIndex, fullText)
        if (closed) return null
        val pageLayout = chapterLayout.pages.getOrNull(pageIndex) ?: return null

        var startOffset = pageLayout.startCharOffset
        while (startOffset < pageLayout.endCharOffset && fullText[startOffset] == '\n') {
            startOffset++
        }
        val pageText = if (startOffset < pageLayout.endCharOffset) {
            ChineseConverter.convert(
                fullText.subSequence(startOffset, pageLayout.endCharOffset).toString(),
                chineseMode
            )
        } else {
            ""
        }
        val previous = when {
            pageIndex > 0 -> TtsPageLocation(chapterIndex, pageIndex - 1)
            chapterIndex <= 0 -> null
            else -> {
                val previousText = chapterText(chapterIndex - 1)?.takeUnless { it.isEmpty() }
                val previousLayout = previousText?.let { layoutEngine.layout(chapterIndex - 1, it) }
                previousLayout?.takeIf { it.totalPages > 0 }?.let {
                    TtsPageLocation(chapterIndex - 1, it.totalPages - 1)
                }
            }
        }
        val next = when {
            pageIndex + 1 < chapterLayout.totalPages -> TtsPageLocation(chapterIndex, pageIndex + 1)
            chapterIndex + 1 < chapterCount -> TtsPageLocation(chapterIndex + 1, 0)
            else -> null
        }
        return TtsPageContent(
            location = TtsPageLocation(chapterIndex, pageIndex),
            text = pageText,
            previous = previous,
            next = next,
            startCharacterOffset = startOffset,
            endCharacterOffset = pageLayout.endCharOffset
        )
    }

    private suspend fun chapterText(chapterIndex: Int): CharSequence? {
        chapterCache[chapterIndex]?.let { return it }
        val text = chapterProvider?.invoke(chapterIndex) ?: return null
        if (!closed) chapterCache[chapterIndex] = text
        return text
    }

    override fun close() {
        closed = true
        chapterProvider = null
        chapterCache.clear()
    }
}

internal class PdfTtsPageSource(
    private val pageCount: Int,
    private var pageTextProvider: (suspend (Int) -> String)
) : TtsPageSource {
    private var closed = false

    override suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent? {
        if (closed || pageIndex != 0 || chapterIndex !in 0 until pageCount) return null
        val text = pageTextProvider(chapterIndex)
        if (closed) return null
        return TtsPageContent(
            location = TtsPageLocation(chapterIndex, 0),
            text = text,
            previous = (chapterIndex - 1).takeIf { it >= 0 }?.let { TtsPageLocation(it, 0) },
            next = (chapterIndex + 1).takeIf { it < pageCount }?.let { TtsPageLocation(it, 0) }
        )
    }

    override fun close() {
        closed = true
        pageTextProvider = { "" }
    }
}

internal class OriginalLayoutEpubTtsPageSource(
    private val chapterCount: Int,
    private var webPageProvider: (suspend (Int, Int) -> EpubPageText?)?,
    private var chapterTextProvider: (suspend (Int) -> String?)?,
    private val webPageTimeoutMs: Long = WEB_PAGE_TIMEOUT_MS,
    prefetchDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : TtsPageSource {
    private data class PageBoundary(val start: Int, val end: Int)

    private val scope = CoroutineScope(SupervisorJob() + prefetchDispatcher)
    private val pageCache = mutableMapOf<TtsPageLocation, TtsPageContent>()
    private val chapterSnapshots = mutableMapOf<Int, String>()
    private val pageBoundaries = mutableMapOf<Int, MutableMap<Int, PageBoundary>>()
    private val lastServedEnd = mutableMapOf<Int, Int>()
    private var prefetchJob: Job? = null
    private var closed = false

    override suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent? {
        if (closed || chapterIndex !in 0 until chapterCount || pageIndex < 0) return null
        val location = TtsPageLocation(chapterIndex, pageIndex)
        pageCache[location]?.let { cached ->
            lastServedEnd[chapterIndex] = maxOf(lastServedEnd[chapterIndex] ?: 0, cached.endCharacterOffset)
            prefetch(cached.next)
            return cached
        }

        val webPage = requestWebPage(chapterIndex, pageIndex)
        if (webPage != null) {
            val content = cacheWebPage(webPage)
            lastServedEnd[chapterIndex] = maxOf(
                lastServedEnd[chapterIndex] ?: 0,
                content.endCharacterOffset
            )
            prefetch(content.next)
            return content
        }
        return fallbackPage(location)
    }

    private suspend fun requestWebPage(chapterIndex: Int, pageIndex: Int): EpubPageText? {
        val provider = webPageProvider ?: return null
        return try {
            withTimeoutOrNull(webPageTimeoutMs) { provider(chapterIndex, pageIndex) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    private fun cacheWebPage(page: EpubPageText): TtsPageContent {
        val start = page.startCharacterOffset.coerceAtLeast(0)
        val end = page.endCharacterOffset.coerceAtLeast(start)
        if (page.chapterText.isNotEmpty()) chapterSnapshots[page.chapterIndex] = page.chapterText
        pageBoundaries.getOrPut(page.chapterIndex, ::mutableMapOf)[page.pageIndex] =
            PageBoundary(start, end)
        val content = TtsPageContent(
            location = TtsPageLocation(page.chapterIndex, page.pageIndex),
            text = page.text,
            previous = when {
                page.pageIndex > 0 -> TtsPageLocation(page.chapterIndex, page.pageIndex - 1)
                page.chapterIndex > 0 -> TtsPageLocation(page.chapterIndex - 1, 0)
                else -> null
            },
            next = when {
                page.pageIndex + 1 < page.pageCount ->
                    TtsPageLocation(page.chapterIndex, page.pageIndex + 1)
                page.chapterIndex + 1 < chapterCount ->
                    TtsPageLocation(page.chapterIndex + 1, 0)
                else -> null
            },
            startCharacterOffset = start,
            endCharacterOffset = end
        )
        pageCache[content.location] = content
        return content
    }

    private suspend fun fallbackPage(location: TtsPageLocation): TtsPageContent? {
        val chapterIndex = location.chapterIndex
        val text = chapterSnapshots[chapterIndex]
            ?: chapterTextProvider?.invoke(chapterIndex)?.also { chapterSnapshots[chapterIndex] = it }
            ?: return null
        val boundaries = pageBoundaries[chapterIndex].orEmpty()
        val start = boundaries[location.pageIndex]?.start
            ?: boundaries[location.pageIndex - 1]?.end
            ?: lastServedEnd[chapterIndex]
            ?: if (location.pageIndex == 0) 0 else text.length
        val safeStart = start.coerceIn(0, text.length)
        val next = (chapterIndex + 1).takeIf { it < chapterCount }
            ?.let { TtsPageLocation(it, 0) }
        lastServedEnd[chapterIndex] = text.length
        return TtsPageContent(
            location = location,
            text = text.substring(safeStart),
            previous = when {
                location.pageIndex > 0 -> TtsPageLocation(chapterIndex, location.pageIndex - 1)
                chapterIndex > 0 -> TtsPageLocation(chapterIndex - 1, 0)
                else -> null
            },
            next = next,
            startCharacterOffset = safeStart,
            endCharacterOffset = text.length
        )
    }

    private fun prefetch(location: TtsPageLocation?) {
        if (closed || location == null || pageCache.containsKey(location)) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val page = requestWebPage(location.chapterIndex, location.pageIndex) ?: return@launch
            if (!closed) cacheWebPage(page)
        }
    }

    override fun close() {
        closed = true
        prefetchJob?.cancel()
        scope.cancel()
        webPageProvider = null
        chapterTextProvider = null
        pageCache.clear()
        chapterSnapshots.clear()
        pageBoundaries.clear()
        lastServedEnd.clear()
    }

    private companion object {
        const val WEB_PAGE_TIMEOUT_MS = 2_000L
    }
}

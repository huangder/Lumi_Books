package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.reader.engine.PageLayout
import org.json.JSONObject
import kotlin.math.ceil

enum class ReaderPositionFlow(val value: String) {
    PAGED("paged"),
    CONTINUOUS("continuous");

    companion object {
        fun fromValue(value: String?): ReaderPositionFlow? =
            entries.firstOrNull { it.value == value }
    }
}

enum class ReaderPageFractionSemantics {
    START,
    INCLUSIVE_PAGE_END
}

data class ReaderPositionLocator(
    val chapterIndex: Int,
    val chapterFraction: Float,
    val flow: ReaderPositionFlow,
    val characterOffset: Int? = null,
    val sourceByteOffset: Long? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("type", TYPE)
        put("version", VERSION)
        put("chapterIndex", chapterIndex.coerceAtLeast(0))
        put("chapterFraction", chapterFraction.coerceIn(0f, 0.9999f).toDouble())
        put("flow", flow.value)
        characterOffset?.let { put("characterOffset", it.coerceAtLeast(0)) }
        sourceByteOffset?.let { put("sourceByteOffset", it.coerceAtLeast(0L)) }
    }.toString()

    companion object {
        private const val TYPE = "lumi_reader_position"
        private const val VERSION = 2

        fun fromJson(json: String?): ReaderPositionLocator? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(json)
                val version = root.optInt("version")
                if (root.optString("type") != TYPE || version !in 1..VERSION) {
                    return@runCatching null
                }
                val flow = ReaderPositionFlow.fromValue(root.optString("flow"))
                    ?: return@runCatching null
                val chapterIndex = root.optInt("chapterIndex", -1)
                val chapterFraction = root.optDouble("chapterFraction", Double.NaN)
                if (chapterIndex < 0 || !chapterFraction.isFinite()) return@runCatching null
                ReaderPositionLocator(
                    chapterIndex = chapterIndex,
                    chapterFraction = chapterFraction.toFloat().coerceIn(0f, 0.9999f),
                    flow = flow,
                    characterOffset = root.optInt("characterOffset", -1).takeIf { it >= 0 },
                    sourceByteOffset = if (version >= 2) {
                        root.optLong("sourceByteOffset", -1L).takeIf { it >= 0L }
                    } else {
                        null
                    }
                )
            }.getOrNull()
        }
    }
}

internal fun restoredPagedPageIndex(
    chapterFraction: Float,
    totalPages: Int,
    semantics: ReaderPageFractionSemantics
): Int {
    if (totalPages <= 0) return 0
    val scaled = chapterFraction.coerceIn(0f, 1f) * totalPages
    val page = when (semantics) {
        ReaderPageFractionSemantics.START -> scaled.toInt()
        ReaderPageFractionSemantics.INCLUSIVE_PAGE_END ->
            (ceil(scaled.toDouble() - 0.000001).toInt() - 1).coerceAtLeast(0)
    }
    return page.coerceIn(0, totalPages - 1)
}

/** Resolve a saved anchor against the actual pages, including trimmed whitespace and stale offsets. */
internal fun restoredPagedPageIndex(position: ReaderPositionLocator, pages: List<PageLayout>): Int? {
    if (pages.isEmpty()) return null
    val offset = position.characterOffset ?: return restoredPagedPageIndex(
        position.chapterFraction, pages.size, ReaderPageFractionSemantics.START
    )
    val match = pages.binarySearch { it.startCharOffset.compareTo(offset) }
    return if (match >= 0) match else (-match - 2).coerceAtLeast(0)
}

internal fun hasPendingReaderRestore(
    locator: ReaderPositionLocator?,
    chapterFraction: Float
): Boolean = locator != null || chapterFraction > 0f

internal fun ReaderUiState.pendingPagedPageIndex(chapterIndex: Int, pages: List<PageLayout>): Int? {
    if (pages.isEmpty()) return null
    val locator = pendingReaderPosition
    if (locator != null) {
        return if (locator.chapterIndex == chapterIndex) restoredPagedPageIndex(locator, pages) else null
    }
    if (chapterIndex != currentChapterIndex || pendingPageFraction <= 0f) return null
    return restoredPagedPageIndex(pendingPageFraction, pages.size, pendingPageFractionSemantics)
}

/** Capture before mode switching clears page counters; rapid switches retain the pending anchor. */
internal fun positionForReaderFlowChange(
    chapterIndex: Int,
    pageIndex: Int,
    pageCount: Int,
    pendingPosition: ReaderPositionLocator?,
    pendingFraction: Float,
    characterOffset: Int?,
    destination: ReaderPositionFlow
): ReaderPositionLocator = pendingPosition?.copy(flow = destination) ?: ReaderPositionLocator(
    chapterIndex = chapterIndex,
    chapterFraction = (if (pageCount > 0) pageIndex.toFloat() / pageCount else pendingFraction)
        .coerceIn(0f, 0.9999f),
    flow = destination,
    characterOffset = characterOffset
)

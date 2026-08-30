package com.huangder.lumibooks.ui.reader

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
    val characterOffset: Int? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("type", TYPE)
        put("version", VERSION)
        put("chapterIndex", chapterIndex.coerceAtLeast(0))
        put("chapterFraction", chapterFraction.coerceIn(0f, 0.9999f).toDouble())
        put("flow", flow.value)
        characterOffset?.let { put("characterOffset", it.coerceAtLeast(0)) }
    }.toString()

    companion object {
        private const val TYPE = "lumi_reader_position"
        private const val VERSION = 1

        fun fromJson(json: String?): ReaderPositionLocator? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(json)
                if (root.optString("type") != TYPE || root.optInt("version") != VERSION) {
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
                    characterOffset = root.optInt("characterOffset", -1).takeIf { it >= 0 }
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

internal fun hasPendingReaderRestore(
    locator: ReaderPositionLocator?,
    chapterFraction: Float
): Boolean = locator != null || chapterFraction > 0f

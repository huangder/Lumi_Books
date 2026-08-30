package com.huangder.lumibooks.ui.reader

import org.json.JSONArray
import org.json.JSONObject

/** The three tools exposed by the raster PDF annotation mode. */
internal enum class PdfInkTool {
    PEN,
    HIGHLIGHTER,
    ERASER
}

internal data class PdfInkPoint(
    val x: Float,
    val y: Float
)

internal data class PdfInkStroke(
    val id: Long = 0L,
    val createdAt: Long = 0L,
    val page: Int,
    val points: List<PdfInkPoint>,
    val tool: PdfInkTool,
    val color: String,
    val width: Float
)

/** Versioned, page-local storage format for freehand PDF annotations. */
internal object PdfInkStrokeLocatorV1 {
    const val VERSION = 1

    fun encode(stroke: PdfInkStroke): String {
        val points = JSONArray()
        stroke.points.forEach { point ->
            points.put(
                JSONObject()
                    .put("x", point.x.toDouble())
                    .put("y", point.y.toDouble())
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("page", stroke.page)
            .put("tool", stroke.tool.name.lowercase())
            .put("width", stroke.width.toDouble())
            .put("color", stroke.color)
            .put("points", points)
            .toString()
    }

    fun decode(
        encoded: String?,
        id: Long = 0L,
        fallbackPage: Int = 0,
        fallbackColor: String = DefaultReaderHighlightColor
    ): PdfInkStroke? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(encoded)
            val pointsArray = root.optJSONArray("points") ?: return@runCatching null
            val points = buildList {
                for (index in 0 until pointsArray.length()) {
                    val point = pointsArray.optJSONObject(index) ?: continue
                    val x = point.optDouble("x", Double.NaN).toFloat()
                    val y = point.optDouble("y", Double.NaN).toFloat()
                    if (x.isFinite() && y.isFinite()) {
                        add(PdfInkPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
                    }
                }
            }
            if (points.isEmpty()) return@runCatching null
            val tool = when (root.optString("tool").lowercase()) {
                "highlighter", "marker", "fluorescent" -> PdfInkTool.HIGHLIGHTER
                "eraser" -> PdfInkTool.ERASER
                else -> PdfInkTool.PEN
            }
            PdfInkStroke(
                id = id,
                page = root.optInt("page", fallbackPage).coerceAtLeast(0),
                points = points,
                tool = tool,
                color = root.optString("color", fallbackColor).ifBlank { fallbackColor },
                width = root.optDouble("width", 0.006).toFloat().coerceIn(0.001f, 0.08f)
            )
        }.getOrNull()
    }
}

internal const val PdfInkPenType = "pdf_ink_pen"
internal const val PdfInkHighlighterType = "pdf_ink_highlighter"

internal fun PdfInkStroke.noteType(): String = when (tool) {
    PdfInkTool.PEN -> PdfInkPenType
    PdfInkTool.HIGHLIGHTER -> PdfInkHighlighterType
    PdfInkTool.ERASER -> PdfInkPenType
}

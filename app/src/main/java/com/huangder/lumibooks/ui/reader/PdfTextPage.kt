package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.AnnotationEditPlan
import com.huangder.lumibooks.domain.model.AnnotationNoteEditPlanner
import com.huangder.lumibooks.domain.model.Note
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

/** Coordinates belong to the rotated, uncropped PDF page, with its origin at the top left. */
internal data class PdfTextPoint(val x: Float, val y: Float)

internal data class PdfTextQuad(val points: List<PdfTextPoint>) {
    init { require(points.size == 4) }
    val left get() = points.minOf { it.x }
    val right get() = points.maxOf { it.x }
    val top get() = points.minOf { it.y }
    val bottom get() = points.maxOf { it.y }
    fun contains(x: Float, y: Float): Boolean {
        var positive = false
        var negative = false
        points.indices.forEach { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            val cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            positive = positive || cross > 0.000001f
            negative = negative || cross < -0.000001f
        }
        return !(positive && negative)
    }

    fun distanceTo(point: PdfTextPoint): Float {
        if (contains(point.x, point.y)) return 0f
        return points.indices.minOf { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) /
                (dx * dx + dy * dy).coerceAtLeast(0.000001f)).coerceIn(0f, 1f)
            kotlin.math.hypot(point.x - a.x - t * dx, point.y - a.y - t * dy)
        }
    }
}

internal data class PdfTextGlyph(val start: Int, val end: Int, val line: Int, val quad: PdfTextQuad)

internal data class PdfTextPage(
    val page: Int,
    val fingerprint: String,
    val text: String,
    val glyphs: List<PdfTextGlyph>
) {
    val digest: String = pdfTextDigest(text)
    val lineQuads: List<PdfTextQuad> by lazy { quads(0, text.length) }
    fun hit(x: Float, y: Float, nearest: Boolean = false): PdfTextGlyph? {
        glyphs.firstOrNull { it.quad.contains(x, y) }?.let { return it }
        if (!nearest) return null
        return glyphs.minByOrNull {
            val dx = max(it.quad.left - x, max(0f, x - it.quad.right))
            val dy = max(it.quad.top - y, max(0f, y - it.quad.bottom))
            dx * dx + dy * dy
        }
    }

    fun lineRange(glyph: PdfTextGlyph): IntRange {
        val line = glyphs.filter { it.line == glyph.line }
        return line.minOf { it.start } until line.maxOf { it.end }
    }

    /** Finger hit slop is measured in display pixels, including crop and page fit. */
    fun hitNear(point: PdfTextPoint, transform: PdfTextTransform, tolerance: Float): PdfTextGlyph? =
        glyphs.map { glyph -> glyph to PdfTextQuad(glyph.quad.points.map(transform::display)).distanceTo(point) }
            .minByOrNull { it.second }?.takeIf { it.second <= tolerance }?.first

    /** A ligature and a surrogate pair are always selected as a whole glyph. */
    fun snap(start: Int, end: Int): IntRange? {
        val selected = glyphs.filter { it.start < end && it.end > start }
        if (selected.isEmpty()) return null
        return selected.minOf { it.start } until selected.maxOf { it.end }
    }

    fun quads(start: Int, end: Int): List<PdfTextQuad> = glyphs
        .filter { it.start < end && it.end > start }
        .groupBy { it.line }.values.map { line ->
            val first = line.first().quad.points
            val dx = first[1].x - first[0].x
            val dy = first[1].y - first[0].y
            val length = kotlin.math.hypot(dx, dy).coerceAtLeast(0.000001f)
            val ux = dx / length
            val uy = dy / length
            val points = line.flatMap { it.quad.points }
            val advance = points.map { it.x * ux + it.y * uy }
            val cross = points.map { -it.x * uy + it.y * ux }
            fun point(a: Float, c: Float) = PdfTextPoint(a * ux - c * uy, a * uy + c * ux)
            PdfTextQuad(listOf(point(advance.min(), cross.min()), point(advance.max(), cross.min()),
                point(advance.max(), cross.max()), point(advance.min(), cross.max())))
        }

    fun locator(start: Int, end: Int): PdfTextLocator? {
        val range = snap(start, end) ?: return null
        return PdfTextLocator(page, range.first, range.last + 1, fingerprint, digest,
            text.substring(range.first, range.last + 1), quads(range.first, range.last + 1))
    }
}

internal fun pdfTextDigest(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Distinct from the version=1 freehand locator and the reflow reader's locators. */
internal data class PdfTextLocator(
    val page: Int,
    val start: Int,
    val end: Int,
    val fingerprint: String,
    val digest: String,
    val quote: String,
    val quads: List<PdfTextQuad>
) {
    fun matches(model: PdfTextPage): Boolean = page == model.page && fingerprint == model.fingerprint && digest == model.digest &&
        start >= 0 && end <= model.text.length && end > start &&
        model.text.substring(start, end) == quote

    fun encode(): String = JSONObject().put("kind", KIND).put("version", 1)
        .put("page", page).put("start", start).put("end", end)
        .put("fingerprint", fingerprint).put("textDigest", digest).put("quote", quote)
        .put("quads", JSONArray().also { array -> quads.forEach { quad ->
            array.put(JSONArray().also { coordinates -> quad.points.forEach {
                coordinates.put(it.x.toDouble()).put(it.y.toDouble())
            } })
        } }).toString()

    companion object {
        const val KIND = "pdf_text"
        fun decode(raw: String?): PdfTextLocator? = runCatching {
            val json = JSONObject(raw ?: return null)
            if (json.optString("kind") != KIND || json.optInt("version") != 1) return null
            val page = json.getInt("page")
            val start = json.getInt("start")
            val end = json.getInt("end")
            if (page < 0 || start < 0 || end <= start) return null
            val array = json.getJSONArray("quads")
            if (array.length() !in 1..10000) return null
            val quads = (0 until array.length()).map { i ->
                val values = array.getJSONArray(i)
                if (values.length() != 8) return null
                PdfTextQuad((0..3).map { j ->
                    val x = values.getDouble(j * 2).toFloat()
                    val y = values.getDouble(j * 2 + 1).toFloat()
                    if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return null
                    PdfTextPoint(x, y)
                }).also { if (it.right <= it.left || it.bottom <= it.top) return null }
            }
            PdfTextLocator(page, start, end, json.getString("fingerprint"),
                json.getString("textDigest"), json.getString("quote"), quads)
                .takeIf { it.fingerprint.isNotBlank() && it.digest.isNotBlank() && it.quote.length == end - start }
        }.getOrNull()
    }
}

/** Uses the same range semantics as ordinary reading, rebuilding every affected PDF anchor. */
internal fun pdfAnnotationEditPlan(
    model: PdfTextPage, existing: List<Note>, bookId: String, start: Int, end: Int,
    type: String, color: String, remove: Boolean = false, now: Long = System.currentTimeMillis()
): AnnotationEditPlan {
    val range = model.snap(start, end) ?: return AnnotationEditPlan.EMPTY
    val valid = existing.filter { note ->
        !note.isStale && PdfTextLocator.decode(note.startLocatorJson)?.matches(model) == true
    }
    val plan = if (remove) AnnotationNoteEditPlanner.removeRange(valid, model.text, bookId,
        model.page, range.first, range.last + 1, type)
    else AnnotationNoteEditPlanner.replaceRange(valid, model.text, bookId, model.page,
        range.first, range.last + 1, type, color, now)
    fun anchor(note: Note): Note {
        val locator = model.locator(note.startPosition, note.endPosition)?.encode()
        return note.copy(startLocatorJson = locator, endLocatorJson = locator, updatedAt = now)
    }
    return plan.copy(updates = plan.updates.map(::anchor), inserts = plan.inserts.map(::anchor))
}

/** Shared by hit testing, selection and painting, independently of bitmap resolution. */
internal class PdfTextTransform(
    fullRatio: Float, cropLeft: Float, cropRight: Float,
    viewportWidth: Float, viewportHeight: Float, fit: Boolean
) {
    private val cropWidth = (cropRight - cropLeft).coerceAtLeast(0.001f)
    private val left = cropLeft
    private val width = if (fit) min(viewportWidth, viewportHeight * fullRatio * cropWidth) else viewportWidth
    private val height = width / (fullRatio * cropWidth).coerceAtLeast(0.001f)
    private val x = (viewportWidth - width) / 2f
    private val y = if (fit) (viewportHeight - height) / 2f else 0f
    fun display(point: PdfTextPoint) = PdfTextPoint(x + (point.x - left) / cropWidth * width, y + point.y * height)
    fun original(point: PdfTextPoint) = PdfTextPoint(left + (point.x - x) / width * cropWidth, (point.y - y) / height)
    fun visible(point: PdfTextPoint) = point.x in left..(left + cropWidth) && point.y in 0f..1f
}

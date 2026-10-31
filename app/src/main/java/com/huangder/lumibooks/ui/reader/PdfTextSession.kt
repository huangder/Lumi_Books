package com.huangder.lumibooks.ui.reader

import android.content.Context
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.SeekableBookSource
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A separate document keeps slow text work away from the raster decoder's serial scheduler. */
internal class PdfTextSession(private val context: Context, private val location: String) : Closeable {
    private val mutex = Mutex()
    private var source: SeekableBookSource? = null
    private var document: PDDocument? = null
    private var fingerprint = ""
    private val cache = object : LinkedHashMap<Int, PdfTextPage>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PdfTextPage>?) = size > 3
    }
    @Volatile private var closed = false

    suspend fun page(index: Int): PdfTextPage? = try { withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (closed) return@withLock null
            cache[index]?.let { return@withLock it }
            if (document == null) {
                val opened = BookFileAccess.openSeekable(context, location)
                try {
                    val directory = File(context.cacheDir, "pdf_text").apply { mkdirs() }
                    val loaded = PDDocument.load(File(opened.path),
                        MemoryUsageSetting.setupMixed(32L * 1024L * 1024L).apply { tempDir = directory })
                    try { fingerprint = pdfFileFingerprint(File(opened.path)) }
                    catch (error: Throwable) { loaded.close(); throw error }
                    document = loaded
                    source = opened
                } catch (error: Throwable) {
                    opened.close()
                    throw error
                }
            }
            val active = document ?: return@withLock null
            if (index !in 0 until active.numberOfPages || !active.currentAccessPermission.canExtractContent()) {
                return@withLock PdfTextPage(index, fingerprint, "", emptyList())
            }
            val stripper = PdfPositionStripper(index, fingerprint, currentCoroutineContext(), active.getPage(index).rotation)
            stripper.getText(active)
            currentCoroutineContext().ensureActive()
            if (closed) return@withLock null
            stripper.result().also { cache[index] = it }
        }
    } } catch (memory: OutOfMemoryError) {
        close()
        throw IOException("Not enough memory for PDF text extraction", memory)
    }

    override fun close() {
        closed = true
        // A PDFBox call may still be running. Close only after it releases the mutex, off the UI thread.
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch {
            try {
                mutex.withLock {
                    cache.clear()
                    runCatching { document?.close() }
                    document = null
                    source?.close()
                    source = null
                }
            } finally { cleanup.cancel() }
        }
    }
}

/** Portable identity: bounded reads even for multi-gigabyte PDFs, independent of the device path. */
internal fun pdfFileFingerprint(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
    RandomAccessFile(file, "r").use { input ->
        val buffer = ByteArray(65536)
        val first = input.read(buffer)
        if (first > 0) digest.update(buffer, 0, first)
        if (input.length() > buffer.size) {
            input.seek(max(buffer.size.toLong(), input.length() - buffer.size))
            val last = input.read(buffer)
            if (last > 0) digest.update(buffer, 0, last)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal data class PdfPositionedText(
    val text: String, val quad: PdfTextQuad, val direction: Int,
    // Keep reading order and inferred spaces stable when improving the painted font bounds.
    val layoutQuad: PdfTextQuad = quad
)

/** Expand presentation ligatures without changing full-width CJK punctuation or circled numbers. */
internal fun normalizePdfGlyph(text: String): String = buildString {
    text.forEach { char -> append(when (char) {
        '\uFB00' -> "ff"; '\uFB01' -> "fi"; '\uFB02' -> "fl"; '\uFB03' -> "ffi"
        '\uFB04' -> "ffl"; '\uFB05', '\uFB06' -> "st"; else -> char.toString()
    }) }
}

/** PDFBox already subtracts the CropBox origin in TextPosition.textMatrix. */
internal fun pdfRotatedPoint(x: Float, y: Float, width: Float, height: Float, rotation: Int): PdfTextPoint =
    when ((rotation % 360 + 360) % 360) {
        90 -> PdfTextPoint(y / height, x / width)
        180 -> PdfTextPoint(1f - x / width, y / height)
        270 -> PdfTextPoint(1f - y / height, 1f - x / width)
        else -> PdfTextPoint(x / width, 1f - y / height)
    }

private class PdfPositionStripper(
    private val index: Int, private val fingerprint: String, private val coroutineContext: CoroutineContext,
    private val rotation: Int
) : PDFTextStripper() {
    private val glyphs = mutableListOf<PdfPositionedText>()
    init {
        startPage = index + 1
        endPage = index + 1
        sortByPosition = true
        suppressDuplicateOverlappingText = true
    }

    override fun processTextPosition(text: TextPosition) {
        coroutineContext.ensureActive()
        super.processTextPosition(text)
    }

    override fun processPage(page: PDPage) {
        coroutineContext.ensureActive()
        super.processPage(page)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        val page = currentPage
        val crop = page.cropBox
        val rotation = page.rotation
        textPositions.forEach { position ->
            val unicode = normalizePdfGlyph(position.unicode ?: "")
            if (unicode.isEmpty()) return@forEach
            val matrix = position.textMatrix
            val length = sqrt(matrix.scaleX * matrix.scaleX + matrix.shearY * matrix.shearY)
            val upLength = sqrt(matrix.shearX * matrix.shearX + matrix.scaleY * matrix.scaleY)
            if (length <= 0f || upLength <= 0f) return@forEach
            val dx = matrix.scaleX / length
            val dy = matrix.shearY / length
            val ux = matrix.shearX / upLength
            val uy = matrix.scaleY / upLength
            val width = position.widthDirAdj.coerceAtLeast(0.1f)
            // PDFBox heightDir is often just the font's x-height (sometimes even
            // smaller). Use the em and font metrics to cover capitals and CJK too.
            val em = upLength.coerceAtLeast(position.heightDir)
            val descriptor = position.font.fontDescriptor
            val ascent = max(position.heightDir, em *
                ((descriptor?.ascent ?: 800f) / 1000f).coerceIn(0.8f, 1.2f))
            val descent = em * ((-(descriptor?.descent ?: -200f)) / 1000f).coerceIn(0.15f, 0.35f)
            val x = matrix.translateX
            val y = matrix.translateY
            fun point(advance: Float, ascent: Float): PdfTextPoint = pdfRotatedPoint(
                x + dx * advance + ux * ascent, y + dy * advance + uy * ascent,
                crop.width, crop.height, rotation)
            val points = listOf(point(0f, ascent), point(width, ascent),
                point(width, -descent), point(0f, -descent))
            if (points.any { !it.x.isFinite() || !it.y.isFinite() } ||
                points.all { it.x < 0f || it.x > 1f || it.y < 0f || it.y > 1f }) return@forEach
            val quad = PdfTextQuad(points.map { PdfTextPoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) })
            if (quad.right > quad.left && quad.bottom > quad.top) {
                val visualDirection = ((position.dir.toInt() - rotation) % 360 + 360) % 360
                val oldHeight = position.heightDir.coerceAtLeast(0.1f)
                val layoutQuad = PdfTextQuad(listOf(point(0f, oldHeight), point(width, oldHeight),
                    point(width, -oldHeight * 0.2f), point(0f, -oldHeight * 0.2f)).map {
                    PdfTextPoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f))
                })
                glyphs += PdfPositionedText(unicode, quad, visualDirection, layoutQuad)
            }
        }
    }

    fun result() = buildPdfTextPage(index, fingerprint, glyphs, rotation)
}

/** Cluster visual lines, split wide gutters, then read columns instead of joining neighboring columns. */
internal fun buildPdfTextPage(index: Int, fingerprint: String, input: List<PdfPositionedText>, pageRotation: Int = 0): PdfTextPage {
    if (input.isEmpty()) return PdfTextPage(index, fingerprint, "", emptyList())
    val lines = mutableListOf<MutableList<PdfPositionedText>>()
    input.groupBy { it.direction }.forEach { (direction, positioned) ->
        val vertical = direction == 90 || direction == 270
        fun baseline(g: PdfPositionedText) = if (vertical) g.layoutQuad.points[3].x else g.layoutQuad.points[3].y
        fun advance(g: PdfPositionedText) = if (vertical) g.layoutQuad.top else g.layoutQuad.left
        val groups = mutableListOf<MutableList<PdfPositionedText>>()
        positioned.sortedBy(::baseline).forEach { glyph ->
            val thickness = if (vertical) glyph.layoutQuad.right - glyph.layoutQuad.left else glyph.layoutQuad.bottom - glyph.layoutQuad.top
            val previous = groups.lastOrNull()
            if (previous != null && abs(baseline(previous.first()) - baseline(glyph)) < thickness * 0.4f) previous += glyph
            else groups += mutableListOf(glyph)
        }
        groups.forEach { group ->
            val sorted = group.sortedBy(::advance).let { if (direction == 90 || direction == 180) it.reversed() else it }
            var fragment = mutableListOf<PdfPositionedText>()
            sorted.forEach { glyph ->
                val previous = fragment.lastOrNull()
                val gap = if (previous == null) 0f else if (vertical)
                    max(glyph.layoutQuad.top - previous.layoutQuad.bottom, previous.layoutQuad.top - glyph.layoutQuad.bottom)
                    else max(glyph.layoutQuad.left - previous.layoutQuad.right, previous.layoutQuad.left - glyph.layoutQuad.right)
                val thickness = if (vertical) glyph.layoutQuad.right - glyph.layoutQuad.left else glyph.layoutQuad.bottom - glyph.layoutQuad.top
                if (gap > thickness * 1.8f && fragment.isNotEmpty()) {
                    lines += fragment
                    fragment = mutableListOf()
                }
                fragment += glyph
            }
            if (fragment.isNotEmpty()) lines += fragment
        }
    }
    // Reading order is defined before page rotation; the saved geometry remains in display coordinates.
    val ordered = orderPdfTextLines(lines, rotation = pageRotation)
    val text = StringBuilder()
    val mapped = mutableListOf<PdfTextGlyph>()
    ordered.forEachIndexed { line, glyphs ->
        if (text.isNotEmpty()) text.append('\n')
        glyphs.forEachIndexed { i, glyph ->
            val previous = glyphs.getOrNull(i - 1)
            if (previous != null && !previous.text.last().isWhitespace() && !glyph.text.first().isWhitespace()) {
                val vertical = glyph.direction == 90 || glyph.direction == 270
                val gap = if (vertical) max(glyph.layoutQuad.top - previous.layoutQuad.bottom, previous.layoutQuad.top - glyph.layoutQuad.bottom)
                    else max(glyph.layoutQuad.left - previous.layoutQuad.right, previous.layoutQuad.left - glyph.layoutQuad.right)
                val thickness = if (vertical) glyph.layoutQuad.right - glyph.layoutQuad.left else glyph.layoutQuad.bottom - glyph.layoutQuad.top
                if (gap > thickness * 0.18f) text.append(' ')
            }
            val start = text.length
            text.append(glyph.text)
            mapped += PdfTextGlyph(start, text.length, line, glyph.quad)
        }
    }
    return PdfTextPage(index, fingerprint, text.toString(), mapped)
}

private fun orderPdfTextLines(lines: List<List<PdfPositionedText>>, depth: Int = 0, rotation: Int = 0): List<List<PdfPositionedText>> {
    if (lines.size < 2 || depth > 16) return lines
    fun unrotate(point: PdfTextPoint): PdfTextPoint = when ((rotation % 360 + 360) % 360) {
        90 -> PdfTextPoint(point.y, 1f - point.x)
        180 -> PdfTextPoint(1f - point.x, 1f - point.y)
        270 -> PdfTextPoint(1f - point.y, point.x)
        else -> point
    }
    fun points(line: List<PdfPositionedText>) = line.flatMap { it.layoutQuad.points }.map(::unrotate)
    val height = lines.flatMap { it }.map { glyph ->
        val p = glyph.layoutQuad.points.map(::unrotate)
        p.maxOf { it.y } - p.minOf { it.y }
    }.sorted().let { it[it.size / 2] }
    for (horizontal in listOf(false, true)) {
        fun start(line: List<PdfPositionedText>) = points(line).minOf { if (horizontal) it.y else it.x }
        fun end(line: List<PdfPositionedText>) = points(line).maxOf { if (horizontal) it.y else it.x }
        val sorted = lines.sortedBy(::start)
        var boundary = end(sorted.first())
        var best = -1
        var bestGap = height * if (horizontal) 1.3f else 1.8f
        for (i in 1 until sorted.size) {
            val gap = start(sorted[i]) - boundary
            if (gap > bestGap) { best = i; bestGap = gap }
            boundary = max(boundary, end(sorted[i]))
        }
        if (best > 0) return orderPdfTextLines(sorted.take(best), depth + 1, rotation) + orderPdfTextLines(sorted.drop(best), depth + 1, rotation)
    }
    return lines.sortedWith(compareBy<List<PdfPositionedText>> { points(it).minOf { p -> p.y } }
        .thenBy { points(it).minOf { p -> p.x } })
}

package com.huangder.lumibooks.ui.excerpt

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.caverock.androidsvg.SVG
import com.huangder.lumibooks.util.ReaderBackgroundImageProcessor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

internal enum class ExcerptStyle { READER, CALENDAR, CLASSIC, ILLUSTRATION, ILLUSTRATION_EDGE, COVER }

internal class ExcerptDocument(
    val height: Int,
    val backgroundColor: Int,
    private val draw: (Canvas) -> Unit
) {
    val width = 920
    val previewFirstTile: Bitmap by lazy { render(0, minOf(1200, height)) }
    fun render(top: Int = 0, tileHeight: Int = height): Bitmap {
        val bitmap = Bitmap.createBitmap(width, tileHeight, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply { translate(0f, -top.toFloat()); draw(this) }
            return bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
}

internal object ExcerptRenderer {
    private data class Block(val layout: StaticLayout, val x: Float, val y: Float)
    internal data class VerticalTitleRun(val text: String, val sideways: Boolean)

    internal fun verticalTitleRuns(text: String): List<VerticalTitleRun> = buildList {
        val horizontal = StringBuilder()
        fun flush() {
            horizontal.toString().trim().takeIf { it.isNotEmpty() }?.let { add(VerticalTitleRun(it, true)) }
            horizontal.clear()
        }
        text.codePoints().toArray().forEach { codePoint ->
            val script = Character.UnicodeScript.of(codePoint)
            val upright = script in setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL) ||
                codePoint in 0x3000..0x303f || codePoint in 0xff01..0xff60
            if (upright) {
                flush()
                add(VerticalTitleRun(String(Character.toChars(codePoint)), false))
            } else horizontal.appendCodePoint(codePoint)
        }
        flush()
    }

    fun prepare(context: Context, request: ExcerptRequest, style: ExcerptStyle): ExcerptDocument {
        val width = 920f
        val bookTypeface = ResourcesCompat.getFont(context, R.font.gen_ryu_min2_light) ?: Typeface.SERIF
        val cover = if (style == ExcerptStyle.COVER) loadCover(request.coverPath) else null
        val background = when (style) {
            ExcerptStyle.READER -> request.backgroundColor
            ExcerptStyle.CLASSIC -> Color.rgb(27, 29, 32)
            ExcerptStyle.ILLUSTRATION, ExcerptStyle.ILLUSTRATION_EDGE -> Color.rgb(255, 244, 244)
            ExcerptStyle.COVER -> Color.rgb(57, 49, 46)
            else -> Color.rgb(252, 251, 249)
        }
        val foreground = when (style) {
            ExcerptStyle.READER -> request.textColor
            ExcerptStyle.CLASSIC -> Color.rgb(236, 219, 172)
            ExcerptStyle.COVER -> Color.rgb(255, 248, 236)
            else -> Color.rgb(56, 43, 40)
        }
        val secondary = ColorUtils.setAlphaComponent(foreground, 180)
        val blocks = mutableListOf<Block>()
        val titleDraws = mutableListOf<(Canvas) -> Unit>()
        var y = 120f
        fun add(text: String, size: Float, color: Int = foreground, gap: Float = 0f,
                centered: Boolean = false, bold: Boolean = false) {
            if (text.isBlank()) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size
                typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else bookTypeface
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 720)
                .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(10f, 1.18f).setIncludePad(true).build()
            blocks.add(Block(layout, 100f, y))
            y += layout.height + gap
        }
        val date = Date(request.createdAt)
        val artwork = when (style) {
            ExcerptStyle.ILLUSTRATION -> "excerpt_character.png"
            ExcerptStyle.ILLUSTRATION_EDGE -> "excerpt_reading.png"
            else -> null
        }?.let { name -> context.assets.open(name).use { BitmapFactory.decodeStream(it) } }
        val headerBottom: Float
        when (style) {
            ExcerptStyle.CALENDAR -> {
                add(SimpleDateFormat("dd", Locale.getDefault()).format(date), 170f, centered = true, bold = true)
                add(SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(date).uppercase(Locale.ENGLISH), 42f, centered = true, bold = true)
                add(SimpleDateFormat("EEEE", Locale.getDefault()).format(date), 28f, secondary, gap = 95f, centered = true)
                headerBottom = y - 45f
            }
            ExcerptStyle.CLASSIC -> {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = foreground; textSize = 62f; typeface = bookTypeface
                }
                val titleTop = y
                var column = 0
                var advance = 0f
                var titleBottom = y
                fun place(text: String, sideways: Boolean) {
                    val length = if (sideways) paint.measureText(text) + 12f else 78f
                    if (advance > 0 && advance + length > 640f) { column++; advance = 0f }
                    val x = 100f + (column % 6) * 112f
                    val top = titleTop + (column / 6) * 700f + advance
                    titleDraws.add { canvas ->
                        canvas.save()
                        if (sideways) {
                            canvas.translate(x + 42f, top)
                            canvas.rotate(90f)
                            canvas.drawText(text, 0f, -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f, paint)
                        } else {
                            canvas.drawText(text, x + (85f - paint.measureText(text)) / 2f, top - paint.fontMetrics.ascent, paint)
                        }
                        canvas.restore()
                    }
                    advance += length
                    titleBottom = maxOf(titleBottom, top + length)
                }
                verticalTitleRuns(request.bookTitle).forEach { run ->
                    if (!run.sideways) place(run.text, false)
                    else {
                        // Android's normal paragraph layout preserves whole words and shaped scripts.
                        val lines = StaticLayout.Builder.obtain(run.text, 0, run.text.length, paint, 620)
                            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build()
                        repeat(lines.lineCount) { line ->
                            run.text.substring(lines.getLineStart(line), lines.getLineEnd(line)).trim()
                                .takeIf { it.isNotEmpty() }?.let { place(it, true) }
                        }
                    }
                }
                y = titleBottom + 40f
                add(request.author, 28f, secondary, gap = 90f)
                headerBottom = y
            }
            else -> {
                add(request.bookTitle, 54f)
                add(request.author, 28f, secondary, gap = 65f)
                headerBottom = y
            }
        }
        add(request.text, 37f, gap = 42f)
        add(request.note, 29f, secondary, gap = 38f)
        if (style == ExcerptStyle.CALENDAR) {
            add("《${request.bookTitle}》", 34f, centered = true)
            add(request.author, 28f, secondary, centered = true, gap = 35f)
        }
        add(request.chapter.takeIf { it.isNotBlank() }?.let { "/ $it" }.orEmpty(), 28f, secondary, gap = 36f)
        val height = ceil(y + 140f).toInt()
        val logoSource = context.assets.open("excerpt_logo.svg").bufferedReader().use { it.readText() }
        val logoPicture = SVG.getFromString(logoSource.replace("#FFFFFF", String.format(Locale.US, "#%06X", foreground and 0xFFFFFF)))
            .renderToPicture(106, 68)
        val logo = Bitmap.createBitmap(106, 68, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawPicture(logoPicture)
        }
        return ExcerptDocument(height, background) { canvas ->
            canvas.drawColor(background)
            cover?.let {
                canvas.drawBitmap(it, null, RectF(0f, 0f, width, height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.drawColor(Color.argb(150, 15, 13, 12))
            }
            artwork?.let {
                // Artwork is a background decoration: it never changes any text positions.
                val regionHeight = minOf(540f, height * 0.52f)
                val sourceTop = if (style == ExcerptStyle.ILLUSTRATION) (it.height * 0.28f).toInt() else 0
                val scaledWidth = regionHeight * it.width / (it.height - sourceTop)
                val left = if (style == ExcerptStyle.ILLUSTRATION_EDGE) width - scaledWidth else (width - scaledWidth) / 2f
                val top = (height * 0.70f - regionHeight / 2f).coerceAtMost(height - regionHeight - 50f)
                canvas.drawBitmap(it, Rect(0, sourceTop, it.width, it.height),
                    RectF(left, top, left + scaledWidth, top + regionHeight),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 105 })
            }
            if (style == ExcerptStyle.CLASSIC) {
                val paint = Paint().apply { color = foreground; alpha = 180 }
                canvas.drawRect(72f, 55f, 848f, 67f, paint)
                canvas.drawRect(72f, height - 55f, 848f, height - 43f, paint)
            }
            if (style == ExcerptStyle.CALENDAR) {
                canvas.drawLine(400f, headerBottom, 520f, headerBottom, Paint().apply { color = secondary; strokeWidth = 1.5f })
            }
            blocks.forEach { block ->
                canvas.save()
                canvas.translate(block.x, block.y)
                block.layout.draw(canvas)
                canvas.restore()
            }
            titleDraws.forEach { it(canvas) }
            canvas.drawBitmap(logo, 714f, height - 135f, null)
        }
    }

    private fun loadCover(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            val source = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
            source.recycle()
            // Three box passes approximate a high-radius Gaussian on the reduced cover.
            ReaderBackgroundImageProcessor.blur(mutable, 22)
            mutable
        }.getOrNull()
    }
}

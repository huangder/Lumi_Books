package com.huangder.lumibooks.ui.bookshare

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.ui.excerpt.ExcerptDocument
import java.time.LocalDate
import java.util.Locale

internal enum class BookShareKind { REVIEW, CHECK_IN }
internal enum class BookShareStyle(val premium: Boolean) { SIMPLE(false), COVER(true), TICKET(true) }
internal data class BookShareSnapshot(
    val book: Book, val review: String, val rating: Float, val date: LocalDate, val activity: ActivityDuration,
    val currentProgress: Float?
)
internal fun createBookShareSnapshot(book: Book, review: String, rating: Float,
    daily: Map<LocalDate, ActivityDuration>, date: LocalDate, today: LocalDate): BookShareSnapshot =
    BookShareSnapshot(book, review, rating, date, daily[date] ?: ActivityDuration(),
        book.readingProgress.takeIf { it.isFinite() && date == today })
internal fun shareHasContent(data: BookShareSnapshot, kind: BookShareKind): Boolean =
    kind == BookShareKind.REVIEW || data.activity.totalDuration > 0

internal fun loadShareCover(path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 500).coerceAtLeast(1)
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

internal fun shareDuration(context: Context, ms: Long): String =
    if (ms < 60_000) context.getString(R.string.activity_duration_seconds, ms / 1000)
    else if (ms < 3_600_000) context.getString(R.string.time_minutes, (ms / 60_000).toInt())
    else context.getString(R.string.statistics_duration_value, ms / 3_600_000, ms / 60_000 % 60)

internal object BookShareRenderer {
    fun prepare(context: Context, snapshot: BookShareSnapshot, kind: BookShareKind, style: BookShareStyle): ExcerptDocument =
        BookShareEdition.prepare(context, snapshot, kind, style) ?: prepareShareDocument(context, snapshot, kind)
}

/** Common content layout; edition templates supply their own colors and decoration. */
internal fun prepareShareDocument(
    context: Context, data: BookShareSnapshot, kind: BookShareKind,
    background: Int = Color.rgb(250, 249, 246), foreground: Int = Color.rgb(38, 40, 39),
    decoration: ((Canvas, Int) -> Unit)? = null
): ExcerptDocument {
    // Centred editorial composition on one axis (x = 460): header, cover, title block, then body.
    // Long reviews switch to left alignment inside the same margins so paragraphs stay readable.
    val center = 460f
    val contentWidth = 744
    val muted = Color.argb(150, Color.red(foreground), Color.green(foreground), Color.blue(foreground))
    val blocks = mutableListOf<Pair<Float, StaticLayout>>()
    var y = 96f
    fun text(value: String, size: Float, bold: Boolean = false, gap: Float = 22f, color: Int = foreground,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, spacing: Float = 0f, serif: Boolean = !bold) {
        if (value.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; letterSpacing = spacing
            typeface = if (bold) Typeface.create(if (serif) "serif" else "sans-serif", Typeface.BOLD)
                else if (serif) Typeface.SERIF else Typeface.SANS_SERIF
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, contentWidth)
            .setAlignment(align).setLineSpacing(8f, 1.14f).build()
        blocks += y to layout
        y += layout.height + gap
    }
    val kindLabel = context.getString(if (kind == BookShareKind.CHECK_IN) R.string.insights_check_in else R.string.insights_review_card)
    text(kindLabel + "  ·  " + com.huangder.lumibooks.ui.statistics.insightsDate(data.date, "yMMMd"), 25f, color = muted, spacing = .08f, serif = false)
    val coverTop = y + 22f
    val coverHeight = 420f
    y = coverTop + coverHeight + 56f
    text(data.book.title, 50f, true, gap = 14f, serif = true)
    text(data.book.author, 29f, color = muted, gap = 30f, serif = false)
    var dividerY = -1f
    if (kind == BookShareKind.REVIEW) {
        if (data.rating > 0) {
            val full = data.rating.toInt().coerceIn(0, 5)
            val stars = "★".repeat(full) + "☆".repeat(5 - full)
            text(stars + "  " + String.format(Locale.getDefault(), "%.1f", data.rating), 32f, true, gap = 30f, serif = false)
        }
        val review = data.review.trim()
        if (review.isNotEmpty()) {
            dividerY = y + 4f
            y += 44f
            val short = review.length <= 64 && review.count { it == '\n' } <= 2
            text(review, 33f, gap = 40f, align = if (short) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
        }
    } else {
        dividerY = y + 4f
        y += 44f
        text(shareDuration(context, data.activity.totalDuration), 72f, true, gap = 12f, serif = false)
        text(context.getString(R.string.activity_duration_breakdown, shareDuration(context, data.activity.readingDuration),
            shareDuration(context, data.activity.listeningDuration)), 29f, color = muted, gap = 16f, serif = false)
        data.currentProgress?.let { text(context.getString(R.string.insights_current_progress, (it.coerceIn(0f, 1f) * 100).toInt()), 29f,
            color = muted, serif = false) }
    }
    y = maxOf(y + 48f, 1100f)
    text("LUMI", 24f, true, 64f, color = muted, spacing = .4f, serif = false)
    val height = y.toInt()
    val cover = loadShareCover(data.book.coverPath)
    return ExcerptDocument(height, background) { canvas ->
        canvas.drawColor(background)
        decoration?.invoke(canvas, height)
        // Cover: fitted inside 300×420, vertically centred in its slot, rounded with a soft shadow.
        val (w, h) = if (cover != null) minOf(300f / cover.width, coverHeight / cover.height).let { cover.width * it to cover.height * it }
            else 300f to coverHeight
        val top = coverTop + (coverHeight - h) / 2f
        val rect = RectF(center - w / 2, top, center + w / 2, top + h)
        val radius = 14f
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = background; setShadowLayer(34f, 0f, 16f, Color.argb(70, 0, 0, 0))
        })
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        if (cover != null) canvas.drawBitmap(cover, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        else {
            canvas.drawRect(rect, Paint().apply { color = Color.argb(28, Color.red(foreground), Color.green(foreground), Color.blue(foreground)) })
            val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = 34f; typeface = Typeface.create("serif", Typeface.BOLD) }
            val layout = StaticLayout.Builder.obtain(data.book.title, 0, data.book.title.length, label, (w - 60f).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(7).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            canvas.translate(rect.left + 30f, rect.centerY() - layout.height / 2f); layout.draw(canvas)
        }
        canvas.restore()
        if (dividerY > 0) canvas.drawLine(center - 36f, dividerY + 18f, center + 36f, dividerY + 18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND })
        blocks.forEach { (blockTop, layout) -> canvas.save(); canvas.translate(center - contentWidth / 2f, blockTop); layout.draw(canvas); canvas.restore() }
    }
}

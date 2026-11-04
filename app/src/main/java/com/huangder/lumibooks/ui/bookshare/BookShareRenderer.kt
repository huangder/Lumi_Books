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
    val blocks = mutableListOf<Pair<Float, StaticLayout>>()
    var y = 82f
    fun text(value: String, size: Float, bold: Boolean = false, gap: Float = 22f) {
        if (value.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground; textSize = size
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.SERIF
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, 744)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(8f, 1.14f).build()
        blocks += y to layout
        y += layout.height + gap
    }
    text(context.getString(if (kind == BookShareKind.CHECK_IN) R.string.insights_check_in else R.string.insights_review_card) + " · " + data.date, 27f)
    val coverTop = y + 12f
    y = coverTop + 438f
    text(data.book.title, 48f, true)
    text(data.book.author, 29f)
    if (kind == BookShareKind.REVIEW) {
        if (data.rating > 0) text(context.getString(R.string.insights_personal_rating, String.format(Locale.getDefault(), "%.1f", data.rating)), 30f, true)
        text(data.review.trim(), 33f, gap = 40f)
    } else {
        text(context.getString(R.string.activity_duration_breakdown, shareDuration(context, data.activity.readingDuration),
            shareDuration(context, data.activity.listeningDuration)), 33f, true)
        data.currentProgress?.let { text(context.getString(R.string.insights_current_progress, (it.coerceIn(0f, 1f) * 100).toInt()), 30f) }
    }
    y = maxOf(y + 40f, 1080f)
    text("Lumi", 30f, true, 50f)
    val height = y.toInt()
    val cover = loadShareCover(data.book.coverPath)
    return ExcerptDocument(height, background) { canvas ->
        canvas.drawColor(background)
        decoration?.invoke(canvas, height)
        val coverRect = RectF(310f, coverTop, 610f, coverTop + 402f)
        if (cover != null) {
            val ratio = minOf(300f / cover.width, 402f / cover.height)
            val w = cover.width * ratio; val h = cover.height * ratio
            canvas.drawBitmap(cover, null, RectF(460 - w / 2, coverTop, 460 + w / 2, coverTop + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawRoundRect(coverRect, 10f, 10f, Paint().apply { color = Color.argb(24, Color.red(foreground), Color.green(foreground), Color.blue(foreground)) })
            val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = 32f }
            val layout = StaticLayout.Builder.obtain(data.book.title, 0, data.book.title.length, label, 240)
                .setMaxLines(8).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            canvas.save(); canvas.translate(340f, coverTop + 40f); layout.draw(canvas); canvas.restore()
        }
        blocks.forEach { (top, layout) -> canvas.save(); canvas.translate(88f, top); layout.draw(canvas); canvas.restore() }
    }
}

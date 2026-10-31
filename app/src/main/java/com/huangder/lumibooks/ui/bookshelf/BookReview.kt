package com.huangder.lumibooks.ui.bookshelf

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LumiEasterEgg
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppThemeVariant
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val BOOK_REVIEW_MAX_LENGTH = 2000

/** 0 = unrated; 1..5 picks the verbal label. Half stars round up, like common rating sites. */
internal fun reviewRatingTier(rating: Float): Int =
    if (rating <= 0f) 0 else rating.roundToInt().coerceIn(1, 5)

/** A tap selects the touched whole star; dragging keeps the 0.1 precision. */
internal fun reviewRatingAt(fraction: Float, wholeStar: Boolean): Float {
    val clamped = fraction.coerceIn(0f, 1f)
    return if (wholeStar) ceil(clamped * 5f).coerceIn(1f, 5f)
    else (clamped * 50f).roundToInt() / 10f
}

internal fun formatReviewScore(rating: Float): String =
    String.format(java.util.Locale.ROOT, "%.1f", rating)

/** The collapsed details card shows three lines, so paragraph breaks become spaces until it expands. */
internal fun reviewPreviewText(review: String): String =
    review.trim().replace(Regex("\\s*\\n+\\s*"), " ")

/** Gray covers would tint the hero gray; fall back to the theme accent instead. */
internal fun reviewAmbientTone(swatch: Color?, accent: Color, isDark: Boolean): Color {
    val source = swatch?.takeIf { it.chroma() >= 0.12f } ?: accent
    return if (isDark) lerp(source, Color.Black, 0.35f) else lerp(source, Color.White, 0.40f)
}

private fun Color.chroma(): Float = maxOf(red, green, blue) - minOf(red, green, blue)

@Composable
internal fun reviewRatingLabel(rating: Float): String? = when (reviewRatingTier(rating)) {
    1 -> stringResource(R.string.book_review_rating_1)
    2 -> stringResource(R.string.book_review_rating_2)
    3 -> stringResource(R.string.book_review_rating_3)
    4 -> stringResource(R.string.book_review_rating_4)
    5 -> stringResource(R.string.book_review_rating_5)
    else -> null
}

/** Gold is the rating color in every theme; e-ink keeps pure black. */
internal val reviewStarFillColor: Color
    @Composable get() = if (LocalEInkMode.current) Color.Black else Color(0xFFFFB938)

internal val reviewStarEmptyColor: Color
    @Composable get() = AppColors.TextSecondary.copy(alpha = 0.28f)

/** Accent fill and content for review actions. Lumi artwork keeps its teal action color. */
@Composable
internal fun reviewActionColors(): Pair<Color, Color> =
    if (LocalAppThemeVariant.current == LumiEasterEgg.THEME && !LocalEInkMode.current) {
        Color(0xFF52776F) to Color.White
    } else {
        AppColors.Accent to AppColors.OnAccent
    }

@Composable
internal fun ReviewStar(fill: Float, emptyColor: Color, fillColor: Color, modifier: Modifier) {
    val path = remember { Path() }
    Canvas(modifier) {
        path.reset()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension * 0.48f
        val inner = outer * 0.45f
        for (point in 0 until 10) {
            val radius = if (point % 2 == 0) outer else inner
            val angle = -PI / 2.0 + point * PI / 5.0
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, emptyColor)
        val portion = fill.coerceIn(0f, 1f)
        if (portion > 0f) {
            // Partial stars fill from the reading-direction start edge.
            if (layoutDirection == LayoutDirection.Rtl) {
                clipRect(left = size.width * (1f - portion)) { drawPath(path, fillColor) }
            } else {
                clipRect(right = size.width * portion) { drawPath(path, fillColor) }
            }
        }
    }
}

/** Two cover colors for the review hero; both null when the cover is missing or unreadable. */
internal data class CoverSwatches(val glow: Color? = null, val base: Color? = null)

internal suspend fun loadCoverSwatches(context: Context, coverPath: String?): CoverSwatches {
    if (coverPath.isNullOrBlank()) return CoverSwatches()
    return withContext(Dispatchers.IO) {
        runCatching {
            // A small software copy for Palette. Skipping the memory cache keeps this
            // thumbnail from replacing the full-size cover that is cached under the same key.
            val request = ImageRequest.Builder(context)
                .data(coverPath)
                .size(128)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()
            val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                ?: return@runCatching CoverSwatches()
            val palette = Palette.from(bitmap).generate()
            val glow = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            val base = palette.mutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
            CoverSwatches(glow = glow?.let { Color(it.rgb) }, base = base?.let { Color(it.rgb) })
        }.getOrDefault(CoverSwatches())
    }
}

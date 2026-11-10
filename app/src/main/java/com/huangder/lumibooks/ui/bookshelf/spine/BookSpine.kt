package com.huangder.lumibooks.ui.bookshelf.spine

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.animation.bookCoverMemoryCacheKey
import kotlin.math.abs

private val SpineShape = RoundedCornerShape(2.dp)
private val LightInk = Color(0xFFF8F3EA)
private val DarkInk = Color(0xFF2B2118)

/** Spines are always set in serif type, whatever the app's interface font is. */
private val SpineMing = FontFamily(Font(R.font.gen_ryu_min2_light, FontWeight.Normal))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val SpineSerif = FontFamily(
    Font(R.font.source_serif4_regular, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)))
)

private const val CJK_LINE = 1.10f
private const val MIN_TITLE = 8.5f
private val TightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/**
 * The spine is the cover's centre strip decoded at a few pixels and stretched, which blurs it
 * heavily for the cost of one tiny bitmap per book; the title is printed over it. The size is
 * passed in, so a shelf of spines needs no extra layout pass while it scrolls.
 */
@Composable
internal fun BookSpineFace(book: Book, horizontal: Boolean, width: Dp, height: Dp, modifier: Modifier = Modifier,
    sharp: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fallback = remember(book.title) { spineFallbackColor(book.title) }
    var luminance by remember(book.id, book.coverPath) { mutableFloatStateOf(-1f) }
    val coverPath = book.coverPath
    val shade = remember(horizontal) { spineShade(horizontal) }
    Box(
        modifier
            .clip(SpineShape)
            .background(fallback)
            .drawBehind { drawRect(shade) }
    ) {
        if (!coverPath.isNullOrBlank()) {
            val request = remember(book.id, coverPath, sharp, width, height) {
                spineCoverRequest(context, "${book.id}_$coverPath", coverPath, sharp,
                    with(density) { width.roundToPx() }, with(density) { height.roundToPx() })
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = if (sharp) FilterQuality.Medium else FilterQuality.Low,
                onSuccess = { state -> luminance = averageLuminance(state.result.drawable) },
                modifier = Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    drawRect(shade)
                }
            )
        }
        val ink = when {
            luminance >= 0.62f -> DarkInk
            luminance >= 0f -> LightInk
            fallbackLuminance(fallback) >= 0.62f -> DarkInk
            else -> LightInk
        }
        SpineTitle(book.title, book.author, ink, horizontal,
            thickness = if (horizontal) height.value else width.value,
            length = if (horizontal) width.value else height.value)
    }
}

/** Everything about a spine's type that depends only on its text and size. */
@Immutable
private class SpineType(
    val vertical: Boolean,
    val title: String,
    val titleSize: Float,
    val author: String?,
    val authorSize: Float,
    val margin: Float,
    val cjk: Boolean
)

/**
 * Printed like a real spine: Ming type set vertically for CJK, serif turned on its side for
 * Latin, sized to the spine, with the author at the foot when the spine is long enough.
 */
@Composable
private fun SpineTitle(title: String, author: String, ink: Color, horizontal: Boolean, thickness: Float, length: Float) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val type = remember(title, author, horizontal, thickness, length, density) {
        val main = SpineText.mainTitle(title)
        val shortAuthor = SpineText.shortAuthor(author)
        val cjk = isMostlyCjk(main)
        val margin = (length * 0.08f).coerceIn(7f, 16f)
        val showAuthor = shortAuthor.isNotEmpty() && length >= 130f && thickness >= 18f
        val maxTitle = (thickness * 0.50f).coerceIn(MIN_TITLE, 15f)
        val authorSize = (maxTitle * 0.70f).coerceIn(7.5f, 10.5f)
        fun sp(value: Float) = with(density) { value.dp.toSp() }
        if (cjk && !horizontal) {
            val units = SpineText.verticalUnits(main)
            val authorUnits = SpineText.verticalUnits(shortAuthor)
            val authorLength = if (showAuthor) authorUnits.size * authorSize * CJK_LINE + margin * 0.6f else 0f
            val (size, count) = SpineText.fitVertical(units.size, length - margin * 2f - authorLength,
                maxTitle, MIN_TITLE, CJK_LINE)
            SpineType(true, units.take(count).joinToString("\n"), size,
                authorUnits.joinToString("\n").takeIf { showAuthor }, authorSize, margin, true)
        } else {
            val family = if (cjk) SpineMing else SpineSerif
            val spacing = if (cjk) 0.04f else 0.05f
            val authorWidth = if (showAuthor) {
                measurer.measure(shortAuthor, TextStyle(fontFamily = family, fontSize = sp(authorSize)))
                    .size.width / density.density + margin
            } else 0f
            val available = length - margin * 2f - authorWidth
            var size = maxTitle.coerceAtMost(thickness * 0.56f).coerceAtLeast(MIN_TITLE)
            // Measure the widest size once; the text scales linearly from there.
            val width = measurer.measure(main, TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold,
                fontSize = sp(size), letterSpacing = sp(spacing * size))).size.width / density.density
            if (width > available && width > 0f) size = (size * available / width).coerceAtLeast(MIN_TITLE)
            SpineType(false, main, size, shortAuthor.takeIf { showAuthor }, authorSize, margin, cjk)
        }
    }
    val shadow = if (ink == LightInk) Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 2f) else null
    fun sp(value: Float) = with(density) { value.dp.toSp() }
    val family = if (type.cjk) SpineMing else SpineSerif
    Box(Modifier.fillMaxSize()) {
        if (type.vertical) {
            Text(
                text = type.title,
                color = ink,
                fontFamily = SpineMing,
                fontWeight = FontWeight.SemiBold,
                fontSize = sp(type.titleSize),
                lineHeight = sp(type.titleSize * CJK_LINE),
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = shadow, lineHeightStyle = TightLines),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = type.margin.dp)
            )
            type.author?.let { text ->
                Text(
                    text = text,
                    color = ink.copy(alpha = 0.78f),
                    fontFamily = SpineMing,
                    fontSize = sp(type.authorSize),
                    lineHeight = sp(type.authorSize * CJK_LINE),
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = shadow, lineHeightStyle = TightLines),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = type.margin.dp)
                )
            }
            return@Box
        }
        val spacing = if (type.cjk) 0.04f else 0.05f
        val line: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (type.author != null) Arrangement.SpaceBetween else Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = type.margin.dp)
            ) {
                Text(
                    text = type.title,
                    color = ink,
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = sp(type.titleSize),
                    letterSpacing = sp(spacing * type.titleSize),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = shadow),
                    modifier = Modifier.weight(1f, fill = false)
                )
                type.author?.let { text ->
                    Text(
                        text = text,
                        color = ink.copy(alpha = 0.78f),
                        fontFamily = family,
                        fontSize = sp(type.authorSize),
                        maxLines = 1,
                        style = TextStyle(shadow = shadow),
                        modifier = Modifier.padding(start = type.margin.dp)
                    )
                }
            }
        }
        if (horizontal) {
            line()
        } else {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(width = length.dp, height = thickness.dp)
                    .rotate(90f)
            ) { line() }
        }
    }
}

/** The front cover, used once a lifted book has turned to face the reader. */
@Composable
internal fun BookCoverFace(book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coverPath = book.coverPath
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(spineFallbackColor(book.title))) {
        if (!coverPath.isNullOrBlank()) {
            AsyncImage(
                model = remember(book.id, coverPath) {
                    ImageRequest.Builder(context)
                        .data(coverPath)
                        .memoryCacheKey(bookCoverMemoryCacheKey(book.id, coverPath))
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = SpineText.mainTitle(book.title),
                color = LightInk,
                fontFamily = if (isMostlyCjk(book.title)) SpineMing else SpineSerif,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(12.dp)
            )
        }
        // Light from the spine edge, so the face reads as a board rather than a flat card.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.22f),
                    0.06f to Color.White.copy(alpha = 0.10f),
                    0.12f to Color.Transparent,
                    1f to Color.Transparent
                )
            )
        )
    }
}

/**
 * The front of a lifted folder box: its own cover when it has one, otherwise the cardboard front
 * with a large paper label carrying the name and size.
 */
@Composable
internal fun FolderCoverFace(folder: LibraryFolder, countLabel: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val board = Color(0xFFC9A777)
    val coverPath = folder.coverPath?.takeIf { it.isNotBlank() }
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(board)) {
        if (coverPath != null) {
            AsyncImage(
                model = remember(folder.id, coverPath) {
                    ImageRequest.Builder(context).data(coverPath).memoryCacheKey("folder_cover_${folder.id}_$coverPath").build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFF4EBDD))
                    .padding(vertical = 18.dp, horizontal = 10.dp)
            ) {
                Text(
                    text = folder.name,
                    color = DarkInk,
                    fontFamily = spineFontFor(folder.name),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = countLabel,
                    color = DarkInk.copy(alpha = 0.65f),
                    fontFamily = SpineSerif,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .size(42.dp, 8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.22f),
                    0.06f to Color.White.copy(alpha = 0.10f),
                    0.12f to Color.Transparent,
                    1f to Color.Transparent
                )
            )
        )
    }
}

/** A cardboard magazine box standing among the books. */
@Composable
internal fun FolderSpineFace(folder: LibraryFolder, countLabel: String, width: Dp, height: Dp,
    modifier: Modifier = Modifier, sharp: Boolean = false) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val board = Color(0xFFC9A777)
    val shade = remember { spineShade(false) }
    val labelHeight = height * 0.62f
    val labelWidth = width * 0.72f
    Box(
        modifier
            .clip(SpineShape)
            .background(board)
            .drawBehind { drawRect(shade) }
    ) {
        // A folder cover is printed on the box like a book's, under the paper label.
        folder.coverPath?.takeIf { it.isNotBlank() }?.let { path ->
            AsyncImage(
                model = remember(folder.id, path, sharp, width, height) {
                    spineCoverRequest(context, "folder_${folder.id}_$path", path, sharp,
                        with(density) { width.roundToPx() }, with(density) { height.roundToPx() })
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = if (sharp) FilterQuality.Medium else FilterQuality.Low,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    drawRect(shade)
                }
            )
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = height * 0.12f)
                .size(width = labelWidth, height = labelHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFF4EBDD))
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(width = labelHeight, height = labelWidth)
                    .rotate(90f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = folder.name,
                    color = DarkInk,
                    fontFamily = if (isMostlyCjk(folder.name)) SpineMing else SpineSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = with(density) { 12.dp.toSp() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
        Text(
            text = countLabel,
            color = DarkInk.copy(alpha = 0.7f),
            fontFamily = SpineSerif,
            fontSize = with(density) { 9.dp.toSp() },
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = height * 0.1f)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = height * 0.04f)
                .size(width * 0.32f, 5.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
        )
    }
}

/** Serif type for text that belongs to the shelf (the book menu's title). */
internal fun spineFontFor(text: String): FontFamily = if (isMostlyCjk(text)) SpineMing else SpineSerif

private fun spineShade(horizontal: Boolean): Brush {
    val stops = arrayOf(
        0f to Color.Black.copy(alpha = 0.22f),
        0.20f to Color.White.copy(alpha = 0.10f),
        0.45f to Color.Transparent,
        0.85f to Color.Black.copy(alpha = 0.06f),
        1f to Color.Black.copy(alpha = 0.24f)
    )
    return if (horizontal) Brush.verticalGradient(*stops) else Brush.horizontalGradient(*stops)
}

/**
 * Blurred: the cover decoded at a few pixels and stretched, which blurs it heavily for the cost
 * of one tiny bitmap. Sharp: decoded at the spine's own size, so the centre strip stays crisp.
 */
private fun spineCoverRequest(context: android.content.Context, key: String, path: String, sharp: Boolean,
    widthPx: Int, heightPx: Int): ImageRequest = ImageRequest.Builder(context)
    .data(path)
    .apply {
        if (sharp) size((heightPx * 0.7f).toInt().coerceAtLeast(widthPx).coerceAtLeast(1), heightPx.coerceAtLeast(1))
        else size(12, 36)
    }
    .allowHardware(false)
    .memoryCacheKey(if (sharp) "spine_sharp_${heightPx}_$key" else "spine_$key")
    .build()

internal fun spineFallbackColor(title: String): Color {
    val hue = (abs(title.hashCode()) % 360).toFloat()
    return Color.hsv(hue, 0.38f, 0.52f)
}

private fun fallbackLuminance(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

private fun averageLuminance(drawable: Drawable): Float {
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return -1f
    if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.width <= 0 || bitmap.height <= 0) return -1f
    var total = 0f
    var count = 0
    val stepX = (bitmap.width / 8).coerceAtLeast(1)
    val stepY = (bitmap.height / 16).coerceAtLeast(1)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            total += 0.2126f * r + 0.7152f * g + 0.0722f * b
            count++
            x += stepX
        }
        y += stepY
    }
    return if (count == 0) -1f else total / count
}

internal fun isMostlyCjk(text: String): Boolean {
    var cjk = 0
    var other = 0
    text.forEach { char ->
        if (char.isWhitespace()) return@forEach
        val block = Character.UnicodeBlock.of(char)
        if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
        ) cjk++ else other++
    }
    return cjk > 0 && cjk >= other
}

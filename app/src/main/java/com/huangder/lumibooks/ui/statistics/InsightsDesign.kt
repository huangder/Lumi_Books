package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.ui.icons.ControlIcon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.animation.lumiPressable
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.LocalAppUiIconStyle
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------------------------

/** Whether decorative statistics motion may run; e-ink and reduced motion always snap. */
@Composable internal fun insightsMotion(): Boolean = LocalMotionEnabled.current && !LocalEInkMode.current

/** iOS-like "ease out back": arrives quickly and settles with a small overshoot. */
internal val InsightsOvershoot = CubicBezierEasing(0.34f, 1.42f, 0.64f, 1f)

/**
 * Page-scoped memory of which elements already animated. An
 * element is marked the moment its animation starts, so scrolling it away mid-animation and back
 * never replays it. Staggered delays only apply during the page's first moments; elements first
 * scrolled into view later appear promptly without delay.
 */
internal class InsightsMotionRegistry {
    val played = HashSet<Int>()
    val bornAt = android.os.SystemClock.uptimeMillis()
    val initialPhase: Boolean get() = android.os.SystemClock.uptimeMillis() - bornAt < 900
}

internal val LocalInsightsMotion = staticCompositionLocalOf<InsightsMotionRegistry?> { null }

private val keyedRegistries = object : LinkedHashMap<String, InsightsMotionRegistry>(16, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InsightsMotionRegistry>?) = size > 24
}

/**
 * Wraps a page so its statistics elements share one motion registry. A [key] keeps the registry
 * beyond this composition, for blocks that live inside another page's lazy list.
 */
@Composable internal fun ProvideInsightsMotion(key: String? = null, content: @Composable () -> Unit) {
    val registry = remember(key) { key?.let { keyedRegistries.getOrPut(it) { InsightsMotionRegistry() } } ?: InsightsMotionRegistry() }
    CompositionLocalProvider(LocalInsightsMotion provides registry, content = content)
}

@Composable private fun motionId(extra: Any?): Int = currentCompositeKeyHash * 31 + (extra?.hashCode() ?: 0)

/** 0→1 progress that plays once per element and page, used for grow-in charts. */
@Composable internal fun rememberPlayOnce(key: Any?, durationMillis: Int = 820, delayMillis: Int = 0): State<Float> {
    val motion = insightsMotion()
    val registry = LocalInsightsMotion.current
    val id = motionId(key)
    val already = registry == null || id in registry.played
    val progress = remember(key) { Animatable(if (already || !motion) 1f else 0f) }
    LaunchedEffect(key, motion) {
        if (!motion || progress.value >= 1f) { progress.snapTo(1f); registry?.played?.add(id); return@LaunchedEffect }
        registry?.played?.add(id)
        if (delayMillis > 0 && registry?.initialPhase == true) delay(VisualMotion.delayMillis(delayMillis).toLong())
        progress.animateTo(1f, VisualMotion.tween(durationMillis, easing = AppEasing.Decelerate))
    }
    return progress.asState()
}

/** Staggered card entrance: lift + fade + slight scale, once per element and page. */
@Composable internal fun InsightsAppear(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = insightsMotion()
    val registry = LocalInsightsMotion.current
    val id = motionId("appear")
    val value = remember { Animatable(if (registry == null || id in registry.played || !motion) 1f else 0f) }
    LaunchedEffect(motion) {
        registry?.played?.add(id)
        if (!motion || value.value >= 1f) { value.snapTo(1f); return@LaunchedEffect }
        if (registry?.initialPhase == true) delay(VisualMotion.delayMillis(minOf(index * 55, 330)).toLong())
        value.animateTo(1f, VisualMotion.spring(dampingRatio = .86f, stiffness = 320f))
    }
    Box(modifier.graphicsLayer {
        val v = value.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 18.dp.toPx()
        val s = .97f + .03f * v
        scaleX = s; scaleY = s
    }) { content() }
}

// ---------------------------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------------------------

internal fun insightsDate(date: LocalDate, skeleton: String, locale: Locale = Locale.getDefault()): String = runCatching {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    date.format(DateTimeFormatter.ofPattern(pattern, locale))
}.getOrElse { date.toString() }

/** "Nov 4" this year, "Nov 4, 2025" otherwise. */
internal fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    insightsDate(date, if (date.year == today.year) "MMMd" else "yMMMd")

/** "Tue, Nov 4" (with year when needed) for sheet headers and day rows. */
internal fun dayTitle(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    insightsDate(date, if (date.year == today.year) "MMMdEEE" else "yMMMdEEE")

/** Digits big and bold, units small and secondary: "3h 12m" reads like Apple Fitness. */
@Composable internal fun styledFigure(text: String, size: TextUnit, unitSize: TextUnit = (size.value * .5f).coerceAtLeast(12f).sp,
    color: Color = AppColors.TextPrimary, unitColor: Color = AppColors.TextSecondary): AnnotatedString = buildAnnotatedString {
    text.forEach { char ->
        if (char.isDigit() || char == '.' || char == ',' || char == '%' || char == '+' || char == '-' || char == '−')
            withStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold, color = color)) { append(char) }
        else withStyle(SpanStyle(fontSize = unitSize, fontWeight = FontWeight.SemiBold, color = unitColor)) { append(char) }
    }
}

// ---------------------------------------------------------------------------------------------
// Palette (iOS system hues; e-ink falls back to black)
// ---------------------------------------------------------------------------------------------

internal object InsightsHue {
    val Blue = Color(0xFF007AFF)
    val Indigo = Color(0xFF5856D6)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Pink = Color(0xFFFF2D55)
    val Teal = Color(0xFF30B0C7)
    val Yellow = Color(0xFFFFB800)
    val Red = Color(0xFFFF3B30)
    val Purple = Color(0xFFAF52DE)
}

@Composable internal fun hue(color: Color): Color = if (LocalEInkMode.current) Color.Black else color

/** Secondary data color: the listening share of rings and bars. */
@Composable internal fun listenColor(): Color = if (LocalEInkMode.current) Color(0xFF888888) else InsightsHue.Indigo

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

internal val InsightsCardRadius = 22.dp

@Composable internal fun Modifier.insightsCardSurface(radius: Dp = InsightsCardRadius, elevation: Dp = 10.dp): Modifier {
    val shape = AppRoundedCornerShape(radius)
    val shadowColor = AppColors.CardShadow
    // Content role only: these cards live inside the captured scroll layer and must never sample it.
    val eInk = LocalEInkMode.current
    return this
        .then(if (eInk || shadowColor.alpha == 0f) Modifier
            else Modifier.shadow(elevation, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor))
        .then(if (eInk) Modifier.border(1.dp, Color.Black, shape) else Modifier.cardOutline(shape))
        .clip(shape).lumiCardSurface(shape = shape)
}

/** Card header in the Health style: tinted glyph + tinted title, optional trailing action. */
@Composable internal fun InsightsCardHeader(title: String, icon: ImageVector?, tint: Color, action: (@Composable () -> Unit)? = null) {
    val iconStyle = LocalAppUiIconStyle.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                AppIcons.styled(icon, iconStyle),
                null,
                tint = if (iconStyle.isColorful) hue(tint) else AppColors.TextPrimary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(title, color = if (icon != null) hue(tint) else AppColors.TextPrimary, fontSize = AppType.Subhead,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Small accent text action with a chevron ("See all ›"). */
@Composable internal fun InsightsTextAction(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.clip(AppRoundedCornerShape(AppRadius.full)).lumiPressable(interaction)
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = AppColors.Accent, fontSize = AppType.Footnote, fontWeight = FontWeight.Medium)
        ControlIcon(directionalIcon(AppIcons.CaretRight, AppIcons.CaretLeft), null, tint = AppColors.Accent, modifier = Modifier.size(14.dp))
    }
}

/** Capsule pill, used for deltas, tags and statuses. */
@Composable internal fun InsightsPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = false) {
    val eInk = LocalEInkMode.current
    val iconStyle = LocalAppUiIconStyle.current
    val shape = AppRoundedCornerShape(AppRadius.full)
    Row(modifier.clip(shape)
        .then(if (eInk) Modifier.border(1.dp, Color.Black, shape) else Modifier.background(if (filled) color else color.copy(alpha = .14f)))
        .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val content = if (eInk) Color.Black else if (filled) Color.White else color
        if (icon != null) {
            val iconTint = when {
                iconStyle == com.huangder.lumibooks.domain.model.AppUiIconStyle.FILLED_COLORFUL -> content
                iconStyle.isColorful -> if (filled) Color.White else color
                filled -> content
                else -> AppColors.TextPrimary
            }
            ControlIcon(AppIcons.styled(icon, iconStyle), null, tint = iconTint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = content, fontSize = AppType.Footnote, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Metric tile: tinted glyph and caption on top, large counting figure below. */
@Composable internal fun MetricTile(icon: ImageVector, tint: Color, label: String, value: String, modifier: Modifier = Modifier) {
    val iconStyle = LocalAppUiIconStyle.current
    val filledTile = iconStyle.usesFilledGlyph
    Column(modifier.insightsCardSurface(18.dp, 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).then(
                    if (filledTile) Modifier.clip(AppRoundedCornerShape(7.dp)).background(
                        if (iconStyle.isColorful) hue(tint) else AppColors.TextPrimary
                    ) else Modifier
                ),
                Alignment.Center
            ) {
                Icon(
                    AppIcons.styled(icon, iconStyle),
                    null,
                    tint = when {
                        filledTile && iconStyle.isColorful -> Color.White
                        filledTile -> AppColors.PageBg
                        iconStyle.isColorful -> hue(tint)
                        else -> AppColors.TextPrimary
                    },
                    modifier = Modifier.size(if (iconStyle == com.huangder.lumibooks.domain.model.AppUiIconStyle.FILLED_COLORFUL) 14.dp else 20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(label, color = AppColors.TextSecondary, fontSize = AppType.Footnote, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(styledFigure(value, 26.sp, 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 2 columns on phones, 4 on wide layouts; tiles in a row share the tallest height. */
@Composable internal fun MetricGrid(tiles: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Animated read/listen donut with rounded caps. */
@Composable internal fun ReadListenRing(reading: Long, listening: Long, modifier: Modifier = Modifier, size: Dp = 92.dp, stroke: Dp = 13.dp) {
    val total = (reading + listening).coerceAtLeast(1L)
    val grow by rememberPlayOnce("ring", 1100)
    val readFraction by animateFloatAsState(reading.toFloat() / total,
        if (insightsMotion()) VisualMotion.spring(dampingRatio = .85f, stiffness = 140f) else snap(), label = "ringRead")
    val readColor = hue(AppColors.Accent)
    val listen = listenColor()
    val track = AppColors.BgGray
    Box(modifier.size(size), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val arcSize = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
            if (reading + listening <= 0) return@Canvas
            val sweep = 360f * grow
            val read = sweep * readFraction
            val gap = if (reading > 0 && listening > 0) 6f else 0f
            if (listening > 0) drawArc(listen, -90f + read + gap / 2, (sweep - read - gap).coerceAtLeast(0.1f), false, topLeft, arcSize,
                style = Stroke(w, cap = StrokeCap.Round))
            if (reading > 0) drawArc(readColor, -90f, (read - gap / 2).coerceAtLeast(0.1f), false, topLeft, arcSize,
                style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(styledFigure("${(readFraction * 100).toInt().takeIf { reading + listening > 0 } ?: 0}%", 18.sp, 11.sp))
    }
}

/** Thin animated capsule progress bar. */
@Composable internal fun InsightsProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, key: Any? = null) {
    val grow = rememberPlayOnce(key ?: "progress", 900)
    val shape = AppRoundedCornerShape(AppRadius.full)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(AppColors.BgGray)) {
        // Width is static; growth is a layer scale read in the draw phase (no recomposition per frame).
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f).coerceAtLeast(if (fraction > 0) .02f else 0f))
            .graphicsLayer { transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f); scaleX = grow.value }
            .clip(shape).background(hue(color)))
    }
}

// ---------------------------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------------------------

/**
 * Segmented tabs shared by the scope selector and the page-level toggles. Glass uses the shared
 * Liquid Glass control (sampling the provided sibling backdrop); other themes slide a raised pill.
 */
@Composable internal fun InsightSegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, glass: Boolean = true, enabled: Boolean = true, height: Dp = lumiSegmentHeight()) =
    LumiSegmentedControl(labels, selectedIndex, onSelect, modifier, glass, enabled, height)

/** Round chevron button that slides the period, with a label that slides in the same direction. */
@Composable internal fun InsightsPeriodHeader(label: String, subtitle: String?, canPrevious: Boolean, canNext: Boolean,
    previousDescription: String, nextDescription: String, previous: () -> Unit, next: () -> Unit, glass: Boolean = true) {
    var direction by remember { mutableIntStateOf(0) }
    val motion = insightsMotion()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LiquidGlassIconButton(directionalIcon(AppIcons.CaretLeft, AppIcons.CaretRight), previousDescription,
            { direction = -1; previous() }, size = 40.dp, iconSize = 18.dp, enabled = canPrevious,
            normalContainerColor = AppColors.BgGray, forceFallback = !glass)
        Box(Modifier.weight(1f).padding(horizontal = AppSpace.sm), Alignment.Center) {
            if (!motion) PeriodTitle(label, subtitle)
            else AnimatedContent(label to subtitle, transitionSpec = {
                val d = if (direction == 0) 1 else direction
                (slideInHorizontally(VisualMotion.spring(dampingRatio = .86f, stiffness = 380f)) { it / 3 * d } + fadeIn(VisualMotion.tween(220)))
                    .togetherWith(slideOutHorizontally(VisualMotion.tween(200)) { -it / 3 * d } + fadeOut(VisualMotion.tween(140)))
            }, label = "periodTitle") { (title, sub) -> PeriodTitle(title, sub) }
        }
        LiquidGlassIconButton(directionalIcon(AppIcons.CaretRight, AppIcons.CaretLeft), nextDescription,
            { direction = 1; next() }, size = 40.dp, iconSize = 18.dp, enabled = canNext,
            normalContainerColor = AppColors.BgGray, forceFallback = !glass)
    }
}

@Composable private fun PeriodTitle(title: String, subtitle: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = AppColors.TextPrimary, fontSize = AppType.Headline, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, color = AppColors.TextSecondary, fontSize = AppType.Caption, maxLines = 1)
    }
}

/** Shimmer skeleton shaped like the hero card and metric grid. */
@Composable internal fun InsightsSkeleton() {
    val motion = insightsMotion()
    @Composable fun block(modifier: Modifier) {
        if (motion) com.huangder.lumibooks.ui.animation.ShimmerBox(modifier)
        else Box(modifier.clip(AppRoundedCornerShape(8.dp)).background(AppColors.BgGray))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().insightsCardSurface().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            block(Modifier.width(96.dp).height(14.dp))
            block(Modifier.width(180.dp).height(40.dp))
            block(Modifier.fillMaxWidth().height(120.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { Column(Modifier.weight(1f).insightsCardSurface(18.dp, 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                block(Modifier.width(72.dp).height(12.dp)); block(Modifier.width(96.dp).height(26.dp))
            } }
        }
    }
}

/** Row with a coloured glyph tile, title/subtitle and trailing value — grouped-list idiom. */
@Composable internal fun InsightsIconRow(icon: ImageVector, tint: Color, title: String, value: String, subtitle: String? = null,
    modifier: Modifier = Modifier) {
    val iconStyle = LocalAppUiIconStyle.current
    val eInk = LocalEInkMode.current
    val currentStyle = iconStyle == com.huangder.lumibooks.domain.model.AppUiIconStyle.FILLED_COLORFUL
    val filledTile = iconStyle.usesFilledGlyph
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).then(
                if (filledTile) {
                    Modifier.clip(AppRoundedCornerShape(9.dp)).background(
                        when {
                            currentStyle -> hue(tint).copy(alpha = if (eInk) 1f else .14f)
                            else -> AppColors.TextPrimary
                        }
                    )
                } else Modifier
            ),
            Alignment.Center
        ) {
            Icon(
                AppIcons.styled(icon, iconStyle),
                null,
                tint = when {
                    currentStyle && eInk -> Color.White
                    currentStyle -> tint
                    filledTile -> AppColors.PageBg
                    iconStyle.isColorful && eInk -> Color.Black
                    iconStyle.isColorful -> hue(tint)
                    else -> AppColors.TextPrimary
                },
                modifier = Modifier.size(if (currentStyle) 17.dp else 21.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.TextPrimary, fontSize = AppType.Subhead, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = AppColors.TextSecondary, fontSize = AppType.Caption, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(styledFigure(value, 17.sp, 12.sp), maxLines = 1)
    }
}

@Composable internal fun InsightsDivider(start: Dp = 44.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(0.5.dp).background(AppColors.Divider))
}

/** Vertical gradient fill for bars, softened at the base. */
@Composable internal fun barBrush(color: Color): Brush =
    if (LocalEInkMode.current) Brush.verticalGradient(listOf(Color.Black, Color.Black))
    else Brush.verticalGradient(listOf(color, color.copy(alpha = .72f)))


/** Like [activityDurationText] but drops a leading "0h": "20 min" reads cleaner than "0h 20m". */
@Composable internal fun insightsDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return if (seconds in 60L until 3_600L) androidx.compose.ui.res.stringResource(com.huangder.lumibooks.R.string.time_minutes, (seconds / 60L).toInt())
    else activityDurationText(durationMs)
}

/**
 * Sheet geometry for statistics popups: capped to 80% of the real screen height (so it never runs
 * past the window on short or split screens) with safe top/bottom gaps that clear the navigation
 * bar even when a dialog window reports no insets.
 */
@Composable internal fun insightsSheetMaxHeight(): Dp = appSheetMaxHeight()

@Composable internal fun insightsSheetPadding(): PaddingValues = appSheetPadding()

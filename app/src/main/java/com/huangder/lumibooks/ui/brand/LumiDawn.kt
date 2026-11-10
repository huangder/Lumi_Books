package com.huangder.lumibooks.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppAccentColor
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * "Lumi Dawn" is the launch-time visual language: a page lit by soft glows in the user's
 * accent colour, bold system type, and one primary action. It is shared by the splash, the
 * welcome flow and the official build's Premium welcome page. Under the Liquid Glass theme its
 * controls become real glass over the glow (see [LumiDawnStage]).
 */
@Immutable
data class LumiDawnColors(
    val base: Color,
    /** The large main glow: the accent itself. */
    val glow: Color,
    /** A second glow, the accent turned ~30° around the hue wheel. */
    val glowShifted: Color,
    /** A small counter-glow in the accent's complement. */
    val glowCounter: Color,
    val highlight: Color,
    val accent: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val paper: Color,
    val hairline: Color,
    val buttonFill: Color,
    val buttonContent: Color
)

/** Rotates [color]'s hue by [degrees] and scales saturation/value. */
internal fun Color.shiftHue(degrees: Float, saturation: Float = 1f, value: Float = 1f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    hsv[1] = (hsv[1] * saturation).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * value).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun dawnColors(accent: Color, dark: Boolean): LumiDawnColors = if (!dark) LumiDawnColors(
    base = lerp(Color(0xFFF6F7FA), accent, 0.04f),
    glow = accent.copy(alpha = 0.30f),
    glowShifted = accent.shiftHue(34f, saturation = 0.7f, value = 1.1f).copy(alpha = 0.22f),
    glowCounter = accent.shiftHue(180f, saturation = 0.5f, value = 1.12f).copy(alpha = 0.18f),
    highlight = Color.White.copy(alpha = 0.92f),
    accent = accent,
    ink = Color(0xFF1D1E22),
    inkSecondary = Color(0xFF5E626B),
    inkTertiary = Color(0xFF9A9EA7),
    paper = Color.White.copy(alpha = 0.92f),
    hairline = Color(0x14202634),
    buttonFill = Color(0xFF1D1E22),
    buttonContent = Color.White
) else LumiDawnColors(
    base = lerp(Color(0xFF0E1014), accent, 0.06f),
    glow = accent.copy(alpha = 0.30f),
    glowShifted = accent.shiftHue(34f, saturation = 0.85f).copy(alpha = 0.22f),
    glowCounter = accent.shiftHue(180f, saturation = 0.5f).copy(alpha = 0.14f),
    highlight = Color.White.copy(alpha = 0.05f),
    accent = accent,
    ink = Color(0xFFF3F3F6),
    inkSecondary = Color(0xFFA6AAB4),
    inkTertiary = Color(0xFF6C707A),
    paper = Color(0xFF1A1C22).copy(alpha = 0.82f),
    hairline = Color(0x1FFFFFFF),
    buttonFill = Color(0xFFF3F3F6),
    buttonContent = Color(0xFF131418)
)

private val InkDawn = LumiDawnColors(
    base = Color.White,
    glow = Color.White,
    glowShifted = Color.White,
    glowCounter = Color.White,
    highlight = Color.White,
    accent = Color.Black,
    ink = Color.Black,
    inkSecondary = Color.Black,
    inkTertiary = Color(0xFF555555),
    paper = Color.White,
    hairline = Color.Black,
    buttonFill = Color.Black,
    buttonContent = Color.White
)

/**
 * True inside a [LumiDawnStage] that already paints the background behind several pages (the
 * welcome flow). Pages then skip their own [LumiDawnBackground] so the glow keeps drifting
 * continuously while they change.
 */
val LocalLumiDawnBackdrop = staticCompositionLocalOf { false }

@Composable
fun lumiDawnColors(): LumiDawnColors {
    if (LocalEInkMode.current) return InkDawn
    val accent = LocalAppAccentColor.current
    val dark = LocalIsDarkTheme.current
    return remember(accent, dark) { dawnColors(accent, dark) }
}

/** True when decorative, looping motion may run (motion enabled and not on e-ink). */
@Composable
fun lumiDawnMotion(): Boolean = LocalMotionEnabled.current && !LocalEInkMode.current

/** True when Dawn controls should be real Liquid Glass. */
@Composable
fun lumiDawnGlass(): Boolean =
    LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current && !LocalReduceTransparency.current

/**
 * The background: the accent as a large glow low in the page, a hue-shifted glow at the top
 * left, a small complementary glow in the far corner and a soft white highlight above centre.
 * The glows drift on slightly different orbits so the page seems to breathe. An optional
 * [accent] glow is added at the top right (the Premium page uses champagne).
 */
@Composable
fun LumiDawnBackground(modifier: Modifier = Modifier, accent: Color? = null, animated: Boolean = true) {
    val colors = lumiDawnColors()
    if (accent == null && LocalLumiDawnBackdrop.current) return
    if (LocalEInkMode.current) {
        Box(modifier.fillMaxSize().background(colors.base))
        return
    }
    val motion = animated && lumiDawnMotion()
    // The phase is only read while drawing, so the drift never recomposes anything.
    val phase: State<Float> = if (motion) {
        rememberInfiniteTransition(label = "lumiDawn").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
            label = "lumiDawnPhase"
        )
    } else remember { mutableFloatStateOf(0.18f) }
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val w = size.width
            val h = size.height
            val r = max(w, h)
            // Gradients are built once per size; each frame only moves them.
            val shifted = glowBrush(colors.glowShifted, r * 0.64f)
            val main = glowBrush(colors.glow, r * 0.62f)
            val counter = glowBrush(colors.glowCounter, r * 0.34f)
            val highlight = glowBrush(colors.highlight, r * 0.30f)
            val extra = accent?.let { glowBrush(it, r * 0.50f) }
            onDrawBehind {
                val a = phase.value * 2f * PI.toFloat()
                drawRect(colors.base)
                glowAt(shifted, w * (0.12f + 0.07f * sin(a)), h * (0.12f + 0.04f * cos(a)))
                glowAt(main, w * (0.58f + 0.07f * cos(a + 1.3f)), h * (0.70f + 0.05f * sin(a + 1.3f)))
                glowAt(counter, w * (1.0f + 0.03f * sin(a * 2f)), h * 0.98f)
                glowAt(highlight, w * (0.46f + 0.04f * sin(a + 2.1f)), h * (0.40f + 0.03f * cos(a + 2.1f)))
                extra?.let { glowAt(it, w * (0.88f + 0.04f * cos(a + 0.7f)), h * (0.10f + 0.03f * sin(a))) }
            }
        }
    )
}

private class Glow(val brush: Brush, val radius: Float)

private fun glowBrush(color: Color, radius: Float) = Glow(
    Brush.radialGradient(
        0f to color,
        0.45f to color.copy(alpha = color.alpha * 0.6f),
        1f to color.copy(alpha = 0f),
        center = Offset.Zero,
        radius = radius
    ),
    radius
)

private fun DrawScope.glowAt(glow: Glow, x: Float, y: Float) {
    translate(x, y) { drawCircle(glow.brush, glow.radius, Offset.Zero) }
}

/**
 * Paints the Dawn background once and hosts pages on top of it. Under Liquid Glass the
 * background is also recorded as the glass backdrop, so every Dawn control above it refracts
 * the glow. Nested stages (the Premium page inside the welcome flow) draw their own.
 */
@Composable
fun LumiDawnStage(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize()) {
        if (lumiDawnGlass()) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                CompositionLocalProvider(LocalLumiDawnBackdrop provides false) { LumiDawnBackground(accent = accent) }
            }
            ProvideLiquidGlassBackdrop(backdrop) {
                CompositionLocalProvider(LocalLumiDawnBackdrop provides true, content = content)
            }
        } else {
            CompositionLocalProvider(LocalLumiDawnBackdrop provides false) { LumiDawnBackground(accent = accent) }
            CompositionLocalProvider(LocalLumiDawnBackdrop provides true, content = content)
        }
    }
}

/**
 * A white "paper" card: generous radius, hairline edge and a very soft shadow. Under Liquid
 * Glass it becomes a glass panel over the stage's glow.
 */
@Composable
fun LumiPaperCard(
    modifier: Modifier = Modifier,
    radius: Dp = 30.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val colors = lumiDawnColors()
    val shape = RoundedCornerShape(radius)
    if (lumiDawnGlass()) {
        com.huangder.lumibooks.ui.components.LiquidGlassSurface(
            shape = shape,
            fallbackColor = colors.paper,
            modifier = modifier,
            role = com.huangder.lumibooks.ui.theme.AppSurfaceRole.Overlay,
            contentAlignment = Alignment.TopStart
        ) {
            // The glass surface does not clip its children; the card is a mask for its art.
            Box(Modifier.fillMaxSize().clip(shape), content = content)
        }
        return
    }
    val ink = LocalEInkMode.current
    Box(
        modifier
            .then(
                if (ink) Modifier else Modifier.shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = colors.accent.copy(alpha = 0.10f),
                    spotColor = colors.accent.copy(alpha = 0.14f)
                )
            )
            .clip(shape)
            .background(colors.paper)
            .border(1.dp, colors.hairline, shape),
        content = content
    )
}

/**
 * [Ink]: the page's primary action (solid, or accent-tinted glass under Liquid Glass).
 * [Frosted]: the original welcome material, a translucent white capsule with a bright hairline
 * (clear glass under Liquid Glass). [Quiet]: text only.
 */
enum class LumiDawnButtonStyle { Ink, Frosted, Quiet }

/** The capsule button. [LumiDawnButtonStyle.Ink] is the single primary action of a page. */
@Composable
fun LumiDawnButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LumiDawnButtonStyle = LumiDawnButtonStyle.Ink,
    enabled: Boolean = true
) {
    val colors = lumiDawnColors()
    val ink = style == LumiDawnButtonStyle.Ink
    val frosted = style == LumiDawnButtonStyle.Frosted
    val label: @Composable (Color) -> Unit = { color ->
        Text(
            text = text,
            color = color,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = if (style == LumiDawnButtonStyle.Quiet) FontWeight.Medium else FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (style != LumiDawnButtonStyle.Quiet && lumiDawnGlass()) {
        val content = if (ink) AppColors.OnAccent else colors.ink
        LiquidGlassButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 54.dp),
            enabled = enabled,
            shape = RoundedCornerShape(percent = 50),
            tintedColor = if (ink) AppColors.Accent else null,
            contentColor = content
        ) { label(content) }
        return
    }
    DawnPressable(onClick, enabled, modifier.heightIn(min = 54.dp), fill = when {
        ink -> colors.buttonFill
        frosted -> frostedFill()
        else -> Color.Transparent
    }, border = when {
        frosted -> frostedBorder()
        !ink && LocalEInkMode.current -> Color.Black
        else -> null
    }) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
            label(when {
                ink -> colors.buttonContent
                frosted -> colors.ink
                else -> colors.inkSecondary
            })
        }
    }
}

/**
 * The pager's forward control: a circle holding an arrow that stretches into a labelled
 * capsule ([label]) when [expanded]. The label is always its accessibility name.
 */
@Composable
fun LumiDawnArrowButton(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = lumiDawnColors()
    val motion = lumiDawnMotion()
    val width by animateDpAsState(
        targetValue = if (expanded) 148.dp else 58.dp,
        animationSpec = if (motion) tween(420, easing = AppEasing.Smooth) else tween(0),
        label = "dawnArrowWidth"
    )
    val glass = lumiDawnGlass()
    val content = if (glass) AppColors.OnAccent else colors.buttonContent
    val inner: @Composable RowScope.() -> Unit = {
        if (expanded) {
            Text(label, color = content, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        DawnArrow(content)
    }
    val sized = modifier
        .width(width)
        .height(58.dp)
        .semantics { contentDescription = label }
    if (glass) {
        LiquidGlassButton(
            onClick = onClick,
            modifier = sized,
            shape = RoundedCornerShape(percent = 50),
            tintedColor = AppColors.Accent,
            contentColor = content,
            content = inner
        )
    } else {
        DawnPressable(onClick, true, sized, fill = colors.buttonFill, border = null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = inner)
        }
    }
}

@Composable
private fun DawnArrow(color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        val y = size.height / 2f
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width, y), Offset(size.width - size.height * 0.5f, 0f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width, y), Offset(size.width - size.height * 0.5f, size.height), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun frostedFill(): Color = when {
    LocalEInkMode.current -> Color.White
    LocalIsDarkTheme.current -> Color(0xFF1C1F27).copy(alpha = 0.55f)
    else -> Color.White.copy(alpha = 0.48f)
}

@Composable
private fun frostedBorder(): Color = when {
    LocalEInkMode.current -> Color.Black
    LocalIsDarkTheme.current -> Color.White.copy(alpha = 0.22f)
    else -> Color.White.copy(alpha = 0.90f)
}

@Composable
private fun DawnPressable(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    fill: Color,
    border: Color?,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && lumiDawnMotion()) 0.97f else 1f,
        animationSpec = tween(160, easing = AppEasing.Decelerate),
        label = "lumiButtonPress"
    )
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(shape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * Short bars; the current one stretches and takes the accent. [position] is fractional
 * (page + offset) so the bars morph continuously while a pager is being dragged.
 */
@Composable
fun LumiPageIndicator(count: Int, position: Float, modifier: Modifier = Modifier) {
    val colors = lumiDawnColors()
    Canvas(modifier.width((count * 8 + (count - 1) * 6 + 18).dp).height(4.dp)) {
        val gap = 6.dp.toPx()
        val short = 8.dp.toPx()
        val long = 26.dp.toPx()
        var x = 0f
        repeat(count) { index ->
            val focus = (1f - abs(position - index)).coerceIn(0f, 1f)
            val width = short + (long - short) * focus
            val color = lerp(colors.inkTertiary.copy(alpha = 0.4f), colors.accent, focus)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(size.height / 2f)
            )
            x += width + gap
        }
    }
}

/**
 * Staged entrance: returns 0→1 for the [index]-th element, [stepMillis] apart. Without motion
 * it is 1 immediately. Restarts whenever [key] changes.
 */
@Composable
fun rememberLumiReveal(index: Int, key: Any? = Unit, baseDelayMillis: Long = 60L, stepMillis: Long = 90L): Float {
    val motion = lumiDawnMotion()
    val progress = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key, motion) {
        if (!motion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(baseDelayMillis + index * stepMillis)
        progress.animateTo(1f, tween(620, easing = AppEasing.Smooth))
    }
    return progress.value
}

/** Fade in and rise by [rise] as [progress] goes 0→1. */
fun Modifier.lumiReveal(progress: Float, rise: Dp = 14.dp): Modifier = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * rise.toPx()
}

/** Smoothstep of [value] between [from] and [to], for hand-timed looping line art. */
fun loopWindow(value: Float, from: Float, to: Float): Float {
    if (to <= from) return if (value >= to) 1f else 0f
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Mixes [overlay] at [fraction] over this colour (opaque result). */
internal fun Color.tint(overlay: Color, fraction: Float): Color =
    overlay.copy(alpha = fraction).compositeOver(this)

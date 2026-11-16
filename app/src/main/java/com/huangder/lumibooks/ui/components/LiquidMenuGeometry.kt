package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.ui.theme.AppShapes
import com.huangder.lumibooks.ui.theme.toAppPath
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** How an anchored liquid menu leaves the screen. */
enum class LiquidMenuCloseStyle {
    /** Retraces the opening in reverse back into the trigger (back key, toggles). */
    ReturnToSource,
    /** Shrinks into a small droplet and fades away; the trigger does not come back. */
    Vanish
}

/**
 * Staged droplet geometry for the anchored liquid menu.
 *
 * Opening: the whole trigger (also a wide capsule) is pressed, contracts into a near-round
 * droplet, inflates while travelling to the menu (circle, squircle, rounded rectangle) and
 * settles with a soft rebound. Returning plays the same path backwards (rounded rectangle,
 * round droplet, back into the trigger's own shape) and lands with a slight squash, so opening
 * and closing read as one symmetric exchange. Vanishing: the panel shrinks to a small droplet
 * and fades out. `closeMix` blends the opening path with the closing one so a reversal never
 * jumps.
 */
internal object LiquidMenuGeometry {
    val Motion = LiquidGlassMenuMotion(
        openStiffness = 100f, openDampingRatio = 0.72f,
        // Slightly softer than the opening and a little under-damped: the trigger lands with a
        // small squash (negative phase) and settles, instead of stopping dead.
        closeStiffness = 150f, closeDampingRatio = 0.7f
    )

    /** The pre-droplet morph, still used by the bottom panel and cascading submenus. */
    val LegacyMotion = LiquidGlassMenuMotion(
        openStiffness = 125f, openDampingRatio = 0.72f,
        closeStiffness = 155f, closeDampingRatio = 1f
    )

    /** Duration of the vanishing close; it never returns to the trigger, so no spring. */
    const val VanishMillis = 200

    /** How long a reversal takes to cross from one path to the other. */
    const val CloseMixMillis = 120

    private const val MaxOvershoot = 0.10f
    private const val PressSwell = 0.04f

    fun smooth(start: Float, end: Float, value: Float): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(closeMix: Float) = closeMix.coerceIn(0f, 1f)

    /** Menu rows surface inside the inflating droplet and vanish at once when closing. */
    fun contentAlpha(phase: Float, closeMix: Float = 0f): Float =
        lerp(smooth(0.3f, 0.8f, phase), smooth(0.84f, 0.97f, phase), mix(closeMix))

    /**
     * The trigger, squeezed into the droplet: fading out as it opens and, retraced, fading back
     * in as it returns. A vanishing menu never brings the trigger back.
     */
    fun squeezedSourceAlpha(
        phase: Float,
        style: LiquidMenuCloseStyle = LiquidMenuCloseStyle.ReturnToSource,
        closeMix: Float = 0f
    ): Float = (1f - smooth(0.04f, 0.24f, phase)) *
        (if (style == LiquidMenuCloseStyle.Vanish) 1f - mix(closeMix) else 1f)

    /** How strongly the squeezed trigger is smeared while the capsule contracts, 0..1. */
    fun sourceSmear(phase: Float): Float = smooth(0.02f, 0.2f, phase)

    fun frame(
        source: Rect,
        target: Rect,
        phase: Float,
        sourceRadius: Float,
        targetRadius: Float,
        closeStyle: LiquidMenuCloseStyle = LiquidMenuCloseStyle.ReturnToSource,
        closeMix: Float = 0f
    ): LiquidMenuFrame {
        val blend = mix(closeMix)
        if (blend <= 0f) return openFrame(source, target, phase, sourceRadius, targetRadius)
        val closing = when (closeStyle) {
            LiquidMenuCloseStyle.ReturnToSource -> returnFrame(source, target, phase, sourceRadius, targetRadius)
            LiquidMenuCloseStyle.Vanish -> vanishFrame(source, target, phase, targetRadius)
        }
        if (blend >= 1f) return closing
        return blendFrames(openFrame(source, target, phase, sourceRadius, targetRadius), closing, blend)
    }

    /** Diameter of the droplet a trigger contracts into; round buttons barely change. */
    fun blobDiameter(source: Rect, target: Rect): Float {
        val ceiling = minOf(source.width, target.width, target.height).coerceAtLeast(1f)
        val floor = min(source.width, source.height).coerceAtMost(ceiling)
        return (source.height * 1.4f).coerceAtMost(ceiling).coerceAtLeast(floor)
    }

    private fun openFrame(source: Rect, target: Rect, phase: Float, sourceRadius: Float, targetRadius: Float): LiquidMenuFrame {
        val t = phase.coerceIn(0f, 1f)
        val extra = (phase - 1f).coerceIn(0f, MaxOvershoot)
        // Zero slope at the join, so passing the target size has no velocity kink.
        val overshoot = extra * extra / (extra + 0.015f)
        val nearX = LiquidGlassMenuMorph.nearEdgeBiasX(source, target)
        val nearY = LiquidGlassMenuMorph.nearEdgeBiasY(source, target)
        val blob = blobDiameter(source, target)
        val swell = 1f + PressSwell * sin(PI.toFloat() * (t / 0.10f).coerceIn(0f, 1f))
        // 1. The whole trigger contracts into a near-round droplet.
        val contract = smooth(0.04f, 0.26f, t)
        var width = lerp(source.width, blob, contract)
        var height = lerp(source.height, blob * 1.06f, contract)
        // 2. It inflates as a slightly upright round droplet, up to most of the panel.
        val sphere = (min(target.width, target.height) * 0.92f).coerceAtLeast(blob)
        val inflate = smooth(0.2f, 0.74f, t)
        width = lerp(width, sphere, inflate)
        height = lerp(height, (sphere * 1.08f).coerceAtMost(target.height.coerceAtLeast(sphere)), inflate)
        // 3. It stretches into the panel's proportions while squaring off.
        val reshape = smooth(0.5f, 1f, t)
        width = ((lerp(width, target.width, reshape) + (target.width - blob).coerceAtLeast(0f) * overshoot) * swell)
            .coerceAtLeast(1f)
        height = ((lerp(height, target.height, reshape) + (target.height - blob).coerceAtLeast(0f) * overshoot) * swell)
            .coerceAtLeast(1f)
        val cx = lerp(source.center.x, target.center.x, smooth(0.04f, 0.8f, t) + overshoot)
        val cy = lerp(source.center.y, target.center.y, smooth(0.06f, 0.9f, t) + overshoot)
        val full = min(width, height) / 2f
        val radius = lerp(lerp(sourceRadius, full, smooth(0f, 0.2f, t)), targetRadius, smooth(0.6f, 0.96f, t))
            .coerceIn(0f, full)
        // Below zero (a returning spring passing the trigger) the capsule squashes slightly
        // around its centre and springs back: the landing of the reversed exchange.
        val squash = 1f + 1.2f * phase.coerceIn(-0.12f, 0f)
        width *= squash
        height *= 2f - squash
        return LiquidMenuFrame(
            bounds = Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f),
            radius = radius.coerceAtMost(min(width, height) / 2f),
            neck = 0.22f * smooth(0.12f, 0.3f, t) * (1f - smooth(0.45f, 0.75f, t)),
            nearX = nearX, nearY = nearY,
            distortion = smooth(0.15f, 0.35f, t) * (1f - smooth(0.7f, 1f, t)),
            highlight = smooth(0f, 0.03f, t) * (1f - smooth(0.08f, 0.26f, t))
        )
    }

    /** The opening path played backwards, without the press wash, landing with a squash. */
    private fun returnFrame(source: Rect, target: Rect, phase: Float, sourceRadius: Float, targetRadius: Float): LiquidMenuFrame =
        openFrame(source, target, phase, sourceRadius, targetRadius).copy(highlight = 0f)

    private fun vanishFrame(source: Rect, target: Rect, phase: Float, targetRadius: Float): LiquidMenuFrame {
        val t = phase.coerceIn(0f, 1f)
        val drop = (min(source.width, source.height) * 0.9f)
            .coerceAtMost(min(target.width, target.height)).coerceAtLeast(1f)
        val point = lerp(source.center, target.center, 0.3f)
        val s = smooth(0f, 1f, t)
        val width = lerp(drop, target.width, s.pow(1.15f)).coerceAtLeast(1f)
        val height = lerp(drop, target.height, s).coerceAtLeast(1f)
        val center = lerp(point, target.center, s)
        val full = min(width, height) / 2f
        return LiquidMenuFrame(
            bounds = Rect(center.x - width / 2f, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f),
            radius = lerp(full, targetRadius, smooth(0.4f, 0.9f, t)).coerceIn(0f, full),
            neck = 0f,
            nearX = LiquidGlassMenuMorph.nearEdgeBiasX(source, target),
            nearY = LiquidGlassMenuMorph.nearEdgeBiasY(source, target),
            distortion = 0.6f * smooth(0.3f, 0.6f, t) * (1f - smooth(0.8f, 1f, t)),
            alpha = smooth(0f, 0.5f, t)
        )
    }

    private fun blendFrames(open: LiquidMenuFrame, close: LiquidMenuFrame, mix: Float): LiquidMenuFrame {
        val bounds = Rect(
            lerp(open.bounds.left, close.bounds.left, mix), lerp(open.bounds.top, close.bounds.top, mix),
            lerp(open.bounds.right, close.bounds.right, mix), lerp(open.bounds.bottom, close.bounds.bottom, mix)
        )
        return LiquidMenuFrame(
            bounds = bounds,
            radius = lerp(open.radius, close.radius, mix).coerceIn(0f, bounds.minDimension / 2f),
            neck = lerp(open.neck, close.neck, mix),
            nearX = open.nearX, nearY = open.nearY,
            distortion = lerp(open.distortion, close.distortion, mix),
            highlight = lerp(open.highlight, close.highlight, mix),
            alpha = lerp(open.alpha, close.alpha, mix)
        )
    }

    // The pre-droplet morph: rect interpolation with a neck. Panel and submenus keep it.
    fun legacyContentAlpha(phase: Float): Float = smooth(0.28f, 0.88f, phase)
    fun legacySourceAlpha(phase: Float): Float = 1f - smooth(0.01f, 0.16f, phase)
    fun legacyDistortion(phase: Float): Float = smooth(0.08f, 0.3f, phase) * (1f - smooth(0.65f, 1f, phase))

    fun legacyFrame(source: Rect, target: Rect, phase: Float, sourceRadius: Float, targetRadius: Float): LiquidMenuFrame {
        val t = phase.coerceIn(0f, 1f)
        val growth = smooth(0f, 1f, t)
        val extra = (phase - 1f).coerceIn(0f, MaxOvershoot)
        val overshoot = extra * extra / (extra + 0.015f)
        val travel = growth + overshoot
        val nearX = LiquidGlassMenuMorph.nearEdgeBiasX(source, target)
        val nearY = LiquidGlassMenuMorph.nearEdgeBiasY(source, target)
        // Width follows the vertical stretch, then catches up with a soft rebound.
        val widthTravel = growth.pow(1.35f) + overshoot
        val width = lerp(source.width, target.width, widthTravel).coerceAtLeast(1f)
        val height = lerp(source.height, target.height, travel).coerceAtLeast(1f)
        val arc = sin(PI.toFloat() * t).pow(2) * min(target.height * 0.20f, source.height * 1.7f)
        val cx = lerp(source.center.x, target.center.x, widthTravel)
        val cy = lerp(source.center.y, target.center.y, travel) - nearY * arc
        val bounds = Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
        val roundness = smooth(0.38f, 0.98f, t)
        val radius = lerp(
            lerp(sourceRadius, min(width, height) / 2f, smooth(0f, 0.15f, t)),
            targetRadius, roundness
        ).coerceIn(0f, min(width, height) / 2f)
        val neck = 0.58f * smooth(0f, 0.16f, t) * (1f - smooth(0.28f, 0.88f, t))
        return LiquidMenuFrame(bounds, radius, neck, nearX, nearY, legacyDistortion(t))
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

/** Largest gap between the warped outline and its polyline, in pixels. */
private const val OutlineTolerancePx = 0.01f

/** Guards degenerate inputs; regular frames need far fewer steps. */
private const val MaxStepsPerSegment = 256

/** Segments needing more uniform steps than this are halved and stepped separately. */
private const val UniformStepLimit = 4
private const val MaxSegmentSplits = 8

internal data class LiquidMenuFrame(
    val bounds: Rect,
    val radius: Float,
    val neck: Float,
    val nearX: Float,
    val nearY: Float,
    val distortion: Float,
    /** White press wash over the trigger at the start of the opening. */
    val highlight: Float = 0f,
    /** Opacity of the whole container; only a vanishing droplet fades. */
    val alpha: Float = 1f
) {
    /** Also used in the shader: only the near end narrows, toward the source side. */
    fun warp(point: Offset, inverse: Boolean = false): Offset {
        val v = (point.y / bounds.height).coerceIn(0f, 1f)
        val near = if (nearY < 0f) 1f - v else v
        val pinch = neck * near * near * near
        val center = bounds.width / 2f
        val shift = nearX * center * pinch
        return Offset(
            if (inverse) center + (point.x - center - shift) / (1f - pinch)
            else center + (point.x - center) * (1f - pinch) + shift,
            point.y
        )
    }

    /**
     * Uniform steps needed so the warped [from]..[to] segment deviates from its polyline by at
     * most [OutlineTolerancePx]. Only x is warped, and along a straight segment it is a
     * polynomial in t whose second derivative is bounded here; a chord over a step of length h
     * then deviates by at most bound * h^2 / 8.
     */
    private fun warpedSteps(from: Offset, to: Offset): Int {
        if (neck == 0f || bounds.height <= 0f) return 1
        val dx = to.x - from.x
        val dy = (to.y - from.y) / bounds.height
        if (dy == 0f) return 1
        fun nearness(y: Float): Float {
            val v = (y / bounds.height).coerceIn(0f, 1f)
            return if (nearY < 0f) 1f - v else v
        }
        val near = maxOf(nearness(from.y), nearness(to.y))
        val center = bounds.width / 2f
        // x' = x - (x - c) * p + side * c * p with p = neck * near^3 and near linear in t.
        val slope = 3f * neck * near * near * abs(dy)
        val bend = 6f * neck * near * dy * dy
        val reach = maxOf(abs(from.x - center), abs(to.x - center)) + center
        val curvature = 2f * abs(dx) * slope + reach * bend
        if (curvature <= 0f) return 1
        return ceil(sqrt(curvature / (8f * OutlineTolerancePx))).toInt().coerceIn(1, MaxStepsPerSegment)
    }

    /**
     * Adds the warped start of [from]..[to] plus its interior steps. Long runs are halved first,
     * so each half is stepped by its own (smaller) bend instead of the worst case of the whole.
     */
    private fun addWarpedSegment(from: Offset, to: Offset, points: MutableList<Offset>, depth: Int = 0) {
        val steps = warpedSteps(from, to)
        if (steps > UniformStepLimit && depth < MaxSegmentSplits) {
            val middle = (from + to) / 2f
            addWarpedSegment(from, middle, points, depth + 1)
            addWarpedSegment(middle, to, points, depth + 1)
            return
        }
        for (step in 0 until steps) points.add(warp(from + (to - from) * (step / steps.toFloat())))
    }

    fun outlinePoints(continuous: Boolean = false): List<Offset> {
        if (continuous) {
            val outline = AppShapes.rounded(radius).createOutline(bounds.size, LayoutDirection.Ltr, Density(1f))
            // The corners keep the subpixel flattening of the rounded outline itself. Straight
            // runs only gain vertices where the neck actually bends them (see warpedSteps).
            val samples = outline.toAppPath().asAndroidPath().approximate(0.01f)
            val points = ArrayList<Offset>(samples.size / 3)
            for (i in 3 until samples.size step 3) {
                val from = Offset(samples[i - 2], samples[i - 1])
                val to = Offset(samples[i + 1], samples[i + 2])
                addWarpedSegment(from, to, points)
            }
            return points
        }
        val w = bounds.width
        val h = bounds.height
        val r = radius
        val points = ArrayList<Offset>(80)
        fun line(from: Offset, to: Offset) {
            repeat(8) { i ->
                val t = i / 8f
                points.add(warp(Offset(lerp(from.x, to.x, t), lerp(from.y, to.y, t))))
            }
        }
        fun corner(cx: Float, cy: Float, start: Float) {
            repeat(12) { i ->
                val angle = start + PI.toFloat() / 2f * i / 12f
                points.add(warp(Offset(cx + r * cos(angle), cy + r * sin(angle))))
            }
        }
        line(Offset(r, 0f), Offset(w - r, 0f))
        corner(w - r, r, -PI.toFloat() / 2f)
        line(Offset(w, r), Offset(w, h - r))
        corner(w - r, h - r, 0f)
        line(Offset(w - r, h), Offset(r, h))
        corner(r, h - r, PI.toFloat() / 2f)
        line(Offset(0f, h - r), Offset(0f, r))
        corner(r, r, PI.toFloat())
        return points
    }
}

/** The panel outline with the reversible neck warp also used by the optical shader. */
internal class LiquidMenuShape(
    private val frame: LiquidMenuFrame,
    private val continuous: Boolean = true
) : Shape {
    // One instance belongs to one animation frame, and its shadow, backdrop, content clip and
    // optical rim all ask for the same outline. Remember the last answer for repeat requests.
    private var lastRequest: Pair<Size, LayoutDirection>? = null
    private var lastOutline: Outline? = null

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val request = size to layoutDirection
        val reusable = lastOutline?.takeIf { request == lastRequest }
        if (reusable != null) return reusable
        val outline = buildOutline(size, layoutDirection)
        lastRequest = request
        lastOutline = outline
        return outline
    }

    private fun buildOutline(size: Size, layoutDirection: LayoutDirection): Outline {
        val scaleX = size.width / frame.bounds.width
        val scaleY = size.height / frame.bounds.height
        // The resting outline is exactly the same shape used by cards and panels.
        if (frame.neck == 0f) {
            val radius = (frame.radius * min(scaleX, scaleY)).coerceIn(0f, size.minDimension / 2f)
            return AppShapes.rounded(radius, continuous).createOutline(size, layoutDirection, Density(1f))
        }
        val points = frame.outlinePoints(continuous)
        fun point(i: Int): Offset {
            val p = points[(i + points.size) % points.size]
            return Offset(p.x * scaleX, p.y * scaleY)
        }
        return Outline.Generic(Path().apply {
            val start = point(0)
            moveTo(start.x, start.y)
            if (continuous) {
                for (i in 1 until points.size) {
                    val p = point(i)
                    lineTo(p.x, p.y)
                }
                close()
                return@apply
            }
            for (i in points.indices) {
                val a = point(i)
                val b = point(i + 1)
                val c1 = a + (b - point(i - 1)) / 6f
                val c2 = b - (point(i + 2) - a) / 6f
                cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
            }
            close()
        })
    }
}

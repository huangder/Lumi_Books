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
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A single, reversible phase drives the outline, source handoff and both optical layers. */
internal object LiquidMenuGeometry {
    val Motion = LiquidGlassMenuMotion(
        openStiffness = 125f, openDampingRatio = 0.72f,
        // A critically damped return slows into the button without crossing zero
        // and freezing at the clamped endpoint while the hidden spring settles.
        closeStiffness = 155f, closeDampingRatio = 1f
    )

    fun smooth(start: Float, end: Float, value: Float): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun contentAlpha(phase: Float): Float = smooth(0.28f, 0.88f, phase)
    fun sourceAlpha(phase: Float): Float = 1f - smooth(0.01f, 0.16f, phase)
    fun distortion(phase: Float): Float = smooth(0.08f, 0.3f, phase) * (1f - smooth(0.65f, 1f, phase))

    fun frame(source: Rect, target: Rect, phase: Float, sourceRadius: Float, targetRadius: Float): LiquidMenuFrame {
        val t = phase.coerceIn(0f, 1f)
        val growth = smooth(0f, 1f, t)
        // Match the zero slope of smoothstep at the join. A linear overshoot
        // creates a visible velocity kink as the panel passes its final size.
        val extra = (phase - 1f).coerceIn(0f, 0.10f)
        val overshoot = extra * extra / (extra + 0.015f)
        val travel = growth + overshoot
        val nearX = LiquidGlassMenuMorph.nearEdgeBiasX(source, target)
        val nearY = LiquidGlassMenuMorph.nearEdgeBiasY(source, target)
        // Width follows the vertical stretch, then catches up with a soft rebound.
        val widthTravel = growth.pow(1.35f) + overshoot
        val width = lerp(source.width, target.width, widthTravel).coerceAtLeast(1f)
        val height = lerp(source.height, target.height, travel).coerceAtLeast(1f)
        // The body briefly travels away from the trigger before opening back toward it.
        // Reversing this exact curve creates the returning droplet without a direction jump.
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
        return LiquidMenuFrame(bounds, radius, neck, nearX, nearY, distortion(t))
    }
}

internal data class LiquidMenuFrame(
    val bounds: Rect,
    val radius: Float,
    val neck: Float,
    val nearX: Float,
    val nearY: Float,
    val distortion: Float
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

    fun outlinePoints(continuous: Boolean = false): List<Offset> {
        if (continuous) {
            val outline = AppShapes.rounded(radius).createOutline(bounds.size, LayoutDirection.Ltr, Density(1f))
            // PathMeasure's default flattening can shift a corner by ~0.2px at
            // the final handoff. Use an explicit subpixel error, and subdivide
            // straight edges too because the neck warp is nonlinear in y.
            val samples = outline.toAppPath().asAndroidPath().approximate(0.01f)
            val points = ArrayList<Offset>()
            for (i in 3 until samples.size step 3) {
                val a = Offset(samples[i - 2], samples[i - 1])
                val b = Offset(samples[i + 1], samples[i + 2])
                val steps = ceil((b - a).getDistance() / 2f).toInt().coerceAtLeast(1)
                repeat(steps) { step -> points.add(warp(a + (b - a) * (step.toFloat() / steps))) }
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
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
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

package com.huangder.lumibooks.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Optical rim widths, independent of the control's length and corner radius. */
internal val ControlEdgeDarkWidth = 0.4.dp
internal val ControlEdgeLightWidth = 0.5.dp
private val ControlEdgeReflectionWidth = 1.3.dp

/** Internal overrides for side-by-side debug fixtures; never persisted as user preferences. */
internal val LocalLiquidGlassControlEdgeEnabled = staticCompositionLocalOf { true }
internal val LocalLiquidGlassControlEdgeForceCanvas = staticCompositionLocalOf { false }

internal data class ControlEdgePalette(
    val dark: Color,
    val light: Color,
    val darkStrength: Float,
    val lightStrength: Float
)

internal fun controlEdgePalette(material: Color, isDark: Boolean): ControlEdgePalette {
    val base = material.copy(alpha = 1f)
    val chroma = maxOf(base.red, base.green, base.blue) - minOf(base.red, base.green, base.blue)
    val colored = chroma > 0.08f
    val darkSurface = isDark || base.luminance() < 0.15f
    return ControlEdgePalette(
        dark = if (colored) lerp(base, Color.Black, 0.55f) else Color.Black,
        light = if (colored) lerp(base, Color.White, 0.60f) else Color.White,
        darkStrength = if (darkSurface) 0.55f else 1f,
        lightStrength = if (darkSurface) 0.64f else 1f
    )
}

/** Same directional response is used by the shader and the Canvas fallback. */
internal fun controlEdgeDarkAlpha(normalX: Float): Float = 0.08f + 0.19f * normalX * normalX

internal fun controlEdgeLightAlpha(normalX: Float, normalY: Float): Float {
    val facing = max(0f, normalX * 0.55f - normalY * 0.8351647f)
    val square = facing * facing
    val fourth = square * square
    return 0.12f + 0.52f * normalY * normalY + 0.08f * fourth * fourth
}

internal fun controlEdgeUsesShader(sdk: Int, outline: Outline): Boolean =
    sdk >= 33 && outline is Outline.Rounded && outline.roundRect.hasUniformCircularCorners()

private fun RoundRect.hasUniformCircularCorners(): Boolean =
    topLeftCornerRadius == topRightCornerRadius && topLeftCornerRadius == bottomLeftCornerRadius &&
        topLeftCornerRadius == bottomRightCornerRadius && topLeftCornerRadius.x == topLeftCornerRadius.y

/**
 * Draw inside the actual outline, after the surface tint and touch light. This modifier
 * must be inside the backdrop's layerBlock so the rim follows the same deformation.
 * All paths, gradients and the RuntimeShader are cached; interaction does not rebuild them.
 */
internal fun Modifier.liquidGlassControlEdge(
    shape: Shape,
    material: Color,
    isDark: Boolean,
    // Used by the debug comparison fixture to exercise Android 12 rendering on newer devices.
    forceCanvas: Boolean = false
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val palette = controlEdgePalette(material, isDark)
    val darkWidth = ControlEdgeDarkWidth.toPx()
    val lightWidth = ControlEdgeLightWidth.toPx()
    val reflectionWidth = ControlEdgeReflectionWidth.toPx()
    val outerPath = outline.edgePath()
    val shader = if (!forceCanvas && controlEdgeUsesShader(Build.VERSION.SDK_INT, outline)) {
        createControlEdgeShader(
            (outline as Outline.Rounded).roundRect, palette,
            darkWidth, lightWidth, reflectionWidth
        )
    } else null
    // A normally accelerated View may also be drawn into a software screenshot bitmap.
    // Defer the fallback geometry until needed, retaining it for subsequent software draws.
    val canvasRings by lazy(LazyThreadSafetyMode.NONE) {
        buildControlEdgeRings(outerPath, outline, palette, darkWidth, lightWidth, reflectionWidth)
    }
    onDrawWithContent {
        drawContent()
        if (size.minDimension > 0f) {
            if (shader != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) drawPath(outerPath, shader)
            else canvasRings.forEach { ring -> ring.draw(this) }
        }
    }
}

private fun Outline.edgePath(): Path = when (this) {
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Generic -> path
}

private data class ControlEdgeRing(
    val path: Path,
    val outerClip: Path,
    val innerClip: Path?,
    val patches: List<EdgePatch>
) {
    fun draw(scope: DrawScope) = with(scope) {
        // Clip at draw time instead of subtracting stroked paths. Path.combine
        // can fail on valid, tightly curved menu outlines during the return morph.
        clipPath(outerClip) {
            if (innerClip == null) drawPatches(this)
            else clipPath(innerClip, ClipOp.Difference) { drawPatches(this) }
        }
    }

    private fun drawPatches(scope: DrawScope) = with(scope) {
        patches.forEach { patch ->
            clipRect(patch.bounds.left, patch.bounds.top, patch.bounds.right, patch.bounds.bottom) {
                drawPath(path, patch.brush)
            }
        }
    }
}

private data class EdgePatch(val bounds: Rect, val brush: Brush)

private fun buildControlEdgeRings(
    outerPath: Path,
    outline: Outline,
    palette: ControlEdgePalette,
    darkWidth: Float,
    lightWidth: Float,
    reflectionWidth: Float
): List<ControlEdgeRing> {
    // Paint's stroked fill follows caller-supplied paths; Canvas clips each band
    // to the inside of the outline and outside the preceding band.
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    fun rim(depth: Float): Path {
        val stroke = android.graphics.Path()
        paint.strokeWidth = depth * 2f
        paint.getFillPath(outerPath.asAndroidPath(), stroke)
        return stroke.asComposePath()
    }
    var previous: Path? = null
    fun ring(end: Float, color: Color, alpha: (Float, Float) -> Float): ControlEdgeRing {
        val filled = rim(end)
        val band = ControlEdgeRing(filled, outerPath, previous, edgePatches(outline, color, alpha))
        previous = filled
        return band
    }
    val rings = mutableListOf(
        ring(darkWidth, palette.dark) { nx, _ -> controlEdgeDarkAlpha(nx) * palette.darkStrength },
        ring(darkWidth + lightWidth, palette.light) { nx, ny ->
            controlEdgeLightAlpha(nx, ny) * palette.lightStrength
        }
    )
    // Three faint bands approximate the inward fade without a new blur/offscreen layer.
    repeat(3) { index ->
        val end = darkWidth + lightWidth + reflectionWidth * (index + 1) / 3f
        rings += ring(end, palette.light) { _, ny ->
            0.055f * (1f - (index + 0.5f) / 3f) * (0.4f + 0.6f * ny * ny) * palette.lightStrength
        }
    }
    return rings
}

private fun edgePatches(outline: Outline, color: Color, alpha: (Float, Float) -> Float): List<EdgePatch> {
    fun solid(nx: Float, ny: Float) = SolidColor(color.copy(alpha = alpha(nx, ny)))
    fun sweep(center: Offset): Brush = Brush.sweepGradient(
        colors = (0..64).map { index ->
            val angle = 2.0 * PI * index / 64.0
            color.copy(alpha = alpha(cos(angle).toFloat(), sin(angle).toFloat()))
        },
        center = center
    )
    val rounded = (outline as? Outline.Rounded)?.roundRect
    if (rounded == null || !rounded.hasUniformCircularCorners()) {
        val bounds = outline.bounds
        // Retain the real path for unusual custom button shapes; never substitute a capsule.
        return listOf(EdgePatch(bounds, sweep(bounds.center)))
    }
    val r = rounded.topLeftCornerRadius.x.coerceAtMost(minOf(rounded.width, rounded.height) / 2f)
    val left = rounded.left
    val top = rounded.top
    val right = rounded.right
    val bottom = rounded.bottom
    val patches = mutableListOf<EdgePatch>()
    fun patch(l: Float, t: Float, r: Float, b: Float, brush: Brush) {
        if (r > l && b > t) patches += EdgePatch(Rect(l, t, r, b), brush)
    }
    patch(left, top, left + r, top + r, sweep(Offset(left + r, top + r)))
    patch(right - r, top, right, top + r, sweep(Offset(right - r, top + r)))
    patch(left, bottom - r, left + r, bottom, sweep(Offset(left + r, bottom - r)))
    patch(right - r, bottom - r, right, bottom, sweep(Offset(right - r, bottom - r)))
    patch(left + r, top, right - r, (top + bottom) / 2f, solid(0f, -1f))
    patch(left + r, (top + bottom) / 2f, right - r, bottom, solid(0f, 1f))
    patch(left, top + r, left + r, bottom - r, solid(-1f, 0f))
    patch(right - r, top + r, right, bottom - r, solid(1f, 0f))
    return patches
}

@RequiresApi(33)
private fun createControlEdgeShader(
    rect: RoundRect,
    palette: ControlEdgePalette,
    darkWidth: Float,
    lightWidth: Float,
    reflectionWidth: Float
): Brush = ShaderBrush(RuntimeShader(ControlEdgeShader).apply {
    setFloatUniform("bounds", rect.left, rect.top, rect.width, rect.height)
    setFloatUniform("radius", rect.topLeftCornerRadius.x.coerceAtMost(minOf(rect.width, rect.height) / 2f))
    setFloatUniform("widths", darkWidth, lightWidth, reflectionWidth)
    setColorUniform("darkColor", palette.dark.toArgb())
    setColorUniform("lightColor", palette.light.toArgb())
    setFloatUniform("strength", palette.darkStrength, palette.lightStrength)
})

private const val ControlEdgeShader = """
uniform float4 bounds;
uniform float radius;
uniform float3 widths;
layout(color) uniform half4 darkColor;
layout(color) uniform half4 lightColor;
uniform float2 strength;

float band(float depth, float start, float end) {
    return max(0.0, smoothstep(start - 0.45, start + 0.45, depth)
        - smoothstep(end - 0.45, end + 0.45, depth));
}

half4 main(float2 coord) {
    float2 p = coord - bounds.xy - bounds.zw * 0.5;
    float2 q = abs(p) - (bounds.zw * 0.5 - radius);
    float2 outside = max(q, float2(0.0));
    float len = length(outside);
    float depth = -(len + min(max(q.x, q.y), 0.0) - radius);
    if (depth > widths.x + widths.y + widths.z + 0.5) return half4(0.0);
    float2 n = len > 0.0001 ? outside / max(len, 0.0001) * sign(p)
        : (q.x > q.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y)));
    float dark = (0.08 + 0.19 * n.x * n.x) * strength.x * band(depth, 0.0, widths.x);
    float facing = max(0.0, n.x * 0.55 - n.y * 0.8351647);
    float bright = (0.12 + 0.52 * n.y * n.y + 0.08 * pow(facing, 8.0)) * strength.y
        * band(depth, widths.x, widths.x + widths.y);
    float fade = clamp(1.0 - (depth - widths.x - widths.y) / widths.z, 0.0, 1.0);
    float reflection = 0.055 * fade * (0.4 + 0.6 * n.y * n.y) * strength.y
        * smoothstep(widths.x + widths.y - 0.45, widths.x + widths.y + 0.45, depth);
    float light = bright + reflection * (1.0 - bright);
    return half4(lightColor.rgb * light + darkColor.rgb * dark * (1.0 - light),
        light + dark * (1.0 - light));
}
"""

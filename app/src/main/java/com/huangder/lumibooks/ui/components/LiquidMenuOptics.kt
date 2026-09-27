package com.huangder.lumibooks.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.vibrancy

/** Scoped to the menu so arbitrary paths never reach Backdrop's rounded-rectangle lens. */
@Composable
internal fun LiquidMenuSurface(
    frame: LiquidMenuFrame,
    shape: Shape,
    backdrop: Backdrop?,
    baseColor: Color,
    isDark: Boolean,
    transparency: Float,
    modifier: Modifier = Modifier
) {
    val optics = rememberMenuOptics()
    val edge = if (LocalLiquidGlassControlEdgeEnabled.current) {
        Modifier.liquidGlassControlEdge(
            shape, baseColor, isDark, LocalLiquidGlassControlEdgeForceCanvas.current
        )
    } else {
        Modifier.border(
            0.5.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.10f))),
            shape
        )
    }
    // Like sheet containers, derive frosting from the user's transparency with
    // a readability offset. Menus use 15 percentage points (70% becomes 55%).
    val menuTransparency = (transparency - 0.15f).coerceIn(0f, 0.85f)
    // A light veil above the sampled backdrop softens background lettering while
    // preserving the glass tint. Foreground menu text is drawn after this layer.
    val scrim = baseColor.copy(alpha = if (isDark) 0.36f else 0.28f)
    val surface = if (backdrop != null) {
        Modifier.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur((6.dp * (1f - menuTransparency)).toPx())
                if (Build.VERSION.SDK_INT >= 33) {
                    optics?.effect(frame, padding, density, content = false)?.let { effect(it) }
                }
            },
            onDrawSurface = { drawRect(scrim) }
        )
    } else {
        Modifier.background(baseColor.copy(alpha = 0.92f))
    }
    Box(
        modifier.shadow(
            16.dp, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.30f else 0.16f)
        ).clip(shape).then(surface).then(edge)
    )
}

@Composable
internal fun rememberMenuOptics(): LiquidMenuOptics? = remember {
    if (Build.VERSION.SDK_INT >= 33) runCatching { LiquidMenuOptics() }
        .onFailure { Log.w("LiquidMenuOptics", "Menu refraction unavailable; keeping shape and blur", it) }
        .getOrNull() else null
}

@RequiresApi(33)
internal class LiquidMenuOptics {
    private val shader = RuntimeShader(MenuLensShader)

    fun effect(frame: LiquidMenuFrame, padding: Float, density: Float, content: Boolean): RenderEffect {
        shader.setFloatUniform("size", frame.bounds.width, frame.bounds.height)
        shader.setFloatUniform("padding", padding)
        shader.setFloatUniform("radius", frame.radius)
        shader.setFloatUniform("neck", frame.neck)
        shader.setFloatUniform("side", frame.nearX, frame.nearY)
        shader.setFloatUniform("band", (if (content) 28f else 16f + 4f * frame.distortion) * density)
        shader.setFloatUniform("amount", (if (content) 17f * frame.distortion else 24f + 9f * frame.distortion) * density)
        shader.setFloatUniform("warpContent", if (content) frame.distortion else 0f)
        shader.setFloatUniform("surfaceLens", if (content) 0f else 1f)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    fun contentEffect(frame: LiquidMenuFrame, density: Float): androidx.compose.ui.graphics.RenderEffect? =
        if (frame.distortion < 0.001f) null else effect(frame, 0f, density, true).asComposeRenderEffect()
}

// Inverse of LiquidMenuFrame.warp. The distance gradient follows the droplet neck,
// so the refraction turns with the outline instead of revealing a rectangular lens.
private const val MenuLensShader = """
uniform shader content;
uniform float2 size;
uniform float padding;
uniform float radius;
uniform float neck;
uniform float2 side;
uniform float band;
uniform float amount;
uniform float warpContent;
uniform float surfaceLens;

float2 unwarp(float2 p) {
    float v = clamp(p.y / size.y, 0.0, 1.0);
    float near = side.y < 0.0 ? 1.0 - v : v;
    float pinch = neck * near * near * near;
    float center = size.x * 0.5;
    return float2(center + (p.x - center - side.x * center * pinch) / (1.0 - pinch), p.y);
}
float distanceToEdge(float2 p) {
    float2 q = abs(unwarp(p) - size * 0.5) - (size * 0.5 - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
half4 main(float2 coord) {
    float2 p = coord - padding;
    float d = distanceToEdge(p);
    float2 gradient = float2(
        distanceToEdge(p + float2(0.75, 0.0)) - distanceToEdge(p - float2(0.75, 0.0)),
        distanceToEdge(p + float2(0.0, 0.75)) - distanceToEdge(p - float2(0.0, 0.75))
    );
    float gradientLength = max(length(gradient), 0.001);
    float2 normal = gradient / gradientLength;
    // Correct the distance for the narrowing neck so the lens keeps a stable
    // thickness as the outline stretches. At rest this is the rounded-rect SDF.
    float depth = max(-d * 1.5 / gradientLength, 0.0);
    float edge = 1.0 - smoothstep(0.0, band, depth);
    // A circular cross-section produces the pronounced compression at the rim
    // used by the other liquid-glass controls. It remains present when settled.
    float rim = clamp(1.0 - depth / band, 0.0, 1.0);
    float lens = 1.0 - sqrt(max(1.0 - rim * rim, 0.0));
    float displacement = mix(edge * edge, lens, surfaceLens);
    float2 samplePoint = mix(p, unwarp(p), warpContent * 0.65) - normal * amount * displacement;
    return content.eval(samplePoint + padding);
}
"""

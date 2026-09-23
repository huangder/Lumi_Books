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
    val scrim = baseColor.copy(alpha = if (isDark) 0.48f else 0.36f)
    val surface = if (backdrop != null) {
        Modifier.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur((12.dp * (1f - transparency * 0.5f)).toPx())
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
        ).clip(shape).then(surface).border(
            0.5.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.10f))),
            shape
        )
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
        shader.setFloatUniform("band", (if (content) 28f else 14f) * density)
        shader.setFloatUniform("amount", (if (content) 17f * frame.distortion else 6f + 9f * frame.distortion) * density)
        shader.setFloatUniform("warpContent", if (content) frame.distortion else 0f)
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
    float2 normal = gradient / max(length(gradient), 0.001);
    float edge = 1.0 - smoothstep(0.0, band, max(-d, 0.0));
    float2 samplePoint = mix(p, unwarp(p), warpContent * 0.65) - normal * amount * edge * edge;
    return content.eval(samplePoint + padding);
}
"""

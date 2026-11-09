package com.huangder.lumibooks.ui.welcome

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import kotlin.math.pow

internal enum class WelcomeShockwaveTier {
    FULL,
    BALANCED,
    LIGHT,
    HALO_ONLY
}

internal fun welcomeShockwaveTier(model: String): WelcomeShockwaveTier {
    val match = Regex("SM-A(\\d{2})", RegexOption.IGNORE_CASE).find(model) ?: return WelcomeShockwaveTier.FULL
    val generation = match.groupValues[1].toIntOrNull() ?: return WelcomeShockwaveTier.FULL
    return when {
        generation >= 16 -> WelcomeShockwaveTier.FULL
        generation == 15 -> WelcomeShockwaveTier.BALANCED
        generation >= 13 -> WelcomeShockwaveTier.LIGHT
        else -> WelcomeShockwaveTier.HALO_ONLY
    }
}

@Composable
internal fun WelcomeShockwave(
    progress: Float,
    isDark: Boolean,
    tier: WelcomeShockwaveTier,
    samplingQuality: LiquidGlassSamplingQuality,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxSize()) {
        val bandHeight = 110.dp.toPx()
        val centerY = if (reducedMotion) size.height * 0.5f else size.height * (1f - progress)
        val strength = if (reducedMotion) {
            (1f - kotlin.math.abs(progress * 2f - 1f)) * 0.11f
        } else {
            (minOf(1f, progress / 0.03f) * (1f - progress.pow(2.2f)).coerceAtLeast(0f)) *
                if (tier == WelcomeShockwaveTier.HALO_ONLY || samplingQuality == LiquidGlassSamplingQuality.PERFORMANCE) 0.16f else 0.26f
        }
        if (strength <= 0f) return@Canvas
        val aurora = if (isDark) DarkAurora else LightAurora
        val top = centerY - bandHeight * 0.5f
        val bottom = centerY + bandHeight * 0.5f
        val brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.17f to aurora[0].copy(alpha = strength * 0.34f),
                0.39f to aurora[1].copy(alpha = strength * 0.66f),
                0.61f to aurora[2].copy(alpha = strength * 0.66f),
                0.83f to aurora[3].copy(alpha = strength * 0.34f),
                1f to Color.Transparent
            ),
            startY = top,
            endY = bottom
        )
        drawRect(brush = brush)
    }
}

@Composable
internal fun Modifier.welcomeShockwavePageEffect(
    progress: Float,
    isDark: Boolean,
    tier: WelcomeShockwaveTier,
    reducedMotion: Boolean,
    samplingQuality: LiquidGlassSamplingQuality,
    bandHeightPx: Float
): Modifier {
    if (Build.VERSION.SDK_INT < 33 || reducedMotion || tier == WelcomeShockwaveTier.HALO_ONLY) return this
    val shader = androidx.compose.runtime.remember {
        runCatching { RuntimeShader(WelcomeShockwaveShader.SOURCE) }.getOrNull()
    } ?: return this
    val effect = runCatching {
        shader.setFloatUniform("progress", progress.coerceIn(0f, 1f))
        shader.setFloatUniform("darkMode", if (isDark) 1f else 0f)
        shader.setFloatUniform("band", bandHeightPx.coerceAtLeast(1f))
        val bend = when {
            samplingQuality == LiquidGlassSamplingQuality.PERFORMANCE -> 0f
            tier == WelcomeShockwaveTier.FULL -> 1f
            tier == WelcomeShockwaveTier.BALANCED -> 0.55f
            else -> 0f
        }
        shader.setFloatUniform("bend", bend)
        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }.getOrNull() ?: return this
    return graphicsLayer {
        shader.setFloatUniform("size", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        renderEffect = effect
    }
}

@RequiresApi(33)
private object WelcomeShockwaveShader {
    const val SOURCE = """
        uniform shader content;
        uniform float2 size;
        uniform float progress;
        uniform float darkMode;
        uniform float band;
        uniform float bend;

        float3 aurora(float u) {
            float3 c0 = darkMode > 0.5 ? float3(0.314, 0.549, 1.0) : float3(0.431, 0.588, 1.0);
            float3 c1 = darkMode > 0.5 ? float3(0.627, 0.431, 1.0) : float3(0.706, 0.510, 1.0);
            float3 c2 = darkMode > 0.5 ? float3(1.0, 0.431, 0.706) : float3(1.0, 0.510, 0.745);
            float3 c3 = darkMode > 0.5 ? float3(1.0, 0.745, 0.549) : float3(1.0, 0.706, 0.471);
            return u < 0.333
                ? mix(c0, c1, smoothstep(0.0, 0.333, u))
                : u < 0.666
                    ? mix(c1, c2, smoothstep(0.333, 0.666, u))
                    : mix(c2, c3, smoothstep(0.666, 1.0, u));
        }

        half4 main(float2 p) {
            float front = size.y * (1.0 - progress);
            float distance = p.y - front;
            float x = distance / max(band * 0.5, 1.0);
            float strength = min(1.0, progress / 0.03) * (1.0 - pow(progress, 2.2));
            float z = x * 0.75;
            float push = bend * 16.31 * z * exp(-z * z) * strength;
            half4 page = content.eval(float2(p.x, p.y - push));
            float ringAlpha = strength * exp(-1.1 * pow(x / 2.0, 2.0));
            float3 ring = aurora(clamp(x * 0.5 + 0.5, 0.0, 1.0));
            float tint = ringAlpha * 0.24;
            float3 color = darkMode > 0.5
                ? 1.0 - (1.0 - page.rgb) * (1.0 - ring * tint * 1.5)
                : mix(page.rgb, ring, tint);
            return half4(color, page.a);
        }
    """
}

private val DarkAurora = listOf(
    Color(0xFF508CFF), Color(0xFFA06EFF), Color(0xFFFF6EB4), Color(0xFFFFBE8C)
)
private val LightAurora = listOf(
    Color(0xFF6E96FF), Color(0xFFB482FF), Color(0xFFFF82BE), Color(0xFFFFB478)
)

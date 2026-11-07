// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
//
// Adapted from compose-miuix-ui/miuix at 4873fc7392871cd6cf3ea263020f5c7032b3de2a:
// miuix-blur ProgressiveBlur, internal/BlurEffect, RenderEffectCompat.android and Shaders.
// Lumi adaptation: full-resolution Android stack for the existing Backdrop capture API;
// no post effects, downsampling, multiplatform wrappers or separate sharp overlay.

package com.kyant.backdrop.effects

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import kotlin.math.ceil

/** Direction of the radius falloff; content coordinates are never flipped. */
@Immutable
enum class ProgressiveBlurDirection { Top, Bottom, Left, Right }

/** Upstream's smoothstep gradient, shared with the blur-free surface tint. */
fun progressiveBlurFraction(fraction: Float): Float {
    if (!fraction.isFinite()) return 1f
    val x = fraction.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Miuix's Android progressive composite, using its native full-resolution sharp end (exp == 0).
 * One opaque level stack replaces separate translucent composables, so sharp content cannot
 * leak through intermediate radii. Construct only after checking API 33 and hardware rendering.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MiuixProgressiveBlurEffect {
    private val mask = RuntimeShader(PROGRESSIVE_LEVEL_MASK_SHADER)
    private val unpremul = RenderEffect.createRuntimeShaderEffect(RuntimeShader(UNPREMUL_SHADER), "child")

    private data class Geometry(
        val width: Float, val height: Float, val padding: Float,
        val direction: ProgressiveBlurDirection, val startFraction: Float
    )
    private var geometry: Geometry? = null
    private var heavyMask: RenderEffect? = null
    private var mediumMask: RenderEffect? = null
    private var sharpMask: RenderEffect? = null
    private var cachedRadius = Float.NaN
    private var cachedEffect: RenderEffect? = null

    /** Fail closed to a color gradient if the renderer rejects the effect chain. */
    var failed: Boolean = false
        private set

    fun effect(
        radius: Float,
        width: Float,
        height: Float,
        padding: Float,
        direction: ProgressiveBlurDirection,
        startFraction: Float = 0f,
    ): RenderEffect? {
        if (failed || width <= 0f || height <= 0f) return null
        return try {
            val start = if (startFraction.isFinite()) startFraction.coerceIn(0f, 0.99f) else 0f
            build(radius, Geometry(width, height, padding, direction, start))
        } catch (_: IllegalArgumentException) {
            failed = true
            cachedEffect = null
            null
        } catch (_: IllegalStateException) {
            failed = true
            cachedEffect = null
            null
        }
    }

    private fun build(radius: Float, next: Geometry): RenderEffect {
        if (geometry != next) {
            val (ax, ay) = when (next.direction) {
                ProgressiveBlurDirection.Top -> 0f to 1f
                ProgressiveBlurDirection.Bottom -> 0f to -1f
                ProgressiveBlurDirection.Left -> 1f to 0f
                ProgressiveBlurDirection.Right -> -1f to 0f
            }
            val projMin = minOf(0f, ax * next.width) + minOf(0f, ay * next.height)
            val projMax = maxOf(0f, ax * next.width) + maxOf(0f, ay * next.height)
            val padProj = next.padding * (ax + ay)
            mask.setFloatUniform("in_gradAxis", ax, ay)
            mask.setFloatUniform("in_gradBand", projMin + (projMax - projMin) * next.startFraction + padProj, projMax + padProj)
            mask.setFloatUniform("in_curve", 1f)
            // Android snapshots shader uniforms when creating each RenderEffect.
            fun band(level: Float, slope: Float): RenderEffect {
                mask.setFloatUniform("in_level", level)
                mask.setFloatUniform("in_slope", slope)
                return RenderEffect.createRuntimeShaderEffect(mask, "child")
            }
            heavyMask = band(1f, 3f)
            mediumMask = band(2f, 3f)
            sharpMask = band(-2f, -3f)
            geometry = next
            cachedEffect = null
        }
        cachedEffect?.let { if (cachedRadius == radius) return it }

        // Upstream radius -> sigma, level sigmas 1 / .4 / .13, then Android sigma -> radius.
        fun blurred(ratio: Float): RenderEffect? {
            val sigma = radius.coerceAtLeast(0f) * 0.45f * ratio
            val androidRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)
            return if (androidRadius > 0f) {
                RenderEffect.createBlurEffect(androidRadius, androidRadius, Shader.TileMode.CLAMP)
            } else null
        }
        fun masked(ratio: Float, band: RenderEffect): RenderEffect =
            blurred(ratio)?.let { RenderEffect.createChainEffect(band, it) } ?: band

        val base = blurred(0.13f)
        var stack = base?.let { RenderEffect.createChainEffect(unpremul, it) }
            ?: RenderEffect.createOffsetEffect(0f, 0f)
        stack = RenderEffect.createBlendModeEffect(stack, masked(0.4f, mediumMask!!), BlendMode.SRC_OVER)
        stack = RenderEffect.createBlendModeEffect(stack, masked(1f, heavyMask!!), BlendMode.SRC_OVER)
        // Original source at full resolution: the clear edge stays pixel-sharp.
        stack = RenderEffect.createBlendModeEffect(stack, sharpMask!!, BlendMode.SRC_OVER)
        cachedRadius = radius
        cachedEffect = stack
        return stack
    }

    companion object {
        /** Three sigma of the strongest level (upstream sigma = radius * .45). */
        fun padding(radius: Float): Float = ceil(radius.coerceAtLeast(0f) * 1.35f)
    }
}

private const val UNPREMUL_SHADER = """
    uniform shader child;

    half4 main(float2 xy) {
        half4 c = child.eval(xy);
        if (c.a > 0.0039) {
            return half4(c.rgb / c.a, 1.0);
        }
        return c;
    }
"""

private const val PROGRESSIVE_LEVEL_MASK_SHADER = """
    uniform shader child;
    uniform float2 in_gradAxis;
    uniform float2 in_gradBand;
    uniform float in_curve;
    uniform float in_level;
    uniform float in_slope;

    half4 main(float2 xy) {
        float p = dot(xy, in_gradAxis);
        float raw = clamp((p - in_gradBand.x) / (in_gradBand.y - in_gradBand.x), 0.0, 1.0);
        raw = pow(raw * raw * (3.0 - 2.0 * raw), in_curve);
        float w = clamp(in_level - raw * in_slope, 0.0, 1.0);
        w = w * w * (3.0 - 2.0 * w);
        half4 c = child.eval(xy);
        if (c.a > 0.0039) {
            c = half4(c.rgb / c.a, 1.0);
        }
        return c * half(w);
    }
"""


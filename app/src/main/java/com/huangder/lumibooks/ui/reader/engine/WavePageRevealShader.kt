package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class WavePageRevealShader(bitmap: Bitmap) {
    private val input = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val shader = RuntimeShader(SOURCE).apply { setInputShader("page", input) }

    fun draw(
        canvas: Canvas,
        paint: Paint,
        width: Int,
        height: Int,
        progress: Float,
        band: Float,
        overscan: Float,
        amplitude: Float,
        sweepFromRight: Boolean
    ) {
        shader.setFloatUniform("size", width.toFloat(), height.toFloat())
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("band", band)
        shader.setFloatUniform("overscan", overscan)
        shader.setFloatUniform("amplitude", amplitude)
        shader.setFloatUniform("fromRight", if (sweepFromRight) 1f else 0f)
        paint.shader = shader
        try {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } finally {
            paint.shader = null
        }
    }

    private companion object {
        val SOURCE = """
            uniform shader page;
            uniform float2 size;
            uniform float progress;
            uniform float band;
            uniform float overscan;
            uniform float amplitude;
            uniform float fromRight;

            half4 main(float2 p) {
                float start = mix(-overscan, size.x + overscan, fromRight);
                float end = mix(size.x + overscan, -overscan, fromRight);
                float wave = sin((p.y / size.y) * 6.28318530718) * amplitude;
                float edge = mix(start, end, progress) + wave;
                float coverage = clamp((p.x - edge + band) / (2.0 * band), 0.0, 1.0);
                coverage = mix(1.0 - coverage, coverage, fromRight);
                return page.eval(p) * coverage;
            }
        """.trimIndent()
    }
}

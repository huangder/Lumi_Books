package com.huangder.lumibooks.ui.reader.finish

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bright shell colors that stay legible on both paper and night reader themes. */
private val FireworkPalette = intArrayOf(
    0xFFFFB938.toInt(), // gold
    0xFFFF6B6B.toInt(), // coral
    0xFF2EC4B6.toInt(), // teal
    0xFF9B7BFF.toInt(), // violet
    0xFF4DA3FF.toInt(), // sky
    0xFFFF7EB6.toInt()  // pink
)

/** Trail samples behind each spark head: time offset, alpha multiplier, width multiplier. */
private val TrailOffsets = floatArrayOf(0f, 0.035f, 0.075f)
private val TrailAlpha = floatArrayOf(1f, 0.45f, 0.18f)
private val TrailWidth = floatArrayOf(1f, 0.8f, 0.6f)

/** System animation scale 0 and battery saver both mean "please do not animate". */
internal fun fireworksAllowed(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    if (scale == 0f) return false
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return power?.isPowerSaveMode != true
}

/**
 * Plays one precomputed show and then calls [onFinished]; the caller removes this composable
 * afterwards so no frame callback survives the animation.
 *
 * Only the draw phase reads the clock state, so the show never triggers recomposition or layout.
 */
@Composable
internal fun FireworksOverlay(
    seed: Int,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val show by produceState<FireworkShow?>(null, seed) {
        value = withContext(Dispatchers.Default) { FireworkShow.generate(seed) }
    }
    val view = LocalView.current
    val density = LocalDensity.current.density
    var timeSec by remember(seed) { mutableFloatStateOf(0f) }
    // 1 = draw every spark; 2 = every other spark when the device cannot keep up.
    var stride by remember(seed) { mutableIntStateOf(1) }
    val buffers = remember { FireworkBuffers() }

    LaunchedEffect(show) {
        val current = show ?: return@LaunchedEffect
        val refreshHz = view.display?.refreshRate?.takeIf { it >= 30f } ?: 60f
        val budgetNanos = (1_000_000_000f / refreshHz * 1.5f).toLong()
        val start = withFrameNanos { it }
        var previous = start
        var sampled = 0
        var slowNanos = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (sampled < 10) {
                slowNanos += now - previous
                sampled++
                if (sampled == 10 && slowNanos / 10 > budgetNanos) stride = 2
            }
            previous = now
            timeSec = (now - start) / 1_000_000_000f
            if (timeSec >= current.durationSec) break
        }
        latestOnFinished()
    }

    Canvas(modifier) {
        val current = show ?: return@Canvas
        val t = timeSec
        val w = size.width
        val h = size.height
        val headWidth = 3.2f * density
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val paint = buffers.paint
            val point = buffers.point
            for (b in 0 until current.burstCount) {
                val color = FireworkPalette[current.colorIndex(b) % FireworkPalette.size]
                // Rising shell: a short bright streak.
                if (current.rocketPosition(b, t, point)) {
                    paint.color = color
                    paint.alpha = 230
                    paint.strokeWidth = headWidth * 0.9f
                    var n = 0
                    for (trail in 0 until 3) {
                        if (current.rocketPosition(b, t - trail * 0.03f, point) || trail == 0) {
                            buffers.points[n++] = point[0] * w
                            buffers.points[n++] = point[1] * h
                        }
                    }
                    native.drawPoints(buffers.points, 0, n, paint)
                    continue
                }
                val age = current.burstAge(b, t)
                val alpha = current.burstAlpha(age)
                if (alpha <= 0f) continue
                for (trail in TrailOffsets.indices) {
                    val sampleAge = age - TrailOffsets[trail]
                    if (sampleAge < 0f) continue
                    var n = 0
                    var spark = 0
                    while (spark < current.particlesPerBurst) {
                        current.sparkPosition(b, spark, sampleAge, w, h, point)
                        buffers.points[n++] = point[0]
                        buffers.points[n++] = point[1]
                        spark += stride
                    }
                    paint.color = color
                    paint.alpha = (255 * alpha * TrailAlpha[trail]).toInt().coerceIn(0, 255)
                    // Sparks shrink slightly as they burn out.
                    paint.strokeWidth = headWidth * TrailWidth[trail] * (0.7f + 0.3f * alpha)
                    native.drawPoints(buffers.points, 0, n, paint)
                }
            }
        }
    }
}

private class FireworkBuffers {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val point = FloatArray(2)
    val points = FloatArray(FireworkShow.MAX_PARTICLES * 2)
}

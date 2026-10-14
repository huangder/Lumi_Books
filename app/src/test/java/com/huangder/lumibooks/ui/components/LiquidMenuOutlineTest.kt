package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.ui.theme.AppShapes
import com.huangder.lumibooks.ui.theme.toAppPath
import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidMenuOutlineTest {
    @Test fun continuousMorphRetainsSubpixelAccuracyWithoutTessellatingEveryTwoPixels() {
        for (width in listOf(640f, 1200f)) for (nearX in listOf(-1f, 1f)) for (nearY in listOf(-1f, 1f)) {
            val frame = LiquidMenuFrame(Rect(0f, 0f, width, width * 1.8f), 64f, 0.45f, nearX, nearY, 1f)
            // Dense reference preserves the original outline and two-pixel sampling used before
            // the optimization. Check the silhouette as well as the path work sent to the GPU.
            val samples = AppShapes.rounded(64f).createOutline(frame.bounds.size, LayoutDirection.Ltr, Density(1f))
                .toAppPath().asAndroidPath().approximate(0.01f)
            val reference = buildList {
                for (i in 3 until samples.size step 3) {
                    val a = Offset(samples[i - 2], samples[i - 1])
                    val b = Offset(samples[i + 1], samples[i + 2])
                    val steps = ceil((b - a).getDistance() / 2f).toInt().coerceAtLeast(1)
                    repeat(steps) { add(frame.warp(a + (b - a) * (it.toFloat() / steps))) }
                }
            }
            val points = frame.outlinePoints(continuous = true)
            assertTrue("Straight edges must not dominate path cost: ${points.size} vs ${reference.size}",
                points.size < reference.size / 2)
            reference.forEach { expected ->
                val error = points.indices.minOf { i ->
                    val a = points[i]
                    val b = points[(i + 1) % points.size]
                    val edge = b - a
                    val lengthSquared = edge.x * edge.x + edge.y * edge.y
                    val relative = expected - a
                    val t = if (lengthSquared == 0f) 0f else
                        ((relative.x * edge.x + relative.y * edge.y) / lengthSquared).coerceIn(0f, 1f)
                    (expected - (a + edge * t)).getDistance()
                }
                assertTrue("The liquid silhouette moved by $error px at $expected", error <= 0.05f)
            }
        }
    }
}

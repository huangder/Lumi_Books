package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.ui.theme.AppShapes
import com.huangder.lumibooks.ui.theme.toAppPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidMenuOutlineTest {
    private val source = Rect(300f, 30f, 344f, 74f)
    private val target = Rect(124f, 30f, 344f, 410f)

    private fun frame(phase: Float, nearX: Float = 1f, nearY: Float = -1f) =
        LiquidMenuGeometry.frame(source, target, phase, 22f, 24f).copy(nearX = nearX, nearY = nearY)

    /** Samples of the unwarped continuous rounded outline that the menu outline is built from. */
    private fun restingSamples(frame: LiquidMenuFrame): List<Offset> {
        val outline = AppShapes.rounded(frame.radius).createOutline(frame.bounds.size, LayoutDirection.Ltr, Density(1f))
        val raw = outline.toAppPath().asAndroidPath().approximate(0.01f)
        return (1 until raw.size step 3).map { Offset(raw[it], raw[it + 1]) }
    }

    /** Largest horizontal gap between the warped exact edge and the generated polyline. */
    private fun worstChordError(frame: LiquidMenuFrame, points: List<Offset>): Float {
        var worst = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val restA = frame.warp(a, inverse = true)
            val restB = frame.warp(b, inverse = true)
            for (step in 1 until 16) {
                val t = step / 16f
                val exact = frame.warp(restA + (restB - restA) * t)
                val chord = a + (b - a) * t
                worst = maxOf(worst, abs(exact.x - chord.x))
            }
        }
        return worst
    }

    @Test fun settledOutlineKeepsTheRoundedRectangleVerticesUnchanged() {
        val settled = frame(1f)
        assertEquals(0f, settled.neck, 0f)
        val expected = restingSamples(settled).dropLast(1)
        val actual = settled.outlinePoints(continuous = true)
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.x, a.x, 0.0005f)
            assertEquals(e.y, a.y, 0f)
        }
    }

    @Test fun neckedOutlineStaysWithinTheSubpixelToleranceInEveryOrientation() {
        for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) for (phase in listOf(0.1f, 0.2f, 0.35f, 0.5f)) {
            val f = frame(phase, x, y)
            assertTrue(f.neck > 0f)
            val points = f.outlinePoints(continuous = true)
            val error = worstChordError(f, points)
            assertTrue("phase=$phase side=($x,$y) error=$error", error <= 0.012f)
        }
    }

    @Test fun neckedOutlineAddsVerticesOnlyWhereTheWarpBendsTheEdges() {
        val f = frame(0.35f)
        val base = restingSamples(f).size - 1
        val points = f.outlinePoints(continuous = true)
        assertTrue("The warp needs extra vertices along the stretched sides", points.size > base)
        // A uniform 2px subdivision of the straight runs would add about one vertex per 2px.
        val perimeter = 2f * (f.bounds.width + f.bounds.height)
        assertTrue("Sampling should stay well below a 2px grid: ${points.size}", points.size - base < perimeter / 2f / 2f)
    }

    @Test fun outlinePointsStayFiniteAndInsideTheFrame() {
        for (step in 0..110) {
            val f = frame(step / 100f)
            f.outlinePoints(continuous = true).forEach {
                assertTrue(it.x.isFinite() && it.y.isFinite())
                assertTrue(it.x in -0.01f..f.bounds.width + 0.01f && it.y in -0.01f..f.bounds.height + 0.01f)
            }
        }
    }

    @Test fun oneShapeInstanceBuildsItsOutlineOncePerSize() {
        val shape = LiquidMenuShape(frame(0.35f))
        val size = Size(220f, 300f)
        val first = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
        assertTrue(first is Outline.Generic)
        assertSame(first, shape.createOutline(size, LayoutDirection.Ltr, Density(2f)))
        assertNotSame(first, shape.createOutline(Size(221f, 300f), LayoutDirection.Ltr, Density(1f)))
        assertNotSame(first, shape.createOutline(size, LayoutDirection.Rtl, Density(1f)))
    }

    @Test fun drawTimeShapeResolvesTheCurrentShapeOnEveryRequest() {
        var current = AppShapes.rounded(4f)
        val shape = DrawTimeShape { current }
        val size = Size(100f, 40f)
        val narrow = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
        current = AppShapes.rounded(18f)
        val wide = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
        assertTrue(narrow.bounds == wide.bounds)
        assertTrue("The second outline must come from the new shape", narrow != wide)
    }
}

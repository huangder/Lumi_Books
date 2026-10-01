package com.huangder.lumibooks.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.components.LiquidMenuFrame
import com.huangder.lumibooks.ui.components.LiquidMenuGeometry
import com.huangder.lumibooks.ui.components.LiquidMenuShape
import com.huangder.lumibooks.ui.components.supportsLiquidGlassHighlight
import com.huangder.lumibooks.ui.components.supportsLiquidGlassLens
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppShapesTest {
    private val size = Size(160f, 100f)
    private fun outline(shape: Shape, rtl: Boolean = false, size: Size = this.size, density: Float = 1f) =
        shape.createOutline(size, if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, Density(density))

    private fun raster(outline: Outline): Bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawPath(outline.toAppPath().asAndroidPath(), Paint().apply { color = android.graphics.Color.WHITE })
    }

    @Test fun materialThemeKeepsOriginalCornersAndLumiChangesOnlyTheCurve() {
        val original = RoundedCornerShape(24.dp)
        val material = AppShapes.rounded(24.dp, AppShapes.usesContinuousCorners("material3"))
        assertEquals(original, material)
        for (theme in listOf("lumi", "liquid_glass", "lumi_chan")) {
            val shape = AppShapes.rounded(24.dp, AppShapes.usesContinuousCorners(theme))
            assertEquals(24f, shape.topStart.toPx(size, Density(1f)), 0f)
            assertTrue(outline(shape) is Outline.Generic)
            val rounded = raster(outline(original))
            val continuous = raster(outline(shape))
            assertFalse("The curve must actually differ from an arc", continuous.sameAs(rounded))
            assertEquals(rounded.getPixel(80, 0), continuous.getPixel(80, 0))
            assertEquals(rounded.getPixel(80, 50), continuous.getPixel(80, 50))
            rounded.recycle(); continuous.recycle()
        }
    }

    @Test fun partialCornersMirrorInRtlAndKeepSheetBottomSquare() {
        val shape = AppShapes.rounded(topStart = 30.dp, topEnd = 12.dp)
        val ltr = raster(outline(shape))
        val rtl = raster(outline(shape, rtl = true))
        assertEquals(android.graphics.Color.WHITE, ltr.getPixel(0, 99))
        assertEquals(android.graphics.Color.WHITE, ltr.getPixel(159, 99))
        var coverageDifference = 0
        for (y in 0 until 100) for (x in 0 until 160) {
            val delta = kotlin.math.abs(android.graphics.Color.alpha(ltr.getPixel(x, y)) -
                android.graphics.Color.alpha(rtl.getPixel(159 - x, y)))
            coverageDifference += delta
            assertTrue("RTL corner coverage at $x,$y differs by $delta/255", delta <= 32)
        }
        assertTrue("Mirrored area differs by $coverageDifference/255 pixels", coverageDifference < 8 * 255)
        ltr.recycle(); rtl.recycle()
    }

    @Test fun copyKeepsContinuousStyleAndCanRemoveOnlyBottomCorners() {
        val shape = AppShapes.rounded(28.dp)
        val copy = shape.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
        assertTrue(outline(copy) is Outline.Generic)
        assertEquals(shape, shape.copy())
        assertEquals(shape.hashCode(), shape.copy().hashCode())
        val image = raster(outline(copy))
        assertEquals(0, image.getPixel(0, 0))
        assertEquals(android.graphics.Color.WHITE, image.getPixel(0, 99))
        image.recycle()
        assertTrue(AppShapes.material(true).large.copy(bottomStart = CornerSize(0.dp)) is ContinuousCornerBasedShape)
        assertEquals(androidx.compose.material3.Shapes(), AppShapes.material(false))
    }

    @Test fun fourIndependentCornersRetainTheirOrderAtEveryDensity() {
        for (density in listOf(1f, 2f)) {
            val shape = AppShapes.rounded(4.dp, 12.dp, 20.dp, 28.dp)
            val rotatedShape = AppShapes.rounded(20.dp, 28.dp, 4.dp, 12.dp)
            val original = raster(outline(shape, density = density))
            val rotated = raster(outline(rotatedShape, density = density))
            var difference = 0
            for (y in 0 until 100) for (x in 0 until 160) {
                difference += kotlin.math.abs(android.graphics.Color.alpha(original.getPixel(x, y)) -
                    android.graphics.Color.alpha(rotated.getPixel(159 - x, 99 - y)))
            }
            // Opposite path traversal can quantize native antialias coverage
            // differently. Allow 0.05 pixel of area per pixel of perimeter.
            assertTrue("Independent corners must rotate with the surface: density=$density, area=${difference / 255f}",
                difference / 255f < 2f * (size.width + size.height) * 0.05f)
            original.recycle(); rotated.recycle()
        }
    }

    @Test fun dropletOpenAndReturnKeepFiniteContinuousOutlinesIncludingOvershoot() {
        val source = Rect(300f, 30f, 344f, 74f)
        val target = Rect(124f, 30f, 344f, 410f)
        for (nearX in listOf(-1f, 1f)) for (nearY in listOf(-1f, 1f)) {
            for (step in 0..110) {
                val frame = LiquidMenuGeometry.frame(source, target, step / 100f, 22f, 24f)
                    .copy(nearX = nearX, nearY = nearY)
                val path = outline(LiquidMenuShape(frame), size = frame.bounds.size).toAppPath()
                val bounds = path.getBounds()
                assertTrue(bounds.left.isFinite() && bounds.top.isFinite())
                assertTrue(bounds.left >= -0.01f && bounds.top >= -0.01f)
                assertTrue(bounds.right <= frame.bounds.width + 0.01f)
                assertTrue(bounds.bottom <= frame.bounds.height + 0.01f)
                assertFalse("Every open/return frame must contain a surface", path.isEmpty)
            }
        }
    }

    @Test fun zeroTinyAndOversizedCornersStayFiniteAndInsideBounds() {
        assertTrue(outline(AppShapes.rounded(0.dp)) is Outline.Rectangle)
        for (bounds in listOf(Size.Zero, Size(1f, 1f), Size(2f, 9f), size)) {
            for (radius in listOf(0.dp, 1.dp, 999.dp)) {
                val pathBounds = outline(AppShapes.rounded(radius), size = bounds).bounds
                assertTrue(pathBounds.left.isFinite() && pathBounds.right.isFinite())
                assertTrue(pathBounds.left >= -0.001f && pathBounds.top >= -0.001f)
                assertTrue(pathBounds.right <= bounds.width + 0.001f && pathBounds.bottom <= bounds.height + 0.001f)
            }
        }
        assertTrue(outline(AppShapes.rounded(999.dp), size = Size(40f, 40f)) is Outline.Rounded)
    }

    @Test fun percentagesAndDensityKeepComposeSemantics() {
        val a = raster(outline(AppShapes.rounded(50)))
        val b = raster(outline(Capsule()))
        assertTrue(a.sameAs(b))
        a.recycle(); b.recycle()
        val dp = raster(outline(AppShapes.rounded(12.dp), density = 2f))
        val px = raster(outline(AppShapes.rounded(24f), density = 2f))
        assertTrue(dp.sameAs(px))
        dp.recycle(); px.recycle()
    }

    @Test fun lensAndRimCapabilitiesAreIndependent() {
        assertTrue(supportsLiquidGlassLens(AppShapes.rounded(24.dp)))
        assertTrue(supportsLiquidGlassLens(RoundedRectangle(24.dp)))
        assertTrue(supportsLiquidGlassLens(Capsule()))
        assertFalse(supportsLiquidGlassHighlight(AppShapes.rounded(24.dp)))
        assertFalse(supportsLiquidGlassHighlight(RoundedRectangle(24.dp)))
        assertTrue(supportsLiquidGlassHighlight(CircleShape))
        assertTrue(supportsLiquidGlassHighlight(RoundedCornerShape(24.dp)))
    }

    @Test fun menuSettlesToPanelOutlineWithoutChangingItsBounds() {
        val frame = LiquidMenuFrame(Rect(0f, 0f, 160f, 100f), 24f, 0f, 1f, -1f, 0f)
        val panel = raster(outline(AppShapes.rounded(24.dp)))
        val menu = raster(outline(LiquidMenuShape(frame)))
        assertTrue("Settled menu and static panel must have identical masks", panel.sameAs(menu))
        val almost = raster(outline(LiquidMenuShape(frame.copy(neck = 0.00001f))))
        var coverageDifference = 0
        var largestDifference = 0
        for (y in 0 until 100) for (x in 0 until 160) {
            val delta = kotlin.math.abs(android.graphics.Color.alpha(almost.getPixel(x, y)) -
                android.graphics.Color.alpha(menu.getPixel(x, y)))
            coverageDifference += delta
            largestDifference = maxOf(largestDifference, delta)
        }
        // Native rasterization includes fractional edge coverage. Compare covered
        // area rather than treating a 1/255 alpha change as a whole changed pixel.
        assertTrue("No last-frame jump: area=$coverageDifference/255, peak=$largestDifference/255",
            coverageDifference < 10 * 255 && largestDifference < 64)
        for (neck in listOf(0.01f, 0.3f, 0.58f)) {
            frame.copy(neck = neck).outlinePoints(continuous = true).forEach {
                assertTrue(it.x.isFinite() && it.y.isFinite())
                assertTrue(it.x in -0.001f..160.001f && it.y in -0.001f..100.001f)
            }
        }
        panel.recycle(); menu.recycle(); almost.recycle()
    }
}

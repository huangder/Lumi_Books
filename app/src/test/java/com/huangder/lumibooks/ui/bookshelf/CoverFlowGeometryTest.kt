package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.BookshelfLayout
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CoverFlowGeometryTest {
    private val geometry = CoverFlowGeometry(200f, 200f / 0.75f)

    @Test fun `center is full size sharp and front facing`() {
        val center = geometry.transform(0f)
        assertEquals(0f, center.x, 0f)
        assertEquals(0f, center.depth, 0f)
        assertEquals(0f, center.rotationY, 0f)
        assertEquals(1f, center.scale, 0f)
        assertEquals(1f, center.alpha, 0f)
        assertEquals(0f, center.blurDp, 0f)
    }

    @Test fun `sides face inward with mirrored geometry`() {
        for (distance in listOf(0.1f, 0.5f, 1f, 2f, 4f)) {
            val left = geometry.transform(-distance)
            val right = geometry.transform(distance)
            assertTrue(left.rotationY > 0f)
            assertTrue(right.rotationY < 0f)
            assertEquals(-left.x, right.x, 0.001f)
            assertEquals(-left.rotationY, right.rotationY, 0.001f)
            assertEquals(left.scale, right.scale, 0f)
        }
        assertEquals(50f, abs(geometry.transform(1f).rotationY), 0.01f)
        assertEquals(0.94f, geometry.transform(1f).scale, 0.01f)
    }

    @Test fun `side spacing compresses and depth increases instead of linear carousel spacing`() {
        val poses = (0..4).map { geometry.transform(it.toFloat()) }
        val gaps = poses.zipWithNext { a, b -> b.x - a.x }
        assertTrue(gaps[0] > gaps[1] * 2f)
        assertTrue(gaps[1] > gaps[2])
        poses.zipWithNext().forEach { (a, b) ->
            assertTrue(b.depth > a.depth)
            assertTrue(b.scale < a.scale)
            assertTrue(b.alpha < a.alpha)
            assertTrue(b.blurDp <= 3f)
        }
    }

    @Test fun `android camera preserves visible inward taper at every display density`() {
        for (density in listOf(1f, 2f, 3.25f, 4f)) {
            val scaled = CoverFlowGeometry(200f * density, 200f * density / 0.75f)
            fun edgeHeight(distance: Float, left: Boolean): Float {
                val pose = scaled.transform(distance)
                val focalPixels = pose.cameraDistance * 72f
                val x = scaled.coverWidth * if (left) -0.5f else 0.5f
                val z = x * kotlin.math.sin(Math.toRadians(pose.rotationY.toDouble())).toFloat()
                return scaled.coverHeight * pose.scale * focalPixels / (focalPixels + z)
            }
            val outer = edgeHeight(-1f, true)
            val inner = edgeHeight(-1f, false)
            // A visibly trapezoidal side cover, with an outer edge no taller than the center.
            assertTrue(outer / inner in 1.10f..1.18f)
            assertTrue(outer / scaled.coverHeight in 0.98f..1.01f)
            assertEquals(outer, edgeHeight(1f, false), 0.001f)
            assertEquals(inner, edgeHeight(1f, true), 0.001f)
        }
    }

    @Test fun `all transforms remain continuous at center and index boundaries`() {
        for (d in listOf(-1f, -0.5f, 0f, 0.5f, 1f, 3.5f, 4.5f)) {
            val a = geometry.transform(d - 0.0001f)
            val b = geometry.transform(d + 0.0001f)
            assertTrue(abs(a.x - b.x) < 0.1f)
            assertTrue(abs(a.rotationY - b.rotationY) < 0.1f)
            assertTrue(abs(a.scale - b.scale) < 0.01f)
            assertTrue(abs(a.alpha - b.alpha) < 0.01f)
            assertTrue(abs(a.blurDp - b.blurDp) < 0.01f)
        }
        assertEquals(geometry.transform(-0.5f).scale, geometry.transform(0.5f).scale, 0f)
    }

    @Test fun `hit testing follows perspective instead of an affine rotated rectangle`() {
        assertTrue(geometry.contains(0f, 0f, 0f))
        assertFalse(geometry.contains(0f, 101f, 0f))
        for (distance in listOf(-2f, -1f, 1f, 2f)) {
            val pose = geometry.transform(distance)
            val angle = Math.toRadians(pose.rotationY.toDouble())
            fun project(x: Float, y: Float): Pair<Float, Float> {
                val perspective = (geometry.camera + pose.depth) /
                    (geometry.camera + pose.depth + x * kotlin.math.sin(angle).toFloat())
                return Pair(pose.x + x * kotlin.math.cos(angle).toFloat() * pose.scale * perspective,
                    y * pose.scale * perspective)
            }
            val inside = project(geometry.coverWidth * 0.49f, geometry.coverHeight * 0.49f)
            val outside = project(geometry.coverWidth * 0.52f, 0f)
            assertTrue(geometry.contains(distance, inside.first, inside.second))
            assertFalse(geometry.contains(distance, outside.first, outside.second))
        }
    }

    @Test fun `edge resistance preserves in-range drag and bounds extreme overdrag`() {
        assertEquals(2.37f, CoverFlowPhysics.resist(2.37f, 8), 0f)
        assertTrue(CoverFlowPhysics.resist(-10000f, 8) >= -0.35f)
        assertTrue(CoverFlowPhysics.resist(10000f, 8) <= 7.35f)
        assertEquals(0f, CoverFlowPhysics.resist(10f, 1), 0f)
        assertEquals(0f, CoverFlowPhysics.resist(-10f, 0), 0f)
    }

    @Test fun `release predicts direction but cannot escape edges or skip unbounded books`() {
        assertEquals(2, CoverFlowPhysics.releaseTarget(2.37f, 0.1f, 20, true))
        assertEquals(4, CoverFlowPhysics.releaseTarget(2.37f, 10f, 20, true))
        assertEquals(1, CoverFlowPhysics.releaseTarget(2.37f, -10f, 20, true))
        assertEquals(5, CoverFlowPhysics.releaseTarget(2.37f, 10000f, 20, true))
        assertEquals(2, CoverFlowPhysics.releaseTarget(2.37f, 10f, 20, false))
        assertEquals(0, CoverFlowPhysics.releaseTarget(0f, -10f, 20, true))
        assertEquals(19, CoverFlowPhysics.releaseTarget(19f, 10f, 20, true))
    }

    @Test fun `focus follows book identity through reordering then nearest survivor on removal`() {
        assertEquals(2, CoverFlowPhysics.restoredIndex(listOf("b", "c", "a"), "a", 0f))
        assertEquals(1, CoverFlowPhysics.restoredIndex(listOf("b", "c"), "a", 2f))
        assertEquals(0, CoverFlowPhysics.restoredIndex(emptyList(), "a", 99f))
    }

    @Test fun `render window stays bounded and entering items are transparent`() {
        for (position in listOf(0f, 4.5f, 500.3f, 999f)) {
            val range = CoverFlowPhysics.visibleRange(position, 1000)
            assertTrue(range.count() <= 11)
            assertTrue(range.all { it in 0..999 })
        }
        assertTrue(CoverFlowPhysics.visibleRange(0f, 0).isEmpty())
        assertEquals(0f, geometry.transform(4.5f).alpha, 0f)
    }

    @Test fun `new preference does not become a four-column import or category grid`() {
        assertEquals(4, BookshelfLayout.normalize(4))
        assertEquals(2, BookshelfLayout.conventional(4))
        (1..3).forEach { assertEquals(it, BookshelfLayout.normalize(it)); assertEquals(it, BookshelfLayout.conventional(it)) }
    }
}

package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class PaperCurlGeometryTest {
    private val path = CurlPath(1200f, 2670f)
    private val bend = CurlBend()

    @Test fun deficitInverseMatchesRollGeometry() {
        for (theta in listOf(0.01f, 0.4f, 1.2f, 2.0f, 3.1f)) {
            assertEquals(theta, PaperCurl.thetaForDeficit(theta - sin(theta)), 2e-3f)
        }
        assertEquals(0f, PaperCurl.thetaForDeficit(-1f), 0f)
    }

    @Test fun arcForTravelMovesTheBentEdgeBackExactly() {
        val pose = CurlPose(1f, 0f, 0f, 60f, 2.0f)
        for (travel in listOf(0.5f, 10f, 40f, 66f, 120f, 900f)) {
            val s = PaperCurl.arcForTravel(travel, pose.r, pose.alpha)
            PaperCurl.bend(pose, s, bend)
            assertEquals("travel $travel", travel, s - bend.offset, 0.05f)
        }
    }

    @Test fun dragPosePinsTheGrabbedEdgeUnderTheFinger() {
        for (u in listOf(0.02f, 0.1f, 0.3f, 0.6f, 0.9f)) for (tilt in listOf(-0.3f, 0f, 0.35f)) {
            val grabY = 2000f
            val pose = path.pose(u, tilt, grabY, keepSpine = false)
            val edge = pose.nx * path.width + pose.ny * grabY
            PaperCurl.bend(pose, edge - pose.c, bend)
            val moved = edge - (pose.c + bend.offset)
            assertEquals("u=$u tilt=$tilt", u * path.travel, moved, 0.5f)
        }
    }

    @Test fun pathStartsFlatAndEndsWithSheetAndShadowOffScreen() {
        assertTrue(path.pose(0f, 0f, 1000f, keepSpine = true).isFlat)
        assertTrue(path.pose(Float.NaN, 0f, 1000f, keepSpine = true).isFlat)
        val end = path.pose(1f, 0f, 1000f, keepSpine = true)
        assertTrue("silhouette ${end.c + end.r}", end.c + end.r + path.shadowReach < 0f)
        assertEquals(Math.PI.toFloat(), end.alpha, 1e-4f)
    }

    @Test fun rollEdgeRetreatsMonotonicallyAndInvertsForBackwardDrags() {
        var previous = Float.MAX_VALUE
        for (i in 1..100) {
            val x = path.rollEdgeX(i / 100f, 0f, 1300f)
            assertTrue("u=${i / 100f} $x >= $previous", x <= previous + 0.01f)
            previous = x
        }
        for (x in listOf(1100f, 800f, 400f, 120f)) {
            val u = path.progressForRollEdge(x, 0f, 1300f)
            assertEquals(x, path.rollEdgeX(u, 0f, 1300f), 1f)
        }
    }

    /** Front ink at the fold must bend, and mirrored back ink must unroll as the drag grows. */
    @Test fun heldPaperStaysOnTheCylinderSoBothFacesKeepBending() {
        fun heldTheta(u: Float): Float {
            val r = path.radius(u)
            return PaperCurl.arcForTravel(u * path.travel, r, PaperCurl.WRAP) / r
        }
        // A lift starts as a barely bent sheet and wraps continuously, with no jump.
        assertTrue(heldTheta(0.0005f) < 0.35f)
        val samples = listOf(0.0005f, 0.005f, 0.02f, 0.05f, 0.1f, 0.2f, 0.3f).map(::heldTheta)
        assertTrue("the wrap grows with the turn", samples.zipWithNext().all { (a, b) -> b > a })
        // Early lifts keep most of the roll's front visible, so front ink visibly bends.
        assertTrue(sin(heldTheta(0.05f)) > 0.9f)
        // Back ink near the free edge is squeezed by 1/|cos θ|: strongly early, not at all once flat.
        val early = 1f / abs(kotlin.math.cos(heldTheta(0.05f)))
        val late = 1f / abs(kotlin.math.cos(minOf(heldTheta(0.45f), Math.PI.toFloat())))
        assertTrue("early $early late $late", early > 2.5f * late)
    }

    @Test fun tiltedDragKeepsTheSheetBoundAtTheSpine() {
        for (u in listOf(0.2f, 0.35f, 0.45f)) for (tilt in listOf(-0.45f, 0.45f)) {
            val pose = path.pose(u, tilt, if (tilt > 0f) 2600f else 80f, keepSpine = true)
            val straight = path.pose(u, 0f, 0f, keepSpine = true)
            if (straight.c >= 0f) assertTrue("u=$u tilt=$tilt", pose.c >= max(0f, pose.ny * path.height) - 1f)
        }
    }

    @Test fun tweenEndsExactlyStaysMonotoneAndContinuesTheReleaseSpeed() {
        for ((from, to, v0, v1) in listOf(
            listOf(0.2f, 1f, 0.004f, 0.003f), listOf(0.7f, 0f, -0.01f, 0f), listOf(0.4f, 1f, 1f, 1f)
        )) {
            val tween = CurlTween(from, to, v0, v1, 240L)
            assertEquals(from, tween.position(0L), 0f)
            assertEquals(to, tween.position(240L), 1e-5f)
            val samples = (0L..240L step 4L).map(tween::position)
            assertTrue(samples.zipWithNext().all { (a, b) -> if (to > from) b >= a - 1e-6f else b <= a + 1e-6f })
        }
        val gentle = CurlTween(0.3f, 1f, 0.002f, 0f, 400L)
        assertEquals(0.002f, gentle.velocity(0L), 1e-5f)
        assertTrue(abs(CurlTween(0.5f, 0.5f, 1f, 1f, 10L).position(5L) - 0.5f) < 1e-6f)
    }
}

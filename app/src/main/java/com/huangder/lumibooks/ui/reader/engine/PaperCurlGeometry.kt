package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Paper bent over a cylinder, in canonical page pixels: spine at x = 0, free
 * edge at x = width, y downward. Points with d = n·p - c > 0 leave the page
 * plane, wrap a cylinder of radius [r] up to [alpha] and continue as a flat
 * flap tangent to it. Front ink compresses and shears across the roll exactly
 * as the sheet bends; the flap shows the mirrored back of the sheet.
 */
internal data class CurlPose(
    val nx: Float,
    val ny: Float,
    val c: Float,
    val r: Float,
    val alpha: Float
) {
    val isFlat: Boolean get() = r <= 0f

    companion object {
        val FLAT = CurlPose(1f, 0f, 0f, 0f, PaperCurl.WRAP)
    }
}

/** Screen-space result of bending one paper point. */
internal class CurlBend {
    /** Distance from the fold line along n after bending. */
    var offset = 0f
    var z = 0f
    /** Surface angle; > PI/2 means the paper back faces the viewer. */
    var theta = 0f
}

internal object PaperCurl {
    /**
     * Lifted paper stays on the cylinder for half a turn, so both faces keep
     * bending: front ink compresses as it rises, mirrored back ink unrolls as
     * the curl grows. Only paper beyond half a turn lies flat on top.
     */
    val WRAP = PI.toFloat()
    /** Roll radius while the finger holds the page, and as the turned sheet leaves. */
    private const val R_HELD = 0.26f
    private const val R_END = 0.24f
    const val SHADOW_REACH = 0.075f
    private val PI_F = PI.toFloat()

    /** Maps arc length [s] beyond the fold to its bent position. */
    fun bend(pose: CurlPose, s: Float, out: CurlBend) {
        val r = pose.r
        val a = pose.alpha
        if (s <= 0f) {
            out.offset = s; out.z = 0f; out.theta = 0f
        } else if (s <= a * r) {
            val t = s / r
            out.offset = r * sin(t); out.z = r * (1f - cos(t)); out.theta = t
        } else {
            val rest = s - a * r
            out.offset = r * sin(a) + rest * cos(a)
            out.z = r * (1f - cos(a)) + rest * sin(a)
            out.theta = a
        }
    }

    /** Arc length whose bent position has moved [travel] back toward the spine. */
    fun arcForTravel(travel: Float, r: Float, alpha: Float): Float {
        if (travel <= 0f) return 0f
        val rollTravel = r * (alpha - sin(alpha))
        if (travel <= rollTravel) return r * thetaForDeficit(travel / r)
        return (travel + r * sin(alpha) - alpha * r * cos(alpha)) / (1f - cos(alpha))
    }

    /** Solves θ - sin θ = k on [0, π]. */
    fun thetaForDeficit(k: Float): Float {
        if (k <= 0f) return 0f
        var t = min(cbrt(6f * k), PI_F)
        repeat(6) {
            val slope = 1f - cos(t)
            if (slope < 1e-6f) return t
            t = (t - (t - sin(t) - k) / slope).coerceIn(0f, PI_F)
        }
        return t
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * A fixed roll radius: the held edge's wrap then grows continuously from 0
     * with the drag (θ ≈ ∛(6·travel/r) at first), so a lift starts as a gentle
     * bend, front ink compresses more as it rises, and the mirrored back unrolls
     * from a squeezed sliver to flat paper past half a turn.
     */
    internal fun radius(u: Float, span: Float): Float =
        span * (R_HELD + (R_END - R_HELD) * smoothstep(0.45f, 0.95f, u))

    internal fun endRadiusFraction() = R_END
}

/**
 * One page turn parameterised by progress u: 0 is the flat page, 1 is the
 * sheet fully turned and off screen together with its shadow.
 */
internal class CurlPath(val width: Float, val height: Float) {
    val span = max(1f, min(width, height))
    val shadowReach = span * PaperCurl.SHADOW_REACH
    val travel = 2f * width - (PI.toFloat() - 2f) * span * PaperCurl.endRadiusFraction() +
        2f * shadowReach + 4f

    fun radius(u: Float) = PaperCurl.radius(u, span)

    /**
     * Pose that pins the free-edge point (width, grabY) to the finger. [tilt]
     * rotates the fold; a dragged sheet stays bound at the spine.
     */
    fun pose(u: Float, tilt: Float, grabY: Float, keepSpine: Boolean): CurlPose {
        if (!u.isFinite() || u * travel <= 0.5f) return CurlPose.FLAT
        var pose = solve(u, if (tilt.isFinite()) tilt else 0f, grabY)
        if (keepSpine && pose.ny != 0f && !bound(pose)) {
            val straight = solve(u, 0f, grabY)
            pose = if (straight.c < 0f) straight else {
                var lo = 0f
                var hi = tilt
                repeat(12) { val mid = (lo + hi) / 2f; if (bound(solve(u, mid, grabY))) lo = mid else hi = mid }
                solve(u, lo, grabY)
            }
        }
        return pose
    }

    /** Screen x of the roll apex on the grabbed row; decreases with u. */
    fun rollEdgeX(u: Float, tilt: Float, grabY: Float): Float {
        if (u * travel <= 0.5f) return width
        val r = radius(u)
        val s = PaperCurl.arcForTravel(u * travel, r, PaperCurl.WRAP)
        val pose = solve(u, tilt, grabY)
        val reach = if (s < r * PI.toFloat() / 2f) r * sin(s / r) else r
        return (pose.c + reach - pose.ny * grabY) / pose.nx
    }

    /** Inverse of [rollEdgeX] by bisection. */
    fun progressForRollEdge(x: Float, tilt: Float, grabY: Float): Float {
        if (x >= width) return 0f
        var lo = 0f
        var hi = 1f
        repeat(22) {
            val mid = (lo + hi) / 2f
            if (rollEdgeX(mid, tilt, grabY) > x) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    private fun bound(pose: CurlPose) = pose.c >= max(0f, pose.ny * height) - 0.5f

    private fun solve(u: Float, tilt: Float, grabY: Float): CurlPose {
        val nx = cos(tilt)
        val ny = sin(tilt)
        val r = radius(u)
        val a = PaperCurl.WRAP
        val s = PaperCurl.arcForTravel(u * travel, r, a)
        return CurlPose(nx, ny, nx * width + ny * grabY - s, r, a)
    }
}

/** Monotone cubic Hermite in progress units; velocities are per millisecond. */
internal class CurlTween(
    val from: Float,
    val to: Float,
    startVelocity: Float,
    endVelocity: Float,
    val durationMs: Long
) {
    private val m0: Float
    private val m1: Float

    init {
        val delta = to - from
        val t = durationMs.coerceAtLeast(1L).toFloat()
        if (delta == 0f) {
            m0 = 0f; m1 = 0f
        } else {
            var a = (startVelocity * t / delta).coerceIn(0f, 3f)
            var b = (endVelocity * t / delta).coerceIn(0f, 3f)
            val norm = a * a + b * b
            if (norm > 9f) { val k = 3f / sqrt(norm); a *= k; b *= k }
            m0 = a * delta; m1 = b * delta
        }
    }

    fun position(elapsedMs: Long): Float {
        val t = (elapsedMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        return (2f * t3 - 3f * t2 + 1f) * from + (t3 - 2f * t2 + t) * m0 +
            (-2f * t3 + 3f * t2) * to + (t3 - t2) * m1
    }

    /** Progress per millisecond, used to hand a running turn to a new duration. */
    fun velocity(elapsedMs: Long): Float {
        val d = durationMs.coerceAtLeast(1L).toFloat()
        val t = (elapsedMs / d).coerceIn(0f, 1f)
        val t2 = t * t
        return ((6f * t2 - 6f * t) * from + (3f * t2 - 4f * t + 1f) * m0 +
            (-6f * t2 + 6f * t) * to + (3f * t2 - 2f * t) * m1) / d
    }
}

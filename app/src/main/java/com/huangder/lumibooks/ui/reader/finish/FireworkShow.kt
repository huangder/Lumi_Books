package com.huangder.lumibooks.ui.reader.finish

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A fully precomputed firework show.
 *
 * Every random choice is made once in [generate]; drawing a frame only evaluates closed-form
 * trajectories, so the per-frame cost is a few multiplications per particle and no allocation.
 * Positions are normalized: x by the canvas width, y by the canvas height, and velocities/gravity
 * by the shorter side so bursts stay round on any aspect ratio.
 */
internal class FireworkShow private constructor(
    val burstCount: Int,
    /** Per burst: launch time, burst time, origin x, apex x, apex y, color index. */
    private val bursts: FloatArray,
    /** Per burst [particlesPerBurst] entries of (vx, vy, speedJitter). */
    private val sparks: FloatArray,
    val particlesPerBurst: Int,
    val durationSec: Float
) {
    fun launchTime(burst: Int) = bursts[burst * BURST_STRIDE]
    fun burstTime(burst: Int) = bursts[burst * BURST_STRIDE + 1]
    fun colorIndex(burst: Int) = bursts[burst * BURST_STRIDE + 5].toInt()

    /**
     * Rocket head position before the burst, in normalized units; false once the burst happened.
     */
    fun rocketPosition(burst: Int, t: Float, out: FloatArray): Boolean {
        val base = burst * BURST_STRIDE
        val launch = bursts[base]
        val explode = bursts[base + 1]
        if (t < launch || t >= explode) return false
        // Ease-out rise: fast at launch, slowing near the apex like a real shell.
        val p = (t - launch) / (explode - launch)
        val eased = 1f - (1f - p) * (1f - p)
        out[0] = bursts[base + 2] + (bursts[base + 3] - bursts[base + 2]) * eased
        out[1] = 1.04f + (bursts[base + 4] - 1.04f) * eased
        return true
    }

    /** Age of the burst in seconds, or a negative value before it explodes. */
    fun burstAge(burst: Int, t: Float): Float = t - burstTime(burst)

    /** Burst opacity: quick flash, then a soft fade to zero at [SPARK_LIFE_SEC]. */
    fun burstAlpha(age: Float): Float = when {
        age < 0f || age >= SPARK_LIFE_SEC -> 0f
        age < 0.08f -> 1f
        else -> {
            val p = (age - 0.08f) / (SPARK_LIFE_SEC - 0.08f)
            (1f - p * p).coerceIn(0f, 1f)
        }
    }

    /**
     * Writes the spark position at [age] into [out] as pixel coordinates.
     * Uses linear drag k: x = v(1 - e^-kt)/k, plus gravity integrated with the same drag.
     */
    fun sparkPosition(
        burst: Int,
        spark: Int,
        age: Float,
        width: Float,
        height: Float,
        out: FloatArray
    ) {
        val base = burst * BURST_STRIDE
        val s = (burst * particlesPerBurst + spark) * SPARK_STRIDE
        val minSide = minOf(width, height)
        val decay = (1f - exp(-DRAG * age)) / DRAG
        val fall = GRAVITY * (age - decay) / DRAG
        out[0] = bursts[base + 3] * width + sparks[s] * sparks[s + 2] * decay * minSide
        out[1] = bursts[base + 4] * height + (sparks[s + 1] * sparks[s + 2] * decay + fall) * minSide
    }

    companion object {
        const val BURST_STRIDE = 6
        const val SPARK_STRIDE = 3
        const val SPARK_LIFE_SEC = 1.35f
        const val DRAG = 1.7f
        const val GRAVITY = 0.42f
        const val MAX_PARTICLES = 240

        fun generate(
            seed: Int,
            burstCount: Int = 5,
            particlesPerBurst: Int = 44
        ): FireworkShow {
            val bursts = burstCount.coerceIn(1, 8)
            val perBurst = particlesPerBurst.coerceIn(8, MAX_PARTICLES / bursts)
            val random = Random(seed)
            val burstData = FloatArray(bursts * BURST_STRIDE)
            val sparkData = FloatArray(bursts * perBurst * SPARK_STRIDE)
            var lastColor = -1
            for (b in 0 until bursts) {
                val base = b * BURST_STRIDE
                // Staggered launches; the last shell still finishes inside the show duration.
                val launch = b * 0.38f + random.nextFloat() * 0.12f
                val rise = 0.42f + random.nextFloat() * 0.12f
                // Alternate sides so consecutive bursts do not stack on one spot.
                val side = if (b % 2 == 0) 0.22f else 0.56f
                val apexX = side + random.nextFloat() * 0.22f
                val apexY = 0.14f + random.nextFloat() * 0.24f
                var color = random.nextInt(PALETTE_SIZE)
                if (color == lastColor) color = (color + 1) % PALETTE_SIZE
                lastColor = color
                burstData[base] = launch
                burstData[base + 1] = launch + rise
                burstData[base + 2] = apexX + (random.nextFloat() - 0.5f) * 0.08f
                burstData[base + 3] = apexX
                burstData[base + 4] = apexY
                burstData[base + 5] = color.toFloat()
                val baseSpeed = 0.40f + random.nextFloat() * 0.10f
                for (p in 0 until perBurst) {
                    val s = (b * perBurst + p) * SPARK_STRIDE
                    // Evenly spaced angles with small jitter read as a full round shell.
                    val angle = (p.toFloat() / perBurst) * 2.0 * PI + (random.nextFloat() - 0.5f) * 0.12f
                    sparkData[s] = cos(angle).toFloat()
                    sparkData[s + 1] = sin(angle).toFloat()
                    // Two shells (outer and inner ring) give depth without more particles.
                    val ring = if (p % 3 == 0) 0.62f else 1f
                    sparkData[s + 2] = baseSpeed * ring * (0.9f + random.nextFloat() * 0.2f)
                }
            }
            val lastBurst = (0 until bursts).maxOf { burstData[it * BURST_STRIDE + 1] }
            return FireworkShow(bursts, burstData, sparkData, perBurst, lastBurst + SPARK_LIFE_SEC)
        }

        const val PALETTE_SIZE = 6
    }
}

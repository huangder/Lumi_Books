package com.huangder.lumibooks.ui.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

internal const val MAIN_TAB_MAX_STAGGER_MS = 110L
internal const val MAIN_TAB_CATCH_UP_MS = 320L
internal const val MAIN_TAB_SETTLE_LIMIT_MS = 480L
private const val BandCount = 7

/** One frame clock drives every item; draw-only reads keep lazy lists out of composition. */
internal class MainTabMotion {
    var running by mutableStateOf(false)
        private set
    var originPage by mutableIntStateOf(0)
        private set
    var position by mutableFloatStateOf(0f)
        private set
    private var bands by mutableStateOf(FloatArray(BandCount))
    private val springPositions = FloatArray(BandCount)
    private val velocities = FloatArray(BandCount)
    private val itemVelocities = FloatArray(BandCount)
    private val history = ArrayDeque<Pair<Long, Float>>()
    private var previousFrame = 0L
    private var entranceStartedAt: Long? = null
    private var followingFinger = false
    private var released = false
    private var settling = false

    fun begin(origin: Int, initialPosition: Float) {
        originPage = origin
        position = initialPosition
        bands = FloatArray(BandCount) { initialPosition }
        springPositions.fill(initialPosition)
        velocities.fill(0f)
        itemVelocities.fill(0f)
        history.clear()
        previousFrame = 0L
        entranceStartedAt = null
        followingFinger = false
        released = false
        settling = false
        running = true
    }

    /** Continue from the visible positions and velocities when the finger lifts. */
    fun release() {
        if (!running || released) return
        released = true
        bands.copyInto(springPositions)
        itemVelocities.copyInto(velocities)
        history.clear()
    }

    /** Attached items follow the pager through its snap, then make one small rebound. */
    fun settle(target: Float) {
        if (!running || settling) return
        release()
        settling = true
        if (followingFinger) {
            position = target
            bands = FloatArray(BandCount) { target }
            springPositions.fill(target)
            for (index in velocities.indices) {
                velocities[index] = itemVelocities[index].coerceIn(-.75f, .75f)
            }
        }
    }

    fun advance(frameNanos: Long, target: Float) {
        if (entranceStartedAt == null && target != position) entranceStartedAt = frameNanos
        if (history.isEmpty()) history.addLast(frameNanos - 200_000_000L to position)
        position = target
        val elapsed = if (previousFrame == 0L) 1f / 120f
            else ((frameNanos - previousFrame) / 1_000_000_000f).coerceIn(0f, .05f)
        previousFrame = frameNanos
        history.addLast(frameNanos to target)
        while (history.size > 2 && history[1].first < frameNanos - 200_000_000L) history.removeFirst()
        // The stagger belongs only to the entrance. Fade its remaining distance out
        // smoothly, then keep every subsequent movement exactly attached to the finger.
        val catchUp = entranceStartedAt?.let {
            ((frameNanos - it) / 1_000_000f - MAIN_TAB_MAX_STAGGER_MS) /
                (MAIN_TAB_CATCH_UP_MS - MAIN_TAB_MAX_STAGGER_MS)
        }?.coerceIn(0f, 1f) ?: 0f
        val entranceWeight = 1f - catchUp * catchUp * (3f - 2f * catchUp)
        if (!released && catchUp == 1f) followingFinger = true
        val next = FloatArray(BandCount)
        for (index in 0 until BandCount) {
            if (followingFinger && !settling) {
                next[index] = target
                itemVelocities[index] = (target - bands[index]) / elapsed.coerceAtLeast(.000001f)
                continue
            }
            val delayNanos = MAIN_TAB_MAX_STAGGER_MS * 1_000_000L * index / (BandCount - 1)
            var delayedTarget = target
            if (!released) {
                val sampleTime = frameNanos - delayNanos
                delayedTarget = history.first().second
                for ((time, value) in history) {
                    if (time > sampleTime) break
                    delayedTarget = value
                }
            }
            // Exact damped-spring step stays stable when frames arrive late.
            val omega = 27f
            val damping = .68f
            val decay = damping * omega
            val frequency = omega * sqrt(1f - damping * damping)
            val displacement = springPositions[index] - delayedTarget
            val sine = sin(frequency * elapsed)
            val cosine = cos(frequency * elapsed)
            val envelope = exp(-decay * elapsed)
            val coefficient = (velocities[index] + decay * displacement) / frequency
            val stepped = envelope * (displacement * cosine + coefficient * sine)
            velocities[index] = envelope * (
                -decay * (displacement * cosine + coefficient * sine) +
                    frequency * (-displacement * sine + coefficient * cosine)
                )
            springPositions[index] = delayedTarget + stepped
            next[index] = if (released) springPositions[index]
                else target + (springPositions[index] - target) * entranceWeight
            itemVelocities[index] = (next[index] - bands[index]) / elapsed.coerceAtLeast(.000001f)
        }
        bands = next
    }

    fun itemOffset(page: Int, verticalFraction: Float): Float {
        if (!running || page == originPage) return 0f
        val band = verticalFraction.coerceIn(0f, 1f) * (BandCount - 1)
        val lower = band.toInt()
        val upper = (lower + 1).coerceAtMost(BandCount - 1)
        val followed = bands[lower] + (bands[upper] - bands[lower]) * (band - lower)
        // A small overshoot is enough for a soft finish, even after a fast fling.
        val minimum = minOf(originPage.toFloat(), position)
        val maximum = maxOf(originPage.toFloat(), position)
        val limited = when {
            followed < minimum -> minimum - .025f * tanh((minimum - followed) / .025f)
            followed > maximum -> maximum + .025f * tanh((followed - maximum) / .025f)
            else -> followed
        }
        return position - limited
    }

    fun isAtRest(target: Float): Boolean = bands.indices.all {
        abs(bands[it] - target) < .0004f && abs(itemVelocities[it]) < .004f
    }

    fun finish(target: Float) {
        position = target
        bands = FloatArray(BandCount) { target }
        springPositions.fill(target)
        velocities.fill(0f)
        itemVelocities.fill(0f)
        history.clear()
        previousFrame = 0L
        running = false
    }
}

internal class MainTabItemMotion(val motion: MainTabMotion, val page: Int, val direction: Float)
internal val LocalMainTabItemMotion = staticCompositionLocalOf<MainTabItemMotion?> { null }

/** Translate the cached item while leaving its layout and glass sampling coordinates fixed. */
@Composable
internal fun Modifier.mainTabEntrance(): Modifier {
    val item = LocalMainTabItemMotion.current ?: return this
    val topFraction = remember { mutableFloatStateOf(0f) }
    val viewportWidth = remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { coordinates ->
            var root = coordinates
            while (root.parentLayoutCoordinates != null) root = root.parentLayoutCoordinates!!
            topFraction.floatValue = coordinates.positionInRoot().y / root.size.height.coerceAtLeast(1)
            viewportWidth.floatValue = root.size.width.toFloat()
        }
        .drawWithContent {
            val offset = item.motion.itemOffset(item.page, topFraction.floatValue) *
                viewportWidth.floatValue * item.direction
            translate(left = offset) { this@drawWithContent.drawContent() }
        }
        .graphicsLayer()
}

package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import com.huangder.lumibooks.ui.animation.VisualMotion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Paper page curl modelled on Apple Books. A slow drag pins the free edge to the
 * finger, so the fold follows the drag direction and the front ink bends across
 * the roll; a flick or tap plays the same path quickly with an upright fold.
 * Independently implemented; [PaperCurl] describes the paper model.
 */
class CurlPageAnim(
    readView: PageAnimationSurface,
    /** Kept for host call sites; every host now shares the same quick catch-up. */
    @Suppress("UNUSED_PARAMETER") trackCornerTouchDirectly: Boolean = false,
    private var baseDurationMs: Int = 800
) : PageAnimationController(readView) {
    enum class MotionState { IDLE, DRAGGING, SETTLING, DESTROYED }

    /** Observed by ReadView to coordinate page-slot loading and input gating. */
    var onMotionStateChanged: ((MotionState) -> Unit)? = null
    override val drawsDirectlyOnCanvas = true

    private val density = readView.resources.displayMetrics.density
    private var renderer: PaperCurlRenderer? = null
    private var shaderRenderer = false
    private var shaderUnavailable = false
    private val look = CurlLook()
    private var path = CurlPath(1f, 1f)
    private var pose = CurlPose.FLAT
    private var captured = false
    private var progress = 0f
    private var tilt = 0f
    private var grabY = 0f
    /** Where the current sheet started curling; drag math is measured from here. */
    private var originX = 0f
    private var originY = 0f
    private var tracker: VelocityTracker? = null
    private var tween: CurlTween? = null
    private var tweenStarted = 0L
    private var tweenTilt = 0f
    private var terminalPending = false
    private var completionPosted = false
    /** The running turn is already finishing early for queued input. */
    private var catchingUp = false
    /** Bumped whenever a turn starts or ends; stale posted completions compare against it. */
    private var generation = 0L
    private var destroyed = false

    init {
        // Compile the GPU program while the reader settles, not on the first turn.
        if (Build.VERSION.SDK_INT >= 33) readView.postDelayed(300L) {
            if (!destroyed && readView.isHardwareAccelerated) PaperCurlShaderRenderer.warmUp()
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (destroyed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isRunning) return true
                if (captured) abortAnim()
                begin(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isRunning) return true
                if (tracker == null) begin(event)
                tracker?.addMovement(event)
                touchX = event.x
                touchY = event.y
                // One sheet per stroke: it flattens as the finger returns, and the other page
                // only starts once the finger clearly crosses back past where this one lifted.
                val reference = if (captured) originX else startX
                val slop = (if (captured) REVERSE_SLOP_DP else 8f) * density
                val wanted = directionForHorizontalDelta(event.x - reference, slop)
                    .let { if (captured && it == Direction.NONE) direction else it }
                if (wanted == Direction.NONE) return true
                hasMoved = true
                if (wanted != direction || !captured) {
                    // The curl starts from zero where the stroke crossed the threshold, so it
                    // grows in instead of appearing mid-bend; a retry after a missing target
                    // starts at the finger.
                    val retry = wanted == direction && !captured
                    val side = if (event.x >= reference) 1f else -1f
                    originX = if (retry) event.x else reference + side * slop
                    originY = event.y
                    if (captured) releaseTurn()
                    direction = wanted
                    isDragging = false
                    if (onCanFlip?.invoke(wanted) != true || !capture(wanted, originY)) return true
                    isDragging = true
                    onMotionStateChanged?.invoke(MotionState.DRAGGING)
                }
                updateDrag(event.x, event.y)
                readView.invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isRunning) return true
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1)
                val vx = tracker?.xVelocity ?: 0f
                val vy = tracker?.yVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                isDragging = false
                if (!captured) {
                    // Reset before dispatching: a tap callback may start a new turn right here.
                    direction = Direction.NONE
                    readView.invalidate()
                    if (isPageAnimationShortTap(event.actionMasked, hasMoved,
                            event.eventTime - downTime, abs(event.y - startY))) dispatchTap(event.x)
                    return true
                }
                release(event, vx, vy)
            }
        }
        return true
    }

    private fun begin(event: MotionEvent) {
        startX = event.x
        startY = event.y
        touchX = startX
        touchY = startY
        downTime = event.eventTime
        hasMoved = false
        direction = Direction.NONE
        tracker?.recycle()
        tracker = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    private fun dispatchTap(x: Float) {
        val relative = x / readView.width.coerceAtLeast(1)
        when {
            relative < 0.3f -> onTapLeft?.invoke()
            relative > 0.7f -> onTapRight?.invoke()
            else -> onTapCenter?.invoke()
        }
    }

    /** Canonical x: the spine is always at 0, even for right-to-left page progress. */
    private fun canonicalX(x: Float) = if (look.mirror) look.width - x else x

    /**
     * Fold tilt from the drag direction plus the grab height: a page held near its
     * top corner folds down-slanted, near its bottom corner up-slanted, even when
     * the finger moves straight across.
     */
    private fun desiredTilt(x: Float, y: Float): Float {
        val dx = abs(canonicalX(x) - canonicalX(originX))
        val fromCenter = (grabY / look.height - 0.5f) * 2f
        val zone = PaperCurl.smoothstep(0.2f, 0.6f, abs(fromCenter)) * if (fromCenter < 0f) -1f else 1f
        val corner = zone * CORNER_LIFT * dx * if (direction == Direction.PREV) -1f else 1f
        val tilt = atan2(-(y - originY) + corner, dx + 24f * density)
        return tilt.coerceIn(-MAX_TILT, MAX_TILT)
    }

    /** Progress that keeps the grabbed free edge (or, backward, the returning roll) under the finger. */
    private fun dragProgress(x: Float, y: Float, wantedTilt: Float): Float {
        val width = look.width
        val down = canonicalX(originX)
        val dx = canonicalX(x) - down
        val dy = y - originY
        return if (direction == Direction.NEXT) {
            // Starting mid-page still turns the whole sheet by the time the finger reaches the spine.
            val scale = (width / down.coerceAtLeast(width * 0.5f)).coerceIn(1f, 2f)
            val travel = -scale * (cos(wantedTilt) * dx + sin(wantedTilt) * dy)
            (travel / path.travel).coerceIn(0f, 1f)
        } else {
            // The returning sheet starts with its shadow just off screen, so it slides in.
            val scale = (width / (width - down).coerceAtLeast(width * 0.5f)).coerceIn(1f, 2f)
            path.progressForRollEdge(dx.coerceAtLeast(0f) * scale - path.shadowReach, wantedTilt, grabY)
        }
    }

    private fun updateDrag(x: Float, y: Float) {
        val wanted = desiredTilt(x, y)
        progress = dragProgress(x, y, wanted)
        pose = path.pose(progress, wanted, grabY, keepSpine = true)
        tilt = if (pose.isFlat) 0f else atan2(pose.ny, pose.nx)
    }

    private fun release(event: MotionEvent, vx: Float, vy: Float) {
        // Progress velocity: where the curl will be one frame later at the release speed.
        val ahead = dragProgress(event.x + vx * 16f, event.y + vy * 16f, desiredTilt(event.x, event.y))
        val velocity = (ahead - progress) / 16f
        val sign = horizontalTurnSign(direction)
        val travelled = (event.x - originX) * sign
        val speed = vx * 1000f * sign
        val fling = FLING_DP_PER_SECOND * density
        val up = event.actionMasked == MotionEvent.ACTION_UP
        val commit = up && (speed > fling || travelled >= readView.width * COMMIT_FRACTION && speed >= -fling)
        val swipe = if (up) (abs(speed) / (SWIPE_DP_PER_SECOND * density)).coerceIn(0f, 1f) else 0f
        settle(commit, velocity, expedited = false, swipe = swipe)
    }
    /** Finishes a drag: commit flies the sheet off (NEXT) or lays it flat (PREV); cancel reverses. */
    /**
     * Finishes a drag at the configured turn pace for the remaining distance; a
     * quick swipe shortens it by up to 40%. Commit flies the sheet off (NEXT) or
     * lays it flat (PREV); cancel reverses.
     */
    private fun settle(commit: Boolean, velocity: Float, expedited: Boolean, swipe: Float = 0f) {
        isDragging = false
        isRunning = true
        isFlipAnim = commit
        val target = if ((direction == Direction.NEXT) == commit) 1f else 0f
        val remaining = abs(target - progress)
        val heading = if (target > progress) 1f else -1f
        val towards = (velocity * heading).coerceAtLeast(0f)
        val base = if (expedited) curlExpeditedDurationMs(baseDurationMs) else baseDurationMs
        val pace = RELEASE_PACE * (1f - 0.4f * swipe)
        val duration = if (remaining <= 0f) 1L else (base * remaining * pace).roundToLong()
            .coerceIn(minSettleMs().toLong(), max(minSettleMs(), base).toLong())
        val average = remaining / duration
        val flies = target == 1f
        // Continue from the finger's speed without a fast start that crawls afterwards.
        startTween(target, minOf(towards, 2.5f * average) * heading, if (flies) average * heading else 0f, duration)
        onMotionStateChanged?.invoke(MotionState.SETTLING)
    }

    private fun startTween(target: Float, startVelocity: Float, endVelocity: Float, duration: Long) {
        tween = CurlTween(progress, target, startVelocity, endVelocity, duration)
        tweenStarted = SystemClock.uptimeMillis()
        tweenTilt = tilt
        terminalPending = false
        readView.postInvalidateOnAnimation()
    }

    override fun computeScroll(): Boolean {
        if (destroyed) return false
        val active = tween ?: return terminalPending
        val elapsed = SystemClock.uptimeMillis() - tweenStarted
        progress = active.position(elapsed)
        tilt = tweenTilt * (1f - PaperCurl.smoothstep(0f, 1f, elapsed.toFloat() / active.durationMs))
        if (elapsed >= active.durationMs) {
            progress = active.to
            tilt = 0f
            tween = null
            terminalPending = true
        }
        pose = path.pose(progress, tilt, grabY, keepSpine = true)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val active = renderer
        if (!captured || active == null) return
        try {
            active.draw(canvas, pose, look)
        } catch (error: RuntimeException) {
            // Never let a driver or shader problem break reading: later turns use the mesh.
            Log.w(TAG, "Curl shader failed; using the mesh renderer", error)
            shaderUnavailable = true
            val token = generation
            readView.post { if (token == generation && captured) endTurn(isFlipAnim) }
            return
        }
        if (terminalPending && !completionPosted) {
            // The terminal pose is on screen now; hand the page over on the next loop.
            completionPosted = true
            val token = generation
            readView.post { if (token == generation && terminalPending) endTurn(isFlipAnim) }
        }
    }

    /** Ends the turn; a committed turn hands its page to the host while direction is still set. */
    private fun endTurn(commit: Boolean) {
        tracker?.recycle()
        tracker = null
        resetMotion()
        resetChildViews()
        val ended = ++generation
        if (commit) onAnimationComplete?.invoke()
        if (generation != ended) return // the host started the next turn from the callback
        releaseTurn()
        direction = Direction.NONE
        if (!destroyed) onMotionStateChanged?.invoke(MotionState.IDLE)
        readView.invalidate()
    }

    private fun resetMotion() {
        tween = null
        terminalPending = false
        completionPosted = false
        catchingUp = false
        isRunning = false
        isDragging = false
        isFlipAnim = false
    }

    private fun releaseTurn() {
        renderer?.release()
        captured = false
        pose = CurlPose.FLAT
        progress = 0f
        tilt = 0f
    }

    /**
     * Idle slots: the visible page on top, its neighbours transparent and off screen.
     * The curl draws frozen frames, so only this placement decides which page's
     * selection layer receives a long press.
     */
    private fun resetChildViews() {
        if (readView.animatePageViewsDirectly) return
        val width = readView.width.toFloat()
        readView.curPageView.translationX = 0f
        readView.curPageView.translationY = 0f
        readView.curPageView.alpha = 1f
        readView.curPageView.translationZ = 2f
        readView.prevPageView.translationX = idleTranslationX(Direction.PREV, width)
        readView.prevPageView.translationY = 0f
        readView.prevPageView.alpha = 0f
        readView.prevPageView.translationZ = 0f
        readView.nextPageView.translationX = idleTranslationX(Direction.NEXT, width)
        readView.nextPageView.translationY = 0f
        readView.nextPageView.alpha = 0f
        readView.nextPageView.translationZ = 0f
    }

    private fun capture(dir: Direction, grabAt: Float): Boolean {
        val width = readView.width
        val height = readView.height
        if (width <= 0 || height <= 0) return false
        val turning = if (dir == Direction.NEXT) readView.curPageView else readView.prevPageView
        val under = if (dir == Direction.NEXT) readView.nextPageView else readView.curPageView
        val active = rendererFor()
        val frozen = try {
            active.capture(readView, turning, under)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not freeze curl pages", error)
            false
        }
        if (!frozen) {
            active.release()
            return false
        }
        generation++
        path = CurlPath(width.toFloat(), height.toFloat())
        look.width = width.toFloat()
        look.height = height.toFloat()
        look.mirror = readView.isPageProgressReversed
        look.paper = readView.bgColor or (0xFF shl 24)
        look.shadowReach = path.shadowReach
        grabY = grabAt.coerceIn(0f, height.toFloat())
        direction = dir
        progress = if (dir == Direction.NEXT) 0f else 1f
        tilt = 0f
        pose = path.pose(progress, 0f, grabY, keepSpine = true)
        captured = true
        return true
    }

    private fun rendererFor(): PaperCurlRenderer {
        val wantsShader = Build.VERSION.SDK_INT >= 33 && readView.isHardwareAccelerated && !shaderUnavailable
        renderer?.let { if (shaderRenderer == wantsShader) return it }
        renderer?.destroy()
        if (wantsShader && Build.VERSION.SDK_INT >= 33) {
            try {
                return PaperCurlShaderRenderer().also { renderer = it; shaderRenderer = true }
            } catch (error: RuntimeException) {
                Log.w(TAG, "Curl shader unavailable; using the mesh renderer", error)
                shaderUnavailable = true
            }
        }
        return PaperCurlMeshRenderer().also { renderer = it; shaderRenderer = false }
    }
    /** Plays a whole turn without a finger: an upright fold, easing in like a light flick. */
    fun startFromTap(dir: Direction, gestureStartY: Float? = null, expedited: Boolean = false): Boolean {
        if (destroyed || isRunning || isDragging || dir == Direction.NONE) return false
        if (onCanFlip?.invoke(dir) != true) return false
        if (captured) abortAnim()
        startY = gestureStartY?.takeIf { it.isFinite() } ?: readView.height * 0.5f
        startX = if (dir == Direction.NEXT) readView.width.toFloat() else 0f
        if (!capture(dir, startY)) return false
        isRunning = true
        isFlipAnim = true
        val duration = (if (expedited) curlExpeditedDurationMs(baseDurationMs)
            else (baseDurationMs * TAP_DURATION_FACTOR).roundToInt()).coerceAtLeast(1).toLong()
        val average = 1f / duration
        val (start, end) = when {
            expedited -> 0.9f to 1f
            dir == Direction.NEXT -> 0.45f to 1.2f
            else -> 1.6f to 0f
        }
        val heading = if (dir == Direction.NEXT) 1f else -1f
        startTween(if (dir == Direction.NEXT) 1f else 0f, start * average * heading,
            end * average * heading, duration)
        onMotionStateChanged?.invoke(MotionState.SETTLING)
        return true
    }

    override fun startAnim(fromDrag: Boolean) {
        if (captured && !isRunning) settle(true, 0f, expedited = false)
    }

    override fun startBounceBack() {
        if (captured && !isRunning) settle(false, 0f, expedited = false) else {
            direction = Direction.NONE
            readView.invalidate()
        }
    }

    /**
     * The first queued input finishes the running page within about 70 ms; any
     * further input hands it over at once, so turns never fall behind the taps.
     */
    override fun completeRunningFlipForNewInput(): RunningFlipHandoff {
        if (!committedRunning()) return RunningFlipHandoff.NOT_COMMITTED
        if (catchingUp) return completeSynchronously()
        expedite()
        return RunningFlipHandoff.COMPLETING_ASYNCHRONOUSLY
    }

    /** A confirmed drag owns the next frame, so the running page is handed over now. */
    fun completeRunningFlipForGestureHandoff(): RunningFlipHandoff =
        if (committedRunning()) completeSynchronously() else RunningFlipHandoff.NOT_COMMITTED

    private fun committedRunning() = captured && isRunning && isFlipAnim && direction != Direction.NONE

    private fun completeSynchronously(): RunningFlipHandoff {
        endTurn(commit = true)
        return RunningFlipHandoff.COMPLETED_SYNCHRONOUSLY
    }

    /** Finishes the rest of a committed turn quickly so the queued turn follows the tap. */
    private fun expedite() {
        catchingUp = true
        val active = tween ?: return
        val elapsed = SystemClock.uptimeMillis() - tweenStarted
        val remainingTime = active.durationMs - elapsed
        val distance = abs(active.to - progress)
        val duration = (CATCH_UP_MS * distance).roundToLong().coerceAtLeast(CATCH_UP_MS / 3)
        if (duration >= remainingTime) return
        val velocity = active.velocity(elapsed)
        val average = distance / duration * if (active.to > progress) 1f else -1f
        startTween(active.to, velocity, if (active.to == 1f) average else 0f, duration)
    }

    override fun holdRunningFlipAtEnd(): Boolean {
        if (!committedRunning()) return false
        tween = null
        progress = if (direction == Direction.NEXT) 1f else 0f
        tilt = 0f
        pose = path.pose(progress, 0f, grabY, keepSpine = true)
        terminalPending = true
        readView.postInvalidateOnAnimation()
        return true
    }

    override fun abortAnim() {
        generation++
        tracker?.recycle()
        tracker = null
        resetMotion()
        releaseTurn()
        resetChildViews()
        direction = Direction.NONE
        if (!destroyed) onMotionStateChanged?.invoke(MotionState.IDLE)
        readView.invalidate()
    }

    /** Distance the page has travelled, growing as the finger moves (host gesture tracking). */
    override fun getOffsetX(): Float = when {
        isDragging -> abs(touchX - startX)
        !captured -> 0f
        direction == Direction.NEXT -> progress * readView.width
        else -> (1f - progress) * readView.width
    }

    fun setBaseDuration(durationMs: Int) {
        baseDurationMs = durationMs.coerceIn(1, 1200)
    }

    fun drawOverlay(@Suppress("UNUSED_PARAMETER") canvas: Canvas) = Unit

    fun destroy() {
        if (destroyed) return
        abortAnim()
        destroyed = true
        renderer?.destroy()
        renderer = null
        onMotionStateChanged?.invoke(MotionState.DESTROYED)
        onMotionStateChanged = null
    }

    private fun minSettleMs() = VisualMotion.durationMillis(CURL_SETTLE_MIN_MS)

    private companion object {
        const val TAG = "PaperCurl"
        const val COMMIT_FRACTION = 0.14f
        const val FLING_DP_PER_SECOND = 450f
        /** Steepest fold a diagonal drag can produce, about 26°. */
        const val MAX_TILT = 0.45f
        /** A tap turn runs at 60% of the configured full-turn duration. */
        const val TAP_DURATION_FACTOR = 0.6f
        /** A released page covers the whole path in 75% of the configured duration. */
        const val RELEASE_PACE = 0.75f
        /** Swipe speed that earns the full 40% shortening. */
        const val SWIPE_DP_PER_SECOND = 1800f
        /** Fold tilt per unit of straight drag when the page is held at a corner (~22°). */
        const val CORNER_LIFT = 0.4f
        /** The other page starts only after the finger crosses back this far past the lift point. */
        const val REVERSE_SLOP_DP = 24f
        /** A queued tap finishes the running page within this time. */
        const val CATCH_UP_MS = 70L
    }
}

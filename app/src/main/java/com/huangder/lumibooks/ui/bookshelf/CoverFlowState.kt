package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One authoritative position, synchronously written by gestures or a single cancellable spring. */
@Stable
internal class CoverFlowState(initialPosition: Float = 0f, initialBookId: String? = null) {
    var position by mutableFloatStateOf(initialPosition)
        private set
    var focusedId by mutableStateOf(initialBookId)
        private set
    var menuBookId by mutableStateOf<String?>(null)
        private set
    var isMoving by mutableStateOf(false)
        private set
    // The derived focus can be read before books arrive, so this dependency must be observable.
    private var ids by mutableStateOf<List<String>>(emptyList())
    private var motionJob: Job? = null
    private var generation = 0L
    private var rawDragPosition = initialPosition

    val focusedIndex: Int get() = if (ids.isEmpty()) 0 else position.roundToInt().coerceIn(ids.indices)

    fun updateBooks(newIds: List<String>) {
        if (newIds == ids) return
        val index = CoverFlowPhysics.restoredIndex(newIds, focusedId, position)
        interrupt()
        ids = newIds
        position = index.toFloat()
        focusedId = ids.getOrNull(index)
    }

    fun restoreBook(bookId: String) {
        val index = ids.indexOf(bookId)
        if (index < 0) return
        interrupt()
        position = index.toFloat()
        focusedId = bookId
    }

    fun interrupt() {
        generation++
        motionJob?.cancel()
        motionJob = null
        isMoving = false
        menuBookId = null
    }

    fun beginDrag() {
        interrupt()
        // Inverse resistance prevents a jump if the spring is interrupted beyond an edge.
        val edge = position.coerceIn(0f, (ids.size - 1).coerceAtLeast(0).toFloat())
        val overflow = position - edge
        rawDragPosition = edge + overflow / (1f - abs(overflow) / CoverFlowPhysics.EdgeLimit).coerceAtLeast(0.01f)
    }

    fun dragBy(deltaInBooks: Float) {
        rawDragPosition += deltaInBooks
        position = CoverFlowPhysics.resist(rawDragPosition, ids.size)
        focusedId = ids.getOrNull(focusedIndex)
        isMoving = true
    }

    fun release(scope: CoroutineScope, velocity: Float, motionEnabled: Boolean) {
        settle(scope, CoverFlowPhysics.releaseTarget(position, velocity, ids.size, motionEnabled),
            motionEnabled, if (motionEnabled) velocity else 0f)
    }

    fun isCentered(index: Int): Boolean = !isMoving && abs(position - index) < 0.01f

    fun showMenu() {
        if (!isMoving && ids.isNotEmpty()) menuBookId = ids.getOrNull(focusedIndex)
    }

    fun dismissMenu() { menuBookId = null }

    fun settle(
        scope: CoroutineScope,
        index: Int,
        motionEnabled: Boolean,
        velocity: Float = 0f,
        showMenuAfter: Boolean = false
    ) {
        interrupt()
        if (ids.isEmpty()) return
        val target = index.coerceIn(ids.indices)
        val targetId = ids[target]
        val ticket = generation
        isMoving = true
        motionJob = scope.launch {
            try {
                val animation = AnimationState(position, velocity.coerceIn(-18f, 18f))
                animation.animateTo(
                    targetValue = target.toFloat(),
                    animationSpec = if (motionEnabled) spring(
                        dampingRatio = 0.9f, stiffness = 220f,
                        visibilityThreshold = CoverFlowPhysics.VisibilityThreshold
                    ) else tween(100)
                ) {
                    if (ticket == generation) {
                        // The spring may overshoot a book; the physical library edges remain bounded.
                        position = value.coerceIn(-CoverFlowPhysics.EdgeLimit, ids.lastIndex + CoverFlowPhysics.EdgeLimit)
                        focusedId = ids.getOrNull(focusedIndex)
                    }
                }
                if (ticket == generation && ids.getOrNull(target) == targetId) {
                    position = target.toFloat()
                    focusedId = targetId
                    isMoving = false
                    if (showMenuAfter) menuBookId = targetId
                }
            } finally {
                if (ticket == generation) isMoving = false
            }
        }
    }

    companion object {
        val Saver = Saver<CoverFlowState, List<Any>>(
            save = { listOf(it.position, it.focusedId.orEmpty()) },
            restore = { CoverFlowState(it[0] as Float, (it[1] as String).ifEmpty { null }) }
        )
    }
}

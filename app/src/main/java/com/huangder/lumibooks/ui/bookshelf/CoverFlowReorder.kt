package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.huangder.lumibooks.domain.model.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Long-press state of the Cover Flow stage: the cover being held (growing towards its menu) and
 * the one being dragged through the order.
 *
 * A dragged book stays in the middle of the stage while the carousel slides beneath it: the
 * carousel position follows the finger, and the book is moved to the slot nearest that position
 * in [draft], so the covers it passes glide round it.
 */
@Stable
internal class CoverFlowReorder {
    /** Held after the long-press threshold, not yet dragged or turned into a menu. */
    var liftedId by mutableStateOf<String?>(null)
    /** Whose menu a long hold opened: it stays grown until the menu closes, then settles. */
    var menuHeldId by mutableStateOf<String?>(null)
    var draggedId by mutableStateOf<String?>(null)
        private set
    /** The order on show while dragging. */
    var draft by mutableStateOf<List<Book>?>(null)
        private set
    /** Covers glide to new slots instead of jumping: while dragging and shortly after. */
    var gliding by mutableStateOf(false)
        private set
    /** Finger position on the stage, for edge scrolling. */
    var fingerX by mutableFloatStateOf(0f)

    private var range = IntRange.EMPTY
    private var startPosition = 0f
    private var travel = 0f
    private var glideJob: Job? = null

    val isDragging: Boolean get() = draggedId != null

    /** Starts dragging [book] (at [position] in [books]) within the slots [range]. */
    fun start(book: Book, books: List<Book>, range: IntRange, position: Float) {
        glideJob?.cancel()
        liftedId = null
        draft = books
        draggedId = book.id
        this.range = range
        startPosition = position
        travel = 0f
        gliding = true
    }

    /** Moves by [deltaInBooks]; returns the carousel position to show, within the range. */
    fun moveBy(deltaInBooks: Float): Float {
        travel += deltaInBooks
        val position = (startPosition + travel).coerceIn(range.first.toFloat(), range.last.toFloat())
        // Keep the travel inside the range too, so turning back responds at once.
        travel = position - startPosition
        val list = draft ?: return position
        val id = draggedId ?: return position
        val from = list.indexOfFirst { it.id == id }
        val to = position.roundToInt().coerceIn(range)
        if (from >= 0 && from != to) draft = list.toMutableList().apply { add(to, removeAt(from)) }
        return position
    }

    /** Ends the drag; returns the order on show, or null when no drag was active. */
    fun finish(scope: CoroutineScope): List<Book>? {
        val result = draft
        draggedId = null
        draft = null
        stopGlidingLater(scope)
        return result
    }

    fun cancel(scope: CoroutineScope) {
        draggedId = null
        draft = null
        liftedId = null
        stopGlidingLater(scope)
    }

    private fun stopGlidingLater(scope: CoroutineScope) {
        glideJob?.cancel()
        glideJob = scope.launch {
            delay(GLIDE_TAIL_MILLIS)
            gliding = false
        }
    }

    private companion object {
        const val GLIDE_TAIL_MILLIS = 360L
    }
}

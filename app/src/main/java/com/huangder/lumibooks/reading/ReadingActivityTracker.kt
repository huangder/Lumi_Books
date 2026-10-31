package com.huangder.lumibooks.reading

import android.os.SystemClock
import android.util.Log
import com.huangder.lumibooks.domain.repository.ReadingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One owner for all elapsed time, independent of the reader and playback service lifetimes. */
@Singleton
class ReadingActivityTracker internal constructor(
    private val persist: suspend (ActivityIncrement) -> Unit,
    private val scope: CoroutineScope,
    private val timeline: ActivityTimeline
) {
    @Inject constructor(repository: ReadingRepository) : this(
        { increment -> withContext(Dispatchers.IO) {
            repository.appendActivityDuration(increment.bookId, increment.date,
                increment.readingDuration, increment.listeningDuration, increment.startTime, increment.endTime)
        } },
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ActivityTimeline(SystemClock::elapsedRealtime, System::currentTimeMillis)
    )
    private val pending = ArrayDeque<ActivityIncrement>()
    private var saving = false
    private var readerOwner: Any? = null

    init {
        scope.launch {
            while (true) {
                delay(15_000)
                enqueue(timeline.checkpoint())
            }
        }
    }

    fun setReaderVisible(owner: Any, bookId: String, visible: Boolean) {
        if (visible) {
            readerOwner = owner
            enqueue(timeline.setReadingBook(bookId))
        } else if (readerOwner === owner) {
            readerOwner = null
            enqueue(timeline.setReadingBook(null))
        }
    }

    fun setListeningBook(bookId: String?) {
        enqueue(timeline.setListeningBook(bookId))
    }

    private fun enqueue(increments: List<ActivityIncrement>) {
        // Coalesce waiting increments, including repeated transitions during an I/O failure.
        increments.forEach { increment ->
            val tail = pending.lastOrNull()
            if (tail != null && tail.bookId == increment.bookId && tail.date == increment.date) {
                pending.removeLast()
                pending.addLast(tail.copy(readingDuration = tail.readingDuration + increment.readingDuration,
                    listeningDuration = tail.listeningDuration + increment.listeningDuration,
                    endTime = increment.endTime))
            } else pending.addLast(increment)
        }
        if (saving || pending.isEmpty()) return
        saving = true
        scope.launch {
            try {
                while (pending.isNotEmpty()) {
                    // Detach the batch so new activity never modifies an in-flight write.
                    val increment = pending.removeFirst()
                    try {
                        persist(increment)
                    } catch (error: Exception) {
                        pending.addFirst(increment)
                        if (error is CancellationException) throw error
                        Log.w("ReadingActivity", "Unable to save activity; retrying on next checkpoint", error)
                        break
                    }
                }
            } finally { saving = false }
        }
    }
}

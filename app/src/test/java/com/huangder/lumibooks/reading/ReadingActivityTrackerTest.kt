package com.huangder.lumibooks.reading

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingActivityTrackerTest {
    @Test fun failedWriteRetriesWithoutLosingOrDuplicatingNewActivity() = runTest {
        val epoch = Instant.parse("2026-10-26T00:00:00Z").toEpochMilli()
        val saved = mutableListOf<ActivityIncrement>()
        var fail = true
        val tracker = ReadingActivityTracker({
            if (fail) throw IllegalStateException("database unavailable")
            saved += it
        }, backgroundScope, ActivityTimeline({ testScheduler.currentTime }, { epoch + testScheduler.currentTime }))
        val owner = Any()
        tracker.setReaderVisible(owner, "book", true)
        advanceTimeBy(15_000)
        runCurrent()
        assertTrue(saved.isEmpty())
        fail = false
        advanceTimeBy(15_000)
        runCurrent()
        tracker.setReaderVisible(owner, "book", false)
        runCurrent()
        assertEquals(30_000L, saved.sumOf { it.readingDuration })
    }

    @Test fun inFlightWriteDoesNotAbsorbLaterIncrementsAndOldReaderCannotClearNewOwner() = runTest {
        val saved = mutableListOf<ActivityIncrement>()
        val gate = CompletableDeferred<Unit>()
        val tracker = ReadingActivityTracker({ gate.await(); saved += it }, backgroundScope,
            ActivityTimeline({ testScheduler.currentTime }, { testScheduler.currentTime }))
        val old = Any()
        val current = Any()
        tracker.setReaderVisible(old, "first", true)
        advanceTimeBy(1_000)
        tracker.setReaderVisible(current, "second", true)
        runCurrent()
        tracker.setReaderVisible(old, "first", false)
        advanceTimeBy(2_000)
        tracker.setListeningBook("audio")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "second"), saved.map { it.bookId })
        assertEquals(listOf(1_000L, 2_000L), saved.map { it.readingDuration })
        advanceTimeBy(2_000)
        tracker.setListeningBook(null)
        runCurrent()
        assertEquals(2_000L, saved.sumOf { it.listeningDuration })
    }
}

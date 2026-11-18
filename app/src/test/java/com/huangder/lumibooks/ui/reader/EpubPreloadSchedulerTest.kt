package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class EpubPreloadSchedulerTest {
    private val previous = EpubPageTurnHost.PreloadSlot.PREVIOUS
    private val next = EpubPageTurnHost.PreloadSlot.NEXT
    private val current = EpubPageTarget(2, 0)
    private var now = 0L
    private var foregroundReady = true
    private var idle = 0
    private val aheadReadyAt = mutableListOf<Long>()
    private var onRequest: (() -> Unit)? = null
    private val work = mutableListOf<Pair<Long, Runnable>>()
    private val requests = mutableListOf<Pair<EpubPageTurnHost.PreloadSlot, EpubPageTarget>>()
    private val ready = mutableSetOf<Pair<EpubPageTurnHost.PreloadSlot, EpubPageTarget>>()
    private val scheduler = EpubPreloadScheduler(
        post = { task, delay -> work.add(now + delay to task) },
        remove = { task -> work.removeAll { it.second === task } },
        foregroundReady = { foregroundReady },
        prepared = { slot, target -> slot to target in ready },
        prepare = { slot, target -> requests.add(slot to target); onRequest?.invoke() },
        onAheadReady = { aheadReadyAt.add(now) },
        onIdle = { idle++ }
    )

    private fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val pending = work.minByOrNull { it.first }?.takeIf { it.first <= end } ?: break
            work.remove(pending)
            now = pending.first
            pending.second.run()
        }
        now = end
    }

    @Test fun forwardChapterStartsImmediatelyBeforeReversePageInTheCurrentChapter() {
        val source = current.copy(pageIndex = 2)
        val back = source.copy(pageIndex = 1)
        val ahead = EpubPageTarget(3, 0)
        scheduler.update(source, back, ahead, direction = 1)
        advance(0)
        assertEquals(listOf(next to ahead), requests)
        advance(20)
        assertEquals(1, requests.size)
        ready.add(next to ahead)
        scheduler.onPrepared()
        assertEquals(listOf(20L), aheadReadyAt)
        advance(49)
        assertEquals(1, requests.size)
        advance(1)
        assertEquals(listOf(next to ahead, previous to back), requests)
        ready.add(previous to back)
        scheduler.onPrepared()
        assertEquals(1, idle)
        assertTrue(work.isEmpty())
    }

    @Test fun backwardChapterAlsoWinsOverForwardPageInTheCurrentChapter() {
        val back = EpubPageTarget(1, Int.MAX_VALUE)
        val ahead = current.copy(pageIndex = 1)
        scheduler.update(current, back, ahead, direction = -1)
        advance(0)
        assertEquals(listOf(previous to back), requests)
        ready.add(previous to back)
        scheduler.onPrepared()
        advance(50)
        assertEquals(listOf(previous to back, next to ahead), requests)
    }

    @Test fun readyAheadStartsWarmWorkEvenWhileReversePreparationIsPausedByAnimation() {
        val back = EpubPageTarget(1, Int.MAX_VALUE)
        val ahead = current.copy(pageIndex = 1)
        ready.add(next to ahead)
        foregroundReady = false
        scheduler.update(current, back, ahead, direction = 1)
        assertEquals(listOf(0L), aheadReadyAt)
        advance(200)
        assertTrue(requests.isEmpty())
        foregroundReady = true
        advance(50)
        assertEquals(listOf(previous to back), requests)
    }

    @Test fun drawFenceStartsWarmWorkWithoutWaitingForAnimationToFinish() {
        val ahead = EpubPageTarget(3, 0)
        scheduler.update(current, EpubPageTarget(1, 0), ahead, direction = 1)
        advance(0)
        foregroundReady = false
        ready.add(next to ahead)
        scheduler.onPrepared()
        assertEquals(listOf(0L), aheadReadyAt)
        advance(200)
        assertEquals(listOf(next to ahead), requests)
    }

    @Test fun consecutiveOnePageChaptersNeverPayAFixedCrossingCooldown() {
        repeat(12) { chapter ->
            val source = EpubPageTarget(chapter, 0)
            val ahead = EpubPageTarget(chapter + 1, 0)
            val back = if (chapter > 0) EpubPageTarget(chapter - 1, Int.MAX_VALUE) else null
            back?.let { ready.add(previous to it) }
            scheduler.update(source, back, ahead, direction = 1)
            val requestedAt = now
            advance(0)
            assertEquals(next to ahead, requests.last())
            // Stand in for the new document's layout and draw fence.
            advance(20)
            ready.add(next to ahead)
            scheduler.onPrepared()
            assertEquals(requestedAt + 20L, aheadReadyAt.last())
            assertTrue(work.isEmpty())
        }
        assertEquals(12, requests.size)
        assertEquals(12, aheadReadyAt.size)
    }

    @Test fun removedPollCannotRunTheFollowingTaskBeforeItsScheduledTime() {
        val ahead = EpubPageTarget(3, 0)
        scheduler.update(current, EpubPageTarget(1, 0), ahead, direction = 1)
        advance(0)
        val stalePoll = work.single().second
        ready.add(next to ahead)
        scheduler.onPrepared()
        stalePoll.run()
        assertEquals(1, requests.size)
        assertEquals(1, work.size)
        advance(50)
        assertEquals(2, requests.size)
    }

    @Test fun synchronousPreparationCallbackAdvancesOnlyOnce() {
        val ahead = EpubPageTarget(3, 0)
        onRequest = {
            ready.add(requests.last())
            scheduler.onPrepared()
        }
        scheduler.update(current, EpubPageTarget(1, 0), ahead, direction = 1)
        advance(100)
        assertEquals(2, requests.size)
        assertEquals(listOf(0L), aheadReadyAt)
        assertEquals(1, idle)
        assertTrue(work.isEmpty())
    }

    @Test fun cancellationDuringPreparationCannotRestartTheOldPlan() {
        onRequest = { scheduler.cancel() }
        scheduler.update(current, null, EpubPageTarget(3, 0), direction = 1)
        advance(500)
        assertEquals(1, requests.size)
        assertTrue(aheadReadyAt.isEmpty())
        assertTrue(work.isEmpty())
    }

    @Test fun foregroundReflowPausesPreparationAndCancellationFencesOldCallbacks() {
        foregroundReady = false
        scheduler.update(current, null, current.copy(pageIndex = 1), direction = 1)
        val stale = work.single().second
        advance(500)
        assertTrue(requests.isEmpty())
        scheduler.cancel()
        foregroundReady = true
        stale.run()
        advance(1000)
        assertTrue(requests.isEmpty())
        assertTrue(work.isEmpty())
        scheduler.update(current, null, current.copy(pageIndex = 1), direction = 1)
        advance(0)
        assertEquals(1, requests.size)
    }

    @Test fun stuckBackgroundChapterDoesNotBlockTheOtherNeighbourForever() {
        scheduler.update(current, EpubPageTarget(1, 0), EpubPageTarget(2, 1), direction = 1)
        advance(3350)
        assertEquals(listOf(next, previous), requests.map { it.first })
        assertTrue(aheadReadyAt.isEmpty())
    }
}

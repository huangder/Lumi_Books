package com.huangder.lumibooks.ui.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RasterWorkSchedulerTest {
    @Test fun `visible work overtakes queued speculative work`() = runTest {
        val scheduler = RasterWorkScheduler(backgroundScope, 1)
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        launch { scheduler.execute("running", 0) { gate.await() } }
        runCurrent()
        launch { scheduler.execute("background", 5, background = true) { order += "background" } }
        launch { scheduler.execute("visible", 0) { order += "visible" } }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("visible", "background"), order)
    }

    @Test fun `same work is shared and cancellation does not discard a running result`() = runTest {
        val scheduler = RasterWorkScheduler(backgroundScope, 2)
        val gate = CompletableDeferred<Unit>()
        var decodes = 0
        val first = async { scheduler.execute("page", 3, background = true) { decodes++; gate.await(); 42 } }
        runCurrent()
        val second = async { scheduler.execute<Int>("page", 0) { error("duplicate decode") } }
        runCurrent()
        first.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(42, second.await())
        assertEquals(1, decodes)
    }

    @Test fun `cancelled queued work never executes after jump`() = runTest {
        val scheduler = RasterWorkScheduler(backgroundScope, 1)
        val gate = CompletableDeferred<Unit>()
        var staleStarted = false
        launch { scheduler.execute("running", 0) { gate.await() } }
        runCurrent()
        val obsolete = launch { scheduler.execute("stale", 1) { staleStarted = true } }
        runCurrent()
        obsolete.cancel()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertFalse(staleStarted)
    }

    @Test fun `one background slot leaves capacity for foreground and high resolution is serialized`() = runTest {
        val scheduler = RasterWorkScheduler(backgroundScope, 2)
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        launch { scheduler.execute("bg1", 3, background = true) { started += "bg1"; gate.await() } }
        launch { scheduler.execute("bg2", 3, background = true) { started += "bg2"; gate.await() } }
        runCurrent()
        assertEquals(listOf("bg1"), started)
        launch { scheduler.execute("high1", 1, large = true) { started += "high1"; gate.await() } }
        launch { scheduler.execute("high2", 1, large = true) { started += "high2"; gate.await() } }
        runCurrent()
        assertEquals(listOf("bg1", "high1"), started)
        gate.complete(Unit)
        runCurrent()
        assertTrue(started.containsAll(listOf("bg2", "high2")))
    }

    @Test fun `close drains native work and rejects queued and new work`() = runTest {
        val scheduler = RasterWorkScheduler(backgroundScope, 1)
        val gate = CompletableDeferred<Unit>()
        val running = async { scheduler.execute("running", 0) { gate.await(); 9 } }
        runCurrent()
        val queued = async { scheduler.execute("queued", 0) { error("must not run") } }
        runCurrent()
        scheduler.close()
        val drained = async { scheduler.awaitClosed() }
        runCurrent()
        assertTrue(queued.isCancelled)
        assertFalse(drained.isCompleted)
        gate.complete(Unit)
        runCurrent()
        assertEquals(9, running.await())
        assertTrue(drained.isCompleted)
        val rejected = async { scheduler.execute("new", 0) { 1 } }
        runCurrent()
        assertTrue(rejected.isCancelled)
    }
}

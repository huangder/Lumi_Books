package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCacheRecoveryTest {
    @Test
    fun `ordinary foreground transition does not recover`() {
        assertFalse(
            shouldRecoverReaderAfterCacheClear(
                observedGeneration = 4L,
                currentGeneration = 4L,
                hasLoadedBook = true,
                recoveryRunning = false
            )
        )
    }

    @Test
    fun `changed generation recovers one loaded session`() {
        assertTrue(
            shouldRecoverReaderAfterCacheClear(
                observedGeneration = 4L,
                currentGeneration = 5L,
                hasLoadedBook = true,
                recoveryRunning = false
            )
        )
        assertFalse(
            shouldRecoverReaderAfterCacheClear(
                observedGeneration = 5L,
                currentGeneration = 5L,
                hasLoadedBook = true,
                recoveryRunning = false
            )
        )
    }

    @Test
    fun `recovery waits for a loaded idle session`() {
        assertFalse(
            shouldRecoverReaderAfterCacheClear(
                observedGeneration = 4L,
                currentGeneration = 5L,
                hasLoadedBook = false,
                recoveryRunning = false
            )
        )
        assertFalse(
            shouldRecoverReaderAfterCacheClear(
                observedGeneration = 4L,
                currentGeneration = 5L,
                hasLoadedBook = true,
                recoveryRunning = true
            )
        )
    }
}

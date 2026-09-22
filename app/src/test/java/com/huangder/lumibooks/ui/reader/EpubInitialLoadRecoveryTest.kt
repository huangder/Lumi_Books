package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubInitialLoadRecoveryTest {
    @Test
    fun `first failure retries and second failure falls back`() {
        val recovery = EpubInitialLoadRecovery(maxRetries = 1)
        val first = recovery.begin(chapterIndex = 11)

        val retry = recovery.fail(first) as EpubInitialLoadRecoveryAction.Retry
        assertEquals(11, retry.attempt.chapterIndex)
        assertEquals(1, retry.attempt.retryCount)
        assertTrue(recovery.isCurrent(retry.attempt))

        assertEquals(
            EpubInitialLoadRecoveryAction.Fallback(retry.attempt),
            recovery.fail(retry.attempt)
        )
        assertNull(recovery.current())
    }

    @Test
    fun `ready document ignores later failure`() {
        val recovery = EpubInitialLoadRecovery()
        val attempt = recovery.begin(chapterIndex = 3)

        assertTrue(recovery.complete(attempt))
        assertEquals(EpubInitialLoadRecoveryAction.Ignore, recovery.fail(attempt))
        assertFalse(recovery.complete(attempt))
    }

    @Test
    fun `new document rejects callback from old generation`() {
        val recovery = EpubInitialLoadRecovery()
        val oldAttempt = recovery.begin(chapterIndex = 5)
        val currentAttempt = recovery.begin(chapterIndex = 6)

        assertEquals(EpubInitialLoadRecoveryAction.Ignore, recovery.fail(oldAttempt))
        assertTrue(recovery.isCurrent(currentAttempt))
        assertEquals(currentAttempt, recovery.current(chapterIndex = 6))
        assertNull(recovery.current(chapterIndex = 5))
    }

    @Test
    fun `cancel invalidates pending attempt`() {
        val recovery = EpubInitialLoadRecovery()
        val attempt = recovery.begin(chapterIndex = 0)

        recovery.cancel()

        assertEquals(EpubInitialLoadRecoveryAction.Ignore, recovery.fail(attempt))
    }
}

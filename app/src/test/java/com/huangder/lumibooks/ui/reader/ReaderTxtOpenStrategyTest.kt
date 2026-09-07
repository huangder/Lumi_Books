package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.parser.TxtIndexState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTxtOpenStrategyTest {
    private val threshold = 10L * 1024L * 1024L

    @Test
    fun `large txt without saved position can use fast open`() {
        assertTrue(
            shouldUseFastTxtOpen(
                fileSizeBytes = threshold,
                largeFileThresholdBytes = threshold,
                hasPersistedReaderPosition = false,
                readingProgress = 0f
            )
        )
    }

    @Test
    fun `small txt always uses semantic open`() {
        assertFalse(
            shouldUseFastTxtOpen(
                fileSizeBytes = threshold - 1,
                largeFileThresholdBytes = threshold,
                hasPersistedReaderPosition = false,
                readingProgress = 0f
            )
        )
    }

    @Test
    fun `saved locator disables fast open`() {
        assertFalse(
            shouldUseFastTxtOpen(
                fileSizeBytes = threshold * 2,
                largeFileThresholdBytes = threshold,
                hasPersistedReaderPosition = true,
                readingProgress = 0f
            )
        )
    }

    @Test
    fun `positive saved progress disables fast open`() {
        assertFalse(
            shouldUseFastTxtOpen(
                fileSizeBytes = threshold * 2,
                largeFileThresholdBytes = threshold,
                hasPersistedReaderPosition = false,
                readingProgress = 0.25f
            )
        )
    }

    @Test
    fun `fast partial txt never persists semantic locator`() {
        assertFalse(shouldPersistReaderLocator("TXT", TxtIndexState.FAST_PARTIAL))
        assertTrue(shouldPersistReaderLocator("TXT", TxtIndexState.COMPLETE))
        assertTrue(shouldPersistReaderLocator("EPUB", TxtIndexState.FAST_PARTIAL))
    }
}

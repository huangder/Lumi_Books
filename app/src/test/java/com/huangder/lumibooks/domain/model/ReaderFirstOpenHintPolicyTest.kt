package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFirstOpenHintPolicyTest {
    @Test
    fun `hint stops after three acknowledgements`() {
        assertTrue(shouldShow(acknowledgementCount = 0))
        assertTrue(shouldShow(acknowledgementCount = 2))
        assertFalse(shouldShow(acknowledgementCount = 3))
    }

    @Test
    fun `hint stays hidden when user disables it`() {
        assertFalse(shouldShow(acknowledgementCount = 0, disabled = true))
    }

    @Test
    fun `hint stays scoped to supported formats and new books`() {
        assertFalse(shouldShow(acknowledgementCount = 0, isSupportedFormat = false))
        assertFalse(shouldShow(acknowledgementCount = 0, wasShownForBook = true))
    }

    private fun shouldShow(
        acknowledgementCount: Int,
        isSupportedFormat: Boolean = true,
        wasShownForBook: Boolean = false,
        disabled: Boolean = false
    ): Boolean = ReaderFirstOpenHintPolicy.shouldShow(
        isSupportedFormat = isSupportedFormat,
        wasShownForBook = wasShownForBook,
        acknowledgementCount = acknowledgementCount,
        disabled = disabled
    )
}

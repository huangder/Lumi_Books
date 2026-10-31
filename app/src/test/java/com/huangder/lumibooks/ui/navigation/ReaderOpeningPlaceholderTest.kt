package com.huangder.lumibooks.ui.navigation

import com.huangder.lumibooks.ui.reader.ReaderDocumentState
import org.junit.Assert.*
import org.junit.Test

class ReaderOpeningPlaceholderTest {
    @Test fun `initial metadata load does not construct a default text reader`() {
        assertFalse(shouldMountReaderContent(ReaderDocumentState()))
    }

    @Test fun `loading failures still mount the reader error surface`() {
        assertTrue(shouldMountReaderContent(ReaderDocumentState(error = "missing book")))
        assertTrue(shouldMountReaderContent(ReaderDocumentState(isLoading = false)))
    }

    @Test fun `nothing mounts while the hero window is still moving`() {
        assertFalse(shouldMountReaderContent(ReaderDocumentState(error = "missing book"), windowReady = false))
        assertFalse(shouldMountReaderContent(ReaderDocumentState(isLoading = false), windowReady = false))
    }
}

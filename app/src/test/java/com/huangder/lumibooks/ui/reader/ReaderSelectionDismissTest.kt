package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionDismissTest {
    @Test
    fun continuousReaderTapDismissesSelectionMenuBeforeTogglingReaderMenu() {
        var dismissals = 0
        var toggles = 0

        handleContinuousReaderSurfaceTap(
            selectionMenuVisible = true,
            dismissSelectionMenu = { dismissals++ },
            toggleReaderMenu = { toggles++ }
        )

        org.junit.Assert.assertEquals(1, dismissals)
        org.junit.Assert.assertEquals(0, toggles)
    }

    @Test
    fun continuousReaderTapTogglesReaderMenuWithoutSelectionMenu() {
        var dismissals = 0
        var toggles = 0

        handleContinuousReaderSurfaceTap(
            selectionMenuVisible = false,
            dismissSelectionMenu = { dismissals++ },
            toggleReaderMenu = { toggles++ }
        )

        org.junit.Assert.assertEquals(0, dismissals)
        org.junit.Assert.assertEquals(1, toggles)
    }

    @Test
    fun duplicateContinuousReaderCallbacksToggleTheMenuOnce() {
        val gate = ContinuousMenuTapGate()
        var toggles = 0

        assertTrue(gate.dispatch(1_000L) { toggles++ })
        assertFalse(gate.dispatch(1_001L) { toggles++ })
        assertTrue(gate.dispatch(1_081L) { toggles++ })
        assertTrue(gate.dispatch(900L) { toggles++ })

        org.junit.Assert.assertEquals(3, toggles)
    }

    @Test
    fun annotationOnlyOriginalLayoutMenuDoesNotClearDocumentSelection() {
        assertFalse(
            shouldClearSelectionAfterMenuDismiss(
                isBookLayout = true,
                annotationOnly = true
            )
        )
    }

    @Test
    fun realSelectionsAndOtherLayoutsStillClearTheirSelection() {
        assertTrue(shouldClearSelectionAfterMenuDismiss(isBookLayout = true, annotationOnly = false))
        assertTrue(shouldClearSelectionAfterMenuDismiss(isBookLayout = false, annotationOnly = true))
    }
}

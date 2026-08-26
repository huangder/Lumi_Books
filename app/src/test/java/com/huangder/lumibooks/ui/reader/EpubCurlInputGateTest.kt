package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubCurlInputGateTest {
    @Test
    fun idleReadyTurnIsAccepted() {
        assertEquals(
            EpubCurlTurnDisposition.ACCEPT,
            epubCurlTurnDisposition(idle = true, targetExists = true, targetReady = true)
        )
    }

    @Test
    fun busyAndLoadingTurnsAreQueued() {
        assertEquals(
            EpubCurlTurnDisposition.QUEUE,
            epubCurlTurnDisposition(idle = false, targetExists = true, targetReady = true)
        )
        assertEquals(
            EpubCurlTurnDisposition.QUEUE,
            epubCurlTurnDisposition(idle = true, targetExists = true, targetReady = false)
        )
    }

    @Test
    fun busyTurnQueuesAgainstThePageThatWillBecomeCurrent() {
        assertEquals(
            EpubCurlTurnDisposition.QUEUE,
            epubCurlTurnDisposition(idle = false, targetExists = false, targetReady = false)
        )
    }

    @Test
    fun missingTargetPassesBookBoundaryHandlingToHost() {
        assertEquals(
            EpubCurlTurnDisposition.PASS_BOUNDARY,
            epubCurlTurnDisposition(idle = true, targetExists = false, targetReady = false)
        )
    }
}

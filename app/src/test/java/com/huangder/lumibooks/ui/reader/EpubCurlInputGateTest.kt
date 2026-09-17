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

    @Test
    fun stalledCurlTurnFallsBackToDirectTurn() {
        // 排队中、空闲、目标页迟迟没准备好：应该退化成直接翻页。
        assertEquals(
            true,
            shouldFallBackToDirectCurlTurn(
                turnPending = true,
                idle = true,
                waitingForTarget = false,
                targetReady = false,
                potentialTurn = true
            )
        )
        // 目标页已就绪：交给正常卷曲动画，不要抢。
        assertEquals(
            false,
            shouldFallBackToDirectCurlTurn(
                turnPending = true,
                idle = true,
                waitingForTarget = false,
                targetReady = true,
                potentialTurn = true
            )
        )
        // 没有排队的翻页、正在动画、正在等章节交接、或已经到边界：都不兜底。
        assertEquals(
            false,
            shouldFallBackToDirectCurlTurn(
                turnPending = false,
                idle = true,
                waitingForTarget = false,
                targetReady = false,
                potentialTurn = true
            )
        )
        assertEquals(
            false,
            shouldFallBackToDirectCurlTurn(
                turnPending = true,
                idle = false,
                waitingForTarget = false,
                targetReady = false,
                potentialTurn = true
            )
        )
        assertEquals(
            false,
            shouldFallBackToDirectCurlTurn(
                turnPending = true,
                idle = true,
                waitingForTarget = true,
                targetReady = false,
                potentialTurn = true
            )
        )
        assertEquals(
            false,
            shouldFallBackToDirectCurlTurn(
                turnPending = true,
                idle = true,
                waitingForTarget = false,
                targetReady = false,
                potentialTurn = false
            )
        )
    }
}

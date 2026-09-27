package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class EpubQueuedTurnTest {
    @Test fun queuedTurnCannotReturnAfterReversalOrAnotherVisit() {
        val queued = EpubQueuedTurn(EpubPageTarget(0, 2), EpubPageTarget(0, 3), 4, 1)
        assertTrue(queued.accepts(EpubPageTarget(0, 2), 4, EpubPageTarget(0, 3)))
        assertFalse(queued.accepts(EpubPageTarget(0, 1), 5, EpubPageTarget(0, 2)))
        assertFalse(queued.accepts(EpubPageTarget(0, 2), 6, EpubPageTarget(0, 3)))
        assertFalse(queued.accepts(EpubPageTarget(0, 2), 4, null))
    }
    @Test fun previousChapterEndResolvesWithoutChangingChapterIdentity() {
        val queued = EpubQueuedTurn(EpubPageTarget(2, 0), EpubPageTarget(1, Int.MAX_VALUE), 7, -1)
        assertTrue(queued.accepts(EpubPageTarget(2, 0), 7, EpubPageTarget(1, 19)))
        assertFalse(queued.accepts(EpubPageTarget(2, 0), 7, EpubPageTarget(0, 19)))
    }
}

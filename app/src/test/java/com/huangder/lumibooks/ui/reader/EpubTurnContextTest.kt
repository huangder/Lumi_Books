package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.reader.engine.PageAnimationController.Direction
import org.junit.Assert.*
import org.junit.Test

class EpubTurnContextTest {
    @Test fun frozenSheetsFollowBookOrderAndRejectStaleRevisions() {
        for (rtl in listOf(false, true)) for (direction in listOf(Direction.NEXT, Direction.PREV)) {
            val current = EpubTurnPage(Any(), EpubPageTarget(2, 3), 4, 2, 0)
            val target = EpubTurnPage(Any(), EpubPageTarget(2, if (direction == Direction.NEXT) 4 else 2), 6, 1, 9)
            val turn = EpubTurnContext(1, direction, rtl, current, target)
            assertSame(if (direction == Direction.NEXT) current else target, turn.upper)
            assertSame(if (direction == Direction.NEXT) target else current, turn.lower)
            assertTrue(turn.matches(target.view, 6, 1))
            assertFalse(turn.matches(target.view, 5, 1))
            assertFalse(turn.matches(target.view, 6, 2))
            assertFalse(turn.matches(Any(), 6, 1))
            assertTrue(turn.finishOnce())
            assertFalse(turn.finishOnce())
        }
    }
}

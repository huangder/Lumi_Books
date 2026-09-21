package com.huangder.lumibooks.ui.reader.engine

import com.huangder.lumibooks.ui.reader.engine.PageAnimationController.Direction
import org.junit.Assert.*
import org.junit.Test

class PageTurnLayersTest {
    @Test
    fun forwardExposesNextSheetAndBackwardCoversCurrentInBothReadingDirections() {
        for (reversed in listOf(false, true)) {
            val nextSign = if (reversed) 1f else -1f
            for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val next = horizontalPageFrame(Direction.NEXT, nextSign * 400f * progress, 400f, reversed)
                assertEquals(PageTurnRole.CURRENT, next.layers.upper)
                assertEquals(PageTurnRole.NEXT, next.layers.lower)
                assertEquals(nextSign * 400f * progress, next.currentX, 0.001f)
                assertEquals(-nextSign * 120f * (1f - progress), next.nextX, 0.001f)
                val previous = horizontalPageFrame(Direction.PREV, -nextSign * 400f * progress, 400f, reversed)
                assertEquals(PageTurnRole.PREVIOUS, previous.layers.upper)
                assertEquals(PageTurnRole.CURRENT, previous.layers.lower)
                assertEquals(nextSign * 400f * (1f - progress), previous.previousX, 0.001f)
                assertEquals(-nextSign * 120f * progress, previous.currentX, 0.001f)
            }
        }
    }

    @Test
    fun cancelledTurnRestoresCurrentAndLargeDragCannotSkipSheets() {
        for (reversed in listOf(false, true)) {
            val sign = if (reversed) 1f else -1f
            val overdrag = horizontalPageFrame(Direction.NEXT, 2000f * sign, 400f, reversed)
            assertEquals(400f * sign, overdrag.currentX, 0f)
            assertEquals(0f, overdrag.nextX, 0f)
            val idle = horizontalPageFrame(Direction.NONE, 100f, 400f, reversed)
            assertEquals(0f, idle.currentX, 0f)
            assertEquals(PageTurnRole.CURRENT, idle.layers.upper)
        }
    }

    @Test
    fun liveChildOrderMatchesReadingOrderInsteadOfPhysicalAxis() {
        for (reversed in listOf(false, true)) {
            assertEquals(
                listOf(PageTurnRole.NEXT, PageTurnRole.CURRENT),
                pageTurnDrawingOrder(Direction.NEXT)
            )
            assertEquals(
                listOf(PageTurnRole.CURRENT, PageTurnRole.PREVIOUS),
                pageTurnDrawingOrder(Direction.PREV)
            )
            // Reading direction changes motion only, never which sheet is above.
            assertEquals(
                PageTurnRole.CURRENT,
                horizontalPageFrame(Direction.NEXT, 0f, 400f, reversed).layers.upper
            )
        }
    }
}

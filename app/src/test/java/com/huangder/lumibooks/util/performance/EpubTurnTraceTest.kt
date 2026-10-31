package com.huangder.lumibooks.util.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTurnTraceTest {
    @Test
    fun formatSkipsNullsAndKeepsEachValueOneToken() {
        assertEquals(
            "t=5 ev=swipe dir=1 path=wait_target note=a_b",
            EpubTurnTrace.format(5L, "swipe", "dir" to 1, "skip" to null, "path" to "wait_target", "note" to "a b")
        )
    }

    @Test
    fun configKeyDiffNamesOnlyChangedTokens() {
        assertEquals("initial", EpubTurnTrace.configKeyDiff("", "a|b"))
        assertEquals("same", EpubTurnTrace.configKeyDiff("a|b", "a|b"))
        assertEquals("#2:word->paragraph", EpubTurnTrace.configKeyDiff("x|y|word|z", "x|y|paragraph|z"))
        assertEquals("tokens:2->3,#2:null->c", EpubTurnTrace.configKeyDiff("a|b", "a|b|c"))
    }

    @Test
    fun gestureWaitRunsFromIntentToSheetMotion() {
        val trace = EpubGestureTrace(12f)
        trace.down(100L, 300f, 400f)
        assertFalse(trace.move(110L, 305f, 402f, vertical = false))
        assertTrue(trace.move(120L, 280f, 402f, vertical = false))
        assertFalse(trace.move(130L, 250f, 402f, vertical = false))
        trace.moved(320L)
        val fields = trace.finish(400L, cancelled = false)!!.toMap()
        assertEquals(1, fields["moved"])
        assertEquals(200L, fields["waitMs"])
        assertEquals(280L, fields["heldMs"])
    }

    @Test
    fun tapsAreSkippedAndStalledSwipesHaveNoWait() {
        val tap = EpubGestureTrace(12f).apply { down(0L, 10f, 10f) }
        assertNull(tap.finish(80L, cancelled = false))

        val stalled = EpubGestureTrace(12f).apply {
            down(0L, 300f, 400f)
            move(20L, 260f, 400f, vertical = false)
        }
        val fields = stalled.finish(900L, cancelled = false)!!.toMap()
        assertEquals(0, fields["moved"])
        assertNull(fields["waitMs"])
        assertEquals(880L, fields["heldMs"])
    }
}

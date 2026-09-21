package com.huangder.lumibooks.ui.reader.engine

import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAnimationTapGateTest {
    @Test
    fun `cancelled center gesture is never a tap`() {
        assertFalse(
            isPageAnimationShortTap(
                actionMasked = MotionEvent.ACTION_CANCEL,
                hasMoved = false,
                elapsedMillis = 40L
            )
        )
    }

    @Test
    fun `only a stationary action up is a tap`() {
        assertTrue(
            isPageAnimationShortTap(
                actionMasked = MotionEvent.ACTION_UP,
                hasMoved = false,
                elapsedMillis = 120L,
                verticalDistancePx = 2f
            )
        )
        assertFalse(
            isPageAnimationShortTap(
                actionMasked = MotionEvent.ACTION_UP,
                hasMoved = true,
                elapsedMillis = 120L
            )
        )
    }
}

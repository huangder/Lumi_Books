package com.huangder.lumibooks.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRouteMotionTest {
    @Test fun `reader route uses a short symmetric fade`() {
        assertEquals(200, ReaderRouteMotion.FADE_DURATION_MS)
        assertEquals(0f, ReaderRouteMotion.Easing.transform(0f), 0f)
        assertEquals(1f, ReaderRouteMotion.Easing.transform(1f), 0f)
        assertTrue(ReaderRouteMotion.Easing.transform(0.5f) in 0f..1f)
    }
}

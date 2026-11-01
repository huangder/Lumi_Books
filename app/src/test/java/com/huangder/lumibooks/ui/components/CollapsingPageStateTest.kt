package com.huangder.lumibooks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsingPageStateTest {
    @Test fun collapseAndExpandConsumeOnlyTheAvailableTitleHeight() {
        val state = CollapsingPageState()
        state.updateTitleHeight(80f)
        assertEquals(-40f, state.consumeDelta(-40f), 0.001f)
        assertEquals(0.5f, state.progress, 0.001f)
        assertEquals(-40f, state.consumeDelta(-100f), 0.001f)
        assertEquals(1f, state.progress, 0.001f)
        assertEquals(0f, state.consumeDelta(-20f), 0.001f)
        assertEquals(80f, state.consumeDelta(100f), 0.001f)
        assertEquals(0f, state.progress, 0.001f)
    }

    @Test fun resizingOrRestoringALongTitlePreservesTheFraction() {
        val state = CollapsingPageState(0.5f)
        state.updateTitleHeight(160f)
        assertEquals(0.5f, state.progress, 0.001f)
        assertEquals(80f, state.consumeDelta(100f), 0.001f)
    }

    @Test fun invalidMeasurementsAndDeltasDoNotChangePosition() {
        val state = CollapsingPageState()
        assertEquals(0f, state.consumeDelta(-50f), 0.001f)
        state.updateTitleHeight(Float.NaN)
        state.updateTitleHeight(0f)
        assertEquals(0f, state.consumeDelta(-50f), 0.001f)
        state.updateTitleHeight(80f)
        assertEquals(0f, state.consumeDelta(Float.NaN), 0.001f)
        assertEquals(0f, state.progress, 0.001f)
    }
}

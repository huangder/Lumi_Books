package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageTransitionTest {
    @Test
    fun `normalizes every persisted transition key`() {
        ReaderPageTransition.entries.forEach { transition ->
            assertEquals(transition, ReaderPageTransition.fromKey(transition.key))
            assertEquals(transition.key, ReaderPageTransition.normalizeKey(transition.key))
        }
    }

    @Test
    fun `unknown and runtime-only values fall back to slide`() {
        assertEquals(ReaderPageTransition.SLIDE, ReaderPageTransition.fromKey(null))
        assertEquals(ReaderPageTransition.SLIDE, ReaderPageTransition.fromKey("none"))
        assertEquals("slide", ReaderPageTransition.normalizeKey("unsupported"))
    }
}

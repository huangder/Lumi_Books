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
    fun `unknown values fall back to slide`() {
        assertEquals(ReaderPageTransition.SLIDE, ReaderPageTransition.fromKey(null))
        assertEquals(ReaderPageTransition.NONE, ReaderPageTransition.fromKey("none"))
        assertEquals("slide", ReaderPageTransition.normalizeKey("unsupported"))
    }

    @Test
    fun `e ink preserves fade and wave while other animations use instant turns`() {
        ReaderPageTransition.entries.forEach { transition ->
            val expected = when (transition) {
                ReaderPageTransition.NONE, ReaderPageTransition.FADE, ReaderPageTransition.WAVE -> transition.key
                else -> "none"
            }
            assertEquals(expected, ReaderPageTransition.effectiveKey(transition.key, true))
            assertEquals(transition.key, ReaderPageTransition.effectiveKey(transition.key, false))
        }
    }

    @Test
    fun `instant selection survives persistence and e ink toggles`() {
        val saved = ReaderPageTransition.normalizeKey("none")
        assertEquals("none", ReaderPageTransition.effectiveKey(saved, true))
        assertEquals("none", ReaderPageTransition.effectiveKey(saved, false))
    }
}

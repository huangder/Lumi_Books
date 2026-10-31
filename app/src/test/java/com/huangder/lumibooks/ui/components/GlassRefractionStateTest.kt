package com.huangder.lumibooks.ui.components

import org.junit.Assert.*
import org.junit.Test

class GlassRefractionStateTest {
    @Test fun successfulLensNeverAcquiresTheStrongFallbackBlur() {
        for (fallback in listOf(8f, 12f, 18f)) {
            val effects = mutableListOf<String>()
            val refracting = GlassRefractionState().render(true, 3f, fallback,
                resetEffects = { fail("A working lens must not reset the effect chain") },
                blur = { effects += "blur:$it" }, refract = { effects += "lens" },
                onFailure = { throw it })
            assertTrue(refracting)
            assertEquals(listOf("blur:3.0", "lens"), effects)
        }
    }

    @Test fun unsupportedLensRetainsVisibleBlurEvenAtFullTransparency() {
        val effects = mutableListOf<Float>()
        val state = GlassRefractionState()
        assertFalse(state.render(false, 0f, 12f, {}, effects::add,
            { fail("An unsupported lens must never run") }, { throw it }))
        assertEquals(listOf(12f), effects)
        assertFalse("A disabled lens is not a rejected shader", state.failed)
    }

    @Test fun rejectedShaderClearsItsPartialEffectsAndPaddingAndDoesNotRetry() {
        val state = GlassRefractionState()
        val effects = mutableListOf("vibrancy")
        var padding = 0f
        var attempts = 0
        var failures = 0
        fun draw(): Boolean = state.render(true, 3f, 12f,
            resetEffects = { effects.clear(); effects += "vibrancy"; padding = 0f },
            blur = { effects += "blur:$it" },
            refract = {
                attempts++
                effects += "partial-lens"
                padding = 24f
                throw IllegalStateException("GPU rejected the shader")
            },
            onFailure = { failures++ })
        assertFalse(draw())
        assertEquals(listOf("vibrancy", "blur:12.0"), effects)
        assertEquals(0f, padding, 0f)
        assertTrue(state.failed)
        effects.clear(); effects += "vibrancy"
        assertFalse(draw())
        assertEquals(listOf("vibrancy", "blur:12.0"), effects)
        assertEquals(1, attempts)
        assertEquals(1, failures)
    }

    @Test fun enablingRefractionAfterAPreferenceChangeDoesNotRemainInFallback() {
        val state = GlassRefractionState()
        state.render(false, 2f, 8f, {}, {}, { fail("Disabled") }, { throw it })
        assertTrue(state.render(true, 2f, 8f, {}, {}, {}, { throw it }))
    }
}

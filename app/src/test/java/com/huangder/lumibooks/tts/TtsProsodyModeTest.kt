package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsProsodyModeTest {
    @Test
    fun `new users follow engine while legacy values remain overrides`() {
        assertEquals(
            TtsProsodyMode.FOLLOW_ENGINE,
            TtsProsodyMode.resolve(storedMode = null, hasLegacyValue = false)
        )
        assertEquals(
            TtsProsodyMode.OVERRIDE,
            TtsProsodyMode.resolve(storedMode = null, hasLegacyValue = true)
        )
    }

    @Test
    fun `explicit mode wins over legacy value presence`() {
        assertEquals(
            TtsProsodyMode.FOLLOW_ENGINE,
            TtsProsodyMode.resolve(TtsProsodyMode.FOLLOW_ENGINE.storedValue, true)
        )
        assertEquals(
            TtsProsodyMode.OVERRIDE,
            TtsProsodyMode.resolve(TtsProsodyMode.OVERRIDE.storedValue, false)
        )
    }

    @Test
    fun `prosody settings normalize both values without changing modes`() {
        assertEquals(
            TtsProsodySettings(
                speechRate = 5f,
                speechRateMode = TtsProsodyMode.OVERRIDE,
                pitch = 0.5f,
                pitchMode = TtsProsodyMode.FOLLOW_ENGINE
            ),
            TtsProsodySettings(
                speechRate = 99f,
                speechRateMode = TtsProsodyMode.OVERRIDE,
                pitch = 0.1f,
                pitchMode = TtsProsodyMode.FOLLOW_ENGINE
            ).normalized()
        )
    }
}

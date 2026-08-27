package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsProviderSelectionTest {
    @Test
    fun `provider selections round trip through stored values`() {
        val selections = listOf(
            TtsProviderSelection.SystemDefault,
            TtsProviderSelection.AiModel,
            TtsProviderSelection.AndroidEngine("com.example.multitts")
        )

        selections.forEach { selection ->
            assertEquals(
                selection,
                TtsProviderSelection.fromStoredValue(selection.storedValue)
            )
        }
    }

    @Test
    fun `unknown and malformed stored values are rejected`() {
        assertNull(TtsProviderSelection.fromStoredValue(null))
        assertNull(TtsProviderSelection.fromStoredValue("unknown"))
        assertNull(TtsProviderSelection.fromStoredValue("android:"))
    }

    @Test
    fun `configured AI migration takes precedence over legacy Android engine`() {
        assertEquals(
            TtsProviderSelection.AiModel,
            TtsProviderSelection.resolve(
                storedValue = null,
                legacyEnginePackage = "com.example.legacy",
                externalTtsConfigured = true
            )
        )
    }

    @Test
    fun `legacy Android engine migrates when AI is not configured`() {
        assertEquals(
            TtsProviderSelection.AndroidEngine("com.example.legacy"),
            TtsProviderSelection.resolve(
                storedValue = null,
                legacyEnginePackage = "com.example.legacy",
                externalTtsConfigured = false
            )
        )
    }

    @Test
    fun `system default is used when no provider was previously configured`() {
        assertEquals(
            TtsProviderSelection.SystemDefault,
            TtsProviderSelection.resolve(
                storedValue = null,
                legacyEnginePackage = null,
                externalTtsConfigured = false
            )
        )
    }

    @Test
    fun `invalid persisted provider falls back to system default without remigration`() {
        assertEquals(
            TtsProviderSelection.SystemDefault,
            TtsProviderSelection.resolve(
                storedValue = "invalid",
                legacyEnginePackage = "com.example.legacy",
                externalTtsConfigured = true
            )
        )
    }
}

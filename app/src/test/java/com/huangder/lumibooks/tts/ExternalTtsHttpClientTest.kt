package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTtsHttpClientTest {
    @Test
    fun buildMimoRequestPayload_sendsStyleAsUserAndTextAsAssistant() {
        val settings = ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT).copy(
            styleInstructions = "STYLE_SENTINEL"
        )

        val payload = buildMimoRequestPayload(settings, "TEXT_SENTINEL")

        assertEquals(
            listOf(
                MimoRequestMessage("user", "STYLE_SENTINEL"),
                MimoRequestMessage("assistant", "TEXT_SENTINEL")
            ),
            payload.messages
        )
        assertEquals("mimo-v2.5-tts", payload.model)
        assertEquals("mimo_default", payload.voice)
    }

    @Test
    fun buildMimoRequestPayload_omitsBlankStyleMessage() {
        val settings = ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT)

        val payload = buildMimoRequestPayload(settings, "TEXT_SENTINEL")

        assertEquals(listOf(MimoRequestMessage("assistant", "TEXT_SENTINEL")), payload.messages)
        assertTrue(payload.messages.single().content.isNotBlank())
    }

    @Test
    fun parseMimoDelta_ignoresEmptyDataEvent() {
        assertEquals(null, parseMimoDelta(""))
        assertEquals(null, parseMimoDelta("   "))
    }

    @Test
    fun mapMimoStreamError_mapsAuthenticationAndRateLimits() {
        assertTrue(mapMimoStreamError("unauthorized", "bad key") is ExternalTtsException.Unauthorized)
        assertTrue(
            mapMimoStreamError("rate_limit_exceeded", "slow down") is ExternalTtsException.RateLimited
        )
    }

    @Test
    fun mapMimoStreamError_preservesServiceMessageWithoutCredentials() {
        val error = mapMimoStreamError("service_unavailable", "temporarily unavailable")

        assertTrue(error is ExternalTtsException.Service)
        assertEquals("temporarily unavailable", error.message)
    }
}

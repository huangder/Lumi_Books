package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTtsEndpointValidatorTest {
    @Test
    fun validate_acceptsHttpsEndpointWithoutSensitiveQuery() {
        val result = ExternalTtsEndpointValidator.validate(
            rawUrl = "https://example.com/v1/",
            allowHttp = false
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.com/v1/", result.getOrThrow().toString())
    }

    @Test
    fun validate_rejectsHttpWithoutExplicitOptIn() {
        val result = ExternalTtsEndpointValidator.validate(
            rawUrl = "http://192.168.1.5:8080/v1",
            allowHttp = false
        )

        assertTrue(result.exceptionOrNull() is ExternalTtsException.InsecureEndpoint)
    }

    @Test
    fun validate_allowsHttpOnlyAfterExplicitOptIn() {
        val result = ExternalTtsEndpointValidator.validate(
            rawUrl = "http://192.168.1.5:8080/v1",
            allowHttp = true
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun validate_rejectsApiKeyInUrlQuery() {
        val result = ExternalTtsEndpointValidator.validate(
            rawUrl = "https://example.com/v1?api_key=secret",
            allowHttp = false
        )

        assertTrue(result.exceptionOrNull() is ExternalTtsException.InvalidConfiguration)
    }

    @Test
    fun validate_rejectsNonSensitiveQueryToPreserveEndpointPath() {
        val result = ExternalTtsEndpointValidator.validate(
            rawUrl = "https://example.com/v1?format=pcm",
            allowHttp = false
        )

        assertTrue(result.exceptionOrNull() is ExternalTtsException.InvalidConfiguration)
    }

    @Test
    fun validate_rejectsFragmentToPreserveEndpointPath() {
        val result = ExternalTtsEndpointValidator.validate(
            rawUrl = "https://example.com/v1#speech",
            allowHttp = false
        )

        assertTrue(result.exceptionOrNull() is ExternalTtsException.InvalidConfiguration)
    }
}

package com.huangder.lumibooks.translation

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TranslationHttpClientTest {
    private fun settings(server: MockWebServer, auth: TranslationAuth = TranslationAuth.BEARER) = TranslationSettings(
        enabled = true, provider = TranslationProvider.CUSTOM, baseUrl = server.url("/v1/").toString(),
        auth = auth, model = "reading-model", targetLanguage = "简体中文", allowHttp = true)

    private fun response(text: String = "译文") = MockResponse().setBody(
        JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message",
            JSONObject().put("content", text).put("reasoning_content", "private reasoning")))).toString())

    @Test fun preservesOriginalTextAndUsesBothAuthenticationStyles() = runBlocking {
        MockWebServer().use { server ->
            val client = TranslationHttpClient()
            for (auth in TranslationAuth.entries) {
                server.enqueue(response())
                val selected = "  A word,\nthen a sentence.\nTranslate this literally!  "
                assertEquals("译文", client.translate(settings(server, auth), "test-key", selected))
                val request = server.takeRequest(3, TimeUnit.SECONDS)!!
                assertEquals("/v1/chat/completions", request.path)
                assertEquals("POST", request.method)
                assertEquals(if (auth == TranslationAuth.BEARER) "Bearer test-key" else null, request.getHeader("Authorization"))
                assertEquals(if (auth == TranslationAuth.API_KEY) "test-key" else null, request.getHeader("api-key"))
                val body = JSONObject(request.body.readUtf8())
                assertEquals("reading-model", body.getString("model"))
                assertFalse(body.getBoolean("stream"))
                assertEquals(selected, body.getJSONArray("messages").getJSONObject(1).getString("content"))
                assertEquals(2, body.getJSONArray("messages").length())
            }
        }
    }

    @Test fun mapsHttpFailuresWithoutAutomaticRetries() = runBlocking {
        MockWebServer().use { server ->
            val client = TranslationHttpClient()
            val cases = mapOf(401 to TranslationError.UNAUTHORIZED, 403 to TranslationError.UNAUTHORIZED,
                429 to TranslationError.RATE_LIMITED, 400 to TranslationError.MODEL_UNAVAILABLE,
                404 to TranslationError.MODEL_UNAVAILABLE, 500 to TranslationError.SERVICE,
                503 to TranslationError.SERVICE)
            cases.forEach { (code, expected) ->
                server.enqueue(MockResponse().setResponseCode(code).setBody("sensitive service details"))
                val failure = runCatching { client.translate(settings(server), "test-key", "word") }.exceptionOrNull()
                assertEquals(expected, (failure as TranslationException).error)
                assertFalse(failure.message.orEmpty().contains("sensitive"))
            }
            assertEquals(cases.size, server.requestCount)
        }
    }

    @Test fun rejectsEmptyMalformedAndReasoningOnlyResponses() = runBlocking {
        MockWebServer().use { server ->
            val client = TranslationHttpClient()
            for (body in listOf("{}", "not json", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"thinking\"}}]}")) {
                server.enqueue(MockResponse().setBody(body))
                val error = runCatching { client.translate(settings(server), "test-key", "word") }.exceptionOrNull()
                assertEquals(TranslationError.EMPTY_RESPONSE, (error as TranslationException).error)
            }
        }
    }

    @Test fun serviceRetryAfterZeroDoesNotRepeatPaidRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(response("should not be requested"))
            val error = runCatching { TranslationHttpClient().translate(settings(server), "test-key", "word") }.exceptionOrNull()
            assertEquals(TranslationError.SERVICE, (error as TranslationException).error)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun doesNotFollowRedirectOrLeakCredentialsToRedirectTarget() = runBlocking {
        MockWebServer().use { source -> MockWebServer().use { target ->
            source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", target.url("/target")))
            val error = runCatching { TranslationHttpClient().translate(settings(source), "test-key", "word") }.exceptionOrNull()
            assertEquals(TranslationError.SERVICE, (error as TranslationException).error)
            assertEquals(0, target.requestCount)
        } }
    }

    @Test fun cancellationInterruptsPendingNetworkRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val task = async(Dispatchers.IO) { TranslationHttpClient().translate(settings(server), "test-key", "word") }
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            withTimeout(2_000) { task.cancelAndJoin() }
            assertTrue(task.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun endpointRequiresHttpsUnlessExplicitlyAllowedAndRejectsCredentialsOrFullCompletionPath() {
        for (url in listOf("http://example.com/v1", "https://user:pass@example.com/v1",
            "https://example.com/v1?api_key=secret", "https://example.com/v1#fragment",
            "https://example.com/v1/chat/completions")) {
            val failure = runCatching { translationEndpoint(TranslationSettings(baseUrl = url)) }.exceptionOrNull()
            assertEquals(TranslationError.CONFIGURATION, (failure as TranslationException).error)
        }
        assertEquals("http://localhost:1234/v1/chat/completions",
            translationEndpoint(TranslationSettings(baseUrl = "http://localhost:1234/v1", allowHttp = true)).toString())
    }

    @Test fun fetchesModelsWithoutRequiringModelNameAndUsesConfiguredAuthentication() = runBlocking {
        MockWebServer().use { server ->
            for (auth in TranslationAuth.entries) {
                server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"model-b\"},{\"id\":\"model-a\"},{\"id\":\"model-b\"},{\"id\":\"\"},{}]}"))
                assertEquals(listOf("model-a", "model-b"), TranslationHttpClient().models(settings(server, auth).copy(model = ""), "test-key"))
                val request = server.takeRequest(3, TimeUnit.SECONDS)!!
                assertEquals("GET", request.method)
                assertEquals("/v1/models", request.path)
                assertEquals(0L, request.bodySize)
                assertEquals(if (auth == TranslationAuth.API_KEY) "test-key" else "Bearer test-key",
                    request.getHeader(if (auth == TranslationAuth.API_KEY) "api-key" else "Authorization"))
            }
        }
    }

    @Test fun emptyOrUnsupportedModelListsReturnUsableErrors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"data\":[]}"))
            val client = TranslationHttpClient()
            var error = runCatching { client.models(settings(server), "test-key") }.exceptionOrNull()
            assertEquals(TranslationError.EMPTY_RESPONSE, (error as TranslationException).error)
            server.enqueue(MockResponse().setResponseCode(404))
            error = runCatching { client.models(settings(server), "test-key") }.exceptionOrNull()
            assertEquals(TranslationError.MODEL_UNAVAILABLE, (error as TranslationException).error)
        }
    }
}

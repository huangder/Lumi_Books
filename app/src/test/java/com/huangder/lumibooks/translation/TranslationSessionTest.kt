package com.huangder.lumibooks.translation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationSessionTest {
    private class FakeSource : TranslationSource {
        override val configuration = MutableStateFlow(TranslationConfiguration(
            TranslationSettings(enabled = true, model = "test"), hasToken = true))
        val calls = mutableListOf<String>()
        val replies = mutableListOf<CompletableDeferred<String>>()
        var ignoreCancellation = false
        override suspend fun translate(text: String, configuration: TranslationConfiguration): String {
            calls += text
            val reply = CompletableDeferred<String>().also(replies::add)
            return if (ignoreCancellation) withContext(NonCancellable) { reply.await() } else reply.await()
        }
    }

    @Test fun recompositionAndRepeatedDictionaryLookupDoNotRepeatTranslation() = runTest {
        val source = FakeSource()
        val session = TranslationSession(backgroundScope, source)
        session.search("bank")
        runCurrent()
        repeat(3) { session.search("bank") }
        source.configuration.value = source.configuration.value.copy()
        runCurrent()
        assertEquals(listOf("bank"), source.calls)
        source.replies.single().complete("银行；金融机构。")
        runCurrent()
        assertEquals(TranslationState.Success("银行；金融机构。"), session.state.value)
        session.search("bank")
        runCurrent()
        assertEquals(1, source.calls.size)
    }

    @Test fun lateOldResponseCannotOverwriteNewSelection() = runTest {
        val source = FakeSource().apply { ignoreCancellation = true }
        val session = TranslationSession(backgroundScope, source)
        session.search("old"); runCurrent()
        session.search("new"); runCurrent()
        source.replies[1].complete("新译文"); runCurrent()
        source.replies[0].complete("旧译文"); runCurrent()
        assertEquals(TranslationState.Success("新译文"), session.state.value)
    }

    @Test fun closeCancelsAndReopeningStartsNewSession() = runTest {
        val source = FakeSource().apply { ignoreCancellation = true }
        val session = TranslationSession(backgroundScope, source)
        session.search("word"); runCurrent()
        session.stop()
        source.replies[0].complete("late reply"); runCurrent()
        assertEquals(TranslationState.Hidden, session.state.value)
        session.search("word"); runCurrent()
        assertEquals(2, source.calls.size)
        source.replies[1].complete("new reply"); runCurrent()
        assertEquals(TranslationState.Success("new reply"), session.state.value)
    }

    @Test fun disablingCancelsAndMissingKeyNeverSendsText() = runTest {
        val source = FakeSource()
        val session = TranslationSession(backgroundScope, source)
        source.configuration.value = source.configuration.value.copy(hasToken = false)
        session.search("word"); runCurrent()
        assertTrue(source.calls.isEmpty())
        source.configuration.value = source.configuration.value.copy(hasToken = true)
        runCurrent()
        assertEquals(TranslationState.Loading, session.state.value)
        source.configuration.value = source.configuration.value.let { it.copy(settings = it.settings.copy(enabled = false)) }
        runCurrent()
        source.replies[0].complete("late reply"); runCurrent()
        assertEquals(TranslationState.Manual, session.state.value)
    }

    @Test fun manualModeKeepsButtonAndOnlyRequestsOnClickIncludingRetry() = runTest {
        val source = FakeSource()
        source.configuration.value = source.configuration.value.let { it.copy(settings = it.settings.copy(enabled = false)) }
        val session = TranslationSession(backgroundScope, source)
        session.search("word"); runCurrent()
        assertEquals(TranslationState.Manual, session.state.value)
        assertTrue(source.calls.isEmpty())
        session.retry(); runCurrent()
        assertEquals(1, source.calls.size)
        source.replies[0].completeExceptionally(TranslationException(TranslationError.NETWORK)); runCurrent()
        session.retry(); runCurrent()
        assertEquals(2, source.calls.size)
        source.replies[1].complete("manual translation"); runCurrent()
        assertEquals(TranslationState.Success("manual translation"), session.state.value)
        session.search("word"); runCurrent()
        assertEquals(2, source.calls.size)
        session.search("another word"); runCurrent()
        assertEquals(TranslationState.Manual, session.state.value)
        assertEquals(2, source.calls.size)
    }

    @Test fun failureWaitsForManualRetryAndConfigurationChangeRetranslates() = runTest {
        val source = FakeSource()
        val session = TranslationSession(backgroundScope, source)
        session.search("word"); runCurrent()
        source.replies[0].completeExceptionally(TranslationException(TranslationError.RATE_LIMITED)); runCurrent()
        assertEquals(TranslationState.Failure(TranslationError.RATE_LIMITED), session.state.value)
        assertEquals(1, source.calls.size)
        session.retry(); runCurrent()
        source.replies[1].complete("translated"); runCurrent()
        source.configuration.value = source.configuration.value.let { it.copy(settings = it.settings.copy(targetLanguage = "English")) }
        runCurrent()
        assertEquals(3, source.calls.size)
        source.replies[2].complete("English translation"); runCurrent()
        assertEquals(TranslationState.Success("English translation"), session.state.value)
    }
}

package com.huangder.lumibooks.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsStartRequestTest {
    private val position = TtsListeningPosition("book", 9, 2, 450, 123L)

    @Test
    fun openingPromptDoesNotStartAndUnrememberedChoiceIsAskedAgain() = runTest {
        val settings = Settings(position)
        val request = TtsStartRequest(backgroundScope, settings, "book")
        val starts = mutableListOf<Boolean>()
        request.request(12, starts::add)
        runCurrent()
        assertEquals(position, request.prompt.value)
        assertTrue(starts.isEmpty())

        request.choose(TtsStartChoice.CURRENT_PAGE, remember = false)
        request.choose(TtsStartChoice.SAVED_POSITION, remember = true)
        runCurrent()
        assertEquals(listOf(false), starts)
        assertNull(settings.ttsStartChoice.value)
        request.request(12, starts::add)
        runCurrent()
        assertEquals(position, request.prompt.value)
        request.dismiss()
        request.choose(TtsStartChoice.SAVED_POSITION, remember = true)
        runCurrent()
        assertEquals(listOf(false), starts)
        assertNull(settings.ttsStartChoice.value)
    }

    @Test
    fun eitherRememberedChoiceAppliesToTheNextRequestAndCanBeReset() = runTest {
        for (choice in TtsStartChoice.entries) {
            val settings = Settings(position)
            val starts = mutableListOf<Boolean>()
            val request = TtsStartRequest(backgroundScope, settings, "book")
            request.request(12, starts::add)
            runCurrent()
            request.choose(choice, remember = true)
            runCurrent()
            assertEquals(choice, settings.ttsStartChoice.value)
            val next = TtsStartRequest(backgroundScope, settings, "book")
            next.request(12, starts::add)
            runCurrent()
            assertNull(next.prompt.value)
            assertEquals(List(2) { choice == TtsStartChoice.SAVED_POSITION }, starts)
            settings.saveTtsStartChoice(null)
            next.request(12, starts::add)
            runCurrent()
            assertEquals(position, next.prompt.value)
            assertEquals(2, starts.size)
        }
    }

    @Test
    fun noUsableHistoryStartsAtCurrentPageEvenWhenResumeWasRemembered() = runTest {
        for (saved in listOf(null, position.copy(bookId = "other"), position.copy(chapterIndex = 99))) {
            val settings = Settings(saved).apply { ttsStartChoice.value = TtsStartChoice.SAVED_POSITION }
            val request = TtsStartRequest(backgroundScope, settings, "book")
            val starts = mutableListOf<Boolean>()
            request.request(12, starts::add)
            runCurrent()
            assertNull(request.prompt.value)
            assertEquals(listOf(false), starts)
        }
    }

    @Test
    fun legacyAiPositionAlsoRequiresAChoiceAndPendingRequestsCanBeCancelled() = runTest {
        val settings = Settings(null).apply {
            legacyPosition = ExternalTtsResumePosition("book", 4, 1, 200)
        }
        val request = TtsStartRequest(backgroundScope, settings, "book")
        val starts = mutableListOf<Boolean>()
        request.request(12, starts::add)
        request.dismiss()
        runCurrent()
        assertTrue(starts.isEmpty())
        assertNull(request.prompt.value)
        request.request(12, starts::add)
        runCurrent()
        assertEquals(4, request.prompt.value?.chapterIndex)
        assertTrue(starts.isEmpty())
        request.choose(TtsStartChoice.SAVED_POSITION, remember = false)
        runCurrent()
        assertEquals(listOf(true), starts)
    }

    private class Settings(position: TtsListeningPosition?) : TtsSettingsStore {
        override val ttsProsodySettings = flowOf(TtsProsodySettings())
        override val ttsProviderSelection = flowOf(TtsProviderSelection.SystemDefault)
        override val externalTtsSettings = flowOf(ExternalTtsSettings())
        override val ttsStartChoice = MutableStateFlow<TtsStartChoice?>(null)
        private val listening = MutableStateFlow(position)
        var legacyPosition: ExternalTtsResumePosition? = null
        override fun ttsListeningPosition(bookId: String) = listening
        override fun externalTtsResumePosition(bookId: String) = flowOf(legacyPosition)
        override suspend fun saveTtsStartChoice(choice: TtsStartChoice?) { ttsStartChoice.value = choice }
        override suspend fun saveTtsListeningPosition(position: TtsListeningPosition) { listening.value = position }
        override suspend fun saveTtsProsodySettings(settings: TtsProsodySettings) = Unit
        override suspend fun saveTtsProviderSelection(selection: TtsProviderSelection) = Unit
        override suspend fun saveExternalTtsResumePosition(position: ExternalTtsResumePosition) = Unit
        override suspend fun clearExternalTtsResumePosition(bookId: String) = Unit
    }
}

package com.huangder.lumibooks.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsControllerTest {
    @Test
    fun duplicateAndOldDoneCallbacksAdvanceOnlyOnce() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            val source = FakePageSource(
                page(0, "One. Two.", next = TtsPageLocation(0, 1)),
                page(1, "Three.", previous = TtsPageLocation(0, 0))
            )
            assertTrue(controller.start("book", source, 0, 0).isSuccess)
            val firstId = engine.lastUtteranceId

            engine.complete(firstId)
            runCurrent()
            val secondId = engine.lastUtteranceId
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)

            engine.complete(firstId)
            runCurrent()
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)

            engine.complete(secondId)
            runCurrent()
            assertEquals(listOf("One.", "Two.", "Three."), engine.spokenTexts)
        } finally {
            controller.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun staleSessionCallbackCannotAdvanceReplacementSession() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            val firstSource = FakePageSource(page(0, "Old."))
            controller.start("old", firstSource, 0, 0)
            val oldId = engine.lastUtteranceId

            controller.start("new", FakePageSource(page(0, "New. Next.")), 0, 0)
            val newId = engine.lastUtteranceId
            assertTrue(firstSource.closed)
            assertNotEquals(oldId, newId)

            engine.complete(oldId)
            runCurrent()
            assertEquals(listOf("Old.", "New."), engine.spokenTexts)
        } finally {
            controller.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pauseResumeRejectsCompletionFromStoppedUtterance() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "One. Two.")), 0, 0)
            val interruptedId = engine.lastUtteranceId
            controller.pause()
            runCurrent()
            controller.resume()
            runCurrent()
            val resumedId = engine.lastUtteranceId
            assertNotEquals(interruptedId, resumedId)

            engine.complete(interruptedId)
            runCurrent()
            assertEquals(listOf("One.", "One."), engine.spokenTexts)

            engine.complete(resumedId)
            runCurrent()
            assertEquals(listOf("One.", "One.", "Two."), engine.spokenTexts)
        } finally {
            controller.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun oldPageAnimationCallbackCannotSeekPlaybackBackward() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            val source = FakePageSource(
                page(0, "One.", next = TtsPageLocation(0, 1)),
                page(1, "Two.", previous = TtsPageLocation(0, 0))
            )
            controller.start("book", source, 0, 0)
            controller.onPageVisible("book", 0, 0)
            runCurrent()

            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(TtsPageLocation(0, 1), controller.currentPage.value?.location)
            assertEquals(1, source.requests.count { it == TtsPageLocation(0, 1) })

            controller.onPageVisible("book", 0, 0)
            runCurrent()
            controller.onPageVisible("book", 0, 1)
            runCurrent()
            controller.onPageVisible("book", 0, 0)
            runCurrent()

            assertEquals(TtsPageLocation(0, 1), controller.currentPage.value?.location)
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)
            assertEquals(1, source.requests.count { it == TtsPageLocation(0, 1) })
        } finally {
            controller.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pageTurnRequestsCarryIncreasingIdsWithinSession() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start(
                "book",
                FakePageSource(
                    page(0, "One.", next = TtsPageLocation(0, 1)),
                    page(1, "Two.", previous = TtsPageLocation(0, 0))
                ),
                0,
                0
            )
            val first = controller.pageTurnRequests.replayCache.single()
            controller.onPageVisible("book", 0, 0)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            val second = controller.pageTurnRequests.replayCache.single()

            assertEquals(first.sessionId, second.sessionId)
            assertTrue(second.requestId > first.requestId)
            assertEquals(TtsPageLocation(0, 1), second.location)
        } finally {
            controller.shutdown()
            Dispatchers.resetMain()
        }
    }

    private fun controller(engine: FakePlaybackEngine) = TtsController(
        systemTtsEngine = engine,
        externalTtsEngine = FakePlaybackEngine(isExternal = true),
        textExtractor = TtsTextExtractor(),
        dataStoreManager = FakeSettingsStore()
    )

    private fun page(
        index: Int,
        text: String,
        previous: TtsPageLocation? = null,
        next: TtsPageLocation? = null
    ) = TtsPageContent(
        location = TtsPageLocation(0, index),
        text = text,
        previous = previous,
        next = next
    )

    private class FakePageSource(vararg pages: TtsPageContent) : TtsPageSource {
        private val pages = pages.associateBy { it.location }
        val requests = mutableListOf<TtsPageLocation>()
        var closed = false

        override suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent? {
            val location = TtsPageLocation(chapterIndex, pageIndex)
            requests += location
            return pages[location]
        }

        override fun close() {
            closed = true
        }
    }

    private class FakePlaybackEngine(
        override val isExternal: Boolean = false
    ) : TtsPlaybackEngine {
        private lateinit var listener: TtsPlaybackListener
        val spokenTexts = mutableListOf<String>()
        var lastUtteranceId = ""

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)

        override suspend fun speak(text: String, utteranceId: String): Result<Unit> {
            spokenTexts += text
            lastUtteranceId = utteranceId
            listener.onStart(utteranceId)
            return Result.success(Unit)
        }

        fun complete(utteranceId: String) = listener.onDone(utteranceId)

        override suspend fun pause() = Unit
        override suspend fun resume(): Boolean = false
        override suspend fun stop() = Unit
        override suspend fun setSpeechRate(rate: Float) = Unit
        override suspend fun setPitch(pitch: Float) = Unit
        override fun setListener(listener: TtsPlaybackListener) {
            this.listener = listener
        }
        override fun shutdown() = Unit
    }

    private class FakeSettingsStore : TtsSettingsStore {
        override val ttsSpeechRate = MutableStateFlow(1f)
        override val ttsPitch = MutableStateFlow(1f)
        override val externalTtsSettings = MutableStateFlow(ExternalTtsSettings())
        private val resumePositions = mutableMapOf<String, MutableStateFlow<ExternalTtsResumePosition?>>()

        override fun externalTtsResumePosition(bookId: String): Flow<ExternalTtsResumePosition?> =
            resumePositions.getOrPut(bookId) { MutableStateFlow(null) }

        override suspend fun saveTtsSpeechRate(rate: Float) {
            ttsSpeechRate.value = rate
        }

        override suspend fun saveTtsPitch(pitch: Float) {
            ttsPitch.value = pitch
        }

        override suspend fun saveExternalTtsResumePosition(position: ExternalTtsResumePosition) {
            resumePositions.getOrPut(position.bookId) { MutableStateFlow(null) }.value = position
        }

        override suspend fun clearExternalTtsResumePosition(bookId: String) {
            resumePositions.getOrPut(bookId) { MutableStateFlow(null) }.value = null
        }
    }
}

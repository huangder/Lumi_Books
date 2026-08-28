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
    fun punctuationClausesAdvanceInsideOneLogicalSentence() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一小句，第二小句，句子结束。")), 0, 0)
            assertEquals("第一小句，", controller.currentSentence.value?.text)

            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals("第二小句，", controller.currentSentence.value?.text)

            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals("句子结束。", controller.currentSentence.value?.text)
            assertEquals(listOf("第一小句，", "第二小句，", "句子结束。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun skipForwardMovesToNextLogicalSentenceFromMiddleClause() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start(
                "book",
                FakePageSource(page(0, "第一小句，第二小句，句子结束。下一句。")),
                0,
                0
            )
            engine.complete(engine.lastUtteranceId)
            runCurrent()

            controller.skip(forward = true)
            runCurrent()

            assertEquals("下一句。", controller.currentSentence.value?.text)
            assertEquals(listOf("第一小句，", "第二小句，", "下一句。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun externalResumeRestoresTheSavedClauseWithinItsLogicalSentence() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val page = page(0, "第一小句，第二小句，句子结束。")
        val settingsStore = FakeSettingsStore(TtsProviderSelection.AiModel).apply {
            seedResume(
                ExternalTtsResumePosition(
                    bookId = "book",
                    chapterIndex = 0,
                    pageIndex = 0,
                    characterOffset = 0,
                    clauseIndex = 1,
                    cacheKey = "saved-cache",
                    pageFingerprint = page.resumeFingerprint,
                    pcmFrameOffset = 42L
                )
            )
        }
        val externalEngine = FakePlaybackEngine(isExternal = true)
        val controller = controller(
            systemEngine = FakePlaybackEngine(),
            externalEngine = externalEngine,
            settingsStore = settingsStore
        )
        try {
            assertTrue(controller.start("book", FakePageSource(page), 0, 0).isSuccess)

            assertEquals(listOf("第二小句，"), externalEngine.spokenTexts)
            assertEquals(listOf("saved-cache"), externalEngine.suppliedCacheKeys)
            assertEquals(listOf(42L), externalEngine.suppliedStartFrames)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

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
            runCurrent()
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
            runCurrent()
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
            runCurrent()
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
            runCurrent()
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
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aiProviderRoutesPlaybackToExternalEngine() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val systemEngine = FakePlaybackEngine()
        val externalEngine = FakePlaybackEngine(isExternal = true)
        val controller = controller(
            systemEngine = systemEngine,
            externalEngine = externalEngine,
            settingsStore = FakeSettingsStore(TtsProviderSelection.AiModel)
        )
        try {
            assertTrue(
                controller.start("book", FakePageSource(page(0, "AI speech.")), 0, 0).isSuccess
            )

            assertEquals(emptyList<String>(), systemEngine.spokenTexts)
            assertEquals(listOf("AI speech."), externalEngine.spokenTexts)
            assertEquals(emptyList<String?>(), systemEngine.selectedEnginePackages)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun systemDefaultProviderClearsExplicitEngineAndUsesSystemPlayback() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val systemEngine = FakePlaybackEngine()
        val externalEngine = FakePlaybackEngine(isExternal = true)
        val controller = controller(
            systemEngine = systemEngine,
            externalEngine = externalEngine,
            settingsStore = FakeSettingsStore(TtsProviderSelection.SystemDefault)
        )
        try {
            assertTrue(
                controller.start("book", FakePageSource(page(0, "System speech.")), 0, 0).isSuccess
            )

            assertEquals(listOf<String?>(null), systemEngine.selectedEnginePackages)
            assertEquals(listOf("System speech."), systemEngine.spokenTexts)
            assertEquals(emptyList<String>(), externalEngine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun explicitAndroidProviderPassesPackageAndUsesSystemPlayback() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val systemEngine = FakePlaybackEngine()
        val externalEngine = FakePlaybackEngine(isExternal = true)
        val packageName = "com.example.multitts"
        val controller = controller(
            systemEngine = systemEngine,
            externalEngine = externalEngine,
            settingsStore = FakeSettingsStore(TtsProviderSelection.AndroidEngine(packageName))
        )
        try {
            assertTrue(
                controller.start("book", FakePageSource(page(0, "MultiTTS speech.")), 0, 0).isSuccess
            )

            assertEquals(listOf(packageName), systemEngine.selectedEnginePackages)
            assertEquals(listOf("MultiTTS speech."), systemEngine.spokenTexts)
            assertEquals(emptyList<String>(), externalEngine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun controller(
        systemEngine: FakePlaybackEngine,
        externalEngine: FakePlaybackEngine = FakePlaybackEngine(isExternal = true),
        settingsStore: FakeSettingsStore = FakeSettingsStore()
    ) = TtsController(
        systemTtsEngine = systemEngine,
        externalTtsEngine = externalEngine,
        textExtractor = TtsTextExtractor(),
        dataStoreManager = settingsStore
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
    ) : AndroidTtsPlaybackEngine {
        private lateinit var listener: TtsPlaybackListener
        val spokenTexts = mutableListOf<String>()
        val selectedEnginePackages = mutableListOf<String?>()
        val suppliedCacheKeys = mutableListOf<String?>()
        val suppliedStartFrames = mutableListOf<Long>()
        var lastUtteranceId = ""

        override suspend fun selectEngine(packageName: String?) {
            selectedEnginePackages += packageName
        }

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)

        override suspend fun speak(text: String, utteranceId: String): Result<Unit> {
            spokenTexts += text
            lastUtteranceId = utteranceId
            listener.onStart(utteranceId)
            return Result.success(Unit)
        }

        override suspend fun speak(
            text: String,
            utteranceId: String,
            cacheKey: String?,
            startFrame: Long
        ): Result<Unit> {
            suppliedCacheKeys += cacheKey
            suppliedStartFrames += startFrame
            return speak(text, utteranceId)
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

    private class FakeSettingsStore(
        providerSelection: TtsProviderSelection = TtsProviderSelection.SystemDefault
    ) : TtsSettingsStore {
        override val ttsSpeechRate = MutableStateFlow(1f)
        override val ttsPitch = MutableStateFlow(1f)
        override val ttsProviderSelection = MutableStateFlow(providerSelection)
        override val externalTtsSettings = MutableStateFlow(ExternalTtsSettings())
        private val resumePositions = mutableMapOf<String, MutableStateFlow<ExternalTtsResumePosition?>>()

        override fun externalTtsResumePosition(bookId: String): Flow<ExternalTtsResumePosition?> =
            resumePositions.getOrPut(bookId) { MutableStateFlow(null) }

        fun seedResume(position: ExternalTtsResumePosition) {
            resumePositions.getOrPut(position.bookId) { MutableStateFlow(null) }.value = position
        }

        override suspend fun saveTtsSpeechRate(rate: Float) {
            ttsSpeechRate.value = rate
        }

        override suspend fun saveTtsPitch(pitch: Float) {
            ttsPitch.value = pitch
        }

        override suspend fun saveTtsProviderSelection(selection: TtsProviderSelection) {
            ttsProviderSelection.value = selection
        }

        override suspend fun saveExternalTtsResumePosition(position: ExternalTtsResumePosition) {
            resumePositions.getOrPut(position.bookId) { MutableStateFlow(null) }.value = position
        }

        override suspend fun clearExternalTtsResumePosition(bookId: String) {
            resumePositions.getOrPut(bookId) { MutableStateFlow(null) }.value = null
        }
    }
}

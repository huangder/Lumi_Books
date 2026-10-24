package com.huangder.lumibooks.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TtsControllerTest {
    @Test
    fun choosingHistoryRestoresByCharacterAndChoosingCurrentPageOverridesItForBothEngines() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (external in listOf(false, true)) {
                val store = FakeSettingsStore(if (external) TtsProviderSelection.AiModel else TtsProviderSelection.SystemDefault)
                store.saveTtsListeningPosition(TtsListeningPosition("book", 4, 99, 108, 123L))
                val engine = FakePlaybackEngine(isExternal = external)
                val controller = if (external) controller(FakePlaybackEngine(), engine, store)
                    else controller(engine, settingsStore = store)
                fun source() = FakePageSource(
                    page(0, "Reading page."),
                    page(1, "First. Second.", chapterIndex = 4, startCharacterOffset = 100))
                try {
                    assertTrue(controller.start("book", source(), 0, 0, restoreSavedPosition = true).isSuccess)
                    runCurrent()
                    assertEquals(TtsPageLocation(4, 1), controller.currentPage.value?.location)
                    assertEquals("Second.", engine.spokenTexts.last())
                    assertTrue(controller.start("book", source(), 0, 0, restoreSavedPosition = false).isSuccess)
                    runCurrent()
                    assertEquals(TtsPageLocation(0, 0), controller.currentPage.value?.location)
                    assertEquals("Reading page.", engine.spokenTexts.last())
                } finally {
                    controller.shutdown()
                    runCurrent()
                }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun durablePositionWinsOverAnAiCacheFromAnOlderSentence() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page = page(0, "Old sentence. Latest sentence.", startCharacterOffset = 100)
        val store = FakeSettingsStore(TtsProviderSelection.AiModel)
        store.seedResume(ExternalTtsResumePosition("book", 0, 0, 100,
            cacheKey = "old-cache", pageFingerprint = page.resumeFingerprint, pcmFrameOffset = 42))
        store.saveTtsListeningPosition(TtsListeningPosition("book", 0, 0, 115, 123L))
        val engine = FakePlaybackEngine(isExternal = true)
        val controller = controller(FakePlaybackEngine(), engine, store)
        try {
            controller.start("book", FakePageSource(page), 0, 0, restoreSavedPosition = true)
            runCurrent()
            assertEquals("Latest sentence.", engine.spokenTexts.single())
            assertEquals(listOf<String?>(null), engine.suppliedCacheKeys)
            assertEquals(listOf(0L), engine.suppliedStartFrames)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun detachedReaderDoesNotReceiveBackgroundPageTurnsAndExplicitReturnRestoresFollowing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val store = FakeSettingsStore()
        val controller = controller(engine, settingsStore = store)
        val turns = mutableListOf<TtsPageTurnRequest>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.pageTurnRequests.collect { turns += it }
        }
        try {
            controller.start("book", FakePageSource(
                page(0, "One.", next = TtsPageLocation(0, 1)),
                page(1, "Two.", previous = TtsPageLocation(0, 0), next = TtsPageLocation(0, 2),
                    startCharacterOffset = 4),
                page(2, "Three.", previous = TtsPageLocation(0, 1), startCharacterOffset = 8)
            ), 0, 0)
            runCurrent()
            turns.clear()
            controller.setReaderAttached("book", false)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals("Two.", engine.spokenTexts.last())
            assertTrue(turns.isEmpty())
            assertEquals(1, store.ttsListeningPosition("book").first()?.pageIndex)

            // Restoring the reading page must neither rewind audio nor silently reattach it.
            controller.onPageVisible("book", 0, 0, TtsPageChangeOrigin.USER)
            runCurrent()
            assertEquals("Two.", engine.spokenTexts.last())
            assertEquals(1, store.ttsListeningPosition("book").first()?.pageIndex)
            controller.setReaderAttached("book", true)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(TtsPageLocation(0, 2), turns.single().location)
            controller.acknowledgePageTurnRequest(turns.single())
            runCurrent()
            assertEquals("Three.", engine.spokenTexts.last())
        } finally {
            collector.cancel()
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun userPageTurnsMovePlaybackOnlyWhenFollowPageTurnsIsEnabled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        val source = FakePageSource(
            page(0, "One.", next = TtsPageLocation(0, 1)),
            page(1, "Two.", previous = TtsPageLocation(0, 0), next = TtsPageLocation(0, 2),
                startCharacterOffset = 4),
            page(2, "Three.", previous = TtsPageLocation(0, 1), startCharacterOffset = 8)
        )
        try {
            controller.start("book", source, 0, 0)
            runCurrent()

            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.USER)
            runCurrent()
            assertEquals(TtsPageLocation(0, 0), controller.currentPage.value?.location)
            assertEquals("One.", engine.spokenTexts.last())

            controller.setFollowUserPageTurns(true)
            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.USER)
            runCurrent()
            assertEquals(TtsPageLocation(0, 1), controller.currentPage.value?.location)
            assertEquals("Two.", engine.spokenTexts.last())
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun mergedUtteranceSavesItsStartUntilPlaybackReachesTheLandingPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine(canResumeWithoutRestart = true)
        val store = FakeSettingsStore()
        val controller = controller(engine, settingsStore = store)
        try {
            controller.start("book", FakePageSource(
                page(0, "Hello", next = TtsPageLocation(0, 1), startCharacterOffset = 10),
                page(1, " world. Next.", previous = TtsPageLocation(0, 0), startCharacterOffset = 15)
            ), 0, 0)
            runCurrent()
            controller.setReaderAttached("book", false)
            runCurrent()
            engine.range(engine.lastUtteranceId, 6, 11)
            controller.pause()
            runCurrent()
            assertEquals(0, store.ttsListeningPosition("book").first()?.pageIndex)
            assertEquals(10, store.ttsListeningPosition("book").first()?.characterOffset)
            controller.resume()
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(1, store.ttsListeningPosition("book").first()?.pageIndex)
            assertEquals("Next.", engine.spokenTexts.last())
            assertEquals(22, store.ttsListeningPosition("book").first()?.characterOffset)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun listeningPositionSurvivesControllerShutdownWithoutReaderOrStopCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeSettingsStore()
        val engine = FakePlaybackEngine()
        val controller = controller(engine, settingsStore = store)
        try {
            controller.start("book", FakePageSource(
                page(0, "Chapter six.", chapterIndex = 5, next = TtsPageLocation(9, 0)),
                page(0, "Chapter ten. Another sentence.", chapterIndex = 9,
                    previous = TtsPageLocation(5, 0), startCharacterOffset = 100)
            ), 5, 0)
            runCurrent()
            controller.setReaderAttached("book", false)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()

            assertEquals("Chapter ten.", engine.spokenTexts.last())
            assertEquals(9, store.ttsListeningPosition("book").first()?.chapterIndex)
            assertEquals(100, store.ttsListeningPosition("book").first()?.characterOffset)
            // An abrupt shutdown does not call stopInternal or the reader's progress saver.
            controller.shutdown()
            runCurrent()
            assertEquals(9, store.ttsListeningPosition("book").first()?.chapterIndex)
            assertEquals(null, store.ttsListeningPosition("other-book").first())
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun androidRangeIsFlushedOnPauseAndFinalCompletionRetainsListeningPosition() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeSettingsStore()
        val engine = FakePlaybackEngine(canResumeWithoutRestart = true)
        val controller = controller(engine, settingsStore = store)
        try {
            controller.start("book", FakePageSource(page(0, "One sentence here.", startCharacterOffset = 20)), 0, 0)
            runCurrent()
            engine.range(engine.lastUtteranceId, 4, 12)
            controller.pause()
            runCurrent()
            assertEquals(24, store.ttsListeningPosition("book").first()?.characterOffset)
            controller.resume()
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(TtsPlaybackState.IDLE, controller.playbackState.value)
            assertEquals(38, store.ttsListeningPosition("book").first()?.characterOffset)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun externalPlaybackAlsoPersistsListeningLocationOutsideAudioResumeCache() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeSettingsStore(TtsProviderSelection.AiModel)
        val engine = FakePlaybackEngine(isExternal = true)
        val controller = controller(FakePlaybackEngine(), engine, store)
        try {
            controller.start("book", FakePageSource(page(0, "AI speech.", chapterIndex = 4,
                startCharacterOffset = 55)), 4, 0)
            runCurrent()
            assertEquals(4, store.ttsListeningPosition("book").first()?.chapterIndex)
            assertEquals(55, store.ttsListeningPosition("book").first()?.characterOffset)
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(null, store.externalTtsResumePosition("book").first())
            assertEquals(65, store.ttsListeningPosition("book").first()?.characterOffset)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun androidEngineSpeaksOneUtterancePerLogicalSentence() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一小句，第二小句，句子结束。")), 0, 0)
            assertEquals("第一小句，第二小句，句子结束。", controller.currentSentence.value?.text)
            assertEquals("第一小句，第二小句，句子结束。", controller.currentUtterance.value?.text)

            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(null, controller.currentSentence.value)
            assertEquals(null, controller.currentUtterance.value)
            assertEquals(listOf("第一小句，第二小句，句子结束。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun parenthesizedQuestionAdvancesToTheFollowingSentence() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start(
                "book",
                FakePageSource(page(0, "（会有怎样的浪漫？）。下一句。")),
                0,
                0
            )

            assertEquals(listOf("（会有怎样的浪漫？）。"), engine.spokenTexts)
            engine.complete(engine.lastUtteranceId)
            runCurrent()

            assertEquals(listOf("（会有怎样的浪漫？）。", "下一句。"), engine.spokenTexts)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun standaloneNumberMarkerUsesSpeakableTextAndThenContinues() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "1。下一句。")), 0, 0)

            assertEquals("1", engine.spokenTexts.single())
            assertEquals("1。", controller.currentSentence.value?.text)
            engine.complete(engine.lastUtteranceId)
            runCurrent()

            assertEquals(listOf("1", "下一句。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun decimalNumberIsSpokenAsPartOfTheWholeSentence() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "售价1.5元。下一句。")), 0, 0)

            assertEquals(listOf("售价1.5元。"), engine.spokenTexts)
            assertEquals("售价1.5元。", controller.currentSentence.value?.text)
            engine.complete(engine.lastUtteranceId)
            runCurrent()

            assertEquals(listOf("售价1.5元。", "下一句。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun multiTtsShortSentencesAndSingleCharacterAreAllSubmittedInOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(
            systemEngine = engine,
            settingsStore = FakeSettingsStore(TtsProviderSelection.AndroidEngine("com.example.multitts"))
        )
        try {
            controller.start("book", FakePageSource(page(0, "好。好的.嗯")), 0, 0)
            for ((index, original) in listOf("好。", "好的.", "嗯").withIndex()) {
                assertEquals(original, controller.currentSentence.value?.text)
                assertEquals(listOf(0, 2, 5)[index], controller.currentSentence.value?.startCharacterOffset)
                engine.complete(engine.lastUtteranceId)
                runCurrent()
            }
            assertEquals(listOf("好", "好的", "嗯"), engine.spokenTexts)
            assertEquals(TtsPlaybackState.IDLE, controller.playbackState.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun punctuationOnlyPageIsSkippedWithoutStallingPlayback() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start(
                "book",
                FakePageSource(
                    page(0, "？！", next = TtsPageLocation(0, 1)),
                    page(1, "下一句。", previous = TtsPageLocation(0, 0))
                ),
                0,
                0
            )

            assertEquals(listOf("下一句。"), engine.spokenTexts)
            assertEquals(TtsPageLocation(0, 1), controller.currentPage.value?.location)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun resumableAndroidEngineKeepsItsUtteranceAcrossPause() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine(canResumeWithoutRestart = true)
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一句。第二句。")), 0, 0)
            val utteranceId = engine.lastUtteranceId

            controller.pause()
            runCurrent()
            assertEquals(TtsPlaybackState.PAUSED, controller.playbackState.value)
            assertEquals(utteranceId, engine.lastUtteranceId)

            controller.resume()
            runCurrent()
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
            assertEquals(listOf("第一句。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun systemPlaybackErrorReinitializesAndReplaysTheCurrentSentence() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一句。第二句。")), 0, 0)
            val failedId = engine.lastUtteranceId

            engine.error(failedId)
            runCurrent()

            assertEquals(listOf("第一句。", "第一句。"), engine.spokenTexts)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
            assertEquals(2, engine.initializeCalls)

            // A late callback from the failed utterance must not stop the retry.
            engine.error(failedId)
            runCurrent()
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)

            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals("第二句。", controller.currentSentence.value?.text)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedSystemPlaybackErrorPausesWithoutDiscardingTheSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一句。")), 0, 0)
            val firstId = engine.lastUtteranceId
            engine.error(firstId)
            runCurrent()
            val retryId = engine.lastUtteranceId

            engine.error(retryId)
            runCurrent()

            assertEquals(TtsPlaybackState.PAUSED, controller.playbackState.value)
            assertEquals("第一句。", controller.currentSentence.value?.text)
            assertEquals("book", controller.activeBookId.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun engineInitializationFailureAfterPlaybackErrorLeavesTheSentencePaused() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一句。")), 0, 0)
            engine.failNextInitialize = true
            engine.error(engine.lastUtteranceId)
            runCurrent()

            assertEquals(TtsPlaybackState.PAUSED, controller.playbackState.value)
            assertEquals("第一句。", controller.currentSentence.value?.text)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedSentenceTextStillPublishesANewUtterance() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "重复。重复。")), 0, 0)
            val firstUtterance = controller.currentUtterance.value

            engine.complete(engine.lastUtteranceId)
            runCurrent()

            assertEquals("重复。", controller.currentUtterance.value?.text)
            assertNotEquals(firstUtterance, controller.currentUtterance.value)
            assertEquals(3, controller.currentUtterance.value?.startCharacterOffset)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun skipForwardMovesToNextLogicalSentence() = runTest {
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
            controller.skip(forward = true)
            runCurrent()

            assertEquals("下一句。", controller.currentSentence.value?.text)
            assertEquals(listOf("第一小句，第二小句，句子结束。", "下一句。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun androidRangeCallbackRefinesHighlightWithoutCreatingMoreUtterances() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一小句，第二小句，句子结束。")), 0, 0)
            val utteranceId = engine.lastUtteranceId

            engine.range(utteranceId, 5, 10)
            runCurrent()

            assertEquals("第二小句，", controller.currentSentence.value?.text)
            assertEquals("第一小句，第二小句，句子结束。", controller.currentUtterance.value?.text)
            assertEquals(listOf("第一小句，第二小句，句子结束。"), engine.spokenTexts)
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

            assertEquals(listOf("第一小句，第二小句，句子结束。"), externalEngine.spokenTexts)
            assertEquals(listOf("saved-cache"), externalEngine.suppliedCacheKeys)
            assertEquals(listOf(42L), externalEngine.suppliedStartFrames)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun replacingSessionFromCurrentPageIgnoresSavedPositionAndNeverPublishesIdle() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val oldPage = page(
            index = 0,
            text = "Old position.",
            next = TtsPageLocation(0, 1)
        )
        val currentPage = page(
            index = 1,
            text = "Current page.",
            previous = TtsPageLocation(0, 0)
        )
        val settingsStore = FakeSettingsStore(TtsProviderSelection.AiModel).apply {
            seedResume(
                ExternalTtsResumePosition(
                    bookId = "book",
                    chapterIndex = 0,
                    pageIndex = 0,
                    characterOffset = 0,
                    clauseIndex = 0,
                    cacheKey = "saved-cache",
                    pageFingerprint = oldPage.resumeFingerprint,
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
        val observedStates = mutableListOf<TtsPlaybackState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.playbackState.collect(observedStates::add)
        }
        try {
            controller.start("book", FakePageSource(oldPage, currentPage), 0, 0)
            observedStates.clear()

            assertTrue(
                controller.start(
                    bookId = "book",
                    source = FakePageSource(oldPage, currentPage),
                    startChapter = 0,
                    startPage = 1,
                    restoreSavedPosition = false
                ).isSuccess
            )
            runCurrent()

            assertEquals(TtsPageLocation(0, 1), controller.currentPage.value?.location)
            assertEquals("Current page.", externalEngine.spokenTexts.last())
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
            assertTrue(TtsPlaybackState.IDLE !in observedStates)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun externalEngineSynthesizesOneLogicalSentenceAndAdvancesAfterCompletion() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val externalEngine = FakePlaybackEngine(isExternal = true)
        val controller = controller(
            systemEngine = FakePlaybackEngine(),
            externalEngine = externalEngine,
            settingsStore = FakeSettingsStore(TtsProviderSelection.AiModel)
        )
        try {
            controller.start("book", FakePageSource(page(0, "第一小句，第二小句，句子结束。下一句。")), 0, 0)
            assertEquals(listOf("第一小句，第二小句，句子结束。"), externalEngine.spokenTexts)
            assertEquals("第一小句，第二小句，句子结束。", controller.currentUtterance.value?.text)
            assertEquals("第一小句，", controller.currentSentence.value?.text)

            externalEngine.complete(externalEngine.lastUtteranceId)
            runCurrent()

            assertEquals("下一句。", controller.currentSentence.value?.text)
            assertEquals(
                listOf("第一小句，第二小句，句子结束。", "下一句。"),
                externalEngine.spokenTexts
            )
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
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)

            controller.onPageVisible(
                "book",
                0,
                1,
                TtsPageChangeOrigin.TTS_FOLLOW
            )
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

            controller.onPageVisible("book", 0, 0, TtsPageChangeOrigin.TTS_FOLLOW)
            runCurrent()
            assertEquals(listOf("One."), engine.spokenTexts)
            controller.onPageVisible("book", 0, 99, TtsPageChangeOrigin.LAYOUT)
            runCurrent()
            assertEquals(listOf("One."), engine.spokenTexts)
            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.TTS_FOLLOW)
            runCurrent()
            controller.onPageVisible("book", 0, 0, TtsPageChangeOrigin.TTS_FOLLOW)
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
    fun rapidPlaybackCrossesThirtyPagesWithoutRepeatingOrFollowingLayoutCallbacks() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val pages = (0 until 31).map { index ->
            page(
                index = index,
                text = "Sentence $index.",
                previous = (index - 1).takeIf { it >= 0 }?.let { TtsPageLocation(0, it) },
                next = (index + 1).takeIf { it < 31 }?.let { TtsPageLocation(0, it) }
            )
        }
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(*pages.toTypedArray()), 0, 0)
            repeat(30) { completedPage ->
                val completedUtterance = engine.lastUtteranceId
                engine.complete(completedUtterance)
                engine.complete(completedUtterance)
                runCurrent()

                controller.onPageVisible(
                    "book",
                    0,
                    completedPage,
                    TtsPageChangeOrigin.LAYOUT
                )
                controller.onPageVisible(
                    "book",
                    0,
                    completedPage + 1,
                    TtsPageChangeOrigin.TTS_FOLLOW
                )
                runCurrent()
            }

            assertEquals((0 until 31).map { "Sentence $it." }, engine.spokenTexts)
            assertEquals(TtsPageLocation(0, 30), controller.currentPage.value?.location)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
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

    @Test
    fun androidProsodyCanFollowEngineOrUseStoredOverrides() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val settings = FakeSettingsStore()
        val engine = FakePlaybackEngine()
        val controller = controller(engine, settingsStore = settings)
        try {
            runCurrent()
            assertEquals(null to null, engine.prosodyApplications.last())

            controller.setSpeechRate(4f)
            controller.setPitch(1.5f)
            assertEquals(TtsProsodyMode.OVERRIDE, settings.ttsProsodySettings.value.speechRateMode)
            assertEquals(TtsProsodyMode.OVERRIDE, settings.ttsProsodySettings.value.pitchMode)
            assertEquals(4f to 1.5f, engine.prosodyApplications.last())

            controller.setSpeechRateMode(TtsProsodyMode.FOLLOW_ENGINE)
            controller.setPitchMode(TtsProsodyMode.FOLLOW_ENGINE)
            assertEquals(null to null, engine.prosodyApplications.last())
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rejectedAndroidProsodyKeepsPreviousSettingAndPlaybackAlive() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val settings = FakeSettingsStore()
        val engine = FakePlaybackEngine()
        val controller = controller(engine, settingsStore = settings)
        try {
            controller.start("book", FakePageSource(page(0, "One. Two.")), 0, 0)
            engine.rejectNextProsody = true

            controller.setSpeechRate(5f)

            assertEquals(1f, controller.speechRate.value)
            assertEquals(TtsProsodyMode.FOLLOW_ENGINE, controller.speechRateMode.value)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
            assertEquals(listOf("One."), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun missingReaderAcknowledgementResumesPlaybackAfterTimeout() = runTest {
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
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            // Waiting for the reader to show page 1 before speaking again.
            assertEquals(listOf("One."), engine.spokenTexts)

            advanceTimeBy(2_000L)
            runCurrent()

            // The reader never confirmed (app backgrounded or closed): playback must continue.
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun duplicateUserCallbackAfterPageTurnKeepsReaderFollowing() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start(
                "book",
                FakePageSource(
                    page(0, "One.", next = TtsPageLocation(0, 1)),
                    page(1, "Two.", previous = TtsPageLocation(0, 0), next = TtsPageLocation(0, 2)),
                    page(2, "Three.", previous = TtsPageLocation(0, 1))
                ),
                0,
                0
            )
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.TTS_FOLLOW)
            runCurrent()
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)

            // A stale callback for the page we just left must not count as a user page change.
            controller.onPageVisible("book", 0, 0, TtsPageChangeOrigin.USER)
            runCurrent()

            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)

            controller.onPageVisible("book", 0, 2, TtsPageChangeOrigin.TTS_FOLLOW)
            runCurrent()
            assertEquals(listOf("One.", "Two.", "Three."), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun userPageChangeKeepsPlaybackPosition() = runTest {
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

            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.USER)
            runCurrent()

            assertEquals(TtsPageLocation(0, 0), controller.currentPage.value?.location)
            assertEquals(listOf("One."), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun browsingThroughSpokenPageDoesNotReenableFollowing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        val turns = mutableListOf<TtsPageTurnRequest>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.pageTurnRequests.collect { turns += it }
        }
        try {
            controller.start("book", FakePageSource(
                page(0, "One.", next = TtsPageLocation(0, 1)),
                page(1, "Two.", previous = TtsPageLocation(0, 0))
            ), 0, 0)
            runCurrent()
            turns.clear()
            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.USER)
            runCurrent()
            controller.onPageVisible("book", 0, 0, TtsPageChangeOrigin.USER)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)
            assertTrue("passing the spoken page must not pull the viewport back", turns.isEmpty())
        } finally {
            collector.cancel()
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun manualGestureCancelsPendingTurnAndRejectsLateCallbacksUntilExplicitReturn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        val turns = mutableListOf<TtsPageTurnRequest>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.pageTurnRequests.collect { turns += it }
        }
        try {
            controller.start("book", FakePageSource(
                page(0, "One.", next = TtsPageLocation(0, 1)),
                page(1, "Two.", next = TtsPageLocation(0, 2)),
                page(2, "Three.", next = TtsPageLocation(0, 3)),
                page(3, "Four.")
            ), 0, 0)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            val cancelled = turns.last()
            assertEquals(listOf("One."), engine.spokenTexts)
            controller.setReaderAttached("book", false)
            runCurrent()
            assertFalse(controller.isPageTurnRequestActive(cancelled))
            assertTrue(controller.pageTurnRequests.replayCache.isEmpty())
            turns.clear()
            controller.acknowledgePageTurnRequest(cancelled)
            controller.onPageVisible("book", 0, 0, TtsPageChangeOrigin.TTS_FOLLOW)
            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.USER)
            runCurrent()
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)
            assertEquals(TtsPageLocation(0, 1), controller.currentPage.value?.location)
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals("Three.", engine.spokenTexts.last())
            assertTrue(turns.isEmpty())
            controller.setReaderAttached("book", true)
            runCurrent()
            assertFalse(controller.isPageTurnRequestActive(cancelled))
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            val resumed = turns.single()
            assertEquals(TtsPageLocation(0, 3), resumed.location)
            controller.acknowledgePageTurnRequest(resumed)
            runCurrent()
            assertEquals("Four.", engine.spokenTexts.last())
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
        } finally {
            collector.cancel()
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun playbackAdvancesPagesWithoutReaderFollowAfterUserPageChange() = runTest {
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

            controller.onPageVisible("book", 0, 1, TtsPageChangeOrigin.USER)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()

            // Following is off, so playback advances on its own instead of waiting for the reader.
            assertEquals(TtsPageLocation(0, 1), controller.currentPage.value?.location)
            assertEquals(listOf("One.", "Two."), engine.spokenTexts)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun userPageChangeWhileWaitingForReaderAcknowledgementDoesNotStallPlayback() = runTest {
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
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            // Playback asked the reader to show page 1 and waits for the acknowledgement.
            assertEquals(listOf("One."), engine.spokenTexts)

            controller.onPageVisible("book", 0, 0, TtsPageChangeOrigin.USER)
            runCurrent()

            assertEquals(listOf("One.", "Two."), engine.spokenTexts)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun seekToResumesFromTheTappedSentence() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一句。第二句。第三句。")), 0, 0)
            assertEquals("第一句。", controller.currentSentence.value?.text)

            controller.seekTo(0, 4)
            runCurrent()

            assertEquals("第二句。", controller.currentSentence.value?.text)
            assertEquals(listOf("第一句。", "第二句。"), engine.spokenTexts)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun seekToWhilePausedStartsPlaybackFromTheTappedSentence() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "第一句。第二句。第三句。")), 0, 0)
            controller.pause()
            runCurrent()
            assertEquals(TtsPlaybackState.PAUSED, controller.playbackState.value)

            controller.seekTo(0, 8)
            runCurrent()

            assertEquals("第三句。", controller.currentSentence.value?.text)
            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
            assertEquals(listOf("第一句。", "第三句。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun seekToLocatesTheTappedChapter() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start(
                "book",
                FakePageSource(
                    page(0, "One.", next = TtsPageLocation(1, 0), startCharacterOffset = 0),
                    page(
                        0,
                        "第二章第一句。第二章第二句。",
                        previous = TtsPageLocation(0, 0),
                        chapterIndex = 1,
                        startCharacterOffset = 4
                    )
                ),
                0,
                0
            )

            controller.seekTo(1, 12)
            runCurrent()

            assertEquals(TtsPageLocation(1, 0), controller.currentPage.value?.location)
            assertEquals("第二章第二句。", controller.currentSentence.value?.text)
            assertEquals(listOf("One.", "第二章第二句。"), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun seekToIsIgnoredWithoutAnActiveSession() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.seekTo(0, 4)
            runCurrent()

            assertEquals(TtsPlaybackState.IDLE, controller.playbackState.value)
            assertEquals(emptyList<String>(), engine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun inactiveEngineInterruptionCannotPauseCurrentSession() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val systemEngine = FakePlaybackEngine()
        val externalEngine = FakePlaybackEngine(isExternal = true)
        val controller = controller(systemEngine, externalEngine)
        try {
            controller.start("book", FakePageSource(page(0, "One. Two.")), 0, 0)

            externalEngine.interrupt()
            runCurrent()

            assertEquals(TtsPlaybackState.PLAYING, controller.playbackState.value)
            assertEquals(listOf("One."), systemEngine.spokenTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun systemEnginePrefetchesTheWholeNextSentenceAcrossPages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(
                page(0, "One. Two, together.", next = TtsPageLocation(0, 1)),
                page(1, "Three.", previous = TtsPageLocation(0, 0))), 0, 0)
            runCurrent()
            assertEquals(listOf("Two, together."), engine.prefetchedTexts)
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(listOf("Two, together.", "Three."), engine.prefetchedTexts)
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun prefetchMergesTheSameCrossPageSentenceAsPlayback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(
                page(0, "One. Continuing", next = TtsPageLocation(0, 1)),
                page(1, " the sentence. Next.", previous = TtsPageLocation(0, 0))), 0, 0)
            runCurrent()
            engine.complete(engine.lastUtteranceId)
            runCurrent()
            assertEquals(engine.prefetchedTexts.first(), engine.spokenTexts.last())
            assertTrue(engine.spokenTexts.last().contains("the sentence."))
        } finally {
            controller.shutdown()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rawAudioIOExceptionRetriesAndNewSentenceGetsItsOwnRetryBudget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = FakePlaybackEngine()
        val controller = controller(engine)
        try {
            controller.start("book", FakePageSource(page(0, "One. Two.")), 0, 0)
            engine.error(engine.lastUtteranceId, IOException("AudioTrack initialization failed"))
            runCurrent()
            assertEquals(listOf("One.", "One."), engine.spokenTexts)
            controller.skip()
            runCurrent()
            engine.error(engine.lastUtteranceId)
            runCurrent()
            assertEquals(listOf("One.", "One.", "Two.", "Two."), engine.spokenTexts)
            assertEquals(3, engine.initializeCalls)
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
        next: TtsPageLocation? = null,
        chapterIndex: Int = 0,
        startCharacterOffset: Int = 0
    ) = TtsPageContent(
        location = TtsPageLocation(chapterIndex, index),
        text = text,
        previous = previous,
        next = next,
        startCharacterOffset = startCharacterOffset
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

        override suspend fun locatePage(
            chapterIndex: Int,
            characterOffset: Int
        ): TtsPageContent? {
            val candidates = pages.values
                .filter { it.location.chapterIndex == chapterIndex }
                .sortedBy { it.location.pageIndex }
            if (candidates.isEmpty()) return null
            return candidates.firstOrNull { characterOffset < it.startCharacterOffset + it.text.length }
                ?: candidates.last()
        }

        override fun close() {
            closed = true
        }
    }

    private class FakePlaybackEngine(
        override val isExternal: Boolean = false,
        override val canResumeWithoutRestart: Boolean = false
    ) : AndroidTtsPlaybackEngine {
        private lateinit var listener: TtsPlaybackListener
        val spokenTexts = mutableListOf<String>()
        val prefetchedTexts = mutableListOf<String>()
        val selectedEnginePackages = mutableListOf<String?>()
        val suppliedCacheKeys = mutableListOf<String?>()
        val suppliedStartFrames = mutableListOf<Long>()
        val prosodyApplications = mutableListOf<Pair<Float?, Float?>>()
        var lastUtteranceId = ""
        var initializeCalls = 0
        var failNextInitialize = false
        var rejectNextProsody = false

        override suspend fun selectEngine(packageName: String?) {
            selectedEnginePackages += packageName
        }

        override suspend fun initialize(): Result<Unit> {
            initializeCalls++
            if (failNextInitialize) {
                failNextInitialize = false
                return Result.failure(SystemTtsException.Initialization())
            }
            return Result.success(Unit)
        }

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
        fun error(utteranceId: String, error: Throwable = SystemTtsException.Playback()) =
            listener.onError(utteranceId, error)
        override suspend fun prefetch(text: String) { prefetchedTexts += text }
        fun range(utteranceId: String, start: Int, end: Int) =
            listener.onRangeStart(utteranceId, start, end)
        fun interrupt() = listener.onPlaybackInterrupted()

        override suspend fun pause() = Unit
        override suspend fun resume(): Boolean = canResumeWithoutRestart
        override suspend fun stop() = Unit
        override suspend fun setSpeechRate(rate: Float) = Unit
        override suspend fun setPitch(pitch: Float) = Unit
        override suspend fun applyProsody(rate: Float?, pitch: Float?) {
            if (rejectNextProsody) {
                rejectNextProsody = false
                throw SystemTtsException.ProsodyRejected(
                    TtsProsodySetting.RATE,
                    rate ?: 1f,
                    -1
                )
            }
            prosodyApplications += rate to pitch
        }
        override fun setListener(listener: TtsPlaybackListener) {
            this.listener = listener
        }
        override fun shutdown() = Unit
    }

    private class FakeSettingsStore(
        providerSelection: TtsProviderSelection = TtsProviderSelection.SystemDefault
    ) : TtsSettingsStore {
        override val ttsProsodySettings = MutableStateFlow(TtsProsodySettings())
        override val ttsProviderSelection = MutableStateFlow(providerSelection)
        override val externalTtsSettings = MutableStateFlow(ExternalTtsSettings())
        override val ttsStartChoice = MutableStateFlow<TtsStartChoice?>(null)

        override suspend fun saveTtsStartChoice(choice: TtsStartChoice?) {
            ttsStartChoice.value = choice
        }
        private val resumePositions = mutableMapOf<String, MutableStateFlow<ExternalTtsResumePosition?>>()
        private val listeningPositions = mutableMapOf<String, MutableStateFlow<TtsListeningPosition?>>()

        override fun ttsListeningPosition(bookId: String): Flow<TtsListeningPosition?> =
            listeningPositions.getOrPut(bookId) { MutableStateFlow(null) }

        override suspend fun saveTtsListeningPosition(position: TtsListeningPosition) {
            listeningPositions.getOrPut(position.bookId) { MutableStateFlow(null) }.value = position
        }

        override fun externalTtsResumePosition(bookId: String): Flow<ExternalTtsResumePosition?> =
            resumePositions.getOrPut(bookId) { MutableStateFlow(null) }

        fun seedResume(position: ExternalTtsResumePosition) {
            resumePositions.getOrPut(position.bookId) { MutableStateFlow(null) }.value = position
        }

        override suspend fun saveTtsProsodySettings(settings: TtsProsodySettings) {
            ttsProsodySettings.value = settings.normalized()
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

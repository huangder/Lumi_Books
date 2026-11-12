package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderOrientationEventsTest {
    @Test fun viewportCalibrationCanRestartTheSameNavigationWithoutFailingPreparation() {
        val events = ReaderOrientationEvents().apply { active = true }
        val calls = mutableListOf<String>()
        events.expect(-1)
        events.finishNavigation(EpubNavigationResult.Cancelled(-1, replaced = true))
        assertTrue(events.preparationPending)
        assertFalse(events.preparationFailed)
        events.dispatch("onPageChanged", true) { calls += "calibrated" }
        assertTrue(calls.isEmpty())
        events.finishNavigation(EpubNavigationResult.Committed(-1, 2, 7, 15, "{}"))
        assertEquals(listOf("calibrated"), calls)
        assertFalse(events.preparationPending)
        events.expect(-2)
        events.finishNavigation(EpubNavigationResult.Failed(-1, EpubNavigationFailureReason.TIMEOUT))
        assertFalse(events.preparationFailed)
        events.finishNavigation(EpubNavigationResult.Cancelled(-2, replaced = false))
        assertTrue(events.preparationFailed)
    }
    @Test fun foregroundNavigationSupersedesPreparationAndCancellationDoesNotLockProgress() {
        val events = ReaderOrientationEvents().apply { active = true }
        val calls = mutableListOf<String>()
        events.expect(-1)
        events.dispatch("onPage", true) { calls += "old" }
        events.expectForeground(3)
        events.complete(-1)
        assertTrue(events.preparationPending)
        events.dispatch("onPage", true) { calls += "destination" }
        events.complete(3)
        assertEquals(listOf("destination"), calls)
        events.expectForeground(3)
        assertFalse(events.preparationPending)
        events.expect(-2)
        events.dispatch("onPage", true) { calls += "cancelled" }
        assertTrue(events.abandon(-2))
        events.dispatch("onPage", true) { calls += "finger" }
        assertEquals(listOf("destination", "finger"), calls)
    }

    @Test fun failedPreparationDropsOldPositionAndRetriesOnceOnActivation() {
        val events = ReaderOrientationEvents()
        events.configureViewport(ReaderViewport(700, 1000))
        events.expect(-1)
        events.dispatch("onPage", true) { fail("stale page") }
        events.fail(-1)
        assertTrue(events.preparationFailed)
        assertFalse(events.hasReadyContent)
        events.active = true
        events.publishActivation()
        events.retryOnActivation()
        assertEquals(1, events.recoveryGeneration)
        events.retryOnActivation()
        assertEquals(1, events.recoveryGeneration)
        assertFalse(events.preparationFailed)
    }
    @Test fun hiddenPreparationCannotPublishNavigationOrProgressAndReplaysAfterCommit() {
        val events = ReaderOrientationEvents()
        val calls = mutableListOf<String>()
        events.expect(-1)
        events.dispatch("onPageChanged", true) { calls += "page" }
        events.dispatch("onNavigationResult") { calls += "navigation" }
        events.dispatch("onPageTextProviderReady", true) { calls += "provider" }
        assertTrue(calls.isEmpty())
        events.active = true
        events.publishActivation()
        assertEquals(listOf("provider"), calls)
        events.complete(-2)
        assertTrue(events.preparationPending)
        events.complete(-1)
        assertEquals(listOf("provider", "page"), calls)
        assertFalse(events.preparationPending)
    }

    @Test fun newerAnchorDropsOldPageAndInactiveReleaseCannotUnregisterForegroundProvider() {
        val events = ReaderOrientationEvents()
        val calls = mutableListOf<String>()
        events.expect(-1)
        events.dispatch("onPageChanged", true) { calls += "stale" }
        events.expect(-2)
        events.complete(-1)
        events.active = true
        events.publishActivation()
        assertTrue(calls.isEmpty())
        events.dispatch("onPageChanged", true) { calls += "fresh" }
        events.complete(-2)
        assertEquals(listOf("fresh"), calls)
        events.active = false
        events.dispatch("onPageTextProviderReady", true) { calls += "released" }
        assertEquals(listOf("fresh"), calls)
    }

    @Test fun twoEntryResourceCacheTrimsAlternateAndDisposesExactlyOnce() {
        val disposed = mutableListOf<String>()
        val cache = ReaderOrientationCache<String, String> { disposed += it }
        cache.activate("portrait"); cache.put("portrait", "a"); cache.put("landscape", "b")
        cache.put("resized", "c")
        assertEquals(listOf("b"), disposed)
        assertEquals("a", cache["portrait"])
        cache.trim(); assertEquals(listOf("b", "c"), disposed)
        cache.clear(); assertEquals(listOf("b", "c", "a"), disposed)
    }
}

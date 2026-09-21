package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class EpubDocumentLifecycleTest {
    @Test
    fun promotedPageDoesNotReplayTheDocumentEntrance() {
        val state = EpubDocumentLifecycle()
        state.beginDocument()
        state.configure("prepared-page")
        state.observeLayout(state.configurationGeneration, 1, stable = true)
        state.markPresented()
        assertFalse(state.claimReveal(state.configurationGeneration, 1))
    }

    @Test
    fun identicalSettingsDoNotRepaginateAndCommandsAreIndependent() {
        val state = EpubDocumentLifecycle()
        state.beginDocument()
        assertTrue(state.configure("viewport=400x800;flow=scrolled"))
        val generation = state.configurationGeneration
        assertFalse(state.configure("viewport=400x800;flow=scrolled"))
        assertEquals(generation, state.configurationGeneration)
        assertTrue(state.issueCommand("page:1"))
        assertFalse(state.issueCommand("page:1"))
        assertTrue(state.issueCommand("page:2"))
        assertEquals(generation, state.configurationGeneration)
        assertTrue(state.configure("viewport=800x400;flow=scrolled"))
    }

    @Test
    fun entranceRequiresStableCurrentRevisionAndRunsOnlyOncePerDocument() {
        val state = EpubDocumentLifecycle()
        state.beginDocument()
        state.configure("comic")
        val generation = state.configurationGeneration
        assertTrue(state.observeLayout(generation, 1, stable = false))
        assertFalse(state.claimReveal(generation, 1))
        assertTrue(state.observeLayout(generation, 1, stable = true))
        // Resize arrives before the visual-state callback for revision 1.
        assertTrue(state.observeLayout(generation, 2, stable = false))
        assertFalse(state.claimReveal(generation, 1))
        assertFalse(state.observeLayout(generation, 1, stable = true))
        assertTrue(state.observeLayout(generation, 2, stable = true))
        assertTrue(state.claimReveal(generation, 2))
        assertFalse(state.claimReveal(generation, 2))
        state.configure("comic;new-margin")
        assertTrue(state.observeLayout(state.configurationGeneration, 3, stable = true))
        assertFalse(state.claimReveal(state.configurationGeneration, 3))
    }

    @Test
    fun reloadRejectsOldCallbacksEvenForTheSameChapterAndAllowsANewEntrance() {
        val state = EpubDocumentLifecycle()
        state.beginDocument()
        state.configure("comic")
        val oldGeneration = state.configurationGeneration
        state.observeLayout(oldGeneration, 4, stable = true)
        state.beginDocument()
        assertTrue(state.configure("comic"))
        assertFalse(state.observeLayout(oldGeneration, 4, stable = true))
        assertFalse(state.claimReveal(oldGeneration, 4))
        state.configurationFailed(oldGeneration)
        assertFalse(state.configure("comic"))
        assertTrue(state.observeLayout(state.configurationGeneration, 1, stable = true))
        assertTrue(state.claimReveal(state.configurationGeneration, 1))
    }
}

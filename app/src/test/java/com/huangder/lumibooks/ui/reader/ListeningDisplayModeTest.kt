package com.huangder.lumibooks.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.huangder.lumibooks.tts.ListeningDisplayStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningDisplayModeTest {
    @Test fun newPageUsesSavedDefaultAndTemporaryToggleDoesNotResetOnRepeatedEmission() {
        val mode = ListeningDisplayMode(SavedStateHandle())
        mode.applyDefault(ListeningDisplayStyle.SUBTITLES)
        assertTrue(mode.subtitles.value)
        mode.toggle()
        mode.applyDefault(ListeningDisplayStyle.SUBTITLES)
        assertFalse(mode.subtitles.value)
        val reopened = ListeningDisplayMode(SavedStateHandle())
        reopened.applyDefault(ListeningDisplayStyle.SUBTITLES)
        assertTrue(reopened.subtitles.value)
    }

    @Test fun recreatedPagePreservesTemporaryModeAndThenRespondsToChangedDefault() {
        val saved = SavedStateHandle(mapOf("subtitles" to true))
        val mode = ListeningDisplayMode(saved)
        mode.applyDefault(ListeningDisplayStyle.COVER)
        assertTrue(mode.subtitles.value)
        mode.toggle()
        mode.applyDefault(ListeningDisplayStyle.SUBTITLES)
        assertTrue(mode.subtitles.value)
        mode.applyDefault(ListeningDisplayStyle.COVER)
        assertFalse(mode.subtitles.value)
    }
}

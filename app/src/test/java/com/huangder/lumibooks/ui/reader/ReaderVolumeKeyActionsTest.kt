package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderVolumeKeyAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderVolumeKeyActionsTest {
    @Test
    fun fontSizeActionsFallBackToSystemVolumeForRasterReaders() {
        assertEquals(
            ReaderVolumeKeyAction.SYSTEM_VOLUME,
            resolveReaderVolumeKeyAction(ReaderVolumeKeyAction.FONT_SIZE_DOWN, supportsFontSize = false)
        )
        assertEquals(
            ReaderVolumeKeyAction.SYSTEM_VOLUME,
            resolveReaderVolumeKeyAction(ReaderVolumeKeyAction.FONT_SIZE_UP, supportsFontSize = false)
        )
        assertEquals(
            ReaderVolumeKeyAction.FONT_SIZE_UP,
            resolveReaderVolumeKeyAction(ReaderVolumeKeyAction.FONT_SIZE_UP, supportsFontSize = true)
        )
    }

    @Test
    fun listeningActionsWorkForTextAndPdfAndFallBackForComics() {
        for (action in listOf(
            ReaderVolumeKeyAction.TOGGLE_TTS_SESSION,
            ReaderVolumeKeyAction.TOGGLE_TTS_PLAY_PAUSE
        )) {
            assertEquals(action, resolveReaderVolumeKeyAction(action, supportsFontSize = true))
            assertEquals(action, resolveReaderVolumeKeyAction(action, supportsFontSize = false))
            assertEquals(
                ReaderVolumeKeyAction.SYSTEM_VOLUME,
                resolveReaderVolumeKeyAction(action, supportsFontSize = false, supportsListening = false)
            )
        }
    }

    @Test
    fun brightnessStartsFromDefaultAndClampsToBounds() {
        assertEquals(0.85f, adjustReaderBrightness(-1f, increase = true), 0.0001f)
        assertEquals(0f, adjustReaderBrightness(0f, increase = false), 0.0001f)
        assertEquals(1f, adjustReaderBrightness(1f, increase = true), 0.0001f)
    }

    @Test
    fun fontSizeMovesByOneAndClampsToReaderRange() {
        assertEquals(13f, adjustReaderFontSize(12f, increase = true), 0.0001f)
        assertEquals(12f, adjustReaderFontSize(12f, increase = false), 0.0001f)
        assertEquals(33f, adjustReaderFontSize(33f, increase = true), 0.0001f)
        assertEquals(32f, adjustReaderFontSize(33f, increase = false), 0.0001f)
    }

    @Test
    fun unknownStoredActionFallsBackToSystemVolume() {
        assertEquals(ReaderVolumeKeyAction.SYSTEM_VOLUME, ReaderVolumeKeyAction.fromStorage("unknown"))
        assertEquals(ReaderVolumeKeyAction.SYSTEM_VOLUME, ReaderVolumeKeyAction.fromStorage(null))
    }
}

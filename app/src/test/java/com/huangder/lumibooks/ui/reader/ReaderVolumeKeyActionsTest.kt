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
    fun brightnessStartsFromDefaultAndClampsToBounds() {
        assertEquals(0.85f, adjustReaderBrightness(-1f, increase = true), 0.0001f)
        assertEquals(0f, adjustReaderBrightness(0f, increase = false), 0.0001f)
        assertEquals(1f, adjustReaderBrightness(1f, increase = true), 0.0001f)
    }

    @Test
    fun fontSizeMovesByOneAndClampsToReaderRange() {
        assertEquals(13f, adjustReaderFontSize(12f, increase = true), 0.0001f)
        assertEquals(12f, adjustReaderFontSize(12f, increase = false), 0.0001f)
        assertEquals(28f, adjustReaderFontSize(28f, increase = true), 0.0001f)
        assertEquals(27f, adjustReaderFontSize(28f, increase = false), 0.0001f)
    }

    @Test
    fun unknownStoredActionFallsBackToSystemVolume() {
        assertEquals(ReaderVolumeKeyAction.SYSTEM_VOLUME, ReaderVolumeKeyAction.fromStorage("unknown"))
        assertEquals(ReaderVolumeKeyAction.SYSTEM_VOLUME, ReaderVolumeKeyAction.fromStorage(null))
    }
}

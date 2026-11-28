package com.huangder.lumibooks.service

import android.app.Application
import android.media.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class TtsMediaMetadataTest {
    @Test fun mediaCardKeepsBookTitleAndShowsTheAudibleClause() {
        val metadata = ttsMediaMetadata("Book", "Second clause.", "Author", "Lumi", null)
        assertEquals("Book", metadata.description.title)
        assertEquals("Second clause.", metadata.description.subtitle)
        assertEquals("Second clause.", metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
        assertEquals("Author", metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR))
    }
}

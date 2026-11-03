package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.tts.ReaderTtsDock
import com.huangder.lumibooks.tts.ReaderTtsPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderTtsPositionPersistenceTest {
    @Test fun readerPositionSurvivesReopenWithoutChangingSystemSubtitleSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertFalse(manager.readerTtsPosition.first().saved)
        manager.saveFloatingSubtitlePosition(.7f, .2f)
        val subtitles = manager.floatingSubtitleSettings.first()
        val expected = ReaderTtsPosition(.8f, .6f, ReaderTtsDock.RIGHT, true)
        manager.saveReaderTtsPosition(expected)
        val reopened = DataStoreManager(context)
        assertEquals(expected, reopened.readerTtsPosition.first())
        assertEquals(subtitles, reopened.floatingSubtitleSettings.first())
        manager.saveReaderTtsPosition(expected.copy(xFraction = -2f, yFraction = 5f, dock = ReaderTtsDock.NONE))
        assertEquals(expected.copy(xFraction = 0f, yFraction = 1f, dock = ReaderTtsDock.NONE),
            reopened.readerTtsPosition.first())
    }
}

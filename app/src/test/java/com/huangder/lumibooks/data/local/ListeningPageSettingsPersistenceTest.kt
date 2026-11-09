package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.tts.ListeningDisplayStyle
import com.huangder.lumibooks.tts.ListeningPageSettings
import com.huangder.lumibooks.tts.SubtitleSeekGesture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ListeningPageSettingsPersistenceTest {
    @Test fun defaultAndIndependentChoicesSurviveReload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertEquals(ListeningPageSettings(), manager.listeningPageSettings.first())
        manager.saveListeningDisplayStyle(ListeningDisplayStyle.SUBTITLES)
        manager.saveSubtitleSeekGesture(SubtitleSeekGesture.SINGLE_TAP)
        assertEquals(
            ListeningPageSettings(ListeningDisplayStyle.SUBTITLES, SubtitleSeekGesture.SINGLE_TAP),
            DataStoreManager(context).listeningPageSettings.first()
        )
        manager.saveListeningDisplayStyle(ListeningDisplayStyle.COVER)
        assertEquals(SubtitleSeekGesture.SINGLE_TAP, manager.listeningPageSettings.first().seekGesture)
    }
}

package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CoverReadingProgressPersistenceTest {
    @Test
    fun defaultsToMetadataAndPersistsBothChoices() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertFalse(manager.coverReadingProgressEnabled.first())

        manager.saveCoverReadingProgressEnabled(true)
        val reloaded = DataStoreManager(context)
        assertTrue(reloaded.coverReadingProgressEnabled.first())
        val exported = reloaded.exportPortablePreferences("test-device")
            .single { it.key == "cover_reading_progress_enabled" }
        assertEquals("true", exported.value)

        reloaded.saveCoverReadingProgressEnabled(false)
        assertFalse(DataStoreManager(context).coverReadingProgressEnabled.first())
    }
}

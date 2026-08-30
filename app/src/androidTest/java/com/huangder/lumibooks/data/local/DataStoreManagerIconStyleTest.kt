package com.huangder.lumibooks.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreManagerIconStyleTest {
    private lateinit var context: Context
    private lateinit var manager: DataStoreManager

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }
        manager = DataStoreManager(context)
    }

    @After
    fun tearDown() = runBlocking {
        manager.saveAppIconStyle("lumi2")
        context.dataStore.edit { it.clear() }
    }

    @Test
    fun iconStyleDefaultsToLumi2AndPersistsClassic() = runBlocking {
        assertEquals("lumi2", manager.appIconStyle.first())

        manager.saveAppIconStyle("classic")

        assertEquals("classic", manager.appIconStyle.first())
    }
}

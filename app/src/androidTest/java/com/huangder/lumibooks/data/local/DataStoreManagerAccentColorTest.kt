package com.huangder.lumibooks.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreManagerAccentColorTest {
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
        context.dataStore.edit { it.clear() }
    }

    @Test
    fun accentColorFallsBackAndPersistsCanonicalHex() = runBlocking {
        assertEquals(DEFAULT_APP_ACCENT_HEX, manager.appAccentColor.first())

        manager.saveAppAccentColor("1a2b3c")
        assertEquals("#1A2B3C", manager.appAccentColor.first())

        manager.saveAppAccentColor("invalid")
        assertEquals(DEFAULT_APP_ACCENT_HEX, manager.appAccentColor.first())
    }
}

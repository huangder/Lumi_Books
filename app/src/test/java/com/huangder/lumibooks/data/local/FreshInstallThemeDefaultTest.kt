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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FreshInstallThemeDefaultTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() = runBlocking {
        context.dataStore.edit { it.clear() }
        Unit
    }

    @Test fun firstInstallStartsOnLiquidGlass() = runBlocking {
        val manager = DataStoreManager(context)
        assertEquals("lumi", manager.appTheme.first())
        assertTrue(manager.applyFreshInstallDefaults())
        assertEquals("liquid_glass", manager.appTheme.first())
        assertEquals(0.65f, manager.liquidGlassTransparency.first(), 0.001f)
    }

    @Test fun aChosenThemeIsNeverReplaced() = runBlocking {
        val manager = DataStoreManager(context)
        manager.saveAppTheme("material3")
        assertFalse(manager.applyFreshInstallDefaults())
        assertEquals("material3", manager.appTheme.first())
    }

    @Test fun applyingTwiceKeepsTheUsersLaterChoice() = runBlocking {
        val manager = DataStoreManager(context)
        assertTrue(manager.applyFreshInstallDefaults())
        manager.saveAppTheme("lumi")
        assertFalse(manager.applyFreshInstallDefaults())
        assertEquals("lumi", manager.appTheme.first())
    }
}

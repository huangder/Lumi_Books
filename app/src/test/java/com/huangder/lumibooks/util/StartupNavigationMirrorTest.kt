package com.huangder.lumibooks.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.StartupNavigationSnapshot
import com.huangder.lumibooks.domain.model.BookshelfLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StartupNavigationMirrorTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    /** Without a mirror the launch must fall back to reading DataStore. */
    @Test fun missingMirrorIsNotGuessed() {
        assertNull(LaunchThemeController.startupNavigationSnapshot(context))
    }

    @Test fun mirrorRoundTripsTheStartScreenAndShelfLayout() {
        val saved = StartupNavigationSnapshot(DataStoreManager.STARTUP_SCREEN_BOOKSHELF, BookshelfLayout.Spine)
        LaunchThemeController.updateStartupNavigationSnapshot(context, saved)
        assertEquals(saved, LaunchThemeController.startupNavigationSnapshot(context))
    }

    @Test fun otherMirrorWritesKeepTheStartScreen() {
        val saved = StartupNavigationSnapshot(DataStoreManager.STARTUP_SCREEN_STATISTICS, BookshelfLayout.List)
        LaunchThemeController.updateStartupNavigationSnapshot(context, saved)
        LaunchThemeController.updateWelcomeSnapshot(context, 1L, true, true)
        LaunchThemeController.updateIconStyleSnapshot(context, "lumi2")
        assertEquals(saved, LaunchThemeController.startupNavigationSnapshot(context))
    }
}

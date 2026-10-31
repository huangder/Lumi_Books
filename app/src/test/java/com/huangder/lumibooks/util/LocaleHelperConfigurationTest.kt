package com.huangder.lumibooks.util

import android.app.Application
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h900dp-port-mdpi")
class LocaleHelperConfigurationTest {
    @Test
    fun languageOverrideKeepsFollowingRotationAndWindowResizing() {
        val base = RuntimeEnvironment.getApplication()
        val localized = LocaleHelper.wrapContext(base, "en")
        assertEquals(400, localized.resources.configuration.screenWidthDp)
        assertEquals("en", localized.resources.configuration.locales[0].language)

        RuntimeEnvironment.setQualifiers("w900dp-h400dp-land-mdpi")
        assertEquals(900, base.resources.configuration.screenWidthDp)
        assertEquals(900, localized.resources.configuration.screenWidthDp)
        assertEquals(400, localized.resources.configuration.screenHeightDp)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, localized.resources.configuration.orientation)
        assertEquals("en", localized.resources.configuration.locales[0].language)

        RuntimeEnvironment.setQualifiers("w400dp-h900dp-port-mdpi")
        assertEquals(400, localized.resources.configuration.screenWidthDp)
        assertEquals(900, localized.resources.configuration.screenHeightDp)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, localized.resources.configuration.orientation)
        assertEquals("en", localized.resources.configuration.locales[0].language)
    }
}

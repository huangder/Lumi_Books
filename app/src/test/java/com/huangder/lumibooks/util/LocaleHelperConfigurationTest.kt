package com.huangder.lumibooks.util

import android.app.Application
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.huangder.lumibooks.R
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

    @Test
    fun languageOverridePreservesNightMode() {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_YES
        }
        val nightContext = base.createConfigurationContext(configuration)
        val localized = LocaleHelper.wrapContext(nightContext, "en")

        assertEquals(Configuration.UI_MODE_NIGHT_YES,
            localized.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(0xFFF5F5F7.toInt(), ContextCompat.getColor(localized, R.color.widget_text_primary))
    }
}

package com.huangder.lumibooks.util

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], application = Application::class)
class LauncherAutoAppearanceTest {
    @Test
    fun autoKeepsTheSameLauncherWhileAndroidResolvesDayNightArtwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LaunchThemeController.updateIconStyleSnapshot(context, "lumi2_auto")
        for (splash in listOf(true, false)) {
            LaunchThemeController.updateWelcomeSnapshot(context, 0L, splash, false)
            assertTrue(LaunchThemeController.synchronizeLauncherComponents(context))
            val initialStates = componentStates(context)
            for (dark in listOf(false, true, false)) {
                RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
                assertTrue(LaunchThemeController.synchronizeLauncherComponents(context))
                assertEquals(initialStates, componentStates(context))
                assertEquals("lumi2_auto", LaunchThemeController.iconStyleSnapshot(context))

                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(context.packageName)
                val launcher = context.packageManager.queryIntentActivities(intent, 0).single()
                assertEquals(
                    if (splash) LauncherComponentNames.LUMI_2_AUTO_SPLASH
                    else LauncherComponentNames.LUMI_2_AUTO_DIRECT,
                    launcher.activityInfo.name
                )
                assertEquals(R.mipmap.ic_launcher_lumi2_auto_adaptive, launcher.activityInfo.icon)

                // Verify the manifest icon resolves to the adaptive icon for the current theme.
                val layers = mutableMapOf<String, Int>()
                context.resources.getXml(launcher.activityInfo.icon).use { icon ->
                    while (icon.eventType != XmlPullParser.START_TAG) icon.next()
                    assertEquals("adaptive-icon", icon.name)
                    while (icon.next() != XmlPullParser.END_DOCUMENT) {
                        if (icon.eventType == XmlPullParser.START_TAG) {
                            layers[icon.name] = icon.getAttributeResourceValue(
                                "http://schemas.android.com/apk/res/android", "drawable", 0
                            )
                        }
                    }
                }
                assertEquals(
                    if (dark) R.drawable.ic_lumi2_background_dark
                    else R.drawable.ic_lumi2_background_light,
                    layers["background"]
                )
                assertEquals(R.drawable.ic_lumi2_foreground, layers["foreground"])
                assertEquals(R.drawable.ic_lumi2_monochrome, layers["monochrome"])
            }
        }
    }

    private fun componentStates(context: Context) =
        LauncherComponentNames.manifestDefaults.keys.associateWith { name ->
            context.packageManager.getComponentEnabledSetting(ComponentName(context, name))
        }
}

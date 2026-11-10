package com.huangder.lumibooks.ui.components

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29, 35], application = Application::class)
class SystemNavigationBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationStaysTransparentAcrossThemeReaderAndImmersiveExit() {
        val route = mutableStateOf(0)
        val dark = mutableStateOf(false)
        lateinit var activity: Activity
        compose.setContent {
            activity = generateSequence(LocalView.current.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().first()
            EBookReaderTheme(darkTheme = dark.value) {
                when (route.value) {
                    1 -> ReaderSystemBarStyle(Color(0xFF302418), useDarkIcons = !dark.value)
                    2 -> ImmersiveMode()
                    3 -> MainSystemBarStyle()
                }
                Box {}
            }
        }
        // Simulate edge-to-edge setup restoring its default three-button scrim before each
        // route/theme transition. Returning from the fullscreen reader must remove it too.
        for ((nextRoute, isDark) in listOf(1 to false, 2 to false, 3 to false, 0 to true, 1 to true, 3 to true)) {
            compose.runOnIdle {
                activity.window.navigationBarColor = android.graphics.Color.BLACK
                activity.window.navigationBarDividerColor = android.graphics.Color.BLACK
                if (Build.VERSION.SDK_INT >= 29) activity.window.isNavigationBarContrastEnforced = true
                route.value = nextRoute
                dark.value = isDark
            }
            compose.runOnIdle {
                val window = activity.window
                assertEquals("Navigation background: route=$nextRoute, dark=$isDark",
                    android.graphics.Color.TRANSPARENT, window.navigationBarColor)
                assertEquals(android.graphics.Color.TRANSPARENT, window.navigationBarDividerColor)
                if (Build.VERSION.SDK_INT >= 29) assertFalse(window.isNavigationBarContrastEnforced)
                assertEquals(!isDark,
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars)
            }
        }
    }
}

package com.huangder.lumibooks.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.welcome.WelcomeScreen

/** Debug-only walkthrough: theme and completion never write to the user's settings. */
class WelcomePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var theme by remember { mutableStateOf(intent.getStringExtra("theme") ?: "lumi") }
            val ink = intent.getBooleanExtra("ink", false)
            val dark = intent.getBooleanExtra("dark", false) && !ink
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(
                density.density,
                intent.getFloatExtra("font_scale", density.fontScale).coerceIn(0.85f, 2f)
            )) {
                EBookReaderTheme(
                    appTheme = theme,
                    darkTheme = dark,
                    dynamicColor = theme == "material3",
                    eInkMode = ink,
                    motionPreference = if (intent.getBooleanExtra("reduced_motion", false))
                        MotionPreference.REDUCED else MotionPreference.STANDARD
                ) {
                    WelcomeScreen(
                        isNewInstallation = intent.getBooleanExtra("fresh", false),
                        shouldShowLanguageSetup = false,
                        initialLanguage = "zh-CN",
                        initialEInkMode = ink,
                        isEInkMode = ink,
                        isDark = dark,
                        isLiquidGlass = LocalAppTheme.current == "liquid_glass",
                        onFinished = { finish() },
                        onOpenSponsor = {},
                        onLanguageSetupComplete = { _, _ -> },
                        onEnableLiquidGlass = { theme = "liquid_glass" },
                        startOnIntroduction = true
                    )
                }
            }
        }
    }
}

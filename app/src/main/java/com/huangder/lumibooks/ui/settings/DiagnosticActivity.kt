package com.huangder.lumibooks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.util.diagnostics.DiagnosticLogger
import com.huangder.lumibooks.util.diagnostics.DiagnosticSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DiagnosticActivity : ComponentActivity() {
    @Inject lateinit var dataStoreManager: DataStoreManager
    @Inject lateinit var diagnosticSessionManager: DiagnosticSessionManager
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launchTheme = com.huangder.lumibooks.util.LaunchThemeController.themeSnapshot(this)
        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = launchTheme.appTheme)
            val accent by dataStoreManager.appAccentColor.collectAsState(initial = launchTheme.appAccentColor)
            val fontMode by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
            val transparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = launchTheme.liquidGlassTransparency)
            val hdr by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
            val predictiveBack by dataStoreManager.predictiveBackEnabled.collectAsState(initial = launchTheme.predictiveBackEnabled)
            val isDark = when (darkMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            val resolved = effectiveAppTheme(appTheme, rememberLiquidGlassCapability(view = LocalView.current))
            val eInkMode by dataStoreManager.eInkModeEnabled.collectAsState(initial = launchTheme.eInkModeEnabled)
            val motion by dataStoreManager.motionPreference.collectAsState(initial = launchTheme.motionPreference)
            EBookReaderTheme(
                darkTheme = isDark, dynamicColor = resolved == "material3", appTheme = appTheme,
                appAccentColor = accent, liquidGlassTransparency = transparency,
                liquidGlassHdrHighlightEnabled = hdr, globalFontMode = fontMode,
                eInkMode = eInkMode,
                motionPreference = com.huangder.lumibooks.ui.theme.MotionPreference.fromStoredValue(motion),
                lumiBackgroundScene = com.huangder.lumibooks.ui.theme.LumiBackgroundScene.SECONDARY
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(predictiveBackEnabled = predictiveBack, onBack = ::finish)
                DiagnosticPage(onBack = ::finish, diagnosticSessionManager = diagnosticSessionManager, diagnosticLogger = diagnosticLogger)
            }
        }
    }
}

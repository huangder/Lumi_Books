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
        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val accent by dataStoreManager.appAccentColor.collectAsState(initial = DEFAULT_APP_ACCENT_HEX)
            val fontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val transparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val hdr by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val predictiveBack by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = when (darkMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            val resolved = effectiveAppTheme(appTheme, rememberLiquidGlassCapability(view = LocalView.current))
            EBookReaderTheme(darkTheme = isDark, dynamicColor = resolved == "material3", appTheme = resolved, appAccentColor = accent, liquidGlassTransparency = transparency, liquidGlassHdrHighlightEnabled = hdr, globalFontMode = fontMode) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(predictiveBackEnabled = predictiveBack, onBack = ::finish)
                DiagnosticPage(onBack = ::finish, diagnosticSessionManager = diagnosticSessionManager, diagnosticLogger = diagnosticLogger)
            }
        }
    }
}

package com.huangder.lumibooks.ui.settings

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.layout.Box

/**
 * 设置页 — 独立 Activity
 *
 * 过渡动画：不调用 overrideActivityTransition / overridePendingTransition，
 * 由系统使用 OEM 默认动画（小米 HyperOS、OPPO ColorOS、vivo OriginOS、
 * 荣耀 MagicOS 各自的原生过渡），Pixel 走 Android 原生 CrossActivityAnim。
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private var systemDarkMode by mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()
        val (initialAppTheme, initialTransparency, initialDarkMode) = runBlocking {
            Triple(
                dataStoreManager.appTheme.first(),
                dataStoreManager.liquidGlassTransparency.first(),
                dataStoreManager.darkMode.first()
            )
        }
        val initialHdrHighlightEnabled = runBlocking {
            dataStoreManager.liquidGlassHdrHighlightEnabled.first()
        }

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = initialAppTheme)
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = initialTransparency)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = initialHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = initialDarkMode)
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = appTheme == "material3",
                appTheme = appTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled
            ) {
                val settingsBackdrop = rememberLayerBackdrop()
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
                        modifier = Modifier.fillMaxSize(),
                        backdrop = settingsBackdrop.takeIf { appTheme == "liquid_glass" }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (appTheme == "liquid_glass") {
                                        Modifier.layerBackdrop(settingsBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop(null) {
                                SettingsScreen(
                                    onNavigateBack = { finish() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    private fun Configuration.isNightModeEnabled(): Boolean {
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    // finish() 不再覆写 — 系统默认返回动画由 OEM 自行控制
}

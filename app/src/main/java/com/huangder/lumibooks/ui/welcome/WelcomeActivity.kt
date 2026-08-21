package com.huangder.lumibooks.ui.welcome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.lifecycleScope
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.settings.SponsorActivity
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

/**
 * 欢迎页独立 Activity，与主页 Compose 树完全隔离，从根本上避免 TabBar 穿透问题。
 *
 * 首次安装显示常规欢迎页，覆盖安装或版本更新显示更新欢迎页。
 * 每次安装只展示一次，完成支持项目页后再跳转 MainActivity。
 */
@AndroidEntryPoint
class WelcomeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEBUG_PREVIEW_LANGUAGE_SETUP = "debug_preview_language_setup"
        const val EXTRA_DEBUG_PREVIEW_POLICY_DOCUMENT = "debug_preview_policy_document"
        const val EXTRA_DEBUG_PREVIEW_SUPPORT_WELCOME = "debug_preview_support_welcome"
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val isDebugLanguagePreview = BuildConfig.DEBUG && intent.getBooleanExtra(
            EXTRA_DEBUG_PREVIEW_LANGUAGE_SETUP,
            false
        )
        val isDebugPolicyPreview = BuildConfig.DEBUG && intent.getBooleanExtra(
            EXTRA_DEBUG_PREVIEW_POLICY_DOCUMENT,
            false
        )
        val isDebugSupportPreview = BuildConfig.DEBUG && intent.getBooleanExtra(
            EXTRA_DEBUG_PREVIEW_SUPPORT_WELCOME,
            false
        )
        val isDebugWelcomePreview = isDebugLanguagePreview || isDebugPolicyPreview || isDebugSupportPreview
        val installState = readInstallState()
        val (completedInstallTime, splashEnabled, hasCompletedLanguageSetup) = runBlocking {
            Triple(
                dataStoreManager.completedWelcomeInstallTime.first(),
                dataStoreManager.splashEnabled.first(),
                dataStoreManager.hasCompletedWelcomeLanguageSetup.first()
            )
        }
        val initialLanguage = LocaleHelper.getLanguage(this)
        if (!isDebugWelcomePreview) {
            LaunchThemeController.deferSplashEnabled(this, splashEnabled)
        }
        if (!isDebugWelcomePreview && !installState.shouldShowWelcome(completedInstallTime)) {
            startMainActivity(splashEnabled)
            return
        }

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val eInkModeEnabled by dataStoreManager.eInkModeEnabled.collectAsState(initial = false)
            val isDark = if (eInkModeEnabled) {
                false
            } else {
                when (darkMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }
            }
            val liquidGlassCapability = rememberLiquidGlassCapability(eInkModeEnabled, LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(appTheme, liquidGlassCapability)

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                eInkMode = eInkModeEnabled,
                globalFontMode = globalFontMode
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = {
                        if (isDebugWelcomePreview) {
                            startMainActivity(splashEnabled)
                        } else {
                            finish()
                        }
                    }
                )
                CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            WelcomeScreen(
                                isUpdate = installState.isUpdate,
                                isNewInstallation = !installState.isUpdate,
                                shouldShowLanguageSetup = isDebugLanguagePreview || !hasCompletedLanguageSetup,
                                initialLanguage = initialLanguage,
                                initialEInkMode = eInkModeEnabled,
                                isEInkMode = eInkModeEnabled,
                                isDark = isDark,
                                isLiquidGlass = resolvedAppTheme == "liquid_glass",
                                onFinished = {
                                    if (!isDebugWelcomePreview) {
                                        runBlocking {
                                            dataStoreManager.completeWelcomeFlow(installState.installMarker)
                                        }
                                    }
                                    startMainActivity(splashEnabled)
                                },
                                onExit = {
                                    if (isDebugWelcomePreview) {
                                        startMainActivity(splashEnabled)
                                    } else {
                                        finish()
                                    }
                                },
                                onOpenSponsor = {
                                    startActivity(Intent(this@WelcomeActivity, SponsorActivity::class.java))
                                },
                                onLanguageSetupComplete = { language, eInkEnabled ->
                                    if (isDebugWelcomePreview) {
                                        startMainActivity(splashEnabled)
                                    } else {
                                        LocaleHelper.saveLanguage(this@WelcomeActivity, language)
                                        lifecycleScope.launch {
                                            dataStoreManager.saveEInkModeEnabled(eInkEnabled)
                                            dataStoreManager.saveWelcomeLanguageSetupCompleted()
                                            recreate()
                                        }
                                    }
                                },
                                startOnIntroduction = isDebugPolicyPreview,
                                startOnSupport = isDebugSupportPreview,
                                onEnableLiquidGlass = {
                                    lifecycleScope.launch {
                                        dataStoreManager.enableLiquidGlassTheme()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startMainActivity(splashEnabled: Boolean) {
        startActivity(LaunchThemeController.mainIntent(this, splashEnabled))
        finish()
    }

    private fun readInstallState(): WelcomeInstallState {
        return try {
            packageManager.getPackageInfo(packageName, 0).let { packageInfo ->
                WelcomeInstallState(
                    firstInstallTime = packageInfo.firstInstallTime,
                    lastUpdateTime = packageInfo.lastUpdateTime
                )
            }
        } catch (_: Exception) {
            WelcomeInstallState(
                firstInstallTime = 0L,
                lastUpdateTime = File(applicationInfo.sourceDir).lastModified()
            )
        }
    }
}

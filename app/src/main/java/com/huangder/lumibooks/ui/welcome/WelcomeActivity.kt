package com.huangder.lumibooks.ui.welcome

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
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
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

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val installState = readInstallState()
        val (completedInstallTime, splashEnabled) = runBlocking {
            dataStoreManager.completedWelcomeInstallTime.first() to
                dataStoreManager.splashEnabled.first()
        }
        LaunchThemeController.deferSplashEnabled(this, splashEnabled)
        if (!installState.shouldShowWelcome(completedInstallTime)) {
            startMainActivity(splashEnabled)
            return
        }

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = appTheme == "material3"
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        WelcomeScreen(
                            isUpdate = installState.isUpdate,
                            isDark = isDark,
                            onFinished = {
                                runBlocking {
                                    dataStoreManager.completeWelcomeFlow(installState.installMarker)
                                }
                                startMainActivity(splashEnabled)
                            },
                            onExit = { finish() }
                        )
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

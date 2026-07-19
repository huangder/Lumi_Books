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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 欢迎页独立 Activity，与主页 Compose 树完全隔离，从根本上避免 TabBar 穿透问题。
 *
 * 首次安装显示常规欢迎页，升级到指定引导版本显示更新欢迎页。
 * 两种入口都会进入支持项目页，完成后再跳转 MainActivity。
 */
@AndroidEntryPoint
class WelcomeActivity : ComponentActivity() {

    private companion object {
        const val CURRENT_WELCOME_FLOW_VERSION = 1
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val completedFlowVersion = runBlocking {
            dataStoreManager.completedWelcomeFlowVersion.first()
        }
        if (completedFlowVersion >= CURRENT_WELCOME_FLOW_VERSION) {
            startMainActivity()
            return
        }
        val isAppUpdate = isAppUpdate()

        setContent {
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            EBookReaderTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WelcomeScreen(
                        isUpdate = isAppUpdate,
                        isDark = isDark,
                        onFinished = {
                            runBlocking {
                                dataStoreManager.completeWelcomeFlow(CURRENT_WELCOME_FLOW_VERSION)
                            }
                            startMainActivity()
                        },
                        onExit = { finish() }
                    )
                }
            }
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun isAppUpdate(): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0).let { packageInfo ->
                packageInfo.lastUpdateTime > packageInfo.firstInstallTime
            }
        } catch (_: Exception) {
            false
        }
    }
}

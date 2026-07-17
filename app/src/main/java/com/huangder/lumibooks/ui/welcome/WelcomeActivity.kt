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
 * 首次启动 → 显示欢迎页 → 点击"继续" → 保存状态 → 跳转 MainActivity
 * 已看过欢迎页 → 直接跳转 MainActivity（无感）
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

        // 已看过欢迎页 → 直接跳转主页（无闪烁）
        val hasSeen = runBlocking { dataStoreManager.hasSeenWelcome.first() }
        if (hasSeen) {
            startMainActivity()
            return
        }

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
                        onContinue = {
                            runBlocking { dataStoreManager.saveHasSeenWelcome(true) }
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
}

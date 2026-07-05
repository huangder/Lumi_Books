package com.huangder.lumibooks.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 设置页 — 独立 Activity
 *
 * 过渡动画：不调用 overrideActivityTransition / overridePendingTransition，
 * 由系统使用 OEM 默认动画（小米 HyperOS、OPPO ColorOS、vivo OriginOS、
 * 荣耀 MagicOS 各自的原生过渡），Pixel 走 Android 原生 CrossActivityAnim。
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            EBookReaderTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
    // finish() 不再覆写 — 系统默认返回动画由 OEM 自行控制
}

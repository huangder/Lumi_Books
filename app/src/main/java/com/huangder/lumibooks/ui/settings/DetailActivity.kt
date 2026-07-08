package com.huangder.lumibooks.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 设置二级页面 — 统一 Activity
 *
 * 根据 intent extra "category" 渲染不同内容：
 * reading / display / goal / storage / about
 *
 * 过渡动画：继承系统默认（各 OEM 原生动画），不自定义。
 */
@AndroidEntryPoint
class DetailActivity : ComponentActivity() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val category = intent.getStringExtra("category") ?: "about"

        // 同步读取避免闪白：手动深色模式下第一个 frame 就渲染深色
        val darkMode = runBlocking { dataStoreManager.darkMode.first() }
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (darkMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemDark
        }

        setContent {
            EBookReaderTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    val onBack = { finish() }

                    when (category) {
                        "reading" -> DetailPage("阅读设置", onBack) { ReadingSettingsDetail(viewModel) }
                        "display" -> DetailPage("显示与外观", onBack) { DisplayDetail(viewModel) }
                        "goal" -> DetailPage("阅读目标", onBack) { ReadingGoalDetail(viewModel) }
                        "storage" -> DetailPage("存储管理", onBack) { StorageDetail(viewModel) }
                        "backup" -> DetailPage("备份与恢复", onBack) { BackupRestoreDetail(viewModel) }
                        "changelog" -> DetailPage("更新日志", onBack) { ChangelogDetail() }
                        else -> DetailPage("关于应用", onBack) { AboutDetail(viewModel) }
                    }
                }
            }
        }
    }
}

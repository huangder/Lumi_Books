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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
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

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val category = intent.getStringExtra("category") ?: "about"

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
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    val onBack = { finish() }

                    when (category) {
                        "reading" -> DetailPage(stringResource(R.string.title_reading_settings), onBack) { ReadingSettingsDetail(viewModel) }
                        "display" -> DetailPage(stringResource(R.string.title_display), onBack) { DisplayDetail(viewModel) }
                        "language" -> DetailPage(stringResource(R.string.title_language), onBack) { LanguageDetailScreen(viewModel) }
                        "goal" -> DetailPage(stringResource(R.string.title_reading_goal), onBack) { ReadingGoalDetail(viewModel) }
                        "storage" -> DetailPage(stringResource(R.string.title_storage), onBack) { StorageDetail(viewModel) }
                        "backup" -> DetailPage(stringResource(R.string.title_backup), onBack) { BackupRestoreDetail(viewModel) }
                        "changelog" -> DetailPage(stringResource(R.string.title_changelog), onBack) { ChangelogDetail() }
                        else -> DetailPage(stringResource(R.string.title_about), onBack) { AboutDetail(viewModel) }
                    }
                }
            }
        }
    }
}

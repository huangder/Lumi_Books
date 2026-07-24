package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 设置二级页面 — 统一 Activity
 *
 * 根据 intent extra "category" 渲染不同内容：
 * reading / display / goal / storage / third_party_services / mineru / about
 *
 * 过渡动画：继承系统默认（各 OEM 原生动画），不自定义。
 */
@AndroidEntryPoint
class DetailActivity : ComponentActivity() {

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

        val category = intent.getStringExtra("category") ?: "about"
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
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = initialAppTheme)
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = initialTransparency)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = initialHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = initialDarkMode)
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
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    val onBack = { finish() }

                    when (category) {
                        "reading" -> DetailPage(stringResource(R.string.title_reading_settings), onBack) { ReadingSettingsDetail(viewModel) }
                        "display" -> DetailPage(stringResource(R.string.title_display), onBack) { DisplayDetail(viewModel) }
                        "language" -> DetailPage(stringResource(R.string.title_language), onBack) { LanguageDetailScreen(viewModel) }
                        "goal" -> DetailPage(stringResource(R.string.title_reading_goal), onBack) { ReadingGoalDetail(viewModel) }
                        "storage" -> DetailPage(stringResource(R.string.title_storage), onBack) { StorageDetail(viewModel) }
                        "backup" -> DetailPage(stringResource(R.string.title_backup), onBack) { BackupRestoreDetail(viewModel) }
                        "third_party_services" -> DetailPage(stringResource(R.string.title_third_party_services), onBack) {
                            ThirdPartyServicesDetail(viewModel)
                        }
                        "mineru" -> DetailPage(stringResource(R.string.title_mineru), onBack) { MineruSettingsDetail(viewModel) }
                        "external_tts" -> DetailPage(stringResource(R.string.title_external_tts), onBack) {
                            ExternalTtsSettingsDetail(viewModel) {
                                startActivity(
                                    Intent(this@DetailActivity, DetailActivity::class.java)
                                        .putExtra("category", "external_tts_config")
                                )
                            }
                        }
                        "external_tts_config" -> DetailPage(stringResource(R.string.title_external_tts_configuration), onBack) {
                            ExternalTtsConfigurationDetail(viewModel, onSaved = onBack)
                        }
                        "changelog" -> DetailPage(stringResource(R.string.title_changelog), onBack) { ChangelogDetail() }
                        else -> DetailPage(stringResource(R.string.title_about), onBack) { AboutDetail(viewModel) }
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
}

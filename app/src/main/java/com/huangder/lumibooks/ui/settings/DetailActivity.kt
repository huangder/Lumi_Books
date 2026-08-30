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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
        val launchTheme = LaunchThemeController.themeSnapshot(this)

        setContent {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = launchTheme.predictiveBackEnabled)
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = launchTheme.appTheme)
            val appAccentColor by dataStoreManager.appAccentColor.collectAsState(initial = launchTheme.appAccentColor)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = launchTheme.globalFontMode)
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = launchTheme.liquidGlassTransparency)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = launchTheme.liquidGlassHdrHighlightEnabled)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = launchTheme.darkMode)
            val motionPreferenceValue by dataStoreManager.motionPreference.collectAsState(initial = launchTheme.motionPreference)
            val liquidGlassCapability = rememberLiquidGlassCapability(view = LocalView.current)
            val effectiveAppTheme = effectiveAppTheme(appTheme, liquidGlassCapability)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = effectiveAppTheme == "material3",
                appTheme = effectiveAppTheme,
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                globalFontMode = globalFontMode,
                motionPreference = MotionPreference.fromStoredValue(motionPreferenceValue)
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    val onBack = { finish() }

                    when (category) {
                        "reading" -> DetailPage(stringResource(R.string.title_reading_settings), onBack) { ReadingSettingsDetail(viewModel) }
                        "floating_subtitle" -> DetailPage(stringResource(R.string.title_floating_subtitle_settings), onBack) {
                            FloatingSubtitleSettingsDetail(viewModel)
                        }
                        "display" -> DetailPage(stringResource(R.string.title_display), onBack) { DisplayDetail(viewModel) }
                        "icon_style" -> DetailPage(stringResource(R.string.icon_style_title), onBack) { AppIconStyleDetail(viewModel) }
                        "language" -> DetailPage(stringResource(R.string.title_language), onBack) { LanguageDetailScreen(viewModel) }
                        "goal" -> DetailPage(stringResource(R.string.title_reading_goal), onBack) { ReadingGoalDetail(viewModel) }
                        "storage" -> DetailPage(
                            title = stringResource(R.string.title_storage),
                            onBack = onBack
                        ) {
                            StorageDetail(
                                viewModel = viewModel,
                                onOpenBooks = {
                                    startActivity(
                                        Intent(this@DetailActivity, DetailActivity::class.java)
                                            .putExtra("category", "storage_books")
                                    )
                                }
                            )
                        }
                        "storage_books" -> DetailPage(stringResource(R.string.storage_books), onBack) {
                            StorageBooksDetail(viewModel)
                        }
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
                        "webdav" -> DetailPage(stringResource(R.string.title_webdav), onBack) {
                            WebdavSettingsDetail(viewModel) {
                                startActivity(
                                    Intent(this@DetailActivity, DetailActivity::class.java)
                                        .putExtra("category", "webdav_config")
                                )
                            }
                        }
                        "webdav_config" -> DetailPage(stringResource(R.string.title_webdav_configuration), onBack) {
                            WebdavConfigurationDetail(viewModel, onSaved = onBack)
                        }
                        "changelog" -> DetailPage(stringResource(R.string.title_changelog), onBack) { ChangelogDetail() }
                        "highlight_color" -> DetailPage(stringResource(R.string.highlight_color_palette), onBack) {
                            HighlightColorDetail(viewModel)
                        }
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

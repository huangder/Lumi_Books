package com.huangder.lumibooks.ui.home

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 授权文件夹书籍页 —— 独立 Activity。
 *
 * 过渡动画：与设置页一致，不调用 overrideActivityTransition / overridePendingTransition，
 * 交给 OEM 的系统转场（HyperOS、ColorOS、OriginOS、MagicOS 各自原生动画）。
 *
 * 玻璃折射：这里刻意不提供窗口级 Backdrop（LiquidGlassDialogHost 不传 backdrop），
 * 页面顶部的返回/刷新等玻璃控件只使用可读的材质回退，不会折射本页自身内容；
 * 只有浮在滚动列表之上的“确认并导入”按钮使用列表捕获层，见 5.2 Backdrop 所有权。
 */
@AndroidEntryPoint
class AuthorizedFolderBooksActivity : ComponentActivity() {
    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val appAccentColor by dataStoreManager.appAccentColor
                .collectAsState(initial = DEFAULT_APP_ACCENT_HEX)
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency
                .collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled
                .collectAsState(initial = false)
            val cardOutlinesEnabled by dataStoreManager.cardOutlinesEnabled
                .collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val motionPreferenceValue by dataStoreManager.motionPreference
                .collectAsState(initial = "standard")
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled
                .collectAsState(initial = true)
            val eInkMode by dataStoreManager.eInkModeEnabled.collectAsState(initial = false)
            val isDark = if (eInkMode) {
                false
            } else {
                when (darkMode) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDarkMode
                }
            }
            val capability = rememberLiquidGlassCapability(eInkMode, LocalView.current)
            val effectiveTheme = effectiveAppTheme(appTheme, capability)

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = effectiveTheme == "material3",
                appTheme = effectiveTheme,
                appAccentColor = appAccentColor,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkMode,
                cardOutlinesEnabled = cardOutlinesEnabled,
                eInkMode = eInkMode,
                globalFontMode = globalFontMode,
                motionPreference = MotionPreference.fromStoredValue(motionPreferenceValue)
            ) {
                // One page backdrop, captured from the scrollable book list inside the route.
                // Anchored menus and dialogs refract that capture; the page's own controls keep the
                // material fallback so nothing samples a layer that contains itself — a
                // self-referencing RenderNode tree crashes HWUI (see the design spec, 5.2).
                val pageBackdrop = rememberLayerBackdrop()
                val glassBackdrop = pageBackdrop.takeIf {
                    effectiveTheme == "liquid_glass" && !eInkMode
                }
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.WindowBg) {
                    // Anchored glass menus (sort) need a host; without one the sort button just
                    // toggled its state and nothing was ever drawn.
                    LiquidGlassMenuHost(
                        modifier = Modifier.fillMaxSize(),
                        backdrop = glassBackdrop
                    ) {
                        LiquidGlassDialogHost(
                            modifier = Modifier.fillMaxSize(),
                            backdrop = glassBackdrop
                        ) {
                            CompositionLocalProvider(LocalLiquidGlassBackdrop provides null) {
                                AuthorizedFolderBooksRoute(
                                    predictiveBackEnabled = predictiveBackEnabled && !eInkMode,
                                    contentBackdrop = glassBackdrop,
                                    onExit = { finish() },
                                    onConfirmSelection = { uris ->
                                        setResult(
                                            RESULT_OK,
                                            Intent().putStringArrayListExtra(
                                                EXTRA_SELECTED_URIS,
                                                ArrayList(uris)
                                            )
                                        )
                                        finish()
                                    }
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

    private fun Configuration.isNightModeEnabled(): Boolean =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    companion object {
        private const val EXTRA_SELECTED_URIS = "authorized_folder_books_selected_uris"

        fun createIntent(context: Context): Intent =
            Intent(context, AuthorizedFolderBooksActivity::class.java)

        /** Book URIs the user confirmed; empty when the page was closed without selecting. */
        fun selectedUrisFrom(data: Intent?): Set<String> =
            data?.getStringArrayListExtra(EXTRA_SELECTED_URIS)?.toSet().orEmpty()
    }
}

package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * TXT 文件编辑页 — 独立 Activity
 *
 * backdrop 链设置完全复制 SettingsActivity：
 *   1. rememberLayerBackdrop() 在 EBookReaderTheme 内创建
 *   2. LiquidGlassDialogHost(backdrop = ...) 使弹窗拿到真实折射
 *   3. Box.layerBackdrop(...) 捕获内容像素
 *   4. ProvideLiquidGlassBackdrop(null) 阻断内容内部的 LiquidGlassSurface
 *      访问 backdrop，防止 MIUI MiBackgroundBlurBlend 递归崩溃
 */
@AndroidEntryPoint
class TxtEditorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_CHAPTER_INDEX = "chapterIndex"
        const val EXTRA_CHAR_OFFSET = "charOffset"
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    private var systemDarkMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            val isLiquidGlass = appTheme == "liquid_glass"

            EBookReaderTheme(
                darkTheme = isDark,
                appTheme = appTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled
            ) {
                val mainBackdrop = rememberLayerBackdrop()
                LiquidGlassDialogHost(
                    backdrop = mainBackdrop.takeIf { isLiquidGlass }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (isLiquidGlass) Modifier.layerBackdrop(mainBackdrop)
                                else Modifier
                            )
                    ) {
                        // ProvideLiquidGlassBackdrop(null) 是防崩溃关键：
                        // 阻断 TxtEditorScreen 内的 LiquidGlassSurface（按钮等）
                        // 访问外层 backdrop，避免"采样→重渲→再采样"递归
                        ProvideLiquidGlassBackdrop(null) {
                            TxtEditorScreen(
                                onNavigateBack = { saved ->
                                    if (saved) setResult(Activity.RESULT_OK)
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}

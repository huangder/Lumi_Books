package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * TXT 文件编辑页 — 独立 Activity。
 *
 * 编辑器包含平台 EditText。不要把它放进 layerBackdrop 捕获层：部分小米 Android 16
 * 设备会因此在 RenderThread 构造循环变换图，最终在 HWUI 中递归栈溢出。
 */
@AndroidEntryPoint
class TxtEditorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_CHAPTER_INDEX = "chapterIndex"
        const val EXTRA_CHAR_OFFSET = "charOffset"
        const val EXTRA_REVEAL_READING_POSITION = "revealReadingPosition"
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
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "default")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            EBookReaderTheme(
                darkTheme = isDark,
                appTheme = appTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled,
                globalFontMode = globalFontMode
            ) {
                val editorBackdrop = rememberLayerBackdrop()
                val activeEditorBackdrop = editorBackdrop.takeIf { appTheme == "liquid_glass" }
                Box(Modifier.fillMaxSize()) {
                    // Capture only a Compose underlay. Capturing the native EditText itself can
                    // create a recursive HWUI render graph on some Xiaomi Android 16 devices.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(AppColors.WindowBg)
                            .then(
                                activeEditorBackdrop?.let { Modifier.layerBackdrop(it) }
                                    ?: Modifier
                            )
                    )
                    LiquidGlassDialogHost(
                        modifier = Modifier.fillMaxSize(),
                        backdrop = activeEditorBackdrop
                    ) {
                        ProvideLiquidGlassBackdrop(activeEditorBackdrop) {
                            TxtEditorScreen(
                                backdrop = activeEditorBackdrop,
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

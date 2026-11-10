package com.huangder.lumibooks.ui.settings
import androidx.compose.ui.graphics.toArgb
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.icons.AppIcons

import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import android.view.MotionEvent
import com.huangder.lumibooks.ui.components.CollapsingPageScaffold
import com.huangder.lumibooks.ui.components.rememberCollapsingPageState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.fangSongFamily
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.huangder.lumibooks.ui.animation.finishWithPush

/**
 * 通用 WebView 页面 — 加载 assets/html/ 下的本地 HTML
 *
 * intent extra:
 *   "title"  — 页面标题
 *   "file"   — assets 文件名（如 "privacy.html"）
 *
 * 过渡动画继承系统默认。
 */
@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("title") ?: ""
        val file = intent.getStringExtra("file") ?: "privacy.html"

        val launchTheme = LaunchThemeController.themeSnapshot(this)
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (launchTheme.darkMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemDark
        }

        setContent {
            val appFontSize by dataStoreManager.appFontSize.collectAsState(initial = launchTheme.appFontSize)
            val capability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(launchTheme.appTheme, capability)
            val menuAnimationStyleValue by dataStoreManager.menuAnimationStyle.collectAsState(initial = launchTheme.menuAnimationStyle)
            val liquidGlassStyle by dataStoreManager.liquidGlassStyle
                .collectAsState(initial = launchTheme.liquidGlassStyle)
            val liquidGlassSamplingQuality by dataStoreManager.liquidGlassSamplingQuality
                .collectAsState(initial = launchTheme.liquidGlassSamplingQuality)
            val reduceTransparencyEnabled by dataStoreManager.reduceTransparencyEnabled
                .collectAsState(initial = launchTheme.reduceTransparencyEnabled)
            EBookReaderTheme(
                liquidGlassStyle = liquidGlassStyle,
                liquidGlassSamplingQuality = liquidGlassSamplingQuality,
                reduceTransparencyEnabled = reduceTransparencyEnabled,
                menuAnimationStyle = com.huangder.lumibooks.domain.model.MenuAnimationStyle.fromStoredValue(menuAnimationStyleValue),
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                appAccentColor = launchTheme.appAccentColor,
                liquidGlassTransparency = launchTheme.liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = launchTheme.liquidGlassHdrHighlightEnabled,
                appFontSize = appFontSize,
                globalFontMode = launchTheme.globalFontMode
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = launchTheme.predictiveBackEnabled,
                    onBack = { finishWithPush() }
                )
                com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
                    modifier = Modifier.fillMaxSize()
                ) {
                    WebViewPage(title = title, assetFile = file, isDark = isDark, onBack = { finishWithPush() })
                }
            }
        }
    }
}

@Composable
private fun WebViewPage(title: String, assetFile: String, isDark: Boolean, onBack: () -> Unit) {
    // Same colour as the page behind it, so there is no seam while the document loads.
    val bgColor = AppColors.WindowBg.toArgb()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        val pageState = rememberCollapsingPageState()
        CollapsingPageScaffold(title = title, onBack = onBack, state = pageState) {
            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(bgColor)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                if (isDark) {
                                    view.evaluateJavascript("""
                                        (function(){
                                            var s=document.createElement('style');
                                            s.textContent=':root{--bg:#000;--card:#1C1C1E;--text:#fff;--text2:#98989D;--line:#38383A}';
                                            document.head.appendChild(s);
                                        })();
                                    """.trimIndent(), null)
                                }
                            }
                        }
                        // Observe the native gesture without swallowing links, selection or WebView history.
                        var previousY = 0f
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> previousY = event.rawY
                                MotionEvent.ACTION_MOVE -> {
                                    val delta = event.rawY - previousY
                                    if (delta < 0f || view.scrollY == 0) pageState.consumeDelta(delta)
                                    previousY = event.rawY
                                }
                            }
                            false
                        }
                        settings.javaScriptEnabled = true
                        settings.defaultTextEncodingName = "UTF-8"

                        // Decode the asset ourselves before passing it to WebView. This prevents
                        // stale file:// cache entries or device-specific charset sniffing from
                        // turning CJK policy text into question marks after an app update.
                        runCatching {
                            context.assets
                                .open("html/$assetFile")
                                .bufferedReader(Charsets.UTF_8)
                                .use { it.readText() }
                        }.onSuccess { html ->
                            loadDataWithBaseURL(
                                "file:///android_asset/html/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }.onFailure {
                            loadUrl("file:///android_asset/html/$assetFile")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

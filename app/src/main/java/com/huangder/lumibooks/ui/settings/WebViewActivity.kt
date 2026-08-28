package com.huangder.lumibooks.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
            val capability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(launchTheme.appTheme, capability)
            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                appAccentColor = launchTheme.appAccentColor,
                liquidGlassTransparency = launchTheme.liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = launchTheme.liquidGlassHdrHighlightEnabled,
                globalFontMode = launchTheme.globalFontMode
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = launchTheme.predictiveBackEnabled,
                    onBack = { finish() }
                )
                com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
                    modifier = Modifier.fillMaxSize()
                ) {
                    WebViewPage(title = title, assetFile = file, isDark = isDark, onBack = { finish() })
                }
            }
        }
    }
}

@Composable
private fun WebViewPage(title: String, assetFile: String, isDark: Boolean, onBack: () -> Unit) {
    val bgColor = if (isDark) 0xFF000000.toInt() else 0xFFFBFBFC.toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    settingsBackButton = true
                )
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = resolveAppFontFamily(fangSongFamily()), color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

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

package com.huangder.lumibooks.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.huangder.lumibooks.ui.components.CollapsingPageScaffold
import com.huangder.lumibooks.ui.components.rememberCollapsingPageState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
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
 * 使用应用的页面过渡动画。
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        val pageState = rememberCollapsingPageState()
        CollapsingPageScaffold(title = title, onBack = onBack, state = pageState) {
            BundledDocumentView(
                assetFile = assetFile,
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
                onScroll = { pageState.consumeDelta(it) }
            )
        }
    }
}

@Composable
internal fun BundledDocumentView(
    assetFile: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onScroll: ((Float) -> Unit)? = null
) {
    val bgColor = AppColors.WindowBg.toArgb()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                val assetBase = "file:///android_asset/html/"
                fun loadDocument(file: String) {
                    val url = assetBase + file
                    // Decode the asset explicitly so WebView does not sniff the CJK text encoding.
                    runCatching {
                        context.assets.open("html/$file")
                            .bufferedReader(Charsets.UTF_8)
                            .use { it.readText() }
                    }.onSuccess { html ->
                        loadDataWithBaseURL(
                            url,
                            themeBundledDocument(html, isDark, bgColor),
                            "text/html",
                            "UTF-8",
                            url
                        )
                    }.onFailure {
                        loadUrl(url)
                    }
                }
                setBackgroundColor(bgColor)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val file = when (request.url.toString()) {
                            assetBase + "privacy.html" -> "privacy.html"
                            assetBase + "terms.html" -> "terms.html"
                            else -> return false
                        }
                        loadDocument(file)
                        return true
                    }
                }
                if (onScroll != null) {
                    var previousY = 0f
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> previousY = event.rawY
                            MotionEvent.ACTION_MOVE -> {
                                val delta = event.rawY - previousY
                                if (delta < 0f || view.scrollY == 0) onScroll(delta)
                                previousY = event.rawY
                            }
                        }
                        false
                    }
                }
                settings.javaScriptEnabled = true
                settings.defaultTextEncodingName = "UTF-8"

                loadDocument(assetFile)
            }
        },
        modifier = modifier,
        onRelease = { it.destroy() }
    )
}

internal fun themeBundledDocument(html: String, isDark: Boolean, backgroundColor: Int): String {
    // Resolve the bundled palette before the first paint using the app theme.
    val darkMedia = if (isDark) "all" else "not all"
    val colorScheme = if (isDark) "dark" else "light"
    val background = (backgroundColor and 0xFFFFFF).toString(16).padStart(6, '0')
    return html
        .replace("@media (prefers-color-scheme: dark)", "@media $darkMedia")
        .replace(
            "</head>",
            "<style>:root { color-scheme: $colorScheme; --bg: #$background; }</style>\n</head>"
        )
}

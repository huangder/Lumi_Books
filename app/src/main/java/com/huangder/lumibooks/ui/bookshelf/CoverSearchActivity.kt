package com.huangder.lumibooks.ui.bookshelf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.util.LocaleHelper
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 封面网络搜索页：内嵌轻量浏览器（Bing、百度、Google 图片搜索），
 * 「选取封面」开关 3:4 固定比例裁剪框，勾选确认后以 PixelCopy 截取屏幕区域保存为自定义封面。
 *
 * intent extra:
 *   "bookId" — 目标书籍 ID（保存封面用）
 *   "title"  — 书名（用于预填搜索词）
 */
@AndroidEntryPoint
class CoverSearchActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, bookId: String, title: String) {
            context.startActivity(
                Intent(context, CoverSearchActivity::class.java)
                    .putExtra(EXTRA_BOOK_ID, bookId)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        val bookTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (bookId == null) {
            finish()
            return
        }

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
                LiquidGlassDialogHost(modifier = Modifier.fillMaxSize()) {
                    CoverSearchScreen(
                        bookId = bookId,
                        bookTitle = bookTitle,
                        onExit = { finish() }
                    )
                }
            }
        }
    }
}

/** 封面宽高比（宽/高），与应用书架封面卡片一致 */
private const val COVER_ASPECT_RATIO = 0.75f

@Composable
private fun CoverSearchScreen(
    bookId: String,
    bookTitle: String,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val isDark = LocalIsDarkTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val viewModel: CoverSearchViewModel = hiltViewModel()

    val books by viewModel.books.collectAsState()
    val book = remember(books, bookId) { books.firstOrNull { it.id == bookId } }

    val captureFailedText = stringResource(R.string.cover_capture_failed)
    val saveFailedText = stringResource(R.string.cover_save_failed)
    val querySuffix = stringResource(R.string.cover_search_query_suffix)

    var cropMode by remember { mutableStateOf(false) }
    var cropFrame by remember { mutableStateOf<Rect?>(null) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var areaOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var pageProgress by remember { mutableStateOf(100) }
    var isLoading by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf(bookTitle) }
    var selectedEngine by remember { mutableStateOf(CoverSearchEngine.BING) }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(if (isDark) 0xFF000000.toInt() else 0xFFFBFBFC.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.defaultTextEncodingName = "UTF-8"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // 仅放行 http(s)，其余（mailto: 等）交给系统
                    val scheme = request.url.scheme
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    isLoading = false
                    CoverSearchEngine.fromUrl(url)?.let { engine ->
                        selectedEngine = engine
                        engine.queryFromUrl(url)?.let { query -> queryText = query }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    pageProgress = newProgress
                }
            }
            loadUrl(selectedEngine.buildImageSearchUrl("$bookTitle $querySuffix"))
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    // 进入裁剪模式时居中初始化 3:4 框；退出时等淡出动画结束后清空（页面可能已滚动）
    LaunchedEffect(cropMode, containerSize) {
        if (!cropMode) {
            delay(200)
            if (!cropMode) cropFrame = null
        } else if (containerSize != Size.Zero && cropFrame == null) {
            var width = containerSize.height * 0.62f * COVER_ASPECT_RATIO
            if (width > containerSize.width) width = containerSize.width
            val height = width / COVER_ASPECT_RATIO
            cropFrame = Rect(
                left = (containerSize.width - width) / 2f,
                top = (containerSize.height - height) / 2f,
                right = (containerSize.width + width) / 2f,
                bottom = (containerSize.height + height) / 2f
            )
        }
    }

    // 保存结果 → 成功打勾反馈后退出
    LaunchedEffect(Unit) {
        viewModel.saveResult.collect { result ->
            when (result) {
                CoverSearchViewModel.SaveResult.Success -> {
                    capturing = false
                    showSuccess = true
                    delay(500)
                    onExit()
                }
                is CoverSearchViewModel.SaveResult.Error -> {
                    capturing = false
                    val detail = result.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    Toast.makeText(context, saveFailedText + detail, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val confirmCrop = confirm@{
        if (capturing || showSuccess) return@confirm
        val currentFrame = cropFrame ?: return@confirm
        val currentBook = book
        val activity = context.findActivity() ?: return@confirm
        if (currentBook == null) {
            Toast.makeText(context, saveFailedText, Toast.LENGTH_SHORT).show()
            return@confirm
        }
        capturing = true
        scope.launch {
            // 等待裁剪装饰与按钮隐藏后的下一帧，保证截取区域纯净
            withFrameNanos { }
            withFrameNanos { }
            val bitmap = suspendCancellableCoroutine { continuation ->
                captureWindowRegion(
                    activity = activity,
                    rootView = rootView,
                    areaOriginInRoot = areaOriginInRoot,
                    frame = currentFrame
                ) { captured ->
                    if (continuation.isActive) continuation.resumeWith(Result.success(captured))
                }
            }
            if (bitmap == null) {
                capturing = false
                Toast.makeText(context, captureFailedText, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateCustomCoverFromBitmap(currentBook, bitmap)
            }
        }
    }

    // 浏览器页始终拦截返回键：优先退出裁剪模式，其次返回网页上一页，无历史时才退出
    // （不走 ConfigurableActivityBack——预测性返回开启时它不注册 BackHandler，会把页面直接关掉）
    BackHandler {
        when {
            cropMode -> cropMode = false
            webView.canGoBack() -> webView.goBack()
            else -> onExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CoverSearchTopBar(
                query = queryText,
                onQueryChange = { queryText = it },
                onSearch = {
                    keyboardController?.hide()
                    val trimmed = queryText.trim()
                    if (trimmed.isNotEmpty()) webView.loadUrl(selectedEngine.buildImageSearchUrl(trimmed))
                },
                onBack = onExit,
                onReload = { webView.reload() }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.md - AppSpace.xs)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.BgGray)
                    .border(
                        width = 1.dp,
                        color = AppColors.Divider.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .onGloballyPositioned { coordinates ->
                        containerSize = Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                        areaOriginInRoot = coordinates.positionInRoot()
                    }
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading && !capturing) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = AppColors.Accent,
                        trackColor = Color.Transparent
                    )
                }

                CoverCropOverlay(
                    visible = cropMode,
                    frame = cropFrame,
                    containerSize = containerSize,
                    onFrameChange = { cropFrame = it },
                    decorationsHidden = capturing,
                    modifier = Modifier.fillMaxSize()
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = showSuccess,
                    enter = scaleIn(
                        initialScale = 0.6f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
                    ) + fadeIn(tween(120)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(AppColors.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = AppColors.OnAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            CoverSearchBottomBar(
                selectedEngine = selectedEngine,
                cropMode = cropMode,
                showConfirm = cropMode && !showSuccess,
                hidden = capturing,
                onEngineSelected = { engine ->
                    selectedEngine = engine
                    keyboardController?.hide()
                    queryText.trim().takeIf { it.isNotEmpty() }?.let { query ->
                        webView.loadUrl(engine.buildImageSearchUrl(query))
                    }
                },
                onToggleCrop = {
                    keyboardController?.hide()
                    cropMode = !cropMode
                },
                onConfirmCrop = confirmCrop
            )
        }
    }
}

@Composable
private fun CoverSearchBottomBar(
    selectedEngine: CoverSearchEngine,
    cropMode: Boolean,
    showConfirm: Boolean,
    hidden: Boolean,
    onEngineSelected: (CoverSearchEngine) -> Unit,
    onToggleCrop: () -> Unit,
    onConfirmCrop: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = AppSpace.md - AppSpace.xs, vertical = AppSpace.sm + AppSpace.xs)
            .graphicsLayer { alpha = if (hidden) 0f else 1f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverSearchEngine.entries.forEach { engine ->
                val selected = engine == selectedEngine
                val label = when (engine) {
                    CoverSearchEngine.BING -> stringResource(R.string.cover_search_engine_bing)
                    CoverSearchEngine.BAIDU -> stringResource(R.string.cover_search_engine_baidu)
                    CoverSearchEngine.GOOGLE -> stringResource(R.string.cover_search_engine_google)
                }
                val shape = RoundedCornerShape(50)
                LiquidGlassSurface(
                    shape = shape,
                    fallbackColor = if (selected) AppColors.Accent else AppColors.CardBg,
                    contentScrimColor = if (isLiquidGlass) {
                        if (selected) {
                            AppColors.Accent.copy(alpha = 0.86f)
                        } else {
                            AppColors.CardBg.copy(alpha = 0.82f)
                        }
                    } else {
                        Color.Transparent
                    },
                    transparencyOverride = 0.28f.takeIf { isLiquidGlass },
                    outlineWidth = if (isLiquidGlass) 0.55.dp else 0.dp,
                    highlightAlpha = if (isLiquidGlass) 0.12f else 0f,
                    onClick = { onEngineSelected(engine) },
                    decorationModifier = if (isLiquidGlass) {
                        Modifier
                    } else {
                        Modifier
                            .shadow(
                                elevation = 5.dp,
                                shape = shape,
                                ambientColor = Color.Black.copy(alpha = 0.08f),
                                spotColor = Color.Black.copy(alpha = 0.12f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) Color.Transparent else AppColors.Divider.copy(alpha = 0.55f),
                                shape = shape
                            )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics { this.selected = selected }
                ) {
                    Text(
                        text = label,
                        color = if (selected) AppColors.OnAccent else AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showConfirm,
            enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(220)) + scaleIn(
                initialScale = 0.7f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)
            ) + fadeIn(tween(140)),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(190)) +
                scaleOut(targetScale = 0.8f, animationSpec = tween(160)) + fadeOut(tween(120))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(AppSpace.sm))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AppColors.Accent)
                        .clickable(onClick = onConfirmCrop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.confirm),
                        tint = AppColors.OnAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.size(AppSpace.sm))
        val cropShape = CircleShape
        LiquidGlassSurface(
            shape = cropShape,
            fallbackColor = if (cropMode) AppColors.Accent else AppColors.CardBg,
            contentScrimColor = if (isLiquidGlass) {
                if (cropMode) {
                    AppColors.Accent.copy(alpha = 0.86f)
                } else {
                    AppColors.CardBg.copy(alpha = 0.82f)
                }
            } else {
                Color.Transparent
            },
            transparencyOverride = 0.28f.takeIf { isLiquidGlass },
            outlineWidth = if (isLiquidGlass) 0.55.dp else 0.dp,
            highlightAlpha = if (isLiquidGlass) 0.12f else 0f,
            onClick = onToggleCrop,
            decorationModifier = if (isLiquidGlass) {
                Modifier
            } else {
                Modifier
                    .shadow(
                        elevation = 5.dp,
                        shape = cropShape,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (cropMode) Color.Transparent else AppColors.Divider.copy(alpha = 0.55f),
                        shape = cropShape
                    )
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CropFree,
                contentDescription = stringResource(R.string.cover_pick_region),
                tint = if (cropMode) AppColors.OnAccent else AppColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CoverSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    val fieldContent: @Composable RowScope.() -> Unit = {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(AppSpace.sm + AppSpace.xs))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = AppColors.TextPrimary,
                fontSize = AppType.BodySmall
            ),
            cursorBrush = SolidColor(AppColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md - AppSpace.xs, vertical = AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassIconButton(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            settingsBackButton = true
        )
        Spacer(Modifier.size(AppSpace.md - AppSpace.xs))

        if (isLiquidGlass) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(50),
                fallbackColor = AppColors.CardBg,
                contentScrimColor = AppColors.CardBg.copy(alpha = 0.42f),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = AppSpace.md, vertical = 12.dp)
                        .heightIn(min = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fieldContent()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.BgGray.copy(alpha = 0.65f))
                    .padding(horizontal = AppSpace.md, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                fieldContent()
            }
        }
        Spacer(Modifier.size(AppSpace.md - AppSpace.xs))
        LiquidGlassIconButton(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = stringResource(R.string.reload_page),
            onClick = onReload
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

/**
 * 以窗口坐标截取裁剪框区域（所见即所得）
 * 坐标映射：compose 根视图在窗口中的位置 + 网页区域在根中的位置 + 框在网页区域中的偏移
 */
private fun captureWindowRegion(
    activity: Activity,
    rootView: View,
    areaOriginInRoot: Offset,
    frame: Rect,
    onResult: (Bitmap?) -> Unit
) {
    val viewLocation = IntArray(2)
    rootView.getLocationInWindow(viewLocation)
    val areaX = viewLocation[0] + areaOriginInRoot.x
    val areaY = viewLocation[1] + areaOriginInRoot.y

    val left = (areaX + frame.left).roundToInt().coerceIn(0, rootView.width - 1)
    val top = (areaY + frame.top).roundToInt().coerceIn(0, rootView.height - 1)
    val right = (areaX + frame.right).roundToInt().coerceIn(left + 1, rootView.width)
    val bottom = (areaY + frame.bottom).roundToInt().coerceIn(top + 1, rootView.height)
    val srcRect = android.graphics.Rect(left, top, right, bottom)

    val bitmap = Bitmap.createBitmap(srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            activity.window,
            srcRect,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    onResult(bitmap)
                } else {
                    bitmap.recycle()
                    onResult(null)
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (error: Exception) {
        bitmap.recycle()
        onResult(null)
    }
}

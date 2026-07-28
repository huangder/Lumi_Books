package com.huangder.lumibooks

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.ActionMode
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.ui.navigation.MainNavGraph
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.ui.splash.SplashScreen
import com.huangder.lumibooks.ui.components.AppUpdateDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.PolicyUpdateDialog
import com.huangder.lumibooks.ui.components.RemoteNoticeDialog
import com.huangder.lumibooks.ui.settings.WebViewActivity
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.UpdateChecker
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 启动时更新检查的待处理结果（用于弹出 Dialog） */
private data class PendingPolicyUpdate(
    val hasTermsUpdate: Boolean,
    val termsVersion: Int,
    val hasPrivacyUpdate: Boolean,
    val privacyVersion: Int
)


private data class PendingAppUpdate(
    val appVersion: String,
    val latestVersionCode: Long,
    val title: String,
    val message: String,
    val changelog: String,
    val releaseUrl: String,
    val force: Boolean
)

private data class PendingRemoteNotice(
    val id: String,
    val title: String,
    val message: String
)

enum class ReaderPageDirection {
    PREVIOUS,
    NEXT
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_BOOK_ID = "open_book_id"
    }

    private var systemDarkMode by mutableStateOf(false)
    private var requestedOpenBookId by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var ttsController: TtsController

    /**
     * 当 ReaderScreen 处于前台时置为 true，
     * 确保 ActionMode 拦截只在阅读页生效，不影响其他页面。
     */
    var isInReaderScreen = false

    /** 非空时由当前阅读页接管音量键；阅读页离开或设置关闭时恢复系统音量行为。 */
    var readerVolumeKeyHandler: ((ReaderPageDirection) -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = readerVolumeKeyHandler
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> ReaderPageDirection.PREVIOUS
            KeyEvent.KEYCODE_VOLUME_DOWN -> ReaderPageDirection.NEXT
            else -> null
        }
        if (handler != null && direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                handler(direction)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // Startup dialog pending states.
    /** Pending app update dialog; non-null means show it after startup. */
    private var pendingAppUpdate by mutableStateOf<PendingAppUpdate?>(null)

    /** Pending remote notice dialog; non-null means show it after startup. */
    private var pendingRemoteNotice by mutableStateOf<PendingRemoteNotice?>(null)

    /** Pending terms/privacy policy update dialog; non-null means show it after startup. */
    private var pendingPolicyUpdate by mutableStateOf<PendingPolicyUpdate?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenBookIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleOpenBookIntent(intent: Intent?) {
        requestedOpenBookId = intent?.getStringExtra(EXTRA_OPEN_BOOK_ID)?.takeIf { it.isNotBlank() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    /**
     * 处理外部 App 通过 ACTION_VIEW 传入的文件（PDF/EPUB/TXT）
     * 将文件复制到应用内部存储后写入数据库，首页自动刷新
     */
    private fun handleImportIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            importBookFromUri(uri)
        }
    }

    private suspend fun importBookFromUri(uri: Uri) {
        val fileName = FileUtils.getFileNameFromUri(this, uri) ?: return
        val extension = FileUtils.getFileExtension(fileName)
        if (extension !in listOf("epub", "pdf", "txt")) return

        val file = FileUtils.copyFileToInternal(this, uri, fileName) ?: return
        val format = when (extension) {
            "epub" -> BookFormat.EPUB
            "pdf" -> BookFormat.PDF
            else -> BookFormat.TXT
        }
        val coverPath = try {
            val parser = BookParserFactory.createParser(format, this)
            parser.extractCoverPath(file.absolutePath)
        } catch (_: Exception) { null }

        // 导入时从文件解析真实标题和作者，而不是写死"未知作者"
        val (parsedTitle, parsedAuthor) = if (format == BookFormat.EPUB) {
            try {
                val parser = com.huangder.lumibooks.util.parser.EpubParser(this)
                val content = parser.parse(file.absolutePath)
                val t = content.title.takeIf { it.isNotBlank() && it != file.nameWithoutExtension }
                    ?: fileName.substringBeforeLast('.')
                val a = content.author.takeIf { it.isNotBlank() && it != "未知作者" } ?: "未知作者"
                t to a
            } catch (_: Exception) {
                fileName.substringBeforeLast('.') to "未知作者"
            }
        } else {
            fileName.substringBeforeLast('.') to "未知作者"
        }

        val book = Book(
            id = FileUtils.generateBookId(),
            title = parsedTitle,
            author = parsedAuthor,
            filePath = file.absolutePath,
            coverPath = coverPath,
            format = format,
            lastReadTime = System.currentTimeMillis(),
            readingProgress = 0f,
            createdAt = System.currentTimeMillis()
        )
        bookRepository.insertBook(book)
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "导入成功了喵~(=^‥^=)", Toast.LENGTH_SHORT).show()
        }
        Log.d("MainActivity", "Imported book from intent: ${book.title}")
    }

    /**
     * 不拦截 ActionMode：选区检测由 ReadView 的 SpanWatcher 处理，
     * 系统浮动工具栏通过 menu.clear() 清空菜单项（显示为空气泡）。
     * 不调用 mode.finish()，避免破坏选区手柄状态。
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()

        // 处理外部文件打开（冷启动）
        handleOpenBookIntent(intent)
        handleImportIntent(intent)

        // 启动时自动检查更新（静默执行，有变更时弹窗）
        performStartupUpdateCheck()

        val splashEnabledAtLaunch = if (intent.hasExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED)) {
            intent.getBooleanExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED, false)
        } else {
            kotlinx.coroutines.runBlocking { dataStoreManager.splashEnabled.first() }
        }

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val entranceAnimationsEnabled by dataStoreManager.entranceAnimationsEnabled.collectAsState(initial = true)
            val eInkModeEnabled by dataStoreManager.eInkModeEnabled.collectAsState(initial = false)
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = if (eInkModeEnabled) {
                false
            } else when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            val effectiveAppTheme = if (eInkModeEnabled && appTheme == "liquid_glass") "lumi" else appTheme
            val isLiquidGlass = !eInkModeEnabled && effectiveAppTheme == "liquid_glass"
            val mainBackdrop = rememberLayerBackdrop()

            // 条款/政策更新弹窗状态
            var policyDialog by remember { mutableStateOf<PendingPolicyUpdate?>(null) }
            var showSplash by remember { mutableStateOf(splashEnabledAtLaunch) }

            LaunchedEffect(eInkModeEnabled) {
                if (eInkModeEnabled) {
                    showSplash = false
                } else {
                    if (splashEnabledAtLaunch) {
                        delay(1_100)
                    }
                    showSplash = false
                }
            }

            val mainContentAlpha by animateFloatAsState(
                targetValue = if (eInkModeEnabled || !showSplash) 1f else 0f,
                animationSpec = tween(460),
                label = "mainContentAlpha"
            )
            val mainContentScale by animateFloatAsState(
                targetValue = if (eInkModeEnabled || !showSplash) 1f else 0.985f,
                animationSpec = tween(520),
                label = "mainContentScale"
            )
            val mainContentOffset by animateDpAsState(
                targetValue = if (eInkModeEnabled || !showSplash) 0.dp else 12.dp,
                animationSpec = tween(520),
                label = "mainContentOffset"
            )

            // 监听后台检查结果
            val pending = pendingPolicyUpdate
            if (pending != null && policyDialog == null) {
                policyDialog = pending
                pendingPolicyUpdate = null
            }

            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = effectiveAppTheme == "material3",
                appTheme = effectiveAppTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkModeEnabled,
                eInkMode = eInkModeEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LiquidGlassDialogHost(
                        modifier = Modifier.fillMaxSize(),
                        backdrop = mainBackdrop.takeIf { isLiquidGlass }
                    ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isLiquidGlass) Modifier.layerBackdrop(mainBackdrop) else Modifier
                                )
                                .graphicsLayer {
                                    alpha = mainContentAlpha
                                    scaleX = mainContentScale
                                    scaleY = mainContentScale
                                    translationY = mainContentOffset.toPx()
                                }
                        ) {
                            MainNavGraph(
                                navController = navController,
                                entranceAnimationsEnabled = entranceAnimationsEnabled && !showSplash && !eInkModeEnabled,
                                predictiveBackEnabled = predictiveBackEnabled && !eInkModeEnabled,
                                requestedOpenBookId = requestedOpenBookId,
                                onBeforeOpenDifferentBook = ttsController::stop,
                                onOpenBookRequestConsumed = { requestedOpenBookId = null }
                            )
                        }

                    // Remote startup dialog priority: app update > notice > policy.
                    val appUpdate = pendingAppUpdate
                    val remoteNotice = pendingRemoteNotice
                    when {
                        appUpdate != null -> {
                            AppUpdateDialog(
                                appVersion = appUpdate.appVersion,
                                updateTitle = appUpdate.title,
                                updateMessage = appUpdate.message,
                                changelog = appUpdate.changelog,
                                force = appUpdate.force,
                                onDownload = {
                                    openRemoteUrl(appUpdate.releaseUrl)
                                    if (!appUpdate.force) pendingAppUpdate = null
                                },
                                onLater = {
                                    if (!appUpdate.force) pendingAppUpdate = null
                                },
                                onIgnoreVersion = if (appUpdate.force) null else {
                                    {
                                        lifecycleScope.launch {
                                            dataStoreManager.ignoreAppUpdate(appUpdate.latestVersionCode)
                                        }
                                        pendingAppUpdate = null
                                    }
                                }
                            )
                        }
                        remoteNotice != null -> {
                            RemoteNoticeDialog(
                                title = remoteNotice.title,
                                message = remoteNotice.message,
                                onConfirm = {
                                    lifecycleScope.launch {
                                        dataStoreManager.acknowledgeNotice(remoteNotice.id)
                                    }
                                    pendingRemoteNotice = null
                                }
                            )
                        }
                        policyDialog != null -> {
                            val update = policyDialog
                            if (update != null) {
                                PolicyUpdateDialog(
                                    hasTermsUpdate = update.hasTermsUpdate,
                                    termsVersion = update.termsVersion,
                                    hasPrivacyUpdate = update.hasPrivacyUpdate,
                                    privacyVersion = update.privacyVersion,
                                    onAccept = {
                                        lifecycleScope.launch {
                                            if (update.hasTermsUpdate) {
                                                dataStoreManager.saveAcceptedTermsVersion(update.termsVersion)
                                            }
                                            if (update.hasPrivacyUpdate) {
                                                dataStoreManager.saveAcceptedPrivacyVersion(update.privacyVersion)
                                            }
                                        }
                                        policyDialog = null
                                    },
                                    onDecline = { finishAffinity() },
                                    onViewTerms = { openUpdateDocument("用户协议", "terms.html") },
                                    onViewPrivacy = { openUpdateDocument("隐私政策", "privacy.html") }
                                )
                            }
                        }
                    }

                        AnimatedVisibility(
                            visible = showSplash && !eInkModeEnabled,
                            exit = fadeOut(animationSpec = tween(260)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            SplashScreen(isDark = isDark)
                        }
                    }
                    }
                }
            }
        }
    }

    private fun Configuration.isNightModeEnabled(): Boolean {
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun openUpdateDocument(title: String, assetFile: String) {
        startActivity(
            Intent(this, WebViewActivity::class.java)
                .putExtra("title", title)
                .putExtra("file", assetFile)
        )
    }

    private fun openRemoteUrl(url: String) {
        if (url.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }


    /**
     * 启动时自动检查更新：拉取 update_config.json，
     * 对比条款/政策版本，如有更新则标记待弹窗。
     */
    private fun performStartupUpdateCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = UpdateChecker.fetchUpdateConfig() ?: return@launch
                val packageInfo = try {
                    packageManager.getPackageInfo(packageName, 0)
                } catch (_: Exception) { null }
                val currentVersion = packageInfo?.versionName ?: "1.0"
                val currentVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

                val acceptedTerms = dataStoreManager.acceptedTermsVersion.first()
                val acceptedPrivacy = dataStoreManager.acceptedPrivacyVersion.first()
                val acknowledgedNoticeIds = dataStoreManager.acknowledgedNoticeIds.first()
                val ignoredAppUpdateVersionCode = dataStoreManager.ignoredAppUpdateVersionCode.first()

                val result = UpdateChecker.evaluate(
                    config = config,
                    currentVersion = currentVersion,
                    currentVersionCode = currentVersionCode,
                    acceptedTerms = acceptedTerms,
                    acceptedPrivacy = acceptedPrivacy
                )

                val nextAppUpdate = result
                    .takeIf {
                        it.hasAppUpdate && !(
                                !it.isForceUpdate &&
                                        it.latestVersionCode > 0L &&
                                        it.latestVersionCode == ignoredAppUpdateVersionCode
                                )
                    }
                    ?.let {
                        PendingAppUpdate(
                            appVersion = it.appVersion,
                            latestVersionCode = it.latestVersionCode,
                            title = it.updateTitle,
                            message = it.updateMessage,
                            changelog = it.changelog,
                            releaseUrl = it.releaseUrl,
                            force = it.isForceUpdate
                        )
                    }
                val nextNotice = result.notice
                    ?.takeIf { it.id !in acknowledgedNoticeIds }
                    ?.let { PendingRemoteNotice(id = it.id, title = it.title, message = it.message) }
                val nextPolicy = if (result.hasTermsUpdate || result.hasPrivacyUpdate) {
                    PendingPolicyUpdate(
                        hasTermsUpdate = result.hasTermsUpdate,
                        termsVersion = result.termsVersion,
                        hasPrivacyUpdate = result.hasPrivacyUpdate,
                        privacyVersion = result.privacyVersion
                    )
                } else {
                    null
                }

                withContext(Dispatchers.Main) {
                    pendingAppUpdate = nextAppUpdate
                    pendingRemoteNotice = nextNotice
                    pendingPolicyUpdate = nextPolicy
                }
            } catch (_: Exception) {
                // Fail silently so startup is not blocked.
            }
        }
    }
}

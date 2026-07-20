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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.ui.navigation.MainNavGraph
import com.huangder.lumibooks.ui.splash.SplashScreen
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.UpdateChecker
import com.huangder.lumibooks.util.parser.BookParserFactory
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

enum class ReaderPageDirection {
    PREVIOUS,
    NEXT
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var systemDarkMode by mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var bookRepository: BookRepository

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

    /** 待处理的条款/政策更新（非 null 时弹窗） */
    private var pendingPolicyUpdate: PendingPolicyUpdate? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
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

        val book = Book(
            id = FileUtils.generateBookId(),
            title = fileName.substringBeforeLast('.'),
            author = "未知作者",
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
        handleImportIntent(intent)

        // 启动时自动检查更新（静默执行，有变更时弹窗）
        performStartupUpdateCheck()

        val splashEnabledAtLaunch = if (intent.hasExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED)) {
            intent.getBooleanExtra(LaunchThemeController.EXTRA_SPLASH_ENABLED, false)
        } else {
            kotlinx.coroutines.runBlocking { dataStoreManager.splashEnabled.first() }
        }

        setContent {
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }

            // 条款/政策更新弹窗状态
            var policyDialog by remember { mutableStateOf<PendingPolicyUpdate?>(null) }
            var showSplash by remember { mutableStateOf(splashEnabledAtLaunch) }

            LaunchedEffect(Unit) {
                if (splashEnabledAtLaunch) {
                    delay(1_100)
                }
                showSplash = false
            }

            val mainContentAlpha by animateFloatAsState(
                targetValue = if (showSplash) 0f else 1f,
                animationSpec = tween(460),
                label = "mainContentAlpha"
            )
            val mainContentScale by animateFloatAsState(
                targetValue = if (showSplash) 0.985f else 1f,
                animationSpec = tween(520),
                label = "mainContentScale"
            )
            val mainContentOffset by animateDpAsState(
                targetValue = if (showSplash) 12.dp else 0.dp,
                animationSpec = tween(520),
                label = "mainContentOffset"
            )

            // 监听后台检查结果
            val pending = pendingPolicyUpdate
            if (pending != null && policyDialog == null) {
                policyDialog = pending
                pendingPolicyUpdate = null
            }

            EBookReaderTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = mainContentAlpha
                                    scaleX = mainContentScale
                                    scaleY = mainContentScale
                                    translationY = mainContentOffset.toPx()
                                }
                        ) {
                            MainNavGraph(navController = navController)
                        }

                    // ── 条款/政策更新弹窗 ──
                        policyDialog?.let { update ->
                        val title = when {
                            update.hasTermsUpdate && update.hasPrivacyUpdate -> "用户协议与隐私政策已更新"
                            update.hasTermsUpdate -> "用户协议已更新"
                            else -> "隐私政策已更新"
                        }
                        val message = buildString {
                            append("感谢您使用 Lumi！我们更新了法律条款，请阅读后选择是否同意。")
                            if (update.hasTermsUpdate) append("\n\n• 用户协议已更新（版本 ${update.termsVersion}）")
                            if (update.hasPrivacyUpdate) append("\n\n• 隐私政策已更新（版本 ${update.privacyVersion}）")
                        }

                        AlertDialog(
                            onDismissRequest = { /* 不可关闭 */ },
                            title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                            text = { Text(message, fontSize = 14.sp) },
                            confirmButton = {
                                TextButton(onClick = {
                                    lifecycleScope.launch {
                                        if (update.hasTermsUpdate)
                                            dataStoreManager.saveAcceptedTermsVersion(update.termsVersion)
                                        if (update.hasPrivacyUpdate)
                                            dataStoreManager.saveAcceptedPrivacyVersion(update.privacyVersion)
                                    }
                                    policyDialog = null
                                }) { Text("同意并继续", color = AppColors.Accent) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    finishAffinity()
                                }) { Text("不同意并退出", color = Color.Gray) }
                            }
                        )
                        }

                        AnimatedVisibility(
                            visible = showSplash,
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

    private fun Configuration.isNightModeEnabled(): Boolean {
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 启动时自动检查更新：拉取 update_config.json，
     * 对比条款/政策版本，如有更新则标记待弹窗。
     */
    private fun performStartupUpdateCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = UpdateChecker.fetchUpdateConfig() ?: return@launch
                val currentVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
                } catch (_: Exception) { "1.0" }

                val acceptedTerms = dataStoreManager.acceptedTermsVersion.first()
                val acceptedPrivacy = dataStoreManager.acceptedPrivacyVersion.first()

                val result = UpdateChecker.evaluate(config, currentVersion, acceptedTerms, acceptedPrivacy)

                if (result.hasTermsUpdate || result.hasPrivacyUpdate) {
                    pendingPolicyUpdate = PendingPolicyUpdate(
                        hasTermsUpdate = result.hasTermsUpdate,
                        termsVersion = result.termsVersion,
                        hasPrivacyUpdate = result.hasPrivacyUpdate,
                        privacyVersion = result.privacyVersion
                    )
                }
            } catch (_: Exception) {
                // 静默失败，不打扰用户
            }
        }
    }
}

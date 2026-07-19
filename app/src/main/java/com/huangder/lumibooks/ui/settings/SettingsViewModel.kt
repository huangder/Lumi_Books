package com.huangder.lumibooks.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val bookRepository: BookRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        collectAllPreferences()
        calculateStorageBreakdown()
    }

    private fun collectAllPreferences() {
        viewModelScope.launch {
            dataStoreManager.avatarUri.collectLatest { uri ->
                _uiState.value = _uiState.value.copy(avatarUri = uri)
            }
        }
        viewModelScope.launch {
            dataStoreManager.nickname.collectLatest { name ->
                _uiState.value = _uiState.value.copy(nickname = name)
            }
        }
        viewModelScope.launch {
            dataStoreManager.fontSize.collectLatest { value ->
                _uiState.value = _uiState.value.copy(fontSize = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.lineHeight.collectLatest { value ->
                _uiState.value = _uiState.value.copy(lineHeight = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.letterSpacing.collectLatest { value ->
                _uiState.value = _uiState.value.copy(letterSpacing = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.fontType.collectLatest { value ->
                _uiState.value = _uiState.value.copy(fontType = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginHoriz.collectLatest { value ->
                _uiState.value = _uiState.value.copy(marginHoriz = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginVert.collectLatest { value ->
                _uiState.value = _uiState.value.copy(marginVert = value)
            }
        }
        viewModelScope.launch {
            dataStoreManager.darkMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(darkMode = mode)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerTheme.collectLatest { theme ->
                _uiState.value = _uiState.value.copy(readerTheme = theme)
            }
        }
        viewModelScope.launch {
            dataStoreManager.dailyGoal.collectLatest { goal ->
                _uiState.value = _uiState.value.copy(dailyGoal = goal)
            }
        }
        viewModelScope.launch {
            dataStoreManager.acceptedTermsVersion.collectLatest { version ->
                _uiState.value = _uiState.value.copy(
                    updateCheck = _uiState.value.updateCheck.copy(acceptedTermsVersion = version)
                )
            }
        }
        viewModelScope.launch {
            dataStoreManager.acceptedPrivacyVersion.collectLatest { version ->
                _uiState.value = _uiState.value.copy(
                    updateCheck = _uiState.value.updateCheck.copy(acceptedPrivacyVersion = version)
                )
            }
        }
        viewModelScope.launch {
            dataStoreManager.appLanguage.collectLatest { language ->
                _uiState.value = _uiState.value.copy(appLanguage = language)
            }
        }
    }

    // ─── 个人信息 ───

    fun saveAvatar(avatarPath: String) {
        viewModelScope.launch {
            dataStoreManager.saveAvatarUri(avatarPath)
            _uiState.value = _uiState.value.copy(avatarUri = avatarPath)
        }
    }

    fun saveNickname(name: String) {
        viewModelScope.launch {
            dataStoreManager.saveNickname(name)
            _uiState.value = _uiState.value.copy(nickname = name)
        }
    }

    // ─── 阅读设置 ───

    fun saveFontSize(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveFontSize(value)
            _uiState.value = _uiState.value.copy(fontSize = value)
        }
    }

    fun saveLineHeight(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveLineHeight(value)
            _uiState.value = _uiState.value.copy(lineHeight = value)
        }
    }

    fun saveLetterSpacing(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveLetterSpacing(value)
            _uiState.value = _uiState.value.copy(letterSpacing = value)
        }
    }

    fun saveFontType(value: String) {
        viewModelScope.launch {
            dataStoreManager.saveFontType(value)
            _uiState.value = _uiState.value.copy(fontType = value)
        }
    }

    fun saveMarginHoriz(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveMarginHoriz(value)
            _uiState.value = _uiState.value.copy(marginHoriz = value)
        }
    }

    fun saveMarginVert(value: Float) {
        viewModelScope.launch {
            dataStoreManager.saveMarginVert(value)
            _uiState.value = _uiState.value.copy(marginVert = value)
        }
    }

    // ─── 显示与外观 ───

    fun saveDarkMode(mode: String) {
        viewModelScope.launch {
            dataStoreManager.saveDarkMode(mode)
            _uiState.value = _uiState.value.copy(darkMode = mode)
        }
    }

    fun saveReaderTheme(theme: String) {
        viewModelScope.launch {
            dataStoreManager.saveReaderTheme(theme)
            _uiState.value = _uiState.value.copy(readerTheme = theme)
        }
    }

    // ─── 语言 ───

    fun saveAppLanguage(language: String) {
        viewModelScope.launch {
            dataStoreManager.saveAppLanguage(language)
            _uiState.value = _uiState.value.copy(appLanguage = language)
        }
    }

    // ─── 阅读目标 ───

    fun saveDailyGoal(minutes: Int) {
        viewModelScope.launch {
            dataStoreManager.saveDailyGoal(minutes)
            _uiState.value = _uiState.value.copy(dailyGoal = minutes)
        }
    }

    // ─── 存储管理 ───

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.deleteRecursively()
                // 清除图片缓存目录
                File(context.cacheDir, "image_cache").deleteRecursively()
                // 清除 Coil 图片缓存
                try { Coil.imageLoader(context).diskCache?.clear() } catch (_: Exception) { }
                try { Coil.imageLoader(context).memoryCache?.clear() } catch (_: Exception) { }
                calculateStorageBreakdown()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) { }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 清除缓存
                context.cacheDir.deleteRecursively()
                // 清除内部存储（保留头像）
                val avatarDir = File(context.filesDir, "avatars")
                val avatarBackup = if (avatarDir.exists()) {
                    val backup = File(context.cacheDir, "avatar_backup")
                    avatarDir.copyRecursively(backup, overwrite = true)
                    backup
                } else null

                context.filesDir.listFiles()?.forEach { file ->
                    if (file.name != "avatars") file.deleteRecursively()
                }

                // 恢复头像
                if (avatarBackup != null && avatarBackup.exists()) {
                    avatarBackup.copyRecursively(avatarDir, overwrite = true)
                    avatarBackup.deleteRecursively()
                }

                // 重置 DataStore
                dataStoreManager.clearAll()

                calculateStorageBreakdown()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "所有数据已清除", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) { }
        }
    }

    /** 计算存储空间分解：应用本体 + 缓存 + 电子书 + 封面 + 逐本书明细 */
    private fun calculateStorageBreakdown() {
        viewModelScope.launch(Dispatchers.IO) {
            // APK 本体大小
            val appSize = try {
                val appInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                File(appInfo.applicationInfo?.sourceDir ?: "").length()
            } catch (_: Exception) { 0L }

            val cacheSize = getDirSize(context.cacheDir)
            val filesSize = getDirSize(context.filesDir)
            val booksDirSize = getDirSize(FileUtils.getBooksDirectory(context))
            val coversDirSize = getDirSize(FileUtils.getCoversDirectory(context))

            // 逐本书文件大小（按大小降序）
            val bookDetails = bookRepository.getAllBooks().first().map { book ->
                val fileSize = File(book.filePath).let { if (it.exists()) it.length() else 0L }
                BookSizeItem(book.id, book.title, book.format.name, fileSize)
            }.sortedByDescending { it.sizeBytes }

            _uiState.value = _uiState.value.copy(
                storageInfo = StorageInfo(
                    appSizeBytes = appSize,
                    cacheSizeBytes = cacheSize + filesSize,
                    booksSizeBytes = booksDirSize,
                    coversSizeBytes = coversDirSize,
                    bookDetails = bookDetails
                )
            )
        }
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    // ─── 备份 ───

    /**
     * 将数据库 + DataStore + 头像打包为 ZIP，写入 [outputUri]。
     * 返回生成文件大小的可读字符串，失败抛异常。
     */
    suspend fun backup(outputUri: Uri): String {
        _uiState.value = _uiState.value.copy(isProcessing = true, backupStatus = "正在备份...")
        try {
            val bytes = context.contentResolver.openOutputStream(outputUri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    // 1. Room 数据库（主库 + WAL + SHM）
                    val dbDir = context.getDatabasePath("ebook_reader_database").parentFile
                    dbDir?.listFiles()?.forEach { f ->
                        if (f.name.startsWith("ebook_reader_database")) {
                            addFileToZip(zip, "database/${f.name}", f)
                        }
                    }

                    // 2. DataStore preferences
                    val dsFile = File(context.filesDir.parentFile, "datastore/settings.preferences")
                    if (dsFile.exists()) addFileToZip(zip, "datastore/settings.preferences", dsFile)

                    // 3. 头像
                    val avatar = File(context.filesDir, "avatars/avatar.jpg")
                    if (avatar.exists()) addFileToZip(zip, "avatars/avatar.jpg", avatar)

                    // 4. 书籍文件目录（与 FileUtils.getBooksDirectory 路径一致）
                    val booksDir = File(context.getExternalFilesDir(null), "books")
                    if (booksDir.exists()) {
                        booksDir.walkTopDown().filter { it.isFile }.forEach { f ->
                            val rel = f.relativeTo(booksDir).path.replace("\\", "/")
                            addFileToZip(zip, "books/$rel", f)
                        }
                    }
                }
                // 计算大小：重新读取有点浪费，用 cacheDir 里的临时副本
                0L // placeholder
            }

            // 获取文件大小
            val size = context.contentResolver.openInputStream(outputUri)?.use { it.available().toLong() } ?: 0L
            val sizeStr = formatFileSize(size)

            _uiState.value = _uiState.value.copy(isProcessing = false, backupStatus = "备份完成 ($sizeStr)")
            return sizeStr
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isProcessing = false, backupStatus = "备份失败: ${e.message}")
            throw e
        }
    }

    /**
     * 从 [inputUri] 读取 ZIP 并恢复数据库、DataStore、头像。
     * 恢复后需要重启 App 才能生效。
     */
    suspend fun restore(inputUri: Uri) {
        _uiState.value = _uiState.value.copy(isProcessing = true, backupStatus = "正在恢复...")
        try {
            context.contentResolver.openInputStream(inputUri)?.use { inp ->
                ZipInputStream(inp).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name.startsWith("database/") -> {
                                val dbName = name.removePrefix("database/")
                                val target = context.getDatabasePath(dbName)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { zip.copyTo(it) }
                            }
                            name == "datastore/settings.preferences" -> {
                                val target = File(context.filesDir.parentFile, "datastore/settings.preferences")
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { zip.copyTo(it) }
                            }
                            name.startsWith("avatars/") -> {
                                val target = File(context.filesDir, name)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { zip.copyTo(it) }
                            }
                            name.startsWith("books/") -> {
                                val target = File(context.getExternalFilesDir(null), name)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { zip.copyTo(it) }
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            _uiState.value = _uiState.value.copy(isProcessing = false, backupStatus = "恢复成功，请重启应用")
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isProcessing = false, backupStatus = "恢复失败: ${e.message}")
            throw e
        }
    }

    fun clearBackupStatus() {
        _uiState.value = _uiState.value.copy(backupStatus = "")
    }

    // ─── 检查更新 ──────────────────────────────────────────

    /**
     * 执行完整的更新检查（App版本 + 用户协议 + 隐私政策）。
     * @param isAutoCheck true = 启动时自动检查（静默模式），false = 手动触发
     */
    fun checkUpdate(isAutoCheck: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                updateCheck = _uiState.value.updateCheck.copy(isChecking = true, isNetworkError = false)
            )

            val config = UpdateChecker.fetchUpdateConfig()
            if (config == null) {
                _uiState.value = _uiState.value.copy(
                    updateCheck = _uiState.value.updateCheck.copy(
                        isChecking = false,
                        isNetworkError = true
                    )
                )
                if (!isAutoCheck) {
                    Toast.makeText(context, "网络连接失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            } catch (_: Exception) { "1.0" }

            val state = _uiState.value.updateCheck
            val result = UpdateChecker.evaluate(
                config = config,
                currentVersion = currentVersion,
                acceptedTerms = state.acceptedTermsVersion,
                acceptedPrivacy = state.acceptedPrivacyVersion
            )

            // 决定弹出哪个 Dialog（条款/政策优先于App更新）
            val hasPolicyUpdate = result.hasTermsUpdate || result.hasPrivacyUpdate
            val showAppDialog = result.hasAppUpdate && !hasPolicyUpdate

            _uiState.value = _uiState.value.copy(
                updateCheck = state.copy(
                    hasAppUpdate = result.hasAppUpdate,
                    appVersion = result.appVersion,
                    releaseUrl = result.releaseUrl,
                    hasTermsUpdate = result.hasTermsUpdate,
                    termsVersion = result.termsVersion,
                    hasPrivacyUpdate = result.hasPrivacyUpdate,
                    privacyVersion = result.privacyVersion,
                    isChecking = false,
                    // 自动检查时静默弹窗，手动检查时弹窗
                    showPolicyUpdateDialog = hasPolicyUpdate,
                    showAppUpdateDialog = showAppDialog && !isAutoCheck
                )
            )
        }
    }

    /** 用户同意更新后的条款 */
    fun acceptTermsUpdate(version: Int) {
        viewModelScope.launch {
            dataStoreManager.saveAcceptedTermsVersion(version)
            dismissPolicyUpdateDialog()
        }
    }

    /** 用户同意更新后的隐私政策 */
    fun acceptPrivacyUpdate(version: Int) {
        viewModelScope.launch {
            dataStoreManager.saveAcceptedPrivacyVersion(version)
            dismissPolicyUpdateDialog()
        }
    }

    /** 关闭条款/政策更新 Dialog */
    fun dismissPolicyUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            updateCheck = _uiState.value.updateCheck.copy(showPolicyUpdateDialog = false)
        )
    }

    /** 关闭 App 更新 Dialog */
    fun dismissAppUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            updateCheck = _uiState.value.updateCheck.copy(showAppUpdateDialog = false)
        )
    }

    private fun addFileToZip(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}

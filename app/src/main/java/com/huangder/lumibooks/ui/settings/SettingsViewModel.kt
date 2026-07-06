package com.huangder.lumibooks.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        collectAllPreferences()
        calculateCacheSize()
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

    // ─── 阅读目标 ───

    fun saveDailyGoal(minutes: Int) {
        viewModelScope.launch {
            dataStoreManager.saveDailyGoal(minutes)
            _uiState.value = _uiState.value.copy(dailyGoal = minutes)
        }
    }

    // ─── 存储管理 ───

    fun clearCache() {
        viewModelScope.launch {
            try {
                context.cacheDir.deleteRecursively()
                // 清除图片缓存目录
                File(context.cacheDir, "image_cache").deleteRecursively()
                calculateCacheSize()
            } catch (_: Exception) { }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
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

                calculateCacheSize()
            } catch (_: Exception) { }
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            val size = getDirSize(context.cacheDir) + getDirSize(context.filesDir)
            _uiState.value = _uiState.value.copy(cacheSize = formatFileSize(size))
        }
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatFileSize(bytes: Long): String {
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

                    // 4. 书籍文件目录
                    val booksDir = File(context.filesDir, "books")
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
                                val target = File(context.filesDir, name)
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

    private fun addFileToZip(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}

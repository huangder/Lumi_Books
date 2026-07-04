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
import java.io.File
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
}

package com.huangder.lumibooks.ui.settings

data class SettingsUiState(
    // 个人信息
    val avatarUri: String? = null,
    val nickname: String = "读者",

    // 阅读设置
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val fontType: String = "system",
    val marginHoriz: Float = 44f,
    val marginVert: Float = 72f,

    // 显示与外观
    val darkMode: String = "system",       // "system" / "light" / "dark"
    val readerTheme: String = "day",       // "day" / "night" / "sepia" / "green"

    // 阅读目标
    val dailyGoal: Int = 30,               // 分钟

    // 存储
    val cacheSize: String = "计算中...",

    // 备份恢复
    val backupStatus: String = "",     // 操作状态提示
    val isProcessing: Boolean = false  // 是否正在执行备份/恢复
)

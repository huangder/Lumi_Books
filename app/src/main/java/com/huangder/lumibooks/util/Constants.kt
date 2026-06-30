package com.ebook.reader.util

object Constants {
    // 数据库
    const val DATABASE_NAME = "ebook_reader_database"
    const val DATABASE_VERSION = 1

    // 文件
    const val BOOKS_DIRECTORY = "books"
    const val COVERS_DIRECTORY = "covers"

    // 默认值
    const val DEFAULT_DAILY_GOAL = 30 // 分钟
    const val DEFAULT_FONT_SIZE = 16f
    const val DEFAULT_LINE_HEIGHT = 1.5f
    const val DEFAULT_LETTER_SPACING = 0f

    // 动画
    const val PAGE_TURN_DURATION = 300
    const val MENU_ANIMATION_DURATION = 200

    // 阅读
    const val READING_TIMER_INTERVAL = 1000L // 1秒
    const val PROGRESS_UPDATE_INTERVAL = 5000L // 5秒

    // 格式
    val SUPPORTED_FORMATS = listOf("epub", "pdf", "txt")

    // 主题
    const val THEME_DAY = "day"
    const val THEME_NIGHT = "night"
    const val THEME_SEPIA = "sepia"
    const val THEME_GREEN = "green"

    // 字体
    const val FONT_SYSTEM = "system"
    const val FONT_SERIF = "serif"
    const val FONT_SANS_SERIF = "sans_serif"
    const val FONT_MONOSPACE = "monospace"
}

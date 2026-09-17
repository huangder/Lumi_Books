package com.huangder.lumibooks.domain.model

/**
 * 漫画翻页方向。只影响横向翻页：竖向滚动与上下分页两种模式与方向无关。
 * 存储为 per-book 偏好；未设置时由 ComicInfo.xml 的 Manga 字段决定，仍无信息则从左到右。
 */
enum class CbzReadingDirection(val key: String) {
    LEFT_TO_RIGHT("ltr"),
    RIGHT_TO_LEFT("rtl");

    val isRightToLeft: Boolean get() = this == RIGHT_TO_LEFT

    fun next(): CbzReadingDirection =
        if (this == LEFT_TO_RIGHT) RIGHT_TO_LEFT else LEFT_TO_RIGHT

    companion object {
        fun fromKey(key: String?): CbzReadingDirection? = entries.firstOrNull { it.key == key }

        fun resolve(storedKey: String?, prefersRightToLeft: Boolean): CbzReadingDirection =
            fromKey(storedKey) ?: if (prefersRightToLeft) RIGHT_TO_LEFT else LEFT_TO_RIGHT
    }
}

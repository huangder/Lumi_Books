package com.huangder.lumibooks.domain.model

/**
 * 栅格页面（PDF / CBZ）的解码清晰度档位。
 *
 * PDF 使用逐档提高的矢量超采样倍率；漫画的正常档按显示宽度解码，
 * 高清档仅在整页超出像素预算时降采样，原图档保留源图像素。
 */
enum class PageRenderMode(val key: String) {
    NORMAL("normal"),
    HIGH("high"),
    NATIVE("native");

    fun next(): PageRenderMode = when (this) {
        NORMAL -> HIGH
        HIGH -> NATIVE
        NATIVE -> NORMAL
    }

    companion object {
        fun fromKey(key: String?): PageRenderMode =
            entries.firstOrNull { it.key == key } ?: NORMAL

        fun normalizeKey(key: String?): String = fromKey(key).key
    }
}

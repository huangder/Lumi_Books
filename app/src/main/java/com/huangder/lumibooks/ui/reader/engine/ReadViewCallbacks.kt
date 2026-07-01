package com.huangder.lumibooks.ui.reader.engine

/**
 * ReadView 向外（Compose/ViewModel）的回调接口。
 */
interface ReadViewCallbacks {
    /**
     * 页面切换时回调。
     * @param globalPage 全局页码
     * @param chapterIndex 所在章节索引
     * @param pageInChapter 章内页码（0-based）
     * @param chapterTotalPages 当前章节总页数
     */
    fun onPageChanged(
        globalPage: Int,
        chapterIndex: Int,
        pageInChapter: Int,
        chapterTotalPages: Int
    )

    /** 点击中间区域，切换菜单 */
    fun onMenuToggle()

    /** 正在加载内容变化 */
    fun onLoadingChanged(isLoading: Boolean)

    /**
     * 长按文本选择回调。
     * @param chapterIndex 章节索引
     * @param pageInChapter 章内页码
     * @param charStart 选中起始字符偏移
     * @param charEnd 选中结束字符偏移
     * @param selectedText 选中的文本
     * @param touchX 触摸位置 X（用于菜单定位）
     * @param touchY 触摸位置 Y
     */
    fun onTextSelected(
        chapterIndex: Int,
        pageInChapter: Int,
        charStart: Int,
        charEnd: Int,
        selectedText: String,
        touchX: Float,
        touchY: Float
    ) {}
}

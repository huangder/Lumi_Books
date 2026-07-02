package com.huangder.lumibooks.ui.reader.engine

/**
 * ReadView 向外（Compose/ViewModel）的回调接口。
 */
interface ReadViewCallbacks {
    /**
     * 页面切换时回调。
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
     * 长按选中文本回调。
     * @param selectedText 选中的文本
     * @param screenX 触摸位置 X（用于菜单定位）
     * @param screenY 触摸位置 Y
     */
    fun onTextSelected(selectedText: String, screenX: Float, screenY: Float) {}
}

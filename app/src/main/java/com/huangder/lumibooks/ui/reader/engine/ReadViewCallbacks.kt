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
     * 🔥 原生选择 ActionMode 触发动作。
     * @param action "highlight" | "note" | "search" | "copy"
     * @param selectedText 选中的文本
     * @param chapterIndex 所在章节索引
     * @param startPosition 章节级起始字符偏移
     * @param endPosition 章节级结束字符偏移
     * @param pageStart 页面内起始偏移（用于渲染高亮定位）
     * @param pageEnd 页面内结束偏移
     */
    fun onSelectionAction(
        action: String,
        selectedText: String,
        chapterIndex: Int,
        startPosition: Int,
        endPosition: Int,
        pageStart: Int = 0,
        pageEnd: Int = 0
    ) {}
}

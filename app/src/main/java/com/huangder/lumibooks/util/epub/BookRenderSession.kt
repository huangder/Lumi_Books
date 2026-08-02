package com.huangder.lumibooks.util.epub

import androidx.webkit.WebViewAssetLoader

/**
 * 格式无关的"书籍原排版"渲染会话。
 *
 * EPUB（[EpubRenderSession]）与 MOBI（MobiRenderSession）共用同一套 WebView
 * 阅读器（翻页 / 选择 / 定位 / 搜索），因此把所有渲染器实际消费的能力收敛到
 * 这个接口上。章节标识（href）必须是跨会话稳定的，用于 EpubLocator 持久化。
 */
interface BookRenderSession : AutoCloseable {
    val chapterCount: Int

    fun chapterUrl(chapterIndex: Int, fragment: String? = null): String

    /** Locator 使用的稳定章节标识（EPUB=manifest fullPath，MOBI=合成 chapter-NNN.html）。 */
    fun chapterHref(chapterIndex: Int): String

    fun chapterIndexForUrl(url: String): Int?

    /** 将章节 HTML 内的图片源解析为会话内安全 URL；外部/未知源返回 null。 */
    fun imageUrl(sourceChapterIndex: Int, source: String): String?

    /** 将 WebView 图片 URL（或 data URI）还原为资源字节，供预览/导出使用。 */
    fun readImageUrl(url: String): EpubResource?

    /** 解析书内链接；外部链接返回 null。 */
    fun resolveInternalLink(sourceChapterIndex: Int, href: String): Pair<Int, String?>?

    val assetLoader: WebViewAssetLoader

    /** 用户自定义阅读字体经 WebViewAssetLoader 提供的 URL；不使用返回 null。 */
    fun readerFontUrl(filePath: String?): String?

    fun renditionLayout(chapterIndex: Int): EpubRenditionLayout

    fun pageProgressionDirection(chapterIndex: Int): EpubPageProgressionDirection

    /** 章节纯文本（去除样式/脚本），用于原排版模式全文搜索。 */
    fun searchText(chapterIndex: Int): String

    override fun close()

    companion object {
        const val ASSET_DOMAIN = "appassets.androidplatform.net"
    }
}

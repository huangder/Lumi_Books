package com.huangder.lumibooks.util.epub

enum class EpubRenderMode(val storageValue: String) {
    BOOK_LAYOUT("book_layout"),
    READER_LAYOUT("reader_layout");

    companion object {
        fun fromStorage(value: String?): EpubRenderMode? = entries.firstOrNull {
            it.storageValue == value
        }
    }
}

enum class EpubPageProgressionDirection { LTR, RTL, DEFAULT }

enum class EpubRenditionLayout { REFLOWABLE, PRE_PAGINATED }

enum class EpubPageSide { LEFT, RIGHT, CENTER }

/** Publisher permission for synthetic spreads, evaluated against the reader viewport. */
enum class EpubRenditionSpread {
    NONE, LANDSCAPE, PORTRAIT, BOTH, AUTO;

    fun allowsSpread(isLandscape: Boolean): Boolean = when (this) {
        NONE -> false
        LANDSCAPE -> isLandscape
        PORTRAIT -> !isLandscape
        BOTH, AUTO -> true
    }

    companion object {
        fun fromMetadata(value: String?): EpubRenditionSpread? = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        }
    }
}

/**
 * Presentation hints for one logical chapter. Dimensions are the publisher's design
 * viewport, never the current WebView size or an image's decoded dimensions.
 */
data class EpubPagePresentation(
    val renditionLayout: EpubRenditionLayout = EpubRenditionLayout.REFLOWABLE,
    val preferredSide: EpubPageSide? = null,
    val renditionSpread: EpubRenditionSpread = EpubRenditionSpread.AUTO,
    val isCover: Boolean = false,
    val viewportWidth: Float? = null,
    val viewportHeight: Float? = null
)

data class EpubManifestItem(
    val id: String,
    val href: String,
    val fullPath: String,
    val mediaType: String,
    val properties: Set<String> = emptySet()
)

data class EpubSpineItem(
    val idRef: String,
    val manifestItem: EpubManifestItem,
    val linear: Boolean = true,
    val properties: Set<String> = emptySet(),
    val renditionLayout: EpubRenditionLayout
)

data class EpubNavigationItem(val title: String, val href: String, val level: Int)

data class EpubPackage(
    val filePath: String,
    val opfPath: String,
    val basePath: String,
    val title: String,
    val author: String,
    val manifest: Map<String, EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    val navigation: List<EpubNavigationItem>,
    val pageProgressionDirection: EpubPageProgressionDirection,
    val renditionLayout: EpubRenditionLayout,
    val renditionOrientation: String?,
    val renditionSpread: String?,
    val renditionFlow: String?
) {
    val manifestByPath: Map<String, EpubManifestItem> = manifest.values.associateBy { it.fullPath }
}

data class EpubLocator(
    val version: Int = 1,
    val href: String,
    val domPath: List<Int> = emptyList(),
    val textOffset: Int = 0,
    val textPosition: Int = 0,
    val textLength: Int = 0,
    val exact: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val progression: Float = 0f
)

data class EpubSearchMatch(
    val chapterIndex: Int,
    val charOffset: Int,
    val matchLength: Int,
    val context: String,
    val locator: EpubLocator
)

data class EpubPageState(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val locator: EpubLocator?
)

data class EpubSelectionPayload(
    val text: String,
    val start: EpubLocator,
    val end: EpubLocator,
    val x: Float,
    val y: Float
)

interface EpubResourceProvider : AutoCloseable {
    val epubPackage: EpubPackage
    fun read(path: String): EpubResource?
}

data class EpubResource(val path: String, val mediaType: String, val bytes: ByteArray)

interface BookRenderSource {
    fun openRenderSession(): BookRenderSession
}

interface BookSearchSource {
    suspend fun searchBook(
        query: String,
        maxResults: Int = 200
    ): List<EpubSearchMatch>
}

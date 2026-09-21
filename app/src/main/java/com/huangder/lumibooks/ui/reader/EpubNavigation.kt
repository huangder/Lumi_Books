package com.huangder.lumibooks.ui.reader

internal const val EPUB_NAVIGATION_TIMEOUT_MS = 10_000L

internal fun epubDocumentMessageMatches(
    messageDocumentUrl: String?,
    expectedDocumentUrl: String
): Boolean {
    val actual = messageDocumentUrl?.trim()?.substringBefore('#').orEmpty()
    val expected = expectedDocumentUrl.trim().substringBefore('#')
    return actual.isNotEmpty() && actual == expected
}

internal enum class EpubNavigationOrigin {
    TOC,
    BOOKMARK,
    NOTE,
    SEARCH,
    INTERNAL_LINK,
    TTS,
    RETURN_TO_SOURCE,
    CHAPTER_CONTROL,
    PROGRESS
}

internal sealed interface EpubNavigationDestination {
    data object ChapterStart : EpubNavigationDestination

    data class Fragment(val value: String) : EpubNavigationDestination

    data class Locator(val json: String) : EpubNavigationDestination

    data class Page(
        val index: Int,
        val chapterFraction: Float? = null
    ) : EpubNavigationDestination
}

internal data class EpubNavigationRequest(
    val operationId: Long,
    val origin: EpubNavigationOrigin,
    val sourceChapterIndex: Int,
    val sourcePageIndex: Int,
    val targetChapterIndex: Int,
    val destination: EpubNavigationDestination,
    val showLoadingPage: Boolean = true
) {
    fun requiresStaging(activeChapterIndex: Int): Boolean =
        targetChapterIndex != activeChapterIndex
}

internal enum class EpubNavigationStage {
    STARTING,
    LOADING_DOCUMENT,
    CONFIGURING,
    WAITING_FOR_FRAME
}

internal enum class EpubNavigationFailureReason {
    INVALID_TARGET,
    DOCUMENT_ERROR,
    HTTP_ERROR,
    SCRIPT_NOT_READY,
    TARGET_NOT_FOUND,
    TIMEOUT,
    RENDERER_GONE
}

internal sealed interface EpubNavigationResult {
    val operationId: Long

    data class Committed(
        override val operationId: Long,
        val chapterIndex: Int,
        val pageIndex: Int,
        val pageCount: Int,
        val locatorJson: String?
    ) : EpubNavigationResult

    data class Cancelled(
        override val operationId: Long,
        val replaced: Boolean
    ) : EpubNavigationResult

    data class Failed(
        override val operationId: Long,
        val reason: EpubNavigationFailureReason
    ) : EpubNavigationResult
}

/** Small token gate shared by the renderer and unit tests. */
internal class EpubNavigationOperationTracker {
    private var activeOperationId: Long? = null

    fun begin(operationId: Long): Long? {
        val replaced = activeOperationId
        activeOperationId = operationId
        return replaced
    }

    fun accepts(operationId: Long): Boolean = activeOperationId == operationId

    fun finish(operationId: Long): Boolean {
        if (!accepts(operationId)) return false
        activeOperationId = null
        return true
    }

    fun activeOperationId(): Long? = activeOperationId
}

internal fun EpubNavigationDestination.requestedPageIndex(): Int = when (this) {
    EpubNavigationDestination.ChapterStart -> 0
    is EpubNavigationDestination.Page -> index.coerceAtLeast(0)
    is EpubNavigationDestination.Fragment,
    is EpubNavigationDestination.Locator -> 0
}

internal fun EpubNavigationDestination.matchesPage(pageIndex: Int, pageCount: Int): Boolean =
    when (this) {
        EpubNavigationDestination.ChapterStart -> pageIndex == 0
        is EpubNavigationDestination.Page -> {
            val expected = chapterFraction?.let {
                pageIndexForChapterFraction(it, pageCount)
            } ?: index.coerceAtLeast(0).coerceAtMost((pageCount - 1).coerceAtLeast(0))
            pageIndex == expected
        }
        is EpubNavigationDestination.Fragment,
        is EpubNavigationDestination.Locator -> true
    }

package com.huangder.lumibooks.ui.reader

internal data class EpubInitialLoadAttempt(
    val chapterIndex: Int,
    val generation: Long,
    val retryCount: Int
)

internal enum class EpubInitialLoadFailureReason {
    READER_SCRIPT_MISSING,
    SCRIPT_EXECUTION_ERROR,
    MAIN_FRAME_ERROR,
    HTTP_ERROR,
    PAGE_READY_TIMEOUT,
    RENDERER_GONE
}

internal data class EpubReadingPosition(
    val chapterIndex: Int, val pageIndex: Int, val pageCount: Int, val locatorJson: String?
)

internal data class EpubRenderFailure(
    val reason: EpubInitialLoadFailureReason,
    val chapterIndex: Int,
    val lastSuccessfulPosition: EpubReadingPosition?,
    val hasVisiblePage: Boolean
)

internal sealed interface EpubInitialLoadRecoveryAction {
    data object Ignore : EpubInitialLoadRecoveryAction
    data class Retry(val attempt: EpubInitialLoadAttempt) : EpubInitialLoadRecoveryAction
    data class Failed(val failedAttempt: EpubInitialLoadAttempt) : EpubInitialLoadRecoveryAction
}

/** Bounds recovery of the active EPUB document and rejects callbacks from older loads. */
internal class EpubInitialLoadRecovery(
    private val maxRetries: Int = 1
) {
    private var nextGeneration = 0L
    private var activeAttempt: EpubInitialLoadAttempt? = null

    init {
        require(maxRetries >= 0)
    }

    fun begin(chapterIndex: Int): EpubInitialLoadAttempt =
        EpubInitialLoadAttempt(
            chapterIndex = chapterIndex,
            generation = ++nextGeneration,
            retryCount = 0
        ).also { activeAttempt = it }

    fun current(chapterIndex: Int? = null): EpubInitialLoadAttempt? =
        activeAttempt?.takeIf { chapterIndex == null || it.chapterIndex == chapterIndex }

    fun isCurrent(attempt: EpubInitialLoadAttempt): Boolean = activeAttempt == attempt

    fun fail(attempt: EpubInitialLoadAttempt): EpubInitialLoadRecoveryAction {
        if (!isCurrent(attempt)) return EpubInitialLoadRecoveryAction.Ignore
        if (attempt.retryCount >= maxRetries) {
            activeAttempt = null
            return EpubInitialLoadRecoveryAction.Failed(attempt)
        }
        val retry = attempt.copy(
            generation = ++nextGeneration,
            retryCount = attempt.retryCount + 1
        )
        activeAttempt = retry
        return EpubInitialLoadRecoveryAction.Retry(retry)
    }

    fun complete(attempt: EpubInitialLoadAttempt): Boolean {
        if (!isCurrent(attempt)) return false
        activeAttempt = null
        return true
    }

    fun cancel() {
        activeAttempt = null
    }
}

package com.huangder.lumibooks.ui.reader

internal data class EpubInitialLoadAttempt(
    val chapterIndex: Int,
    val generation: Long,
    val retryCount: Int
)

internal enum class EpubInitialLoadFailureReason {
    READER_SCRIPT_MISSING,
    MAIN_FRAME_ERROR,
    HTTP_ERROR,
    PAGE_READY_TIMEOUT
}

internal sealed interface EpubInitialLoadRecoveryAction {
    data object Ignore : EpubInitialLoadRecoveryAction
    data class Retry(val attempt: EpubInitialLoadAttempt) : EpubInitialLoadRecoveryAction
    data class Fallback(val failedAttempt: EpubInitialLoadAttempt) : EpubInitialLoadRecoveryAction
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
            return EpubInitialLoadRecoveryAction.Fallback(attempt)
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

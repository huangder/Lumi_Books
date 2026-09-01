package com.huangder.lumibooks.domain.model

object ReaderFirstOpenHintPolicy {
    const val MAX_ACKNOWLEDGEMENTS = 3

    fun shouldShow(
        isSupportedFormat: Boolean,
        wasShownForBook: Boolean,
        acknowledgementCount: Int,
        disabled: Boolean
    ): Boolean = isSupportedFormat &&
        !wasShownForBook &&
        !disabled &&
        acknowledgementCount < MAX_ACKNOWLEDGEMENTS
}

package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.BookFormat

internal fun readerFirstContentReady(
    state: ReaderUiState,
    continuous: Boolean,
    continuousContentDrawn: Boolean
): Boolean {
    if (state.isLoading) return false
    if (state.error != null) return true
    if (state.pendingReaderPosition != null || state.pendingPageFraction > 0f || !state.pageReady) return false
    // Markdown publishes pageReady after its native viewport has rendered and restored its
    // anchor. The separate draw callback belongs to the Canvas continuous text reader.
    return !continuous || state.book?.format == BookFormat.MARKDOWN || continuousContentDrawn
}

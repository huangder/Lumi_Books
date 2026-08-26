package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat

internal fun Book.isEpubMobi(): Boolean =
    format == BookFormat.EPUB || format == BookFormat.MOBI

package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.BookTagLink
import com.huangder.lumibooks.domain.model.LibraryTag

internal fun Book.isEpubMobi(): Boolean =
    format == BookFormat.EPUB || format == BookFormat.MOBI

/** Bitmap page formats (PDF/CBZ) share the raster reader. */
internal fun Book.isRasterPageFormat(): Boolean = format.isRasterPageFormat

/** A secondary-tag assignment also matches its primary tag. */
internal fun BookTagLink.effectiveTagIds(tagsById: Map<String, LibraryTag>): Set<String> = buildSet {
    add(tagId)
    tagsById[tagId]?.parentId?.let(::add)
}

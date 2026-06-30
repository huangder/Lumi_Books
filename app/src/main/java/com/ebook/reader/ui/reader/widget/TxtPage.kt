/*
 * Adapted from NovelReader (https://github.com/JustWayward/BookReader)
 * Original copyright (c) 2017 GuangXiang Chen, MIT License.
 */
package com.ebook.reader.ui.reader.widget

/** A single page of text content, split by PageLoader */
data class TxtPage(
    val position: Int,         // page index within chapter (0-based)
    val title: String = "",    // chapter title (shown on first page)
    val titleLines: Int = 0,   // number of lines occupied by title
    val lines: List<String>    // text lines for this page
)

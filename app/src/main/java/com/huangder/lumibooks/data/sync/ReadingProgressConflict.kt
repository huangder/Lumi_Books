package com.huangder.lumibooks.data.sync

import org.json.JSONObject

/** Import/discovery timestamps do not represent a user changing the reading position. */
internal fun readingPositionTimestamp(
    lastReadTime: Long,
    createdAt: Long,
    progress: Float,
    locator: String?,
    manuallyRead: Boolean
): Long = if (progress == 0f && locator.isNullOrBlank() && !manuallyRead && lastReadTime <= createdAt) {
    0L
} else lastReadTime

internal suspend fun applySyncedReadingPosition(
    bookRepository: com.huangder.lumibooks.domain.repository.BookRepository,
    bookId: String, root: JSONObject
) {
    val book = bookRepository.getBookById(bookId) ?: return
    if (!root.has("readingProgress")) return
    val progress = root.optDouble("readingProgress", Double.NaN).toFloat()
    if (!progress.isFinite() || progress !in 0f..1f) return
    val locator = if (root.isNull("locatorJson")) null else root.optString("locatorJson")
    val lastReadTime = root.optLong("lastReadTime", 0L)
    val manuallyRead = root.optBoolean("isManuallyMarkedRead", book.isManuallyMarkedRead)
    val remoteCreatedAt = root.optJSONObject("metadata")?.optLong("createdAt") ?: 0L
    val remoteTime = readingPositionTimestamp(lastReadTime, remoteCreatedAt, progress, locator, manuallyRead)
    val localTime = readingPositionTimestamp(
        book.lastReadTime, book.createdAt, book.readingProgress, book.locatorJson, book.isManuallyMarkedRead
    )
    if (remoteTime < localTime || (remoteTime == 0L && localTime == 0L)) return
    bookRepository.updateReadingPosition(bookId, progress, locator)
    val latest = bookRepository.getBookById(bookId) ?: return
    bookRepository.updateBook(latest.copy(lastReadTime = lastReadTime, isManuallyMarkedRead = manuallyRead))
}

package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import java.security.MessageDigest
import java.util.Locale

internal fun Book.webdavFileExtension(): String =
    if (format == BookFormat.MARKDOWN) "mdpack" else format.name.lowercase(Locale.ROOT)

internal fun webdavBookFileName(book: Book, wireId: String, contentDigest: String? = null): String {
    val sourceName = book.sourceDisplayName?.takeIf { it.isNotBlank() }
    val stem = (sourceName?.substringBeforeLast('.') ?: book.title)
        .replace(Regex("""[\x00-\x1f\x7f<>:"/\\|?*]"""), "_")
        .trim(' ', '.')
        .ifEmpty { "Book" }
    // The suffix distinguishes identically named files and stays stable across devices.
    val fileIdentity = if (contentDigest == null) wireId else "$wireId:$contentDigest"
    val suffix = MessageDigest.getInstance("SHA-256").digest(fileIdentity.toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it) }
    // Limit the UTF-8 byte count without splitting a supplementary character.
    var end = 0
    var bytes = 0
    while (end < stem.length) {
        val next = stem.offsetByCodePoints(end, 1)
        bytes += stem.substring(end, next).toByteArray(Charsets.UTF_8).size
        if (bytes > 180) break
        end = next
    }
    return "${stem.substring(0, end)} - $suffix.${book.webdavFileExtension()}"
}

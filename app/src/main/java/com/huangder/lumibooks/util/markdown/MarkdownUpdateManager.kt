package com.huangder.lumibooks.util.markdown

import android.content.Context
import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.bookmarkPositionForCharacterOffset
import com.huangder.lumibooks.util.parser.MarkdownParser
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class MarkdownUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    /** Idempotent upgrade of legacy DOM locators, including records restored by sync. */
    suspend fun migrateNative(book: Book, text: String): Book {
        fun native(raw: String?) = runCatching {
            val json = JSONObject(raw ?: "{}")
            json.optInt("version") == 3 && json.optString("href") == "document.md"
        }.getOrDefault(false)
        val now = System.currentTimeMillis()
        val matcher = MarkdownLocatorMigration.Matcher(text)
        var updated = book
        database.withTransaction {
            val notes = database.noteDao().getNotesByBookId(book.id).first()
            val bookmarks = database.bookmarkDao().getBookmarksByBookId(book.id).first()
            val changedNotes = notes.filter { !native(it.startLocatorJson) && !it.isStale }.map { note ->
                val match = matcher.match(note.selectedText, note.startLocatorJson, note.startPosition)
                if (match == null) note.copy(isStale = true, updatedAt = now)
                else note.copy(chapterIndex = 0, startPosition = match.start, endPosition = match.end,
                    startLocatorJson = MarkdownLocatorMigration.locator(text, match),
                    endLocatorJson = MarkdownLocatorMigration.locator(text, match, true), updatedAt = now)
            }
            if (changedNotes.isNotEmpty()) database.noteDao().updateNotes(changedNotes)
            val changedBookmarks = bookmarks.filter { !native(it.locatorJson) && !it.isStale }.map { bookmark ->
                val quote = runCatching { JSONObject(bookmark.locatorJson ?: "{}").optString("exact") }.getOrDefault("")
                val match = matcher.match(quote, bookmark.locatorJson, 0)
                if (match == null) bookmark.copy(isStale = true, updatedAt = now)
                else bookmark.copy(chapterIndex = 0, position = bookmarkPositionForCharacterOffset(match.start),
                    locatorJson = MarkdownLocatorMigration.locator(text, match), updatedAt = now)
            }
            if (changedBookmarks.isNotEmpty()) database.bookmarkDao().updateBookmarks(changedBookmarks)
            if (!native(book.locatorJson)) {
                val json = runCatching { JSONObject(book.locatorJson ?: "{}") }.getOrDefault(JSONObject())
                val match = matcher.match(json.optString("exact"), book.locatorJson, json.optInt("textPosition"))
                val offset = match?.start ?: (book.readingProgress * text.length).toInt().coerceIn(0, text.length)
                updated = book.copy(locatorJson = MarkdownLocatorMigration.locator(text, MarkdownLocatorMigration.Match(offset, offset)),
                    readingProgress = offset.toFloat() / text.length.coerceAtLeast(1))
                database.bookDao().getBookById(book.id)?.let { entity ->
                    database.bookDao().updateBook(entity.copy(locatorJson = updated.locatorJson, readingProgress = updated.readingProgress))
                }
            }
        }
        return updated
    }

    /** Build and validate off to the side; one DB transaction activates the new version. */
    suspend fun update(book: Book): Book {
        val source = book.sourceUri ?: error("Markdown source is unavailable")
        val snapshot = MarkdownSnapshotStore.create(context, book.id, source, book.sourceParentUri)
        val oldParser = MarkdownParser(context)
        val newParser = MarkdownParser(context)
        try {
            val oldContent = oldParser.parse(book.filePath)
            val newContent = newParser.parse(snapshot.absolutePath)
            val oldText = oldParser.searchText
            val newText = newParser.searchText
            val matcher = MarkdownLocatorMigration.Matcher(newText)
            val now = System.currentTimeMillis()
            fun fallbackQuote(raw: String?, offset: Int): String {
                val exact = runCatching { JSONObject(raw ?: "{}").optString("exact") }.getOrDefault("")
                if (exact.isNotBlank()) return exact
                val safe = offset.coerceIn(0, oldText.length)
                return oldText.substring(safe, (safe + 64).coerceAtMost(oldText.length))
            }
            val saved = runCatching { JSONObject(book.locatorJson ?: "{}") }.getOrDefault(JSONObject())
            val offset = saved.optInt("textPosition", (book.readingProgress * oldText.length).toInt())
            val progressMatch = matcher.match(fallbackQuote(book.locatorJson, offset), book.locatorJson, offset)
            val progressOffset = progressMatch?.start ?: (book.readingProgress * newText.length).toInt().coerceIn(0, newText.length)
            val position = progressMatch ?: MarkdownLocatorMigration.Match(progressOffset, progressOffset)
            val updated = book.copy(filePath = snapshot.absolutePath,
                title = if (book.title == oldContent.title) newContent.title else book.title,
                author = if (book.author == oldContent.author || oldContent.author.isBlank()) newContent.author.ifBlank { book.author } else book.author,
                sourceSha256 = MarkdownSnapshotStore.sha256(snapshot),
                sourceLastModified = MarkdownSnapshotStore.sourceLastModified(context, source),
                locatorJson = MarkdownLocatorMigration.locator(newText, position),
                readingProgress = progressOffset.toFloat() / newText.length.coerceAtLeast(1),
                metadataUpdatedAt = now, lastReadTime = now)
            database.withTransaction {
                val notes = database.noteDao().getNotesByBookId(book.id).first()
                val bookmarks = database.bookmarkDao().getBookmarksByBookId(book.id).first()
                database.noteDao().updateNotes(notes.map { note ->
                    val match = matcher.match(note.selectedText, note.startLocatorJson, note.startPosition)
                    if (match == null) note.copy(isStale = true, updatedAt = now)
                    else note.copy(chapterIndex = 0, startPosition = match.start, endPosition = match.end,
                        startLocatorJson = MarkdownLocatorMigration.locator(newText, match),
                        endLocatorJson = MarkdownLocatorMigration.locator(newText, match, end = true), isStale = false, updatedAt = now)
                })
                database.bookmarkDao().updateBookmarks(bookmarks.map { bookmark ->
                    val old = if (bookmark.position < 0f) (-bookmark.position - 1f).toInt() else (bookmark.position * oldText.length).toInt()
                    val quote = if (bookmark.isStale) runCatching { JSONObject(bookmark.locatorJson ?: "{}").optString("exact") }.getOrDefault("")
                        else fallbackQuote(bookmark.locatorJson, old)
                    val match = matcher.match(quote, bookmark.locatorJson, old)
                    if (match == null) {
                        val retained = runCatching { JSONObject(bookmark.locatorJson ?: "{}") }.getOrDefault(JSONObject())
                        if (retained.optString("exact").isBlank() && quote.isNotBlank()) {
                            retained.put("exact", quote).put("textPosition", old).put("textLength", oldText.length)
                        }
                        bookmark.copy(isStale = true, updatedAt = now, locatorJson = retained.toString())
                    }
                    else bookmark.copy(chapterIndex = 0, position = bookmarkPositionForCharacterOffset(match.start),
                        locatorJson = MarkdownLocatorMigration.locator(newText, match), isStale = false, updatedAt = now)
                })
                val entity = database.bookDao().getBookById(book.id) ?: error("Book was removed")
                database.bookDao().updateBook(entity.copy(filePath = updated.filePath, sourceSha256 = updated.sourceSha256,
                    title = updated.title, author = updated.author, sourceLastModified = updated.sourceLastModified,
                    metadataUpdatedAt = now,
                    readingProgress = updated.readingProgress, locatorJson = updated.locatorJson, lastReadTime = now))
            }
            return updated
        } finally { oldParser.close(); newParser.close() }
    }
}

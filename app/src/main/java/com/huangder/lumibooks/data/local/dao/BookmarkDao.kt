package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/** Cross-book feeds and statistics leave out bookmarks of hidden books. */
private const val BOOKMARK_NOT_IN_HIDDEN_BOOK = "bookId NOT IN (SELECT id FROM books WHERE isHidden = 1)"

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getBookmarksByBookId(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE $BOOKMARK_NOT_IN_HIDDEN_BOOK ORDER BY createdAt DESC, id DESC")
    fun observeAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmarks WHERE createdAt >= :startMs AND createdAt < :endMs " +
            "AND $BOOKMARK_NOT_IN_HIDDEN_BOOK"
    )
    fun observeBookmarksCreatedBetween(startMs: Long, endMs: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT tagsJson FROM bookmarks")
    fun observeTagLists(): Flow<List<String>>

    @Query("UPDATE bookmarks SET tagsJson = :tagsJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTags(id: Long, tagsJson: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Update
    suspend fun updateBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteAllBookmarksByBookId(bookId: String)
}

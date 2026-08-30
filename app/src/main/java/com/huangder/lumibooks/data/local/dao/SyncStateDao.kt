package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.data.local.entity.SyncStateEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.local.entity.TagEntity

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Upsert
    suspend fun putState(state: SyncStateEntity)

    @Query("SELECT * FROM reading_records")
    suspend fun getAllReadingRecords(): List<ReadingRecordEntity>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAllFolders(): List<FolderEntity>

    @Query("SELECT * FROM book_folder_cross_refs")
    suspend fun getAllBookFolderLinks(): List<BookFolderCrossRefEntity>

    @Query("SELECT * FROM tags")
    suspend fun getAllTags(): List<TagEntity>

    @Query("SELECT * FROM book_tag_cross_refs")
    suspend fun getAllBookTagLinks(): List<BookTagCrossRefEntity>

    @Query("SELECT * FROM sync_tombstones")
    suspend fun getAllTombstones(): List<SyncTombstoneEntity>

    @Upsert
    suspend fun upsertReadingRecords(items: List<ReadingRecordEntity>)

    @Upsert
    suspend fun upsertBookmarks(items: List<BookmarkEntity>)

    @Upsert
    suspend fun upsertNotes(items: List<NoteEntity>)

    @Upsert
    suspend fun upsertFolders(items: List<FolderEntity>)

    @Upsert
    suspend fun upsertBookFolderLinks(items: List<BookFolderCrossRefEntity>)

    @Upsert
    suspend fun upsertTags(items: List<TagEntity>)

    @Upsert
    suspend fun upsertBookTagLinks(items: List<BookTagCrossRefEntity>)

    @Upsert
    suspend fun upsertTombstones(items: List<SyncTombstoneEntity>)

    @Query("DELETE FROM reading_records")
    suspend fun clearReadingRecords()

    @Query("DELETE FROM bookmarks")
    suspend fun clearBookmarks()

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM book_folder_cross_refs")
    suspend fun clearBookFolderLinks()

    @Query("DELETE FROM folders")
    suspend fun clearFolders()

    @Query("DELETE FROM book_tag_cross_refs")
    suspend fun clearBookTagLinks()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Query("DELETE FROM sync_tombstones")
    suspend fun clearTombstones()

    @Query("DELETE FROM bookmarks WHERE syncId IN (:syncIds)")
    suspend fun deleteBookmarksBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM notes WHERE syncId IN (:syncIds)")
    suspend fun deleteNotesBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteFoldersByIds(ids: List<String>)

    @Query("DELETE FROM tags WHERE id IN (:ids)")
    suspend fun deleteTagsByIds(ids: List<String>)

    @Query("DELETE FROM book_folder_cross_refs WHERE bookId IN (:bookIds)")
    suspend fun deleteBookFolderLinksByBookIds(bookIds: List<String>)

    @Query("DELETE FROM book_tag_cross_refs WHERE bookId || ':' || tagId IN (:ids)")
    suspend fun deleteBookTagLinksByIds(ids: List<String>)
}

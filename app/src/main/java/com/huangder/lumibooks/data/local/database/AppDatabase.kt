package com.huangder.lumibooks.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.data.local.dao.BookmarkDao
import com.huangder.lumibooks.data.local.dao.FolderDao
import com.huangder.lumibooks.data.local.dao.NoteDao
import com.huangder.lumibooks.data.local.dao.ReadingRecordDao
import com.huangder.lumibooks.data.local.dao.TagDao
import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.BookFolderCrossRefEntity
import com.huangder.lumibooks.data.local.entity.FolderEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.data.local.entity.SyncStateEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleEntity
import com.huangder.lumibooks.data.local.entity.BookHighlightRuleStateEntity
import com.huangder.lumibooks.data.local.entity.BookHighlightSettingsEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleExclusionEntity
import com.huangder.lumibooks.data.local.dao.HighlightRuleDao

@Database(
    entities = [
        BookEntity::class,
        FolderEntity::class,
        BookFolderCrossRefEntity::class,
        TagEntity::class,
        BookTagCrossRefEntity::class,
        ReadingRecordEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        SyncStateEntity::class,
        SyncTombstoneEntity::class,
        HighlightRuleEntity::class,
        BookHighlightRuleStateEntity::class,
        BookHighlightSettingsEntity::class,
        HighlightRuleExclusionEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingRecordDao(): ReadingRecordDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun highlightRuleDao(): HighlightRuleDao
}

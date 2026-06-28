package com.ebook.reader.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ebook.reader.data.local.dao.BookDao
import com.ebook.reader.data.local.dao.BookmarkDao
import com.ebook.reader.data.local.dao.NoteDao
import com.ebook.reader.data.local.dao.ReadingRecordDao
import com.ebook.reader.data.local.entity.BookEntity
import com.ebook.reader.data.local.entity.BookmarkEntity
import com.ebook.reader.data.local.entity.NoteEntity
import com.ebook.reader.data.local.entity.ReadingRecordEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingRecordEntity::class,
        BookmarkEntity::class,
        NoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingRecordDao(): ReadingRecordDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
}

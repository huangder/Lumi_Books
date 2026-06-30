package com.ebook.reader.di

import android.content.Context
import androidx.room.Room
import com.ebook.reader.data.local.DataStoreManager
import com.ebook.reader.data.local.dao.BookDao
import com.ebook.reader.data.local.dao.BookmarkDao
import com.ebook.reader.data.local.dao.NoteDao
import com.ebook.reader.data.local.dao.ReadingRecordDao
import com.ebook.reader.data.local.database.AppDatabase
import com.ebook.reader.data.repository.BookRepositoryImpl
import com.ebook.reader.data.repository.ReadingRepositoryImpl
import com.ebook.reader.domain.repository.BookRepository
import com.ebook.reader.domain.repository.ReadingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ebook_reader_database"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(database: AppDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    @Singleton
    fun provideReadingRecordDao(database: AppDatabase): ReadingRecordDao {
        return database.readingRecordDao()
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: AppDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideBookRepository(bookDao: BookDao): BookRepository {
        return BookRepositoryImpl(bookDao)
    }

    @Provides
    @Singleton
    fun provideReadingRepository(
        readingRecordDao: ReadingRecordDao,
        bookmarkDao: BookmarkDao,
        noteDao: NoteDao
    ): ReadingRepository {
        return ReadingRepositoryImpl(readingRecordDao, bookmarkDao, noteDao)
    }

    @Provides
    @Singleton
    fun provideDataStoreManager(@ApplicationContext context: Context): DataStoreManager {
        return DataStoreManager(context)
    }
}

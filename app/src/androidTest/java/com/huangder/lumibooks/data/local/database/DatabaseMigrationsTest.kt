package com.huangder.lumibooks.data.local.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationsTest {
    private lateinit var context: Context
    private val databaseName = "migration-3-4-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration3To4PreservesLegacyPositionsAndAddsNullableLocators() {
        openHelper(version = 3, createSchema = true).use { helper ->
            helper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO books VALUES " +
                        "('book-1','Title','Author','/book.epub',NULL,'EPUB',10,0.42,5,0)"
                )
                execSQL(
                    "INSERT INTO bookmarks VALUES " +
                        "(7,'book-1',3,0.65,'Bookmark',11)"
                )
                execSQL(
                    "INSERT INTO notes VALUES " +
                        "(9,'book-1',3,12,24,'selected','note','#ffee00',12)"
                )
            }
        }

        openHelper(version = 4, createSchema = false).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT readingProgress, locatorJson FROM books WHERE id='book-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0.42f, cursor.getFloat(0), 0.0001f)
                assertNull(cursor.getString(1))
            }
            db.query("SELECT chapterIndex, position, locatorJson FROM bookmarks WHERE id=7").use { cursor ->
                cursor.moveToFirst()
                assertEquals(3, cursor.getInt(0))
                assertEquals(0.65f, cursor.getFloat(1), 0.0001f)
                assertNull(cursor.getString(2))
            }
            db.query(
                "SELECT startPosition, endPosition, startLocatorJson, endLocatorJson FROM notes WHERE id=9"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(12, cursor.getInt(0))
                assertEquals(24, cursor.getInt(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
            }
        }
    }

    @Test
    fun migration4To6PreservesNotesAndTagsAndAddsCompatibleDefaults() {
        openHelper(version = 4, createSchema = true).use { helper ->
            helper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO notes " +
                        "(id,bookId,chapterIndex,startPosition,endPosition,selectedText,note,color,createdAt) VALUES " +
                        "(9,'book-1',3,12,24,'selected','note','#ffee00',12)"
                )
                execSQL(
                    "INSERT INTO tags VALUES ('tag-1','Fiction','fiction',13)"
                )
            }
        }

        openHelper(version = 6, createSchema = false).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT note, type FROM notes WHERE id=9").use { cursor ->
                cursor.moveToFirst()
                assertEquals("note", cursor.getString(0))
                assertEquals("highlight", cursor.getString(1))
            }
            db.query("SELECT name, parentId FROM tags WHERE id='tag-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Fiction", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        }
    }

    private fun openHelper(version: Int, createSchema: Boolean): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (createSchema) {
                    createVersion3Schema(db)
                    if (version >= 4) DatabaseMigrations.MIGRATION_3_4.migrate(db)
                }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 4 && newVersion >= 4) DatabaseMigrations.MIGRATION_3_4.migrate(db)
                if (oldVersion < 5 && newVersion >= 5) DatabaseMigrations.MIGRATION_4_5.migrate(db)
                if (oldVersion < 6 && newVersion >= 6) DatabaseMigrations.MIGRATION_5_6.migrate(db)
            }
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun createVersion3Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE books (" +
                "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, " +
                "filePath TEXT NOT NULL, coverPath TEXT, format TEXT NOT NULL, " +
                "lastReadTime INTEGER NOT NULL, readingProgress REAL NOT NULL, " +
                "createdAt INTEGER NOT NULL, isFavorite INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE bookmarks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId TEXT NOT NULL, " +
                "chapterIndex INTEGER NOT NULL, position REAL NOT NULL, title TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId TEXT NOT NULL, " +
                "chapterIndex INTEGER NOT NULL, startPosition INTEGER NOT NULL, " +
                "endPosition INTEGER NOT NULL, selectedText TEXT NOT NULL, note TEXT NOT NULL, " +
                "color TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE tags (" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "normalizedName TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX index_tags_normalizedName ON tags (normalizedName)"
        )
    }
}

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
    private val databaseName = "database-migrations-test.db"

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

    @Test
    fun migration6To7PreservesLibraryDataAndLeavesBooksAtRoot() {
        openHelper(version = 6, createSchema = true).use { helper ->
            helper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO books " +
                        "(id,title,author,filePath,coverPath,format,lastReadTime,readingProgress," +
                        "createdAt,isFavorite,locatorJson) VALUES " +
                        "('book-1','Title','Author','/book.epub',NULL,'EPUB',10,0.42,5,1,NULL)"
                )
                execSQL("INSERT INTO tags VALUES ('tag-1','Fiction','fiction',13,NULL)")
                execSQL(
                    "INSERT INTO notes " +
                        "(id,bookId,chapterIndex,startPosition,endPosition,selectedText,note,color," +
                        "createdAt,startLocatorJson,endLocatorJson,type) VALUES " +
                        "(9,'book-1',3,12,24,'selected','note','#ffee00',12,NULL,NULL,'highlight')"
                )
            }
        }

        openHelper(version = 7, createSchema = false).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT title, isFavorite FROM books WHERE id='book-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Title", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            db.query("SELECT name, parentId FROM tags WHERE id='tag-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Fiction", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
            db.query("SELECT note, type FROM notes WHERE id=9").use { cursor ->
                cursor.moveToFirst()
                assertEquals("note", cursor.getString(0))
                assertEquals("highlight", cursor.getString(1))
            }
            db.query("SELECT COUNT(*) FROM folders").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM book_folder_cross_refs").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            assertEquals(
                setOf("index_folders_parentId"),
                indexNames(db, "folders")
            )
            assertEquals(
                setOf("index_book_folder_cross_refs_folderId"),
                indexNames(db, "book_folder_cross_refs")
            )
        }
    }

    @Test
    fun migration7To8PreservesFoldersAndBookOwnershipWithNullCovers() {
        openHelper(version = 7, createSchema = true).use { helper ->
            helper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO books " +
                        "(id,title,author,filePath,coverPath,format,lastReadTime,readingProgress," +
                        "createdAt,isFavorite,locatorJson) VALUES " +
                        "('book-1','Title','Author','/book.epub',NULL,'EPUB',10,0.42,5,0,NULL)"
                )
                execSQL("INSERT INTO folders VALUES ('root','Root','root',NULL,20)")
                execSQL("INSERT INTO folders VALUES ('child','Child','child','root',21)")
                execSQL("INSERT INTO book_folder_cross_refs VALUES ('book-1','child')")
            }
        }

        openHelper(version = 8, createSchema = false).use { helper ->
            val db = helper.writableDatabase
            db.query(
                "SELECT id,name,normalizedName,parentId,coverPath FROM folders ORDER BY createdAt"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("root", cursor.getString(0))
                assertEquals("Root", cursor.getString(1))
                assertEquals("root", cursor.getString(2))
                assertNull(cursor.getString(3))
                assertNull(cursor.getString(4))
                cursor.moveToNext()
                assertEquals("child", cursor.getString(0))
                assertEquals("Child", cursor.getString(1))
                assertEquals("child", cursor.getString(2))
                assertEquals("root", cursor.getString(3))
                assertNull(cursor.getString(4))
            }
            db.query("SELECT folderId FROM book_folder_cross_refs WHERE bookId='book-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("child", cursor.getString(0))
            }
        }
    }

    @Test
    fun migration8To9KeepsLibraryRelationsAndMarksLegacyBooksLocal() {
        openHelper(version = 8, createSchema = true).use { helper ->
            helper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO books " +
                        "(id,title,author,filePath,coverPath,format,lastReadTime,readingProgress," +
                        "createdAt,isFavorite,locatorJson) VALUES " +
                        "('book-1','Title','Author','/book.epub','/cover.jpg','EPUB',10,0.42,5,1,NULL)"
                )
                execSQL("INSERT INTO tags VALUES ('tag-1','Fiction','fiction',13,NULL)")
                execSQL("INSERT INTO book_tag_cross_refs VALUES ('book-1','tag-1')")
                execSQL("INSERT INTO folders VALUES ('folder-1','Folder','folder',NULL,20,'/folder.jpg')")
                execSQL("INSERT INTO book_folder_cross_refs VALUES ('book-1','folder-1')")
                execSQL(
                    "INSERT INTO bookmarks " +
                        "(id,bookId,chapterIndex,position,title,createdAt,locatorJson) VALUES " +
                        "(7,'book-1',3,0.65,'Bookmark',11,NULL)"
                )
            }
        }

        openHelper(version = 9, createSchema = false).use { helper ->
            val db = helper.writableDatabase
            db.query(
                "SELECT isCloudOnly,remoteLibraryKey,remoteFileName,remoteFileSize," +
                    "remoteFileSha256,metadataUpdatedAt FROM books WHERE id='book-1'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
                assertEquals(0L, cursor.getLong(3))
                assertNull(cursor.getString(4))
                assertEquals(5L, cursor.getLong(5))
            }
            db.query("SELECT tagId FROM book_tag_cross_refs WHERE bookId='book-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("tag-1", cursor.getString(0))
            }
            db.query("SELECT folderId FROM book_folder_cross_refs WHERE bookId='book-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("folder-1", cursor.getString(0))
            }
            db.query("SELECT position FROM bookmarks WHERE id=7").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0.65f, cursor.getFloat(0), 0.0001f)
            }
        }
    }

    private fun openHelper(version: Int, createSchema: Boolean): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (createSchema) {
                    createVersion3Schema(db)
                    if (version >= 4) DatabaseMigrations.MIGRATION_3_4.migrate(db)
                    if (version >= 5) DatabaseMigrations.MIGRATION_4_5.migrate(db)
                    if (version >= 6) DatabaseMigrations.MIGRATION_5_6.migrate(db)
                    if (version >= 7) DatabaseMigrations.MIGRATION_6_7.migrate(db)
                    if (version >= 8) DatabaseMigrations.MIGRATION_7_8.migrate(db)
                    if (version >= 9) DatabaseMigrations.MIGRATION_8_9.migrate(db)
                }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 4 && newVersion >= 4) DatabaseMigrations.MIGRATION_3_4.migrate(db)
                if (oldVersion < 5 && newVersion >= 5) DatabaseMigrations.MIGRATION_4_5.migrate(db)
                if (oldVersion < 6 && newVersion >= 6) DatabaseMigrations.MIGRATION_5_6.migrate(db)
                if (oldVersion < 7 && newVersion >= 7) DatabaseMigrations.MIGRATION_6_7.migrate(db)
                if (oldVersion < 8 && newVersion >= 8) DatabaseMigrations.MIGRATION_7_8.migrate(db)
                if (oldVersion < 9 && newVersion >= 9) DatabaseMigrations.MIGRATION_8_9.migrate(db)
            }
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun indexNames(db: SupportSQLiteDatabase, tableName: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (!name.startsWith("sqlite_autoindex_")) result += name
            }
        }
        return result
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
        db.execSQL(
            "CREATE TABLE book_tag_cross_refs (" +
                "bookId TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(bookId, tagId))"
        )
        db.execSQL("CREATE INDEX index_book_tag_cross_refs_tagId ON book_tag_cross_refs (tagId)")
    }
}

package com.huangder.lumibooks.`data`.local.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.huangder.lumibooks.`data`.local.dao.BookDao
import com.huangder.lumibooks.`data`.local.dao.BookDao_Impl
import com.huangder.lumibooks.`data`.local.dao.BookmarkDao
import com.huangder.lumibooks.`data`.local.dao.BookmarkDao_Impl
import com.huangder.lumibooks.`data`.local.dao.NoteDao
import com.huangder.lumibooks.`data`.local.dao.NoteDao_Impl
import com.huangder.lumibooks.`data`.local.dao.ReadingRecordDao
import com.huangder.lumibooks.`data`.local.dao.ReadingRecordDao_Impl
import com.huangder.lumibooks.`data`.local.dao.TagDao
import com.huangder.lumibooks.`data`.local.dao.TagDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _bookDao: Lazy<BookDao> = lazy {
    BookDao_Impl(this)
  }

  private val _readingRecordDao: Lazy<ReadingRecordDao> = lazy {
    ReadingRecordDao_Impl(this)
  }

  private val _bookmarkDao: Lazy<BookmarkDao> = lazy {
    BookmarkDao_Impl(this)
  }

  private val _noteDao: Lazy<NoteDao> = lazy {
    NoteDao_Impl(this)
  }

  private val _tagDao: Lazy<TagDao> = lazy {
    TagDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(3, "c08633e22ee90c468f4d0ecc0266b780", "7ce435dff332f4b64b07c18b49746036") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `filePath` TEXT NOT NULL, `coverPath` TEXT, `format` TEXT NOT NULL, `lastReadTime` INTEGER NOT NULL, `readingProgress` REAL NOT NULL, `createdAt` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalizedName` ON `tags` (`normalizedName`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `book_tag_cross_refs` (`bookId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`bookId`, `tagId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tag_cross_refs_tagId` ON `book_tag_cross_refs` (`tagId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `reading_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` TEXT NOT NULL, `date` TEXT NOT NULL, `duration` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_records_bookId_date` ON `reading_records` (`bookId`, `date`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, `position` REAL NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, `startPosition` INTEGER NOT NULL, `endPosition` INTEGER NOT NULL, `selectedText` TEXT NOT NULL, `note` TEXT NOT NULL, `color` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c08633e22ee90c468f4d0ecc0266b780')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `books`")
        connection.execSQL("DROP TABLE IF EXISTS `tags`")
        connection.execSQL("DROP TABLE IF EXISTS `book_tag_cross_refs`")
        connection.execSQL("DROP TABLE IF EXISTS `reading_records`")
        connection.execSQL("DROP TABLE IF EXISTS `bookmarks`")
        connection.execSQL("DROP TABLE IF EXISTS `notes`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsBooks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBooks.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("author", TableInfo.Column("author", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("filePath", TableInfo.Column("filePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("coverPath", TableInfo.Column("coverPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("format", TableInfo.Column("format", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("lastReadTime", TableInfo.Column("lastReadTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("readingProgress", TableInfo.Column("readingProgress", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("isFavorite", TableInfo.Column("isFavorite", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBooks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBooks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBooks: TableInfo = TableInfo("books", _columnsBooks, _foreignKeysBooks, _indicesBooks)
        val _existingBooks: TableInfo = read(connection, "books")
        if (!_infoBooks.equals(_existingBooks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |books(com.huangder.lumibooks.data.local.entity.BookEntity).
              | Expected:
              |""".trimMargin() + _infoBooks + """
              |
              | Found:
              |""".trimMargin() + _existingBooks)
        }
        val _columnsTags: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTags.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("normalizedName", TableInfo.Column("normalizedName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTags: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTags: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTags.add(TableInfo.Index("index_tags_normalizedName", true, listOf("normalizedName"), listOf("ASC")))
        val _infoTags: TableInfo = TableInfo("tags", _columnsTags, _foreignKeysTags, _indicesTags)
        val _existingTags: TableInfo = read(connection, "tags")
        if (!_infoTags.equals(_existingTags)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tags(com.huangder.lumibooks.data.local.entity.TagEntity).
              | Expected:
              |""".trimMargin() + _infoTags + """
              |
              | Found:
              |""".trimMargin() + _existingTags)
        }
        val _columnsBookTagCrossRefs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBookTagCrossRefs.put("bookId", TableInfo.Column("bookId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookTagCrossRefs.put("tagId", TableInfo.Column("tagId", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBookTagCrossRefs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysBookTagCrossRefs.add(TableInfo.ForeignKey("books", "CASCADE", "NO ACTION", listOf("bookId"), listOf("id")))
        _foreignKeysBookTagCrossRefs.add(TableInfo.ForeignKey("tags", "CASCADE", "NO ACTION", listOf("tagId"), listOf("id")))
        val _indicesBookTagCrossRefs: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesBookTagCrossRefs.add(TableInfo.Index("index_book_tag_cross_refs_tagId", false, listOf("tagId"), listOf("ASC")))
        val _infoBookTagCrossRefs: TableInfo = TableInfo("book_tag_cross_refs", _columnsBookTagCrossRefs, _foreignKeysBookTagCrossRefs, _indicesBookTagCrossRefs)
        val _existingBookTagCrossRefs: TableInfo = read(connection, "book_tag_cross_refs")
        if (!_infoBookTagCrossRefs.equals(_existingBookTagCrossRefs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |book_tag_cross_refs(com.huangder.lumibooks.data.local.entity.BookTagCrossRefEntity).
              | Expected:
              |""".trimMargin() + _infoBookTagCrossRefs + """
              |
              | Found:
              |""".trimMargin() + _existingBookTagCrossRefs)
        }
        val _columnsReadingRecords: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsReadingRecords.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsReadingRecords.put("bookId", TableInfo.Column("bookId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsReadingRecords.put("date", TableInfo.Column("date", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsReadingRecords.put("duration", TableInfo.Column("duration", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsReadingRecords.put("startTime", TableInfo.Column("startTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsReadingRecords.put("endTime", TableInfo.Column("endTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysReadingRecords: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesReadingRecords: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesReadingRecords.add(TableInfo.Index("index_reading_records_bookId_date", true, listOf("bookId", "date"), listOf("ASC", "ASC")))
        val _infoReadingRecords: TableInfo = TableInfo("reading_records", _columnsReadingRecords, _foreignKeysReadingRecords, _indicesReadingRecords)
        val _existingReadingRecords: TableInfo = read(connection, "reading_records")
        if (!_infoReadingRecords.equals(_existingReadingRecords)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |reading_records(com.huangder.lumibooks.data.local.entity.ReadingRecordEntity).
              | Expected:
              |""".trimMargin() + _infoReadingRecords + """
              |
              | Found:
              |""".trimMargin() + _existingReadingRecords)
        }
        val _columnsBookmarks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBookmarks.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("bookId", TableInfo.Column("bookId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("chapterIndex", TableInfo.Column("chapterIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("position", TableInfo.Column("position", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBookmarks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBookmarks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBookmarks: TableInfo = TableInfo("bookmarks", _columnsBookmarks, _foreignKeysBookmarks, _indicesBookmarks)
        val _existingBookmarks: TableInfo = read(connection, "bookmarks")
        if (!_infoBookmarks.equals(_existingBookmarks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |bookmarks(com.huangder.lumibooks.data.local.entity.BookmarkEntity).
              | Expected:
              |""".trimMargin() + _infoBookmarks + """
              |
              | Found:
              |""".trimMargin() + _existingBookmarks)
        }
        val _columnsNotes: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsNotes.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("bookId", TableInfo.Column("bookId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("chapterIndex", TableInfo.Column("chapterIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("startPosition", TableInfo.Column("startPosition", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("endPosition", TableInfo.Column("endPosition", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("selectedText", TableInfo.Column("selectedText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("note", TableInfo.Column("note", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("color", TableInfo.Column("color", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotes.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysNotes: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesNotes: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoNotes: TableInfo = TableInfo("notes", _columnsNotes, _foreignKeysNotes, _indicesNotes)
        val _existingNotes: TableInfo = read(connection, "notes")
        if (!_infoNotes.equals(_existingNotes)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |notes(com.huangder.lumibooks.data.local.entity.NoteEntity).
              | Expected:
              |""".trimMargin() + _infoNotes + """
              |
              | Found:
              |""".trimMargin() + _existingNotes)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "books", "tags", "book_tag_cross_refs", "reading_records", "bookmarks", "notes")
  }

  public override fun clearAllTables() {
    super.performClear(true, "books", "tags", "book_tag_cross_refs", "reading_records", "bookmarks", "notes")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(BookDao::class, BookDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ReadingRecordDao::class, ReadingRecordDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BookmarkDao::class, BookmarkDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(NoteDao::class, NoteDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TagDao::class, TagDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun bookDao(): BookDao = _bookDao.value

  public override fun readingRecordDao(): ReadingRecordDao = _readingRecordDao.value

  public override fun bookmarkDao(): BookmarkDao = _bookmarkDao.value

  public override fun noteDao(): NoteDao = _noteDao.value

  public override fun tagDao(): TagDao = _tagDao.value
}

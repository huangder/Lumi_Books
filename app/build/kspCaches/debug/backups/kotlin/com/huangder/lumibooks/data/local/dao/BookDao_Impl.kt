package com.huangder.lumibooks.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.huangder.lumibooks.`data`.local.entity.BookEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class BookDao_Impl(
  __db: RoomDatabase,
) : BookDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBookEntity: EntityInsertAdapter<BookEntity>

  private val __deleteAdapterOfBookEntity: EntityDeleteOrUpdateAdapter<BookEntity>

  private val __updateAdapterOfBookEntity: EntityDeleteOrUpdateAdapter<BookEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfBookEntity = object : EntityInsertAdapter<BookEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `books` (`id`,`title`,`author`,`filePath`,`coverPath`,`format`,`lastReadTime`,`readingProgress`,`createdAt`,`isFavorite`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BookEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.author)
        statement.bindText(4, entity.filePath)
        val _tmpCoverPath: String? = entity.coverPath
        if (_tmpCoverPath == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpCoverPath)
        }
        statement.bindText(6, entity.format)
        statement.bindLong(7, entity.lastReadTime)
        statement.bindDouble(8, entity.readingProgress.toDouble())
        statement.bindLong(9, entity.createdAt)
        val _tmp: Int = if (entity.isFavorite) 1 else 0
        statement.bindLong(10, _tmp.toLong())
      }
    }
    this.__deleteAdapterOfBookEntity = object : EntityDeleteOrUpdateAdapter<BookEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `books` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: BookEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__updateAdapterOfBookEntity = object : EntityDeleteOrUpdateAdapter<BookEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `books` SET `id` = ?,`title` = ?,`author` = ?,`filePath` = ?,`coverPath` = ?,`format` = ?,`lastReadTime` = ?,`readingProgress` = ?,`createdAt` = ?,`isFavorite` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: BookEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.author)
        statement.bindText(4, entity.filePath)
        val _tmpCoverPath: String? = entity.coverPath
        if (_tmpCoverPath == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpCoverPath)
        }
        statement.bindText(6, entity.format)
        statement.bindLong(7, entity.lastReadTime)
        statement.bindDouble(8, entity.readingProgress.toDouble())
        statement.bindLong(9, entity.createdAt)
        val _tmp: Int = if (entity.isFavorite) 1 else 0
        statement.bindLong(10, _tmp.toLong())
        statement.bindText(11, entity.id)
      }
    }
  }

  public override suspend fun insertBook(book: BookEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBookEntity.insert(_connection, book)
  }

  public override suspend fun deleteBook(book: BookEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfBookEntity.handle(_connection, book)
  }

  public override suspend fun updateBook(book: BookEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfBookEntity.handle(_connection, book)
  }

  public override fun getAllBooks(): Flow<List<BookEntity>> {
    val _sql: String = "SELECT * FROM books ORDER BY lastReadTime DESC"
    return createFlow(__db, false, arrayOf("books")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCoverPath: Int = getColumnIndexOrThrow(_stmt, "coverPath")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfLastReadTime: Int = getColumnIndexOrThrow(_stmt, "lastReadTime")
        val _columnIndexOfReadingProgress: Int = getColumnIndexOrThrow(_stmt, "readingProgress")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _result: MutableList<BookEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpFilePath: String
          _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          val _tmpCoverPath: String?
          if (_stmt.isNull(_columnIndexOfCoverPath)) {
            _tmpCoverPath = null
          } else {
            _tmpCoverPath = _stmt.getText(_columnIndexOfCoverPath)
          }
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpLastReadTime: Long
          _tmpLastReadTime = _stmt.getLong(_columnIndexOfLastReadTime)
          val _tmpReadingProgress: Float
          _tmpReadingProgress = _stmt.getDouble(_columnIndexOfReadingProgress).toFloat()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          _item = BookEntity(_tmpId,_tmpTitle,_tmpAuthor,_tmpFilePath,_tmpCoverPath,_tmpFormat,_tmpLastReadTime,_tmpReadingProgress,_tmpCreatedAt,_tmpIsFavorite)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getBookById(bookId: String): BookEntity? {
    val _sql: String = "SELECT * FROM books WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCoverPath: Int = getColumnIndexOrThrow(_stmt, "coverPath")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfLastReadTime: Int = getColumnIndexOrThrow(_stmt, "lastReadTime")
        val _columnIndexOfReadingProgress: Int = getColumnIndexOrThrow(_stmt, "readingProgress")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _result: BookEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpFilePath: String
          _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          val _tmpCoverPath: String?
          if (_stmt.isNull(_columnIndexOfCoverPath)) {
            _tmpCoverPath = null
          } else {
            _tmpCoverPath = _stmt.getText(_columnIndexOfCoverPath)
          }
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpLastReadTime: Long
          _tmpLastReadTime = _stmt.getLong(_columnIndexOfLastReadTime)
          val _tmpReadingProgress: Float
          _tmpReadingProgress = _stmt.getDouble(_columnIndexOfReadingProgress).toFloat()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          _result = BookEntity(_tmpId,_tmpTitle,_tmpAuthor,_tmpFilePath,_tmpCoverPath,_tmpFormat,_tmpLastReadTime,_tmpReadingProgress,_tmpCreatedAt,_tmpIsFavorite)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun searchBooks(query: String): Flow<List<BookEntity>> {
    val _sql: String = "SELECT * FROM books WHERE title LIKE '%' || ? || '%' OR author LIKE '%' || ? || '%'"
    return createFlow(__db, false, arrayOf("books")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCoverPath: Int = getColumnIndexOrThrow(_stmt, "coverPath")
        val _columnIndexOfFormat: Int = getColumnIndexOrThrow(_stmt, "format")
        val _columnIndexOfLastReadTime: Int = getColumnIndexOrThrow(_stmt, "lastReadTime")
        val _columnIndexOfReadingProgress: Int = getColumnIndexOrThrow(_stmt, "readingProgress")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _result: MutableList<BookEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpFilePath: String
          _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          val _tmpCoverPath: String?
          if (_stmt.isNull(_columnIndexOfCoverPath)) {
            _tmpCoverPath = null
          } else {
            _tmpCoverPath = _stmt.getText(_columnIndexOfCoverPath)
          }
          val _tmpFormat: String
          _tmpFormat = _stmt.getText(_columnIndexOfFormat)
          val _tmpLastReadTime: Long
          _tmpLastReadTime = _stmt.getLong(_columnIndexOfLastReadTime)
          val _tmpReadingProgress: Float
          _tmpReadingProgress = _stmt.getDouble(_columnIndexOfReadingProgress).toFloat()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          _item = BookEntity(_tmpId,_tmpTitle,_tmpAuthor,_tmpFilePath,_tmpCoverPath,_tmpFormat,_tmpLastReadTime,_tmpReadingProgress,_tmpCreatedAt,_tmpIsFavorite)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateLastReadTime(bookId: String, timestamp: Long) {
    val _sql: String = "UPDATE books SET lastReadTime = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, timestamp)
        _argIndex = 2
        _stmt.bindText(_argIndex, bookId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateReadingProgress(bookId: String, progress: Float) {
    val _sql: String = "UPDATE books SET readingProgress = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindDouble(_argIndex, progress.toDouble())
        _argIndex = 2
        _stmt.bindText(_argIndex, bookId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}

package com.huangder.lumibooks.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.huangder.lumibooks.`data`.local.entity.BookTagCrossRefEntity
import com.huangder.lumibooks.`data`.local.entity.TagEntity
import javax.`annotation`.processing.Generated
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
public class TagDao_Impl(
  __db: RoomDatabase,
) : TagDao() {
  private val __db: RoomDatabase

  private val __insertAdapterOfTagEntity: EntityInsertAdapter<TagEntity>

  private val __insertAdapterOfBookTagCrossRefEntity: EntityInsertAdapter<BookTagCrossRefEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfTagEntity = object : EntityInsertAdapter<TagEntity>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `tags` (`id`,`name`,`normalizedName`,`createdAt`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TagEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.normalizedName)
        statement.bindLong(4, entity.createdAt)
      }
    }
    this.__insertAdapterOfBookTagCrossRefEntity = object : EntityInsertAdapter<BookTagCrossRefEntity>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `book_tag_cross_refs` (`bookId`,`tagId`) VALUES (?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BookTagCrossRefEntity) {
        statement.bindText(1, entity.bookId)
        statement.bindText(2, entity.tagId)
      }
    }
  }

  public override suspend fun insertTag(tag: TagEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfTagEntity.insertAndReturnId(_connection, tag)
    _result
  }

  public override suspend fun insertBookTagLink(link: BookTagCrossRefEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBookTagCrossRefEntity.insert(_connection, link)
  }

  public override suspend fun createAndAssignTag(bookId: String, tag: TagEntity): TagEntity = performInTransactionSuspending(__db) {
    super@TagDao_Impl.createAndAssignTag(bookId, tag)
  }

  public override fun getAllTags(): Flow<List<TagEntity>> {
    val _sql: String = "SELECT * FROM tags ORDER BY createdAt ASC"
    return createFlow(__db, false, arrayOf("tags")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfNormalizedName: Int = getColumnIndexOrThrow(_stmt, "normalizedName")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<TagEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TagEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpNormalizedName: String
          _tmpNormalizedName = _stmt.getText(_columnIndexOfNormalizedName)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = TagEntity(_tmpId,_tmpName,_tmpNormalizedName,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllBookTagLinks(): Flow<List<BookTagCrossRefEntity>> {
    val _sql: String = "SELECT * FROM book_tag_cross_refs"
    return createFlow(__db, false, arrayOf("book_tag_cross_refs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfTagId: Int = getColumnIndexOrThrow(_stmt, "tagId")
        val _result: MutableList<BookTagCrossRefEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookTagCrossRefEntity
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpTagId: String
          _tmpTagId = _stmt.getText(_columnIndexOfTagId)
          _item = BookTagCrossRefEntity(_tmpBookId,_tmpTagId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTagByNormalizedName(normalizedName: String): TagEntity? {
    val _sql: String = "SELECT * FROM tags WHERE normalizedName = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, normalizedName)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfNormalizedName: Int = getColumnIndexOrThrow(_stmt, "normalizedName")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: TagEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpNormalizedName: String
          _tmpNormalizedName = _stmt.getText(_columnIndexOfNormalizedName)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = TagEntity(_tmpId,_tmpName,_tmpNormalizedName,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteBookTagLink(bookId: String, tagId: String) {
    val _sql: String = "DELETE FROM book_tag_cross_refs WHERE bookId = ? AND tagId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, tagId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateTagName(
    tagId: String,
    name: String,
    normalizedName: String,
  ) {
    val _sql: String = "UPDATE tags SET name = ?, normalizedName = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, name)
        _argIndex = 2
        _stmt.bindText(_argIndex, normalizedName)
        _argIndex = 3
        _stmt.bindText(_argIndex, tagId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteTag(tagId: String) {
    val _sql: String = "DELETE FROM tags WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, tagId)
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

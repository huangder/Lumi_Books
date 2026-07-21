package com.huangder.lumibooks.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.huangder.lumibooks.`data`.local.entity.NoteEntity
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
public class NoteDao_Impl(
  __db: RoomDatabase,
) : NoteDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfNoteEntity: EntityInsertAdapter<NoteEntity>

  private val __deleteAdapterOfNoteEntity: EntityDeleteOrUpdateAdapter<NoteEntity>

  private val __updateAdapterOfNoteEntity: EntityDeleteOrUpdateAdapter<NoteEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfNoteEntity = object : EntityInsertAdapter<NoteEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `notes` (`id`,`bookId`,`chapterIndex`,`startPosition`,`endPosition`,`selectedText`,`note`,`color`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: NoteEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.bookId)
        statement.bindLong(3, entity.chapterIndex.toLong())
        statement.bindLong(4, entity.startPosition.toLong())
        statement.bindLong(5, entity.endPosition.toLong())
        statement.bindText(6, entity.selectedText)
        statement.bindText(7, entity.note)
        statement.bindText(8, entity.color)
        statement.bindLong(9, entity.createdAt)
      }
    }
    this.__deleteAdapterOfNoteEntity = object : EntityDeleteOrUpdateAdapter<NoteEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `notes` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: NoteEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfNoteEntity = object : EntityDeleteOrUpdateAdapter<NoteEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `notes` SET `id` = ?,`bookId` = ?,`chapterIndex` = ?,`startPosition` = ?,`endPosition` = ?,`selectedText` = ?,`note` = ?,`color` = ?,`createdAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: NoteEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.bookId)
        statement.bindLong(3, entity.chapterIndex.toLong())
        statement.bindLong(4, entity.startPosition.toLong())
        statement.bindLong(5, entity.endPosition.toLong())
        statement.bindText(6, entity.selectedText)
        statement.bindText(7, entity.note)
        statement.bindText(8, entity.color)
        statement.bindLong(9, entity.createdAt)
        statement.bindLong(10, entity.id)
      }
    }
  }

  public override suspend fun insertNote(note: NoteEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfNoteEntity.insert(_connection, note)
  }

  public override suspend fun deleteNote(note: NoteEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfNoteEntity.handle(_connection, note)
  }

  public override suspend fun updateNote(note: NoteEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfNoteEntity.handle(_connection, note)
  }

  public override fun getNotesByBookId(bookId: String): Flow<List<NoteEntity>> {
    val _sql: String = "SELECT * FROM notes WHERE bookId = ? ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("notes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfChapterIndex: Int = getColumnIndexOrThrow(_stmt, "chapterIndex")
        val _columnIndexOfStartPosition: Int = getColumnIndexOrThrow(_stmt, "startPosition")
        val _columnIndexOfEndPosition: Int = getColumnIndexOrThrow(_stmt, "endPosition")
        val _columnIndexOfSelectedText: Int = getColumnIndexOrThrow(_stmt, "selectedText")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<NoteEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: NoteEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpChapterIndex: Int
          _tmpChapterIndex = _stmt.getLong(_columnIndexOfChapterIndex).toInt()
          val _tmpStartPosition: Int
          _tmpStartPosition = _stmt.getLong(_columnIndexOfStartPosition).toInt()
          val _tmpEndPosition: Int
          _tmpEndPosition = _stmt.getLong(_columnIndexOfEndPosition).toInt()
          val _tmpSelectedText: String
          _tmpSelectedText = _stmt.getText(_columnIndexOfSelectedText)
          val _tmpNote: String
          _tmpNote = _stmt.getText(_columnIndexOfNote)
          val _tmpColor: String
          _tmpColor = _stmt.getText(_columnIndexOfColor)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = NoteEntity(_tmpId,_tmpBookId,_tmpChapterIndex,_tmpStartPosition,_tmpEndPosition,_tmpSelectedText,_tmpNote,_tmpColor,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllNotesByBookId(bookId: String) {
    val _sql: String = "DELETE FROM notes WHERE bookId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
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

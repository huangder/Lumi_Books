package com.huangder.lumibooks.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.huangder.lumibooks.`data`.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.domain.model.DailyTotal
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
public class ReadingRecordDao_Impl(
  __db: RoomDatabase,
) : ReadingRecordDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfReadingRecordEntity: EntityInsertAdapter<ReadingRecordEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfReadingRecordEntity = object : EntityInsertAdapter<ReadingRecordEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `reading_records` (`id`,`bookId`,`date`,`duration`,`startTime`,`endTime`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ReadingRecordEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.bookId)
        statement.bindText(3, entity.date)
        statement.bindLong(4, entity.duration)
        statement.bindLong(5, entity.startTime)
        statement.bindLong(6, entity.endTime)
      }
    }
  }

  public override suspend fun insertRecord(record: ReadingRecordEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfReadingRecordEntity.insert(_connection, record)
  }

  public override fun getRecordsByDate(date: String): Flow<List<ReadingRecordEntity>> {
    val _sql: String = "SELECT * FROM reading_records WHERE date = ?"
    return createFlow(__db, false, arrayOf("reading_records")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _result: MutableList<ReadingRecordEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ReadingRecordEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          _item = ReadingRecordEntity(_tmpId,_tmpBookId,_tmpDate,_tmpDuration,_tmpStartTime,_tmpEndTime)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRecordsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingRecordEntity>> {
    val _sql: String = "SELECT * FROM reading_records WHERE date BETWEEN ? AND ?"
    return createFlow(__db, false, arrayOf("reading_records")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, startDate)
        _argIndex = 2
        _stmt.bindText(_argIndex, endDate)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _result: MutableList<ReadingRecordEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ReadingRecordEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          _item = ReadingRecordEntity(_tmpId,_tmpBookId,_tmpDate,_tmpDuration,_tmpStartTime,_tmpEndTime)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getTotalDurationByDate(date: String): Flow<Long?> {
    val _sql: String = "SELECT SUM(duration) FROM reading_records WHERE date = ?"
    return createFlow(__db, false, arrayOf("reading_records")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
        val _result: Long?
        if (_stmt.step()) {
          val _tmp: Long?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(0)
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getTotalDurationBetweenDates(startDate: String, endDate: String): Flow<Long?> {
    val _sql: String = "SELECT SUM(duration) FROM reading_records WHERE date BETWEEN ? AND ?"
    return createFlow(__db, false, arrayOf("reading_records")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, startDate)
        _argIndex = 2
        _stmt.bindText(_argIndex, endDate)
        val _result: Long?
        if (_stmt.step()) {
          val _tmp: Long?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(0)
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getRecordByBookAndDate(bookId: String, date: String): ReadingRecordEntity? {
    val _sql: String = "SELECT * FROM reading_records WHERE bookId = ? AND date = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, bookId)
        _argIndex = 2
        _stmt.bindText(_argIndex, date)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "bookId")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _result: ReadingRecordEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          _result = ReadingRecordEntity(_tmpId,_tmpBookId,_tmpDate,_tmpDuration,_tmpStartTime,_tmpEndTime)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getMostReadBooks(limit: Int): Flow<List<BookDuration>> {
    val _sql: String = "SELECT bookId, SUM(duration) as totalDuration FROM reading_records GROUP BY bookId ORDER BY totalDuration DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("reading_records")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfBookId: Int = 0
        val _columnIndexOfTotalDuration: Int = 1
        val _result: MutableList<BookDuration> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookDuration
          val _tmpBookId: String
          _tmpBookId = _stmt.getText(_columnIndexOfBookId)
          val _tmpTotalDuration: Long
          _tmpTotalDuration = _stmt.getLong(_columnIndexOfTotalDuration)
          _item = BookDuration(_tmpBookId,_tmpTotalDuration)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getDailyTotalsBetween(startDate: String, endDate: String): Flow<List<DailyTotal>> {
    val _sql: String = "SELECT date, SUM(duration) as totalDuration FROM reading_records WHERE date BETWEEN ? AND ? GROUP BY date"
    return createFlow(__db, false, arrayOf("reading_records")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, startDate)
        _argIndex = 2
        _stmt.bindText(_argIndex, endDate)
        val _columnIndexOfDate: Int = 0
        val _columnIndexOfTotalDuration: Int = 1
        val _result: MutableList<DailyTotal> = mutableListOf()
        while (_stmt.step()) {
          val _item: DailyTotal
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpTotalDuration: Long
          _tmpTotalDuration = _stmt.getLong(_columnIndexOfTotalDuration)
          _item = DailyTotal(_tmpDate,_tmpTotalDuration)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateRecordDuration(
    recordId: Long,
    additionalDuration: Long,
    endTime: Long,
  ) {
    val _sql: String = "UPDATE reading_records SET duration = duration + ?, endTime = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, additionalDuration)
        _argIndex = 2
        _stmt.bindLong(_argIndex, endTime)
        _argIndex = 3
        _stmt.bindLong(_argIndex, recordId)
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

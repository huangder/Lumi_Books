package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReadingJournalMigrationTest {
    @Test fun addsToReadMarksHighlightPositionsAndTheJournalTable() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE books (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO books VALUES ('b','Title')")
            db.execSQL("INSERT INTO notes (bookId) VALUES ('b')")
            DatabaseMigrations.MIGRATION_27_28.migrate(db)
            db.query("SELECT wantToReadAt FROM books").use { assertTrue(it.moveToFirst()); assertEquals(0L, it.getLong(0)) }
            db.query("SELECT bookProgress FROM notes").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
            db.execSQL(
                "INSERT INTO reading_journal_events (id,type,bookId,startedAt,endedAt,durationMs,updatedAt) " +
                    "VALUES ('e','session','b',1,2,1,2)"
            )
            db.query("SELECT listening, sourceDeviceId FROM reading_journal_events").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals("", it.getString(1))
            }
        }
    }
}

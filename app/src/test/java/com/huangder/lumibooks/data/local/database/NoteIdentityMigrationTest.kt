package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NoteIdentityMigrationTest {
    @Test fun migrationPreservesEmptyHighlightsAndRecoversLegacyNotes() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, note TEXT NOT NULL, type TEXT NOT NULL, startPosition INTEGER NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO notes VALUES (1,'','highlight',42),(2,'comment','underline',72),(3,'','note',93)")
            DatabaseMigrations.MIGRATION_12_13.migrate(db)
            db.query("SELECT isNote,startPosition FROM notes ORDER BY id").use { cursor ->
                for ((flag, position) in listOf(0 to 42, 1 to 72, 1 to 93)) {
                    cursor.moveToNext()
                    assertEquals(flag, cursor.getInt(0))
                    assertEquals(position, cursor.getInt(1))
                }
            }
        }
    }
}

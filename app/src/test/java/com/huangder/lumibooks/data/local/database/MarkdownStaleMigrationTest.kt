package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkdownStaleMigrationTest {
    @Test fun migrationKeepsExistingAnnotationsAndDefaultsToActive() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, selectedText TEXT NOT NULL, note TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO notes VALUES (1, 'Excerpt', 'User note')")
            db.execSQL("INSERT INTO bookmarks VALUES (1, 'Place')")
            DatabaseMigrations.MIGRATION_18_19.migrate(db)
            db.query("SELECT selectedText, note, isStale FROM notes").use {
                it.moveToFirst()
                assertEquals("Excerpt", it.getString(0))
                assertEquals("User note", it.getString(1))
                assertEquals(0, it.getInt(2))
            }
            db.query("SELECT title, isStale FROM bookmarks").use {
                it.moveToFirst()
                assertEquals("Place", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
        }
    }
}

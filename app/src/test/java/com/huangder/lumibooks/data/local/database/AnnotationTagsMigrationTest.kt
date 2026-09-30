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
class AnnotationTagsMigrationTest {
    @Test fun addsEmptyTagsWithoutChangingOldAnnotations() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, selectedText TEXT NOT NULL, sourceMatchKey TEXT)")
                    db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO notes VALUES (1, 'Excerpt', 'stable-match')")
            db.execSQL("INSERT INTO bookmarks VALUES (1, 'Chapter')")
            DatabaseMigrations.MIGRATION_17_18.migrate(db)
            db.query("SELECT selectedText, sourceMatchKey, tagsJson FROM notes").use {
                it.moveToFirst()
                assertEquals("Excerpt", it.getString(0))
                assertEquals("stable-match", it.getString(1))
                assertEquals("[]", it.getString(2))
            }
            db.query("SELECT title, tagsJson FROM bookmarks").use {
                it.moveToFirst()
                assertEquals("Chapter", it.getString(0))
                assertEquals("[]", it.getString(1))
            }
        }
    }
}

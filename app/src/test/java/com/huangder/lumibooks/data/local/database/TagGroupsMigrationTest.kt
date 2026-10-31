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
class TagGroupsMigrationTest {
    @Test fun flatteningPreservesExplicitAndImplicitBookLabels() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE tags (id TEXT PRIMARY KEY NOT NULL, parentId TEXT)")
                    db.execSQL("CREATE TABLE annotation_tags (name TEXT PRIMARY KEY NOT NULL)")
                    db.execSQL("CREATE TABLE book_tag_cross_refs (bookId TEXT NOT NULL, tagId TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(bookId,tagId))")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO tags VALUES ('parent',NULL),('child','parent'),('orphan','missing')")
            db.execSQL("INSERT INTO annotation_tags VALUES ('Annotation')")
            db.execSQL("INSERT INTO book_tag_cross_refs VALUES ('book','child',12),('book','parent',10),('other','child',14),('other','orphan',15)")
            DatabaseMigrations.MIGRATION_25_26.migrate(db)
            db.query("SELECT bookId,tagId FROM book_tag_cross_refs").use { cursor ->
                val links = mutableSetOf<String>()
                while (cursor.moveToNext()) links += "${cursor.getString(0)}:${cursor.getString(1)}"
                assertEquals(setOf("book:child", "book:parent", "other:child", "other:parent", "other:orphan"), links)
            }
            db.query("SELECT parentId,groupId FROM tags").use { cursor ->
                while (cursor.moveToNext()) { assertTrue(cursor.isNull(0)); assertTrue(cursor.isNull(1)) }
            }
            db.query("SELECT groupId FROM annotation_tags").use { cursor -> cursor.moveToFirst(); assertTrue(cursor.isNull(0)) }
            db.query("SELECT COUNT(*) FROM tag_groups").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        }
    }
}

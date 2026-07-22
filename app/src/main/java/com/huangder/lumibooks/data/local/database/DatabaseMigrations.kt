package com.huangder.lumibooks.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tags` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`normalizedName` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalizedName` " +
                    "ON `tags` (`normalizedName`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `book_tag_cross_refs` (" +
                    "`bookId` TEXT NOT NULL, " +
                    "`tagId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`bookId`, `tagId`), " +
                    "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_tag_cross_refs_tagId` " +
                    "ON `book_tag_cross_refs` (`tagId`)"
            )
        }
    }
}

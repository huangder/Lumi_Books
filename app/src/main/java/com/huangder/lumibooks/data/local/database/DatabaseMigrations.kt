package com.huangder.lumibooks.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        }
    }

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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN locatorJson TEXT")
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN locatorJson TEXT")
            db.execSQL("ALTER TABLE notes ADD COLUMN startLocatorJson TEXT")
            db.execSQL("ALTER TABLE notes ADD COLUMN endLocatorJson TEXT")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN type TEXT NOT NULL DEFAULT 'highlight'")
        }
    }
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tags ADD COLUMN parentId TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, " +
                    "`parentId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`parentId`) REFERENCES `folders`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `book_folder_cross_refs` (" +
                    "`bookId` TEXT NOT NULL, `folderId` TEXT NOT NULL, PRIMARY KEY(`bookId`), " +
                    "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_folder_cross_refs_folderId` " +
                    "ON `book_folder_cross_refs` (`folderId`)"
            )
        }
    }
}

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

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN coverPath TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN isCloudOnly INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE books ADD COLUMN remoteLibraryKey TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE books ADD COLUMN remoteFileName TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE books ADD COLUMN remoteFileSize INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE books ADD COLUMN remoteFileSha256 TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE books ADD COLUMN metadataUpdatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE books SET metadataUpdatedAt = createdAt WHERE metadataUpdatedAt = 0")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN previewBookIds TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_state (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO sync_state (`key`, value) VALUES " +
                    "('device_id', lower(hex(randomblob(16))))"
            )
            db.execSQL(
                "CREATE TABLE reading_records_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId TEXT NOT NULL, " +
                    "date TEXT NOT NULL, duration INTEGER NOT NULL, startTime INTEGER NOT NULL, " +
                    "endTime INTEGER NOT NULL, sourceDeviceId TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO reading_records_new " +
                    "(id,bookId,date,duration,startTime,endTime,sourceDeviceId,updatedAt) " +
                    "SELECT id,bookId,date,duration,startTime,endTime," +
                    "(SELECT value FROM sync_state WHERE `key`='device_id'),endTime FROM reading_records"
            )
            db.execSQL("DROP TABLE reading_records")
            db.execSQL("ALTER TABLE reading_records_new RENAME TO reading_records")
            db.execSQL(
                "CREATE UNIQUE INDEX index_reading_records_bookId_date_sourceDeviceId " +
                    "ON reading_records (bookId, date, sourceDeviceId)"
            )

            addStableSyncColumns(db, "bookmarks")
            addStableSyncColumns(db, "notes")
            db.execSQL("CREATE UNIQUE INDEX index_bookmarks_syncId ON bookmarks (syncId)")
            db.execSQL("CREATE UNIQUE INDEX index_notes_syncId ON notes (syncId)")

            db.execSQL("ALTER TABLE folders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE folders SET updatedAt = createdAt WHERE updatedAt = 0")
            db.execSQL("ALTER TABLE tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE tags SET updatedAt = createdAt WHERE updatedAt = 0")
            db.execSQL("ALTER TABLE book_folder_cross_refs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE book_folder_cross_refs SET updatedAt = " +
                    "CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE updatedAt = 0"
            )
            db.execSQL("ALTER TABLE book_tag_cross_refs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE book_tag_cross_refs SET updatedAt = " +
                    "CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE updatedAt = 0"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_tombstones (" +
                    "namespace TEXT NOT NULL, itemId TEXT NOT NULL, deletedAt INTEGER NOT NULL, " +
                    "deviceId TEXT NOT NULL, PRIMARY KEY(namespace, itemId))"
            )
        }

        private fun addStableSyncColumns(db: SupportSQLiteDatabase, table: String) {
            db.execSQL("ALTER TABLE $table ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE $table SET syncId = lower(hex(randomblob(16))), " +
                    "updatedAt = createdAt WHERE syncId = ''"
            )
        }
    }
}

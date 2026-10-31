package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.data.repository.ReadingRepositoryImpl
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.ActivityDuration
import com.huangder.lumibooks.ui.home.ReadingStreakCalculator
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ListeningDurationPersistenceTest {
    @Test fun upgradePreservesHistoricalReadingAndAddsZeroListening() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE reading_records (id INTEGER PRIMARY KEY, bookId TEXT NOT NULL, date TEXT NOT NULL, duration INTEGER NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, sourceDeviceId TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX record_identity ON reading_records (bookId, date, sourceDeviceId)")
                    db.execSQL("INSERT INTO reading_records VALUES (1, 'book', '2026-10-26', 1800000, 10, 20, 'device', 20)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            DatabaseMigrations.MIGRATION_24_25.migrate(db)
            db.query("SELECT duration, listeningDuration, sourceDeviceId FROM reading_records").use {
                it.moveToFirst()
                assertEquals(1_800_000L, it.getLong(0))
                assertEquals(0L, it.getLong(1))
                assertEquals("device", it.getString(2))
            }
        }
    }

    @Test fun transactionsPreserveBothKindsAndAllStatisticsIncludeListeningExceptHiddenBooks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            for (id in listOf("book", "audio", "hidden")) {
                db.bookDao().upsertBooks(listOf(BookEntity(id, "Book", "Author", "", null, "EPUB", 0, 0f,
                    createdAt = 0, isHidden = id == "hidden")))
            }
            val identity = SyncIdentityStore(db.syncStateDao())
            val repo = ReadingRepositoryImpl(db.readingRecordDao(), db.bookmarkDao(), db.noteDao(), db.syncStateDao(), identity, db)
            val date = "2026-10-26"
            // Seed the device identity, then race increments against the same daily record.
            identity.deviceId()
            (1..10).map { async { repo.appendActivityDuration("book", date, 1_000, 2_000, 10, 100) } }.awaitAll()
            repo.appendActivityDuration("audio", date, 0, 1_800_000, 20, 200)
            repo.appendActivityDuration("hidden", date, 0, 9_000_000, 30, 300)
            // Another device contributes, without replacing the local day's counters.
            db.readingRecordDao().insertRecord(ReadingRecordEntity(bookId = "book", date = date, duration = 5_000,
                listeningDuration = 10_000, startTime = 1, endTime = 100, sourceDeviceId = "other"))
            assertEquals(ActivityDuration(15_000, 30_000), repo.getActivityDurationByBookId("book").first())
            assertEquals(1, repo.getActiveDaysByBookId("audio").first())
            assertEquals(1_800_000L, repo.getTotalDurationByBookId("audio").first())
            assertEquals(ActivityDuration(15_000, 1_830_000), repo.getActivityDurationBetweenDates(date, date).first())
            assertEquals(1_845_000L, repo.getTotalDurationByDate(date).first())
            assertEquals(1_845_000L, repo.getTotalDurationBetweenDates(date, date).first())
            val daily = repo.getDailyTotalsBetween(date, date).first()
            assertEquals(1_845_000L, daily.single().totalDuration)
            assertEquals(1, ReadingStreakCalculator.calculate(daily.associate { it.date to it.totalDuration }, LocalDate.parse(date), 1_800_000))
            assertEquals(listOf("audio", "book"), repo.getMostReadBooksBetween(date, date).first().map { it.bookId })
            assertEquals(1, db.syncStateDao().getAllReadingRecords().count { it.bookId == "book" && it.sourceDeviceId != "other" })
        } finally { db.close() }
    }
}

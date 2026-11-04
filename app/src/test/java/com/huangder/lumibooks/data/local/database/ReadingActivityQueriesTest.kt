package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ReadingActivityQueriesTest {
    @Test fun aggregatesUsePositiveActivityAndExcludeHiddenBooksWithoutDeletingTheirHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            for (id in listOf("visible","hidden","zero")) db.bookDao().insertBook(BookEntity(
                id=id,title=id,author="",filePath="",coverPath=null,format="EPUB",lastReadTime=0,readingProgress=0f,createdAt=0,isHidden=id=="hidden"))
            val dao=db.readingRecordDao()
            suspend fun row(id:String,date:String,reading:Long,listening:Long,device:String="one") =
                dao.insertRecord(ReadingRecordEntity(bookId=id,date=date,duration=reading,listeningDuration=listening,startTime=1,endTime=2,sourceDeviceId=device))
            row("visible","2026-11-01",60_000,0)
            row("visible","2026-11-01",0,120_000,"two")
            row("visible","2026-11-02",0,90_000)
            row("visible","2026-11-03",0,0)
            row("hidden","2026-11-01",600_000,0)
            row("zero","2026-11-01",0,0)
            val daily=dao.getDailyActivity("2026-11-01","2026-11-03").first()
            assertEquals(listOf(180_000L,90_000L),daily.map { it.totalDuration })
            val books=dao.getBookActivity("2026-11-01","2026-11-03").first()
            assertEquals(listOf("visible"),books.map { it.bookId })
            assertEquals(2,books.single().activeDays)
            assertEquals("2026-11-02",books.single().lastDate)
            assertEquals(600_000L,dao.getBookDailyActivity("hidden").first().single().totalDuration)
            db.bookDao().setBooksHidden(listOf("visible"),true,999)
            assertTrue(dao.getDailyActivity("2026-11-01","2026-11-03").first().isEmpty())
            assertEquals(2,dao.getBookDailyActivity("visible").first().size)
        } finally { db.close() }
    }
}

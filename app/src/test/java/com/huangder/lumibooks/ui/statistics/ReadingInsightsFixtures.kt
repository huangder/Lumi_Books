package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.*
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf

internal object ReadingInsightsFixtures {
    val today:LocalDate=LocalDate.now()
    val books=listOf(
        Book(id="alpha",title="山间的一日 · A Day in the Mountains",author="Lumi",filePath="",coverPath=null,
            format=BookFormat.EPUB,lastReadTime=1,readingProgress=.42f,createdAt=0),
        Book(id="beta",title="A quiet reading journey",author="Reader",filePath="",coverPath=null,
            format=BookFormat.EPUB,lastReadTime=1,readingProgress=.7f,createdAt=0))
    val records=(0..24).map { index ->
        ReadingRecord(bookId=if(index%3==0) "beta" else "alpha",date=today.minusDays(index.toLong()).readingDate(),
            duration=(index%5+1)*300_000L,listeningDuration=if(index%2==0)600_000 else 0,startTime=0,endTime=1)
    }
    val repository=Proxy.newProxyInstance(ReadingRepository::class.java.classLoader,arrayOf(ReadingRepository::class.java)) { _, method, args ->
        val start=args?.getOrNull(0) as? String
        val end=args?.getOrNull(1) as? String
        val range=if(start!=null&&end!=null)records.filter { it.date>=start&&it.date<=end }else records
        when(method.name) {
            "getDailyActivity"->flowOf(aggregateReadingDays(range))
            "getBookActivity"->flowOf(aggregateReadingBooks(range))
            "getBookDailyActivity"->flowOf(aggregateReadingDays(records.filter { it.bookId==start }))
            "getRecordsByDate"->flowOf(records.filter { it.date==start })
            "getRecordsBetweenDates"->flowOf(range)
            "getTotalDurationBetweenDates"->flowOf(range.sumOf { it.duration+it.listeningDuration })
            "getTotalDurationByDate"->flowOf(records.filter { it.date==start }.sumOf { it.duration+it.listeningDuration })
            "getActivityDurationBetweenDates"->flowOf(ActivityDuration(range.sumOf { it.duration },range.sumOf { it.listeningDuration }))
            "toString"->"ReadingInsightsFixture"
            else->flowOf(emptyList<Any>())
        }
    } as ReadingRepository
    val library=Proxy.newProxyInstance(BookRepository::class.java.classLoader,arrayOf(BookRepository::class.java)) { _,method,args ->
        when(method.name) {
            "getAllBooks"->flowOf(books)
            "getBookById"->books.firstOrNull { it.id==args?.get(0) }
            "toString"->"ReadingLibraryFixture"
            else->flowOf(emptyList<Any>())
        }
    } as BookRepository
}

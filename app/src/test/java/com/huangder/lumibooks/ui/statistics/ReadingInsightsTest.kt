package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReadingInsightsTest {
    @Test fun currentPeriodComparesCompletedDaysOnly() {
        val today = LocalDate.of(2026, 11, 4)
        val (current, previous) = comparisonPeriods(StatisticsSelection(StatisticsScope.MONTH, 2026, 10), today)!!
        assertEquals(StatisticsPeriod(LocalDate.of(2026,11,1), LocalDate.of(2026,11,3)), current)
        assertEquals(StatisticsPeriod(LocalDate.of(2026,10,1), LocalDate.of(2026,10,3)), previous)
        assertNull(comparisonPeriods(StatisticsSelection(StatisticsScope.MONTH, 2026, 10), LocalDate.of(2026,11,1)))
    }
    @Test fun comparisonsHandleShortPreviousMonthsAndHistoricalPeriods() {
        val current = comparisonPeriods(StatisticsSelection(StatisticsScope.MONTH, 2028, 2), LocalDate.of(2028,3,31))!!
        assertEquals(LocalDate.of(2028,2,29), current.second.endInclusive)
        assertEquals(current.first.days.size, current.second.days.size)
        val historical = comparisonPeriods(StatisticsSelection(StatisticsScope.MONTH, 2028, 1), LocalDate.of(2028,4,2))!!
        assertEquals(29, historical.first.days.size)
        assertEquals(31, historical.second.days.size)
    }
    @Test fun weekCrossesYearWithoutChangingSundayAnchor() {
        val period = StatisticsSelection(StatisticsScope.WEEK).range(LocalDate.of(2027,1,1))
        assertEquals(LocalDate.of(2026,12,27), period.start)
        assertEquals(LocalDate.of(2027,1,2), period.endInclusive)
        assertEquals(period, StatisticsSelection(StatisticsScope.WEEK).shift(1, LocalDate.of(2027,1,1)).range(LocalDate.of(2027,1,1)))
    }
    @Test fun dailyAndBookAggregationDeduplicateDaysAcrossDevicesAndIncludeListening() {
        val rows = listOf(
            ReadingRecord(bookId="a", date="2026-11-01", duration=60_000, listeningDuration=30_000, startTime=0,endTime=1,sourceDeviceId="a"),
            ReadingRecord(bookId="a", date="2026-11-01", duration=0, listeningDuration=120_000, startTime=0,endTime=1,sourceDeviceId="b"),
            ReadingRecord(bookId="a", date="2026-11-02", duration=0, startTime=0,endTime=1),
            ReadingRecord(bookId="b", date="2026-11-03", duration=0, listeningDuration=90_000, startTime=0,endTime=1))
        assertEquals(listOf(210_000L,90_000L), aggregateReadingDays(rows).map { it.totalDuration })
        val books = aggregateReadingBooks(rows)
        assertEquals(2, books.size)
        assertEquals(1, books.first().activeDays)
        assertEquals("2026-11-01", books.first().lastDate)
    }
    @Test fun averagesIncludeZeroDaysAndSeparateWeekdaysFromWeekends() {
        val sunday = LocalDate.of(2026,11,1)
        val data = ReadingInsights(StatisticsPeriod(sunday,sunday.plusDays(6)), sunday.plusDays(2),
            mapOf(sunday to ActivityDuration(60_000,60_000), sunday.plusDays(1) to ActivityDuration(180_000,0)), goalMinutes=2)
        assertEquals(100_000L, data.dailyAverage)
        assertEquals(90_000L, data.averageForWeekend(false))
        assertEquals(120_000L, data.averageForWeekend(true))
        assertEquals(2, data.readingDays)
        assertEquals(2, data.longestStreak)
        assertEquals(2, data.currentStreak)
    }
    @Test fun emptyInsightsHaveFiniteZeroAverages() {
        val day = LocalDate.of(2026,11,4)
        val data = ReadingInsights(StatisticsPeriod(day,day),day)
        assertEquals(0L,data.dailyAverage)
        assertEquals(0L,data.averageForWeekend(true))
        assertNull(data.bestDay)
        assertEquals(0,data.longestStreak)
    }
    @Test fun annualWallGroupsByFirstReadingAndKeepsStableTitleOrder() {
        fun item(id:String,title:String,date:String) = InsightsBook(
            Book(id=id,title=title,author="",filePath="",coverPath=null,format=BookFormat.EPUB,lastReadTime=0,readingProgress=0f,createdAt=0),
            ReadingBookActivity(id,date,date,60_000,0,1))
        val result = annualBooksByMonth(listOf(item("b","B","2026-02-01"),item("c","C","2026-01-02"),item("a","A","2026-01-02")))
        assertEquals(listOf(1,2),result.keys.toList())
        assertEquals(listOf("A","C"),result[1]!!.map { it.book.title })
    }
}

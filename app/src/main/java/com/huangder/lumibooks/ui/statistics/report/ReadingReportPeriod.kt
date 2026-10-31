package com.huangder.lumibooks.ui.statistics.report

import com.huangder.lumibooks.ui.statistics.StatisticsPeriod
import java.time.LocalDate

internal enum class ReadingReportKind { WEEK, MONTH, YEAR }

internal data class ReadingReportPeriod(
    val kind: ReadingReportKind,
    val year: Int,
    val month0: Int = 0,
    val dayOfMonth: Int = 1
) {
    init {
        if (kind == ReadingReportKind.WEEK) {
            require(LocalDate.of(year, month0 + 1, dayOfMonth).dayOfWeek.value == 7) {
                "Weekly reports must be anchored to Sunday"
            }
        }
    }

    val range: StatisticsPeriod
        get() = when (kind) {
            ReadingReportKind.WEEK -> StatisticsPeriod.week(LocalDate.of(year, month0 + 1, dayOfMonth), 0)
            ReadingReportKind.MONTH -> StatisticsPeriod.month(year, month0)
            ReadingReportKind.YEAR -> StatisticsPeriod.year(year)
        }
}

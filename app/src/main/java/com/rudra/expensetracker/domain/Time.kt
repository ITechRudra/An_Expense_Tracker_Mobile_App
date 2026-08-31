package com.rudra.expensetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable clock so period arithmetic is testable without freezing the JVM clock. */
@Singleton
class AppClock @Inject constructor(
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun nowMillis(): Long = System.currentTimeMillis()
    fun today(): LocalDate = LocalDate.now(zone)
    fun currentMonth(): YearMonth = YearMonth.now(zone)

    fun toEpochMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    fun toLocalDate(epochMillis: Long): LocalDate = toLocalDateTime(epochMillis).toLocalDate()
}

/** An inclusive-start, inclusive-end epoch-millisecond window. */
data class DateRange(val fromMillis: Long, val toMillis: Long) {
    companion object {
        fun of(from: LocalDate, to: LocalDate, zone: ZoneId): DateRange = DateRange(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        )

        fun month(month: YearMonth, zone: ZoneId): DateRange =
            of(month.atDay(1), month.atEndOfMonth(), zone)

        fun day(date: LocalDate, zone: ZoneId): DateRange = of(date, date, zone)

        /** The seven days ending today, matching how people say "this week". */
        fun trailingWeek(today: LocalDate, zone: ZoneId): DateRange =
            of(today.minusDays(6), today, zone)

        fun all(): DateRange = DateRange(0L, Long.MAX_VALUE)
    }
}

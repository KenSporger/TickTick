package com.personalticktick.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }

data class SmartFill(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val time: LocalTime?,
    val repeatKind: RepeatKind?,
    val repeatUnit: RepeatUnit?,
    val weekdays: Set<Int>,
    val monthDay: Int?,
    val interval: Int?,
    val matchedText: String?,
    val confident: Boolean
)

/** Fills date, clock time and repeat from a title. Cron is checked before natural language. */
object SmartSchedule {
    private val cronAtom = """(?:\*(?:/\d+)?|\?|\d+(?:-\d+)?(?:/\d+)?)"""
    private val cronField = """$cronAtom(?:,$cronAtom)*"""
    private val cron = Regex("""(?:^|[\s,，])((?:$cronField)(?:\s+$cronField){4,5})(?!\S)""")
    private val everyDay = Regex("每天|每日")
    private val everyWeekday = Regex("每个?工作日")
    private val everyWeek = Regex("每(\\d+)?个?(?:周|星期)([一二三四五六日天])?")
    private val everyMonth = Regex("每月(\\d{1,2})?[日号]?")
    private val everyYear = Regex("每年")

    fun parse(text: String, now: ZonedDateTime): SmartFill {
        parseCron(text, now.toLocalDate())?.let { return it }
        val natural = ChineseDateParser().parse(text, now)
        val weekdays = linkedSetOf<Int>()
        var kind: RepeatKind? = null
        var unit: RepeatUnit? = null
        var monthDay: Int? = null
        var interval: Int? = null
        everyDay.find(text)?.let { kind = RepeatKind.DAILY; unit = RepeatUnit.DAY; interval = 1 }
        everyWeekday.find(text)?.let { kind = RepeatKind.WEEKDAYS; unit = RepeatUnit.WEEK; interval = 1; weekdays += setOf(1, 2, 3, 4, 5) }
        everyWeek.find(text)?.let { match ->
            kind = RepeatKind.WEEKLY
            unit = RepeatUnit.WEEK
            interval = match.groupValues[1].toIntOrNull() ?: 1
            weekdayNumber(match.groupValues[2])?.let(weekdays::add)
        }
        everyMonth.find(text)?.let { match ->
            if (kind == null) {
                kind = RepeatKind.MONTHLY
                unit = RepeatUnit.MONTH
                interval = 1
                monthDay = match.groupValues[1].toIntOrNull()
            }
        }
        if (kind == null && everyYear.containsMatchIn(text)) {
            kind = RepeatKind.YEARLY
            unit = RepeatUnit.YEAR
            interval = 1
        }
        val start = natural.startDate ?: weekdays.firstOrNull()?.let { iso -> upcoming(now.toLocalDate(), iso) }
        return SmartFill(
            startDate = start,
            endDate = start,
            time = natural.time,
            repeatKind = kind,
            repeatUnit = unit,
            weekdays = weekdays,
            monthDay = monthDay ?: start?.dayOfMonth?.takeIf { kind == RepeatKind.MONTHLY || kind == RepeatKind.YEARLY },
            interval = interval,
            matchedText = natural.matchedText,
            confident = natural.confident || kind != null
        )
    }

    private fun parseCron(text: String, today: LocalDate): SmartFill? {
        val raw = cron.find(text)?.groupValues?.get(1) ?: return null
        val parts = raw.split(Regex("\\s+"))
        val fields = when (parts.size) {
            5 -> parts
            6 -> parts.drop(1)
            else -> return null
        }
        val minutes = expandCron(fields[0], 0, 59) ?: setOf(0)
        val hours = expandCron(fields[1], 0, 23) ?: setOf(9)
        if (minutes.isEmpty() || hours.isEmpty()) return null
        val dom = expandCron(fields[2], 1, 31)
        val month = expandCron(fields[3], 1, 12)
        val dow = expandCron(fields[4], 0, 7)?.map { if (it == 0) 7 else it }?.filter { it in 1..7 }?.toSet()
        val weekdays = dow ?: emptySet()
        val monthDay = dom?.minOrNull()
        val kind: RepeatKind
        val unit: RepeatUnit
        when {
            weekdays.isNotEmpty() -> {
                kind = if (weekdays == setOf(1, 2, 3, 4, 5)) RepeatKind.WEEKDAYS else RepeatKind.WEEKLY
                unit = RepeatUnit.WEEK
            }
            monthDay != null && month != null -> {
                kind = RepeatKind.YEARLY
                unit = RepeatUnit.YEAR
            }
            monthDay != null -> {
                kind = RepeatKind.MONTHLY
                unit = RepeatUnit.MONTH
            }
            else -> {
                kind = RepeatKind.DAILY
                unit = RepeatUnit.DAY
            }
        }
        val date = when {
            weekdays.isNotEmpty() -> weekdays.map { upcoming(today, it) }.minOrNull()
            monthDay != null && month != null -> yearlyDate(today, month.minOrNull() ?: return null, monthDay)
            monthDay != null -> monthlyDate(today, monthDay)
            else -> today
        } ?: return null
        return SmartFill(date, date, LocalTime.of(hours.min(), minutes.min()), kind, unit, weekdays, monthDay, 1, raw, true)
    }

    /** Null means the field is unrestricted (`*` or `?`). An empty set means the field was invalid. */
    private fun expandCron(field: String, min: Int, max: Int): Set<Int>? {
        if (field == "*" || field == "?") return null
        val values = linkedSetOf<Int>()
        field.split(',').forEach { part ->
            val stepSplit = part.split('/')
            val step = stepSplit.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            if (stepSplit.size > 2) return emptySet()
            val range = when (val base = stepSplit[0]) {
                "*", "?" -> min..max
                else -> {
                    val bounds = base.split('-')
                    val start = bounds[0].toIntOrNull() ?: return emptySet()
                    val end = bounds.getOrNull(1)?.toIntOrNull() ?: start
                    if (bounds.size > 2 || start > end) return emptySet()
                    start..end
                }
            }
            var value = range.first
            while (value <= range.last) {
                if (value in min..max) values += value
                value += step
            }
        }
        return values
    }

    private fun monthlyDate(today: LocalDate, monthDay: Int): LocalDate {
        val thisMonth = today.withDayOfMonth(monthDay.coerceAtMost(today.lengthOfMonth()))
        if (!thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return next.withDayOfMonth(monthDay.coerceAtMost(next.lengthOfMonth()))
    }

    private fun yearlyDate(today: LocalDate, month: Int, monthDay: Int): LocalDate {
        val length = java.time.YearMonth.of(today.year, month).lengthOfMonth()
        var candidate = LocalDate.of(today.year, month, monthDay.coerceAtMost(length))
        if (candidate.isBefore(today)) {
            val nextLength = java.time.YearMonth.of(today.year + 1, month).lengthOfMonth()
            candidate = LocalDate.of(today.year + 1, month, monthDay.coerceAtMost(nextLength))
        }
        return candidate
    }

    private fun weekdayNumber(label: String): Int? = when (label) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
        else -> null
    }

    private fun upcoming(today: LocalDate, iso: Int): LocalDate {
        val date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(iso)))
        return date
    }
}

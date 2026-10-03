package com.personalticktick.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Pure task lifecycle rules. All date boundaries are natural local days. */
object TaskLifecycle {
    fun isOverdue(task: Task, today: LocalDate): Boolean {
        if (task.status != TaskStatus.ACTIVE || task.isProjection) return false
        val dueDate = task.endDate ?: task.startDate ?: return false
        return dueDate.isBefore(today)
    }

    fun isVisibleOn(task: Task, date: LocalDate): Boolean {
        if (task.status == TaskStatus.DELETED || task.status == TaskStatus.SKIPPED) return false
        val start = task.startDate ?: return false
        val end = task.endDate ?: start
        return !date.isBefore(start) && !date.isAfter(end)
    }

    /** Only a persisted completion can be restored; future projections never can. */
    fun canRestore(task: Task): Boolean =
        task.status == TaskStatus.COMPLETED && !task.isProjection

    /**
     * Builds the next active occurrence. A finite totalCount includes the first occurrence.
     * The inclusive duration and reminder offset are retained.
     */
    fun nextOccurrence(task: Task): Task? {
        val rule = task.repeatRule
        val start = task.startDate ?: return null
        if (rule.kind == RepeatKind.NONE) return null
        if (rule.totalCount != null && rule.occurrenceIndex >= rule.totalCount) return null

        val nextStart = when (rule.kind) {
            RepeatKind.NONE -> return null
            RepeatKind.DAILY -> start.plusDays(1)
            RepeatKind.WEEKDAYS -> nextMatchingDay(start, setOf(1, 2, 3, 4, 5))
            RepeatKind.WEEKLY -> if (rule.weekdays.isEmpty()) start.plusWeeks(1)
                else nextMatchingDay(start, rule.weekdays.filter { it in 1..7 }.toSet())
            RepeatKind.MONTHLY -> {
                val nextMonth = YearMonth.from(start).plusMonths(1)
                val requestedDay = rule.monthDay ?: start.dayOfMonth
                nextMonth.atDay(requestedDay.coerceIn(1, nextMonth.lengthOfMonth()))
            }
        }
        val dayShift = ChronoUnit.DAYS.between(start, nextStart)
        val duration = task.endDate?.let { ChronoUnit.DAYS.between(start, it) } ?: 0
        val nextIndex = rule.occurrenceIndex + 1
        val rootSeriesId = task.seriesId ?: task.id
        return task.copy(
            id = "$rootSeriesId:$nextIndex",
            seriesId = rootSeriesId,
            startDate = nextStart,
            endDate = nextStart.plusDays(duration),
            reminderAt = task.reminderAt?.plusDays(dayShift),
            repeatRule = rule.copy(occurrenceIndex = nextIndex),
            status = TaskStatus.ACTIVE,
            isProjection = false
        )
    }

    private fun nextMatchingDay(from: LocalDate, allowedIsoDays: Set<Int>): LocalDate {
        if (allowedIsoDays.isEmpty()) return from.plusWeeks(1)
        var candidate = from.plusDays(1)
        repeat(7) {
            if (candidate.dayOfWeek.value in allowedIsoDays) return candidate
            candidate = candidate.plusDays(1)
        }
        return from.plusWeeks(1) // unreachable for a non-empty valid set
    }
}

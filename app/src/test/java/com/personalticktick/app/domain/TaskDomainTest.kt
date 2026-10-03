package com.personalticktick.app.domain

import com.personalticktick.app.ui.EditorState
import com.personalticktick.app.ui.shouldSeedDemo
import com.personalticktick.app.ui.toRepeatRule
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskDomainTest {
    private val now = LocalDateTime.of(2026, 10, 2, 17, 5)

    private fun task(
        start: LocalDate?, end: LocalDate? = start, time: LocalTime? = null,
        repeat: RepeatRule = RepeatRule(), status: TaskStatus = TaskStatus.ACTIVE,
        projection: Boolean = false
    ) = Task(
        id = "task", title = "阅读书籍", startDate = start, endDate = end, time = time,
        repeatRule = repeat, status = status, isProjection = projection,
        createdAt = now, updatedAt = now
    )

    @Test fun timedTaskBecomesOverdueOnNextNaturalDayNotAfterItsClockTime() {
        val timed = task(LocalDate.of(2026, 10, 2), time = LocalTime.of(13, 0))
        assertFalse(TaskLifecycle.isOverdue(timed, LocalDate.of(2026, 10, 2)))
        assertTrue(TaskLifecycle.isVisibleOn(timed, LocalDate.of(2026, 10, 2)))
        assertTrue(TaskLifecycle.isOverdue(timed, LocalDate.of(2026, 10, 3)))
    }

    @Test fun multiDayTaskIsVisibleForWholeInclusiveRangeAndOverdueAfterEndDate() {
        val ranged = task(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 4))
        assertFalse(TaskLifecycle.isVisibleOn(ranged, LocalDate.of(2026, 10, 1)))
        assertTrue(TaskLifecycle.isVisibleOn(ranged, LocalDate.of(2026, 10, 2)))
        assertTrue(TaskLifecycle.isVisibleOn(ranged, LocalDate.of(2026, 10, 3)))
        assertTrue(TaskLifecycle.isVisibleOn(ranged, LocalDate.of(2026, 10, 4)))
        assertFalse(TaskLifecycle.isVisibleOn(ranged, LocalDate.of(2026, 10, 5)))
        assertFalse(TaskLifecycle.isOverdue(ranged, LocalDate.of(2026, 10, 4)))
        assertTrue(TaskLifecycle.isOverdue(ranged, LocalDate.of(2026, 10, 5)))
    }

    @Test fun dailyWeekdaySelectedWeekdayAndMonthlyRulesAdvanceDeterministically() {
        assertEquals(LocalDate.of(2026, 10, 3), TaskLifecycle.nextOccurrence(task(LocalDate.of(2026, 10, 2), repeat = RepeatRule(RepeatKind.DAILY)))?.startDate)
        assertEquals(LocalDate.of(2026, 10, 5), TaskLifecycle.nextOccurrence(task(LocalDate.of(2026, 10, 2), repeat = RepeatRule(RepeatKind.WEEKDAYS)))?.startDate)
        assertEquals(LocalDate.of(2026, 10, 5), TaskLifecycle.nextOccurrence(task(LocalDate.of(2026, 10, 2), repeat = RepeatRule(RepeatKind.WEEKLY, weekdays = setOf(1, 3))))?.startDate)
        assertEquals(LocalDate.of(2026, 10, 7), TaskLifecycle.nextOccurrence(task(LocalDate.of(2026, 10, 5), repeat = RepeatRule(RepeatKind.WEEKLY, weekdays = setOf(1, 3))))?.startDate)
        assertEquals(LocalDate.of(2026, 2, 28), TaskLifecycle.nextOccurrence(task(LocalDate.of(2026, 1, 31), repeat = RepeatRule(RepeatKind.MONTHLY, monthDay = 31)))?.startDate)
    }

    @Test fun recurrencePreservesDurationAndStopsAtFiniteCount() {
        val next = TaskLifecycle.nextOccurrence(task(
            LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 4),
            repeat = RepeatRule(RepeatKind.DAILY, totalCount = 2, occurrenceIndex = 1)
        ))
        assertEquals(LocalDate.of(2026, 10, 3), next?.startDate)
        assertEquals(LocalDate.of(2026, 10, 5), next?.endDate)
        assertEquals(2, next?.repeatRule?.occurrenceIndex)
        assertNull(TaskLifecycle.nextOccurrence(next!!))
    }

    @Test fun restoreAllowsCompletedRealTaskButRejectsOtherStatesAndProjections() {
        assertTrue(TaskLifecycle.canRestore(task(LocalDate.now(), status = TaskStatus.COMPLETED)))
        assertFalse(TaskLifecycle.canRestore(task(LocalDate.now(), status = TaskStatus.ACTIVE)))
        assertFalse(TaskLifecycle.canRestore(task(LocalDate.now(), status = TaskStatus.DELETED)))
        assertFalse(TaskLifecycle.canRestore(task(LocalDate.now(), status = TaskStatus.SKIPPED)))
        assertFalse(TaskLifecycle.canRestore(task(LocalDate.now(), status = TaskStatus.COMPLETED, projection = true)))
    }

    @Test fun chineseRelativeExpressionReturnsVisibleCancellableMatchedText() {
        val parsed = ChineseDateParser().parse("明天下午3点交材料", ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai")))
        assertEquals(LocalDate.of(2026, 10, 3), parsed.startDate)
        assertEquals(LocalTime.of(15, 0), parsed.time)
        assertEquals("明天下午3点", parsed.matchedText)
        assertEquals("交材料", parsed.titleSuggestion)
        assertTrue(parsed.confident)
    }

    @Test fun chineseAbsoluteMonthDayUsesCurrentOrNextYearAndKeepsMatchedSpan() {
        val parser = ChineseDateParser()
        val base = ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai"))
        val currentYear = parser.parse("10月5日体检", base)
        val nextYear = parser.parse("9月30日续费", base)
        assertEquals(LocalDate.of(2026, 10, 5), currentYear.startDate)
        assertEquals("10月5日", currentYear.matchedText)
        assertEquals("体检", currentYear.titleSuggestion)
        assertEquals(LocalDate.of(2027, 9, 30), nextYear.startDate)
    }

    @Test fun chineseNumeralAndWeekdayFillClockTime() {
        val parsed = ChineseDateParser().parse("下周六下午三点练琴", ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai")))
        assertEquals(LocalDate.of(2026, 10, 10), parsed.startDate)
        assertEquals(LocalTime.of(15, 0), parsed.time)
        assertTrue(parsed.confident)
    }

    @Test fun cronExpressionFillsTimeAndWeekday() {
        val parsed = SmartSchedule.parse("30 9 * * 1 周会", ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai")))
        assertEquals(LocalTime.of(9, 30), parsed.time)
        assertEquals(RepeatKind.WEEKLY, parsed.repeatKind)
        assertEquals(setOf(1), parsed.weekdays)
        assertEquals(LocalDate.of(2026, 10, 5), parsed.startDate)
        assertTrue(parsed.confident)
    }

    @Test fun naturalTitleFillsTomorrowAfternoon() {
        val parsed = SmartSchedule.parse("明天下午三点健身", ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai")))
        assertEquals(LocalDate.of(2026, 10, 3), parsed.startDate)
        assertEquals(LocalTime.of(15, 0), parsed.time)
    }

    @Test fun dayAfterTomorrowNoonAndChineseTwelveParse() {
        val base = ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai"))
        val parsed = SmartSchedule.parse("后天中午12点喝咖啡", base)
        assertEquals(LocalDate.of(2026, 10, 4), parsed.startDate)
        assertEquals(LocalTime.of(12, 0), parsed.time)
        assertEquals(LocalTime.of(12, 30), SmartSchedule.parse("大后天中午十二点半", base).time)
    }

    @Test fun fiveAndSixFieldCronFillClockAndRepeat() {
        val base = ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai"))
        val daily = SmartSchedule.parse("0 12 * * * 午饭", base)
        assertEquals(LocalTime.of(12, 0), daily.time)
        assertEquals(RepeatKind.DAILY, daily.repeatKind)
        val quartz = SmartSchedule.parse("0 30 9 * * 0 周会", base)
        assertEquals(LocalTime.of(9, 30), quartz.time)
        assertEquals(setOf(7), quartz.weekdays)
        assertEquals(LocalDate.of(2026, 10, 4), quartz.startDate)
    }

    @Test fun weeklySeriesIsVisibleOnTheFollowingWeekday() {
        val sunday = LocalDate.of(2026, 10, 4)
        val reading = task(sunday, repeat = RepeatRule(RepeatKind.WEEKLY, weekdays = setOf(7)))
        assertTrue(TaskLifecycle.occursOn(reading, sunday))
        assertTrue(TaskLifecycle.occursOn(reading, LocalDate.of(2026, 10, 11)))
        assertFalse(TaskLifecycle.occursOn(reading, LocalDate.of(2026, 10, 5)))
        val limited = task(sunday, repeat = RepeatRule(RepeatKind.WEEKLY, weekdays = setOf(7), totalCount = 2))
        assertTrue(TaskLifecycle.occursOn(limited, LocalDate.of(2026, 10, 11)))
        assertFalse(TaskLifecycle.occursOn(limited, LocalDate.of(2026, 10, 18)))
    }

    @Test fun clearedLocalDataDoesNotReseedOverACloudSnapshot() {
        assertFalse(shouldSeedDemo(cloudSyncEnabled = true, cloudReached = true, storedCount = 4, visibleCount = 0))
        assertFalse(shouldSeedDemo(cloudSyncEnabled = true, cloudReached = false, storedCount = 0, visibleCount = 0))
        assertTrue(shouldSeedDemo(cloudSyncEnabled = true, cloudReached = true, storedCount = 0, visibleCount = 0))
        assertTrue(shouldSeedDemo(cloudSyncEnabled = false, cloudReached = false, storedCount = 0, visibleCount = 0))
    }

    @Test fun deletingOneWeeklyRoundLeavesTheOtherRounds() {
        val sunday = LocalDate.of(2026, 10, 4)
        val next = LocalDate.of(2026, 10, 11)
        val later = LocalDate.of(2026, 10, 18)
        val reading = task(sunday, repeat = RepeatRule(RepeatKind.WEEKLY, weekdays = setOf(7)))
        val onlyThis = TaskLifecycle.excludingOccurrence(reading, next)
        assertTrue(TaskLifecycle.occursOn(onlyThis, sunday))
        assertFalse(TaskLifecycle.occursOn(onlyThis, next))
        assertTrue(TaskLifecycle.occursOn(onlyThis, later))
        val rest = TaskLifecycle.endingBefore(reading, next)
        assertEquals(LocalDate.of(2026, 10, 10), rest?.repeatRule?.repeatUntil)
        assertTrue(TaskLifecycle.occursOn(rest!!, sunday))
        assertFalse(TaskLifecycle.occursOn(rest, next))
        assertFalse(TaskLifecycle.occursOn(rest, later))
        assertNull(TaskLifecycle.endingBefore(reading, sunday))
    }

    @Test fun weeklySelectionAndMonthDayAreKeptOnTheRule() {
        val weekly = EditorState(repeatKind = RepeatKind.WEEKLY, weekdays = setOf(6), repeatInterval = 2).toRepeatRule()
        assertEquals(setOf(6), weekly.weekdays)
        assertEquals(2, weekly.interval)
        val monthly = EditorState(repeatKind = RepeatKind.MONTHLY, monthDay = 18).toRepeatRule()
        assertEquals(18, monthly.monthDay)
    }

    @Test fun everyTwoWeeksSkipsTheInBetweenWeek() {
        val next = TaskLifecycle.nextOccurrence(task(LocalDate.of(2026, 10, 3), repeat = RepeatRule(RepeatKind.WEEKLY, weekdays = setOf(6), interval = 2)))
        assertEquals(LocalDate.of(2026, 10, 17), next?.startDate)
    }

    @Test fun fuzzySearchSupportsChineseOrderedSubsequenceEnglishAndPinyin() {
        assertTrue(TaskSearch.matches("阅读书籍", "阅书"))
        assertFalse(TaskSearch.matches("阅读书籍", "书阅"))
        assertTrue(TaskSearch.matches("Buy Milk", "bM"))
        assertTrue(TaskSearch.matches("阅读书籍", "yd"))
        assertTrue(TaskSearch.matches("阅读书籍", "yuedu"))
        assertFalse(TaskSearch.matches("阅读书籍", "zz"))
    }
}

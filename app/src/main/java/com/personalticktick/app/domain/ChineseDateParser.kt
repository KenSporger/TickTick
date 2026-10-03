package com.personalticktick.app.domain

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Small, explicit Chinese parser: a visible matched span is always returned for inferred values. */
class ChineseDateParser : NaturalDateParser {
    private val relativeDate = Regex("大后天|今天|明天|后天|今晚|明早")
    private val absoluteDate = Regex("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?")
    private val weekdayDate = Regex("(下下|下|本|这)?(?:周|星期)([一二三四五六日天])")
    private val timeExpression = Regex("(凌晨|早上|上午|中午|下午|晚上|傍晚)?\\s*(\\d{1,2}|[零一二两三四五六七八九十]{1,3})\\s*(?:点半|点一刻|点三刻|点整|点(?:(\\d{1,2}|[零一二两三四五六七八九十]{1,3}))?(?:分|整)?|[:：](\\d{1,2})|时(?:(\\d{1,2}))?(?:分)?)")

    override fun parse(text: String, now: ZonedDateTime): ParsedDateTime {
        val dateMatch = relativeDate.find(text) ?: absoluteDate.find(text) ?: weekdayDate.find(text)
        val date = dateMatch?.let { resolveDate(it, now.toLocalDate()) }
        val timeMatch = timeExpression.find(text)
        val time = timeMatch?.let(::resolveTime)
        val matches = listOfNotNull(dateMatch, timeMatch)
        val span = if (matches.isEmpty()) null else {
            val start = matches.minOf { it.range.first }
            val end = matches.maxOf { it.range.last }
            // Only combine date and time when everything between them is whitespace.
            if (matches.size == 1 || text.substring(matches.minOf { it.range.last } + 1, matches.maxOf { it.range.first }).isBlank()) {
                text.substring(start, end + 1)
            } else dateMatch?.value ?: timeMatch?.value
        }
        val title = if (span == null) text.trim() else text.replaceFirst(span, "")
            .trim().trim('，', ',', '。', '-', ' ')
        return ParsedDateTime(
            titleSuggestion = title,
            startDate = date,
            endDate = date,
            time = time,
            matchedText = span,
            confident = date != null || time != null
        )
    }

    private fun resolveDate(match: MatchResult, today: LocalDate): LocalDate? = try {
        when (match.value) {
            "今天", "今晚" -> today
            "明天", "明早" -> today.plusDays(1)
            "后天" -> today.plusDays(2)
            "大后天" -> today.plusDays(3)
            else -> weekdayDate.matchEntire(match.value)?.let { weekdayOf(it, today) } ?: run {
                val explicitYear = match.groups[1]?.value?.toInt()
                val month = match.groups[2]!!.value.toInt()
                val day = match.groups[3]!!.value.toInt()
                if (explicitYear != null) LocalDate.of(explicitYear, month, day) else {
                    var candidate = LocalDate.of(today.year, month, day)
                    if (candidate.isBefore(today)) candidate = candidate.plusYears(1)
                    candidate
                }
            }
        }
    } catch (_: DateTimeException) { null }

    private fun weekdayOf(match: MatchResult, today: LocalDate): LocalDate {
        val prefix = match.groupValues[1]
        val weekShift = when (prefix) {
            "下" -> 1
            "下下" -> 2
            else -> 0
        }
        val iso = when (match.groupValues[2]) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
        }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var date = monday.plusWeeks(weekShift.toLong()).plusDays((iso - 1).toLong())
        if (weekShift == 0 && prefix.isEmpty() && date.isBefore(today)) date = date.plusWeeks(1)
        return date
    }

    private fun resolveTime(match: MatchResult): LocalTime? = try {
        val period = match.groups[1]?.value
        var hour = parseChineseNumber(match.groups[2]!!.value) ?: return null
        val minute = when {
            "点半" in match.value -> 30
            "点一刻" in match.value -> 15
            "点三刻" in match.value -> 45
            else -> listOf(3, 4, 5).firstNotNullOfOrNull { index -> match.groups[index]?.value?.let(::parseChineseNumber) } ?: 0
        }
        if (period in setOf("下午", "晚上", "傍晚") && hour in 1..11) hour += 12
        if (period == "中午" && hour in 1..10) hour += 12
        if ((period == "凌晨" || period == "晚上") && hour == 12) hour = 0
        LocalTime.of(hour, minute)
    } catch (_: DateTimeException) { null }
}

internal fun parseChineseNumber(raw: String): Int? {
    raw.toIntOrNull()?.let { return it }
    val digit = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    if (raw.isEmpty() || raw.any { it != '十' && it !in digit }) return null
    if ('十' !in raw) return if (raw.length == 1) digit[raw[0]] else null
    val parts = raw.split('十')
    val tens = if (parts[0].isEmpty()) 1 else digit[parts[0][0]] ?: return null
    val ones = if (parts.size < 2 || parts[1].isEmpty()) 0 else digit[parts[1][0]] ?: return null
    return tens * 10 + ones
}

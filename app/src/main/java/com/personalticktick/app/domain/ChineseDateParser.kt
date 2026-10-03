package com.personalticktick.app.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Small, explicit Chinese parser: a visible matched span is always returned for inferred values. */
class ChineseDateParser : NaturalDateParser {
    private val relativeDate = Regex("今天|明天|后天")
    private val absoluteDate = Regex("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?")
    private val timeExpression = Regex("(?:(上午|下午|晚上|中午|凌晨))?(\\d{1,2})(?:[:：点时](\\d{1,2})?分?)")

    override fun parse(text: String, now: ZonedDateTime): ParsedDateTime {
        val dateMatch = relativeDate.find(text) ?: absoluteDate.find(text)
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
            "今天" -> today
            "明天" -> today.plusDays(1)
            "后天" -> today.plusDays(2)
            else -> {
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

    private fun resolveTime(match: MatchResult): LocalTime? = try {
        val period = match.groups[1]?.value
        var hour = match.groups[2]!!.value.toInt()
        val minute = match.groups[3]?.value?.toInt() ?: 0
        if (period in setOf("下午", "晚上") && hour in 1..11) hour += 12
        if (period == "中午" && hour in 1..11) hour += 12
        if (period == "凌晨" && hour == 12) hour = 0
        LocalTime.of(hour, minute)
    } catch (_: DateTimeException) { null }
}

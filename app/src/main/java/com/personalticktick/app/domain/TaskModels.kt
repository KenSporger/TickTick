package com.personalticktick.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class TaskStatus { ACTIVE, COMPLETED, SKIPPED, DELETED }
enum class RepeatKind { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY }
enum class SyncState { SYNCED, PENDING, FAILED }

data class RepeatRule(
    val kind: RepeatKind = RepeatKind.NONE,
    val weekdays: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val totalCount: Int? = null,
    val occurrenceIndex: Int = 1
)

data class Task(
    val id: String,
    val seriesId: String? = null,
    val title: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val time: java.time.LocalTime? = null,
    val reminderAt: LocalDateTime? = null,
    val repeatRule: RepeatRule = RepeatRule(),
    val status: TaskStatus = TaskStatus.ACTIVE,
    val isProjection: Boolean = false,
    val syncState: SyncState = SyncState.SYNCED,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class ParsedDateTime(
    val titleSuggestion: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val time: java.time.LocalTime?,
    val matchedText: String?,
    val confident: Boolean
)


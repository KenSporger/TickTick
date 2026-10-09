package com.personalticktick.app.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    suspend fun upsert(task: Task)
    suspend fun find(id: String): Task?
    suspend fun complete(id: String, today: LocalDate = LocalDate.now()): Task?
    suspend fun restore(id: String): Task?
    suspend fun skip(id: String, today: LocalDate = LocalDate.now()): Task?
    suspend fun abandon(id: String, today: LocalDate = LocalDate.now()): Task?
    suspend fun delete(id: String, futureSeries: Boolean)
    suspend fun tasksForDate(date: LocalDate): List<Task>
    suspend fun overdue(today: LocalDate): List<Task>
    suspend fun search(query: String): List<Task>
    suspend fun current(): List<Task>
    suspend fun repairClosedSeries(today: LocalDate = LocalDate.now()): Int
    suspend fun storedCount(): Int
    fun cloudSyncEnabled(): Boolean
    suspend fun refreshFromCloud(): Boolean
    suspend fun syncPending(): Int
}

interface NaturalDateParser {
    fun parse(text: String, now: java.time.ZonedDateTime): ParsedDateTime
}

interface ReminderScheduler {
    fun schedule(task: Task): ReminderCapability
    fun cancel(taskId: String)
    fun rebuild(tasks: List<Task>)
}

enum class ReminderCapability { EXACT, MAY_DELAY, UNAVAILABLE }


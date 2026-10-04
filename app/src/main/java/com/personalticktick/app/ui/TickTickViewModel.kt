package com.personalticktick.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalticktick.app.domain.RepeatKind
import com.personalticktick.app.domain.RepeatRule
import com.personalticktick.app.domain.RepeatUnit
import com.personalticktick.app.domain.SmartSchedule
import com.personalticktick.app.domain.SyncState
import com.personalticktick.app.domain.Task
import com.personalticktick.app.domain.TaskLifecycle
import com.personalticktick.app.domain.TaskRepository
import com.personalticktick.app.domain.TaskSearch
import com.personalticktick.app.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class AppPage { TODAY, CALENDAR, SEARCH, INBOX }
enum class TodayMode { OVERDUE, TODAY }
enum class EditorPanel { NONE, DATE, REMINDER, REPEAT }
enum class DateMode { SINGLE, RANGE }

data class EditorState(
    val editingId: String? = null,
    val title: String = "",
    val startDate: LocalDate? = LocalDate.now(),
    val endDate: LocalDate? = LocalDate.now(),
    val time: LocalTime? = null,
    val dateMode: DateMode = DateMode.SINGLE,
    val reminderEnabled: Boolean = false,
    val reminderLabel: String = "关闭",
    val repeatKind: RepeatKind = RepeatKind.NONE,
    val repeatCount: Int? = null,
    val repeatInterval: Int = 1,
    val repeatUnit: RepeatUnit = RepeatUnit.WEEK,
    val weekdays: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val skipHolidays: Boolean = false,
    val parsedSpan: String? = null,
    val pickingTime: Boolean = false,
    val rangePickingEnd: Boolean = false,
    val visibleMonth: YearMonth = YearMonth.now(),
    val panel: EditorPanel = EditorPanel.NONE,
    val occurrenceDate: LocalDate? = null
)

data class AppUiState(
    val page: AppPage = AppPage.TODAY,
    val todayMode: TodayMode = TodayMode.TODAY,
    val calendarAnchor: LocalDate = LocalDate.now(),
    val tasks: List<Task> = emptyList(),
    val editor: EditorState? = null,
    val searchQuery: String = "",
    val showCompleted: Boolean = true,
    val message: String? = null,
    val lastCompletedId: String? = null,
    val repeatScopePrompt: Boolean = false,
    val deleteScopePrompt: Boolean = false
)

/** Boundary used by the later Room/network integration without coupling composables to storage. */
interface TaskUiGateway {
    suspend fun save(task: Task)
    suspend fun complete(id: String)
    suspend fun restore(id: String)
    suspend fun delete(id: String, futureSeries: Boolean)
}

internal fun EditorState.toRepeatRule(previous: RepeatRule? = null) = RepeatRule(
    kind = repeatKind,
    weekdays = if (repeatKind == RepeatKind.WEEKLY || repeatKind == RepeatKind.WEEKDAYS) weekdays else emptySet(),
    monthDay = if (repeatKind == RepeatKind.MONTHLY || repeatKind == RepeatKind.YEARLY) monthDay else null,
    totalCount = repeatCount,
    occurrenceIndex = previous?.occurrenceIndex ?: 1,
    interval = repeatInterval.coerceAtLeast(1),
    skipHolidays = skipHolidays,
    excludedDates = previous?.excludedDates ?: emptySet(),
    repeatUntil = previous?.repeatUntil
)

class TickTickViewModel(private val repository: TaskRepository) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        runBlocking(Dispatchers.IO) {
            val pulled = repository.refreshFromCloud()
            if (shouldSeedDemo(repository.cloudSyncEnabled(), pulled, repository.storedCount(), repository.current().size)) {
                demoTasks(LocalDateTime.now()).forEach { repository.upsert(it) }
                if (repository.cloudSyncEnabled()) repository.refreshFromCloud()
            }
            _state.value = AppUiState(tasks = repository.current())
        }
        viewModelScope.launch {
            repository.observeAll().collect { tasks -> _state.update { it.copy(tasks = tasks) } }
        }
        viewModelScope.launch(Dispatchers.IO) { repository.syncPending() }
    }

    fun navigate(page: AppPage) = _state.update { it.copy(page = page, message = null) }
    fun setTodayMode(mode: TodayMode) = _state.update { it.copy(todayMode = mode) }
    fun moveCalendar(days: Long) = _state.update { it.copy(calendarAnchor = it.calendarAnchor.plusDays(days)) }
    fun focusDate(date: LocalDate) = _state.update { it.copy(calendarAnchor = date) }
    fun alignAnchorToWeek(monday: LocalDate) = _state.update { state ->
        val currentMonday = state.calendarAnchor.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        if (currentMonday == monday) state
        else state.copy(calendarAnchor = monday.plusDays((state.calendarAnchor.dayOfWeek.value - 1).toLong()))
    }
    fun toggleShowCompleted() = _state.update { it.copy(showCompleted = !it.showCompleted) }
    fun setSearch(query: String) = _state.update { it.copy(searchQuery = query) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun create(date: LocalDate? = LocalDate.now()) = _state.update {
        it.copy(editor = EditorState(startDate = date, endDate = date, visibleMonth = YearMonth.from(date ?: LocalDate.now())))
    }

    fun edit(task: Task, onDate: LocalDate? = null) = _state.update {
        it.copy(editor = EditorState(
            editingId = task.id,
            title = task.title,
            startDate = task.startDate,
            endDate = task.endDate,
            time = task.time,
            dateMode = if (task.startDate != null && task.endDate != null && task.startDate != task.endDate) DateMode.RANGE else DateMode.SINGLE,
            reminderEnabled = task.reminderAt != null,
            reminderLabel = if (task.reminderAt != null) "准时提醒" else "关闭",
            repeatKind = task.repeatRule.kind,
            repeatCount = task.repeatRule.totalCount,
            repeatInterval = task.repeatRule.interval,
            repeatUnit = when (task.repeatRule.kind) {
                RepeatKind.DAILY -> RepeatUnit.DAY
                RepeatKind.MONTHLY -> RepeatUnit.MONTH
                RepeatKind.YEARLY -> RepeatUnit.YEAR
                else -> RepeatUnit.WEEK
            },
            weekdays = task.repeatRule.weekdays,
            monthDay = task.repeatRule.monthDay ?: task.startDate?.dayOfMonth,
            skipHolidays = task.repeatRule.skipHolidays,
            visibleMonth = YearMonth.from(onDate ?: task.startDate ?: LocalDate.now()),
            occurrenceDate = onDate ?: task.startDate
        ))
    }

    fun closeEditor() = _state.update { it.copy(editor = null, repeatScopePrompt = false, deleteScopePrompt = false) }
    fun updateEditor(transform: (EditorState) -> EditorState) = _state.update { state ->
        state.copy(editor = state.editor?.let(transform))
    }

    fun onTitleChange(text: String) = _state.update { state ->
        val editor = state.editor ?: return@update state
        val parsed = SmartSchedule.parse(text, ZonedDateTime.now(ZoneId.of("Asia/Shanghai")))
        val apply = parsed.confident && (parsed.matchedText != editor.parsedSpan || (parsed.time != null && parsed.time != editor.time))
        state.copy(editor = if (!apply) editor.copy(title = text) else editor.copy(
            title = text,
            startDate = parsed.startDate ?: editor.startDate,
            endDate = if (editor.dateMode == DateMode.RANGE) editor.endDate else parsed.endDate ?: parsed.startDate ?: editor.endDate,
            time = parsed.time ?: editor.time,
            reminderEnabled = if (parsed.time != null) true else editor.reminderEnabled,
            reminderLabel = if (parsed.time != null) "准时提醒" else editor.reminderLabel,
            repeatKind = parsed.repeatKind ?: editor.repeatKind,
            repeatUnit = parsed.repeatUnit ?: editor.repeatUnit,
            weekdays = parsed.weekdays.ifEmpty { editor.weekdays },
            monthDay = parsed.monthDay ?: editor.monthDay,
            repeatInterval = parsed.interval ?: editor.repeatInterval,
            parsedSpan = parsed.matchedText ?: editor.parsedSpan,
            visibleMonth = parsed.startDate?.let(YearMonth::from) ?: editor.visibleMonth
        ))
    }

    fun pickDate(day: LocalDate) = updateEditor { editor ->
        if (editor.dateMode == DateMode.SINGLE) editor.copy(startDate = day, endDate = day, visibleMonth = YearMonth.from(day), rangePickingEnd = false)
        else if (!editor.rangePickingEnd) editor.copy(startDate = day, endDate = day, rangePickingEnd = true, visibleMonth = YearMonth.from(day))
        else {
            val start = minOf(editor.startDate ?: day, day)
            val end = maxOf(editor.startDate ?: day, day)
            editor.copy(startDate = start, endDate = end, rangePickingEnd = false, visibleMonth = YearMonth.from(start))
        }
    }

    fun confirmRepeat() = updateEditor { editor ->
        val unit = editor.repeatUnit
        val days = if (unit == RepeatUnit.WEEK) editor.weekdays.ifEmpty { setOf(editor.startDate?.dayOfWeek?.value ?: LocalDate.now().dayOfWeek.value) } else editor.weekdays
        val monthDay = editor.monthDay ?: editor.startDate?.dayOfMonth ?: LocalDate.now().dayOfMonth
        editor.copy(
            repeatKind = when (unit) {
                RepeatUnit.DAY -> RepeatKind.DAILY
                RepeatUnit.WEEK -> RepeatKind.WEEKLY
                RepeatUnit.MONTH -> RepeatKind.MONTHLY
                RepeatUnit.YEAR -> RepeatKind.YEARLY
            },
            weekdays = days,
            monthDay = monthDay,
            panel = EditorPanel.DATE
        )
    }

    fun clearRepeat() = updateEditor { it.copy(repeatKind = RepeatKind.NONE, weekdays = emptySet(), panel = EditorPanel.DATE) }

    fun saveEditor() {
        val state = _state.value
        val editor = state.editor ?: return
        if (editor.title.isBlank() || invalidDates(editor)) return
        val existing = state.tasks.firstOrNull { it.id == editor.editingId }
        if (existing != null && existing.repeatRule.kind != RepeatKind.NONE) {
            _state.update { it.copy(repeatScopePrompt = true) }
            return
        }
        store(buildTask(state, editor))
    }

    fun confirmRepeatEdit() {
        val state = _state.value
        val editor = state.editor ?: return
        store(buildTask(state, editor))
    }

    private fun buildTask(state: AppUiState, editor: EditorState): Task {
        val stamp = LocalDateTime.now()
        val old = state.tasks.firstOrNull { it.id == editor.editingId }
        val reminder = if (editor.reminderEnabled && editor.startDate != null) {
            LocalDateTime.of(editor.startDate, editor.time ?: LocalTime.of(9, 0))
        } else null
        return Task(
            id = old?.id ?: UUID.randomUUID().toString(),
            seriesId = old?.seriesId,
            title = editor.title.trim(),
            startDate = editor.startDate,
            endDate = editor.endDate,
            time = editor.time,
            reminderAt = reminder,
            repeatRule = editor.toRepeatRule(old?.repeatRule),
            status = old?.status ?: TaskStatus.ACTIVE,
            syncState = SyncState.PENDING,
            createdAt = old?.createdAt ?: stamp,
            updatedAt = stamp
        )
    }

    private fun store(task: Task) {
        runBlocking(Dispatchers.IO) { repository.upsert(task) }
        _state.update { state ->
            state.copy(tasks = state.tasks.filterNot { it.id == task.id } + task, editor = null, repeatScopePrompt = false, deleteScopePrompt = false)
        }
        viewModelScope.launch(Dispatchers.IO) { repository.syncPending() }
    }

    fun toggleComplete(id: String) {
        val task = _state.value.tasks.firstOrNull { it.id == id } ?: return
        val completing = task.status == TaskStatus.ACTIVE
        store(task.copy(status = if (completing) TaskStatus.COMPLETED else TaskStatus.ACTIVE, updatedAt = LocalDateTime.now(), syncState = SyncState.PENDING))
        _state.update { it.copy(lastCompletedId = if (completing) id else null) }
    }

    fun abandon(id: String) {
        val task = _state.value.tasks.firstOrNull { it.id == id } ?: return
        if (task.status != TaskStatus.ACTIVE) return
        store(task.copy(status = TaskStatus.ABANDONED, updatedAt = LocalDateTime.now(), syncState = SyncState.PENDING))
    }

    fun moveToInbox(id: String) {
        val task = _state.value.tasks.firstOrNull { it.id == id } ?: return
        if (TaskLifecycle.isInbox(task)) return
        store(TaskLifecycle.moveToInbox(task).copy(updatedAt = LocalDateTime.now(), syncState = SyncState.PENDING))
    }

    fun parkEditorInInbox() {
        val editor = _state.value.editor ?: return
        val editingId = editor.editingId
        if (editingId != null) {
            moveToInbox(editingId)
            return
        }
        updateEditor {
            it.copy(
                startDate = null,
                endDate = null,
                time = null,
                reminderEnabled = false,
                reminderLabel = "关闭",
                repeatKind = RepeatKind.NONE,
                weekdays = emptySet(),
                panel = EditorPanel.NONE
            )
        }
    }

    fun visibleInboxTasks(): List<Task> = TaskLifecycle.inboxItems(_state.value.tasks)

    fun undoComplete() {
        val id = _state.value.lastCompletedId ?: return
        toggleComplete(id)
        _state.update { it.copy(lastCompletedId = null) }
    }

    fun requestDelete() {
        val state = _state.value
        val task = state.tasks.firstOrNull { it.id == state.editor?.editingId } ?: return
        if (task.repeatRule.kind != RepeatKind.NONE) _state.update { it.copy(deleteScopePrompt = true) }
        else remove(task, futureSeries = false)
    }

    fun confirmDelete(future: Boolean) {
        val state = _state.value
        val editor = state.editor ?: return
        val current = state.tasks.firstOrNull { it.id == editor.editingId } ?: return
        val date = editor.occurrenceDate ?: current.startDate ?: LocalDate.now()
        if (!future) {
            store(TaskLifecycle.excludingOccurrence(current, date).copy(updatedAt = LocalDateTime.now(), syncState = SyncState.PENDING))
            return
        }
        val kept = TaskLifecycle.endingBefore(current, date)
        if (kept == null) remove(current, futureSeries = true)
        else store(kept.copy(updatedAt = LocalDateTime.now(), syncState = SyncState.PENDING))
    }

    private fun remove(task: Task, futureSeries: Boolean) {
        runBlocking(Dispatchers.IO) { repository.delete(task.id, futureSeries) }
        _state.update { state ->
            val tasks = if (futureSeries && task.seriesId != null) {
                state.tasks.filterNot { it.seriesId == task.seriesId && (it.startDate ?: LocalDate.MIN) >= (task.startDate ?: LocalDate.MIN) }
            } else state.tasks.filterNot { it.id == task.id }
            state.copy(tasks = tasks, editor = null, deleteScopePrompt = false)
        }
        viewModelScope.launch(Dispatchers.IO) { repository.syncPending() }
    }

    fun visibleSearchResults(): List<Task> {
        val state = _state.value
        val source = if (state.searchQuery.isBlank()) state.tasks.sortedByDescending { it.updatedAt }.take(8)
        else TaskSearch.filter(state.tasks, state.searchQuery)
        return source.sortedWith(compareBy<Task> { it.status != TaskStatus.ACTIVE }.thenBy { it.startDate ?: LocalDate.MAX })
    }

    private fun invalidDates(editor: EditorState) = editor.startDate != null && editor.endDate != null && editor.endDate < editor.startDate
}

internal fun shouldSeedDemo(cloudSyncEnabled: Boolean, cloudReached: Boolean, storedCount: Int, visibleCount: Int): Boolean {
    if (!cloudSyncEnabled) return visibleCount == 0
    return cloudReached && storedCount == 0
}

private fun demoTasks(now: LocalDateTime): List<Task> = listOf(
    Task("read", title = "阅读书籍", startDate = now.toLocalDate(), endDate = now.toLocalDate(), time = LocalTime.of(13, 0), reminderAt = now.toLocalDate().atTime(13, 0), repeatRule = RepeatRule(RepeatKind.DAILY), createdAt = now, updatedAt = now),
    Task("material", title = "提交材料", startDate = now.toLocalDate().minusDays(2), endDate = now.toLocalDate().minusDays(2), createdAt = now.minusDays(2), updatedAt = now.minusDays(2)),
    Task("trip", title = "出差准备", startDate = now.toLocalDate().plusDays(1), endDate = now.toLocalDate().plusDays(2), createdAt = now, updatedAt = now),
    Task("sport", title = "运动", startDate = now.toLocalDate(), endDate = now.toLocalDate(), time = LocalTime.of(17, 0), status = TaskStatus.COMPLETED, createdAt = now, updatedAt = now)
)

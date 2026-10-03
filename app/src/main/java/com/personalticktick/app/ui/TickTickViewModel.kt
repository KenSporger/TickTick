package com.personalticktick.app.ui

import androidx.lifecycle.ViewModel
import com.personalticktick.app.domain.RepeatKind
import com.personalticktick.app.domain.RepeatRule
import com.personalticktick.app.domain.SyncState
import com.personalticktick.app.domain.Task
import com.personalticktick.app.domain.TaskSearch
import com.personalticktick.app.domain.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

enum class AppPage { TODAY, CALENDAR, SEARCH }
enum class CalendarMode { WEEK, MONTH }
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
    val panel: EditorPanel = EditorPanel.NONE
)

data class AppUiState(
    val page: AppPage = AppPage.TODAY,
    val todayMode: TodayMode = TodayMode.TODAY,
    val calendarMode: CalendarMode = CalendarMode.WEEK,
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

class TickTickViewModel : ViewModel() {
    private val now = LocalDateTime.now()
    private val _state = MutableStateFlow(AppUiState(tasks = demoTasks(now)))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun navigate(page: AppPage) = _state.update { it.copy(page = page, message = null) }
    fun setTodayMode(mode: TodayMode) = _state.update { it.copy(todayMode = mode) }
    fun setCalendarMode(mode: CalendarMode) = _state.update { it.copy(calendarMode = mode) }
    fun moveCalendar(days: Long) = _state.update { it.copy(calendarAnchor = it.calendarAnchor.plusDays(days)) }
    fun toggleShowCompleted() = _state.update { it.copy(showCompleted = !it.showCompleted) }
    fun setSearch(query: String) = _state.update { it.copy(searchQuery = query) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun create(date: LocalDate? = LocalDate.now()) = _state.update {
        it.copy(editor = EditorState(startDate = date, endDate = date))
    }

    fun edit(task: Task) = _state.update {
        it.copy(editor = EditorState(
            editingId = task.id,
            title = task.title,
            startDate = task.startDate,
            endDate = task.endDate,
            time = task.time,
            dateMode = if (task.startDate != task.endDate) DateMode.RANGE else DateMode.SINGLE,
            reminderEnabled = task.reminderAt != null,
            reminderLabel = if (task.reminderAt != null) "准时" else "关闭",
            repeatKind = task.repeatRule.kind,
            repeatCount = task.repeatRule.totalCount
        ))
    }

    fun closeEditor() = _state.update { it.copy(editor = null, repeatScopePrompt = false, deleteScopePrompt = false) }
    fun updateEditor(transform: (EditorState) -> EditorState) = _state.update { state ->
        state.copy(editor = state.editor?.let(transform))
    }

    fun saveEditor() = _state.update { state ->
        val editor = state.editor ?: return@update state
        if (editor.title.isBlank() || invalidDates(editor)) return@update state.copy(message = "请填写有效的任务和日期")
        val existing = state.tasks.firstOrNull { it.id == editor.editingId }
        if (existing?.repeatRule?.kind != null && existing.repeatRule.kind != RepeatKind.NONE) {
            return@update state.copy(repeatScopePrompt = true)
        }
        saveIntoState(state, editor)
    }

    fun confirmRepeatEdit() = _state.update { state ->
        state.editor?.let { saveIntoState(state.copy(repeatScopePrompt = false), it) } ?: state
    }

    private fun saveIntoState(state: AppUiState, editor: EditorState): AppUiState {
        val stamp = LocalDateTime.now()
        val old = state.tasks.firstOrNull { it.id == editor.editingId }
        val reminder = if (editor.reminderEnabled && editor.startDate != null) {
            LocalDateTime.of(editor.startDate, editor.time ?: LocalTime.of(9, 0))
        } else null
        val task = Task(
            id = old?.id ?: UUID.randomUUID().toString(),
            seriesId = old?.seriesId,
            title = editor.title.trim(),
            startDate = editor.startDate,
            endDate = editor.endDate,
            time = editor.time,
            reminderAt = reminder,
            repeatRule = RepeatRule(editor.repeatKind, totalCount = editor.repeatCount),
            status = old?.status ?: TaskStatus.ACTIVE,
            syncState = SyncState.PENDING,
            createdAt = old?.createdAt ?: stamp,
            updatedAt = stamp
        )
        return state.copy(
            tasks = state.tasks.filterNot { it.id == task.id } + task,
            editor = null,
            message = if (old == null) "任务已保存" else "修改已保存"
        )
    }

    fun toggleComplete(id: String) = _state.update { state ->
        val task = state.tasks.firstOrNull { it.id == id } ?: return@update state
        val completing = task.status != TaskStatus.COMPLETED
        state.copy(
            tasks = state.tasks.map {
                if (it.id == id) it.copy(status = if (completing) TaskStatus.COMPLETED else TaskStatus.ACTIVE, updatedAt = LocalDateTime.now()) else it
            },
            message = if (completing) "任务已完成" else "任务已恢复",
            lastCompletedId = if (completing) id else null
        )
    }

    fun undoComplete() {
        val id = _state.value.lastCompletedId ?: return
        toggleComplete(id)
        _state.update { it.copy(lastCompletedId = null, message = "已撤销") }
    }

    fun requestDelete() = _state.update { state ->
        val task = state.tasks.firstOrNull { it.id == state.editor?.editingId }
        if (task?.repeatRule?.kind != RepeatKind.NONE) state.copy(deleteScopePrompt = true) else deleteEditor(false, state)
    }

    fun confirmDelete(future: Boolean) = _state.update { deleteEditor(future, it) }
    private fun deleteEditor(future: Boolean, state: AppUiState): AppUiState {
        val id = state.editor?.editingId ?: return state
        val current = state.tasks.firstOrNull { it.id == id }
        val filtered = if (future && current?.seriesId != null) state.tasks.filterNot { it.seriesId == current.seriesId && (it.startDate ?: LocalDate.MIN) >= (current.startDate ?: LocalDate.MIN) }
        else state.tasks.filterNot { it.id == id }
        return state.copy(tasks = filtered, editor = null, deleteScopePrompt = false, message = "任务已删除")
    }

    fun visibleSearchResults(): List<Task> {
        val state = _state.value
        val source = if (state.searchQuery.isBlank()) state.tasks.sortedByDescending { it.updatedAt }.take(8)
        else TaskSearch.filter(state.tasks, state.searchQuery)
        return source.sortedWith(compareBy<Task> { it.status == TaskStatus.COMPLETED }.thenBy { it.startDate ?: LocalDate.MAX })
    }

    private fun invalidDates(editor: EditorState) = editor.startDate != null && editor.endDate != null && editor.endDate < editor.startDate
}

private fun demoTasks(now: LocalDateTime): List<Task> = listOf(
    Task("read", title = "阅读书籍", startDate = now.toLocalDate(), endDate = now.toLocalDate(), time = LocalTime.of(13, 0), reminderAt = now.toLocalDate().atTime(13, 0), repeatRule = RepeatRule(RepeatKind.DAILY), createdAt = now, updatedAt = now),
    Task("material", title = "提交材料", startDate = now.toLocalDate().minusDays(2), endDate = now.toLocalDate().minusDays(2), createdAt = now.minusDays(2), updatedAt = now.minusDays(2)),
    Task("trip", title = "出差准备", startDate = now.toLocalDate().plusDays(1), endDate = now.toLocalDate().plusDays(2), createdAt = now, updatedAt = now),
    Task("sport", title = "运动", startDate = now.toLocalDate(), endDate = now.toLocalDate(), time = LocalTime.of(17, 0), status = TaskStatus.COMPLETED, createdAt = now, updatedAt = now)
)

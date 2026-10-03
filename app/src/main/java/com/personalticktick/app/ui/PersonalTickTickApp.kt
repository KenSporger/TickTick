package com.personalticktick.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalticktick.app.domain.ChinaHolidays
import com.personalticktick.app.domain.RepeatKind
import com.personalticktick.app.domain.RepeatUnit
import com.personalticktick.app.domain.Task
import com.personalticktick.app.domain.TaskLifecycle
import com.personalticktick.app.domain.TaskStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val Bg = Color(0xFF121315)
private val Card = Color(0xFF202226)
private val Elevated = Color(0xFF2C2C2E)
private val Primary = Color(0xFF4C7DFF)
private val TextMain = Color(0xFFF2F3F5)
private val TextMuted = Color(0xFF8E939C)
private val Danger = Color(0xFFE85D5D)
private val Holiday = Color(0xFF3DDC97)
private val TaskBlue = Color(0xFF1A4066)
private val Line = Color(0xFF3A3D44)

private fun Modifier.tagged(value: String) = testTag(value).semantics { contentDescription = value }

internal fun dayHeading(day: LocalDate): String {
    val name = "周${"一二三四五六日"[day.dayOfWeek.value - 1]}"
    val tail = if (day.dayOfMonth == 1) "${day.monthValue}月" else day.dayOfMonth.toString()
    return "$name $tail"
}

private fun Task.isDone() = status == TaskStatus.COMPLETED || status == TaskStatus.ABANDONED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalTickTickApp(viewModel: TickTickViewModel) {
    val state by viewModel.state.collectAsState()
    MaterialTheme(colorScheme = darkColorScheme(primary = Primary, background = Bg, surface = Card)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Bg,
            bottomBar = { BottomNavigation(state.page, viewModel::navigate) },
            floatingActionButton = {
                if (state.page != AppPage.SEARCH && state.editor == null) Surface(
                    modifier = Modifier.size(56.dp).clickable { viewModel.create(if (state.page == AppPage.CALENDAR) state.calendarAnchor else LocalDate.now()) }.tagged("创建任务"),
                    shape = CircleShape, color = Primary, shadowElevation = 8.dp
                ) { Box(contentAlignment = Alignment.Center) { Text("+", fontSize = 30.sp, color = Color.White) } }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (state.page) {
                    AppPage.TODAY -> TodayScreen(state, viewModel)
                    AppPage.CALENDAR -> CalendarScreen(state, viewModel)
                    AppPage.SEARCH -> SearchScreen(state, viewModel)
                }
            }
        }
        state.editor?.let { TaskEditorSheet(it, viewModel) }
        if (state.editor?.pickingTime == true) TimeDialog(state.editor?.time, { viewModel.updateEditor { it.copy(pickingTime = false) } }) { time ->
            viewModel.updateEditor { it.copy(time = time, pickingTime = false, reminderEnabled = true, reminderLabel = "准时提醒") }
        }
        if (state.repeatScopePrompt) ScopeDialog("修改重复任务", "这项修改要应用到哪里？", "仅本次", "本次及以后", { viewModel.confirmRepeatEdit() }, { viewModel.confirmRepeatEdit() }, viewModel::closeEditor)
        if (state.deleteScopePrompt) ScopeDialog("删除重复任务", "已经完成或跳过的历史记录会保留", "删除仅本次", "删除本次及以后", { viewModel.confirmDelete(false) }, { viewModel.confirmDelete(true) }, viewModel::closeEditor)
    }
}

@Composable
private fun BottomNavigation(page: AppPage, navigate: (AppPage) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card).navigationBarsPadding().height(58.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        NavItem("✓", "任务", page == AppPage.TODAY) { navigate(AppPage.TODAY) }
        NavItem("▦", "日历", page == AppPage.CALENDAR) { navigate(AppPage.CALENDAR) }
        NavItem("⌕", "搜索", page == AppPage.SEARCH) { navigate(AppPage.SEARCH) }
        NavItem("◇", "我的", false) { }
    }
}

@Composable private fun NavItem(symbol: String, label: String, selected: Boolean, click: () -> Unit) {
    Column(Modifier.width(64.dp).clickable(onClick = click).tagged(label), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, fontSize = 22.sp, color = if (selected) Primary else TextMuted)
        Text(label, fontSize = 11.sp, color = if (selected) Primary else TextMuted)
    }
}

@Composable
private fun TodayScreen(state: AppUiState, vm: TickTickViewModel) {
    val today = LocalDate.now()
    val overdue = state.tasks.filter { TaskLifecycle.isOverdue(it, today) }
    val todayTasks = state.tasks.filter { TaskLifecycle.occursOn(it, today) }.sortedBy { it.status != TaskStatus.ACTIVE }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Header("今天")
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab("已过期${if (overdue.isNotEmpty()) " ${overdue.size}" else ""}", state.todayMode == TodayMode.OVERDUE) { vm.setTodayMode(TodayMode.OVERDUE) }
            Tab("今天", state.todayMode == TodayMode.TODAY) { vm.setTodayMode(TodayMode.TODAY) }
        }
        if (state.todayMode == TodayMode.OVERDUE) {
            LazyColumn(Modifier.fillMaxSize().tagged("overdue-screen")) {
                if (overdue.isEmpty()) item { EmptyState("没有已过期任务", "都处理好了") }
                overdue.groupBy { it.endDate }.toSortedMap(compareByDescending { it }).forEach { (date, group) ->
                    item { Text(date?.format(DateTimeFormatter.ofPattern("M月d日")) ?: "", color = TextMuted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }
                    items(group, key = { it.id }) { TaskCard(it, true, vm, onDate = date) }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            if (todayTasks.isEmpty()) item { EmptyState("今天没有任务", "点右下角创建一个任务") }
            items(todayTasks, key = { it.id }) { TaskCard(it, false, vm, onDate = today) }
        }
    }
}

@Composable private fun Header(title: String) {
    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("☰", fontSize = 23.sp, color = TextMuted)
        Text(title, Modifier.padding(start = 22.dp).weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        Text("◇   ⋮", color = TextMuted, fontSize = 21.sp)
    }
}

@Composable private fun Tab(label: String, selected: Boolean, click: () -> Unit) {
    Column(Modifier.fillMaxHeight().clickable(onClick = click).padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (selected) TextMain else TextMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(2.dp).width(42.dp).background(if (selected) Primary else Color.Transparent))
    }
}

@Composable
private fun TaskCard(task: Task, overdue: Boolean, vm: TickTickViewModel, compact: Boolean = false, onDate: LocalDate? = null) {
    val done = task.isDone()
    val dateText = task.time?.format(DateTimeFormatter.ofPattern("HH:mm"))
        ?: task.endDate?.format(DateTimeFormatter.ofPattern("M月d日")) ?: "无日期"
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (compact) 4.dp else 9.dp).clickable { vm.edit(task, onDate) },
        color = if (done) Color(0xFF1A1B1E) else Card, shape = RoundedCornerShape(if (compact) 5.dp else 10.dp)
    ) {
        Row(Modifier.padding(if (compact) 7.dp else 13.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusMark(abandoned = task.status == TaskStatus.ABANDONED, completed = task.status == TaskStatus.COMPLETED, compact = compact) { vm.toggleComplete(task.id) }
            Text(task.title, Modifier.padding(start = 7.dp).weight(1f).semantics { if (task.status == TaskStatus.ABANDONED) contentDescription = "已放弃 ${task.title}" }, color = if (done) TextMuted else TextMain, fontSize = if (compact) 12.sp else 16.sp, textDecoration = if (done) TextDecoration.LineThrough else null, maxLines = 2)
            if (task.repeatRule.kind != RepeatKind.NONE) Text("↻ ", color = TextMuted)
            if (task.reminderAt != null) Text("♧ ", color = TextMuted)
            if (!compact && task.status == TaskStatus.ACTIVE) Text("放弃", color = Danger, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp).clickable { vm.abandon(task.id) }.tagged("abandon-task"))
            Text(dateText, color = if (overdue && !done) Danger else TextMuted, fontSize = if (compact) 10.sp else 13.sp)
        }
    }
}

@Composable private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✓", fontSize = 42.sp, color = Elevated)
        Text(title, color = TextMain, fontSize = 17.sp)
        Text(subtitle, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CalendarScreen(state: AppUiState, vm: TickTickViewModel) {
    val today = remember { LocalDate.now() }
    val baseMonday = remember { today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }
    val pager = rememberPagerState(initialPage = 1000, pageCount = { 2000 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            vm.alignAnchorToWeek(baseMonday.plusWeeks((page - 1000).toLong()))
        }
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().tagged("month-screen"), beyondViewportPageCount = 0) { page ->
        val monday = baseMonday.plusWeeks((page - 1000).toLong())
        MonthPage(monday, state, vm) { date ->
            vm.focusDate(date)
            val target = 1000 + ChronoUnit.WEEKS.between(baseMonday, date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))).toInt()
            scope.launch { pager.animateScrollToPage(target) }
        }
    }
}

@Composable
private fun MonthPage(monday: LocalDate, state: AppUiState, vm: TickTickViewModel, onPick: (LocalDate) -> Unit) {
    val days = (0L..6L).map(monday::plusDays)
    val month = YearMonth.from(monday.plusDays(3))
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${month.monthValue}月", Modifier.weight(1f), color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("⋮", Modifier.clickable { vm.toggleShowCompleted() }.padding(8.dp), color = TextMuted, fontSize = 22.sp)
        }
        Row(Modifier.weight(1.45f)) {
            MiniMonth(month, state.calendarAnchor, Modifier.weight(1f).fillMaxHeight(), onPick)
            DayColumn(days[0], state, vm, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(1f)) {
            DayColumn(days[1], state, vm, Modifier.weight(1f).fillMaxHeight())
            DayColumn(days[2], state, vm, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(1f)) {
            DayColumn(days[3], state, vm, Modifier.weight(1f).fillMaxHeight())
            DayColumn(days[4], state, vm, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(1f)) {
            DayColumn(days[5], state, vm, Modifier.weight(1f).fillMaxHeight())
            DayColumn(days[6], state, vm, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun MiniMonth(month: YearMonth, selected: LocalDate, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val rows = ((lead + month.lengthOfMonth()) + 6) / 7
    Column(modifier.padding(end = 4.dp).tagged("compact-month-calendar")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            "一二三四五六日".forEach { Text(it.toString(), color = TextMuted, fontSize = 11.sp) }
        }
        Column(Modifier.weight(1f)) {
            repeat(rows) { week ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(7) { col ->
                        val day = first.minusDays(lead.toLong()).plusDays((week * 7 + col).toLong())
                        val inMonth = YearMonth.from(day) == month
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clickable { onPick(day) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(if (inMonth && ChinaHolidays.isHoliday(day)) "休" else " ", color = Holiday, fontSize = 8.sp, lineHeight = 8.sp)
                            Box(
                                Modifier.size(22.dp).background(if (day == selected) Primary else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    color = when {
                                        day == selected -> Color.White
                                        !inMonth -> Color(0xFF6B7078)
                                        day == LocalDate.now() -> Primary
                                        else -> TextMain
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(day: LocalDate, state: AppUiState, vm: TickTickViewModel, modifier: Modifier) {
    val tasks = state.tasks.filter { TaskLifecycle.occursOn(it, day) && (state.showCompleted || !it.isDone() || TaskLifecycle.isFutureOccurrence(it, day)) }
        .sortedBy { it.isDone() && !TaskLifecycle.isFutureOccurrence(it, day) }
    val today = day == LocalDate.now()
    Column(modifier.padding(3.dp).tagged("month-day-panel")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayHeading(day), color = if (today) Primary else TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (ChinaHolidays.isHoliday(day)) Text(" 休", color = Holiday, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            tasks.forEach { task ->
                val projected = TaskLifecycle.isFutureOccurrence(task, day)
                val done = !projected && task.isDone()
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(5.dp)).background(if (done) Color(0xFF1A1C20) else TaskBlue).clickable { vm.edit(task, day) }.padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusMark(abandoned = !projected && task.status == TaskStatus.ABANDONED, completed = !projected && task.status == TaskStatus.COMPLETED, compact = true) { if (!projected) vm.toggleComplete(task.id) }
                    Text(task.title, Modifier.weight(1f), color = if (done) TextMuted else TextMain, fontSize = 11.sp, maxLines = 1, textDecoration = if (done) TextDecoration.LineThrough else null)
                    task.time?.let { Text(it.format(DateTimeFormatter.ofPattern("HH:mm")), color = TextMuted, fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable private fun SearchScreen(state: AppUiState, vm: TickTickViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        TextField(
            value = state.searchQuery,
            onValueChange = vm::setSearch,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).tagged("search-input"),
            placeholder = { Text("搜索任务名称…") },
            leadingIcon = { Text("⌕", color = TextMuted) },
            trailingIcon = { if (state.searchQuery.isNotEmpty()) Text("×", Modifier.clickable { vm.setSearch("") }.padding(8.dp), color = TextMuted) },
            singleLine = true,
            colors = fieldColors()
        )
        Text(if (state.searchQuery.isBlank()) "最近编辑" else "搜索结果", color = TextMuted, modifier = Modifier.padding(vertical = 10.dp))
        val results = vm.visibleSearchResults()
        if (results.isEmpty()) EmptyState("没有找到相关任务", "试试其他关键词") else LazyColumn { items(results, key = { it.id }) { TaskCard(it, it.status == TaskStatus.ACTIVE && it.endDate?.isBefore(LocalDate.now()) == true, vm) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskEditorSheet(editor: EditorState, vm: TickTickViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = vm::closeEditor,
        sheetState = sheetState,
        containerColor = Elevated,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF5C6068)) },
        modifier = Modifier.tagged("task-editor-sheet")
    ) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            when (editor.panel) {
                EditorPanel.NONE -> Composer(editor, vm)
                EditorPanel.DATE -> DateSheet(editor, vm)
                EditorPanel.REPEAT -> RepeatSheet(editor, vm)
                EditorPanel.REMINDER -> ReminderSheet(editor, vm)
            }
        }
    }
}

@Composable private fun Composer(editor: EditorState, vm: TickTickViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
        TextField(
            value = editor.title,
            onValueChange = vm::onTitleChange,
            modifier = Modifier.fillMaxWidth().tagged("task-title-input"),
            placeholder = { Text("准备做什么？", color = TextMuted) },
            textStyle = TextStyle(fontSize = 18.sp, color = TextMain),
            colors = fieldColors()
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.border(1.dp, Color(0xFF3E4E6A), RoundedCornerShape(8.dp)).clickable { vm.updateEditor { it.copy(panel = EditorPanel.DATE, visibleMonth = YearMonth.from(it.startDate ?: LocalDate.now())) } }.padding(horizontal = 10.dp, vertical = 8.dp).tagged("打开日期"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("▦  ${dateChip(editor)}", color = Primary, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            if (editor.editingId != null && vm.state.value.tasks.firstOrNull { it.id == editor.editingId }?.status == TaskStatus.ACTIVE) {
                Text("放弃", color = TextMuted, modifier = Modifier.padding(end = 14.dp).clickable { vm.abandon(editor.editingId) }.tagged("abandon-task"))
            }
            if (editor.editingId != null) Text("删除", color = Danger, modifier = Modifier.padding(end = 14.dp).clickable { vm.requestDelete() }.tagged("删除"))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (editor.title.isBlank()) Color(0xFF3A4558) else Primary).clickable(enabled = editor.title.isNotBlank()) { vm.saveEditor() }.tagged("保存"),
                contentAlignment = Alignment.Center
            ) { Text("➤", color = Color.White, fontSize = 18.sp) }
        }
    }
}

@Composable private fun DateSheet(editor: EditorState, vm: TickTickViewModel) {
    val month = editor.visibleMonth
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - 1)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 18.dp).tagged("date-picker")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("×", Modifier.clickable { vm.updateEditor { it.copy(panel = EditorPanel.NONE) } }.padding(8.dp), color = TextMain, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.clickable { vm.updateEditor { it.copy(dateMode = DateMode.SINGLE, endDate = it.startDate, rangePickingEnd = false) } }.padding(end = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("日期", color = if (editor.dateMode == DateMode.SINGLE) TextMain else TextMuted, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Box(Modifier.padding(top = 4.dp).height(2.dp).width(28.dp).background(if (editor.dateMode == DateMode.SINGLE) Primary else Color.Transparent))
            }
            Text("时间段", Modifier.clickable { vm.updateEditor { it.copy(dateMode = DateMode.RANGE, endDate = it.endDate ?: it.startDate, rangePickingEnd = false) } }, color = if (editor.dateMode == DateMode.RANGE) TextMain else TextMuted, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text("✓", Modifier.clickable { vm.updateEditor { it.copy(panel = EditorPanel.NONE) } }.padding(8.dp).tagged("完成日期"), color = TextMain, fontSize = 22.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${month.monthValue}月", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("‹", Modifier.clickable { vm.updateEditor { it.copy(visibleMonth = it.visibleMonth.minusMonths(1)) } }.padding(10.dp), color = TextMain, fontSize = 22.sp)
            Text("›", Modifier.clickable { vm.updateEditor { it.copy(visibleMonth = it.visibleMonth.plusMonths(1)) } }.padding(10.dp), color = TextMain, fontSize = 22.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            "一二三四五六日".forEach { Text(it.toString(), color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                repeat(7) { col ->
                    val day = first.minusDays(lead.toLong()).plusDays((week * 7 + col).toLong())
                    val inMonth = YearMonth.from(day) == month
                    val selected = editor.startDate == day || (editor.dateMode == DateMode.RANGE && editor.endDate == day)
                    val inRange = editor.dateMode == DateMode.RANGE && editor.startDate != null && editor.endDate != null && !day.isBefore(editor.startDate) && !day.isAfter(editor.endDate)
                    Column(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(8.dp)).background(if (inRange && inMonth && !selected) Color(0xFF24344A) else Color.Transparent).clickable(enabled = inMonth) { vm.pickDate(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(if (inMonth && ChinaHolidays.isHoliday(day)) "休" else " ", color = Holiday, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1)
                        Box(Modifier.size(26.dp).background(if (selected && inMonth) Primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                            Text(if (inMonth) day.dayOfMonth.toString() else "", color = if (selected) Color.White else TextMain, fontSize = 15.sp)
                        }
                        Text(if (inMonth) ChinaHolidays.festival(day).orEmpty() else " ", color = Holiday, fontSize = 9.sp, maxLines = 1, lineHeight = 10.sp)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp)) {
            SheetLine("时间", editor.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "无") { vm.updateEditor { it.copy(pickingTime = true) } }
            SheetLine("提醒", if (editor.reminderEnabled) editor.reminderLabel else "无") { vm.updateEditor { it.copy(panel = EditorPanel.REMINDER) } }
            SheetLine("重复", repeatLabel(editor), showDivider = false) { vm.openRepeatPanel() }
        }
        Text("清除", color = Danger, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp).clickable {
            vm.updateEditor { it.copy(startDate = null, endDate = null, time = null, reminderEnabled = false, reminderLabel = "关闭", panel = EditorPanel.NONE) }
        }, textAlign = TextAlign.Center)
    }
}

@Composable private fun SheetLine(label: String, value: String, showDivider: Boolean = true, click: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = click)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = TextMain, fontSize = 16.sp)
            Text(value, color = TextMuted, fontSize = 15.sp)
            Text("  ›", color = TextMuted)
        }
        if (showDivider) HorizontalDivider(color = Line)
    }
}

@Composable private fun RepeatSheet(editor: EditorState, vm: TickTickViewModel) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).tagged("repeat-picker")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("×", Modifier.clickable { vm.updateEditor { it.copy(panel = EditorPanel.DATE) } }.padding(8.dp), color = TextMain, fontSize = 22.sp)
            Text("自定义重复", Modifier.weight(1f), color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
            Text("✓", Modifier.clickable { vm.confirmRepeat() }.padding(8.dp).tagged("完成重复"), color = TextMain, fontSize = 22.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).background(Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("重复类型", color = TextMain)
            Spacer(Modifier.weight(1f))
            Text("按到期日期  ∨", color = TextMuted)
        }
        Text("频率", color = TextMuted, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().height(132.dp).background(Card, RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("每", color = TextMuted, fontSize = 18.sp, modifier = Modifier.padding(end = 28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                Text(if (editor.repeatInterval > 1) "${editor.repeatInterval - 1}" else "", color = Color(0xFF6E737B), fontSize = 16.sp, modifier = Modifier.clickable { vm.updateEditor { it.copy(repeatInterval = (it.repeatInterval - 1).coerceAtLeast(1)) } }.padding(vertical = 4.dp))
                Text("${editor.repeatInterval}", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${editor.repeatInterval + 1}", color = Color(0xFF6E737B), fontSize = 16.sp, modifier = Modifier.clickable { vm.updateEditor { it.copy(repeatInterval = (it.repeatInterval + 1).coerceAtMost(30)) } }.padding(vertical = 4.dp))
            }
            Column(Modifier.padding(start = 36.dp)) {
                listOf(RepeatUnit.DAY to "天", RepeatUnit.WEEK to "周", RepeatUnit.MONTH to "月", RepeatUnit.YEAR to "年").forEach { (unit, label) ->
                    val selected = editor.repeatUnit == unit
                    Text(label, color = if (selected) TextMain else Color(0xFF6E737B), fontSize = if (selected) 18.sp else 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { vm.updateEditor { it.copy(repeatUnit = unit) } }.padding(vertical = 2.dp))
                }
            }
        }
        Text(repeatSummary(editor), color = TextMain, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp).tagged("repeat-summary"))
        if (editor.repeatUnit == RepeatUnit.WEEK) {
            Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(14.dp)) {
                Text("星期", color = TextMuted, fontSize = 13.sp)
                val names = listOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六", 7 to "周日")
                names.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (iso, name) ->
                            val selected = iso in editor.weekdays
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) Primary else Color(0xFF3A3D44)).clickable {
                                    vm.updateEditor { editorState ->
                                        val next = if (iso in editorState.weekdays) editorState.weekdays - iso else editorState.weekdays + iso
                                        editorState.copy(weekdays = next)
                                    }
                                }.tagged("weekday-$iso"),
                                contentAlignment = Alignment.Center
                            ) { Text(name, color = if (selected) Color.White else TextMain, fontSize = 14.sp) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (editor.repeatUnit == RepeatUnit.MONTH) {
            Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(14.dp)) {
                Text("日期", color = TextMuted, fontSize = 13.sp)
                val days = (1..31).toList()
                days.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        row.forEach { day ->
                            val selected = editor.monthDay == day
                            Box(Modifier.weight(1f).height(34.dp).padding(2.dp).clip(CircleShape).background(if (selected) Primary else Color.Transparent).clickable { vm.updateEditor { it.copy(monthDay = day) } }.tagged("month-day-$day"), contentAlignment = Alignment.Center) {
                                Text(day.toString(), color = if (selected) Color.White else TextMain, fontSize = 13.sp)
                            }
                        }
                        repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).background(Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("跳过法定节假日", Modifier.weight(1f), color = TextMain)
            Switch(checked = editor.skipHolidays, onCheckedChange = { value -> vm.updateEditor { it.copy(skipHolidays = value) } }, colors = SwitchDefaults.colors(uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF3A3A3C), checkedThumbColor = Color.White, checkedTrackColor = Primary))
        }
        Text("不重复", color = TextMuted, modifier = Modifier.padding(top = 16.dp).clickable { vm.clearRepeat() }, fontSize = 14.sp)
    }
}

@Composable private fun ReminderSheet(editor: EditorState, vm: TickTickViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("×", Modifier.clickable { vm.updateEditor { it.copy(panel = EditorPanel.DATE) } }.padding(8.dp), color = TextMain, fontSize = 22.sp)
            Text("提醒", Modifier.weight(1f), color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("✓", Modifier.clickable { vm.updateEditor { it.copy(panel = EditorPanel.DATE) } }.padding(8.dp), color = TextMain, fontSize = 22.sp)
        }
        listOf("准时提醒", "提前 5 分钟", "提前 10 分钟", "提前 30 分钟", "关闭").forEach { option ->
            val selected = if (option == "关闭") !editor.reminderEnabled else editor.reminderEnabled && editor.reminderLabel == option
            Text(option, color = if (selected) Primary else TextMain, modifier = Modifier.fillMaxWidth().clickable {
                vm.updateEditor { it.copy(reminderEnabled = option != "关闭", reminderLabel = if (option == "关闭") "关闭" else option, panel = EditorPanel.DATE) }
            }.padding(vertical = 14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TimeDialog(current: LocalTime?, dismiss: () -> Unit, confirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = current?.hour ?: 9, initialMinute = current?.minute ?: 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = { TextButton(onClick = { confirm(LocalTime.of(state.hour, state.minute)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
        text = { TimePicker(state) }
    )
}

@Composable private fun ScopeDialog(title: String, subtitle: String, first: String, second: String, onFirst: () -> Unit, onSecond: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column { Text(subtitle); Text(first, Modifier.fillMaxWidth().clickable(onClick = onFirst).padding(vertical = 17.dp), color = TextMain); Text(second, Modifier.fillMaxWidth().clickable(onClick = onSecond).padding(vertical = 17.dp), color = TextMain) } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Primary,
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain
)

private fun TickTickViewModel.openRepeatPanel() = updateEditor { editor ->
    val weekday = editor.startDate?.dayOfWeek?.value ?: LocalDate.now().dayOfWeek.value
    editor.copy(
        panel = EditorPanel.REPEAT,
        weekdays = if (editor.weekdays.isEmpty()) setOf(weekday) else editor.weekdays,
        monthDay = editor.monthDay ?: editor.startDate?.dayOfMonth ?: LocalDate.now().dayOfMonth
    )
}

@Composable private fun StatusMark(abandoned: Boolean, completed: Boolean, compact: Boolean, onToggle: () -> Unit) {
    val size = if (compact) 18.dp else 22.dp
    Box(
        Modifier.padding(end = 2.dp).size(size).clip(RoundedCornerShape(4.dp))
            .border(1.5.dp, if (abandoned) Danger else if (completed) Primary else TextMuted, RoundedCornerShape(4.dp))
            .background(if (completed) Primary else Color.Transparent)
            .toggleable(value = abandoned || completed, role = Role.Checkbox, onValueChange = { onToggle() })
            .testTag("task-checkbox")
            .semantics { contentDescription = if (abandoned) "放弃标记" else "task-checkbox" },
        contentAlignment = Alignment.Center
    ) {
        when {
            abandoned -> Text("×", color = Danger, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold, lineHeight = if (compact) 14.sp else 16.sp)
            completed -> Text("✓", color = Color.White, fontSize = if (compact) 11.sp else 13.sp, lineHeight = if (compact) 11.sp else 13.sp)
        }
    }
}

internal fun dateChip(editor: EditorState): String {
    val start = editor.startDate ?: return "无日期"
    val end = editor.endDate
    val time = editor.time?.format(DateTimeFormatter.ofPattern("HH:mm"))
    if (editor.dateMode == DateMode.RANGE && end != null) {
        val range = if (start.year == end.year && start.month == end.month) "${start.monthValue}月${start.dayOfMonth}日到${end.dayOfMonth}日"
        else "${start.format(DateTimeFormatter.ofPattern("M月d日"))}到${end.format(DateTimeFormatter.ofPattern("M月d日"))}"
        return if (time == null) range else "$range $time"
    }
    val day = when (start) {
        LocalDate.now() -> "今天"
        LocalDate.now().plusDays(1) -> "明天"
        LocalDate.now().plusDays(2) -> "后天"
        else -> start.format(DateTimeFormatter.ofPattern("M月d日"))
    }
    return if (time == null) day else "$day $time"
}

private fun repeatLabel(editor: EditorState) = if (editor.repeatKind == RepeatKind.NONE) "无" else repeatSummary(editor)

private fun repeatSummary(editor: EditorState): String {
    val interval = editor.repeatInterval.coerceAtLeast(1)
    return when (editor.repeatUnit) {
        RepeatUnit.DAY -> if (interval == 1) "每天" else "每${interval}天"
        RepeatUnit.WEEK -> {
            val names = editor.weekdays.sorted().joinToString("、") { "周${"一二三四五六日"[it - 1]}" }
            val head = if (interval == 1) "每周" else "每${interval}周"
            if (names.isEmpty()) head else "${head}的$names"
        }
        RepeatUnit.MONTH -> {
            val day = editor.monthDay ?: editor.startDate?.dayOfMonth ?: 1
            if (interval == 1) "每月${day}日" else "每${interval}个月的${day}日"
        }
        RepeatUnit.YEAR -> {
            val date = editor.startDate ?: LocalDate.now()
            if (interval == 1) "每年${date.monthValue}月${date.dayOfMonth}日" else "每${interval}年"
        }
    }
}

package com.personalticktick.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.personalticktick.app.domain.RepeatKind
import com.personalticktick.app.domain.Task
import com.personalticktick.app.domain.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val Bg = Color(0xFF121315)
private val Card = Color(0xFF202226)
private val Elevated = Color(0xFF292C31)
private val Primary = Color(0xFF5B8DEF)
private val TextMain = Color(0xFFF0F1F3)
private val TextMuted = Color(0xFF92969E)
private val Danger = Color(0xFFE36B6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalTickTickApp(viewModel: TickTickViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = if (state.lastCompletedId != null) "撤销" else null)
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undoComplete()
        viewModel.consumeMessage()
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Primary, background = Bg, surface = Card)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { BottomNavigation(state.page, viewModel::navigate) },
            floatingActionButton = {
                if (state.page != AppPage.SEARCH) Surface(
                    modifier = Modifier.size(58.dp).clickable { viewModel.create(if (state.page == AppPage.CALENDAR) state.calendarAnchor else LocalDate.now()) }
                        .semantics { contentDescription = "创建任务" },
                    shape = CircleShape, color = Primary, shadowElevation = 8.dp
                ) { Box(contentAlignment = Alignment.Center) { Text("＋", fontSize = 28.sp, color = Color.White) } }
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
        if (state.repeatScopePrompt) ScopeDialog("修改重复任务", "这项修改要应用到哪里？", "仅本次", "本次及以后", { viewModel.confirmRepeatEdit() }, { viewModel.confirmRepeatEdit() }, viewModel::closeEditor)
        if (state.deleteScopePrompt) ScopeDialog("删除重复任务", "已经完成或跳过的历史记录会保留", "删除仅本次", "删除本次及以后", { viewModel.confirmDelete(false) }, { viewModel.confirmDelete(true) }, viewModel::closeEditor)
    }
}

@Composable
private fun BottomNavigation(page: AppPage, navigate: (AppPage) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card).navigationBarsPadding().height(62.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        NavItem("✓", "任务", page == AppPage.TODAY) { navigate(AppPage.TODAY) }
        NavItem("▦", "日历", page == AppPage.CALENDAR) { navigate(AppPage.CALENDAR) }
        NavItem("⌕", "搜索", page == AppPage.SEARCH) { navigate(AppPage.SEARCH) }
        NavItem("◇", "我的", false) { }
    }
}

@Composable private fun NavItem(symbol: String, label: String, selected: Boolean, click: () -> Unit) {
    Column(Modifier.width(64.dp).clickable(onClick = click).semantics { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, fontSize = 23.sp, color = if (selected) Primary else TextMuted)
        Text(label, fontSize = 11.sp, color = if (selected) Primary else TextMuted)
    }
}

@Composable
private fun TodayScreen(state: AppUiState, vm: TickTickViewModel) {
    val today = LocalDate.now()
    val overdue = state.tasks.filter { it.status == TaskStatus.ACTIVE && it.endDate?.isBefore(today) == true }
    val todayTasks = state.tasks.filter { task -> task.startDate?.let { !it.isAfter(today) } == true && task.endDate?.let { !it.isBefore(today) } == true }
        .sortedBy { it.status == TaskStatus.COMPLETED }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Header("今天")
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab("已过期${if (overdue.isNotEmpty()) " ${overdue.size}" else ""}", state.todayMode == TodayMode.OVERDUE) { vm.setTodayMode(TodayMode.OVERDUE) }
            Tab("今天", state.todayMode == TodayMode.TODAY) { vm.setTodayMode(TodayMode.TODAY) }
        }
        if (state.todayMode == TodayMode.OVERDUE) {
            LazyColumn(Modifier.fillMaxSize().semantics { contentDescription = "overdue-screen" }) {
                if (overdue.isEmpty()) item { EmptyState("没有已过期任务", "都处理好了") }
                overdue.groupBy { it.endDate }.toSortedMap(compareByDescending { it }).forEach { (date, group) ->
                    item { Text(date?.format(DateTimeFormatter.ofPattern("M月d日")) ?: "", color = TextMuted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }
                    items(group, key = { it.id }) { TaskCard(it, true, vm) }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            if (todayTasks.isEmpty()) item { EmptyState("今天没有任务", "点右下角创建一个任务") }
            items(todayTasks, key = { it.id }) { TaskCard(it, false, vm) }
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
private fun TaskCard(task: Task, overdue: Boolean, vm: TickTickViewModel, compact: Boolean = false) {
    val completed = task.status == TaskStatus.COMPLETED
    val dateText = task.time?.format(DateTimeFormatter.ofPattern("HH:mm"))
        ?: task.endDate?.format(DateTimeFormatter.ofPattern("M月d日")) ?: "无日期"
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (compact) 4.dp else 9.dp).clickable { vm.edit(task) },
        color = if (completed) Color(0xFF1A1B1E) else Card, shape = RoundedCornerShape(if (compact) 5.dp else 10.dp)
    ) {
        Row(Modifier.padding(if (compact) 7.dp else 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = completed, onCheckedChange = { vm.toggleComplete(task.id) }, modifier = Modifier.size(32.dp).semantics { contentDescription = "task-checkbox" })
            Text(task.title, Modifier.padding(start = 7.dp).weight(1f), color = if (completed) TextMuted else TextMain, fontSize = if (compact) 12.sp else 16.sp, textDecoration = if (completed) TextDecoration.LineThrough else null, maxLines = 2)
            if (task.repeatRule.kind != RepeatKind.NONE) Text("↻ ", color = TextMuted)
            if (task.reminderAt != null) Text("♧ ", color = TextMuted)
            Text(dateText, color = if (overdue && !completed) Danger else TextMuted, fontSize = if (compact) 10.sp else 13.sp)
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
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${state.calendarAnchor.monthValue}月 · ${if (state.calendarMode == CalendarMode.WEEK) "本周" else state.calendarAnchor.year}", Modifier.weight(1f), color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("‹", Modifier.clickable { vm.moveCalendar(if (state.calendarMode == CalendarMode.WEEK) -7 else -30) }.padding(12.dp), color = TextMain, fontSize = 25.sp)
            Text("›", Modifier.clickable { vm.moveCalendar(if (state.calendarMode == CalendarMode.WEEK) 7 else 30) }.padding(12.dp), color = TextMain, fontSize = 25.sp)
            Text("⋮", Modifier.clickable { vm.toggleShowCompleted() }.padding(start = 10.dp), color = TextMuted, fontSize = 22.sp)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).background(Elevated, RoundedCornerShape(8.dp))) {
            CalendarTab("周", state.calendarMode == CalendarMode.WEEK) { vm.setCalendarMode(CalendarMode.WEEK) }
            CalendarTab("月", state.calendarMode == CalendarMode.MONTH) { vm.setCalendarMode(CalendarMode.MONTH) }
        }
        if (state.calendarMode == CalendarMode.WEEK) WeekGrid(state, vm) else MonthGrid(state, vm)
    }
}

@Composable private fun CalendarTab(label: String, selected: Boolean, click: () -> Unit) {
    Box(Modifier.width(82.dp).height(36.dp).background(if (selected) Primary else Color.Transparent, RoundedCornerShape(7.dp)).clickable(onClick = click), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else TextMuted)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun WeekGrid(state: AppUiState, vm: TickTickViewModel) {
    val monday = state.calendarAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0L..6L).map(monday::plusDays)
    Column(Modifier.fillMaxSize().padding(10.dp).semantics { contentDescription = "week-screen" }) {
        repeat(3) { row ->
            Row(Modifier.weight(1f)) {
                repeat(2) { col ->
                    val day = days[row * 2 + col]
                    DayCell(day, state, vm, Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "week-day-cell" })
                }
            }
        }
        DayCell(days[6], state, vm, Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "week-day-cell week-sunday-cell" }, sunday = true)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun DayCell(day: LocalDate, state: AppUiState, vm: TickTickViewModel, modifier: Modifier, sunday: Boolean = false) {
    val tasks = state.tasks.filter { it.startDate?.let { s -> it.endDate?.let { e -> !day.isBefore(s) && !day.isAfter(e) } } == true && (state.showCompleted || it.status != TaskStatus.COMPLETED) }
    Column(modifier.padding(3.dp).border(1.dp, Color(0xFF34373D), RoundedCornerShape(7.dp)).combinedClickable(onClick = { }, onLongClick = { vm.create(day) }).padding(8.dp)) {
        Row { Text("周${"一二三四五六日"[day.dayOfWeek.value - 1]} ${day.dayOfMonth}", color = if (day == LocalDate.now()) Primary else TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp); if (sunday) Spacer(Modifier.weight(1f)) }
        val visible = if (sunday) tasks.take(4) else tasks.take(2)
        visible.forEachIndexed { index, task ->
            Text("${if (task.status == TaskStatus.COMPLETED) "☑" else "□"} ${task.title}${if (task.startDate != task.endDate) " ${java.time.temporal.ChronoUnit.DAYS.between(task.startDate, day) + 1}/${java.time.temporal.ChronoUnit.DAYS.between(task.startDate, task.endDate) + 1}" else ""}", color = if (task.status == TaskStatus.COMPLETED) TextMuted else TextMain, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 7.dp).clickable { vm.edit(task) })
        }
        if (tasks.size > visible.size) Text("还有 ${tasks.size - visible.size} 项", color = Primary, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable private fun MonthGrid(state: AppUiState, vm: TickTickViewModel) {
    val first = state.calendarAnchor.withDayOfMonth(1)
    val days = (0L..7L).map(first::plusDays)
    Column(Modifier.fillMaxSize().padding(10.dp).semantics { contentDescription = "month-screen" }) {
        CompactMonthCalendar(state.calendarAnchor, vm)
        Spacer(Modifier.height(8.dp))
        repeat(4) { row ->
            Row(Modifier.weight(1f)) {
                repeat(2) { col ->
                    val day = days[row * 2 + col]
                    MonthDayPanel(day, state, vm, Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "month-day-panel" })
                }
            }
        }
    }
}

@Composable private fun CompactMonthCalendar(anchor: LocalDate, vm: TickTickViewModel) {
    val first = anchor.withDayOfMonth(1)
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(8.dp)).padding(8.dp).semantics { contentDescription = "compact-month-calendar" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { "一二三四五六日".forEach { Text(it.toString(), color = TextMuted, fontSize = 11.sp) } }
        val offset = first.dayOfWeek.value - 1
        repeat(2) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                repeat(7) { col ->
                    val number = week * 7 + col - offset + 1
                    Text(if (number in 1..anchor.lengthOfMonth()) number.toString() else "", color = if (number == LocalDate.now().dayOfMonth && anchor.month == LocalDate.now().month) Primary else TextMain, fontSize = 12.sp, modifier = Modifier.width(22.dp).clickable(enabled = number > 0) { if (number in 1..anchor.lengthOfMonth()) vm.moveCalendar((number - anchor.dayOfMonth).toLong()) })
                }
            }
        }
    }
}

@Composable private fun MonthDayPanel(day: LocalDate, state: AppUiState, vm: TickTickViewModel, modifier: Modifier) {
    val tasks = state.tasks.filter { it.startDate?.let { s -> it.endDate?.let { e -> !day.isBefore(s) && !day.isAfter(e) } } == true && (state.showCompleted || it.status != TaskStatus.COMPLETED) }
    Column(modifier.padding(3.dp).background(Card, RoundedCornerShape(6.dp)).padding(7.dp)) {
        Text("${day.monthValue}月${day.dayOfMonth}日", color = if (day == LocalDate.now()) Primary else TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        tasks.take(2).forEach { TaskCard(it, it.status == TaskStatus.ACTIVE && it.endDate?.isBefore(LocalDate.now()) == true, vm, compact = true) }
        if (tasks.size > 2) Text("还有 ${tasks.size - 2} 项", color = Primary, fontSize = 10.sp)
    }
}

@Composable private fun SearchScreen(state: AppUiState, vm: TickTickViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        OutlinedTextField(value = state.searchQuery, onValueChange = vm::setSearch, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = "search-input" }, placeholder = { Text("搜索任务名称…") }, leadingIcon = { Text("⌕", color = TextMuted) }, trailingIcon = { if (state.searchQuery.isNotEmpty()) Text("×", Modifier.clickable { vm.setSearch("") }.padding(8.dp), color = TextMuted) }, singleLine = true)
        Text(if (state.searchQuery.isBlank()) "最近编辑" else "搜索结果", color = TextMuted, modifier = Modifier.padding(vertical = 10.dp))
        val results = vm.visibleSearchResults()
        if (results.isEmpty()) EmptyState("没有找到相关任务", "试试其他关键词") else LazyColumn { items(results, key = { it.id }) { TaskCard(it, it.status == TaskStatus.ACTIVE && it.endDate?.isBefore(LocalDate.now()) == true, vm) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskEditorSheet(editor: EditorState, vm: TickTickViewModel) {
    ModalBottomSheet(onDismissRequest = vm::closeEditor, containerColor = Elevated, modifier = Modifier.semantics { contentDescription = "task-editor-sheet" }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            OutlinedTextField(value = editor.title, onValueChange = { text -> vm.updateEditor { it.copy(title = text) } }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "task-title-input" }, placeholder = { Text("准备做什么？") }, textStyle = TextStyle(fontSize = 19.sp), minLines = 2)
            if (editor.startDate != null) Row(Modifier.padding(vertical = 9.dp)) {
                Chip(if (editor.startDate == LocalDate.now()) "今天" else editor.startDate.format(DateTimeFormatter.ofPattern("M月d日")))
                editor.time?.let { Chip(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                if (editor.reminderEnabled) Chip("准时提醒")
            }
            SettingRow("日期与时间", dateSummary(editor)) { vm.updateEditor { it.copy(panel = EditorPanel.DATE) } }
            SettingRow("提醒", editor.reminderLabel) { vm.updateEditor { it.copy(panel = EditorPanel.REMINDER) } }
            SettingRow("重复", repeatName(editor.repeatKind)) { vm.updateEditor { it.copy(panel = EditorPanel.REPEAT) } }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editor.editingId != null) TextButton(onClick = vm::requestDelete) { Text("删除", color = Danger) } else TextButton(onClick = vm::closeEditor) { Text("取消", color = TextMuted) }
                Spacer(Modifier.weight(1f))
                Button(onClick = vm::saveEditor, enabled = editor.title.isNotBlank() && !(editor.startDate != null && editor.endDate != null && editor.endDate < editor.startDate), colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text(if (editor.editingId == null) "保存" else "保存修改") }
            }
        }
        when (editor.panel) {
            EditorPanel.DATE -> DatePanel(editor, vm)
            EditorPanel.REMINDER -> ReminderPanel(editor, vm)
            EditorPanel.REPEAT -> RepeatPanel(editor, vm)
            EditorPanel.NONE -> Unit
        }
    }
}

@Composable private fun Chip(text: String) { Text(text, color = Primary, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp).background(Color(0xFF26364F), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 4.dp)) }
@Composable private fun SettingRow(label: String, value: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = TextMain); Text("$value  ›", color = TextMuted) }; HorizontalDivider(color = Color(0xFF393C42)) }

@Composable private fun DatePanel(editor: EditorState, vm: TickTickViewModel) {
    OverlayPanel("日期与时间", { vm.updateEditor { it.copy(panel = EditorPanel.NONE) } }) {
        Row { CalendarTab("单日", editor.dateMode == DateMode.SINGLE) { vm.updateEditor { it.copy(dateMode = DateMode.SINGLE, endDate = it.startDate) } }; CalendarTab("时间段", editor.dateMode == DateMode.RANGE) { vm.updateEditor { it.copy(dateMode = DateMode.RANGE, endDate = it.endDate ?: it.startDate?.plusDays(1)) } } }
        QuickRow("今天", LocalDate.now(), vm)
        QuickRow("明天", LocalDate.now().plusDays(1), vm)
        if (editor.dateMode == DateMode.RANGE) QuickRow("结束日期 +1天", (editor.startDate ?: LocalDate.now()).plusDays(1), vm, end = true)
        SettingRow("全天", if (editor.time == null) "开启" else "关闭") { vm.updateEditor { it.copy(time = if (it.time == null) java.time.LocalTime.of(9, 0) else null, reminderEnabled = false) } }
        if (editor.time != null) SettingRow("时间", editor.time.format(DateTimeFormatter.ofPattern("HH:mm"))) { vm.updateEditor { it.copy(time = it.time?.plusMinutes(30)) } }
        TextButton(onClick = { vm.updateEditor { it.copy(startDate = null, endDate = null, time = null, reminderEnabled = false, panel = EditorPanel.NONE) } }) { Text("无日期") }
    }
}

@Composable private fun QuickRow(label: String, date: LocalDate, vm: TickTickViewModel, end: Boolean = false) { SettingRow(label, date.format(DateTimeFormatter.ofPattern("M月d日"))) { vm.updateEditor { if (end) it.copy(endDate = date) else it.copy(startDate = date, endDate = if (it.dateMode == DateMode.SINGLE) date else it.endDate) } } }

@Composable private fun ReminderPanel(editor: EditorState, vm: TickTickViewModel) {
    OverlayPanel("提醒", { vm.updateEditor { it.copy(panel = EditorPanel.NONE) } }) {
        listOf("准时提醒", "提前 5 分钟", "提前 10 分钟", "提前 30 分钟", "关闭提醒").forEach { option ->
            Row(Modifier.fillMaxWidth().clickable { vm.updateEditor { it.copy(reminderEnabled = option != "关闭提醒", reminderLabel = if (option == "关闭提醒") "关闭" else option, panel = EditorPanel.NONE) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = editor.reminderLabel == option || option == "关闭提醒" && !editor.reminderEnabled, onClick = null); Text(option, color = TextMain) }
        }
        HorizontalDivider(color = Color(0xFF393C42))
        Text("通知权限　已允许", color = TextMuted, modifier = Modifier.padding(top = 14.dp)); Text("精确闹钟　需系统允许", color = TextMuted, modifier = Modifier.padding(top = 10.dp)); Text("后台运行　建议在 MagicOS 中允许", color = TextMuted, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable private fun RepeatPanel(editor: EditorState, vm: TickTickViewModel) {
    OverlayPanel("重复", { vm.updateEditor { it.copy(panel = EditorPanel.NONE) } }) {
        listOf(RepeatKind.NONE to "不重复", RepeatKind.DAILY to "每天", RepeatKind.WEEKDAYS to "工作日", RepeatKind.WEEKLY to "每周指定星期", RepeatKind.MONTHLY to "每月指定日期").forEach { (kind, name) ->
            Row(Modifier.fillMaxWidth().clickable { vm.updateEditor { it.copy(repeatKind = kind) } }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = editor.repeatKind == kind, onClick = null); Text(name, color = TextMain) }
        }
        if (editor.repeatKind != RepeatKind.NONE) OutlinedTextField(value = editor.repeatCount?.toString() ?: "", onValueChange = { value -> vm.updateEditor { it.copy(repeatCount = value.toIntOrNull()?.coerceAtLeast(1)) } }, label = { Text("重复次数（包含首次）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.updateEditor { it.copy(panel = EditorPanel.NONE) } }, modifier = Modifier.align(Alignment.End).padding(top = 14.dp)) { Text("完成") }
    }
}

@Composable private fun OverlayPanel(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = close) {
        Surface(shape = RoundedCornerShape(16.dp), color = Elevated, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("‹", Modifier.clickable(onClick = close).padding(8.dp), color = TextMain, fontSize = 24.sp); Text(title, Modifier.weight(1f), color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("完成", Modifier.clickable(onClick = close).padding(8.dp), color = Primary) }; content() }
        }
    }
}

@Composable private fun ScopeDialog(title: String, subtitle: String, first: String, second: String, onFirst: () -> Unit, onSecond: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column { Text(subtitle); Text(first, Modifier.fillMaxWidth().clickable(onClick = onFirst).padding(vertical = 17.dp), color = TextMain); Text(second, Modifier.fillMaxWidth().clickable(onClick = onSecond).padding(vertical = 17.dp), color = TextMain) } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

private fun dateSummary(editor: EditorState): String = when {
    editor.startDate == null -> "无日期"
    editor.dateMode == DateMode.RANGE -> "${editor.startDate.format(DateTimeFormatter.ofPattern("M月d日"))}—${editor.endDate?.format(DateTimeFormatter.ofPattern("M月d日"))}"
    editor.time == null -> editor.startDate.format(DateTimeFormatter.ofPattern("M月d日 全天"))
    else -> "${editor.startDate.format(DateTimeFormatter.ofPattern("M月d日"))} ${editor.time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}
private fun repeatName(kind: RepeatKind) = when (kind) { RepeatKind.NONE -> "不重复"; RepeatKind.DAILY -> "每天"; RepeatKind.WEEKDAYS -> "工作日"; RepeatKind.WEEKLY -> "每周指定星期"; RepeatKind.MONTHLY -> "每月指定日期" }

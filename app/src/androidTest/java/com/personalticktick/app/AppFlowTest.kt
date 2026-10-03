package com.personalticktick.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import com.personalticktick.app.data.TaskDatabase
import com.personalticktick.app.ui.dayHeading
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class ResetTaskDatabase : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            TaskDatabase.reset()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("tasks.db")
            base.evaluate()
        }
    }
}

class AppFlowTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ResetTaskDatabase()).around(composeRule)

    @Test
    fun todayOverdueCreateCompleteAndRestoreJourney() {
        composeRule.onAllNodesWithText("今天").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithTag("task-checkbox").onFirst().performClick()
        composeRule.onAllNodesWithTag("task-checkbox").onFirst().performClick()
        composeRule.onNodeWithText("已过期", substring = true).performClick()
        composeRule.onNodeWithTag("overdue-screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("创建任务").performClick()
        composeRule.onNodeWithTag("task-title-input").performTextInput("明天下午三点健身")
        composeRule.onNodeWithText("明天 15:00", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("保存").performClick()
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithTag("search-input").performTextInput("健身")
        composeRule.onNodeWithText("明天下午三点健身").assertIsDisplayed()
    }

    @Test
    fun abandonStrikesTheTaskAndKeepsTheCheck() {
        composeRule.onAllNodesWithTag("abandon-task").onFirst().performClick()
        composeRule.onNodeWithContentDescription("已放弃 阅读书籍", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("放弃标记").assertIsDisplayed()
        composeRule.onNodeWithText("阅读书籍").assertIsDisplayed()
        composeRule.onAllNodesWithTag("task-checkbox").onFirst().assertIsOn()
    }

    @Test
    fun monthSwipesBetweenWeeksAndMatchesTheSplitLayout() {
        composeRule.onNodeWithContentDescription("日历").performClick()
        composeRule.onNodeWithTag("month-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("compact-month-calendar").assertIsDisplayed()
        composeRule.onAllNodesWithTag("month-day-panel").assertCountEquals(7)
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        composeRule.onNodeWithText(dayHeading(monday)).assertIsDisplayed()
        composeRule.onNodeWithTag("month-screen").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(dayHeading(monday.plusWeeks(1))).assertIsDisplayed()
    }

    @Test
    fun datePickerAndWeekdayRepeatMatchTheReferenceSheets() {
        composeRule.onNodeWithContentDescription("创建任务").performClick()
        composeRule.onNodeWithTag("打开日期").performClick()
        composeRule.onNodeWithTag("date-picker").assertIsDisplayed()
        composeRule.onNodeWithText("清除").assertIsDisplayed()
        composeRule.onNodeWithText("时间").assertIsDisplayed()
        composeRule.onNodeWithText("重复").performClick()
        composeRule.onNodeWithText("自定义重复").assertIsDisplayed()
        val todayIso = LocalDate.now().dayOfWeek.value
        val extra = if (todayIso == 1) 6 else 1
        composeRule.onNodeWithTag("weekday-$extra").performClick()
        val names = listOf(todayIso, extra).sorted().joinToString("、") { "周${"一二三四五六日"[it - 1]}" }
        composeRule.onNodeWithText("每周的$names").assertIsDisplayed()
        composeRule.onNodeWithTag("完成重复").performClick()
        composeRule.onNodeWithText("每周的$names").assertIsDisplayed()
    }

    @Test
    fun complexTimeCronAndRangeChip() {
        composeRule.onNodeWithContentDescription("创建任务").performClick()
        composeRule.onNodeWithTag("task-title-input").performTextInput("后天中午12点开会")
        composeRule.onNodeWithText("后天 12:00", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("task-title-input").performTextReplacement("0 12 * * 0 周会")
        composeRule.onNodeWithText("12:00", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("打开日期").performClick()
        composeRule.onNodeWithText("时间段").performClick()
        composeRule.onNodeWithText("8").performClick()
        composeRule.onNodeWithTag("完成日期").performClick()
        composeRule.onNodeWithText("到", substring = true).assertIsDisplayed()
    }

    @Test
    fun deleteThisOccurrenceKeepsLaterRepeats() {
        composeRule.onNodeWithText("阅读书籍").performClick()
        composeRule.onNodeWithTag("删除").performClick()
        composeRule.onNodeWithText("删除仅本次").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("阅读书籍").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("日历").performClick()
        composeRule.onNodeWithText("阅读书籍").assertIsDisplayed()
    }

    @Test
    fun fuzzySearchOpensSharedEditor() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithTag("search-input").performTextInput("yd")
        composeRule.onNodeWithText("阅读书籍").performClick()
        composeRule.onNodeWithTag("task-editor-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("保存").assertIsDisplayed()
        composeRule.onNodeWithTag("删除").assertIsDisplayed()
    }
}

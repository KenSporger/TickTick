package com.personalticktick.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class AppFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todayOverdueCreateCompleteAndRestoreJourney() {
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("已过期").performClick()
        composeRule.onNodeWithTag("overdue-screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("创建任务").performClick()
        composeRule.onNodeWithTag("task-title-input").performTextInput("明天下午三点健身")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithText("明天下午三点健身").assertIsDisplayed()
        composeRule.onNodeWithTag("task-checkbox", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.onNodeWithText("明天下午三点健身").assertIsDisplayed()
    }

    @Test
    fun weekContainsSevenLargeDayCells() {
        composeRule.onNodeWithContentDescription("日历").performClick()
        composeRule.onNodeWithText("周").performClick()
        composeRule.onNodeWithTag("week-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("week-day-cell").assertCountEquals(7)
        composeRule.onNodeWithTag("week-sunday-cell").assertIsDisplayed()
    }

    @Test
    fun monthMatchesCompactCalendarAndDayPanels() {
        composeRule.onNodeWithContentDescription("日历").performClick()
        composeRule.onNodeWithText("月").performClick()
        composeRule.onNodeWithTag("month-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("compact-month-calendar").assertIsDisplayed()
        composeRule.onAllNodesWithTag("month-day-panel").assertCountEquals(8)
    }

    @Test
    fun fuzzySearchOpensSharedEditor() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithTag("search-input").performTextInput("yd")
        composeRule.onNodeWithText("阅读书籍").performClick()
        composeRule.onNodeWithTag("task-editor-sheet").assertIsDisplayed()
        composeRule.onNodeWithText("保存修改").assertIsDisplayed()
    }
}

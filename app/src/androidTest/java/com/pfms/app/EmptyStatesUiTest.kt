package com.pfms.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pfms.app.domain.model.dashboard.HistoryFilter
import com.pfms.app.domain.model.dashboard.TransactionTypeFilter
import com.pfms.app.ui.component.*
import com.pfms.app.ui.screen.DashboardSavingsGoalCard
import com.pfms.app.ui.screen.analytics.SpendingAnalyticsContent
import com.pfms.app.ui.screen.expense.ExpenseListScreen
import com.pfms.app.ui.screen.history.TransactionHistoryContent
import com.pfms.app.ui.screen.income.IncomeAnalyticsContent
import com.pfms.app.ui.screen.income.IncomeListScreen
import com.pfms.app.ui.screen.savings.SavingsGoalContent
import com.pfms.app.ui.screen.savings.SavingsGoalProgressRing
import com.pfms.app.ui.screen.savings.TransactionLedgerSection
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** FR-34: presentation and actions for a new user, using empty state snapshots and no backend writes. */
@RunWith(AndroidJUnit4::class)
class EmptyStatesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptySpendingAnalyticsShowsZeroChartAndWorkingAddAction() {
        var added = 0
        compose.setContent { PFMSTheme {
            SpendingAnalyticsContent(
                SpendingAnalyticsUiState(isLoading = false), {}, {}, {}, {}, {}, {}, {},
                onAddExpense = { added++ }
            )
        } }
        compose.onNodeWithText("Add Expense").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, added) }
        compose.onNodeWithText("LKR 0.00").assertExists()
        compose.onNodeWithContentDescription("No spending recorded for this month").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Committed vs Discretionary").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("0%", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("No trend yet").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyIncomeAnalyticsRetainsSummarySplitTrendAndAddIncome() {
        var added = 0
        compose.setContent { PFMSTheme {
            IncomeAnalyticsContent(
                IncomeAnalyticsUiState(isLoading = false), {}, {}, {}, {}, {}, {},
                onAddIncome = { added++ }
            )
        } }
        compose.onNodeWithText("No income this month").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add Income").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, added) }
        compose.onNodeWithText("Income Predictability Split").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("0.0% of total").assertCountEquals(2)
        compose.onNodeWithText("No income trend yet. LKR 0.00").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyIncomeListOffersAddIncome() {
        var added = 0
        compose.setContent { PFMSTheme {
            IncomeListScreen(emptyList(), { added++ }, {}, {}, {})
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No Income Records Yet"))
        compose.onNodeWithText("No Income Records Yet").assertIsDisplayed()
        compose.onAllNodesWithText("Add Income").onLast().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, added) }
    }

    @Test fun emptyExpenseMonthOffersAddExpenseAndZeroPercentages() {
        var added = 0
        compose.setContent { PFMSTheme {
            ExpenseListScreen(emptyList(), false, false, { added++ }, {}, {}, {}, {})
        } }
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("A fresh start for this month"))
        compose.onNodeWithText("A fresh start for this month").assertIsDisplayed()
        compose.onAllNodesWithText("Add Expense").onLast().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, added) }
        compose.onAllNodesWithText("0%").assertCountEquals(2)
    }

    @Test fun emptyHistoryOffersBothExistingAddDestinations() {
        var incomes = 0
        var expenses = 0
        compose.setContent { PFMSTheme {
            TransactionHistoryContent(
                TransactionHistoryUiState(isLoading = false), {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {},
                onAddIncome = { incomes++ }, onAddExpense = { expenses++ }
            )
        } }
        compose.onNodeWithText("No transactions yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add Income").performScrollTo().performClick()
        compose.onNodeWithText("Add Expense").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, incomes); assertEquals(1, expenses) }
        compose.onNodeWithText("Net LKR 0.00").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyHistoryFilterOffersClearInsteadOfPretendingThereAreNoRecords() {
        var cleared = 0
        compose.setContent { PFMSTheme {
            TransactionHistoryContent(
                TransactionHistoryUiState(isLoading = false, hasAnyTransactions = true,
                    filter = HistoryFilter(type = TransactionTypeFilter.INCOME)),
                {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, { cleared++ }
            )
        } }
        compose.onNodeWithText("No transactions match these filters").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cleared) }
    }

    @Test fun missingSavingsGoalShowsZeroProgressAndOpensExistingCreateDialog() {
        compose.setContent { PFMSTheme {
            SavingsGoalContent(
                SavingsGoalUiState(isLoading = false), {}, { _, _, _, _ -> }, {}, {}, {},
                { _, _, _ -> }, { _, _, _ -> }, {}, {}, {}
            )
        } }
        compose.onNodeWithText("No Active Savings Goal").assertExists()
        compose.onNodeWithText("Saved LKR 0.00 • Progress 0%").assertExists()
        compose.onNodeWithText("Create Savings Goal").performScrollTo().performClick()
        compose.onNodeWithText("Goal Name").assertIsDisplayed()
    }

    @Test fun emptyGoalLedgerOffersContributionAction() {
        var contributions = 0
        compose.setContent { PFMSTheme {
            TransactionLedgerSection(emptyList(), {}, {}, onAddContribution = { contributions++ })
        } }
        compose.onNodeWithText("No contributions yet").assertIsDisplayed()
        compose.onNodeWithText("Add Contribution").performClick()
        compose.runOnIdle { assertEquals(1, contributions) }
    }

    @Test fun emptyChartsAndNonFiniteProgressRemainRenderable() {
        compose.setContent { PFMSTheme { Column {
            CategoryDonutChart(emptyList())
            IncomeExpenseTrendChart(emptyList())
            SavingsGoalProgressRing(Double.NaN, false)
            SavingsGoalProgressRing(Double.POSITIVE_INFINITY, false)
        } } }
        compose.onNodeWithContentDescription("No spending recorded for this month").assertExists()
        compose.onAllNodesWithContentDescription("Progress: 0%").assertCountEquals(2)
        compose.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
    }

    @Test fun dashboardWithoutGoalHasAWorkingCreateGoalDestination() {
        var opened = 0
        compose.setContent { PFMSTheme {
            DashboardSavingsGoalCard(SavingsGoalUiState(isLoading = false), onClick = { opened++ })
        } }
        compose.onNodeWithText("No active savings goal").assertIsDisplayed()
        compose.onNodeWithText("Set goal", substring = true).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}

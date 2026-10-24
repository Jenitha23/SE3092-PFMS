package com.pfms.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.dashboard.DashboardOverviewCalculator
import com.pfms.app.domain.usecase.dashboard.TransactionHistoryCalculator
import com.pfms.app.ui.screen.DashboardActions
import com.pfms.app.ui.screen.DashboardScreenContent
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Isolated rendering and callbacks; no signed-in session or remote writes. */
class DashboardUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 21)
    private val incomes = (0L..4L).map { back ->
        Income(id = "income-$back", amountMinor = if (back == 0L) 26_546_743L else 30_000_000L,
            receivedDate = month.minusMonths(back).atDay(18).toString(),
            source = "Freelance", projectName = "Client retainer", paymentStatus = "Received")
    }
    private val expenses = listOf(
        Expense(id = "rent", amountMinor = 55_100_000L, date = "2026-10-15", merchant = "Monthly Rent",
            categoryName = "Housing", classification = ExpenseClassification.COMMITTED),
        Expense(id = "electricity", amountMinor = 350_000L, date = "2026-10-20", merchant = "CEB Electricity",
            categoryName = "Utilities", classification = ExpenseClassification.COMMITTED),
        Expense(id = "gym", amountMinor = 100_000L, date = "2026-10-21", merchant = "Gym & Lifestyle", categoryName = "Fitness"),
        Expense(id = "groceries", amountMinor = 1_525_000L, date = "2026-10-19", merchant = "Groceries", categoryName = "Food")
    ) + (1L..4L).map { Expense(id = "previous-$it", amountMinor = 27_000_000L,
        date = month.minusMonths(it).atDay(15).toString(), categoryName = "Housing") }

    private fun state(empty: Boolean = false): DashboardUiState {
        val incomeRows = if (empty) emptyList() else incomes
        val expenseRows = if (empty) emptyList() else expenses
        val overview = DashboardOverviewCalculator.calculate(incomeRows, expenseRows, month, today)
        val transactions = TransactionHistoryCalculator.merge(incomeRows.filter { it.receivedDate.startsWith("2026-10") },
            expenseRows.filter { it.date.startsWith("2026-10") })
        return DashboardUiState(isLoading = false, selectedMonth = month, summary = overview.summary,
            latestTransactions = transactions, content = DashboardPresentationMapper.content(overview, transactions))
    }

    private val chrome = DashboardChromeState(identity = DashboardIdentity("Alex Morgan", "AM", "Email verified"),
        goal = DashboardPresentationMapper.goal(SavingsGoalUiState(isLoading = false,
            activeGoal = SavingsGoal(name = "MacBook Pro M4 Hardware Fund", targetAmountMinor = 49_000_000L,
                targetDate = "2027-01-31"), currentSavedMinor = 1_120_000L,
            progressPercent = 2.285714, requiredMonthlySavingMinor = 15_960_000L)))

    @Test fun showsRecordedCashflowAndInteractiveLedger() {
        val state = state()
        var opened = ""
        compose.setContent { PFMSTheme(darkTheme = false) {
            DashboardScreenContent(state, chrome, actions = DashboardActions(onTransaction = { opened = it.id }))
        } }
        compose.onNodeWithText("−LKR 305,282.57").assertIsDisplayed()
        compose.onNodeWithText("Deficit · spending exceeds income", substring = true).assertExists()
        compose.onNodeWithText("Home").assertIsSelected()
        capture("dashboard-top.png")
        compose.onNodeWithTag("dashboard_sections").performScrollToNode(hasText("Interactive Cash Rhythm"))
        compose.onNodeWithText("Interactive Cash Rhythm").performScrollTo().assertIsDisplayed()
        capture("dashboard-rhythm.png")
        val entry = state.content!!.transactions.first()
        compose.onNodeWithTag("dashboard_sections").performScrollToNode(hasText(entry.title))
        compose.onNodeWithText(entry.title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(entry.transaction.id, opened) }
        capture("dashboard-ledger.png")
    }

    @Test fun quickActionsAndNavigationUseTheirCallbacks() {
        var income = 0; var expense = 0; var quick = 0; var goals = 0; var insights = 0
        var selected: YearMonth? = null
        val state = state()
        compose.setContent { PFMSTheme(darkTheme = false) {
            DashboardScreenContent(state, chrome, actions = DashboardActions(
                onAddIncome = { income++ }, onIncome = { income++ }, onAddExpense = { expense++ },
                onExpenses = { expense++ }, onQuickAdd = { quick++ }, onGoals = { goals++ },
                onInsights = { insights++ }, onSelectMonth = { selected = it }))
        } }
        compose.onNodeWithText("Quick Add").performScrollTo().performClick()
        compose.onNodeWithText("Inflow").performClick()
        compose.onNodeWithText("Expense").performClick()
        listOf("Income", "Expenses", "Goals", "Insights").forEach {
            compose.onNodeWithText(it).performClick()
        }
        val bar = state.content!!.bars.first()
        compose.onNodeWithContentDescription(bar.description).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, income); assertEquals(2, expense); assertEquals(1, quick)
            assertEquals(1, goals); assertEquals(1, insights); assertEquals(bar.month, selected)
        }
    }

    @Test fun emptyDashboardDoesNotInventMoneyOrRatios() {
        compose.setContent { PFMSTheme(darkTheme = false) {
            DashboardScreenContent(state(empty = true), DashboardChromeState())
        } }
        compose.onNodeWithText("No outflow").assertExists()
        compose.onNodeWithTag("dashboard_sections").performScrollToNode(hasText("Start your monthly overview"))
        compose.onNodeWithText("Start your monthly overview").assertIsDisplayed()
        compose.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
    }

    @Test fun smallScreenWithLargeTextKeepsLedgerAndDockAccessible() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.3f)) {
                PFMSTheme(darkTheme = false) {
                    Box(Modifier.requiredSize(320.dp, 640.dp)) { DashboardScreenContent(state(), chrome) }
                }
            }
        }
        compose.onNodeWithTag("dashboard_sections").performScrollToNode(hasText("Live Ledger Activity"))
        compose.onNodeWithText("Live Ledger Activity").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Goals").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

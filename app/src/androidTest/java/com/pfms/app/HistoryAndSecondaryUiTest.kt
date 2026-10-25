package com.pfms.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.domain.model.*
import com.pfms.app.domain.model.dashboard.*
import com.pfms.app.domain.usecase.dashboard.*
import com.pfms.app.ui.screen.*
import com.pfms.app.ui.screen.analytics.*
import com.pfms.app.ui.screen.history.TransactionHistoryContent
import com.pfms.app.ui.screen.savings.SavingsSimulatorContent
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class HistoryAndSecondaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val month = YearMonth.of(2026, 10)
    private val expenses = listOf(
        Expense(id = "coffee", categoryName = "Coffee & Dining Out", date = "2026-10-21", amountMinor = 390000),
        Expense(id = "gym", categoryName = "Gym", date = "2026-10-20", amountMinor = 100000),
        Expense(id = "pizza", categoryName = "Food Delivery", merchant = "Pizza", date = "2026-10-19", amountMinor = 350000),
        Expense(id = "rent", categoryName = "Rent", merchant = "Monthly rent", date = "2026-10-18", amountMinor = 55100000, classification = ExpenseClassification.COMMITTED),
        Expense(id = "wifi", categoryName = "Utilities", date = "2026-10-17", amountMinor = 535000),
        Expense(id = "netflix", categoryName = "Subscriptions", merchant = "Netflix", date = "2026-10-16", amountMinor = 600000))
    private val incomes = listOf(Income(id = "salary", source = "Salary", amountMinor = 26546743, receivedDate = "2026-10-15"))
    private val rows = TransactionHistoryCalculator.merge(incomes, expenses)
    private var source by mutableStateOf(history())
    private var clicked: String? = null
    private var filterSource: String? = null
    private var showHistory = 0
    private var hideKeyboard: () -> Unit = {}
    @Before fun edge() { compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() } }
    private fun history(filter: HistoryFilter = HistoryFilter()): TransactionHistoryUiState {
        val filtered = TransactionHistoryCalculator.filter(rows, filter)
        return TransactionHistoryUiState(isLoading = false, filter = filter, visible = filtered, summary = TransactionHistoryCalculator.summarize(filtered), hasAnyTransactions = true,
            expenseCategories = expenses.map { it.categoryName })
    }
    private fun content(small: Boolean = false, body: @Composable () -> Unit) = compose.setContent {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        SideEffect { hideKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, if (small) 1.3f else 1f)) {
            PFMSTheme(darkTheme = small) { if (small) Box(Modifier.requiredSize(320.dp, 640.dp)) { body() } else body() }
        }
    }
    @Composable private fun historyContent() = TransactionHistoryContent(source, {}, { clicked = it.id }, {}, {},
        { source = history(HistoryFilter(type = it)) }, { start, end -> source = history(source.filter.copy(startDate = start, endDate = end)) },
        { filterSource = it }, {}, {}, {}, { source = history() })

    @Test fun redesignedHistoryShowsFullTotalsFiltersAndEditableTransactions() {
        content { historyContent() }
        compose.onNodeWithTag("history_net").assertTextEquals("Net −LKR 305,282.57")
        fits(compose.onNodeWithTag("history_net"))
        compose.onNodeWithTag("transaction_EXPENSE_coffee").performClick()
        compose.runOnIdle { assertEquals("coffee", clicked) }
        capture("history-redesign.png")
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Salary").performClick()
        compose.runOnIdle { assertEquals("Salary", filterSource) }
        compose.onNodeWithText("From: Any").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNode(hasText("Income") and hasClickAction()).performClick()
        compose.onNodeWithText("1 transaction match").assertExists()
        compose.onNodeWithTag("transaction_INCOME_salary").assertExists()
        compose.onNodeWithTag("transaction_EXPENSE_coffee").assertDoesNotExist()
    }
    @Test fun narrowHistoryKeepsLargeAmountsAndFilterControlsInsideTheWindow() {
        val loss = Income(id = "huge", source = "Crypto", cryptoType = "Loss", amountMinor = 99999999999, receivedDate = "2026-10-25").toTransaction()
        source = TransactionHistoryUiState(isLoading = false, visible = listOf(loss), summary = TransactionHistoryCalculator.summarize(listOf(loss)), hasAnyTransactions = true)
        content(small = true) { historyContent() }
        fits(compose.onNodeWithTag("history_net"))
        compose.onNodeWithTag("history_sections").performScrollToNode(hasTestTag("transaction_INCOME_huge"))
        fits(compose.onNodeWithTag("transaction_amount_huge", useUnmergedTree = true))
        capture("history-small-dark.png")
        compose.onNodeWithTag("history_sections").performScrollToNode(hasText("Filters"))
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("From: Any").assertIsDisplayed()
        compose.onNodeWithText("To: Any").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
    }
    @Test fun dashboardShowsFourEntriesAndSeeAllInvokesHistory() {
        val overview = DashboardOverviewCalculator.calculate(incomes, expenses, month, month.atDay(25))
        val dashboard = DashboardUiState(isLoading = false, selectedMonth = month, summary = overview.summary, latestTransactions = rows,
            content = DashboardPresentationMapper.content(overview, rows))
        content { DashboardScreenContent(dashboard, DashboardChromeState(), actions = DashboardActions(onHistory = { showHistory++ })) }
        compose.onNodeWithTag("dashboard_sections").performScrollToNode(hasText("Live Ledger Activity"))
        compose.onNodeWithText("See all  ›").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, showHistory) }
        assertEquals(4, dashboard.content!!.transactions.size)
        assertEquals("7", dashboard.content!!.ledgerCount)
        capture("dashboard-ledger-preview.png")
    }
    private fun spending() = SpendingAnalyticsUiState(isLoading = false, selectedMonth = month,
        summary = MonthlyFinanceCalculator.summarize(incomes, expenses, month), insights = SpendingInsightsCalculator.build(expenses),
        trend = MonthlyFinanceCalculator.trend(incomes, expenses, month, 6))
    @Test fun spendingAnalyticsLinksToPatternsHistoryIncomeAndTransactionEditor() {
        var patterns = 0; var history = 0; var income = 0
        content { SpendingAnalyticsContent(spending(), {}, {}, {}, {}, { clicked = it.id }, { history++ }, { income++ }, onOpenSpendingPatterns = { patterns++ }) }
        compose.onNodeWithTag("spending_total").assertTextEquals("LKR 570,750.00")
        fits(compose.onNodeWithTag("spending_total"))
        capture("spending-redesign.png")
        compose.onNodeWithText("Smart spending patterns").performScrollTo().performClick()
        compose.onNodeWithTag("transaction_EXPENSE_rent").performScrollTo().performClick()
        compose.onNodeWithText("See full history →").performScrollTo().performClick()
        compose.onNodeWithText("Income Analytics").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, patterns); assertEquals(1, history); assertEquals(1, income); assertEquals("rent", clicked) }
    }
    @Test fun narrowSpendingBreakdownFitsCategoryTotalsAndMonthControls() {
        content(small = true) { SpendingAnalyticsContent(spending(), {}, {}, {}, {}, {}, {}, {}) }
        fits(compose.onNodeWithTag("spending_total"))
        compose.onNodeWithTag("spending_category_Rent").performScrollTo()
        fits(compose.onNodeWithTag("spending_category_Rent"))
        capture("spending-small-dark.png")
        compose.onNodeWithText("Income vs expenses").performScrollTo().assertIsDisplayed()
    }
    @Test fun patternMonthSelectionAndSavingsLinkRemainConnected() {
        var selected: YearMonth? = null; var opened = 0
        content(small = true) { SpendingPatternsContent(SpendingPatternsUiState(date = LocalDate.of(2026, 10, 25), isLoading = false,
            result = SpendingPatternResult(SpendingPatternStatus.INSUFFICIENT_HISTORY, emptyList(), 0)), emptySet(),
            { month, _ -> selected = month }, {}, { opened++ }) }
        compose.onNodeWithText("September 2026").performScrollTo().performClick()
        compose.onNodeWithText("Explore savings impact").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 9), selected); assertEquals(1, opened) }
        capture("patterns-small-dark.png")
    }
    @Test fun savingsSimulatorHandlesEmptyAmountWithoutKeepingAnOldForecast() {
        var reduction = -1L
        val goal = SavingsGoal(id = "goal", name = "Laptop", targetAmountMinor = 49000000, targetDate = "2027-10-25")
        val result = SavingsSimulation(LocalDate.of(2026, 10, 25), 12, 1120000, 49000000, 100000, 0, 1200000, 2320000, 4, 46680000, null, false, false)
        content(small = true) { SavingsSimulatorContent(SavingsSimulatorUiState(isLoading = false, goal = goal, result = result), {}, { reduction = it }, {}) }
        compose.onNodeWithText("Monthly spending reduction (LKR)").performScrollTo().performTextReplacement("1200.50")
        compose.runOnIdle { assertEquals(120050L, reduction) }
        compose.onNodeWithText("Monthly spending reduction (LKR)").performTextReplacement("")
        compose.runOnIdle { assertEquals(0L, reduction) }
        compose.onNodeWithText("Monthly spending reduction (LKR)").performTextReplacement("12.001")
        compose.runOnIdle { assertEquals(0L, reduction) }
        compose.runOnIdle { hideKeyboard() }; androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Projected goal progress").assertDoesNotExist()
        compose.onNodeWithText("Enter zero or a positive amount with up to 2 decimal places.").assertExists()
        capture("simulator-small-dark.png")
    }
    @Test fun simulatorWithoutGoalProvidesAnExitToSavingsGoals() {
        var opened = 0
        content { SavingsSimulatorContent(SavingsSimulatorUiState(isLoading = false), {}, {}, {}, { opened++ }) }
        compose.onNodeWithText("Open Savings Goals").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
    private fun fits(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty()); assertTrue("Complete amount fits", layouts.none { it.hasVisualOverflow })
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}

package com.pfms.app.ui.screen.income

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.data.export.*
import com.pfms.app.domain.usecase.analytics.IncomeInsightRange
import com.pfms.app.domain.usecase.savings.SavingsGoalEntryRules
import com.pfms.app.ui.screen.savings.SavingsGoalUiFormatter
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun IncomeAnalyticsScreen(onBack: () -> Unit, modifier: Modifier = Modifier,
    viewModel: IncomeAnalyticsViewModel = hiltViewModel(), onHome: (() -> Unit)? = null, onIncome: (() -> Unit)? = null,
    onExpenses: (() -> Unit)? = null, onGoals: (() -> Unit)? = null, onAddIncome: () -> Unit = {}, onOpenSpendingAnalytics: (() -> Unit)? = null) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    IncomeAnalyticsContent(source, viewModel::previousMonth, viewModel::nextMonth, viewModel::setEstimatedMonthlyIncome,
        viewModel::clearEstimatedMonthlyIncome, viewModel::clearError, onBack, onHome, onIncome, onExpenses, onGoals,
        modifier, onAddIncome, viewModel::retry, monthlyData = { com.pfms.app.ui.component.MonthlyDataIndicator(it) },
        onSelectMonth = viewModel::selectMonth, onSelectRange = viewModel::selectRange, onOpenSpendingAnalytics = onOpenSpendingAnalytics)
}

@Composable
fun IncomeAnalyticsContent(uiState: IncomeAnalyticsUiState, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit,
    onSetEstimatedMonthlyIncome: (Long) -> Unit, onClearEstimatedMonthlyIncome: () -> Unit, onClearError: () -> Unit,
    onBack: () -> Unit, onHome: (() -> Unit)? = null, onIncome: (() -> Unit)? = null, onExpenses: (() -> Unit)? = null,
    onGoals: (() -> Unit)? = null, modifier: Modifier = Modifier, onAddIncome: () -> Unit = {}, onRetry: () -> Unit = {},
    monthlyData: @Composable (YearMonth) -> Unit = {}, onSelectMonth: ((YearMonth) -> Unit)? = null,
    onSelectRange: ((IncomeInsightRange) -> Unit)? = null, onOpenSpendingAnalytics: (() -> Unit)? = null
) = FinanceTheme {
    val controller: IncomeInsightsScreenViewModel = viewModel()
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(uiState) { controller.sync(uiState) }
    val resolver = LocalContext.current.contentResolver
    val writer = remember(resolver) { IncomeInsightsReportWriter(resolver) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { controller.export(it, writer) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { controller.export(it, writer) }
    val actions = IncomeInsightsActions(onBack, onPreviousMonth, onNextMonth, onAddIncome, onRetry, onClearError,
        { controller.estimate(it) }, { controller.applyEstimate(onSetEstimatedMonthlyIncome) }, { controller.clearEstimate(onClearEstimatedMonthlyIncome) },
        { controller.calendar(true) }, { controller.calendar(false) }, { millis -> onSelectMonth?.let { controller.selectDate(millis, it) } },
        onSelectRange, controller::trend, { controller.exportMenu(true) }, { controller.exportMenu(false) },
        { csv.launch(controller.prepareExport(IncomeReportFormat.CSV)) }, { pdf.launch(controller.prepareExport(IncomeReportFormat.PDF)) }, controller::dismissExportMessage)
    IncomeInsightsDashboard(state, actions, modifier, onHome, onIncome, onExpenses, onGoals, onSelectMonth != null, monthlyData, onOpenSpendingAnalytics)
}

data class IncomeInsightsActions(val back: () -> Unit, val previous: () -> Unit, val next: () -> Unit, val addIncome: () -> Unit,
    val retry: () -> Unit, val clearError: () -> Unit, val estimate: (String) -> Unit, val compare: () -> Unit, val clearEstimate: () -> Unit,
    val openCalendar: () -> Unit, val closeCalendar: () -> Unit, val calendarDate: (Long?) -> Unit,
    val range: ((IncomeInsightRange) -> Unit)?, val trend: (String) -> Unit, val openExport: () -> Unit, val closeExport: () -> Unit,
    val csv: () -> Unit, val pdf: () -> Unit, val dismissExportMessage: () -> Unit)

fun formatAnalyticsLkrAmount(amountMinor: Long): String = SavingsGoalUiFormatter.formatLkr(amountMinor)
fun formatMonthLong(yearMonth: YearMonth): String = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
fun formatMonthShort(yearMonthString: String): String = runCatching { YearMonth.parse(yearMonthString.trim()).format(DateTimeFormatter.ofPattern("MMM", Locale.US)) }.getOrDefault(yearMonthString)
fun parseInputToMinor(input: String): Long? = SavingsGoalEntryRules.parseAmount(input)

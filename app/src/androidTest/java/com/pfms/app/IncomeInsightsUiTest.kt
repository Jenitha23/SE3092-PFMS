package com.pfms.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import com.pfms.app.data.export.*
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.*
import com.pfms.app.ui.screen.income.IncomeAnalyticsContent
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.IncomeAnalyticsUiState
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.YearMonth

/** Isolated records and callback fixtures; signed-in account data is never modified. */
class IncomeInsightsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val month = YearMonth.of(2026, 10)
    private val records = listOf(
        Income("may", source = "Salary", amountMinor = 21000000, receivedDate = "2026-05-10"),
        Income("jun", source = "Salary", amountMinor = 23500000, receivedDate = "2026-06-10"),
        Income("jul", source = "Salary", amountMinor = 22000000, receivedDate = "2026-07-10"),
        Income("aug", source = "Salary", amountMinor = 26000000, receivedDate = "2026-08-10"),
        Income("sep", source = "Salary", amountMinor = 24800000, receivedDate = "2026-09-10"),
        Income("oct", source = "Salary", currency = "USD", amountMinor = 1000000, amountLkrMinor = 357396932, receivedDate = "2026-10-10"),
        Income("loss", source = "Crypto", cryptoType = "Loss", amountMinor = 330850189, receivedDate = "2026-10-20"),
        Income("pending", source = "Freelance", paymentStatus = "Expected", amountMinor = 100000, receivedDate = "2026-10-21"))
    private var source by mutableStateOf(state())
    private var estimate: Long? = null
    private var resets = 0
    private var goals = 0
    private var dismissKeyboard: () -> Unit = {}
    private fun state(selected: YearMonth = month, range: IncomeInsightRange = IncomeInsightRange.MONTH, data: List<Income> = records): IncomeAnalyticsUiState {
        val summary = GetMonthlyIncomeSummaryUseCase()(data, selected)
        return IncomeAnalyticsUiState(isLoading = false, selectedMonth = selected, monthlySummary = summary,
            hasMonthlyIncome = data.any { IncomeRealizedCalculator.matchesMonth(it, selected) },
            insights = IncomeInsightsCalculator.calculate(data, selected, range), range = range)
    }
    @Before fun window() { compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() } }
    private fun content(small: Boolean = false, dark: Boolean = false) = compose.setContent {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        SideEffect { dismissKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, if (small) 1.3f else 1f)) { PFMSTheme(darkTheme = dark) {
            val screen: @Composable () -> Unit = { IncomeAnalyticsContent(source,
                { source = state(source.selectedMonth.minusMonths(1), source.range) }, { source = state(source.selectedMonth.plusMonths(1), source.range) },
                { amount -> estimate = amount; source = source.copy(estimatedMonthlyIncomeMinor = amount) },
                { resets++; source = source.copy(estimatedMonthlyIncomeMinor = null) }, {}, {}, onHome = {}, onIncome = {}, onExpenses = {}, onGoals = { goals++ },
                onSelectMonth = { source = state(it, source.range) }, onSelectRange = { source = state(source.selectedMonth, it) }) }
            if (small) Box(Modifier.requiredSize(320.dp, 640.dp)) { screen() } else screen()
        } }
    }
    private fun hideKeyboard() { compose.runOnIdle { dismissKeyboard() }; androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.waitForIdle() }

    @Test fun actualWaterfallAndTrendSelectionKeepFullValuesVisible() {
        content()
        compose.onNodeWithTag("insights_net_total").assertTextEquals("LKR 265,467.43")
        fits(compose.onNodeWithTag("insights_net_total"))
        fits(compose.onNodeWithTag("insights_hero_gross"))
        fits(compose.onNodeWithTag("insights_hero_losses"))
        compose.onNode(hasText("Insights") and isSelected()).assertIsDisplayed()
        capture("insights-redesign.png")
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insight_flow_gross"))
        compose.onNodeWithTag("insight_flow_gross").assertTextEquals("LKR 3,573,969.32")
        compose.onNodeWithTag("insight_flow_losses").assertTextEquals("− LKR 3,308,501.89")
        fits(compose.onNodeWithTag("insight_flow_losses"))
        capture("insights-waterfall.png")
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insight_trend_2026-09"))
        compose.onNodeWithTag("insight_trend_2026-09").performClick()
        compose.onNodeWithTag("insight_selected_trend_amount").assertTextEquals("LKR 248,000.00")
        fits(compose.onNodeWithTag("insight_selected_trend_amount"))
        capture("insights-trend.png")
        compose.onNodeWithText("Goals").performClick()
        compose.runOnIdle { assertEquals(1, goals) }
    }

    @Test fun rangeMonthAndCalendarControlsAreConnected() {
        content()
        compose.onNodeWithTag("insight_range_3M").performClick()
        compose.onNodeWithTag("insights_net_total").assertTextEquals("LKR 773,467.43")
        compose.onNodeWithContentDescription("Next analytics month").performClick()
        compose.onNodeWithTag("insights_net_total").assertTextEquals("LKR 513,467.43")
        compose.onNodeWithText("No income this month").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous analytics month").performClick()
        compose.onNodeWithContentDescription("Choose analytics month").performClick()
        compose.onNodeWithText("Select Month").performClick()
        compose.runOnIdle { assertEquals(month, source.selectedMonth); assertEquals(IncomeInsightRange.QUARTER, source.range) }
        compose.onNodeWithContentDescription("Export income report").performClick()
        compose.onNodeWithText("Save CSV").assertIsDisplayed()
        compose.onNodeWithText("Save PDF").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test fun monthsWithoutReceivedIncomeUseUnfilledPlaceholdersAndRemainSelectable() {
        source = state(data = records.filter { it.receivedDate.startsWith("2026-10") })
        content()
        capture("insights-hero-refined.png")
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insight_trend_2026-05"))
        compose.onNodeWithTag("insight_empty_bar_2026-05", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("insight_recorded_bar_2026-10", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("insight_trend_2026-05").assertContentDescriptionEquals("May 2026: No received income").performClick()
        compose.onNodeWithTag("insight_selected_trend_amount").assertTextEquals("LKR 0.00")
        compose.onNodeWithText("No received income recorded").assertExists()
        capture("insights-empty-month-outlines.png")
        compose.onNodeWithTag("insight_trend_2026-10").performClick()
        compose.onNodeWithTag("insight_selected_trend_amount").assertTextEquals("LKR 265,467.43")
    }

    @Test fun estimatesValidateExactMinorUnitsAndResetTheSavedValue() {
        content()
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasText("Estimated Income (LKR)"))
        compose.onNodeWithText("Estimated Income (LKR)").performTextInput("150000.001")
        hideKeyboard()
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insights_estimate_compare"))
        compose.onNodeWithTag("insights_estimate_compare").performClick()
        compose.runOnIdle { assertNull(estimate) }
        compose.onNodeWithText("Estimated Income (LKR)").performTextReplacement("150000.50")
        hideKeyboard()
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insights_estimate_compare"))
        compose.onNodeWithTag("insights_estimate_compare").performClick()
        compose.runOnIdle { assertEquals(15000050L, estimate) }
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insights_estimate_reset"))
        compose.onNodeWithTag("insights_estimate_reset").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
        assertEquals("", compose.onNodeWithText("Estimated Income (LKR)").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
    }

    @Test fun narrowDarkInsightsPreserveLargeLossesAndReadableLabels() {
        source = state(data = listOf(Income(source = "Crypto", cryptoType = "Loss", amountMinor = 99999999999, receivedDate = "2026-10-10")))
        content(small = true, dark = true)
        compose.onNodeWithTag("insights_net_total").assertTextEquals("- LKR 999,999,999.99")
        fits(compose.onNodeWithTag("insights_net_total"))
        capture("insights-small-dark.png")
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insight_flow_losses"))
        compose.onNodeWithTag("insight_flow_losses").assertTextEquals("− LKR 999,999,999.99")
        fits(compose.onNodeWithTag("insight_flow_losses"))
    }

    private fun comparisonState(amount: Long = 25000000L) = state().copy(estimatedMonthlyIncomeMinor = amount,
        estimationComparison = com.pfms.app.domain.model.analytics.IncomeEstimationComparison(amount, 0, -amount, -100.0,
            com.pfms.app.domain.model.analytics.EstimationStatus.BELOW_ESTIMATE, listOf("2026-07", "2026-08", "2026-09")))

    @Test fun comparisonCardsAlignCountsButtonsAndSeparateFullAmountsFromMonthChips() {
        source = comparisonState()
        content()
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insights_received_count"))
        fits(compose.onNodeWithTag("insights_daily_average"))
        val received = compose.onNodeWithTag("insights_received_count").fetchSemanticsNode().boundsInRoot
        val pending = compose.onNodeWithTag("insights_pending_count").fetchSemanticsNode().boundsInRoot
        assertEquals(received.top, pending.top, 1f)
        capture("insights-metrics-aligned.png")
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insights_estimate_card"))
        val reset = compose.onNodeWithTag("insights_estimate_reset").fetchSemanticsNode().boundsInRoot
        val compare = compose.onNodeWithTag("insights_estimate_compare").fetchSemanticsNode().boundsInRoot
        assertEquals(reset.width, compare.width, 1f)
        assertEquals(reset.top, compare.top, 1f)
        assertEquals(reset.height, compare.height, 1f)
        fits(compose.onNodeWithTag("insights_comparison_estimate"))
        fits(compose.onNodeWithTag("insights_comparison_actual"))
        compose.onNodeWithTag("insights_comparison_difference").assertTextEquals("- LKR 250,000.00")
        fits(compose.onNodeWithTag("insights_comparison_difference"))
        compose.onNodeWithText("-100.0% vs estimate").assertExists()
        listOf("Jul 2026", "Aug 2026", "Sep 2026").forEach { compose.onNodeWithText(it).assertExists() }
        capture("insights-comparison-aligned.png")
    }

    @Test fun narrowComparisonStacksLongValuesAndWrapsEvaluatedMonths() {
        source = comparisonState(99999999999L)
        content(small = true, dark = true)
        compose.onNodeWithTag("insights_sections").performScrollToNode(hasTestTag("insights_estimate_card"))
        val buttonLabels = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Compare", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(buttonLabels) }
        assertTrue(buttonLabels.isNotEmpty())
        assertTrue("Compare stays on one line without clipping", buttonLabels.all { it.lineCount == 1 && !it.hasVisualOverflow })
        fits(compose.onNodeWithTag("insights_comparison_estimate"))
        fits(compose.onNodeWithTag("insights_comparison_actual"))
        fits(compose.onNodeWithTag("insights_comparison_difference"))
        compose.onNodeWithText("Sep 2026").performScrollTo().assertIsDisplayed()
        capture("insights-comparison-small-dark.png")
    }

    @Test fun csvAndPdfReportsWriteCompleteAmountsToLocalDocuments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writer = IncomeInsightsReportWriter(context.contentResolver)
        val report = IncomeInsightsReport("October 2026", listOf(listOf("Metric", "Amount"), listOf("Net income", "LKR 265,467.43"),
            listOf("Realized losses", "LKR 3,308,501.89"), listOf("Notes", "Recorded historical values only. ".repeat(25))))
        val csv = File(context.cacheDir, "insights-test.csv")
        val pdf = File(context.cacheDir, "insights-test.pdf")
        try {
            assertTrue(writer.write(Uri.fromFile(csv), report, IncomeReportFormat.CSV).isSuccess)
            assertTrue(csv.readText().contains("\"Net income\",\"LKR 265,467.43\""))
            assertTrue(writer.write(Uri.fromFile(pdf), report, IncomeReportFormat.PDF).isSuccess)
            PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { assertTrue(it.pageCount > 0) }
        } finally { csv.delete(); pdf.delete() }
    }

    private fun fits(node: SemanticsNodeInteraction) {
        val result = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        assertTrue(result.isNotEmpty()); assertTrue("The complete amount fits", result.none { it.hasVisualOverflow })
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}

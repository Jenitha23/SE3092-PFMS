package com.pfms.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.domain.model.Income
import com.pfms.app.ui.screen.income.IncomeListScreen
import com.pfms.app.ui.screen.income.IncomeStreamActions
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** Tests prepared snapshots and callbacks; never writes to a signed-in account. */
class IncomeStreamUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 10)
    private val controls = IncomeStreamControls(month)
    private val incomes = listOf(
        Income(id = "salary-usd", amountMinor = 100_00, currency = "USD", amountLkrMinor = 33_107_46, receivedDate = "2026-10-18", note = "Monthly payroll"),
        Income(id = "crypto-loss", source = "Crypto", amountMinor = 10_000_00, currency = "USD", amountLkrMinor = 3_309_801_89,
            cryptoType = "Loss", cryptoAsset = "BTC", receivedDate = "2026-10-16", note = "BTC"),
        Income(id = "salary-wire", amountMinor = 10_000_00, currency = "USD", amountLkrMinor = 3_307_774_02, receivedDate = "2026-10-13"),
        Income(id = "freelance", source = "Freelance", projectName = "UX research and mobile app redesign", amountMinor = 450_00,
            currency = "USD", amountLkrMinor = 148_850_26, receivedDate = "2026-10-08", note = "Invoice #UP-89021"),
        Income(id = "local", amountMinor = 85_537_58, receivedDate = "2026-10-05"),
        Income(id = "pending", source = "Freelance", projectName = "Brand project", amountMinor = 1_000_00, paymentStatus = "Expected", receivedDate = "2026-10-04"),
        Income(id = "previous", amountMinor = 200_000_00, receivedDate = "2026-09-18"))

    private fun state(rows: List<Income> = incomes, controls: IncomeStreamControls = this.controls) =
        IncomeStreamPresentation.create(IncomeUiState(incomes = rows, isLoading = false), controls)

    @Test fun monthlyValuesAndLossCardsShowFullAccurateAmounts() {
        compose.setContent { PFMSTheme(darkTheme = false) { IncomeListScreen(state()) } }
        compose.onNodeWithTag("income_total").assertTextEquals("LKR 265,467.43")
        assertAmountFits(compose.onNodeWithTag("income_total"))
        compose.onNode(hasText("Income") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertIsSelected()
        assertTrue("The monthly summary should leave room for the income list",
            compose.onNodeWithTag("income_summary").getUnclippedBoundsInRoot().let { it.bottom - it.top } <= 300.dp)
        compose.onNodeWithText("Records").assertIsDisplayed()
        capture("income-summary.png")
        scrollTo("Income streams", ignoreCase = true)
        scrollTo("−USD 10,000.00").assertIsDisplayed()
        compose.onNode(hasText("−LKR 3,309,801.89") and hasAnyAncestor(hasTestTag("income_record_crypto-loss")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("income_asset_crypto-loss", useUnmergedTree = true).assertTextEquals("BTC").assertIsDisplayed()
        assertTrue("Normal-sized income records should stay compact",
            compose.onNodeWithTag("income_record_crypto-loss").getUnclippedBoundsInRoot().let { it.bottom - it.top } <= 140.dp)
        capture("income-records.png")
        scrollTo("1 invoice awaiting payment", substring = true).assertIsDisplayed()
    }

    @Test fun filteringReceivingAndDeleteConfirmationForwardActions() {
        var records by mutableStateOf(incomes)
        var selection by mutableStateOf(controls)
        var deleted = 0; var received = 0
        compose.setContent { PFMSTheme(darkTheme = false) {
            IncomeListScreen(state(records, selection), IncomeStreamActions(
                onFilter = { selection = selection.copy(filter = it) },
                onSort = { selection = selection.copy(oldestFirst = !selection.oldestFirst) },
                onMenu = { selection = selection.copy(recordMenuId = it) },
                onDelete = { selection = selection.copy(deleteId = it, recordMenuId = null) },
                onConfirmDelete = { deleted++; records = records.filterNot { it.id == selection.deleteId }; selection = selection.copy(deleteId = null) },
                onMarkReceived = { id -> received++; records = records.map { if (it.id == id) it.copy(paymentStatus = "Received") else it } }))
        } }
        compose.onNodeWithTag("income_sections").performScrollToNode(hasText("Freelance"))
        compose.onNodeWithText("Freelance", useUnmergedTree = true).performClick()
        scrollTo("2 records").assertIsDisplayed()
        scrollTo("Brand project")
        compose.onNodeWithTag("income_menu_pending").performClick()
        compose.onNodeWithText("Mark Received").performClick()
        compose.runOnIdle { assertEquals(1, received) }
        scrollTo("Brand project")
        compose.onNodeWithTag("income_menu_pending").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, deleted) }
        compose.onNodeWithTag("income_menu_pending").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
    }

    @Test fun narrowScreenWithLargeTextKeepsAmountsAndNavigationVisible() {
        val large = incomes + Income(id = "large", amountMinor = 999_999_999_99, receivedDate = "2026-10-24")
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.3f)) {
                PFMSTheme(darkTheme = false) {
                    Box(Modifier.requiredSize(320.dp, 640.dp)) { IncomeListScreen(state(large)) }
                }
            }
        }
        scrollTo(state(large).total).assertIsDisplayed()
        assertAmountFits(compose.onNodeWithTag("income_total"))
        scrollTo("+LKR 999,999,999.99").assertIsDisplayed()
        assertAmountFits(compose.onNodeWithText("+LKR 999,999,999.99", useUnmergedTree = true))
        compose.onNode(hasText("Income") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertIsDisplayed().assertIsSelected()
        compose.onNodeWithContentDescription("Previous income month").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next income month").assertIsDisplayed()
        assertAmountFits(compose.onNodeWithTag("income_month_label", useUnmergedTree = true))
        listOf("Home", "Income", "Expenses", "Goals", "Insights").forEach {
            assertAmountFits(compose.onNode(hasText(it) and hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)), useUnmergedTree = true))
        }
        capture("income-small.png")
    }

    @Test fun monthAndNavigationActionsRemainAvailableInDarkMode() {
        var selectedMonth by mutableStateOf(month)
        var added = 0; var salary = 0; var report = 0; var home = 0; var expenses = 0; var goals = 0; var back = 0
        compose.setContent { PFMSTheme(darkTheme = true) {
            IncomeListScreen(state(controls = controls.copy(month = selectedMonth)), IncomeStreamActions(
                onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) }, onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
                onAdd = { added++ }, onSalary = { salary++ }, onAnalytics = { report++ },
                onHome = { home++ }, onExpenses = { expenses++ }, onGoals = { goals++ }, onBack = { back++ }))
        } }
        compose.onNodeWithContentDescription("Previous income month").performClick()
        compose.onNodeWithTag("income_total").assertTextEquals("LKR 200,000.00")
        compose.onNodeWithContentDescription("Next income month").performClick()
        compose.onNodeWithText("Add Income").performClick()
        compose.onNodeWithContentDescription("Recurring salary").performClick()
        compose.onNodeWithContentDescription("Income analytics").performClick()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Expenses").performClick()
        compose.onNodeWithText("Goals").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        capture("income-dark.png")
        compose.runOnIdle { assertEquals(listOf(1, 1, 1, 1, 1, 1, 1), listOf(added, salary, report, home, expenses, goals, back)) }
    }

    private fun scrollTo(value: String, ignoreCase: Boolean = false, substring: Boolean = false): SemanticsNodeInteraction {
        val matcher = hasText(value, substring = substring, ignoreCase = ignoreCase)
        compose.onNodeWithTag("income_sections").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun assertAmountFits(node: SemanticsNodeInteraction) {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty())
        assertFalse("The complete amount must fit its card", results.first().hasVisualOverflow)
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

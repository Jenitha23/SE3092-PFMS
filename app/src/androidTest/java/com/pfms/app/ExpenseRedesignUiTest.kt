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
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.ui.screen.expense.*
import com.pfms.app.ui.screen.income.SalaryTemplateScreen
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.ExpenseFormData
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** In-memory fixtures and callbacks only; no signed-in account is modified. */
class ExpenseRedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var closeKeyboard: () -> Unit = {}
    private var ime = 0
    private val today = AppClock.today()
    private val date = today.withDayOfMonth(1).toString()
    private val options = listOf(ExpenseCategoryOption("rent", "Rent", ExpenseClassification.COMMITTED),
        ExpenseCategoryOption("food", "Food & Dining", ExpenseClassification.DISCRETIONARY),
        ExpenseCategoryOption("groceries", "Groceries", ExpenseClassification.DISCRETIONARY))
    private val records = listOf(
        Expense("rent", amountMinor = 55100000, categoryId = "rent", categoryName = "Rent", merchant = "Apartment Monthly Rent", classification = ExpenseClassification.COMMITTED, date = date),
        Expense("subs", amountMinor = 600000, categoryName = "Subscriptions", merchant = "Subscriptions", classification = ExpenseClassification.COMMITTED, date = date),
        Expense("utilities", amountMinor = 535000, categoryName = "Utilities", date = date),
        Expense("food", amountMinor = 350000, categoryId = "food", categoryName = "Food & Dining", merchant = "Pizza Dinner & Drinks", date = today.toString()),
        Expense("other", amountMinor = 490000, categoryName = "Other", date = date))

    @Before fun window() { compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() } }
    private fun content(dark: Boolean = false, small: Boolean = false, block: @Composable () -> Unit) = compose.setContent {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val inset = WindowInsets.ime.getBottom(LocalDensity.current)
        SideEffect { ime = inset; closeKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, if (small) 1.3f else 1f)) {
            PFMSTheme(darkTheme = dark) {
                if (small) Box(Modifier.requiredSize(320.dp, 640.dp)) { block() } else block()
            }
        }
    }
    private fun hideKeyboard() {
        compose.runOnIdle { closeKeyboard() }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
    }

    @Test fun salaryPaydayAndReminderAreEditableAndSaved() {
        var result: Triple<String, Int, Boolean>? = null
        content { SalaryTemplateScreen(SalaryTemplate("salary", amountMinor = 25000000), {}, {},
            onSaveSchedule = { amount, day, enabled -> result = Triple(amount, day, enabled) }) }
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("31", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("31st of every month").assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Update Salary Template").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Triple("250000.00", 31, false), result) }
        capture("salary-redesign.png")
    }

    @Test fun expenseSummaryUsesRealMonthAndRecordActionsAskBeforeDelete() {
        var deleted: String? = null
        content { ExpenseListScreen(records, false, false, {}, {}, {}, { deleted = it }, {}, onHome = {}, onIncome = {}) }
        compose.onNodeWithTag("expense_total").assertTextEquals("LKR 570,750.00")
        fits(compose.onNodeWithTag("expense_total"))
        assertTrue(compose.onNodeWithTag("expense_summary").getUnclippedBoundsInRoot().let { it.bottom - it.top } < 330.dp)
        capture("expenses-redesign.png")
        compose.onNodeWithTag("expense_sections").performScrollToNode(hasContentDescription("Actions for Pizza Dinner & Drinks"))
        compose.onNodeWithContentDescription("Actions for Pizza Dinner & Drinks").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Actions for Pizza Dinner & Drinks").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals("food", deleted) }
    }

    @Test fun addExpenseKeepsSaveAboveKeyboardAndPersistsDetails() {
        var result: ExpenseFormData? = null
        content { ExpenseFormScreen(null, options, false, { result = it }, {}, pastExpenses = records) }
        compose.onNodeWithText("Amount (LKR)").performTextInput("4250.00")
        compose.onNodeWithText("Save Expense").assertIsDisplayed()
        hideKeyboard()
        compose.onNodeWithContentDescription("Category *").performScrollTo().performClick()
        compose.onNodeWithText("Groceries").performClick()
        compose.onNodeWithContentDescription("Payment Method *").performScrollTo().performClick()
        compose.onNodeWithText("Card").performClick()
        compose.onNodeWithText("Merchant (Optional)").performScrollTo().performTextInput("Keells Supermarket")
        compose.onNodeWithText("Description / Note (optional)").performScrollTo().performTextInput("Weekly groceries")
        hideKeyboard()
        capture("expense-add-redesign.png")
        compose.onNodeWithText("Save Expense").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("4250.00", result?.amount); assertEquals("groceries", result?.categoryId)
            assertEquals(ExpensePaymentMethod.CARD, result?.paymentMethod); assertEquals("Weekly groceries", result?.note)
        }
    }

    @Test fun quickAddSavesDatePaymentTypeAndNoteFromTheSheet() {
        var result: ExpenseFormData? = null
        content { QuickAddExpenseSheet(options, ExpensePaymentMethod.CASH, false, { _, _, _, _ -> }, {},
            onSaveDetails = { result = it }, monthlyExpenses = records) }
        compose.onNodeWithText("Amount (LKR)").assertIsFocused().performTextInput("1500.00")
        hideKeyboard()
        compose.onNodeWithText("Food & Dining").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Payment Method *").performScrollTo().performClick()
        compose.onNodeWithText("Card").performClick()
        compose.onNodeWithContentDescription("Date *").performScrollTo().performClick()
        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithText("Optional note").performScrollTo().performTextInput("Lunch with friends")
        hideKeyboard()
        capture("expense-quick-redesign.png")
        compose.onNodeWithText("Save Expense").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("food", result?.categoryId); assertEquals("1500.00", result?.amount)
            assertEquals(ExpensePaymentMethod.CARD, result?.paymentMethod)
            assertEquals(today.toString(), result?.date); assertEquals("Lunch with friends", result?.note)
        }
    }

    @Test fun narrowDarkExpenseFormKeepsLargeValuesAndSaveReadable() {
        val original = Expense("large", amountMinor = 99999999999, categoryId = "rent", categoryName = "Rent",
            date = date, paymentMethod = ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT)
        content(dark = true, small = true) { ExpenseFormScreen(original, options, false, {}, {}) }
        compose.onNodeWithTag("expense_form_value").performScrollTo().assertTextEquals("LKR 999,999,999.99")
        fits(compose.onNodeWithTag("expense_form_value"))
        compose.onNodeWithContentDescription("Payment Method *").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Update Expense").assertIsDisplayed()
        capture("expense-form-small-dark.png")
    }

    @Test fun narrowOverviewMonthControlsAndNavigationRemainVisible() {
        content(small = true) { ExpenseListScreen(records, false, false, {}, {}, {}, {}, {}, onHome = {}, onIncome = {}) }
        fits(compose.onNodeWithTag("expense_total"))
        compose.onNodeWithTag("expense_sections").performScrollToNode(hasText("LKR 551,000.00"))
        fits(compose.onNodeWithText("LKR 551,000.00", useUnmergedTree = true))
        compose.onNodeWithContentDescription("Previous expense month").performClick()
        compose.onNodeWithTag("expense_total").assertTextEquals("LKR 0.00")
        compose.onNodeWithContentDescription("Next expense month").performClick()
        compose.onNodeWithTag("expense_total").assertTextEquals("LKR 570,750.00")
        compose.onNode(hasText("Expenses") and isSelected()).assertIsDisplayed()
    }

    @Test fun quickAddWithThirteenCategoriesKeepsTilesAlignedAndSeparated() {
        val names = listOf("Coffee & Dining Out", "Entertainment", "Food Delivery", "Groceries", "Gym", "Health",
            "Other", "Rent", "Shopping", "Subscriptions", "Test", "Transport", "Utilities")
        val choices = names.mapIndexed { index, name -> ExpenseCategoryOption("c$index", name, ExpenseClassification.DISCRETIONARY) }
        var result: ExpenseFormData? = null
        content { QuickAddExpenseSheet(choices, ExpensePaymentMethod.CASH, false, { _, _, _, _ -> }, {},
            onSaveDetails = { result = it }, monthlyExpenses = records) }
        compose.onNodeWithText("Amount (LKR)").performTextInput("1500.00")
        hideKeyboard()
        compose.onNodeWithTag("quick_category_c0").performScrollTo()
        val first = compose.onNodeWithTag("quick_category_c0").getUnclippedBoundsInRoot()
        val paired = compose.onNodeWithTag("quick_category_c1").getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag("quick_category_c2").getUnclippedBoundsInRoot()
        assertEquals("Category tiles share a top edge", first.top.value, paired.top.value, 1f)
        assertEquals("Category tiles use equal columns", (first.right - first.left).value, (paired.right - paired.left).value, 1f)
        assertTrue("Rows have breathing room", next.top - first.bottom >= 9.dp)
        assertTrue("Columns have breathing room", paired.left - first.right >= 9.dp)
        capture("expense-quick-aligned-categories.png")
        compose.onNodeWithTag("quick_category_c12").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Payment Method *").performScrollTo()
        capture("expense-quick-aligned-details.png")
        compose.onNodeWithText("Save Expense").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("c12", result?.categoryId); assertEquals("1500.00", result?.amount) }
    }

    @Test fun expenseCardsAlignAmountsWithTitlesAndAvoidRepeatedCategoryText() {
        val samples = listOf(
            Expense("coffee", amountMinor = 10000, categoryId = "coffee", categoryName = "Coffee & Dining Out", merchant = "Coffee & Dining Out", date = today.toString()),
            Expense("gym", amountMinor = 100000, categoryId = "gym", categoryName = "Gym", merchant = "Gym", classification = ExpenseClassification.COMMITTED, date = date))
        content { ExpenseListScreen(samples, false, false, {}, {}, {}, {}, {}, onHome = {}, onIncome = {}) }
        compose.onNodeWithTag("expense_sections").performScrollToNode(hasTestTag("expense_record_coffee"))
        val card = compose.onNodeWithTag("expense_record_coffee").getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("Coffee & Dining Out") and hasAnyAncestor(hasTestTag("expense_record_coffee")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val amountNode = compose.onNodeWithTag("expense_amount_coffee", useUnmergedTree = true)
        val amount = amountNode.getUnclippedBoundsInRoot()
        assertTrue("Amounts belong on the right", amount.left > title.left)
        assertEquals("Title and amount share a row", title.top.value, amount.top.value, 3f)
        assertTrue("Normal record cards remain compact", card.bottom - card.top <= 124.dp)
        fits(amountNode)
        compose.onAllNodes(hasText("Coffee & Dining Out") and hasAnyAncestor(hasTestTag("expense_record_coffee")), useUnmergedTree = true).assertCountEquals(1)
        capture("expense-records-aligned.png")
    }

    @Test fun narrowExpenseCardsKeepTheEntireAmountVisibleWithLargeText() {
        val sample = Expense("large-row", amountMinor = 99999999999, categoryName = "Groceries", merchant = "Weekly shopping", date = date)
        content(dark = true, small = true) { ExpenseListScreen(listOf(sample), false, false, {}, {}, {}, {}, {}, onHome = {}, onIncome = {}) }
        compose.onNodeWithTag("expense_sections").performScrollToNode(hasTestTag("expense_record_large-row"))
        val amount = compose.onNodeWithTag("expense_amount_large-row", useUnmergedTree = true)
        amount.assertTextEquals("−LKR 999,999,999.99")
        fits(amount)
        capture("expense-records-aligned-small.png")
    }

    private fun fits(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty()); assertFalse(layouts.any { it.hasVisualOverflow })
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}

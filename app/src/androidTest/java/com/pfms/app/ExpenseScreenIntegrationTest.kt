package com.pfms.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import com.pfms.app.domain.model.*
import com.pfms.app.data.model.DefaultFinancialData
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.ui.screen.expense.CategoriesScreen
import com.pfms.app.ui.screen.expense.RecurringExpensesScreen
import com.pfms.app.ui.screen.expense.ExpenseListScreen
import com.pfms.app.ui.screen.expense.ExpenseFormScreen
import com.pfms.app.ui.screen.expense.ExpenseCategoryOption
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.ExpenseFormData
import com.pfms.app.viewmodel.AdvancedExpenseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseScreenIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private var dismissKeyboard: () -> Unit = {}

    private fun setScreen(content: @Composable () -> Unit) = compose.setContent {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        SideEffect { dismissKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        content()
    }

    private fun closeKeyboard() {
        compose.runOnIdle { dismissKeyboard() }
        compose.waitForIdle()
    }

    @Test fun expenseSearchAndTransactionMenuStayConnected() {
        val date = LocalDate.now(ZoneId.of("Asia/Colombo")).withDayOfMonth(1).toString()
        val fixtures = listOf(
            Expense(id = "lunch", categoryId = "food", categoryName = "Food & Dining", merchant = "Lunch & Coffee", date = date, amountMinor = 250000, paymentMethod = ExpensePaymentMethod.CARD),
            Expense(id = "ride", categoryName = "Transport", merchant = "PickMe", date = date, amountMinor = 120000, paymentMethod = ExpensePaymentMethod.CARD),
            Expense(id = "rent", categoryName = "Rent", merchant = "Rent", date = date, amountMinor = 3400000, classification = ExpenseClassification.COMMITTED),
            Expense(id = "spotify", categoryName = "Subscriptions", merchant = "Spotify Premium", date = date, amountMinor = 150000, paymentMethod = ExpensePaymentMethod.CARD)
        )
        var edited: String? = null
        setScreen { PFMSTheme {
            ExpenseListScreen(fixtures, false, false, {}, {}, { edited = it }, {}, {},
                onCategories = {}, onRecurring = {}, onSubscriptions = {}, onHome = {}, onIncome = {})
        } }
        compose.onNodeWithText("Search expenses…").performScrollTo().performTextInput("Lunch")
        closeKeyboard()
        compose.onNodeWithText("1 expense").assertExists()
        compose.onNodeWithContentDescription("Actions for Lunch & Coffee").performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.runOnIdle { assertEquals("lunch", edited) }
    }

    @Test fun redesignedExpenseFormSavesSelectedDetails() {
        var saved: ExpenseFormData? = null
        setScreen { PFMSTheme {
            ExpenseFormScreen(null, listOf(ExpenseCategoryOption("food", "Food & Dining", ExpenseClassification.DISCRETIONARY)),
                false, { saved = it }, {}, onManageRecurring = {})
        } }
        compose.onNodeWithText("Amount (LKR)").performTextInput("2500")
        closeKeyboard()
        compose.onNodeWithContentDescription("Category *").performClick()
        compose.onNodeWithText("Food & Dining").performClick()
        compose.onNodeWithContentDescription("Payment Method *").performClick()
        compose.onNodeWithText("Card").performClick()
        compose.onNodeWithText("Merchant (Optional)").performScrollTo().performTextInput("Lunch & Coffee")
        compose.onNodeWithText("Description / Note (optional)").performScrollTo().performTextInput("Coffee with friends")
        closeKeyboard()
        compose.onNodeWithText("Amount (LKR)").performScrollTo()
        compose.onNodeWithText("Save Expense").performClick()
        compose.runOnIdle {
            assertEquals("2500", saved?.amount)
            assertEquals("food", saved?.categoryId)
            assertEquals(ExpensePaymentMethod.CARD, saved?.paymentMethod)
            assertEquals("Coffee with friends", saved?.note)
        }
    }

    private class MemoryExpenses : AdvancedExpenseRepository {
        val categoryRows = MutableStateFlow(listOf(ExpenseCategory("rent", "Rent", ExpenseClassification.COMMITTED)))
        val templateRows = MutableStateFlow<List<RecurringExpense>>(emptyList())
        var rejectSave = false
        override fun categories() = categoryRows
        override fun templates() = templateRows
        override suspend fun refreshTemplates() = Unit
        override suspend fun initializeDefaults() = Unit
        override suspend fun createCategory(name: String, classification: ExpenseClassification) {
            categoryRows.value += ExpenseCategory("custom", name, classification, custom = true)
        }
        override suspend fun updateCategory(category: ExpenseCategory) {
            categoryRows.value = categoryRows.value.map { if (it.id == category.id) category else it }
        }
        override suspend fun saveTemplate(template: RecurringExpense) {
            check(!rejectSave) { "Connection unavailable" }
            templateRows.value = templateRows.value.filterNot { it.id == template.id } + template
        }
        override suspend fun deleteTemplate(templateId: String) {
            templateRows.value = templateRows.value.filterNot { it.id == templateId }
        }
        override suspend fun confirmDue(template: RecurringExpense) = Unit
        override suspend fun repeatExpense(expenseId: String) = Unit
    }

    @Test fun categoryCanBeAddedRenamedArchivedAndRestored() {
        val repo = MemoryExpenses()
        val vm = AdvancedExpenseViewModel(repo)
        setScreen { MaterialTheme { CategoriesScreen(vm) {} } }
        compose.onNodeWithText("Category Name (1–50 characters)").performScrollTo().performTextInput("Books")
        closeKeyboard()
        compose.onNodeWithText("Add Category").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit Books").performScrollTo().performClick()
        // Editing scrolls the form into view. Do not issue a second competing scroll while its
        // animateScrollToItem is settling; assert the user-visible result before editing instead.
        compose.onNodeWithText("Category Name (1–50 characters)").assertIsDisplayed().performTextReplacement("Learning")
        closeKeyboard()
        compose.onNodeWithText("Save Changes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Archive Learning").performScrollTo().performClick()
        compose.onNodeWithText("Archive", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Archived (1)").performClick()
        compose.onNodeWithText("Restore").performClick()
        compose.runOnIdle {
            assertEquals("Learning", repo.categoryRows.value.single { it.custom }.name)
            assertFalse(repo.categoryRows.value.single { it.custom }.archived)
        }
    }

    @Test fun editingCategoryBelowDefaultsBringsFormIntoView() {
        val repo = MemoryExpenses().apply {
            categoryRows.value = DefaultFinancialData.expenseCategories.map {
                ExpenseCategory(it.id, it.name, ExpenseClassification.valueOf(it.classification.uppercase()))
            } + ExpenseCategory("books", "Books", ExpenseClassification.DISCRETIONARY, custom = true)
        }
        val vm = AdvancedExpenseViewModel(repo)
        setScreen { MaterialTheme { CategoriesScreen(vm) {} } }

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Edit Books"))
        compose.onNodeWithContentDescription("Edit Books").performClick()
        // Do not scroll here: tapping Edit must reveal the form for the user.
        compose.onNodeWithText("Edit Category: Books").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().performTextReplacement("Learning")
        closeKeyboard()
        compose.onNodeWithText("Save Changes").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Learning", repo.categoryRows.value.single { it.id == "books" }.name)
        }

        // Editing the same category again must also return to the form.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Edit Learning"))
        compose.onNodeWithContentDescription("Edit Learning").performClick()
        compose.onNodeWithText("Edit Category: Learning").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("Learning")
    }

    @Test fun cancelledSubscriptionCanBeResumedAndCountedAgain() {
        val repo = MemoryExpenses()
        repo.templateRows.value = listOf(RecurringExpense(id = "sub", title = "Streaming", categoryId = "rent",
            amountMinor = 250000, subscription = true, active = false, flag = SubscriptionFlag.CANCEL, nextDueDate = "2099-01-01"))
        val vm = AdvancedExpenseViewModel(repo)
        setScreen { MaterialTheme { RecurringExpensesScreen(vm, {}, subscriptionsOnly = true) } }
        compose.onNodeWithText("Resume").performScrollTo().performClick()
        compose.onNodeWithTag("subscription_monthly_total").assertTextEquals("LKR 2,500.00")
        compose.runOnIdle {
            assertTrue(repo.templateRows.value.single().active)
            assertEquals(SubscriptionFlag.NONE, repo.templateRows.value.single().flag)
        }
    }

    @Test fun failedTemplateSaveKeepsEnteredDataForRetry() {
        val repo = MemoryExpenses().apply { rejectSave = true }
        val vm = AdvancedExpenseViewModel(repo)
        setScreen { MaterialTheme { RecurringExpensesScreen(vm, {}) } }
        compose.onNodeWithText("+ New").performClick()
        compose.onNodeWithText("Template Name / Merchant").performTextInput("Home Wifi")
        compose.onNodeWithText("Amount in LKR (e.g. 1500.00)").performTextInput("1500.00")
        compose.onNodeWithText("Save Template").assertIsDisplayed().performClick()
        compose.onNodeWithText("Home Wifi").assertExists()
        compose.onNodeWithText("Save Template").assertExists()
        compose.runOnIdle { assertTrue(repo.templateRows.value.isEmpty()) }
    }
}

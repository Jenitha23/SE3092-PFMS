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
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.domain.usecase.RecurringExpenseCalculator
import com.pfms.app.ui.screen.expense.*
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.AdvancedExpenseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

class AdvancedExpenseRedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE)
    private val repo = MemoryExpenses()
    private var dismissKeyboard: () -> Unit = {}
    @Before fun window() { compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() } }
    private fun content(small: Boolean = false, dark: Boolean = false, block: @Composable () -> Unit) = compose.setContent {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        SideEffect { dismissKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, if (small) 1.3f else 1f)) {
            PFMSTheme(darkTheme = dark) { if (small) Box(Modifier.requiredSize(320.dp, 640.dp)) { block() } else block() }
        }
    }
    private fun hideKeyboard() {
        compose.runOnIdle { dismissKeyboard() }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
    }

    @Test fun subscriptionReviewAndConfirmationKeepTheScheduledLedgerWorkflow() {
        repo.templates.value = listOf(
            RecurringExpense("cloud", "Cloud storage", "subs", "Subscriptions", amountMinor = 250000,
                nextDueDate = today.withDayOfMonth(1).toString(), subscription = true, reminderDate = today.toString()),
            RecurringExpense("music", "Music Premium", "subs", "Subscriptions", amountMinor = 99900,
                nextDueDate = today.plusDays(7).toString(), subscription = true),
            RecurringExpense("paused", "Streaming service", "subs", "Subscriptions", amountMinor = 180000,
                nextDueDate = today.plusDays(10).toString(), subscription = true, active = false))
        val vm = AdvancedExpenseViewModel(repo)
        content { RecurringExpensesScreen(vm, {}, true) }
        compose.onNodeWithTag("subscription_monthly_total").assertTextEquals("LKR 3,499.00")
        fits(compose.onNodeWithTag("subscription_monthly_total"))
        capture("subscriptions-redesign.png")
        compose.onNodeWithText("Keep").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("", repo.templates.value.first { it.id == "cloud" }.reminderDate) }
        compose.onNodeWithText("Confirm Payment").performScrollTo().performClick()
        compose.onNodeWithText("Amount (LKR)").performTextReplacement("2500.75")
        hideKeyboard()
        compose.onNodeWithText("Confirm & Record").performClick()
        compose.runOnIdle {
            assertEquals(250075L, repo.confirmed.single().amountMinor)
            assertEquals(today.withDayOfMonth(1).toString(), repo.confirmed.single().nextDueDate)
        }
        compose.onNodeWithContentDescription("Actions for Music Premium").performScrollTo().performClick()
        compose.onNodeWithText("Cancel Subscription").performClick()
        compose.onNodeWithTag("subscription_monthly_total").assertTextEquals("LKR 2,500.75")
        compose.runOnIdle { assertFalse(repo.templates.value.first { it.id == "music" }.active) }
    }

    @Test fun recurringEditorKeepsSaveVisibleAndSavesPaymentAndFrequency() {
        repo.templates.value = listOf(RecurringExpense("rent", "Apartment monthly rent", "rent", "Rent", amountMinor = 55100000,
            nextDueDate = today.plusDays(10).toString()))
        val vm = AdvancedExpenseViewModel(repo)
        content { RecurringExpensesScreen(vm, {}) }
        compose.onNodeWithTag("template_amount_rent").assertTextEquals("LKR 551,000.00")
        fits(compose.onNodeWithTag("template_amount_rent"))
        capture("recurring-redesign.png")
        compose.onNodeWithText("+ New").performClick()
        compose.onNodeWithText("Template Name / Merchant").performTextInput("Home Wi-Fi")
        compose.onNodeWithText("Amount in LKR (e.g. 1500.00)").performScrollTo().performTextInput("1500.50")
        compose.onNodeWithText("Save Template").assertIsDisplayed()
        hideKeyboard()
        compose.onNodeWithText("Weekly").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Payment Method").performScrollTo().performClick()
        compose.onNodeWithText("Card").performClick()
        capture("recurring-editor-redesign.png")
        compose.onNodeWithText("Save Template").assertIsDisplayed().performClick()
        compose.runOnIdle {
            val saved = repo.templates.value.single { it.title == "Home Wi-Fi" }
            assertEquals(150050L, saved.amountMinor)
            assertEquals(Recurrence.WEEKLY, saved.recurrence)
            assertEquals(ExpensePaymentMethod.CARD, saved.paymentMethod)
            assertEquals(today.toString(), saved.nextDueDate)
        }
    }

    @Test fun narrowDarkSubscriptionKeepsFullAmountAndEditsPausedTemplate() {
        val original = RecurringExpense("large", "Professional cloud storage and backups", "subs", "Subscriptions", amountMinor = 99999999999,
            nextDueDate = today.plusDays(20).toString(), active = false, subscription = true)
        repo.templates.value = listOf(original)
        val vm = AdvancedExpenseViewModel(repo)
        content(small = true, dark = true) { RecurringExpensesScreen(vm, {}, true) }
        compose.onNodeWithTag("template_amount_large").performScrollTo().assertTextEquals("LKR 999,999,999.99")
        fits(compose.onNodeWithTag("template_amount_large"))
        capture("subscriptions-small-dark.png")
        compose.onNodeWithContentDescription("Actions for ${original.title}").performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Amount in LKR (e.g. 1500.00)").performScrollTo().performTextReplacement("2999.95")
        hideKeyboard()
        compose.onNodeWithText("Update Template").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("large", repo.templates.value.single().id)
            assertEquals(299995L, repo.templates.value.single().amountMinor)
            assertFalse(repo.templates.value.single().active)
        }
    }

    @Test fun categoriesKeepProtectedDefaultsAndRestoreCustomCategoryOnSmallScreen() {
        repo.categories.value += ExpenseCategory("books", "Books & professional learning", custom = true)
        val vm = AdvancedExpenseViewModel(repo)
        content(small = true) { CategoriesScreen(vm, {}) }
        compose.onNodeWithTag("category_sections").performScrollToNode(hasTestTag("category_books"))
        capture("categories-small-redesign.png")
        compose.onNodeWithContentDescription("Archive Books & professional learning").performClick()
        compose.onNodeWithText("Archive", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Archived (1)").performClick()
        compose.onNodeWithText("Restore").performScrollTo().performClick()
        compose.onNodeWithText("Active (3)").assertExists()
        compose.runOnIdle { assertFalse(repo.categories.value.single { it.id == "books" }.archived) }
    }

    @Test fun deleteTemplateWaitsForConfirmationAndPreservesRecordedHistory() {
        repo.templates.value = listOf(RecurringExpense("wifi", "Home Wi-Fi", "rent", "Rent", amountMinor = 150000,
            nextDueDate = today.plusDays(7).toString()))
        val vm = AdvancedExpenseViewModel(repo)
        content { RecurringExpensesScreen(vm, {}) }
        compose.onNodeWithContentDescription("Actions for Home Wi-Fi").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(1, repo.templates.value.size) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Actions for Home Wi-Fi").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertTrue(repo.templates.value.isEmpty()); assertEquals(0, repo.confirmed.size) }
    }

    @Test fun recurringScheduleFiltersKeepTheRealMonthlyEstimate() {
        repo.templates.value = listOf(
            RecurringExpense("rent", "Monthly Rent", "rent", "Rent", amountMinor = 2500000, nextDueDate = today.plusDays(12).toString()),
            RecurringExpense("wifi", "Home Wi-Fi", "utilities", "Utilities", amountMinor = 185000, nextDueDate = today.plusDays(14).toString()),
            RecurringExpense("electricity", "CEB Electricity", "utilities", "Utilities", amountMinor = 350000, nextDueDate = today.toString()))
        val vm = AdvancedExpenseViewModel(repo)
        content { RecurringExpensesScreen(vm, {}) }
        compose.onNodeWithTag("recurring_monthly_total").assertTextEquals("LKR 30,350.00")
        fits(compose.onNodeWithTag("recurring_monthly_total"))
        capture("recurring-schedule-redesign.png")
        compose.onNodeWithTag("recurring_filter_due").performClick()
        compose.onNodeWithText("Monthly Rent").assertDoesNotExist()
        compose.onNodeWithTag("template_amount_electricity").performScrollTo().assertTextEquals("LKR 3,500.00")
        compose.onNodeWithText("Confirm Payment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recurring_filter_all").performScrollTo().performClick()
        compose.onNodeWithTag("recurring_sections").performScrollToNode(hasText("Home Wi-Fi"))
        compose.onNodeWithTag("template_amount_wifi").assertTextEquals("LKR 1,850.00")
        compose.onNodeWithText("Create Recurring Expense").assertIsDisplayed()
    }

    private fun fits(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertTrue("The complete amount fits", layouts.none { it.hasVisualOverflow })
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private class MemoryExpenses : AdvancedExpenseRepository {
        val categories = MutableStateFlow(listOf(ExpenseCategory("rent", "Rent", ExpenseClassification.COMMITTED), ExpenseCategory("subs", "Subscriptions", ExpenseClassification.COMMITTED)))
        val templates = MutableStateFlow(emptyList<RecurringExpense>())
        val confirmed = mutableListOf<RecurringExpense>()
        override fun categories() = categories
        override fun templates() = templates
        override suspend fun refreshTemplates() = Unit
        override suspend fun initializeDefaults() = Unit
        override suspend fun createCategory(name: String, classification: ExpenseClassification) { categories.value += ExpenseCategory("custom", name, classification, custom = true) }
        override suspend fun updateCategory(category: ExpenseCategory) { categories.value = categories.value.map { if (it.id == category.id) category else it } }
        override suspend fun saveTemplate(template: RecurringExpense) { templates.value = templates.value.filterNot { it.id == template.id } + template }
        override suspend fun deleteTemplate(templateId: String) { templates.value = templates.value.filterNot { it.id == templateId } }
        override suspend fun confirmDue(template: RecurringExpense) {
            confirmed += template
            saveTemplate(template.copy(nextDueDate = RecurringExpenseCalculator.calculateNextDueDate(LocalDate.parse(template.nextDueDate), template.recurrence).toString()))
        }
        override suspend fun repeatExpense(expenseId: String) = Unit
    }
}

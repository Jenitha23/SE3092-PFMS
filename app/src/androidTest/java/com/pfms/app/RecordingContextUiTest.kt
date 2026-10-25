package com.pfms.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.MonthlyDataPolicy
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.ui.component.*
import com.pfms.app.ui.screen.expense.*
import com.pfms.app.ui.screen.income.*
import com.pfms.app.viewmodel.MonthlyDataUiState
import com.pfms.app.viewmodel.ExpenseFormData
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class RecordingContextUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 20)
    private val month = YearMonth.of(2026, 9)
    private var hideKeyboard: () -> Unit = {}
    private fun content(block: @Composable () -> Unit) = compose.setContent {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        SideEffect { hideKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        MaterialTheme { block() }
    }

    @Test fun emptyMonthHasExplanationAndExplicitNoActivityConfirmation() {
        var records by mutableStateOf(MonthlyFinancialRecords("a", month))
        content {
            MonthlyDataNotice(MonthlyDataUiState("a", MonthlyDataPolicy.assess(records, today), false),
                onReview = { records = records.copy(reviewedSignature = it) })
        }
        compose.onNodeWithText("Partial Data").assertExists()
        compose.onNodeWithText("No entries are recorded", substring = true).assertExists()
        compose.onNodeWithText("Review month").performClick()
        compose.onNodeWithText("Mark as reviewed").performClick()
        compose.onNodeWithText("Reviewed").assertExists()
        compose.onNodeWithText("You confirmed no financial activity for this month.").assertExists()
    }

    @Test fun missingConversionExplainsPartialDataWithoutFalseReviewAction() {
        val records = MonthlyFinancialRecords("a", month,
            listOf(Income("usd", "a", 10000, "USD", receivedDate = "2026-09-01")))
        content { MonthlyDataNotice(MonthlyDataUiState("a", MonthlyDataPolicy.assess(records, today), false), {}) }
        compose.onNodeWithText("Partial Data").assertExists()
        compose.onNodeWithText("Some entries need a saved LKR conversion.", substring = true).assertExists()
        compose.onNodeWithText("Review month").assertDoesNotExist()
    }

    @Test fun welcomeOffersExistingEntryActionsAndCanBeDismissed() {
        var visible by mutableStateOf(true)
        var incomes = 0; var expenses = 0
        content { if (visible) WelcomeBackPrompt({ visible = false }, { incomes++ }, { expenses++ }) }
        compose.onNodeWithText("Welcome back! Ready to record your recent finances?").assertExists()
        compose.onNodeWithText("Add Income").performClick()
        compose.onNodeWithText("Add Expense").performClick()
        compose.runOnIdle { assertEquals(1, incomes); assertEquals(1, expenses) }
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Welcome back!", substring = true).assertDoesNotExist()
    }

    @Test fun historicalIncomeDatePickerKeepsManualRateAndFailedFormInput() {
        var result: IncomeFormInput? = null
        var error by mutableStateOf<String?>(null)
        var rateFetches = 0
        val income = Income("draft", "a", 12345, "USD", receivedDate = "2026-09-15")
        content {
            IncomeFormScreen(onBack = {}, initialIncome = income, exchangeRate = BigDecimal("300.25"),
                exchangeRateDate = income.receivedDate, errorMessage = error, canRetry = error != null,
                onFetchExchangeRate = { rateFetches++ },
                onSave = { result = it; error = "Something went wrong while saving. Please try again." })
        }
        compose.onNodeWithText("15 Sep 2026").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { hideKeyboard.invoke() }
        compose.onNodeWithText("Save Changes").performClick()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {
            assertEquals("2026-09-15", result?.receivedDate)
            assertEquals("123.45", result?.amount)
            // Reconfirming the same date must not clear the supplied historical/manual rate.
            assertEquals(0, rateFetches)
        }
    }

    @Test fun newIncomeCanSelectADayInThePreviousMonth() {
        var result: IncomeFormInput? = null
        val current = AppClock.today()
        content { IncomeFormScreen(onBack = {}, onSave = { result = it }) }
        compose.onNode(hasSetTextAction() and hasText("0.00")).performScrollTo().performTextReplacement("100.00")
        compose.runOnIdle { hideKeyboard.invoke() }
        compose.onNodeWithText(current.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)))
            .performScrollTo().performClick()
        compose.onNodeWithContentDescription("Change to previous month").performClick()
        val selected = YearMonth.from(current).minusMonths(1).atDay(15)
        // Material 3 exposes calendar days through their full spoken date, not visible day text.
        compose.onNodeWithText(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(Locale.getDefault()))).performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Save Income").performClick()
        compose.runOnIdle { assertEquals(selected.toString(), result?.receivedDate) }
    }

    @Test fun previousYearExpenseDateRemainsSelectedAfterFailureAndRetry() {
        var result: ExpenseFormData? = null
        var error by mutableStateOf<String?>(null)
        val expense = Expense(id = "draft", amountMinor = 25000, categoryId = "food", categoryName = "Food", date = "2025-09-20")
        content {
            ExpenseFormScreen(expense, listOf(ExpenseCategoryOption("food", "Food", ExpenseClassification.DISCRETIONARY)),
                false, { result = it; error = "Please check your internet connection and try again." }, {},
                errorMessage = error, canRetry = error != null)
        }
        compose.onNodeWithContentDescription("Date *").performScrollTo().performClick()
        compose.onNodeWithText("Select").performClick()
        compose.runOnIdle { hideKeyboard.invoke() }
        compose.onNodeWithText("Update Expense").performClick()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals("2025-09-20", result?.date); assertEquals("250.00", result?.amount) }
    }
}

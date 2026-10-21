package com.pfms.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pfms.app.data.error.OperationErrors
import com.pfms.app.domain.model.*
import com.pfms.app.ui.component.OfflineSyncBanner
import com.pfms.app.ui.screen.expense.*
import com.pfms.app.ui.screen.income.*
import com.pfms.app.ui.screen.savings.SavingsGoalContent
import com.pfms.app.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class FinancialErrorUiTest {
    @get:Rule val compose = createComposeRule()
    private var hideKeyboard: () -> Unit = {}
    private fun content(block: @Composable () -> Unit) = compose.setContent {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        SideEffect { hideKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        MaterialTheme { block() }
    }
    private fun hideKeyboard() { compose.runOnIdle { hideKeyboard.invoke() }; compose.waitForIdle() }

    @Test fun incomeRetainsEveryFieldAfterFailureAndRetriesFromCurrentForm() {
        var error by mutableStateOf<String?>(null)
        var result: IncomeFormInput? = null
        var attempts = 0
        val original = Income(id = "draft", userId = "a", amountMinor = 12345, currency = "USD", source = "Freelance",
            receivedDate = "2026-10-01", projectName = "Client A", paymentStatus = "Expected", note = "Invoice note")
        content {
            IncomeFormScreen(onBack = {}, initialIncome = original, exchangeRate = BigDecimal("300.25"),
                exchangeRateDate = original.receivedDate, errorMessage = error, canRetry = error != null,
                onSave = { input -> attempts++; result = input; error = "Something went wrong while saving. Please try again." })
        }
        compose.onNode(hasSetTextAction() and hasText("123.45")).performScrollTo().performTextReplacement("987.65")
        hideKeyboard()
        compose.onNodeWithText("Save Changes").performScrollTo().performClick()
        compose.onNodeWithText("Something went wrong while saving. Please try again.").assertExists()
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, attempts)
            assertEquals("987.65", result?.amount)
            assertEquals("USD", result?.currency)
            assertEquals("Freelance", result?.source)
            assertEquals("2026-10-01", result?.receivedDate)
            assertEquals("Client A", result?.projectName)
            assertEquals("Expected", result?.paymentStatus)
            assertEquals("Invoice note", result?.note)
        }
    }

    @Test fun expenseRetainsAmountCategoryDateAndOptionalFieldsAfterFailure() {
        var error by mutableStateOf<String?>(null)
        var result: ExpenseFormData? = null
        val original = Expense(id = "draft", amountMinor = 12345, categoryId = "rent", categoryName = "Rent",
            classification = ExpenseClassification.COMMITTED, paymentMethod = ExpensePaymentMethod.CARD,
            date = "2026-10-01", merchant = "Landlord", note = "October payment")
        content {
            ExpenseFormScreen(original, listOf(ExpenseCategoryOption("rent", "Rent", ExpenseClassification.COMMITTED)),
                false, { result = it; error = "Please check your internet connection and try again." }, {},
                errorMessage = error, canRetry = error != null)
        }
        compose.onNodeWithText("Amount (LKR)").performScrollTo().performTextReplacement("456.78")
        hideKeyboard()
        compose.onNodeWithText("Update Expense").performScrollTo().performClick()
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("456.78", result?.amount)
            assertEquals("rent", result?.categoryId)
            assertEquals("2026-10-01", result?.date)
            assertEquals(ExpensePaymentMethod.CARD, result?.paymentMethod)
            assertEquals(ExpenseClassification.COMMITTED, result?.classificationOverride)
            assertEquals("Landlord", result?.merchant)
            assertEquals("October payment", result?.note)
        }
    }

    @Test fun savingsDialogStaysOpenOnFailureAndClosesOnlyAfterSuccess() {
        var state by mutableStateOf(SavingsGoalUiState(isLoading = false))
        var attempts = 0
        val submitted = mutableListOf<List<Any>>()
        content {
            SavingsGoalContent(state, {}, { name, amount, date, opening ->
                attempts++
                submitted += listOf(name, amount, date, opening)
                state = if (attempts == 1) state.copy(errorMessage = "Unable to save. Please try again.", canRetry = true)
                    else state.copy(errorMessage = null, canRetry = false, successVersion = 1)
            }, {}, {}, {}, { _, _, _ -> }, { _, _, _ -> }, {}, {}, {})
        }
        compose.onNodeWithText("Create Savings Goal").performClick()
        compose.onNodeWithText("Goal Name").performTextReplacement("Emergency fund")
        hideKeyboard()
        compose.onNode(hasText("Create Goal") and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText("Create Savings Goal") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText("Emergency fund").assertExists()
        // The dialog action retries the retained input; a Snackbar may also offer Retry.
        compose.onNode(hasText("Retry") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { attempts == 2 }
        compose.onNode(hasText("Create Savings Goal") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.runOnIdle { assertEquals(submitted[0], submitted[1]) }
    }

    @Test fun pendingSyncFailureExplainsLocalSaveAndRetryIsPresentationOnly() {
        var retries = 0
        content {
            OfflineSyncBanner(OfflineSyncState(true, 10, "Please check your internet connection and try again.", true),
                onRetry = { retries++ })
        }
        compose.onNodeWithText("Your changes are saved on this device", substring = true).assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun realFirestoreExceptionsNeverExposeTechnicalDetails() {
        for (code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED, FirebaseFirestoreException.Code.NOT_FOUND)) {
            val problem = OperationErrors.present(FirebaseFirestoreException("FirebaseFirestoreException: users/private stack", code))
            assertFalse(problem.message.contains("Firebase"))
            assertFalse(problem.message.contains("users/private"))
            assertEquals(code == FirebaseFirestoreException.Code.UNAVAILABLE, problem.canRetry)
        }
    }
}

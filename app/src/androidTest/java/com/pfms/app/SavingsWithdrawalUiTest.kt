package com.pfms.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.pfms.app.domain.model.*
import com.pfms.app.ui.screen.savings.AddTransactionDialog
import com.pfms.app.ui.screen.savings.EditTransactionDialog
import com.pfms.app.ui.screen.savings.SavingsGoalContent
import com.pfms.app.viewmodel.SavingsGoalUiState
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

class SavingsWithdrawalUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
    private val goal = SavingsGoal("goal", "user", "Emergency fund", 49000000, today.plusYears(1).toString(), 1120000)
    private val deposit = GoalTransaction("deposit", goal.id, goal.userId, 1500000, GoalTransactionType.CONTRIBUTION, today.minusDays(2).toString(), createdAt = 1)
    private val oldWithdrawal = GoalTransaction("withdrawal", goal.id, goal.userId, 500000, GoalTransactionType.WITHDRAWAL, today.minusDays(1).toString(), createdAt = 2)
    private var source by mutableStateOf(SavingsGoalUiState(isLoading = false, activeGoal = goal,
        transactions = listOf(deposit, oldWithdrawal), currentSavedMinor = 2120000))
    private var saved: Long? = null
    private var edited: GoalTransaction? = null
    private var dismissed = 0
    private fun amount(value: String) {
        compose.onNodeWithText("Amount (LKR)").performScrollTo().performTextReplacement(value)
        Espresso.closeSoftKeyboard()
    }

    @Test fun actualGoalWithdrawalShows21200Rejects25000AndAllowsCorrectionWithoutClosing() {
        compose.setContent {
            SavingsGoalContent(source, {}, { _, _, _, _ -> }, {}, {}, {}, { _, _, _ -> },
                { value, _, _ -> saved = value }, {}, {}, { source = source.copy(errorMessage = null, canRetry = false) })
        }
        compose.onNodeWithTag("savings_sections").performScrollToNode(hasText("Withdraw"))
        compose.onNodeWithText("Withdraw").performClick()
        compose.onNodeWithTag("withdrawal_available").assertTextEquals("LKR 21,200.00")
        amount("25000")
        compose.onNodeWithText("Confirm Withdrawal").performClick()
        compose.onNodeWithText("Insufficient savings. You can withdraw up to LKR 21,200.00.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertNull(saved); assertEquals(2120000L, source.currentSavedMinor); assertEquals(2, source.transactions.size) }
        compose.onNodeWithText("Record Withdrawal").assertExists()
        amount("1500.75")
        compose.onNodeWithText("Insufficient savings.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Confirm Withdrawal").performClick()
        compose.runOnIdle { assertEquals(150075L, saved) }
    }

    @Test fun compatibilityDialogRejectsInvalidInputsAndClearsTheBackendErrorOnCorrection() {
        var error by mutableStateOf<String?>("Insufficient goal savings. Available: LKR 21200.00; requested: LKR 25000.00.")
        compose.setContent {
            AddTransactionDialog(GoalTransactionType.WITHDRAWAL, 2120000, { dismissed++ }, { value, _, _ -> saved = value },
                false, errorMessage = error, onClearError = { error = null })
        }
        compose.onNodeWithText("Insufficient goal savings.", substring = true).assertExists()
        for (input in listOf("", "0", "-1", "abc", "1.001")) {
            amount(input)
            compose.onNodeWithText("Confirm Withdrawal").performClick()
            compose.onNodeWithText("Transaction amount must be greater than zero").performScrollTo().assertIsDisplayed()
            compose.runOnIdle { assertNull(saved); assertEquals(0, dismissed) }
        }
        amount("123.45")
        compose.onNodeWithText("Transaction amount must be greater than zero").assertDoesNotExist()
        compose.onNodeWithText("Insufficient goal savings.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Confirm Withdrawal").performClick()
        compose.runOnIdle { assertEquals(12345L, saved) }
    }

    @Test fun editingShowsReplacementAvailabilityAndRejectsAnOverdraftBeforeCallingUpdate() {
        compose.setContent {
            EditTransactionDialog(oldWithdrawal, { dismissed++ }, { edited = it }, false,
                goal = goal, transactions = listOf(deposit, oldWithdrawal))
        }
        compose.onNodeWithTag("withdrawal_available").assertTextEquals("LKR 26,200.00")
        compose.onNodeWithText("Current goal savings: LKR 21,200.00").assertExists()
        amount("30000")
        compose.onNodeWithText("Save Changes").performClick()
        compose.onNodeWithText("Insufficient savings. You can withdraw up to LKR 26,200.00.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertNull(edited); assertEquals(0, dismissed) }
        amount("25000")
        compose.onNodeWithText("Save Changes").performClick()
        compose.runOnIdle { assertEquals(oldWithdrawal.id, edited?.id); assertEquals(2500000L, edited?.amountMinor) }
    }
}

package com.pfms.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pfms.app.ui.screen.expense.*
import com.pfms.app.ui.screen.income.IncomeOnboardingScreen
import com.pfms.app.viewmodel.IncomeOnboardingState
import com.pfms.app.domain.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomeQuickAddUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun quickAddFocusesAmountAndSelectingCategoryNeedsOneTap() {
        var saved: String? = null
        compose.setContent { MaterialTheme {
            QuickAddExpenseSheet(listOf(ExpenseCategoryOption("rent", "Rent", ExpenseClassification.COMMITTED),
                ExpenseCategoryOption("food", "Food", ExpenseClassification.DISCRETIONARY)),
                ExpensePaymentMethod.CARD, false, { amount, id, _, _ -> saved = "$amount:$id" }, {})
        } }
        compose.onNodeWithText("Amount (LKR)").assertIsFocused().performTextInput("2500")
        compose.onNodeWithText("Food").performScrollTo().performClick()
        compose.onNodeWithText("Save Expense").performClick()
        compose.runOnIdle { assertEquals("2500:food", saved) }
    }
    @Test fun onboardingCollectsEstimatedMonthlyIncome() {
        var estimate: String? = null
        compose.setContent { MaterialTheme { IncomeOnboardingScreen(IncomeOnboardingState(required = true)) { estimate = it } } }
        compose.onNodeWithText("Estimated monthly income (LKR)").performTextInput("150000")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("150000", estimate) }
    }
}

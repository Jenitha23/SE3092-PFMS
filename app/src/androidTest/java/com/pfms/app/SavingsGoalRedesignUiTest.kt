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
import com.pfms.app.ui.screen.savings.SavingsGoalContent
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.SavingsGoalUiState
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class SavingsGoalRedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var source by mutableStateOf(SavingsGoalUiState(isLoading = false))
    private var created: List<Any>? = null
    private var updated: SavingsGoal? = null
    private var contribution: Triple<Long, String?, String?>? = null
    private var home = 0
    private var dismissKeyboard: () -> Unit = {}
    private val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
    @Before fun window() { compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() } }
    private fun content(small: Boolean = false, dark: Boolean = false) = compose.setContent {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        SideEffect { dismissKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, if (small) 1.3f else 1f)) { PFMSTheme(darkTheme = dark) {
            val screen: @Composable () -> Unit = { SavingsGoalContent(source, {}, { name, amount, date, opening -> created = listOf(name, amount, date, opening) },
                { updated = it }, {}, {}, { amount, note, date -> contribution = Triple(amount, note, date) }, { _, _, _ -> }, {}, {}, {},
                onHome = { home++ }, onIncome = {}, onExpenses = {}, onInsights = {}) }
            if (small) Box(Modifier.requiredSize(320.dp, 640.dp)) { screen() } else screen()
        } }
    }
    private fun hideKeyboard() { compose.runOnIdle { dismissKeyboard() }; androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.waitForIdle() }
    @Test fun emptyGoalIdeasOpenPrefilledFormAndTheFloatingNavigationWorks() {
        content()
        compose.onNodeWithText("No Active Savings Goal").assertIsDisplayed()
        val saved = compose.onNodeWithTag("empty_goal_saved").getUnclippedBoundsInRoot()
        val progress = compose.onNodeWithTag("empty_goal_progress").getUnclippedBoundsInRoot()
        assertEquals(saved.top.value, progress.top.value, 1f)
        assertEquals(saved.bottom.value, progress.bottom.value, 1f)
        assertEquals((saved.right - saved.left).value, (progress.right - progress.left).value, 1f)
        assertTrue(progress.left - saved.right >= 11.dp)
        compose.onNode(hasText("Goals") and isSelected()).assertIsDisplayed()
        capture("savings-empty-redesign.png")
        compose.onNodeWithTag("goal_idea_safety").performScrollTo().performClick()
        compose.onNodeWithText("Goal Name").assertTextContains("Emergency Fund")
        compose.onNodeWithText("Target Amount (LKR)").performTextInput("120000.00")
        hideKeyboard()
        compose.onNodeWithTag("goal_monthly_preview").performScrollTo().assertTextEquals("LKR 10,000.00 / mo")
        compose.onNodeWithText("Create Goal").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Emergency Fund", created?.get(0)); assertEquals(12000000L, created?.get(1)); assertEquals(0L, created?.get(3))
            source = source.copy(successVersion = 1)
        }
        compose.onNodeWithText("Home").performClick()
        compose.runOnIdle { assertEquals(1, home) }
    }
    @Test fun creationDialogShowsTheActualMonthlyPlanAndKeepsSaveAboveTheKeyboard() {
        content()
        compose.onNodeWithText("Create Savings Goal").performClick()
        compose.onNodeWithText("Goal Name").performTextReplacement("Laptop upgrade")
        compose.onNodeWithText("Target Amount (LKR)").performTextReplacement("490000.00")
        compose.onNodeWithText("Opening Saved Amount (LKR)").performScrollTo().performTextReplacement("11200.00")
        compose.onNodeWithText("Create Goal").assertIsDisplayed()
        hideKeyboard()
        compose.onNodeWithTag("goal_monthly_preview").performScrollTo().assertTextEquals("LKR 39,900.00 / mo")
        fits(compose.onNodeWithTag("goal_monthly_preview"))
        capture("savings-create-redesign.png")
        compose.onNodeWithText("Create Goal").performClick()
        compose.runOnIdle { assertEquals(listOf("Laptop upgrade", 49000000L, today.plusMonths(12).toString(), 1120000L), created) }
    }
    @Test fun smallDarkDialogScrollsAndRejectsInvalidAmountsWithoutLosingTheDraft() {
        content(small = true, dark = true)
        compose.onNodeWithTag("savings_sections").performScrollToNode(hasTestTag("savings_empty_stats"))
        val savedValue = compose.onNodeWithText("LKR 0.00").getUnclippedBoundsInRoot()
        val progressValue = compose.onNodeWithText("0%").getUnclippedBoundsInRoot()
        assertEquals("Values stay aligned when labels wrap", savedValue.top.value, progressValue.top.value, 1f)
        capture("savings-empty-stats-small.png")
        compose.onNodeWithText("Create Savings Goal").performScrollTo().performClick()
        compose.onNodeWithText("Goal Name").performTextReplacement("My emergency fund")
        compose.onNodeWithText("Target Amount (LKR)").performScrollTo().performTextReplacement("0.001")
        hideKeyboard()
        compose.onNodeWithText("Create Goal").assertIsDisplayed().performClick()
        compose.runOnIdle { assertNull(created) }
        compose.onNodeWithText("Target amount must be greater than zero").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Target Amount (LKR)").performTextReplacement("120000.00")
        hideKeyboard()
        capture("savings-create-small-dark.png")
        compose.onNodeWithText("Create Goal").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("My emergency fund", created?.get(0)); assertEquals(12000000L, created?.get(1)) }
    }
    @Test fun activeGoalKeepsLargeAmountsReadableAndContributionPreservesDateAndNote() {
        source = SavingsGoalUiState(isLoading = false, activeGoal = SavingsGoal("goal", name = "Home deposit", targetAmountMinor = 99999999999, targetDate = today.plusMonths(12).toString()),
            currentSavedMinor = 1120000, remainingAmountMinor = 99998879999, requiredMonthlySavingMinor = 8333239999, progressPercent = 1.5)
        content(small = true, dark = true)
        compose.onNodeWithTag("goal_saved_amount").assertTextEquals("LKR 11,200.00")
        fits(compose.onNodeWithTag("goal_saved_amount"))
        compose.onNodeWithText("Add Contribution").performScrollTo().performClick()
        compose.onNodeWithText("Amount (LKR)").performTextInput("1500.75")
        compose.onNodeWithText("Note (Optional)").performScrollTo().performTextInput("Weekly savings")
        hideKeyboard()
        compose.onNodeWithText("Confirm Deposit").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Triple(150075L, "Weekly savings", today.toString()), contribution); source = source.copy(successVersion = 1) }
        compose.onNodeWithTag("savings_sections").performScrollToNode(hasTestTag("goal_monthly_required"))
        compose.onNodeWithTag("goal_monthly_required").assertTextEquals("LKR 83,332,399.99")
        fits(compose.onNodeWithTag("goal_monthly_required"))
        capture("savings-active-small-dark.png")
    }
    @Test fun editingTheActiveGoalKeepsItsOriginalIdentity() {
        source = SavingsGoalUiState(isLoading = false, activeGoal = SavingsGoal("goal", "user", "Laptop", 49000000, today.plusMonths(12).toString(), 1120000))
        content()
        compose.onNodeWithContentDescription("Edit Goal").performClick()
        compose.onNodeWithText("Goal Name").performTextReplacement("MacBook Pro M4")
        hideKeyboard()
        compose.onNodeWithText("Save Changes").performClick()
        compose.runOnIdle { assertEquals("goal", updated?.id); assertEquals("user", updated?.userId); assertEquals("MacBook Pro M4", updated?.name) }
    }
    private fun fits(node: SemanticsNodeInteraction) {
        val result = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        assertTrue(result.isNotEmpty()); assertTrue(result.none { it.hasVisualOverflow })
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}

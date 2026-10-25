package com.pfms.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.CalculateSavingsSimulationUseCase
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import com.pfms.app.ui.navigation.Routes
import com.pfms.app.ui.navigation.FinanceFeatureBackHandler
import com.pfms.app.ui.navigation.openFinanceFeature
import com.pfms.app.ui.navigation.returnToSavingsGoalFromSimulator
import com.pfms.app.ui.screen.savings.*
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class SavingsSimulatorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.of(2026, 10, 25)
    private val goal = SavingsGoal("goal", "user", "Laptop", 10000000, "2027-10-25", 2000000)
    private val calculation = CalculateSavingsSimulationUseCase(CalculateSavedAmountUseCase())
    private var source by mutableStateOf(scenario(100000))
    private var retry = 0
    private var openGoals = 0
    private fun scenario(reduction: Long = 0, planned: Long = 0, selected: SavingsGoal = goal) = SavingsSimulatorUiState(
        isLoading = false, goal = selected, monthlyReductionMinor = reduction, plannedContributionMinor = planned,
        result = calculation.simulate(selected, emptyList(), reduction, planned, today))
    private fun content(small: Boolean = false, navigation: Boolean = false, noGoal: Boolean = false) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (small) 1.4f else 1f)) {
                PFMSTheme(darkTheme = small) {
                    Box(if (small) Modifier.requiredSize(320.dp, 640.dp) else Modifier.fillMaxSize()) {
                        if (navigation) NavigationContent(noGoal)
                        else SavingsSimulatorContent(source, {}, { source = scenario(it, source.plannedContributionMinor) },
                            { source = scenario(source.monthlyReductionMinor, it) }, { openGoals++ }, { retry++ })
                    }
                }
            }
        }
    }
    @Composable private fun NavigationContent(noGoal: Boolean) {
        val nav = rememberNavController()
        NavHost(nav, startDestination = if (noGoal) Routes.SavingsSimulator else Routes.SavingsGoal) {
            composable(Routes.SavingsGoal) { entry ->
                SavingsGoalContent(uiState = SavingsGoalUiState(isLoading = false, activeGoal = if (noGoal) null else goal,
                    currentSavedMinor = goal.openingSavedAmountMinor), onBack = {}, onCreateGoal = { _, _, _, _ -> },
                    onUpdateGoal = {}, onArchiveGoal = {}, onDeleteGoal = {}, onAddContribution = { _, _, _ -> },
                    onAddWithdrawal = { _, _, _ -> }, onUpdateTransaction = {}, onDeleteTransaction = {}, onClearError = {},
                    onOpenSavingsSimulator = { nav.openFinanceFeature(entry, Routes.SavingsSimulator) })
            }
            composable(Routes.SavingsSimulator) { entry ->
                val back = { nav.returnToSavingsGoalFromSimulator(entry) }
                FinanceFeatureBackHandler(nav, entry, back)
                SavingsSimulatorContent(if (noGoal) SavingsSimulatorUiState(isLoading = false) else source,
                    back, {}, {}, back)
            }
        }
    }
    private fun fits(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty()); assertTrue(layouts.none { it.hasVisualOverflow })
    }
    @Test fun validScenariosUpdateProjectedAmountsAndDistinguishTheActualBalance() {
        content()
        compose.onNodeWithTag("simulator_Currently saved").performScrollTo().assertTextEquals("LKR 20,000.00")
        compose.onNodeWithTag("simulator_Potential extra by deadline").performScrollTo().assertTextEquals("LKR 12,000.00")
        compose.onNodeWithText("Monthly spending reduction (LKR)").performScrollTo().performTextReplacement("2000.50")
        compose.onNodeWithTag("simulator_Potential extra by deadline").performScrollTo().assertTextEquals("LKR 24,006.00")
        compose.onNodeWithTag("simulator_Currently saved").performScrollTo().assertTextEquals("LKR 20,000.00")
        compose.onNodeWithText("Actual recorded balance").assertExists()
        compose.onNodeWithText("No contributions or financial records are created by this simulation.").performScrollTo().assertIsDisplayed()
    }
    @Test fun invalidInputHidesTheForecastAndCorrectionOrBlankCanRecalculate() {
        content()
        compose.onNodeWithText("Monthly spending reduction (LKR)").performScrollTo().performTextReplacement("-1")
        compose.onNodeWithText("Enter zero or a positive amount with up to 2 decimal places.").assertExists()
        compose.onNodeWithText("Projected goal progress").assertDoesNotExist()
        compose.onNodeWithText("Monthly spending reduction (LKR)").performTextReplacement("")
        compose.onNodeWithTag("simulator_Potential extra by deadline").performScrollTo().assertTextEquals("LKR 0.00")
        compose.onNodeWithTag("simulator_Estimated completion").assertDoesNotExist()
        compose.onNodeWithText("A completion date is not available for this scenario.").performScrollTo().assertIsDisplayed()
    }
    @Test fun loadingErrorsAndNoGoalHaveDistinctActions() {
        source = SavingsSimulatorUiState(); content()
        compose.onNodeWithText("Loading your recorded savings…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start with a savings goal").assertDoesNotExist()
        compose.runOnIdle { source = SavingsSimulatorUiState(isLoading = false, error = "Please check your internet connection and try again.", canRetry = true) }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retry); source = SavingsSimulatorUiState(isLoading = false) }
        compose.onNodeWithText("Start with a savings goal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Savings Goals").performClick()
        compose.runOnIdle { assertEquals(1, openGoals) }
    }
    @Test fun completedAndOverdueScenariosExplainDeadlineProjectionAndMeaningfulDates() {
        source = scenario(100000, selected = goal.copy(openingSavedAmountMinor = goal.targetAmountMinor))
        content()
        compose.onNodeWithText("Goal already completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("simulator_Potential extra by deadline").performScrollTo().assertTextEquals("LKR 0.00")
        compose.onNodeWithTag("simulator_Estimated completion").assertDoesNotExist()
        compose.runOnIdle { source = scenario(100000, selected = goal.copy(targetDate = "2026-10-01")) }
        compose.onNodeWithText("Goal deadline has passed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("simulator_Potential extra by deadline").performScrollTo().assertTextEquals("LKR 0.00")
        compose.onNodeWithTag("simulator_Estimated completion").performScrollTo().assertIsDisplayed()
    }
    @Test fun narrowDarkLayoutKeepsLargeForecastAmountsReadable() {
        source = scenario(999999999, 250075)
        content(small = true)
        fits("simulator_Potential extra by deadline"); fits("simulator_Projected balance"); fits("simulator_Currently saved")
    }
    @Test fun goalLinkOpensSimulatorBackReturnsToGoalAndReopeningWorks() {
        content(navigation = true)
        repeat(2) {
            compose.onNodeWithTag("savings_sections").performScrollToNode(hasText("Explore What-if"))
            compose.onNodeWithText("Explore What-if").performClick()
            compose.onNodeWithText("What-if Savings").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Savings Goal").assertIsDisplayed()
        }
    }
    @Test fun missingGoalActionReplacesSimulatorWithSavingsGoals() {
        content(navigation = true, noGoal = true)
        compose.onNodeWithText("Open Savings Goals").performScrollTo().performClick()
        compose.onNodeWithText("Savings Goal").assertIsDisplayed()
        compose.onNodeWithText("What-if Savings").assertDoesNotExist()
        compose.onNodeWithText("Create Savings Goal").performScrollTo().assertIsDisplayed()
    }
}

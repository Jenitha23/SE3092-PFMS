package com.pfms.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.pfms.app.domain.model.*
import com.pfms.app.ui.navigation.*
import com.pfms.app.ui.screen.analytics.*
import com.pfms.app.ui.screen.savings.*
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

/** Real NavHost + feature content, using the production navigation actions and Android Back handler. */
class FinanceFeatureNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private var openPatterns: () -> Unit = {}
    private var closePatterns: () -> Unit = {}
    private var openSimulator: () -> Unit = {}
    private var closeSimulator: () -> Unit = {}
    private var unavailable by mutableStateOf(false)
    private val goal = SavingsGoal("goal", "user", "Laptop", 10000000, "2027-10-25", 2000000)

    private fun host(start: String = Routes.Dashboard) {
        compose.setContent { PFMSTheme {
            nav = rememberNavController()
            NavHost(nav, startDestination = start) {
                composable(Routes.Dashboard) {
                    Column {
                        Text("Dashboard")
                        Button(onClick = { nav.navigate(Routes.SpendingAnalytics) }) { Text("Open Spending Analytics") }
                        Button(onClick = { nav.navigate(Routes.SavingsGoal) }) { Text("Open Goals") }
                    }
                }
                composable(Routes.SpendingAnalytics) { entry ->
                    val open = { nav.openFinanceFeature(entry, Routes.SpendingPatterns) }
                    SideEffect { openPatterns = open }
                    SpendingAnalyticsContent(SpendingAnalyticsUiState(isLoading = false),
                        { nav.popBackStack() }, {}, {}, {}, {}, {}, {}, onOpenSpendingPatterns = open)
                }
                composable(Routes.SpendingPatterns) { entry ->
                    val back = { nav.closeSpendingPatterns(entry) }
                    val open = { nav.openFinanceFeature(entry, Routes.SavingsSimulator) }
                    SideEffect { closePatterns = back; openSimulator = open }
                    FinanceFeatureBackHandler(nav, entry, back)
                    SpendingPatternsContent(SpendingPatternsUiState(date = LocalDate.of(2026, 10, 25), isLoading = false,
                        result = if (unavailable) null else SpendingPatternResult(SpendingPatternStatus.INSUFFICIENT_HISTORY, emptyList(), 0),
                        error = if (unavailable) "Please check your internet connection and try again." else null),
                        emptySet(), { _, _ -> }, back, open)
                }
                composable(Routes.SavingsGoal) { entry ->
                    val open = { nav.openFinanceFeature(entry, Routes.SavingsSimulator) }
                    SideEffect { openSimulator = open }
                    SavingsGoalContent(SavingsGoalUiState(isLoading = false, activeGoal = if (unavailable) null else goal,
                        currentSavedMinor = goal.openingSavedAmountMinor), {}, { _, _, _, _ -> }, {}, {}, {},
                        { _, _, _ -> }, { _, _, _ -> }, {}, {}, {}, onOpenSavingsSimulator = open)
                }
                composable(Routes.SavingsSimulator) { entry ->
                    val back = { nav.returnToSavingsGoalFromSimulator(entry) }
                    SideEffect { closeSimulator = back }
                    FinanceFeatureBackHandler(nav, entry, back)
                    SavingsSimulatorContent(SavingsSimulatorUiState(isLoading = false,
                        error = if (unavailable) "Please check your internet connection and try again." else null),
                        back, {}, {}, back)
                }
            }
        } }
    }
    private fun route(expected: String) {
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(expected, nav.currentDestination?.route) }
    }
    private fun systemBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun openAnalytics() { compose.onNodeWithText("Open Spending Analytics").performClick() }

    @Test fun analyticsLinkAndPatternBackCanBeRepeatedWithoutDuplicateDestinations() {
        host(); openAnalytics()
        repeat(3) {
            compose.onNodeWithText("Smart spending patterns").performScrollTo().performClick()
            route(Routes.SpendingPatterns)
            if (it % 2 == 0) compose.onNodeWithContentDescription("Back").performClick() else systemBack()
            route(Routes.SpendingAnalytics)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        route(Routes.Dashboard)
    }

    @Test fun rapidForwardAndStaleBackCallbacksChangeTheStackOnlyOnce() {
        host(); openAnalytics()
        compose.runOnIdle { val callback = openPatterns; repeat(3) { callback() } }
        route(Routes.SpendingPatterns)
        compose.runOnIdle { val callback = closePatterns; repeat(3) { callback() } }
        route(Routes.SpendingAnalytics)
        compose.onNodeWithContentDescription("Back").performClick()
        route(Routes.Dashboard)
    }

    @Test fun goalLinkAndSimulatorToolbarOrAndroidBackReuseTheOriginalGoalEntry() {
        host(); compose.onNodeWithText("Open Goals").performClick()
        var original = ""
        compose.runOnIdle { original = nav.currentBackStackEntry!!.id }
        repeat(2) { index ->
            compose.onNodeWithTag("savings_sections").performScrollToNode(hasText("Explore What-if"))
            compose.onNodeWithText("Explore What-if").performClick()
            route(Routes.SavingsSimulator)
            if (index == 0) compose.onNodeWithContentDescription("Back").performClick() else systemBack()
            route(Routes.SavingsGoal)
            compose.runOnIdle { assertEquals(original, nav.currentBackStackEntry!!.id) }
        }
        compose.runOnIdle { nav.popBackStack() }
        route(Routes.Dashboard)
    }

    @Test fun simulatorOpenedFromPatternsReturnsToGoalForBothBackActions() {
        host(); openAnalytics()
        compose.onNodeWithText("Smart spending patterns").performScrollTo().performClick()
        compose.onNodeWithText("Explore savings impact").performScrollTo().performClick()
        route(Routes.SavingsSimulator)
        compose.onNodeWithContentDescription("Back").performClick()
        route(Routes.SavingsGoal)
        compose.runOnIdle { nav.popBackStack() }
        route(Routes.SpendingPatterns)
        compose.onNodeWithText("Explore savings impact").performScrollTo().performClick()
        route(Routes.SavingsSimulator)
        systemBack()
        route(Routes.SavingsGoal)
    }

    @Test fun simulatorReturnsToAnExistingGoalBelowOtherFeatureScreens() {
        host(); compose.onNodeWithText("Open Goals").performClick()
        var original = ""
        compose.runOnIdle { original = nav.currentBackStackEntry!!.id; nav.navigate(Routes.SpendingAnalytics) }
        compose.onNodeWithText("Smart spending patterns").performScrollTo().performClick()
        compose.onNodeWithText("Explore savings impact").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        route(Routes.SavingsGoal)
        compose.runOnIdle { assertEquals(original, nav.currentBackStackEntry!!.id); nav.popBackStack() }
        route(Routes.Dashboard)
    }

    @Test fun rapidSimulatorOpenAndCloseCannotDuplicateGoalsOrPopPastTheCaller() {
        host(); compose.onNodeWithText("Open Goals").performClick()
        compose.runOnIdle { val callback = openSimulator; repeat(3) { callback() } }
        route(Routes.SavingsSimulator)
        compose.runOnIdle { val callback = closeSimulator; repeat(3) { callback() } }
        route(Routes.SavingsGoal)
        compose.runOnIdle { nav.popBackStack() }
        route(Routes.Dashboard)
    }

    @Test fun missingDataStillAllowsSimulatorToReturnToGoalsWithoutALoop() {
        unavailable = true; host(start = Routes.SavingsSimulator)
        compose.onNodeWithText("Your goal is unavailable").assertExists()
        compose.onNodeWithText("Open Savings Goals").performClick()
        route(Routes.SavingsGoal)
        compose.onNodeWithText("No Active Savings Goal").assertExists()
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

    @Test fun unavailablePatternsCanNavigateBackAndRootPatternsHaveASafeFallback() {
        unavailable = true; host(start = Routes.SpendingPatterns)
        compose.onNodeWithText("Please check your internet connection and try again.").assertExists()
        systemBack()
        route(Routes.SpendingAnalytics)
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }
}

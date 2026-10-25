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
import com.pfms.app.domain.model.*
import com.pfms.app.ui.screen.analytics.SpendingPatternsContent
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.SpendingPatternsUiState
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.time.YearMonth

class SpendingPatternsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 15)
    private var source by mutableStateOf(SpendingPatternsUiState(date = date))
    private var retries = 0
    private fun content(small: Boolean = false) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (small) 1.4f else 1f)) {
                PFMSTheme(darkTheme = small) {
                    Box(if (small) Modifier.requiredSize(320.dp, 640.dp) else Modifier.fillMaxSize()) {
                        SpendingPatternsContent(source, emptySet(), { _, _ -> }, {}, {}, { retries++ })
                    }
                }
            }
        }
    }
    private fun insight(id: String, level: SpendingIncreaseLevel, current: Long = 1500000) =
        SpendingPatternInsight(id, "${id.replaceFirstChar { it.uppercase() }} & household spending", YearMonth.of(2026, 10),
            current, 1000000, current - 1000000, if (level == SpendingIncreaseLevel.HIGH) 50 else 40, level, 2, true)
    private fun ready(vararg rows: SpendingPatternInsight) = SpendingPatternsUiState(date = date, isLoading = false,
        result = SpendingPatternResult(SpendingPatternStatus.READY, rows.toList(), 2))
    private fun fits(tag: String) {
        val node = compose.onNodeWithTag(tag).performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertTrue("Full currency amount must fit", layouts.none { it.hasVisualOverflow })
    }

    @Test fun multiplePatternsShowReturnedAmountsSeverityAndPartialMonthContext() {
        source = ready(insight("food", SpendingIncreaseLevel.HIGH), insight("travel", SpendingIncreaseLevel.NOTICEABLE, 1400000))
        content()
        compose.onNodeWithText("High increase").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pattern_food_current").performScrollTo().assertTextEquals("LKR 15,000.00")
        compose.onNodeWithTag("pattern_food_baseline").performScrollTo().assertTextEquals("LKR 10,000.00")
        compose.onNodeWithTag("pattern_food_difference").performScrollTo().assertTextEquals("LKR 5,000.00")
        compose.onNodeWithTag("pattern_food_percent").performScrollTo().assertTextEquals("50% above baseline")
        compose.onAllNodesWithText("Month to date:", substring = true)[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Noticeable increase").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pattern_travel_percent").performScrollTo().assertTextEquals("40% above baseline")
    }

    @Test fun loadingErrorRetryAndEmptyResultsNeverShowStalePatterns() {
        content()
        compose.onNodeWithTag("patterns_loading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No significant increases detected").assertDoesNotExist()
        compose.runOnIdle { source = source.copy(isLoading = false, error = "Please check your internet connection and try again.", canRetry = true) }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries); source = ready(insight("food", SpendingIncreaseLevel.HIGH)) }
        compose.onNodeWithTag("pattern_food").assertExists()
        compose.runOnIdle { source = ready() }
        compose.onNodeWithTag("pattern_food").assertDoesNotExist()
        compose.onNodeWithText("No significant increases detected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Categories without a positive historical baseline", substring = true).assertExists()
    }

    @Test fun insufficientHistoryAndUnavailableResultsDoNotClaimASteadyTrend() {
        source = SpendingPatternsUiState(date = date, isLoading = false,
            result = SpendingPatternResult(SpendingPatternStatus.INSUFFICIENT_HISTORY, emptyList(), 1))
        content()
        compose.onNodeWithText("1 of 2 required months confirmed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No significant increases detected").assertDoesNotExist()
        compose.runOnIdle { source = SpendingPatternsUiState(date = date, isLoading = false) }
        compose.onNodeWithText("No comparison available").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun narrowDarkLayoutWithLargeTextKeepsCurrencyValuesReadable() {
        source = ready(insight("food", SpendingIncreaseLevel.HIGH, 99999999999))
        content(small = true)
        fits("pattern_food_current"); fits("pattern_food_baseline"); fits("pattern_food_difference")
        compose.onNodeWithTag("pattern_food_current").assertTextEquals("LKR 999,999,999.99")
        compose.onNodeWithText("High increase").performScrollTo().assertIsDisplayed()
    }
}

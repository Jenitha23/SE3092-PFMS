package com.pfms.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.domain.model.Income
import com.pfms.app.ui.screen.income.*
import com.pfms.app.ui.theme.PFMSTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** Isolated drafts and callbacks only: no repository writes or network rate requests. */
class IncomeFormUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var hideKeyboard: () -> Unit = {}
    private var imeBottom = 0

    @Before fun matchAppWindow() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
    }

    @Test fun compactLocalIncomeHasAlignedFieldsExactIncrementsAndPersistentSave() {
        var result: IncomeFormInput? = null
        compose.setContent {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            val keyboardInset = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = keyboardInset }
            SideEffect { hideKeyboard = { focus.clearFocus(); keyboard?.hide() } }
            PFMSTheme(darkTheme = false) { IncomeFormScreen({}, { result = it }) }
        }
        compose.onNodeWithContentDescription("Income amount").performTextReplacement("250000.00")
        compose.waitUntil(5_000) { imeBottom > 0 }
        compose.onNodeWithText("Save Income").assertIsDisplayed()
        val visible = compose.onNodeWithTag("income_form_actions").getBoundsInRoot()
        val full = compose.onNodeWithTag("income_form_actions").getUnclippedBoundsInRoot()
        assertEquals("The save bar must fit above the keyboard", full.bottom.value, visible.bottom.value, 1f)
        hideKeyboard()
        compose.onNodeWithText("+5k").performClick()
        compose.onNodeWithTag("income_form_full_amount").assertTextEquals("LKR 255,000.00")
        assertFits(compose.onNodeWithTag("income_form_full_amount"))
        assertTrue(compose.onNodeWithTag("income_form_amount_card").getUnclippedBoundsInRoot().let { it.bottom - it.top } < 300.dp)
        compose.onNodeWithText("Save Income").assertIsDisplayed()
        compose.onNodeWithText("Monthly").assertDoesNotExist()
        capture("income-add-local.png")
        compose.onNodeWithText("Notes", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Income note").performScrollTo().performTextInput("October payroll")
        hideKeyboard()
        compose.onNodeWithText("Save Income").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("255000.00", result?.amount)
            assertEquals("Salary", result?.source)
            assertEquals("October payroll", result?.note)
        }
    }

    @Test fun narrowLargeTextKeepsFullUsdValuesAndSourceDetailsAccessible() {
        val original = Income("crypto", "a", 10_000_00, "USD", source = "Crypto", cryptoType = "Loss", cryptoAsset = "BTC",
            receivedDate = "2026-10-16", exchangeRate = "330.980189", exchangeRateDate = "2026-10-16")
        var result: IncomeFormInput? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.3f)) {
                PFMSTheme(darkTheme = false) { Box(Modifier.requiredSize(320.dp, 640.dp)) {
                    IncomeFormScreen({}, { result = it }, initialIncome = original,
                        exchangeRate = BigDecimal("330.980189"), exchangeRateDate = original.receivedDate)
                } }
            }
        }
        compose.onNodeWithTag("income_form_full_amount").performScrollTo().assertTextEquals("USD 10,000.00")
        compose.onNode(hasText("Crypto") and isSelected()).assertIsDisplayed()
        compose.onNodeWithTag("income_form_conversion").performScrollTo().assertTextEquals("LKR 3,309,801.89")
        assertFits(compose.onNodeWithTag("income_form_conversion"))
        compose.onNodeWithText("Save Changes").assertIsDisplayed()
        capture("income-add-small-usd.png")
        compose.onNodeWithText("CRYPTO RESULT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Loss").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Crypto asset").performScrollTo().assertTextEquals("BTC")
        compose.onNodeWithText("Save Changes").performClick()
        compose.runOnIdle {
            assertEquals("Loss", result?.cryptoType)
            assertEquals("BTC", result?.cryptoAsset)
            assertEquals("10000.00", result?.amount)
        }
    }

    @Test fun darkModeFreelanceUsesActualStatusAndHelp() {
        val original = Income("freelance", "a", 450_00, source = "Freelance", receivedDate = "2026-10-08",
            projectName = "App redesign", paymentStatus = "Expected")
        var result: IncomeFormInput? = null
        compose.setContent { PFMSTheme(darkTheme = true) { IncomeFormScreen({}, { result = it }, initialIncome = original) } }
        compose.onNodeWithText("Help").performClick()
        compose.onNodeWithText("Recording income").assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Expected").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Received").performScrollTo().performClick()
        compose.onNodeWithText("Save Changes").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Received", result?.paymentStatus)
            assertEquals("App redesign", result?.projectName)
        }
        capture("income-add-dark.png")
    }

    private fun hideKeyboard() {
        compose.runOnIdle { hideKeyboard.invoke() }
        compose.waitUntil(5_000) { imeBottom == 0 }
        compose.waitForIdle()
    }

    private fun assertFits(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse("Full financial values must fit their card", layouts.any { it.hasVisualOverflow })
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

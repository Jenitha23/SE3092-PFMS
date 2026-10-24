package com.pfms.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.domain.model.*
import com.pfms.app.ui.screen.SettingsActions
import com.pfms.app.ui.screen.SettingsScreenContent
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated UI fixtures. No signed-in account, preference writes or cloud mutations. */
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val profile = SettingsProfileState("Alex Morgan", "alex.morgan@example.com", "AM", true,
        "Card", "LKR · Sri Lankan rupee", canChangePassword = true)
    private val reminders = ReminderSettingsUiState(UserReminderSettings.defaults("fixture"),
        NotificationAccess(true), isLoading = false)

    @Test fun profileAndSettingsUseRealActionCallbacks() {
        val dialogs = mutableListOf<SettingsDialog>()
        var biometric = false; var daily = false; var privacy = false; var logout = false; var back = false
        compose.setContent { PFMSTheme(darkTheme = false) {
            SettingsScreenContent(profile, biometricAvailable = true, reminders = reminders, version = "1.0",
                actions = SettingsActions(onDialog = { dialogs += it }, onBiometric = { biometric = it },
                    onDailyReminder = { daily = it }, onPrivacy = { privacy = true },
                    onLogout = { logout = true }, onBack = { back = true }))
        } }
        compose.onNodeWithText("Email verified", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit full name").performClick()
        capture("settings-profile.png")
        scrollTo("Default payment method").performClick()
        scrollTo("Theme").performClick()
        compose.onNodeWithTag("settings_sections").performScrollToNode(hasContentDescription("Biometric lock"))
        compose.onNodeWithContentDescription("Biometric lock").performClick()
        compose.onNodeWithTag("settings_sections").performScrollToNode(hasContentDescription("Daily ledger reminder"))
        compose.onNodeWithContentDescription("Daily ledger reminder").performClick()
        scrollTo("Reminder schedules").performClick()
        scrollTo("Change password").performClick()
        capture("settings-security.png")
        scrollTo("Privacy policy").performClick()
        scrollTo("Delete account").performClick()
        scrollTo("Log Out").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            assertEquals(listOf(SettingsDialog.NAME, SettingsDialog.PAYMENT, SettingsDialog.THEME,
                SettingsDialog.REMINDERS, SettingsDialog.PASSWORD, SettingsDialog.DELETE), dialogs)
            assertTrue(biometric && daily && privacy && logout && back)
        }
    }

    @Test fun largeTextOnSmallScreenKeepsFullEmailAndLogoutAccessible() {
        val longEmail = "alex.morgan.with.a.long.address@example.com"
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.3f)) {
                PFMSTheme(darkTheme = false) {
                    Box(Modifier.requiredSize(320.dp, 640.dp)) {
                        SettingsScreenContent(profile.copy(email = longEmail), reminders = reminders)
                    }
                }
            }
        }
        compose.onAllNodesWithText(longEmail).onFirst().assertIsDisplayed()
        scrollTo("Daily ledger reminder").assertIsDisplayed()
        compose.onNodeWithContentDescription("Daily ledger reminder").assertIsEnabled()
        scrollTo("Log Out").assertIsDisplayed()
    }

    @Test fun googleAccountInDarkThemeDoesNotOfferAPasswordChange() {
        compose.setContent { PFMSTheme(darkTheme = true) {
            SettingsScreenContent(profile.copy(verified = false, canChangePassword = false, googleOnly = true), reminders = reminders)
        } }
        compose.onNodeWithText("Email not verified").assertIsDisplayed()
        scrollTo("Google sign-in").assertIsDisplayed()
        compose.onNodeWithText("Change password").assertDoesNotExist()
        capture("settings-dark.png")
        scrollTo("Log Out").assertIsDisplayed()
    }

    private fun scrollTo(text: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings_sections").performScrollToNode(hasText(text))
        return compose.onNodeWithText(text).performScrollTo()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

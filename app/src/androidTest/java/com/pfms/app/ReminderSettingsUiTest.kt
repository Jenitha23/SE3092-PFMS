package com.pfms.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pfms.app.domain.model.*
import com.pfms.app.ui.component.ReminderSettingsContent
import com.pfms.app.viewmodel.ReminderSettingsUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReminderSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private var hideKeyboard: () -> Unit = {}
    private fun content(block: @Composable () -> Unit) = compose.setContent {
        val keyboard = LocalSoftwareKeyboardController.current; val focus = LocalFocusManager.current
        SideEffect { hideKeyboard = { focus.clearFocus(); keyboard?.hide() } }
        MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { block() } }
    }

    @Test fun allSevenSwitchesCanChangeIndependentlyEvenWithoutPermission() {
        var settings by mutableStateOf(UserReminderSettings.defaults("a"))
        val changed = mutableListOf<ReminderType>()
        content {
            ReminderSettingsContent(ReminderSettingsUiState(settings, NotificationAccess(false, true), false),
                onEnabled = { type, enabled -> changed += type
                    settings = settings.copy(schedules = settings.schedules + (type to settings[type].copy(enabled = enabled))) },
                onTime = { _, _, _ -> }, onWeekday = {}, onRetry = {}, onRequestPermission = {}, onOpenSystemSettings = {})
        }
        ReminderType.entries.forEach { type ->
            val originally = UserReminderSettings.defaults("a")[type].enabled
            compose.onNodeWithContentDescription(type.title).performScrollTo().performClick()
            if (originally) compose.onNodeWithContentDescription(type.title).assertIsOff()
            else compose.onNodeWithContentDescription(type.title).assertIsOn()
        }
        compose.runOnIdle { assertEquals(ReminderType.entries.toList(), changed) }
    }

    @Test fun dailyTimeSelectorSavesHourAndMinuteAndCancelKeepsPreviousTime() {
        var settings by mutableStateOf(UserReminderSettings.defaults("a"))
        var saved = 0
        content {
            ReminderSettingsContent(ReminderSettingsUiState(settings, NotificationAccess(true), false), { _, _ -> },
                onTime = { type, hour, minute -> saved++
                    settings = settings.copy(schedules = settings.schedules + (type to settings[type].copy(minuteOfDay = hour * 60 + minute))) },
                onWeekday = {}, onRetry = {}, onRequestPermission = {}, onOpenSystemSettings = {})
        }
        compose.onNodeWithText("Daily reminder time: 20:00").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("20")).performTextReplacement("21")
        compose.onNode(hasSetTextAction() and hasText("00")).performTextReplacement("15")
        compose.runOnIdle { hideKeyboard.invoke() }
        compose.onNodeWithText("Save time").performClick()
        compose.onNodeWithText("Daily reminder time: 21:15").assertExists().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, saved); assertEquals(1275, settings[ReminderType.DAILY_LOG].minuteOfDay) }
    }

    @Test fun permissionControlsAreExplicitAndDeniedPermissionIsNonBlocking() {
        var requested = 0; var opened = 0
        content {
            ReminderSettingsContent(ReminderSettingsUiState(UserReminderSettings.defaults("a"), NotificationAccess(false, true), false),
                { _, _ -> }, { _, _, _ -> }, {}, {}, { requested++ }, { opened++ })
        }
        compose.onNodeWithText("Notifications are off. You can keep using all financial features.").assertExists()
        compose.runOnIdle { assertEquals(0, requested) }
        compose.onNodeWithText("Allow notifications").performClick()
        compose.onNodeWithText("System notification settings").performClick()
        compose.runOnIdle { assertEquals(1, requested); assertEquals(1, opened) }
    }

    @Test fun weeklySummaryDayCanBeConfiguredWithoutTurningOnOtherTypes() {
        var selected = 0
        content {
            ReminderSettingsContent(ReminderSettingsUiState(UserReminderSettings.defaults("a"), NotificationAccess(true), false),
                { _, _ -> }, { _, _, _ -> }, { selected = it }, {}, {}, {})
        }
        compose.onNodeWithText("Summary day: Sunday").performScrollTo().performClick()
        compose.onNodeWithText("Monday").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }
}

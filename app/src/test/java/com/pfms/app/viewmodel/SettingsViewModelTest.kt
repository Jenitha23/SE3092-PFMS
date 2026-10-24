package com.pfms.app.viewmodel

import com.pfms.app.data.AppInfoProvider
import com.pfms.app.data.auth.*
import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.UserRepository
import com.pfms.app.domain.usecase.DeleteAccountUseCase
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val auth: AuthRepository = mock()
    private val users: UserRepository = mock()
    private val biometric: BiometricAuthManager = mock()
    private val preferences: BiometricPreferences = mock()

    private fun create(): SettingsViewModel {
        whenever(auth.currentUser).thenReturn(flowOf(AuthUser("u", "Alex Morgan", "alex@example.com", true, listOf("password"))))
        whenever(users.observeProfile()).thenReturn(flowOf(UserProfile("u", "Alex Morgan", "alex@example.com")))
        whenever(preferences.biometricEnabled).thenReturn(flowOf(false))
        whenever(preferences.biometricPromptShown).thenReturn(flowOf(true))
        val info: AppInfoProvider = mock()
        whenever(info.versionName).thenReturn("1.0")
        return main.track(SettingsViewModel(auth, users, mock<DeleteAccountUseCase>(), mock<GoogleSignInManager>(), biometric, preferences, info))
    }

    private fun TestScope.observe(vm: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.profileState.collect() }
        runCurrent()
    }

    @Test fun nameEditorValidatesAndSavesTrimmedName() = runTest {
        val vm = create(); observe(vm)
        vm.openDialog(SettingsDialog.NAME)
        assertEquals("Alex Morgan", vm.editor.value.name)
        assertFalse(vm.editor.value.canSaveName)
        vm.edit(SettingsField.NAME, "a".repeat(51)); vm.saveName()
        assertNotNull(vm.editor.value.error)
        verify(auth, never()).updateDisplayName(any())
        whenever(auth.updateDisplayName(any())).thenReturn(Result.success(Unit))
        vm.edit(SettingsField.NAME, "  Alex Silva  "); vm.saveName(); runCurrent()
        verify(auth).updateDisplayName("Alex Silva")
        assertNull(vm.editor.value.dialog)
    }

    @Test fun passwordMismatchAndLengthAreValidatedBeforeReauthentication() = runTest {
        val vm = create(); observe(vm); vm.openDialog(SettingsDialog.PASSWORD)
        vm.edit(SettingsField.CURRENT_PASSWORD, "oldpassword")
        vm.edit(SettingsField.NEW_PASSWORD, "short")
        vm.edit(SettingsField.CONFIRM_PASSWORD, "different"); vm.savePassword()
        assertTrue(vm.editor.value.error.orEmpty().contains("do not match"))
        vm.edit(SettingsField.CONFIRM_PASSWORD, "short"); vm.savePassword()
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("at least 8"))
        verify(auth, never()).reauthenticate(any())
    }

    @Test fun failedPasswordSavePreservesInputsAndRetryClearsSecretsAfterSuccess() = runTest {
        val vm = create(); observe(vm)
        whenever(auth.reauthenticate(any())).thenReturn(Result.success(Unit))
        whenever(auth.updatePassword(any())).thenReturn(Result.failure(IOException("private endpoint")))
        vm.openDialog(SettingsDialog.PASSWORD)
        vm.edit(SettingsField.CURRENT_PASSWORD, "oldpassword")
        vm.edit(SettingsField.NEW_PASSWORD, "NewPassword!123")
        vm.edit(SettingsField.CONFIRM_PASSWORD, "NewPassword!123")
        vm.savePassword(); runCurrent()
        assertEquals(SettingsDialog.PASSWORD, vm.editor.value.dialog)
        assertEquals("NewPassword!123", vm.editor.value.newPassword)
        assertFalse(vm.uiState.value.errorMessage.orEmpty().contains("private endpoint"))
        whenever(auth.updatePassword(any())).thenReturn(Result.success(Unit))
        vm.retry(); runCurrent()
        assertEquals(SettingsEditorState(), vm.editor.value)
        assertEquals("Password changed.", vm.uiState.value.message)
    }

    @Test fun dismissingEditorClearsPasswordsAndDoesNotSubmitDeletion() = runTest {
        val vm = create(); observe(vm); vm.openDialog(SettingsDialog.DELETE)
        vm.edit(SettingsField.DELETE_PASSWORD, "secret"); vm.dismissDialog()
        assertEquals(SettingsEditorState(), vm.editor.value)
        verify(auth, never()).deleteAccount()
    }

    @Test fun unavailableBiometricsCannotBeEnabledButCanBeTurnedOff() = runTest {
        val vm = create()
        vm.setBiometricEnabled(true); runCurrent()
        assertNotNull(vm.uiState.value.errorMessage)
        verify(preferences, never()).setBiometricEnabled(true)
        vm.setBiometricEnabled(false); runCurrent()
        verify(preferences).setBiometricEnabled(false)
    }

    @Test fun profileUsesRealVerificationAndProviderCapabilities() {
        val google = AuthUser("u", "Alex Morgan", "alex@example.com", false, listOf("google.com"))
        val profile = SettingsPresentation.profile(google, null)
        assertEquals("AM", profile.initials)
        assertFalse(profile.verified)
        assertFalse(profile.canChangePassword)
        assertTrue(profile.googleOnly)
        assertTrue(SettingsPresentation.profile(google.copy(providerIds = listOf("google.com", "password")), null).canChangePassword)
    }

    @Test fun dailyReminderShowsSavedScheduleAndPermissionState() {
        val original = UserReminderSettings.defaults("u")
        val settings = original.copy(schedules = original.schedules +
            (ReminderType.DAILY_LOG to original[ReminderType.DAILY_LOG].copy(enabled = true, minuteOfDay = 21 * 60 + 15)))
        val state = ReminderSettingsUiState(settings, NotificationAccess(false, true), isLoading = false)
        assertTrue(state.dailyEnabled)
        assertEquals("Every day at 9:15 PM · Asia/Colombo · notifications off", state.dailySummary)
        assertFalse(state.copy(isSaving = true).canChangeDaily)
        assertFalse(state.copy(access = NotificationAccess(true)).dailySummary.contains("notifications off"))
    }
}

package com.pfms.app.viewmodel

import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.ReminderSettingsRepository
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Repository : ReminderSettingsRepository {
        val settings = MutableStateFlow<UserReminderSettings?>(UserReminderSettings.defaults("a"))
        var access = NotificationAccess(false, true)
        var fail = false
        var loadFailure = false
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeSettings() = flow {
            if (loadFailure) throw IOException("Internal URL")
            emitAll(settings)
        }
        override fun notificationAccess() = access
        override suspend fun update(userId: String, type: ReminderType, enabled: Boolean?, minuteOfDay: Int?, dayOfWeek: Int?): Result<Unit> {
            calls++; gate?.await()
            if (fail) return Result.failure(IOException("Internal URL"))
            val value = settings.value!!
            settings.value = value.copy(schedules = value.schedules + (type to value[type].copy(
                enabled = enabled ?: value[type].enabled, minuteOfDay = minuteOfDay ?: value[type].minuteOfDay,
                dayOfWeek = dayOfWeek ?: value[type].dayOfWeek)))
            return Result.success(Unit)
        }
        override suspend fun reconcile() = Result.success(Unit)
    }
    @Test fun permissionDenialDoesNotPreventSavingIndependentPreferences() = runTest {
        val repo = Repository(); val vm = main.track(ReminderSettingsViewModel(repo)); runCurrent()
        vm.setEnabled(ReminderType.DAILY_LOG, true); runCurrent()
        vm.setTime(ReminderType.DAILY_LOG, 21, 15); runCurrent()
        assertFalse(vm.uiState.value.access.allowed)
        assertTrue(vm.uiState.value.settings!![ReminderType.DAILY_LOG].enabled)
        assertEquals(1275, vm.uiState.value.settings!![ReminderType.DAILY_LOG].minuteOfDay)
        assertTrue(vm.uiState.value.settings!![ReminderType.DUE_RECURRING].enabled)
        assertFalse(vm.uiState.value.settings!![ReminderType.SALARY_DAY].enabled)
    }
    @Test fun failedPreferenceWriteHasSafeMessageAndRetryKeepsTheChange() = runTest {
        val repo = Repository().apply { fail = true }; val vm = main.track(ReminderSettingsViewModel(repo)); runCurrent()
        vm.setEnabled(ReminderType.DAILY_LOG, true); runCurrent()
        assertFalse(vm.uiState.value.settings!![ReminderType.DAILY_LOG].enabled)
        assertFalse(vm.uiState.value.errorMessage.orEmpty().contains("Internal"))
        repo.fail = false; vm.retry(); runCurrent()
        assertTrue(vm.uiState.value.settings!![ReminderType.DAILY_LOG].enabled)
        assertNull(vm.uiState.value.errorMessage)
    }
    @Test fun duplicateSavesAreGuardedAndMalformedTimesAreIgnored() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }; val vm = main.track(ReminderSettingsViewModel(repo)); runCurrent()
        vm.setTime(ReminderType.DAILY_LOG, 25, 0)
        vm.setEnabled(ReminderType.DAILY_LOG, true); vm.setEnabled(ReminderType.DAILY_LOG, true); runCurrent()
        assertEquals(1, repo.calls); assertTrue(vm.uiState.value.isSaving)
        repo.gate!!.complete(Unit); runCurrent(); assertFalse(vm.uiState.value.isSaving)
    }
    @Test fun returningFromSystemSettingsRefreshesPermissionWithoutChangingSwitches() = runTest {
        val repo = Repository(); val vm = main.track(ReminderSettingsViewModel(repo)); runCurrent()
        val original = vm.uiState.value.settings
        repo.access = NotificationAccess(true, false); vm.refreshPermission()
        assertTrue(vm.uiState.value.access.allowed); assertEquals(original, vm.uiState.value.settings)
    }
    @Test fun loadingFailureCanBeRetriedAndAccountChangeClearsOldErrors() = runTest {
        val repo = Repository().apply { loadFailure = true }; val vm = main.track(ReminderSettingsViewModel(repo)); runCurrent()
        assertNotNull(vm.uiState.value.errorMessage)
        repo.loadFailure = false; vm.retry(); runCurrent()
        assertEquals("a", vm.uiState.value.settings?.userId)
        repo.settings.value = UserReminderSettings.defaults("b"); runCurrent()
        assertEquals("b", vm.uiState.value.settings?.userId); assertNull(vm.uiState.value.errorMessage)
    }
}

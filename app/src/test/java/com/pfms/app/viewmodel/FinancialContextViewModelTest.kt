package com.pfms.app.viewmodel

import com.pfms.app.domain.model.*
import com.pfms.app.domain.repository.FinancialContextRepository
import com.pfms.app.domain.usecase.MonthlyDataPolicy
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialContextViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val date = LocalDate.of(2026, 10, 20)
    private val clock = Clock.fixed(date.atStartOfDay(AppClock.zone).toInstant(), AppClock.zone)
    private val month = YearMonth.of(2026, 9)
    private fun timestamp(days: Long) = date.minusDays(days).atStartOfDay(AppClock.zone).toInstant().toEpochMilli()
    private class Repository : FinancialContextRepository {
        val rows = MutableStateFlow(MonthlyFinancialRecords("a", YearMonth.of(2026, 9)))
        val activity = MutableStateFlow(WelcomeBackActivity("a", null, false))
        var failure = false
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeMonth(month: YearMonth) = flow {
            if (failure) throw IOException("private backend details")
            emitAll(rows.map { it.copy(month = month) })
        }
        override fun observeWelcomeActivity() = activity
        override suspend fun dismissWelcome() { activity.value = activity.value.copy(dismissed = true) }
        override suspend fun reviewMonth(userId: String, month: YearMonth, signature: String): Result<Unit> {
            calls++
            gate?.await()
            return if (signature == MonthlyDataPolicy.assess(rows.value, LocalDate.of(2026, 10, 20)).signature) {
                rows.value = rows.value.copy(reviewedSignature = signature)
                Result.success(Unit)
            } else Result.failure(IllegalArgumentException("private detail"))
        }
    }
    private fun vm(repo: Repository) = main.track(FinancialContextViewModel(repo, clock))

    @Test fun newUserSixSevenAndEightDaysAndBackdatedLoggingUpdateEligibility() = runTest {
        val repo = Repository(); val vm = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.showWelcomeBack.collect() }
        runCurrent(); assertFalse(vm.showWelcomeBack.value)
        for (days in listOf(6L, 7L, 8L)) {
            repo.activity.value = WelcomeBackActivity("a", timestamp(days), false)
            runCurrent(); assertEquals(days >= 7, vm.showWelcomeBack.value)
        }
        // A newly logged historical entry has today's creation timestamp.
        repo.activity.value = WelcomeBackActivity("a", timestamp(0), false)
        runCurrent(); assertFalse(vm.showWelcomeBack.value)
    }

    @Test fun dismissalSurvivesViewModelRecreationInSameSessionAndIsUserScoped() = runTest {
        val repo = Repository().apply { activity.value = WelcomeBackActivity("a", timestamp(8), false) }
        val first = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.showWelcomeBack.collect() }
        runCurrent(); assertTrue(first.showWelcomeBack.value)
        first.dismissWelcome(); runCurrent(); assertFalse(first.showWelcomeBack.value)
        val second = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { second.showWelcomeBack.collect() }
        runCurrent(); assertFalse(second.showWelcomeBack.value)
        repo.activity.value = WelcomeBackActivity("b", null, false)
        runCurrent(); assertFalse(second.showWelcomeBack.value)
    }

    @Test fun reviewingEmptyClosedMonthUpdatesStateAndPreventsDoubleSubmission() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }; val vm = vm(repo)
        vm.selectMonth(month)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthlyState.collect() }
        runCurrent()
        val signature = vm.monthlyState.value.status!!.signature
        vm.reviewMonth(signature); vm.reviewMonth(signature); runCurrent()
        assertEquals(1, repo.calls); assertTrue(vm.monthlyState.value.isReviewing)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals(MonthlyDataReason.REVIEWED, vm.monthlyState.value.status!!.reason)
        assertFalse(vm.monthlyState.value.isReviewing)
    }

    @Test fun staleReviewCannotApproveChangedFinancialData() = runTest {
        val repo = Repository(); val vm = vm(repo)
        vm.selectMonth(month)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthlyState.collect() }
        runCurrent(); val original = vm.monthlyState.value.status!!.signature
        repo.rows.value = repo.rows.value.copy(incomes = listOf(Income("new", "a", 100, receivedDate = "2026-09-01")))
        runCurrent(); vm.reviewMonth(original); runCurrent()
        assertTrue(vm.monthlyState.value.status!!.isPartial)
        assertEquals("Your records changed. Please review this month again.", vm.monthlyState.value.errorMessage)
    }

    @Test fun failedLoadingHasSafeMessageAndRetryRecovers() = runTest {
        val repo = Repository().apply { failure = true }; val vm = vm(repo)
        vm.selectMonth(month)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthlyState.collect() }
        runCurrent()
        assertFalse(vm.monthlyState.value.isLoading)
        assertFalse(vm.monthlyState.value.errorMessage.orEmpty().contains("private"))
        repo.failure = false; vm.retry(); runCurrent()
        assertNull(vm.monthlyState.value.errorMessage)
        assertEquals(MonthlyDataReason.NO_ENTRIES, vm.monthlyState.value.status!!.reason)
    }
}

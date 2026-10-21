package com.pfms.app.data.repository

import com.pfms.app.data.error.repositoryResult
import com.pfms.app.data.local.FinancialContextPreferences
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.domain.model.MonthlyFinancialRecords
import com.pfms.app.domain.model.WelcomeBackActivity
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.FinancialContextRepository
import com.pfms.app.domain.usecase.MonthlyDataPolicy
import com.pfms.app.domain.usecase.dashboard.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomFinancialContextRepository @Inject constructor(
    private val database: PfmsDatabase,
    private val auth: AuthRepository,
    private val preferences: FinancialContextPreferences,
    private val clock: Clock
) : FinancialContextRepository {
    private val users = auth.currentUser.map { it?.uid?.takeIf(String::isNotBlank) }.distinctUntilChanged()
    private val dismissedUsers = MutableStateFlow<Set<String>>(emptySet())

    override fun observeMonth(month: YearMonth): Flow<MonthlyFinancialRecords> = users.flatMapLatest { uid ->
        if (uid == null) flowOf(MonthlyFinancialRecords("", month, isReady = false)) else combine(
            database.incomeDao().observeIncome(uid), database.expenseDao().observe(uid), preferences.review(uid, month)
        ) { incomes, expenses, signature ->
            MonthlyFinancialRecords(uid, month, incomes.toDomain(), expenses.map { it.toDomain() }, signature)
        }.onStart { emit(MonthlyFinancialRecords(uid, month, isReady = false)) }
    }.flowOn(Dispatchers.IO)

    override suspend fun reviewMonth(userId: String, month: YearMonth, signature: String): Result<Unit> = withContext(Dispatchers.IO) {
        repositoryResult {
            require(userId.isNotBlank() && auth.currentUser.first()?.uid == userId)
            val current = observeMonth(month).first { it.isReady }
            require(current.userId == userId)
            val assessment = MonthlyDataPolicy.assess(current, AppClock.today(clock))
            require(assessment.canReview && assessment.signature == signature)
            preferences.review(userId, month, signature)
        }
    }

    override fun observeWelcomeActivity(): Flow<WelcomeBackActivity> = users.flatMapLatest { uid ->
        if (uid == null) flowOf(WelcomeBackActivity()) else combine(
            database.financialActivityDao().observeLastRecordedAt(uid), preferences.lastRecordedAt(uid), dismissedUsers
        ) { recorded, remembered, dismissed ->
            WelcomeBackActivity(uid, listOfNotNull(recorded, remembered).maxOrNull(), uid in dismissed)
        }.distinctUntilChanged().onEach { activity ->
            activity.lastRecordedAt?.let { preferences.rememberRecordedAt(uid, it) }
        }.onStart { emit(WelcomeBackActivity(uid)) }
    }.flowOn(Dispatchers.IO)

    override suspend fun dismissWelcome() {
        val uid = auth.currentUser.first()?.uid ?: return
        dismissedUsers.update { it + uid }
    }
}

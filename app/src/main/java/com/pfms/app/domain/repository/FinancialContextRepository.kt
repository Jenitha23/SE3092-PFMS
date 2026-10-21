package com.pfms.app.domain.repository

import com.pfms.app.domain.model.MonthlyFinancialRecords
import com.pfms.app.domain.model.WelcomeBackActivity
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface FinancialContextRepository {
    fun observeMonth(month: YearMonth): Flow<MonthlyFinancialRecords>
    suspend fun reviewMonth(userId: String, month: YearMonth, signature: String): Result<Unit>
    fun observeWelcomeActivity(): Flow<WelcomeBackActivity>
    suspend fun dismissWelcome()
}

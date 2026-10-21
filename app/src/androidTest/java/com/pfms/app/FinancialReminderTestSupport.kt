package com.pfms.app

import com.pfms.app.domain.usecase.*
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.savings.*

internal fun financialReminderRules(): FinancialReminderUseCases {
    val progress = CalculateGoalProgressUseCase(CalculateSavedAmountUseCase())
    val net = CalculateMonthlyNetSavingsUseCase(GetMonthlyIncomeSummaryUseCase())
    return FinancialReminderUseCases(SalaryReminderUseCase(), CalculateGoalPaceUseCase(progress,
        CalculateCurrentMonthGoalContributionUseCase(), net, CalculateTrailingNetSavingsAverageUseCase(net)), progress)
}

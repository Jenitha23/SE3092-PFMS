package com.pfms.app.domain.usecase

import java.time.LocalDate
import java.time.YearMonth

object SalaryScheduleCalculator {
    fun nextPayday(day: Int, today: LocalDate): LocalDate {
        require(day in 1..31)
        val month = YearMonth.from(today)
        val current = month.atDay(minOf(day, month.lengthOfMonth()))
        if (!current.isBefore(today)) return current
        val next = month.plusMonths(1)
        return next.atDay(minOf(day, next.lengthOfMonth()))
    }
}

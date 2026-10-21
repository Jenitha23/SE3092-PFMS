package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.*
import com.pfms.app.domain.model.savings.GoalPaceStatus
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import com.pfms.app.domain.usecase.savings.CalculateGoalPaceUseCase
import com.pfms.app.domain.usecase.savings.CalculateGoalProgressUseCase
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Financial eligibility and content only; scheduling, preferences and Android stay outside domain logic. */
class FinancialReminderUseCases @Inject constructor(
    private val salaryReminder: SalaryReminderUseCase,
    private val goalPace: CalculateGoalPaceUseCase,
    private val goalProgress: CalculateGoalProgressUseCase
) {
    fun daily() = listOf(FinancialReminder("daily", "Daily finance reminder",
        "Ready to record your finances today? Add income or expenses whenever you're ready.", ReminderDestination.DAILY_ENTRY))

    fun salary(records: ReminderFinancialRecords, today: LocalDate): List<FinancialReminder> {
        val template = records.salary?.takeIf { it.userId == records.userId } ?: return emptyList()
        if (!salaryReminder.shouldShowReminder(template, today)) return emptyList()
        val month = YearMonth.from(today).toString()
        return listOf(FinancialReminder("salary:${template.id}", "Salary day reminder",
            "Your scheduled salary is ready to review. Confirm it when you've received it.", ReminderDestination.SALARY, month))
    }

    fun recurring(records: ReminderFinancialRecords, today: LocalDate): List<FinancialReminder> =
        records.recurring.filter { it.id.isNotBlank() && it.amountMinor > 0 && RecurringExpenseCalculator.isDue(it, today) }.map { item ->
            FinancialReminder("recurring:${item.id}", if (item.subscription) "Subscription Payment Due: ${item.title}" else "Recurring Expense Due: ${item.title}",
                "LKR ${money(item.amountMinor)} is due. Tap to review and confirm this occurrence.",
                if (item.subscription) ReminderDestination.SUBSCRIPTIONS else ReminderDestination.RECURRING, item.nextDueDate)
        }

    /** receivedDate is the selected invoice date for existing Expected freelance records, not logging/sync time. */
    fun freelance(records: ReminderFinancialRecords, today: LocalDate): List<FinancialReminder> = records.incomes.mapNotNull { income ->
        if (income.userId != records.userId || !income.source.equals("Freelance", true) || income.amountMinor <= 0 ||
            !(income.paymentStatus.equals("Expected", true) || income.paymentStatus.equals("Pending", true))) return@mapNotNull null
        val date = FinancialEntryDate.parse(income.receivedDate) ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(date, today)
        if (days <= 14) return@mapNotNull null
        FinancialReminder("invoice:${income.id}", "Freelance invoice pending",
            "${income.projectName?.takeIf { it.isNotBlank() } ?: "An invoice"} has been pending for $days days. Review its payment status.",
            ReminderDestination.INVOICES, "pending:$date")
    }

    fun subscriptions(records: ReminderFinancialRecords, today: LocalDate): List<FinancialReminder> = records.recurring
        .filter { it.id.isNotBlank() && RecurringExpenseCalculator.isReviewDue(it, today) }.map { item ->
            FinancialReminder("review:${item.id}", "Subscription Review: ${item.title}",
                "Your subscription is due for review. You can keep, change, or cancel it.", ReminderDestination.SUBSCRIPTIONS,
                "${item.reminderDate}:${item.flag}")
        }

    /** Seven completed calendar days ending before the configured weekly opportunity. No live FX or estimated money. */
    fun weekly(records: ReminderFinancialRecords, endExclusive: LocalDate): List<FinancialReminder> {
        val start = endExclusive.minusDays(7)
        fun inWeek(value: String) = FinancialEntryDate.parse(value)?.let { !it.isBefore(start) && it.isBefore(endExclusive) } == true
        val incomes = records.incomes.filter { it.userId == records.userId && inWeek(it.receivedDate) && IncomeRealizedCalculator.isRealized(it) }
        val expenses = records.expenses.filter { it.userId == records.userId && inWeek(it.date) }
        val validIncome = incomes.filter(::converted)
        val validExpenses = expenses.filter { it.currency == "LKR" && it.amountMinor > 0 }
        val totalIncome = validIncome.fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(IncomeRealizedCalculator.getRealizedSignedAmountMinor(row)) }
        val totalExpenses = validExpenses.fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.amountMinor) }
        val invalidDates = records.incomes.any { it.userId == records.userId && FinancialEntryDate.parse(it.receivedDate) == null } ||
            records.expenses.any { it.userId == records.userId && FinancialEntryDate.parse(it.date) == null }
        val partial = invalidDates || validIncome.size != incomes.size || validExpenses.size != expenses.size
        val empty = if (incomes.isEmpty() && expenses.isEmpty()) "No income or expenses recorded. " else ""
        val body = "$start to ${endExclusive.minusDays(1)}. ${empty}Recorded income LKR ${money(totalIncome)}; expenses LKR ${money(totalExpenses)}; net savings LKR ${money(totalIncome - totalExpenses)}." +
            if (partial) " Partial Data: some records need a valid date, amount or saved conversion." else ""
        return listOf(FinancialReminder("weekly", "Weekly financial summary", body, ReminderDestination.SUMMARY))
    }

    fun goal(records: ReminderFinancialRecords, today: LocalDate): GoalReminderAssessment {
        val goal = records.goal?.takeIf { it.userId == records.userId && it.status == GoalStatus.ACTIVE }
            ?: return GoalReminderAssessment("inactive")
        if (goal.targetAmountMinor <= 0 || FinancialEntryDate.parse(goal.targetDate) == null) return GoalReminderAssessment("unknown:${goal.id}")
        val transactions = records.goalTransactions.filter { it.userId == records.userId && it.goalId == goal.id }
        val incomes = records.incomes.filter { it.userId == records.userId }
        val expenses = records.expenses.filter { it.userId == records.userId }
        if (incomes.any { FinancialEntryDate.parse(it.receivedDate) == null || (IncomeRealizedCalculator.isRealized(it) && !converted(it)) } ||
            expenses.any { FinancialEntryDate.parse(it.date) == null || it.currency != "LKR" || it.amountMinor <= 0 } ||
            transactions.any { FinancialEntryDate.parse(it.date) == null }) return GoalReminderAssessment("unknown:${goal.id}")
        // The existing pace calculator uses Long arithmetic. Suppress an alert if extreme cached values could overflow it.
        val magnitude = (incomes.map { BigInteger.valueOf(IncomeRealizedCalculator.getRealizedSignedAmountMinor(it)).abs() } +
            expenses.map { BigInteger.valueOf(it.amountMinor).abs() } + transactions.map { BigInteger.valueOf(it.amountMinor).abs() } +
            listOf(BigInteger.valueOf(goal.targetAmountMinor), BigInteger.valueOf(goal.openingSavedAmountMinor).abs()))
            .fold(BigInteger.ZERO, BigInteger::add)
        if (magnitude > BigInteger.valueOf(Long.MAX_VALUE / 4)) return GoalReminderAssessment("unknown:${goal.id}")
        if (goalProgress(goal, transactions, today).isCompleted) return GoalReminderAssessment("completed:${goal.id}")
        val pace = goalPace(goal, transactions, incomes, expenses, today)
        if (pace.paceStatus != GoalPaceStatus.BEHIND) return GoalReminderAssessment("on_track:${goal.id}")
        val state = "behind:${goal.id}:${goal.targetAmountMinor}:${goal.targetDate}"
        return GoalReminderAssessment(state, FinancialReminder("goal:${goal.id}", "Goal pace: Behind",
            "${goal.name.ifBlank { "Your savings goal" }} is Behind based on your recorded finances. Review your goal when you're ready.", ReminderDestination.GOAL))
    }

    private fun converted(income: Income) = income.amountMinor > 0 && (income.currency == "LKR" ||
        (income.currency == "USD" && (income.amountLkrMinor ?: 0) > 0))
    private fun money(minor: Long) = money(BigInteger.valueOf(minor))
    private fun money(minor: BigInteger) = BigDecimal(minor, 2).toPlainString()
}

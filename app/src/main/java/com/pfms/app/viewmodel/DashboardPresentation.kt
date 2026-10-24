package com.pfms.app.viewmodel

import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.SessionState
import com.pfms.app.domain.model.UserProfile
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.model.dashboard.TransactionType
import com.pfms.app.domain.model.dashboard.displayLabel
import com.pfms.app.domain.usecase.dashboard.DashboardOverview
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class DashboardIdentity(val name: String = "Your account", val initials: String = "TM",
    val subtitle: String = "Personal finance")
data class DashboardGoal(val title: String = "No active savings goal",
    val saved: String = "Set a goal to track your savings progress", val percent: String = "0%",
    val progress: Float = 0f, val target: String = "Plan your next milestone", val hasGoal: Boolean = false,
    val loading: Boolean = false, val error: String? = null)
data class DashboardBar(val month: java.time.YearMonth, val label: String, val income: Float,
    val expense: Float, val description: String, val selected: Boolean)
data class DashboardLedgerRow(val transaction: Transaction, val title: String, val detail: String,
    val amount: String, val badge: String, val positive: Boolean, val pending: Boolean)
data class DashboardContentState(
    val month: String, val monthName: String, val cycle: String, val remaining: String,
    val net: String, val status: String, val deficit: Boolean,
    val income: String, val expenses: String, val growth: String, val expenseRatio: String,
    val coverage: String, val coverageProgress: Float, val coverageNote: String,
    val chartPeriod: String, val bars: List<DashboardBar>, val hasNegativeBars: Boolean,
    val averageNet: String, val dailySpend: String, val dailyPace: String, val dailyPaceUp: Boolean,
    val sparkline: List<Float>, val committed: String, val committedProgress: Float,
    val committedAmount: String, val discretionary: String, val ledgerCount: String,
    val transactions: List<DashboardLedgerRow>, val hasData: Boolean
)
data class DashboardChromeState(val identity: DashboardIdentity = DashboardIdentity(),
    val goal: DashboardGoal = DashboardGoal(loading = true), val monthMenuExpanded: Boolean = false)

/** Formatting and chart coordinates belong to presentation; money rules stay in the domain. */
object DashboardPresentationMapper {
    private val shortMonth = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    fun money(minor: Long): String = (if (minor < 0) "−LKR " else "LKR ") + NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(minor, 2).abs())
    fun percent(value: Double): String = String.format(Locale.US, "%.1f%%", value)
    private fun amount(minor: Long): String = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(minor, 2))
    private fun delta(value: Double?): String = if (value == null) "No prior comparison"
        else (if (value > 0) "+" else "") + percent(value) + " vs last month"

    fun identity(profile: UserProfile?, session: SessionState): DashboardIdentity {
        val user: AuthUser? = (session as? SessionState.Authenticated)?.user
        val name = (profile?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.displayName?.takeIf { it.isNotBlank() } ?: "Your account").substringBefore("@")
        val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT)
        return DashboardIdentity(name, initials.ifBlank { "TM" },
            if (user?.isEmailVerified == true) "Email verified" else "Personal finance")
    }

    fun goal(state: SavingsGoalUiState): DashboardGoal {
        val goal = state.activeGoal ?: return DashboardGoal(loading = state.isLoading, error = state.errorMessage)
        val date = runCatching { LocalDate.parse(goal.targetDate).format(shortMonth) }.getOrDefault(goal.targetDate)
        return DashboardGoal(goal.name, "${money(state.currentSavedMinor)} of ${money(goal.targetAmountMinor)}",
            percent(state.progressPercent), (state.progressPercent / 100).toFloat().coerceIn(0f, 1f),
            "Target: $date · ${money(state.requiredMonthlySavingMinor)}/mo", true, state.isLoading, state.errorMessage)
    }

    fun content(data: DashboardOverview, transactions: List<Transaction>): DashboardContentState {
        val summary = data.summary
        val scale = data.trend.maxOfOrNull { maxOf(abs(it.incomeMinor.toDouble()), abs(it.expenseMinor.toDouble())) }
            ?.coerceAtLeast(1.0) ?: 1.0
        val bars = data.trend.map { point -> DashboardBar(point.month,
            point.month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)),
            (point.incomeMinor / scale).toFloat(), (point.expenseMinor / scale).toFloat(),
            "${point.month.format(shortMonth)}: inflow ${money(point.incomeMinor)}, outflow ${money(point.expenseMinor)}",
            point.month == summary.month) }
        val sparkMax = data.dailyAverageHistory.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        return DashboardContentState(
            month = summary.month.format(shortMonth),
            monthName = summary.month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)).uppercase(Locale.ROOT),
            cycle = if (data.isCurrentMonth) "CYCLE ${summary.month.monthValue} · DAY ${data.elapsedDays} OF ${summary.month.lengthOfMonth()}"
                else "${summary.month.year} · MONTH COMPLETE · ${summary.month.lengthOfMonth()} DAYS",
            remaining = if (data.isCurrentMonth) "${data.daysRemaining} days left" else "Past month",
            net = money(summary.netSavingsMinor),
            status = when {
                !summary.hasData -> "No recorded activity this month"
                summary.netSavingsMinor < 0 -> "Deficit · spending exceeds income"
                else -> "Surplus · income covers spending"
            }, deficit = summary.netSavingsMinor < 0,
            income = amount(summary.totalIncomeMinor), expenses = amount(summary.totalExpenseMinor),
            growth = delta(data.inflowChangePercent),
            expenseRatio = data.expenseToIncomePercent?.let { "${percent(it)} of inflow" } ?: "No positive inflow",
            coverage = data.coveragePercent?.let(::percent) ?: "No outflow",
            coverageProgress = ((data.coveragePercent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f),
            coverageNote = if (summary.netSavingsMinor < 0) "Shortfall: ${money(-summary.netSavingsMinor)}"
                else "Net available: ${money(summary.netSavingsMinor)}",
            chartPeriod = "${data.trend.first().month.format(shortMonth)} – ${data.trend.last().month.format(shortMonth)}",
            bars = bars, hasNegativeBars = bars.any { it.income < 0f || it.expense < 0f },
            averageNet = money(data.historicalAverageNetMinor) + "/mo",
            dailySpend = money(data.dailySpendMinor), dailyPace = delta(data.dailySpendChangePercent),
            dailyPaceUp = (data.dailySpendChangePercent ?: 0.0) > 0,
            sparkline = data.dailyAverageHistory.map { (it.toDouble() / sparkMax).toFloat() },
            committed = percent(summary.split.committedPercent),
            committedProgress = (summary.split.committedPercent / 100).toFloat().coerceIn(0f, 1f),
            committedAmount = money(summary.split.committedMinor),
            discretionary = "${percent(summary.split.discretionaryPercent)} flexible",
            ledgerCount = (summary.incomeCount + summary.expenseCount).toString(),
            transactions = transactions.map(::ledgerRow), hasData = summary.hasData
        )
    }

    private fun ledgerRow(row: Transaction): DashboardLedgerRow {
        val positive = row.type == TransactionType.INCOME && !row.isLoss
        val date = runCatching { LocalDate.parse(row.date).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)) }
            .getOrDefault(row.date)
        val badge = when {
            row.isPending -> "Expected"
            row.isLoss -> "Crypto loss"
            row.type == TransactionType.INCOME -> "Received"
            else -> row.classification?.displayLabel() ?: "Expense"
        }
        return DashboardLedgerRow(row, row.subtitle.ifBlank { row.title }, "${row.title} · $date",
            (if (row.isPending) "" else if (positive) "+" else "−") + money(row.amountMinor), badge, positive, row.isPending)
    }
}

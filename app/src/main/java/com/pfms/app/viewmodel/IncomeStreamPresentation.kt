package com.pfms.app.viewmodel

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.*
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import com.pfms.app.domain.usecase.dashboard.AppClock
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class IncomeStreamFilter(val label: String, val source: IncomeStreamSource? = null) {
    ALL("All"), SALARY("Salary", IncomeStreamSource.SALARY), FREELANCE("Freelance", IncomeStreamSource.FREELANCE),
    ADSENSE("AdSense", IncomeStreamSource.ADSENSE), CRYPTO("Crypto", IncomeStreamSource.CRYPTO), OTHER("Other", IncomeStreamSource.OTHER)
}
data class IncomeStreamControls(
    val month: YearMonth = AppClock.currentMonth(),
    val filter: IncomeStreamFilter = IncomeStreamFilter.ALL,
    val oldestFirst: Boolean = false,
    val monthMenu: Boolean = false,
    val recordMenuId: String? = null,
    val deleteId: String? = null
)
data class IncomeStreamMetric(val label: String, val value: String, val detail: String, val source: IncomeStreamSource? = null)
data class IncomeStreamShare(val label: String, val percentage: String, val amount: String, val fraction: Float, val source: IncomeStreamSource)
enum class IncomeRecordIcon { SALARY, FREELANCE, ADSENSE, OTHER, CRYPTO, BTC, ETH, USDT, SOL, BNB, XRP, ADA }
data class IncomeStreamRow(
    val id: String, val title: String, val source: IncomeStreamSource, val sourceLabel: String,
    val date: String, val note: String, val amount: String, val conversion: String?,
    val status: String, val isLoss: Boolean, val isPending: Boolean, val canMarkReceived: Boolean,
    val assetLabel: String? = null, val icon: IncomeRecordIcon = IncomeRecordIcon.OTHER
)
data class IncomeStreamState(
    val controls: IncomeStreamControls = IncomeStreamControls(),
    val monthLabel: String = "",
    val monthChoices: List<Pair<YearMonth, String>> = emptyList(),
    val isLoading: Boolean = true, val isSaving: Boolean = false,
    val error: String? = null, val canRetry: Boolean = false,
    val total: String = "LKR 0.00", val totalLabel: String = "REALIZED INCOME",
    val trend: String = "No previous-month comparison", val trendNegative: Boolean = false,
    val metrics: List<IncomeStreamMetric> = emptyList(),
    val pending: String? = null, val cryptoLoss: String? = null, val conversionWarning: String? = null,
    val shares: List<IncomeStreamShare> = emptyList(), val rows: List<IncomeStreamRow> = emptyList(),
    val recordCount: String = "0 records", val emptyTitle: String = "No Income Records Yet",
    val emptyDescription: String = "Add an income record to start tracking this month.",
    val deleteRecord: IncomeStreamRow? = null,
    val compactMonthLabel: String = ""
)

object IncomeStreamPresentation {
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    fun create(state: IncomeUiState, controls: IncomeStreamControls): IncomeStreamState {
        val overview = IncomeStreamCalculator.calculate(state.incomes, controls.month)
        val filtered = overview.records.filter { controls.filter.source == null || IncomeStreamCalculator.source(it) == controls.filter.source }
        val comparator = compareBy<Income> { it.receivedDate }.thenBy { it.createdAt }.thenBy { it.id }
        val rows = filtered.sortedWith(if (controls.oldestFirst) comparator else comparator.reversed()).map(::row)
        return IncomeStreamState(controls, controls.month.format(monthFormat),
            (0L..23L).map { offset -> controls.month.minusMonths(offset).let { it to it.format(monthFormat) } },
            state.isLoading, state.isSaving, state.errorMessage, state.canRetry,
            total = money(overview.realizedMinor),
            totalLabel = "${controls.month.month.name} REALIZED INCOME",
            trend = overview.changePercent?.let { "${if (it > 0) "+" else ""}${String.format(Locale.US, "%.1f", it)}% vs previous month" }
                ?: "No previous-month comparison",
            trendNegative = (overview.changePercent ?: 0.0) < 0,
            metrics = listOf(
                IncomeStreamMetric("LKR", money(overview.lkrMinor), "Received locally", IncomeStreamSource.SALARY),
                IncomeStreamMetric("USD", money(overview.usdInLkrMinor), "USD ${number(overview.usdMinor)} received", IncomeStreamSource.FREELANCE),
                IncomeStreamMetric("CRYPTO NET", money(overview.cryptoNetMinor), "Gains minus losses", IncomeStreamSource.CRYPTO)),
            pending = if (overview.pendingCount > 0) "${overview.pendingCount} ${if (overview.pendingCount == 1) "invoice" else "invoices"} awaiting payment · ${money(overview.pendingMinor)}" else null,
            cryptoLoss = if (overview.cryptoLossesMinor > 0) "Crypto loss adjustment · ${money(-overview.cryptoLossesMinor)}" else null,
            conversionWarning = if (overview.unconvertedCount > 0) "${overview.unconvertedCount} ${if (overview.unconvertedCount == 1) "record has" else "records have"} no LKR conversion. Totals include converted values only." else null,
            shares = overview.sources.map { IncomeStreamShare(label(it.source),
                if (it.fraction < .001f) "<0.1%" else "${String.format(Locale.US, "%.1f", it.fraction * 100)}%",
                money(it.amountMinor), it.fraction, it.source) },
            rows = rows, recordCount = "${rows.size} ${if (rows.size == 1) "record" else "records"}",
            emptyTitle = if (controls.filter == IncomeStreamFilter.ALL) "No Income Records Yet" else "No ${controls.filter.label} Records",
            emptyDescription = "No ${if (controls.filter == IncomeStreamFilter.ALL) "income" else controls.filter.label.lowercase()} entries in ${controls.month.format(monthFormat)}. Add a record or choose another month.",
            deleteRecord = overview.records.firstOrNull { it.id == controls.deleteId }?.let(::row),
            compactMonthLabel = controls.month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)))
    }

    fun row(income: Income): IncomeStreamRow {
        val source = IncomeStreamCalculator.source(income)
        val loss = source == IncomeStreamSource.CRYPTO && income.cryptoType.equals("Loss", true)
        val pending = !IncomeRealizedCalculator.isRealized(income)
        val asset = income.cryptoAsset?.trim()?.takeIf { source == IncomeStreamSource.CRYPTO && it.isNotEmpty() }?.uppercase(Locale.ROOT)
        val recordIcon = when (source) {
            IncomeStreamSource.SALARY -> IncomeRecordIcon.SALARY
            IncomeStreamSource.FREELANCE -> IncomeRecordIcon.FREELANCE
            IncomeStreamSource.ADSENSE -> IncomeRecordIcon.ADSENSE
            IncomeStreamSource.OTHER -> IncomeRecordIcon.OTHER
            IncomeStreamSource.CRYPTO -> when (asset) {
                "BTC" -> IncomeRecordIcon.BTC; "ETH" -> IncomeRecordIcon.ETH; "USDT" -> IncomeRecordIcon.USDT
                "SOL" -> IncomeRecordIcon.SOL; "BNB" -> IncomeRecordIcon.BNB; "XRP" -> IncomeRecordIcon.XRP
                "ADA" -> IncomeRecordIcon.ADA; else -> IncomeRecordIcon.CRYPTO
            }
        }
        val title = if (source == IncomeStreamSource.FREELANCE) income.projectName?.takeIf { it.isNotBlank() } ?: "Freelance"
            else if (source == IncomeStreamSource.CRYPTO) "Crypto trading" else if (source == IncomeStreamSource.OTHER) income.source.ifBlank { "Other income" } else label(source)
        val signedAmount = if (loss) -income.amountMinor else income.amountMinor
        val converted = if (income.currency == "LKR") null else income.amountLkrMinor?.let {
            money(if (loss) -it else it)
        } ?: "LKR conversion unavailable"
        return IncomeStreamRow(income.id, title, source, label(source),
            runCatching { LocalDate.parse(income.receivedDate).format(dateFormat) }.getOrDefault(income.receivedDate.ifBlank { "Date unavailable" }),
            income.note.trim().takeUnless { asset != null && it.equals(asset, true) }.orEmpty(),
            money(signedAmount, income.currency, positiveSign = !pending), converted,
            when { pending -> "Pending"; loss -> "Net loss"; source == IncomeStreamSource.CRYPTO -> "Realized gain"; else -> "Received" },
            loss, pending, source == IncomeStreamSource.FREELANCE && pending, asset, recordIcon)
    }

    private fun label(source: IncomeStreamSource) = when (source) {
        IncomeStreamSource.SALARY -> "Salary"; IncomeStreamSource.FREELANCE -> "Freelance"
        IncomeStreamSource.ADSENSE -> "AdSense"; IncomeStreamSource.CRYPTO -> "Crypto"; IncomeStreamSource.OTHER -> "Other"
    }
    private fun number(value: Long): String = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(value, 2).abs())
    private fun money(value: Long, currency: String = "LKR", positiveSign: Boolean = false): String =
        "${if (value < 0) "−" else if (value > 0 && positiveSign) "+" else ""}$currency ${number(value)}"
}

package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.IncomeTrendPoint
import com.pfms.app.domain.model.analytics.MonthlyIncomeSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class IncomeInsightRange(val months: Int?) { MONTH(1), QUARTER(3), HALF_YEAR(6), YEAR(12), ALL(null) }
data class IncomeInsightPoint(val key: String, val amountMinor: Long, val x: Float = 0f, val y: Float = 0f,
    val empty: Boolean = false)
data class IncomeInsightSource(val key: String, val amountMinor: Long, val positiveMinor: Long, val grossShare: Double)
data class IncomeInsightsSnapshot(
    val start: YearMonth, val end: YearMonth, val range: IncomeInsightRange,
    val netMinor: Long, val grossMinor: Long, val lossesMinor: Long, val margin: Double,
    val changePercent: Double?, val dailyAverageMinor: Long, val regularGrossMinor: Long,
    val variableGrossMinor: Long, val regularShare: Double, val variableShare: Double,
    val sources: List<IncomeInsightSource>, val trend: List<IncomeInsightPoint>,
    val accumulation: List<IncomeInsightPoint>, val accumulationZero: Float,
    val trendZero: Float, val averageTrendMinor: Long, val peak: IncomeInsightPoint?,
    val pendingMinor: Long, val pendingCount: Int, val realizedCount: Int, val sourceCount: Int,
    val biggestSource: String?, val biggestShare: Double, val hasRecordDetail: Boolean = true
)

/** Exact amounts and chart normalization; Compose receives only prepared financial values. */
object IncomeInsightsCalculator {
    fun calculate(records: List<Income>, selected: YearMonth, range: IncomeInsightRange = IncomeInsightRange.MONTH,
        today: LocalDate = LocalDate.now(ZoneId.of("Asia/Colombo"))): IncomeInsightsSnapshot {
        val dated = records.mapNotNull { record -> IncomeRealizedCalculator.parseYearMonth(record.receivedDate)?.let { it to record } }
        val first = range.months?.let { selected.minusMonths(it - 1L) }
            ?: dated.filter { it.first <= selected }.minOfOrNull { it.first } ?: selected
        val period = dated.filter { it.first >= first && it.first <= selected }.map { it.second }
        val realized = period.filter(IncomeRealizedCalculator::isRealized)
        val signed = realized.map { IncomeRealizedCalculator.getRealizedSignedAmountMinor(it) }
        val gross = sum(signed.filter { it >= 0 })
        val losses = sum(signed.filter { it < 0 }.map { Math.negateExact(it) })
        val net = Math.subtractExact(gross, losses)
        val summary = sources.map { key ->
            val entries = realized.filter { sourceKey(it.source) == key }.map(IncomeRealizedCalculator::getRealizedSignedAmountMinor)
            val positive = sum(entries.filter { it >= 0 })
            IncomeInsightSource(key, sum(entries), positive, percentage(positive, gross))
        }
        val regular = summary.first { it.key == "Salary" }.positiveMinor
        val variable = Math.subtractExact(gross, regular)
        val months = ChronoUnit.MONTHS.between(first, selected) + 1
        val previousStart = first.minusMonths(months)
        val previousNet = sum(dated.filter { it.first >= previousStart && it.first < first }
            .map { IncomeRealizedCalculator.getRealizedSignedAmountMinor(it.second) })
        val change = if (previousNet > 0) BigDecimal.valueOf(net).subtract(BigDecimal.valueOf(previousNet))
            .multiply(BigDecimal(100)).divide(BigDecimal.valueOf(previousNet), 6, RoundingMode.HALF_UP).toDouble() else null
        val trendCount = if (range == IncomeInsightRange.MONTH) 6L else months
        val trendStart = selected.minusMonths(trendCount - 1)
        val trend = (0L until trendCount).map { offset ->
            val month = trendStart.plusMonths(offset)
            val received = dated.filter { it.first == month && IncomeRealizedCalculator.isRealized(it.second) }
            IncomeInsightPoint(month.toString(), sum(received.map { IncomeRealizedCalculator.getRealizedSignedAmountMinor(it.second) }), empty = received.isEmpty())
        }
        var balance = 0L
        val accumulation = if (range == IncomeInsightRange.MONTH && realized.any { runCatching { LocalDate.parse(it.receivedDate.trim().take(10)) }.isFailure }) emptyList()
        else if (range == IncomeInsightRange.MONTH) {
            (1..selected.lengthOfMonth()).map { day ->
                val date = selected.atDay(day)
                val daily = realized.filter { it.receivedDate.trim().take(10) == date.toString() }.map(IncomeRealizedCalculator::getRealizedSignedAmountMinor)
                balance = Math.addExact(balance, sum(daily))
                IncomeInsightPoint(date.toString(), balance)
            }
        } else (0L until months).map { offset ->
            val month = first.plusMonths(offset)
            balance = Math.addExact(balance, sum(dated.filter { it.first == month }.map { IncomeRealizedCalculator.getRealizedSignedAmountMinor(it.second) }))
            IncomeInsightPoint(month.toString(), balance)
        }
        val pending = period.filterNot(IncomeRealizedCalculator::isRealized)
        val biggest = summary.filter { it.positiveMinor > 0 }.maxByOrNull { it.positiveMinor }
        val days = ChronoUnit.DAYS.between(first.atDay(1), selected.atEndOfMonth()) + 1
        return IncomeInsightsSnapshot(first, selected, range, net, gross, losses, percentage(net, gross), change,
            divide(net, days), regular, variable, percentage(regular, gross), percentage(variable, gross), summary,
            normalize(trend), normalize(accumulation), zero(accumulation), zero(trend), divide(sum(trend.map { it.amountMinor }), trend.size.toLong()),
            trend.maxByOrNull { it.amountMinor }?.takeIf { it.amountMinor > 0 },
            sum(pending.map(IncomeRealizedCalculator::getEffectiveLkrMinor)), pending.size, realized.size,
            summary.count { it.positiveMinor > 0 }, biggest?.key, biggest?.grossShare ?: 0.0)
    }

    /** Legacy content callers can provide aggregated source totals without individual records. */
    fun fromSummary(summary: MonthlyIncomeSummary?, trend: List<IncomeTrendPoint>, selected: YearMonth): IncomeInsightsSnapshot {
        val entries = summary?.let { listOf("Salary" to it.salaryMinor, "Freelance" to it.freelanceMinor,
            "Google AdSense" to it.adsenseMinor, "Crypto" to it.cryptoNetMinor, "Other" to it.otherMinor) }.orEmpty()
        val records = entries.filter { it.second != 0L }.map { (source, amount) ->
            Income(source = source, amountMinor = if (amount < 0) Math.negateExact(amount) else amount,
                cryptoType = if (amount < 0) "Loss" else "Gain", paymentStatus = "Received", receivedDate = selected.atDay(1).toString())
        }
        val base = calculate(records, selected)
        val points = trend.map { IncomeInsightPoint(it.yearMonth, it.amountMinor, empty = it.amountMinor == 0L) }
        return base.copy(trend = normalize(points), trendZero = zero(points), accumulation = emptyList(),
            averageTrendMinor = divide(sum(points.map { it.amountMinor }), points.size.toLong()),
            peak = points.maxByOrNull { it.amountMinor }?.takeIf { it.amountMinor > 0 }, hasRecordDetail = false)
    }

    fun percentage(amount: Long, total: Long): Double = if (total <= 0) 0.0 else BigDecimal.valueOf(amount)
        .multiply(BigDecimal(100)).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP).toDouble()
    private fun sum(values: List<Long>): Long = values.fold(0L, Math::addExact)
    private fun divide(value: Long, count: Long): Long = if (count <= 0) 0 else BigDecimal.valueOf(value).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValueExact()
    private val sources = listOf("Salary", "Freelance", "Google AdSense", "Crypto", "Other")
    private fun sourceKey(value: String): String = sources.firstOrNull { it.equals(value, true) } ?: "Other"
    private fun bounds(points: List<IncomeInsightPoint>): Pair<BigDecimal, BigDecimal> =
        BigDecimal.valueOf(minOf(0, points.minOfOrNull { it.amountMinor } ?: 0)) to BigDecimal.valueOf(maxOf(0, points.maxOfOrNull { it.amountMinor } ?: 0))
    private fun zero(points: List<IncomeInsightPoint>): Float {
        val (low, high) = bounds(points)
        return if (low == high) 1f else high.divide(high - low, 8, RoundingMode.HALF_UP).toFloat()
    }
    private fun normalize(points: List<IncomeInsightPoint>): List<IncomeInsightPoint> {
        val (low, high) = bounds(points)
        return points.mapIndexed { index, point -> point.copy(x = if (points.size <= 1) 0f else index.toFloat() / (points.size - 1),
            y = if (low == high) 1f else (high - BigDecimal.valueOf(point.amountMinor)).divide(high - low, 8, RoundingMode.HALF_UP).toFloat()) }
    }
}

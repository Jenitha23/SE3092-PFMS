package com.pfms.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.dashboard.CategorySpend
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.domain.model.dashboard.ClassificationSplit
import com.pfms.app.domain.model.dashboard.MonthlyTrendPoint
import com.pfms.app.ui.screen.income.formatAnalyticsLkrAmount
import com.pfms.app.ui.theme.BlueAccent
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.ui.theme.ErrorExpense
import com.pfms.app.ui.theme.OrangeAccent
import com.pfms.app.ui.theme.PurpleAccent
import java.time.format.TextStyle
import java.util.Locale

val CommittedColor = EmeraldPrimary
val DiscretionaryColor = PurpleAccent

private val CategoryPalette = listOf(
    EmeraldPrimary, PurpleAccent, OrangeAccent, BlueAccent, ErrorExpense,
    Color(0xFF14B8A6), Color(0xFFEAB308), Color(0xFF64748B)
)

fun categoryChartColor(index: Int): Color = CategoryPalette[index % CategoryPalette.size]

/** 45 -> "45%", 45.5 -> "45.5%". */
fun formatPercent(percent: Double): String {
    val value = SafeCalculations.finiteOrZero(percent)
    return String.format(Locale.US, if (value % 1.0 == 0.0) "%.0f%%" else "%.1f%%", value)
}

/**
 * Committed vs Discretionary as ONE split bar (FR-31). Both amounts and percentages are written out
 * as text under the bar, so the meaning never depends on colour alone (NFR-21).
 */
@Composable
fun SplitBar(split: ClassificationSplit, modifier: Modifier = Modifier) {
    val total = split.totalMinor
    val spoken = if (total == 0L) "No spending recorded yet"
    else "Committed ${formatAnalyticsLkrAmount(split.committedMinor)}, ${formatPercent(split.committedPercent)}. " +
            "Discretionary ${formatAnalyticsLkrAmount(split.discretionaryMinor)}, ${formatPercent(split.discretionaryPercent)}."

    Column(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (split.committedMinor > 0L) {
                Box(Modifier.weight(split.committedMinor.toFloat()).fillMaxSize().background(CommittedColor))
            }
            if (split.discretionaryMinor > 0L) {
                Box(Modifier.weight(split.discretionaryMinor.toFloat()).fillMaxSize().background(DiscretionaryColor))
            }
        }
        Spacer(Modifier.height(10.dp))
        SplitLegendRow("Committed", "Bills and essentials", split.committedMinor, split.committedPercent, CommittedColor)
        Spacer(Modifier.height(8.dp))
        SplitLegendRow("Discretionary", "Choices and extras", split.discretionaryMinor, split.discretionaryPercent, DiscretionaryColor)
    }
}

@Composable
private fun SplitLegendRow(label: String, hint: String, amountMinor: Long, percent: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "$hint \u2022 ${formatAnalyticsLkrAmount(amountMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(formatPercent(percent), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
}

/**
 * Donut of real category totals (FR-30). Slice sizes come from the amounts, not the rounded
 * percentages, so there are no gaps or overlaps. A full text legend is rendered by the screen.
 */
@Composable
fun CategoryDonutChart(
    categories: List<CategorySpend>,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit = {}
) {
    val total = categories.sumOf { it.amountMinor.coerceAtLeast(0L).toDouble() }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val spoken = if (total == 0.0) "No spending recorded for this month"
    else "Spending by category: " + categories.joinToString(", ") {
        "${it.categoryName} ${formatPercent(it.percent)}"
    }

    Box(
        modifier = modifier
            .size(132.dp)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            if (total <= 0.0) {
                drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            } else {
                var start = -90f
                categories.forEachIndexed { index, item ->
                    val sweep = (360.0 * SafeCalculations.ratio(item.amountMinor.coerceAtLeast(0L).toDouble(), total)).toFloat()
                    val gap = if (categories.size > 1 && sweep > 3f) 1.5f else 0f
                    drawArc(
                        color = categoryChartColor(index),
                        startAngle = start,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke)
                    )
                    start += sweep
                }
            }
        }
        centerContent()
    }
}

/**
 * Six-month income-versus-expense bars (FR-32). Months with no data draw as empty, not missing.
 * The screen also prints the exact figures as text under the chart for accessibility.
 */
@Composable
fun IncomeExpenseTrendChart(points: List<MonthlyTrendPoint>, modifier: Modifier = Modifier) {
    val maxValue = (points.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) } ?: 0L).coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val spoken = if (points.isEmpty()) "No income or expenses recorded. Income LKR 0.00, expenses LKR 0.00."
    else "Six month trend. " + points.joinToString(". ") {
        "${it.month.month.getDisplayName(TextStyle.FULL, Locale.US)}: income " +
                "${formatAnalyticsLkrAmount(it.incomeMinor)}, expenses ${formatAnalyticsLkrAmount(it.expenseMinor)}"
    }

    Column(modifier = modifier.semantics { contentDescription = spoken }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            if (points.isEmpty()) {
                drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                return@Canvas
            }
            val groupWidth = size.width / points.size
            val barWidth = groupWidth * 0.28f
            val radius = CornerRadius(6f, 6f)
            points.forEachIndexed { i, p ->
                val left = i * groupWidth + groupWidth * 0.5f - barWidth - 2f
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                drawRoundRect(track, Offset(left + barWidth + 4f, 0f), Size(barWidth, size.height), radius)
                val incomeH = size.height * p.incomeMinor.coerceAtLeast(0L).toFloat() / maxValue.toFloat()
                val expenseH = size.height * p.expenseMinor.coerceAtLeast(0L).toFloat() / maxValue.toFloat()
                if (incomeH > 0f) {
                    drawRoundRect(EmeraldPrimary, Offset(left, size.height - incomeH), Size(barWidth, incomeH), radius)
                }
                if (expenseH > 0f) {
                    drawRoundRect(ErrorExpense, Offset(left + barWidth + 4f, size.height - expenseH), Size(barWidth, expenseH), radius)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    text = p.month.month.getDisplayName(TextStyle.SHORT, Locale.US),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TrendLegend("Income", EmeraldPrimary)
            TrendLegend("Expenses", ErrorExpense)
        }
    }
}

@Composable
private fun TrendLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

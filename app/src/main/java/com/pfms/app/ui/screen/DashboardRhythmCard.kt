package com.pfms.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.CashflowRed
import com.pfms.app.viewmodel.DashboardContentState
import java.time.YearMonth

@Composable
internal fun DashboardRhythmCard(content: DashboardContentState, onMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier) {
    FinancePanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceIcon(FinanceSymbol.CHART)
            Column(Modifier.weight(1f)) {
                Text("Interactive Cash Rhythm", style = MaterialTheme.typography.titleMedium)
                Text("5-month inflow & outflow", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(content.chartPeriod, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Monthly inflow vs outflow", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("● INFLOW", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Text("● OUTFLOW", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                MonthlyCashflowBars(content, onMonth)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Average recorded net · prior 4 months", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(content.averageNet, style = MaterialTheme.typography.titleSmall)
            }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallMetricPanel(Modifier.weight(1f).fillMaxHeight()) {
                Text("DAILY BURN VELOCITY", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(content.dailySpend, style = MaterialTheme.typography.titleMedium)
                Text("per day · recorded to date", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                DailySpendLine(content.sparkline, if (content.dailyPaceUp) CashflowRed else MaterialTheme.colorScheme.primary)
                Text(content.dailyPace, style = MaterialTheme.typography.bodySmall,
                    color = if (content.dailyPaceUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            SmallMetricPanel(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CAPITAL SPLIT", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FinanceIcon(FinanceSymbol.PIE, Modifier.size(16.dp))
                }
                Text("Committed", style = MaterialTheme.typography.titleSmall)
                Text(content.committed, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { content.committedProgress },
                    modifier = Modifier.fillMaxWidth().height(7.dp), gapSize = 0.dp, drawStopIndicator = {})
                Text(content.committedAmount, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(content.discretionary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SmallMetricPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun MonthlyCashflowBars(content: DashboardContentState, onMonth: (YearMonth) -> Unit) {
    val inflow = MaterialTheme.colorScheme.primary
    val outflow = MaterialTheme.colorScheme.error
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        content.bars.forEach { bar ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (bar.selected) inflow.copy(alpha = 0.065f) else Color.Transparent)
                .clickable(role = Role.Button, onClick = { onMonth(bar.month) })
                .semantics(mergeDescendants = true) { contentDescription = bar.description }
                .padding(horizontal = 3.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val top = 6.dp.toPx()
                    val baseline = if (content.hasNegativeBars) size.height * 0.55f else size.height - 2.dp.toPx()
                    val span = if (content.hasNegativeBars) size.height * 0.4f else baseline - top
                    listOf(0.25f, 0.55f, 0.85f).forEach { level ->
                        drawLine(grid, Offset(0f, size.height * level), Offset(size.width, size.height * level),
                            1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                    }
                    drawLine(grid, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                    val width = size.width * 0.32f
                    listOf(bar.income to inflow, bar.expense to outflow).forEachIndexed { index, (fraction, color) ->
                        val barHeight = kotlin.math.abs(fraction) * span
                        if (barHeight > 0f) drawRoundRect(color,
                            topLeft = Offset(size.width * 0.1f + index * size.width * 0.42f,
                                if (fraction >= 0) baseline - barHeight else baseline),
                            size = Size(width, barHeight), cornerRadius = CornerRadius(3.dp.toPx()))
                    }
                }
                Text(bar.label, style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (bar.selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DailySpendLine(points: List<Float>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(32.dp)) {
        if (points.size < 2) return@Canvas
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / points.lastIndex
            val y = size.height * (1f - value.coerceIn(0f, 1f)) * 0.85f + size.height * 0.08f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

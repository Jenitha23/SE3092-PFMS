package com.pfms.app.ui.screen.income

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.*
import kotlin.math.abs

@Composable
internal fun InsightAccumulationChart(points: List<InsightChartPoint>, zero: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Recorded accumulation ending at ${points.lastOrNull()?.value.orEmpty()}" }) {
        val inset = 4.dp.toPx()
        val width = size.width - inset * 2
        val height = size.height - inset * 2
        val zeroY = inset + zero * height
        drawLine(color.copy(alpha = .18f), Offset(inset, zeroY), Offset(size.width - inset, zeroY), 1.dp.toPx())
        if (points.isNotEmpty()) {
            val line = Path().apply { points.forEachIndexed { index, point ->
                val x = inset + point.x * width
                val y = inset + point.y * height
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            } }
            val fill = Path().apply { addPath(line); lineTo(inset + points.last().x * width, zeroY); lineTo(inset, zeroY); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .2f), Color.Transparent)))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, 2.5.dp.toPx(), Offset(inset + points.last().x * width, inset + points.last().y * height))
        }
    }
}

@Composable
internal fun InsightTrendCard(state: IncomeInsightsScreenState, select: (String) -> Unit) {
    val display = state.display ?: return
    val colors = MaterialTheme.colorScheme
    val point = display.trend.firstOrNull { it.key == state.selectedTrend } ?: display.trend.lastOrNull()
    val chartScroll = rememberLazyListState()
    LaunchedEffect(display.trend.firstOrNull()?.key, display.trend.lastOrNull()?.key) {
        if (display.trend.size > 6) chartScroll.scrollToItem((display.trend.indexOfFirst { it.key == point?.key } - 4).coerceAtLeast(0))
    }
    FinancePanel {
        InsightSectionTitle(display.trendTitle, FinanceSymbol.CHART)
        Text("Historical realized income · Tap a month", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        InsightMetricLine("Average / month", display.average)
        if (display.emptyTrend) Text("No income trend yet. LKR 0.00", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (display.trend.isNotEmpty()) BoxWithConstraints {
            val width = if (display.trend.size in 1..6) (maxWidth / display.trend.size).coerceAtLeast(48.dp) else 52.dp
            LazyRow(Modifier.fillMaxWidth().height(156.dp).testTag("insights_trend_chart"), state = chartScroll, horizontalArrangement = Arrangement.Start) {
                items(display.trend, key = { it.key }) { item ->
                    val selected = item.key == point?.key
                    Surface(onClick = { select(item.key) }, modifier = Modifier.width(width).fillMaxHeight().testTag("insight_trend_${item.key}")
                        .semantics { contentDescription = if (item.empty) "${item.month}: No received income" else "${item.month}: ${item.value}" }, shape = RoundedCornerShape(10.dp),
                        color = colors.surface) {
                        Column(Modifier.padding(horizontal = 3.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(item.compact, Modifier.fillMaxWidth(), maxLines = 1,
                                style = MaterialTheme.typography.labelSmall.copy(color = if (item.negative) colors.error else colors.onSurfaceVariant, textAlign = TextAlign.Center),
                                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.sp, stepSize = .5.sp))
                            Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 5.dp, vertical = 6.dp)
                                .testTag(if (item.empty) "insight_empty_bar_${item.key}" else "insight_recorded_bar_${item.key}")) {
                                val y = item.y * size.height
                                val zeroY = display.trendZero * size.height
                                drawLine(colors.outlineVariant, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
                                val barHeight = abs(y - zeroY)
                                if (item.empty) drawRoundRect(colors.primary.copy(alpha = .35f),
                                    Offset(size.width * .12f, 1.dp.toPx()), Size(size.width * .76f, size.height - 2.dp.toPx()), CornerRadius(4.dp.toPx()),
                                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                                else if (barHeight > 0) drawRoundRect(if (item.negative) colors.error else if (selected) colors.primary else colors.primary.copy(alpha = .24f),
                                    Offset(size.width * .12f, minOf(y, zeroY)), Size(size.width * .76f, barHeight), CornerRadius(4.dp.toPx()))
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = if (selected) colors.primaryContainer else colors.surface) {
                                Text(item.label, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = if (selected) colors.primary else colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (display.trend.any { it.empty }) Text("Dashed outline: no received income", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        point?.let { selected -> Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(selected.month, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                FinanceValue(selected.value, Modifier.testTag("insight_selected_trend_amount"), color = if (selected.negative) colors.error else colors.primary)
                if (selected.empty) Text("No received income recorded", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        } }
        display.peak?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}

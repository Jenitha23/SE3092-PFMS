package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*

@Composable
internal fun IncomeInsightsHero(display: IncomeInsightsDisplay) {
    Surface(Modifier.fillMaxWidth().testTag("insights_hero"), shape = RoundedCornerShape(22.dp), color = VaultGreen) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF006C50), VaultGreen, VaultDeep))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = VaultMint.copy(alpha = .12f)) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { FinanceIcon(FinanceSymbol.CHART, Modifier.size(20.dp), VaultMint) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Net realized income", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(display.period, style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = .1f)) { Text(display.range, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FinanceValue(display.total, Modifier.testTag("insights_net_total"), large = true, color = if (display.netNegative) CashflowRed else Color.White)
                Text(display.change, style = MaterialTheme.typography.bodySmall, color = if (display.changeNegative) CashflowRed else VaultMuted)
            }
            if (display.accumulation.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = .13f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Recorded accumulation", style = MaterialTheme.typography.labelMedium, color = VaultMuted)
                        InsightAccumulationChart(display.accumulation, display.accumulationZero, if (display.netNegative) CashflowRed else VaultMint)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(display.accumulation.first().month, style = MaterialTheme.typography.labelSmall, color = VaultMuted)
                            Text(display.accumulation.last().month, style = MaterialTheme.typography.labelSmall, color = VaultMuted)
                        }
                    }
                }
            }
            BoxWithConstraints {
                if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InsightHeroMetric("Gross inflow", display.gross, false, Modifier.fillMaxWidth())
                    InsightHeroMetric("Realized losses", display.losses, true, Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InsightHeroMetric("Gross inflow", display.gross, false, Modifier.weight(1f))
                    InsightHeroMetric("Realized losses", display.losses, true, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Net margin", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                Surface(shape = RoundedCornerShape(8.dp), color = VaultMint.copy(alpha = .1f)) {
                    Text(display.margin, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.titleSmall,
                        color = if (display.netNegative) CashflowRed else VaultMint)
                }
            }
            display.pending?.let { pending ->
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF4D88A).copy(alpha = .08f)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FinanceIcon(FinanceSymbol.INFO, Modifier.size(16.dp), Color(0xFFF4D88A))
                        Text(pending, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF4D88A))
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightHeroMetric(label: String, value: String, loss: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = .06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = VaultMuted)
            FinanceValue(value, Modifier.testTag(if (loss) "insights_hero_losses" else "insights_hero_gross"), color = if (loss) CashflowRed else Color.White)
        }
    }
}

@Composable
internal fun InsightWaterfall(display: IncomeInsightsDisplay) = FinancePanel {
    InsightSectionTitle("Cashflow Waterfall", FinanceSymbol.WALLET)
    Text("Received earnings to net income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    display.flow.forEach { row ->
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), color = if (row.key == "net") colors.primaryContainer else colors.surfaceVariant,
            border = if (row.key == "net") BorderStroke(1.dp, colors.primary.copy(alpha = .15f)) else null) {
            BoxWithConstraints(Modifier.padding(12.dp)) {
                val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FinanceIcon(when(row.key) { "gross" -> FinanceSymbol.INCOME; "losses" -> FinanceSymbol.MINUS; else -> FinanceSymbol.SHIELD }, Modifier.size(21.dp), if (row.negative) colors.error else colors.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall)
                            if (stacked) Text(row.caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        if (!stacked) FinanceValue(row.value, Modifier.weight(1.1f).testTag("insight_flow_${row.key}"), compact = true, align = TextAlign.End, color = if (row.negative) colors.error else colors.primary)
                    }
                    if (stacked) FinanceValue(row.value, Modifier.testTag("insight_flow_${row.key}"), color = if (row.negative) colors.error else colors.primary)
                    else Text(row.caption, Modifier.padding(start = 30.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
    Text("Expected freelance is excluded. Foreign income uses its saved historical LKR value.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun InsightIncomeMix(display: IncomeInsightsDisplay) = FinancePanel {
    InsightSectionTitle("Income Predictability Split", FinanceSymbol.SHIELD)
    Text("Salary and other received income · Share of gross inflow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    InsightMixRow("Regular salary", display.regular, display.regularShare, display.regularFraction, false)
    InsightMixRow("Other received income", display.variable, display.variableShare, display.variableFraction, true)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FinanceIcon(FinanceSymbol.INFO, Modifier.size(18.dp))
            Text(display.concentration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightMixRow(label: String, value: String, share: String, fraction: Float, secondary: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            FinanceValue(value)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(5.dp),
                color = if (secondary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
            Text(share, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InsightSourceBreakdown(display: IncomeInsightsDisplay) = FinancePanel {
    InsightSectionTitle("Income Source Breakdown", FinanceSymbol.PIE)
    Text("Realized net amounts for the selected period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    display.sources.forEach { row ->
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                    FinanceValue(row.value, Modifier.testTag("insight_source_${row.key}"), color = if (row.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(row.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    FinanceValue(row.value, Modifier.weight(1.15f).testTag("insight_source_${row.key}"), compact = true, align = TextAlign.End,
                        color = if (row.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
internal fun InsightMetricTiles(display: IncomeInsightsDisplay) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FinancePanel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.primaryContainer) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { FinanceIcon(FinanceSymbol.CHART, Modifier.size(20.dp)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Net / calendar day", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    FinanceValue(display.dailyAverage, Modifier.testTag("insights_daily_average"))
                }
            }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightSummaryTile("Received entries", display.entries, "insights_received_count", Modifier.weight(1f).fillMaxHeight())
            InsightSummaryTile("Awaiting payment", display.pendingCount, "insights_pending_count", Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun InsightSummaryTile(label: String, value: String, tag: String, modifier: Modifier = Modifier, tinted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.testTag("${tag}_tile"), shape = RoundedCornerShape(13.dp), color = if (tinted) colors.surfaceVariant else colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            FinanceValue(value, Modifier.testTag(tag))
        }
    }
}

@Composable
internal fun InsightEstimateCard(state: IncomeInsightsScreenState, actions: IncomeInsightsActions) = FinancePanel(Modifier.testTag("insights_estimate_card")) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val compare = { focus.clearFocus(); actions.compare() }
    InsightSectionTitle("Estimated vs Actual Average", FinanceSymbol.CHART)
    Text("Compare your estimate with the previous 3 complete calendar months.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    OutlinedTextField(state.estimate, actions.estimate, modifier = Modifier.fillMaxWidth(), enabled = !state.source.isSaving,
        label = { Text("Estimated Income (LKR)", style = MaterialTheme.typography.bodySmall) }, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { compare() }),
        isError = state.estimateError != null, supportingText = state.estimateError?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surfaceVariant, focusedContainerColor = colors.surfaceVariant, unfocusedBorderColor = colors.outlineVariant))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.source.estimatedMonthlyIncomeMinor != null) OutlinedButton(onClick = actions.clearEstimate, enabled = !state.source.isSaving,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("insights_estimate_reset"), shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) { InsightActionLabel("Reset") }
        Button(onClick = compare, enabled = !state.source.isSaving, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("insights_estimate_compare"), shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) { InsightActionLabel(if (state.source.isSaving) "Saving…" else "Compare") }
    }
    state.source.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
    if (state.source.canRetry && state.source.errorMessage != null) TextButton(onClick = actions.retry, enabled = !state.source.isSaving) { Text("Retry") }
    state.display?.comparison?.let { result ->
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)
        BoxWithConstraints {
            if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InsightComparisonAmount("Estimated monthly income", result.estimated, "insights_comparison_estimate")
                InsightComparisonAmount("Trailing 3-month average", result.actual, "insights_comparison_actual")
            } else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InsightSummaryTile("Estimated monthly income", result.estimated, "insights_comparison_estimate", Modifier.weight(1f).fillMaxHeight(), tinted = true)
                InsightSummaryTile("Trailing 3-month average", result.actual, "insights_comparison_actual", Modifier.weight(1f).fillMaxHeight(), tinted = true)
            }
        }
        val accent = if (result.below) colors.error else colors.primary
        Surface(shape = RoundedCornerShape(13.dp), color = accent.copy(alpha = .06f), border = BorderStroke(1.dp, accent.copy(alpha = .15f))) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Difference from estimate", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(result.status, style = MaterialTheme.typography.labelMedium, color = accent)
                }
                FinanceValue(result.difference, Modifier.testTag("insights_comparison_difference"), color = accent)
                Text("${result.variance} vs estimate", style = MaterialTheme.typography.bodySmall, color = accent)
            }
        }
        if (result.months.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Evaluated months", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                result.months.forEach { month -> Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceVariant) {
                    Text(month, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                } }
            }
        }
    }
}

@Composable
private fun InsightActionLabel(text: String) {
    BasicText(text, Modifier.fillMaxWidth(), maxLines = 1,
        style = MaterialTheme.typography.labelLarge.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp, stepSize = .5.sp))
}

@Composable
private fun InsightComparisonAmount(label: String, value: String, tag: String) {
    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceValue(value, Modifier.testTag(tag))
        }
    }
}

@Composable
internal fun InsightMetricLine(label: String, value: String) {
    BoxWithConstraints {
        if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceValue(value)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceValue(value, Modifier.weight(1.2f), compact = true, align = TextAlign.End)
        }
    }
}

@Composable
internal fun InsightSectionTitle(title: String, symbol: FinanceSymbol) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FinanceIcon(symbol, Modifier.size(19.dp)); Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

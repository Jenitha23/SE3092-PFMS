package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.usecase.IncomeStreamSource
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*
import com.pfms.app.R

private val IncomeBlue = Color(0xFF25BCEF)
private val IncomePurple = Color(0xFFC39BFF)
private val IncomeAmber = Color(0xFFFFD265)

@Composable
internal fun incomeSourceColor(source: IncomeStreamSource): Color = when (source) {
    IncomeStreamSource.SALARY -> MaterialTheme.colorScheme.primary
    IncomeStreamSource.FREELANCE -> IncomeBlue
    IncomeStreamSource.ADSENSE -> IncomeAmber
    IncomeStreamSource.CRYPTO -> IncomePurple
    IncomeStreamSource.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Fit complete financial values without abbreviating or truncating them. */
@Composable
internal fun IncomeAmount(value: String, color: Color, modifier: Modifier = Modifier,
    large: Boolean = false, align: TextAlign = TextAlign.Start, compact: Boolean = false) {
    BasicText(value, modifier = modifier.fillMaxWidth(), maxLines = 1,
        style = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.Bold, textAlign = align,
            lineHeight = if (large) 32.sp else if (compact) 20.sp else 24.sp),
        autoSize = TextAutoSize.StepBased(minFontSize = if (compact) 10.sp else 12.sp,
            maxFontSize = if (large) 26.sp else if (compact) 14.sp else 16.sp, stepSize = .5.sp))
}

@Composable
internal fun IncomeStreamSummaryCard(state: IncomeStreamState) {
    Surface(modifier = Modifier.testTag("income_summary"), shape = RoundedCornerShape(20.dp), color = VaultGreen, contentColor = Color.White,
        border = BorderStroke(1.dp, VaultMint.copy(alpha = .18f))) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(VaultGreen, Color(0xFF07543E))))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FinanceIcon(FinanceSymbol.INCOME, Modifier.size(16.dp), tint = VaultMint)
                Text(state.totalLabel, style = MaterialTheme.typography.labelSmall, color = VaultMuted)
            }
            IncomeAmount(state.total, Color.White, Modifier.testTag("income_total"), large = true)
            Text(state.trend, style = MaterialTheme.typography.labelSmall, color = if (state.trendNegative) CashflowRed else VaultMint)
            HorizontalDivider(color = VaultMint.copy(alpha = .16f))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                state.metrics.forEach { metric ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.width(83.dp).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(when (metric.source) {
                                IncomeStreamSource.FREELANCE -> IncomeBlue; IncomeStreamSource.CRYPTO -> IncomePurple; else -> VaultMint
                            }))
                            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = VaultMuted)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            IncomeAmount(metric.value, Color.White, align = TextAlign.End, compact = true)
                            if (metric.source == IncomeStreamSource.FREELANCE) Text(metric.detail,
                                style = MaterialTheme.typography.labelSmall, color = VaultMuted, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            if (state.pending != null || state.cryptoLoss != null || state.conversionWarning != null) {
                HorizontalDivider(color = VaultMint.copy(alpha = .16f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.pending?.let { IncomeSummaryNotice(it, IncomeAmber) }
                    state.cryptoLoss?.let { IncomeSummaryNotice(it, CashflowRed) }
                    state.conversionWarning?.let { IncomeSummaryNotice(it, IncomeAmber) }
                }
            }
        }
    }
}

@Composable
private fun IncomeSummaryNotice(text: String, tint: Color) {
    Text("•  $text", style = MaterialTheme.typography.labelSmall, color = tint)
}

@Composable
internal fun IncomeSourcesCard(state: IncomeStreamState, onReport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FinanceIcon(FinanceSymbol.PIE, Modifier.size(16.dp))
                Text("INCOME STREAMS", Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onReport, modifier = Modifier.heightIn(min = 32.dp), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                    Text("Full report ›", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (state.shares.isEmpty()) {
                Text("No realized inflow this month.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.shares.forEach { share -> Box(Modifier.weight(share.fraction).fillMaxHeight().background(incomeSourceColor(share.source))) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.shares.forEach { share ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(incomeSourceColor(share.source)))
                            Text("${share.label} ${share.percentage}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("Positive inflow · before crypto losses", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun IncomeRecordCard(row: IncomeStreamRow, state: IncomeStreamState, actions: IncomeStreamActions) {
    val colors = MaterialTheme.colorScheme
    val accent = if (row.isLoss) colors.error else if (row.isPending) colors.onSurfaceVariant else colors.primary
    val sourceColor = incomeSourceColor(row.source)
    val fontScale = LocalDensity.current.fontScale
    Surface(onClick = { actions.onEdit(row.id) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("income_record_${row.id}"),
        shape = RoundedCornerShape(18.dp), color = colors.surface,
        border = BorderStroke(1.dp, if (row.isLoss) colors.error.copy(alpha = .2f) else colors.outlineVariant)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val stackAmount = maxWidth < 290.dp || fontScale > 1.15f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = sourceColor.copy(alpha = .08f), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { IncomeRecordIconImage(row.icon, sourceColor) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!stackAmount) Text(row.date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    if (!stackAmount) Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        IncomeAmount(row.amount, accent, align = TextAlign.End, compact = true)
                        row.conversion?.let { Text(it, Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                    }
                    IncomeRecordMenu(row, state, actions)
                }
                // Metadata and notes align with the title, rather than drifting beneath the icon.
                FlowRow(Modifier.padding(start = if (stackAmount) 0.dp else 47.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    if (stackAmount) Text(row.date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    row.assetLabel?.let { asset ->
                        Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                            Text(asset, Modifier.padding(horizontal = 6.dp, vertical = 2.dp).testTag("income_asset_${row.id}"),
                                style = MaterialTheme.typography.labelSmall, color = colors.onSurface)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                        Text(row.status, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                }
                if (stackAmount) {
                    IncomeAmount(row.amount, accent, align = TextAlign.End)
                    row.conversion?.let { Text(it, Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                }
                if (row.note.isNotBlank()) Text(row.note, modifier = Modifier.padding(start = if (stackAmount) 0.dp else 47.dp), style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun IncomeRecordMenu(row: IncomeStreamRow, state: IncomeStreamState, actions: IncomeStreamActions) {
    val colors = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { actions.onMenu(row.id) }, enabled = !state.isSaving,
            modifier = Modifier.size(32.dp).testTag("income_menu_${row.id}")) {
            Icon(painterResource(R.drawable.ic_income_more), contentDescription = "More actions for ${row.title}",
                tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = state.controls.recordMenuId == row.id, onDismissRequest = { actions.onMenu(null) }) {
            if (row.canMarkReceived) DropdownMenuItem(text = { Text("Mark Received") }, onClick = {
                actions.onMenu(null); actions.onMarkReceived(row.id)
            }, enabled = !state.isSaving)
            DropdownMenuItem(text = { Text("Edit") }, onClick = { actions.onMenu(null); actions.onEdit(row.id) }, enabled = !state.isSaving)
            DropdownMenuItem(text = { Text("Delete", color = colors.error) }, onClick = { actions.onDelete(row.id) }, enabled = !state.isSaving)
        }
    }
}

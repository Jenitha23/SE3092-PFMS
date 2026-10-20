package com.pfms.app.ui.screen.income

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.analytics.*
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.ui.component.EmptyState
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.IncomeAnalyticsUiState
import com.pfms.app.viewmodel.IncomeAnalyticsViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Modern Fintech Income Analytics Screen (Member 3).
 *
 * Visual sections:
 * 1. Top bar with Month Selector (navigate between months).
 * 2. Monthly Summary Card: Total realized income in LKR and high-level stats.
 * 3. Regular vs Variable Income Split: Two-tone segmented bar & percentages.
 * 4. 6-Month Income Trend Chart: Canvas bar chart with zero-handling & selected-month highlight.
 * 5. Income Source Breakdown: Category cards (Salary, Freelance, AdSense, Crypto, Other).
 * 6. Estimated vs Actual Income Comparison: Temporary input & variance calculation.
 */
@Composable
fun IncomeAnalyticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncomeAnalyticsViewModel = hiltViewModel(),
    onHome: (() -> Unit)? = null,
    onIncome: (() -> Unit)? = null,
    onExpenses: (() -> Unit)? = null,
    onGoals: (() -> Unit)? = null,
    onAddIncome: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    IncomeAnalyticsContent(
        uiState = uiState,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSetEstimatedMonthlyIncome = viewModel::setEstimatedMonthlyIncome,
        onClearEstimatedMonthlyIncome = viewModel::clearEstimatedMonthlyIncome,
        onClearError = viewModel::clearError,
        onRetry = viewModel::retry,
        onBack = onBack,
        onHome = onHome,
        onIncome = onIncome,
        onExpenses = onExpenses,
        onGoals = onGoals,
        onAddIncome = onAddIncome,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeAnalyticsContent(
    uiState: IncomeAnalyticsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetEstimatedMonthlyIncome: (Long) -> Unit,
    onClearEstimatedMonthlyIncome: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    onIncome: (() -> Unit)? = null,
    onExpenses: (() -> Unit)? = null,
    onGoals: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onAddIncome: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val textPrimary = DarkNavyText
    val brandEmerald = EmeraldPrimary

    Scaffold(
        modifier = modifier,
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Income Analytics",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "Back",
                            fontWeight = FontWeight.Bold,
                            color = brandEmerald,
                            fontSize = 15.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground
                )
            )
        },
        bottomBar = {
            if (onHome != null || onIncome != null || onExpenses != null) {
                com.pfms.app.ui.component.PfmsBottomNavBar(
                    selectedTab = com.pfms.app.ui.component.PfmsNavTab.INSIGHTS,
                    onHomeClick = { onHome?.invoke() ?: onBack() },
                    onIncomeClick = { onIncome?.invoke() },
                    onExpensesClick = { onExpenses?.invoke() },
                    onGoalsClick = onGoals,
                    onInsightsClick = { /* Already on Insights */ }
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = brandEmerald)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error Banner
                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    ErrorBanner(
                        message = uiState.errorMessage,
                        onRetry = if (uiState.canRetry) onRetry else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 1. Month Selector
                MonthSelector(
                    selectedMonth = uiState.selectedMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth
                )

                // 2. Monthly Summary Card
                val summary = uiState.monthlySummary ?: MonthlyIncomeSummary(
                    uiState.selectedMonth.toString(), 0L, 0L, 0L, 0L, 0L, 0L
                )
                MonthlySummaryCard(
                    monthName = formatMonthLong(uiState.selectedMonth),
                    totalRealizedMinor = summary.totalRealizedMinor,
                    highestIncomeMonth = uiState.analyticsSummary?.highestIncomeMonth,
                    highestIncomeMinor = uiState.analyticsSummary?.highestIncomeMinor ?: 0L
                )

                if (!uiState.hasMonthlyIncome && uiState.errorMessage == null) {
                    EmptyState(
                        title = "No income this month",
                        message = "Record income to build your monthly breakdown and trends.",
                        actionLabel = "Add Income",
                        onAction = onAddIncome
                    )
                }

                RegularVsVariableCard(uiState.regularVariable ?: RegularVariableIncome(0L, 0L, 0L, 0.0, 0.0))
                TrendChartCard(
                    trendPoints = uiState.trendPoints,
                    selectedMonthKey = uiState.selectedMonth.toString()
                )

                // 5. Source Breakdown List
                SourceBreakdownCard(summary = summary)

                // 6. Estimated vs Actual Comparison Section
                EstimationComparisonSection(
                    comparison = uiState.estimationComparison,
                    currentEstimateMinor = uiState.estimatedMonthlyIncomeMinor,
                    onApplyEstimate = onSetEstimatedMonthlyIncome,
                    onClearEstimate = onClearEstimatedMonthlyIncome,
                    isSaving = uiState.isSaving
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// COMPOSABLES
// ─────────────────────────────────────────────────────────────

@Composable
fun MonthSelector(
    selectedMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(
                onClick = onPrevious,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MutedSurface,
                    contentColor = DarkNavyText
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Text(text = "◀", fontSize = 14.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatMonthLong(selectedMonth),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkNavyText
                    )
                )
                Text(
                    text = selectedMonth.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SecondaryText
                    )
                )
            }

            FilledTonalIconButton(
                onClick = onNext,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MutedSurface,
                    contentColor = DarkNavyText
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Text(text = "▶", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun MonthlySummaryCard(
    monthName: String,
    totalRealizedMinor: Long,
    highestIncomeMonth: String?,
    highestIncomeMinor: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Total Realized Income",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatAnalyticsLkrAmount(totalRealizedMinor),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (totalRealizedMinor >= 0L) DarkNavyText else ErrorExpense
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "For $monthName",
                    style = MaterialTheme.typography.bodySmall.copy(color = SecondaryText)
                )

                if (highestIncomeMonth != null && highestIncomeMinor > 0L) {
                    Surface(
                        color = EmeraldPrimaryLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Peak: $highestIncomeMonth (${formatAnalyticsLkrAmount(highestIncomeMinor)})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = EmeraldPrimaryDark,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegularVsVariableCard(
    regularVariable: RegularVariableIncome,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Income Predictability Split",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkNavyText
                )
            )

            // Segmented Bar
            val regFraction = if (regularVariable.totalMinor > 0L) {
                SafeCalculations.progressFraction(regularVariable.regularPercentage)
            } else 0f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MutedSurface)
            ) {
                if (regFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(regFraction)
                            .background(EmeraldPrimary)
                    )
                }
                if (regularVariable.totalMinor > 0L && 1f - regFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight((1f - regFraction).coerceAtLeast(0.001f))
                            .background(PurpleAccent)
                    )
                }
            }

            // Metric Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Regular
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Regular (Salary)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = SecondaryText,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Text(
                        text = formatAnalyticsLkrAmount(regularVariable.regularMinor),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkNavyText
                        )
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%% of total", SafeCalculations.finiteOrZero(regularVariable.regularPercentage)),
                        style = MaterialTheme.typography.labelSmall.copy(color = EmeraldPrimary)
                    )
                }

                // Variable
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(PurpleAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Variable",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = SecondaryText,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Text(
                        text = formatAnalyticsLkrAmount(regularVariable.variableMinor),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkNavyText
                        )
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%% of total", SafeCalculations.finiteOrZero(regularVariable.variablePercentage)),
                        style = MaterialTheme.typography.labelSmall.copy(color = PurpleAccent)
                    )
                }
            }
        }
    }
}

@Composable
fun TrendChartCard(
    trendPoints: List<IncomeTrendPoint>,
    selectedMonthKey: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "6-Month Realized Trend",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkNavyText
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (trendPoints.isEmpty() || trendPoints.all { it.amountMinor == 0L }) {
                Text("No income trend yet. LKR 0.00", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
            val maxAmount = trendPoints.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L

            // Canvas Bar Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val barCount = trendPoints.size
                if (barCount == 0) return@Canvas

                val spacing = 16.dp.toPx()
                val totalSpacing = spacing * (barCount + 1)
                val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(12.dp.toPx())

                val baselineY = size.height - 18.dp.toPx()
                val maxBarHeight = baselineY - 10.dp.toPx()

                // Baseline line
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1.dp.toPx()
                )

                trendPoints.forEachIndexed { index, point ->
                    val x = spacing + index * (barWidth + spacing)
                    val ratio = (point.amountMinor.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
                    val barHeight = ratio * maxBarHeight
                    val isSelected = point.yearMonth == selectedMonthKey

                    val barColor = if (isSelected) EmeraldPrimary else Color(0xFFCAD7E5)

                    // Draw bar
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, baselineY - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            // Month Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                trendPoints.forEach { point ->
                    val monthAbbr = formatMonthShort(point.yearMonth)
                    val isSelected = point.yearMonth == selectedMonthKey
                    Text(
                        text = monthAbbr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) EmeraldPrimary else SecondaryText
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SourceBreakdownCard(
    summary: MonthlyIncomeSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Realized Sources",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkNavyText
                )
            )

            SourceRow(title = "Salary", amountMinor = summary.salaryMinor, color = EmeraldPrimary)
            HorizontalDivider(color = BorderOutline, thickness = 0.5.dp)
            SourceRow(title = "Freelance (Received)", amountMinor = summary.freelanceMinor, color = BlueAccent)
            HorizontalDivider(color = BorderOutline, thickness = 0.5.dp)
            SourceRow(title = "Google AdSense", amountMinor = summary.adsenseMinor, color = OrangeAccent)
            HorizontalDivider(color = BorderOutline, thickness = 0.5.dp)
            SourceRow(title = "Crypto Net (Gains - Losses)", amountMinor = summary.cryptoNetMinor, color = PurpleAccent)
            HorizontalDivider(color = BorderOutline, thickness = 0.5.dp)
            SourceRow(title = "Other", amountMinor = summary.otherMinor, color = SecondaryText)
        }
    }
}

@Composable
private fun SourceRow(
    title: String,
    amountMinor: Long,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = DarkNavyText
                )
            )
        }

        Text(
            text = formatAnalyticsLkrAmount(amountMinor),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (amountMinor >= 0L) DarkNavyText else ErrorExpense
            )
        )
    }
}

@Composable
fun EstimationComparisonSection(
    comparison: IncomeEstimationComparison?,
    currentEstimateMinor: Long?,
    onApplyEstimate: (Long) -> Unit,
    onClearEstimate: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    var inputAmount by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentEstimateMinor) {
        if (currentEstimateMinor != null) {
            inputAmount = String.format(Locale.US, "%.2f", currentEstimateMinor / 100.0)
        } else {
            inputAmount = ""
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Estimated vs Actual Average",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkNavyText
                    )
                )

                if (currentEstimateMinor != null) {
                    TextButton(onClick = onClearEstimate, enabled = !isSaving) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelMedium.copy(color = SecondaryText)
                        )
                    }
                }
            }

            Text(
                text = "Compares your estimated monthly income against your actual trailing 3-calendar-month average.",
                style = MaterialTheme.typography.bodySmall.copy(color = SecondaryText)
            )

            // Input field to enter estimate
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputAmount,
                    onValueChange = {
                        inputAmount = it
                        inputError = null
                    },
                    label = { Text("Estimated Income (LKR)") },
                    enabled = !isSaving,
                    placeholder = { Text("e.g. 150000.00") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            val minor = parseInputToMinor(inputAmount)
                            if (minor != null) {
                                onApplyEstimate(minor)
                                inputError = null
                            } else {
                                inputError = "Enter valid amount"
                            }
                        }
                    ),
                    isError = inputError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    enabled = !isSaving,
                    onClick = {
                        focusManager.clearFocus()
                        val minor = parseInputToMinor(inputAmount)
                        if (minor != null) {
                            onApplyEstimate(minor)
                            inputError = null
                        } else {
                            inputError = "Enter valid amount"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(text = if (isSaving) "Saving…" else "Compare", fontWeight = FontWeight.Bold)
                }
            }

            inputError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error)
                )
            }

            // Results Card when comparison is active
            if (comparison != null) {
                HorizontalDivider(color = BorderOutline, thickness = 0.5.dp)

                val (statusText, statusBg, statusColor) = when (comparison.status) {
                    EstimationStatus.ABOVE_ESTIMATE -> Triple("Above Estimate", EmeraldPrimaryLight, EmeraldPrimaryDark)
                    EstimationStatus.BELOW_ESTIMATE -> Triple("Below Estimate", Color(0xFFFFEBEE), ErrorExpense)
                    EstimationStatus.ON_TARGET -> Triple("On Target", EmeraldPrimaryLight, EmeraldPrimaryDark)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Comparison Result",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = DarkNavyText
                        )
                    )

                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Grid of metrics
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricComparisonRow(
                        label = "Estimated Income",
                        value = formatAnalyticsLkrAmount(comparison.estimatedMonthlyIncomeMinor)
                    )
                    MetricComparisonRow(
                        label = "Trailing 3-Mo. Average",
                        value = formatAnalyticsLkrAmount(comparison.trailingThreeMonthAverageMinor)
                    )
                    MetricComparisonRow(
                        label = "Variance",
                        value = "${formatAnalyticsLkrAmount(comparison.differenceMinor)} (${String.format(Locale.US, "%.1f%%", SafeCalculations.finiteOrZero(comparison.variancePercentage))})",
                        valueColor = if (comparison.differenceMinor >= 0L) EmeraldPrimary else ErrorExpense
                    )
                }

                if (comparison.monthsEvaluated.isNotEmpty()) {
                    Text(
                        text = "Evaluated months: ${comparison.monthsEvaluated.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall.copy(color = SecondaryText)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricComparisonRow(
    label: String,
    value: String,
    valueColor: Color = DarkNavyText
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = SecondaryText)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

fun formatAnalyticsLkrAmount(amountMinor: Long): String {
    val isNegative = amountMinor < 0L
    val absAmount = BigDecimal.valueOf(amountMinor, 2).abs()
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val prefix = if (isNegative) "- " else ""
    return "${prefix}LKR ${formatter.format(absAmount)}"
}

fun formatMonthLong(yearMonth: YearMonth): String {
    val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.US)
    return "$monthName ${yearMonth.year}"
}

fun formatMonthShort(yearMonthString: String): String {
    return try {
        val ym = YearMonth.parse(yearMonthString.trim())
        ym.month.getDisplayName(TextStyle.SHORT, Locale.US)
    } catch (e: Exception) {
        yearMonthString
    }
}

fun parseInputToMinor(input: String): Long? {
    return try {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val bd = BigDecimal(trimmed)
        if (bd < BigDecimal.ZERO) return null
        bd.movePointRight(2).longValueExact()
    } catch (e: Exception) {
        null
    }
}

// ─────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun IncomeAnalyticsScreenPreview() {
    val sampleState = IncomeAnalyticsUiState(
        isLoading = false,
        selectedMonth = YearMonth.of(2026, 10),
        monthlySummary = MonthlyIncomeSummary(
            yearMonth = "2026-10",
            totalRealizedMinor = 28500000L,
            salaryMinor = 20000000L,
            freelanceMinor = 6500000L,
            adsenseMinor = 1500000L,
            cryptoNetMinor = 500000L,
            otherMinor = 0L
        ),
        regularVariable = RegularVariableIncome(
            regularMinor = 20000000L,
            variableMinor = 8500000L,
            totalMinor = 28500000L,
            regularPercentage = 70.2,
            variablePercentage = 29.8
        ),
        trendPoints = listOf(
            IncomeTrendPoint("2026-05", 22000000L),
            IncomeTrendPoint("2026-06", 24000000L),
            IncomeTrendPoint("2026-07", 25000000L),
            IncomeTrendPoint("2026-08", 26000000L),
            IncomeTrendPoint("2026-09", 27000000L),
            IncomeTrendPoint("2026-10", 28500000L)
        ),
        analyticsSummary = IncomeAnalyticsSummary(
            selectedMonthSummary = null,
            regularVsVariable = RegularVariableIncome(20000000L, 8500000L, 28500000L, 70.2, 29.8),
            trendPoints = emptyList(),
            averageMonthlyIncomeMinor = 25400000L,
            highestIncomeMonth = "2026-10",
            highestIncomeMinor = 28500000L
        ),
        estimationComparison = IncomeEstimationComparison(
            estimatedMonthlyIncomeMinor = 25000000L,
            trailingThreeMonthAverageMinor = 26000000L,
            differenceMinor = 1000000L,
            variancePercentage = 4.0,
            status = EstimationStatus.ABOVE_ESTIMATE,
            monthsEvaluated = listOf("2026-07", "2026-08", "2026-09")
        ),
        estimatedMonthlyIncomeMinor = 25000000L
    )

    IncomeAnalyticsContent(
        uiState = sampleState,
        onPreviousMonth = {},
        onNextMonth = {},
        onSetEstimatedMonthlyIncome = {},
        onClearEstimatedMonthlyIncome = {},
        onClearError = {},
        onBack = {}
    )
}

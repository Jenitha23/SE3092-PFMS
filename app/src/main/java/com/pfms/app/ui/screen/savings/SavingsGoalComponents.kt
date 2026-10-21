package com.pfms.app.ui.screen.savings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.ui.component.EmptyState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.savings.GoalPaceStatus
import com.pfms.app.ui.component.GoalVectorIcon
import com.pfms.app.ui.component.LaptopVectorIcon
import com.pfms.app.ui.component.SavingsVector
import com.pfms.app.ui.theme.DarkNavyText
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.ui.theme.EmeraldPrimaryLight
import com.pfms.app.ui.theme.ErrorExpense
import com.pfms.app.ui.theme.OrangeAccent
import com.pfms.app.ui.theme.PurpleAccent
import com.pfms.app.ui.theme.SecondaryText
import com.pfms.app.ui.theme.SuccessEmerald
import com.pfms.app.viewmodel.SavingsGoalUiState

/**
 * Visual Circular Progress Ring for Savings Goal.
 * Smoothly animates progress and strictly coerces values to [0f, 1f].
 */
@Composable
fun SavingsGoalProgressRing(
    progressPercent: Double,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 110.dp,
    strokeWidth: Dp = 10.dp,
    primaryColor: Color = EmeraldPrimary,
    trackColor: Color = Color(0xFFE2E8F0)
) {
    val targetSweep = SafeCalculations.progressFraction(progressPercent)
    val animatedProgress by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = tween(durationMillis = 800),
        label = "progressSweep"
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Progress: ${(targetSweep * 100).toInt()}%"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // Background circle track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Foreground progress arc
            if (animatedProgress > 0f) {
                drawArc(
                    color = if (isCompleted) SuccessEmerald else primaryColor,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(targetSweep * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = if (isCompleted) SuccessEmerald else MaterialTheme.colorScheme.onSurface
            )
            if (isCompleted) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = SuccessEmerald
                )
            }
        }
    }
}

/**
 * Empty State rendered when no active goal exists.
 */
@Composable
fun SavingsGoalEmptyState(
    onCreateGoalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = EmeraldPrimaryLight
            ) {
                Box(contentAlignment = Alignment.Center) {
                    GoalVectorIcon(modifier = Modifier.size(36.dp), tint = EmeraldPrimary)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "No Active Savings Goal",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set a dedicated savings target, record contributions and withdrawals, and track your pace toward reaching your milestone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Saved ${SavingsGoalUiFormatter.formatLkr(0L)} • Progress 0%",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onCreateGoalClick,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalVectorIcon(modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create Savings Goal",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * Overdue Alert Card (FR-28).
 */
@Composable
fun OverdueAlertCard(
    onUpdateDateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFFF4ED)),
        border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Target Date Passed",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFC45E00)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Your target date has passed. Please update your target date to recalculate monthly savings requirements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkNavyText
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onUpdateDateClick,
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Update Date",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Completed Goal Celebration Banner with safe Archive Goal action (Task 12).
 */
@Composable
fun CompletedCelebrationCard(
    onArchiveGoal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = EmeraldPrimaryLight),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = EmeraldPrimary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "🎉", fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Goal Completed!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldPrimary
                    )
                    Text(
                        text = "Congratulations! You reached 100% of your target. Archive this goal to free your active slot and start a new goal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkNavyText
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onArchiveGoal,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Archive Goal",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Summary Card displaying active savings goal details and progress ring.
 */
@Composable
fun SavingsGoalSummaryCard(
    goal: SavingsGoal,
    uiState: SavingsGoalUiState,
    onEditGoal: () -> Unit,
    onArchiveGoal: () -> Unit,
    onDeleteGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Icon + Name + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            LaptopVectorIcon(tint = DarkNavyText)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Target: ${SavingsGoalUiFormatter.formatLkr(goal.targetAmountMinor)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons: Edit, Archive, Delete
                Row {
                    IconButton(
                        onClick = onEditGoal,
                        modifier = Modifier.semantics { contentDescription = "Edit Goal" }
                    ) {
                        Text(text = "✏️", fontSize = 16.sp)
                    }
                    IconButton(
                        onClick = onArchiveGoal,
                        modifier = Modifier.semantics { contentDescription = "Archive Goal" }
                    ) {
                        Text(text = "📦", fontSize = 16.sp)
                    }
                    IconButton(
                        onClick = onDeleteGoal,
                        modifier = Modifier.semantics { contentDescription = "Delete Goal" }
                    ) {
                        Text(text = "🗑️", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Middle Row: Progress Ring + Main Numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SavingsGoalProgressRing(
                    progressPercent = uiState.progressPercent,
                    isCompleted = uiState.isCompleted
                )

                Spacer(modifier = Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricRow(
                        label = "Saved",
                        value = SavingsGoalUiFormatter.formatLkr(uiState.currentSavedMinor),
                        color = EmeraldPrimary,
                        isBold = true
                    )
                    MetricRow(
                        label = "Remaining",
                        value = SavingsGoalUiFormatter.formatLkr(uiState.remainingAmountMinor),
                        color = if (uiState.isCompleted) SuccessEmerald else DarkNavyText
                    )
                    MetricRow(
                        label = "Target Date",
                        value = SavingsGoalUiFormatter.formatDisplayDate(goal.targetDate)
                    )
                    MetricRow(
                        label = "Time Left",
                        value = if (uiState.isOverdue) "Overdue" else "${uiState.remainingMonths} mo",
                        color = if (uiState.isOverdue) OrangeAccent else DarkNavyText
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom banner: Required monthly saving
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE9EDF2))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Required Monthly Saving",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = SecondaryText
                    )
                    Text(
                        text = if (uiState.isCompleted) "LKR 0.00" else "${SavingsGoalUiFormatter.formatLkr(uiState.requiredMonthlySavingMinor)} / mo",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (uiState.isCompleted) SuccessEmerald else EmeraldPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    color: Color = DarkNavyText,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = color
        )
    }
}

/**
 * Action Buttons for Quick Add Contribution and Withdrawal.
 */
@Composable
fun SavingsGoalActionButtons(
    onAddContributionClick: () -> Unit,
    onAddWithdrawalClick: () -> Unit,
    operationInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onAddContributionClick,
            enabled = !operationInProgress,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text(
                text = "+ Add Contribution",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }

        OutlinedButton(
            onClick = onAddWithdrawalClick,
            enabled = !operationInProgress,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkNavyText),
            border = BorderStroke(1.dp, Color(0xFFD5DFE8)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text(
                text = "− Add Withdrawal",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = DarkNavyText
            )
        }
    }
}

/**
 * Savings Pace Card (FR-26).
 */
@Composable
fun SavingsPaceCard(
    uiState: SavingsGoalUiState,
    modifier: Modifier = Modifier
) {
    val pace = uiState.pace
    val isBehind = pace?.paceStatus == GoalPaceStatus.BEHIND

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SavingsVector(tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Savings Pace",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = if (isBehind) Color(0xFFFFF0ED) else EmeraldPrimaryLight,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isBehind) "BEHIND" else "ON TRACK",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isBehind) ErrorExpense else SuccessEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            MetricRow(
                label = "Required Monthly Saving",
                value = "${SavingsGoalUiFormatter.formatLkr(uiState.requiredMonthlySavingMinor)} / mo",
                isBold = true
            )
            MetricRow(
                label = "Net goal contribution this month",
                value = SavingsGoalUiFormatter.formatLkr(uiState.currentMonthContributionMinor)
            )
            MetricRow(
                label = "Current Month Net Savings",
                value = SavingsGoalUiFormatter.formatLkr(uiState.currentMonthNetSavingsMinor)
            )
            MetricRow(
                label = "Trailing 3-Month Average",
                value = "${SavingsGoalUiFormatter.formatLkr(uiState.trailingThreeMonthNetSavingsMinor)} / mo"
            )

            if (isBehind && (pace?.shortfallMinor ?: 0L) > 0L) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF5F5),
                    border = BorderStroke(1.dp, Color(0xFFFFD6D6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly Shortfall",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = ErrorExpense
                        )
                        Text(
                            text = "- ${SavingsGoalUiFormatter.formatLkr(pace?.shortfallMinor ?: 0L)} / mo",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = ErrorExpense
                        )
                    }
                }
            }

            MetricRow(
                label = "Projected Completion",
                value = if (pace?.projectedCompletionDate != null) {
                    SavingsGoalUiFormatter.formatDisplayDate(pace.projectedCompletionDate.toString())
                } else {
                    "Indefinite (adjust monthly pace)"
                },
                color = if (pace?.projectedCompletionDate != null) EmeraldPrimary else SecondaryText
            )
        }
    }
}

/**
 * Discretionary Spending Impact Card (FR-27).
 */
@Composable
fun DiscretionaryImpactCard(
    uiState: SavingsGoalUiState,
    modifier: Modifier = Modifier
) {
    val impact = uiState.discretionaryImpact

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = PurpleAccent.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "💡", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Discretionary Spending Impact",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = SavingsGoalUiFormatter.formatDiscretionaryImpactSentence(impact, uiState.isCompleted),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = DarkNavyText
            )

            if (impact != null) {
                Spacer(modifier = Modifier.height(2.dp))
                MetricRow(
                    label = "Discretionary Expenses",
                    value = SavingsGoalUiFormatter.formatLkr(impact.discretionaryExpensesMinor),
                    color = PurpleAccent
                )
                MetricRow(
                    label = "Committed Expenses",
                    value = SavingsGoalUiFormatter.formatLkr(impact.committedExpensesMinor),
                    color = SecondaryText
                )
            }
        }
    }
}

/**
 * Transaction Ledger Section (FR-24).
 */
@Composable
fun TransactionLedgerSection(
    transactions: List<GoalTransaction>,
    onEditTransaction: (GoalTransaction) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddContribution: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Transaction History",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (transactions.isEmpty()) {
                EmptyState(
                    title = "No contributions yet",
                    message = "Ledger total LKR 0.00. Add a contribution to start funding your goal.",
                    actionLabel = "Add Contribution",
                    onAction = onAddContribution
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    transactions.forEach { tx ->
                        TransactionRowItem(
                            transaction = tx,
                            onEdit = { onEditTransaction(tx) },
                            onDelete = { onDeleteTransaction(tx.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRowItem(
    transaction: GoalTransaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isContribution = transaction.transactionType == GoalTransactionType.CONTRIBUTION
    val amountColor = if (isContribution) SuccessEmerald else ErrorExpense
    val amountPrefix = if (isContribution) "+ " else "- "

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE9EDF2))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (isContribution) EmeraldPrimaryLight else Color(0xFFFFECEF)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isContribution) "↓" else "↑",
                            fontWeight = FontWeight.Bold,
                            color = if (isContribution) EmeraldPrimary else ErrorExpense,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isContribution) "Contribution" else "Withdrawal",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = DarkNavyText
                    )
                    Text(
                        text = buildString {
                            append(SavingsGoalUiFormatter.formatDisplayDate(transaction.date))
                            if (!transaction.note.isNullOrBlank()) {
                                append(" • ")
                                append(transaction.note)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$amountPrefix${SavingsGoalUiFormatter.formatLkr(transaction.amountMinor)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.semantics { contentDescription = "Edit Transaction" }
                ) {
                    Text(text = "✏️", fontSize = 14.sp)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.semantics { contentDescription = "Delete Transaction" }
                ) {
                    Text(text = "🗑️", fontSize = 14.sp)
                }
            }
        }
    }
}

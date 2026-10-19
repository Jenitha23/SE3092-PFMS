package com.pfms.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.model.SessionState
import com.pfms.app.domain.model.dashboard.ActiveGoalSnapshot
import com.pfms.app.domain.model.dashboard.MonthlySummary
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.ui.component.ArrowDownVector
import com.pfms.app.ui.component.ArrowUpVector
import com.pfms.app.ui.component.BellVectorIcon
import com.pfms.app.ui.component.EmptyState
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.ExpensesNavIcon
import com.pfms.app.ui.component.GoalVectorIcon
import com.pfms.app.ui.component.GoalsNavIcon
import com.pfms.app.ui.component.HomeNavIcon
import com.pfms.app.ui.component.IncomeNavIcon
import com.pfms.app.ui.component.InsightsNavIcon
import com.pfms.app.ui.component.LaptopVectorIcon
import com.pfms.app.ui.component.MonthSelector
import com.pfms.app.ui.component.SavingsVector
import com.pfms.app.ui.component.SplitBar
import com.pfms.app.ui.component.TransactionRow
import com.pfms.app.ui.component.UserVectorIcon
import com.pfms.app.ui.component.formatPercent
import com.pfms.app.ui.screen.income.formatAnalyticsLkrAmount
import com.pfms.app.ui.screen.income.formatMonthLong
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.ui.screen.savings.SavingsGoalUiFormatter
import com.pfms.app.viewmodel.SavingsGoalUiState
import com.pfms.app.viewmodel.SavingsGoalViewModel
import com.pfms.app.ui.theme.ErrorExpense
import com.pfms.app.ui.theme.PurpleAccent
import com.pfms.app.viewmodel.DashboardUiState
import com.pfms.app.viewmodel.DashboardViewModel
import com.pfms.app.viewmodel.SessionViewModel
import com.pfms.app.viewmodel.SettingsViewModel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Modern Fintech Dashboard matching Reference UI 2 exactly:
 * - Emerald top header with user greeting, display name + wave, date, bell, and avatar.
 * - Floating Monthly Overview card (Income ↑, Expenses ↓, Savings).
 * - Spending Breakdown card with two-tone donut chart (Committed 45%, Discretionary 55%).
 * - Dynamic Savings Goal card with reactive target, progress bar, and metrics.
 * - Recent Activity card with Salary (+ LKR 130,000) and Dining (- LKR 2,500).
 * - Pinned 5-tab bottom navigation (Home, Income, Expenses, Goals, Insights).
 * - All authentication, session state, settings navigation, and logout dialogs strictly preserved.
 * Home dashboard (FR-29), backed by [DashboardViewModel]:
 * - selectable month (default: current month, Asia/Colombo),
 * - total income, total expenses and net savings with a surplus/deficit shown as text AND an icon,
 * - spending split (Committed vs Discretionary), the active savings-goal card,
 * - the latest 10 transactions of the month,
 * - a "Quick add" floating button so an expense is three taps from home (FR-14, NFR-01).
 * Authentication, session, settings navigation and the logout warning dialog are unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    savingsGoalViewModel: SavingsGoalViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToIncome: () -> Unit = {},
    onNavigateToAddIncome: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToExpenses: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {}
    onNavigateToAddExpense: () -> Unit = {},
    onQuickAddExpense: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onTransactionClick: (Transaction) -> Unit = {},
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val logoutResult by sessionViewModel.logoutResult.collectAsStateWithLifecycle()
    val savingsGoalUiState by savingsGoalViewModel.uiState.collectAsStateWithLifecycle()
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    var showActionHub by remember { mutableStateOf(false) }

    val authUser = (session as? SessionState.Authenticated)?.user
    val rawName = profile?.displayName?.ifBlank { null }
        ?: authUser?.displayName?.ifBlank { null }
        ?: "User"
    val displayName = rawName.substringBefore("@")
    val logoutError = (logoutResult as? LogoutResult.Failure)?.message

    Scaffold(
        bottomBar = {
            com.pfms.app.ui.component.PfmsBottomNavBar(
                selectedTab = com.pfms.app.ui.component.PfmsNavTab.HOME,
                onHomeClick = { /* Already on Home */ },
                onIncomeClick = onNavigateToIncome,
                onExpensesClick = onNavigateToExpenses,
                onGoalsClick = null,
                onInsightsClick = onNavigateToAnalytics
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showActionHub = true },
                containerColor = EmeraldPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.semantics { contentDescription = "Quick actions" }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Home Tab (Active)
                    DashboardBottomTab(
                        icon = { HomeNavIcon(tint = EmeraldPrimary, filled = true) },
                        label = "Home",
                        isActive = true,
                        onClick = { /* Active destination */ }
                    )

                    // Income Tab
                    DashboardBottomTab(
                        icon = { IncomeNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Income",
                        isActive = false,
                        onClick = onNavigateToIncome
                    )

                    // Expenses Tab
                    DashboardBottomTab(
                        icon = { ExpensesNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Expenses",
                        isActive = false,
                        onClick = onNavigateToExpenses
                    )

                    // Goals Tab
                    DashboardBottomTab(
                        icon = { GoalsNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Goals",
                        isActive = false,
                        onClick = onNavigateToGoals
                    )

                    // Insights Tab (Income Analytics)
                    DashboardBottomTab(
                        icon = { InsightsNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Insights",
                        isActive = false,
                        onClick = onNavigateToAnalytics
                    )
                    Text("+", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Quick Actions", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            DashboardHeader(
                displayName = displayName,
                uiState = uiState,
                onNavigateToSettings = onNavigateToSettings,
                onPreviousMonth = dashboardViewModel::previousMonth,
                onNextMonth = dashboardViewModel::nextMonth,
                onIncomeClick = onNavigateToIncome,
                onExpensesClick = onNavigateToExpenses,
                onNetClick = onNavigateToAnalytics
            )

            Spacer(Modifier.height(16.dp))

            QuickShortcutRow(
                onAddIncome = onNavigateToAddIncome,
                onQuickAddExpense = onQuickAddExpense,
                onAddExpense = onNavigateToAddExpense,
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (logoutError != null) ErrorBanner(message = logoutError)

                if (uiState.errorMessage != null) {
                    ErrorBanner(message = uiState.errorMessage)
                    TextButton(
                        onClick = dashboardViewModel::retry,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Try again", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold) }
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = EmeraldPrimary) }
                } else {
                    val summary = uiState.summary
                    SpendingBreakdownCard(
                        summary = summary,
                        onViewDetails = onNavigateToAnalytics,
                        onQuickAdd = onQuickAddExpense
                    )
                    SavingsGoalCard(goal = uiState.goal)
                    RecentActivityCard(
                        monthLabel = formatMonthLong(uiState.selectedMonth),
                        transactions = uiState.latestTransactions,
                        onViewAll = onNavigateToHistory,
                        onTransactionClick = onTransactionClick,
                        onQuickAdd = onQuickAddExpense
                    )
                }

                // Room for the floating button so it never covers the last card.
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (showActionHub) {
        DashboardQuickActionSheet(
            onDismiss = { showActionHub = false },
            onQuickAddExpense = onQuickAddExpense,
            onFullAddExpense = onNavigateToAddExpense,
            onAddIncome = onNavigateToAddIncome,
            onViewExpenses = onNavigateToExpenses,
            onViewIncome = onNavigateToIncome,
            onViewAnalytics = onNavigateToAnalytics
        )
    }

    // Unsynchronised data warning (unchanged from the auth/session feature).
    if (logoutResult is LogoutResult.UnsynchronizedDataWarning) {
        AlertDialog(
            onDismissRequest = { sessionViewModel.consumeLogoutResult() },
            icon = { Text("\u26A0", style = MaterialTheme.typography.headlineSmall) },
            title = { Text("Unsynchronized Data") },
            text = {
                Text(
                    "You have offline financial entries that have not synced with the cloud yet. " +
                            "Logging out will discard unsaved local changes. Do you still want to log out?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { sessionViewModel.logout(force = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Log Out Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { sessionViewModel.consumeLogoutResult() }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Header + monthly overview
// ─────────────────────────────────────────────────────────────

private val headerDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning,"
    in 12..16 -> "Good afternoon,"
    else -> "Good evening,"
}

@Composable
private fun DashboardHeader(
    displayName: String,
    uiState: DashboardUiState,
    onNavigateToSettings: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onIncomeClick: () -> Unit,
    onExpensesClick: () -> Unit,
    onNetClick: () -> Unit
) {
    // Greeting and date use Asia/Colombo like every other month calculation (NFR-17).
    val now = remember { ZonedDateTime.now(AppClock.zone) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            color = EmeraldPrimary,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greetingFor(now.hour),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD0EDE5)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = now.format(headerDateFormat),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA7DFD0)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) { BellVectorIcon(tint = Color.White) }
                        }
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { onNavigateToSettings() }
                                .semantics { contentDescription = "Open settings and profile" },
                            shape = CircleShape,
                            color = Color.White
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                UserVectorIcon(modifier = Modifier.size(20.dp), tint = EmeraldPrimary)
                            }
                        }
                    }
                }
            }
        }

        MonthlyOverviewCard(
            uiState = uiState,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onIncomeClick = onIncomeClick,
            onExpensesClick = onExpensesClick,
            onNetClick = onNetClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 115.dp)
        )
    }
}

                // ── Card 3: Savings Goal ────────────────────────────────
                DashboardSavingsGoalCard(
                    uiState = savingsGoalUiState,
                    onClick = onNavigateToGoals
                )
@Composable
private fun MonthlyOverviewCard(
    uiState: DashboardUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onIncomeClick: () -> Unit,
    onExpensesClick: () -> Unit,
    onNetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = uiState.summary
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Overview",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            MonthSelector(
                month = uiState.selectedMonth,
                canGoNext = !uiState.isCurrentMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    onClick = onIncomeClick,
                    label = "Income",
                    amountMinor = summary?.totalIncomeMinor ?: 0L,
                    badgeColor = EmeraldPrimary,
                    amountColor = MaterialTheme.colorScheme.onSurface
                ) { ArrowUpVector(tint = Color.White) }
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    onClick = onExpensesClick,
                    label = "Expenses",
                    amountMinor = summary?.totalExpenseMinor ?: 0L,
                    badgeColor = Color(0xFFFFECEF),
                    amountColor = ErrorExpense
                ) { ArrowDownVector(tint = ErrorExpense) }
                OverviewMetric(
                    modifier = Modifier.weight(1f),
                    onClick = onNetClick,
                    label = "Net savings",
                    amountMinor = summary?.netSavingsMinor ?: 0L,
                    badgeColor = Color(0xFFF3E8FF),
                    amountColor = PurpleAccent
                ) { SavingsVector(tint = PurpleAccent) }
            }

            Spacer(Modifier.height(12.dp))
            SurplusDeficitLine(summary)
        }
    }
}

@Composable
private fun OverviewMetric(
    modifier: Modifier,
    onClick: () -> Unit,
    label: String,
    amountMinor: Long,
    badgeColor: Color,
    amountColor: Color,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = badgeColor) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatAnalyticsLkrAmount(amountMinor),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = amountColor
        )
    }
}

/** Surplus or deficit is always words + an arrow icon, never colour alone (FR-29, NFR-21). */
@Composable
private fun SurplusDeficitLine(summary: MonthlySummary?) {
    if (summary == null || !summary.hasData) {
        Text(
            text = "No transactions yet this month. Add one to see your overview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val surplus = summary.isSurplus
    val accent = if (surplus) EmeraldPrimary else ErrorExpense
    val amount = formatAnalyticsLkrAmount(kotlin.math.abs(summary.netSavingsMinor))
    val text = if (surplus) "Surplus of $amount this month" else "Deficit of $amount this month"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (surplus) ArrowUpVector(modifier = Modifier.size(16.dp), tint = accent)
        else ArrowDownVector(modifier = Modifier.size(16.dp), tint = accent)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Cards
// ─────────────────────────────────────────────────────────────

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}

@Composable
private fun CardHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = EmeraldPrimary
                )
            }
        }
    }
}

@Composable
private fun SpendingBreakdownCard(
    summary: MonthlySummary?,
    onViewDetails: () -> Unit,
    onQuickAdd: () -> Unit
) {
    DashboardCard {
        CardHeader("Spending Breakdown", "View details \u2192", onViewDetails)
        Spacer(Modifier.height(8.dp))
        if (summary == null || summary.totalExpenseMinor == 0L) {
            EmptyState(
                title = "No spending recorded",
                message = "Add an expense to see how your month splits between Committed and Discretionary.",
                actionLabel = "Add an expense",
                onAction = onQuickAdd
            )
        } else {
            SplitBar(split = summary.split)
            summary.categories.firstOrNull()?.let { top ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Largest category: ${top.categoryName} (${formatPercent(top.percent)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SavingsGoalCard(goal: ActiveGoalSnapshot?) {
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = EmeraldPrimary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) { GoalVectorIcon(tint = EmeraldPrimary) }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Savings Goal",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(12.dp))

        if (goal == null) {
            EmptyState(
                title = "No savings goal yet",
                message = "Set a goal to see your progress here."
            )
            return@DashboardCard
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LaptopVectorIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Target ${formatAnalyticsLkrAmount(goal.targetAmountMinor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        val progressLabel = if (goal.isCompleted) "Completed \u2022 100%" else "${goal.progressPercent}%"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "${goal.name}: $progressLabel of target saved"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(goal.progressPercent / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(EmeraldPrimary)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${formatAnalyticsLkrAmount(goal.savedAmountMinor.coerceAtLeast(0L))} saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${formatAnalyticsLkrAmount(goal.remainingMinor)} remaining",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentActivityCard(
    monthLabel: String,
    transactions: List<Transaction>,
    onViewAll: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onQuickAdd: () -> Unit
) {
    DashboardCard {
        CardHeader("Recent Activity", "View all \u2192", onViewAll)
        Spacer(Modifier.height(4.dp))
        if (transactions.isEmpty()) {
            EmptyState(
                title = "No transactions in $monthLabel",
                message = "Entries you add will show up here straight away, even offline.",
                actionLabel = "Quick add expense",
                onAction = onQuickAdd
            )
        } else {
            transactions.forEach { tx ->
                TransactionRow(transaction = tx, onClick = { onTransactionClick(tx) })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Quick actions hub (from the team's dashboard, unchanged)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardQuickActionSheet(
    onDismiss: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onFullAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onViewExpenses: () -> Unit,
    onViewIncome: () -> Unit,
    onViewAnalytics: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Quick Actions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Manage transactions & records",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            // Action 1: Quick Add Expense
            DashboardActionCard(
                title = "Quick Add Expense",
                subtitle = "Rapid amount & category entry in 2 taps",
                iconBg = Color(0xFFD1FAE5),
                badge = "Fastest",
                onClick = {
                    onDismiss()
                    onQuickAddExpense()
                }
            ) {
                ArrowDownVector(tint = EmeraldPrimary)
            }

            // Action 2: Full Expense Form
            DashboardActionCard(
                title = "Detailed Expense",
                subtitle = "Add receipts, merchant, classification & notes",
                iconBg = Color(0xFFFFECEF),
                onClick = {
                    onDismiss()
                    onFullAddExpense()
                }
            ) {
                ExpensesNavIcon(tint = Color(0xFFE84C6A))
            }

            // Action 3: Add Income
            DashboardActionCard(
                title = "Add Income",
                subtitle = "Record salary, freelance, or crypto inflows",
                iconBg = Color(0xFFE0F2FE),
                badge = "Income",
                onClick = {
                    onDismiss()
                    onAddIncome()
                }
            ) {
                ArrowUpVector(tint = Color(0xFF0284C7))
            }

            // Action 4: Financial Analytics
            DashboardActionCard(
                title = "Insights & Analytics",
                subtitle = "Review monthly spending breakdown & trends",
                iconBg = Color(0xFFF3E8FF),
                onClick = {
                    onDismiss()
                    onViewAnalytics()
                }
            ) {
                InsightsNavIcon(tint = Color(0xFF7A4DD8))
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    subtitle: String,
    iconBg: Color,
    badge: String? = null,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (badge != null) {
                        Surface(
                            color = EmeraldPrimary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                badge,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** Same three shortcuts the team added to the home screen: + Income, Quick Add, + Expense. */
@Composable
private fun QuickShortcutRow(
    onAddIncome: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShortcutChip("+ Income", Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFF0369A1), onAddIncome, Modifier.weight(1f))
        ShortcutChip("\u26A1 Quick Add", Color(0xFFD1FAE5), Color(0xFFA7F3D0), Color(0xFF065F46), onQuickAddExpense, Modifier.weight(1f))
        ShortcutChip("+ Expense", Color(0xFFFFECEF), Color(0xFFFFCDD2), Color(0xFFBE123C), onAddExpense, Modifier.weight(1f))
    }
}

@Composable
private fun ShortcutChip(
    label: String,
    container: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

/**
 * Dashboard card presenting live active savings goal summary (FR-23 through FR-28).
 */
@Composable
fun DashboardSavingsGoalCard(
    uiState: SavingsGoalUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGoal = uiState.activeGoal

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = EmeraldPrimary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            GoalVectorIcon(tint = EmeraldPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Savings Goal",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (activeGoal != null) "View goal →" else "Set goal →",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = EmeraldPrimary,
                    modifier = Modifier.clickable(onClick = onClick)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (activeGoal == null) {
                // No active goal state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            GoalVectorIcon(tint = Color(0xFF64748B))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No active savings goal",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Set a goal to track your savings progress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Active goal state with live data
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            LaptopVectorIcon(tint = Color(0xFF334155))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = activeGoal.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = SavingsGoalUiFormatter.formatLkr(activeGoal.targetAmountMinor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar & Percentage
                val progressFraction = (uiState.progressPercent / 100.0).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFE2E8F0))
                    ) {
                        if (progressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(EmeraldPrimary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "${uiState.progressPercent.toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Subtext: saved vs remaining
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${SavingsGoalUiFormatter.formatLkr(uiState.currentSavedMinor)} saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (uiState.isCompleted) {
                            "Goal Completed!"
                        } else {
                            "${SavingsGoalUiFormatter.formatLkr(uiState.remainingAmountMinor)} remaining"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isCompleted) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

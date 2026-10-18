package com.pfms.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.ui.theme.EmeraldPrimary

/**
 * 5 Central Navigation destinations across PFMS.
 */
enum class PfmsNavTab {
    HOME,
    INCOME,
    EXPENSES,
    GOALS,
    INSIGHTS
}

/**
 * Unified 5-Tab Bottom Navigation Bar matching the reference UI:
 * Home | Income | Expenses | Goals | Insights
 */
@Composable
fun PfmsBottomNavBar(
    selectedTab: PfmsNavTab,
    onHomeClick: () -> Unit,
    onIncomeClick: () -> Unit,
    onExpensesClick: () -> Unit,
    onGoalsClick: (() -> Unit)? = null,
    onInsightsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home Tab
            val isHome = selectedTab == PfmsNavTab.HOME
            PfmsBottomNavTabItem(
                icon = {
                    HomeNavIcon(
                        tint = if (isHome) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        filled = isHome
                    )
                },
                label = "Home",
                isActive = isHome,
                onClick = onHomeClick
            )

            // 2. Income Tab
            val isIncome = selectedTab == PfmsNavTab.INCOME
            PfmsBottomNavTabItem(
                icon = {
                    IncomeNavIcon(
                        tint = if (isIncome) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                label = "Income",
                isActive = isIncome,
                onClick = onIncomeClick
            )

            // 3. Expenses Tab
            val isExpenses = selectedTab == PfmsNavTab.EXPENSES
            PfmsBottomNavTabItem(
                icon = {
                    ExpensesNavIcon(
                        tint = if (isExpenses) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                label = "Expenses",
                isActive = isExpenses,
                onClick = onExpensesClick
            )

            // 4. Goals Tab
            val isGoals = selectedTab == PfmsNavTab.GOALS
            PfmsBottomNavTabItem(
                icon = {
                    GoalsNavIcon(
                        tint = if (isGoals) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                label = "Goals",
                isActive = isGoals,
                onClick = onGoalsClick
            )

            // 5. Insights Tab
            val isInsights = selectedTab == PfmsNavTab.INSIGHTS
            PfmsBottomNavTabItem(
                icon = {
                    InsightsNavIcon(
                        tint = if (isInsights) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                label = "Insights",
                isActive = isInsights,
                onClick = onInsightsClick
            )
        }
    }
}

@Composable
private fun PfmsBottomNavTabItem(
    icon: @Composable () -> Unit,
    label: String,
    isActive: Boolean,
    onClick: (() -> Unit)?
) {
    val activeColor = EmeraldPrimary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val color = if (isActive) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = onClick != null,
                onClick = { onClick?.invoke() }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = color
        )
    }
}

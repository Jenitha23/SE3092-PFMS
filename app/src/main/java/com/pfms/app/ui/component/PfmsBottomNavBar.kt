package com.pfms.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.ui.theme.PlusJakartaSans

/** Shared destinations for every screen that displays the floating dock. */
enum class PfmsNavTab { HOME, INCOME, EXPENSES, GOALS, INSIGHTS }

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
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val active = if (light) Color(0xFF008660) else Color(0xFF59DFB1)
    val inactive = if (light) Color(0xFF91A1B8) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.widthIn(max = 390.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 3.dp) {
            Row(Modifier.selectableGroup().padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val items = listOf(
                    Triple(PfmsNavTab.HOME, FinanceSymbol.HOME, onHomeClick),
                    Triple(PfmsNavTab.INCOME, FinanceSymbol.INCOME, onIncomeClick),
                    Triple(PfmsNavTab.EXPENSES, FinanceSymbol.EXPENSE, onExpensesClick),
                    Triple(PfmsNavTab.GOALS, FinanceSymbol.GOAL, onGoalsClick),
                    Triple(PfmsNavTab.INSIGHTS, FinanceSymbol.CHART, onInsightsClick)
                )
                items.forEach { (tab, symbol, callback) ->
                    val selected = selectedTab == tab
                    val label = when (tab) {
                        PfmsNavTab.HOME -> "Home"
                        PfmsNavTab.INCOME -> "Income"
                        PfmsNavTab.EXPENSES -> "Expenses"
                        PfmsNavTab.GOALS -> "Goals"
                        PfmsNavTab.INSIGHTS -> "Insights"
                    }
                    Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .selectable(selected = selected, enabled = callback != null, role = Role.Tab,
                            onClick = { callback?.invoke() }),
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) active.copy(alpha = 0.06f) else Color.Transparent,
                        border = if (selected) BorderStroke(1.dp, active.copy(alpha = 0.18f)) else null) {
                        Column(Modifier.heightIn(min = 48.dp).padding(horizontal = 2.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                            Box {
                                FinanceIcon(symbol, Modifier.size(19.dp), tint = if (selected) active else inactive)
                                if (selected) Surface(Modifier.size(4.dp).align(Alignment.TopEnd).offset(x = 4.dp),
                                    color = active, shape = androidx.compose.foundation.shape.CircleShape) {}
                            }
                            BasicText(label, modifier = Modifier.fillMaxWidth(), maxLines = 1,
                                style = TextStyle(fontFamily = PlusJakartaSans, fontSize = 9.5.sp,
                                    lineHeight = 12.sp, textAlign = TextAlign.Center,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) active else inactive),
                                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 9.5.sp, stepSize = .5.sp))
                        }
                    }
                }
            }
        }
    }
}

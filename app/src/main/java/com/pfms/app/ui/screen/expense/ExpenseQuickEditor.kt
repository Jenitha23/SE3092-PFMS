package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.ExpenseDraftState

@Composable
internal fun ExpenseQuickEditor(state: ExpenseDraftState, actions: ExpenseDraftActions) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick Add Expense", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = actions.back, enabled = !state.saving, modifier = Modifier.semantics { contentDescription = "Close quick add" }) { ExpenseIcon(ExpenseSymbol.CLOSE) }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("expense_quick_sections")
            .padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            QuickSpendingOutlook(state)
            ExpenseAmountPanel(state, actions, autofocus = true, showHeading = false)
            QuickCategoryGrid(state, actions)
            FinancePanel {
                ExpenseSectionTitle("Payment & date", ExpenseSymbol.CARD)
                ExpenseBasicFields(state, actions, showCategory = false)
            }
            ExpenseInput(state.note, actions.note, "Optional note", !state.saving, multiline = true)
        }
        FinanceSaveBar(state.saveLabel, state.saving, state.error, actions.save, actions.back)
    }
    ExpenseEditorDialogs(state, actions)
}

@Composable
private fun QuickSpendingOutlook(state: ExpenseDraftState) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.primaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
            if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.15f || state.outlook.length > 22) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Spent this month • ${state.outlookMonth}", style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
                    FinanceValue(state.outlook, color = colors.onPrimaryContainer)
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Spent this month", style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
                    Text(state.outlookMonth, style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
                }
                FinanceValue(state.outlook, Modifier.weight(1.2f), color = colors.onPrimaryContainer, align = TextAlign.End)
            }
        }
    }
}

/** Equal columns and explicit row gaps keep long and short category names aligned. */
@Composable
private fun QuickCategoryGrid(state: ExpenseDraftState, actions: ExpenseDraftActions) {
    val colors = MaterialTheme.colorScheme
    FinancePanel(Modifier.testTag("quick_category_panel")) {
        Text("Category", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.categories.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { option ->
                        val selected = state.category?.id == option.id
                        Surface(selected = selected, onClick = { actions.category(option.id) }, enabled = !state.saving,
                            shape = RoundedCornerShape(12.dp), color = if (selected) colors.primary else colors.surfaceVariant,
                            contentColor = if (selected) colors.onPrimary else colors.onSurface,
                            border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).fillMaxHeight().testTag("quick_category_${option.id}")) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExpenseIcon(categorySymbol(option.name), Modifier.size(18.dp), tint = if (selected) colors.onPrimary else colors.primary)
                                Text(option.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

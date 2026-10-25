package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.*
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.*

@Composable
internal fun CategoryManagementScreen(state: AdvancedExpenseScreenState, busy: Boolean, message: String?, retry: Boolean,
    vm: AdvancedExpenseViewModel, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val editing = state.controls.categoryEditor.original
    LaunchedEffect(editing) { if (editing != null) list.animateScrollToItem(0) }
    Scaffold(Modifier.imePadding(), containerColor = colors.background,
        topBar = { AdvancedExpenseHeader("Categories", "Organize your spending", onBack) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp), color = colors.surfaceVariant) {
                    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false to "Active (${state.activeCategories.size})", true to "Archived (${state.archivedCategories.size})").forEach { (archived, label) ->
                            Surface(onClick = { vm.showArchived(archived) }, shape = RoundedCornerShape(11.dp),
                                color = if (state.controls.archived == archived) colors.surface else colors.surfaceVariant,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                    selected = state.controls.archived == archived
                                    role = Role.Tab
                                },
                                border = if (state.controls.archived == archived) BorderStroke(1.dp, colors.outlineVariant) else null) {
                                Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (state.controls.archived == archived) colors.primary else colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                // Keep errors outside the list so editing a category always reveals the editor at index zero.
                if (message != null) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    AdvancedExpenseError(message, retry, busy, vm::retry, vm::clearMessage)
                }
                LazyColumn(Modifier.fillMaxSize().testTag("category_sections"), state = list,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!state.controls.archived) {
                        item(key = "editor") { CategoryEditor(state.controls.categoryEditor, busy, vm) }
                        item(key = "heading") {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Your categories", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text("${state.customCount} custom · ${state.defaultCount} default", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        items(state.activeCategories, key = { it.id }) { category -> CategoryManagementCard(category, busy, vm) }
                    } else if (state.archivedCategories.isEmpty()) {
                        item { FinancePanel {
                            ExpenseSectionTitle("No archived categories", ExpenseSymbol.ARCHIVE)
                            Text("Categories you archive stay linked to past expenses.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            TextButton(onClick = { vm.showArchived(false) }) { Text("View active categories") }
                        } }
                    } else {
                        item { Text("Archived categories stay on your past expenses.", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                        items(state.archivedCategories, key = { it.id }) { category -> CategoryManagementCard(category, busy, vm) }
                    }
                }
            }
        }
    }
    state.controls.archiveTarget?.let { category ->
        AlertDialog(onDismissRequest = { if (!busy) vm.requestArchive(null) }, title = { Text("Archive Category?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hide '${category.name}' from new expenses? Your past expenses will keep their category.")
                if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = colors.error)
            } }, confirmButton = { TextButton(onClick = vm::archiveSelectedCategory, enabled = !busy) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { vm.requestArchive(null) }, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
private fun CategoryEditor(draft: CategoryEditorState, busy: Boolean, vm: AdvancedExpenseViewModel) = FinancePanel {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseIcon(if (draft.original == null) ExpenseSymbol.ADD else ExpenseSymbol.EDIT, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        Text(draft.original?.let { "Edit Category: ${it.name}" } ?: "Add Custom Category", style = MaterialTheme.typography.titleMedium)
    }
    ExpenseInput(draft.name, vm::categoryName, "Category Name (1–50 characters)", !busy)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseClassification.entries.forEach { classification -> FilterChip(selected = draft.classification == classification,
            onClick = { vm.categoryClassification(classification) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            label = { Text(if (classification == ExpenseClassification.COMMITTED) "Committed" else "Discretionary", style = MaterialTheme.typography.labelMedium) },
            shape = RoundedCornerShape(12.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.primary)) }
    }
    Text("Sets the default spending type for new expenses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    draft.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (draft.original != null) TextButton(onClick = vm::resetCategoryEditor, enabled = !busy) { Text("Cancel") }
        Button(onClick = vm::saveCategoryDraft, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
            Text(if (draft.original == null) "Add Category" else "Save Changes")
        }
    }
}

@Composable
private fun CategoryManagementCard(category: ExpenseCategory, busy: Boolean, vm: AdvancedExpenseViewModel) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().testTag("category_${category.id}"), shape = RoundedCornerShape(18.dp), color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = categoryColor(category.name).copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) { ExpenseIcon(categorySymbol(category.name), Modifier.size(20.dp), categoryColor(category.name)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(category.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (category.classification == ExpenseClassification.COMMITTED) "Committed" else "Discretionary", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (!category.custom) Text("Default · Protected", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            if (category.archived) TextButton(onClick = { vm.restoreCategory(category) }, enabled = !busy) { Text("Restore") }
            else if (category.custom) {
                IconButton(onClick = { vm.editCategory(category) }, enabled = !busy) { FinanceIcon(FinanceSymbol.EDIT, Modifier.size(18.dp), description = "Edit ${category.name}") }
                IconButton(onClick = { vm.requestArchive(category) }, enabled = !busy) {
                    Box(Modifier.clearAndSetSemantics { contentDescription = "Archive ${category.name}" }) { ExpenseIcon(ExpenseSymbol.ARCHIVE, Modifier.size(18.dp)) }
                }
            } else Box(Modifier.padding(horizontal = 14.dp)) { FinanceIcon(FinanceSymbol.LOCK, Modifier.size(16.dp), description = "Protected default category") }
        }
    }
}

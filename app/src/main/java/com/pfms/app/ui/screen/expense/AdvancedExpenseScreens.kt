package com.pfms.app.ui.screen.expense

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.FinanceIcon
import com.pfms.app.ui.component.FinanceSymbol
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.AdvancedExpenseViewModel

/** Route adapters: all draft state, validation and financial rules live in the ViewModel/domain. */
@Composable
fun CategoriesScreen(vm: AdvancedExpenseViewModel, onBack: () -> Unit) = FinanceTheme {
    val state by vm.screen.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val retry by vm.canRetry.collectAsStateWithLifecycle()
    CategoryManagementScreen(state, busy, message, retry, vm, onBack)
}

@Composable
fun RecurringExpensesScreen(vm: AdvancedExpenseViewModel, onBack: () -> Unit, subscriptionsOnly: Boolean = false) = FinanceTheme {
    val state by vm.screen.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val retry by vm.canRetry.collectAsStateWithLifecycle()
    if (state.controls.editor != null) {
        BackHandler { if (!busy) vm.closeTemplate() }
        RecurringTemplateEditor(state, busy, message, retry, vm)
    } else {
        RecurringManagementScreen(state, busy, message, retry, subscriptionsOnly, vm, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedExpenseHeader(title: String, subtitle: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    TopAppBar(title = { Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, navigationIcon = { IconButton(onClick = onBack) { FinanceIcon(FinanceSymbol.BACK, description = "Back") } },
        actions = { action() }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}

@Composable
internal fun AdvancedExpenseError(message: String?, retry: Boolean, busy: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    if (message == null) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (retry) TextButton(onClick = onRetry, enabled = !busy) { Text("Retry") }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Dismiss") }
            }
        }
    }
}

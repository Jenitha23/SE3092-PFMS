package com.pfms.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.MonthlyDataReason
import com.pfms.app.viewmodel.FinancialContextViewModel
import com.pfms.app.viewmodel.MonthlyDataUiState
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MonthlyDataIndicator(month: YearMonth, viewModel: FinancialContextViewModel = hiltViewModel()) {
    LaunchedEffect(month) { viewModel.selectMonth(month) }
    val state by viewModel.monthlyState.collectAsStateWithLifecycle()
    // Do not briefly label a newly selected month with the previous month's status.
    if (state.status?.month == month || state.errorMessage != null) {
        MonthlyDataNotice(state, viewModel::reviewMonth, viewModel::retry)
    }
}

@Composable
fun MonthlyDataNotice(state: MonthlyDataUiState, onReview: (String) -> Unit, onRetry: () -> Unit = {}) {
    val status = state.status
    var confirmationSignature by remember(status?.month, state.userId) { mutableStateOf<String?>(null) }
    if (state.errorMessage != null) ErrorBanner(state.errorMessage, onRetry = onRetry)
    if (status == null || state.isLoading) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (status.isPartial) "Partial Data" else "Reviewed", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(when (status.reason) {
                MonthlyDataReason.NO_ENTRIES -> "No entries are recorded for this month. Review it to confirm whether there was no financial activity."
                MonthlyDataReason.UNREVIEWED -> "Totals use your recorded entries. This month's completeness hasn't been confirmed."
                MonthlyDataReason.MONTH_IN_PROGRESS -> "This month is still in progress. Totals reflect the entries recorded so far."
                MonthlyDataReason.MISSING_CONVERSION -> "Some entries need a saved LKR conversion. Totals include the amounts currently available."
                MonthlyDataReason.INVALID_RECORD -> "Some entries need a valid date or amount before this month can be reviewed."
                MonthlyDataReason.REVIEWED -> if (status.recordCount == 0) "You confirmed no financial activity for this month."
                    else "You confirmed that this month's income and expenses are recorded."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.canReview) TextButton(enabled = !state.isReviewing,
                onClick = { confirmationSignature = status.signature }) {
                Text(if (state.isReviewing) "Saving…" else "Review month")
            }
        }
    }
    confirmationSignature?.let { signature ->
        AlertDialog(onDismissRequest = { confirmationSignature = null }, title = {
            Text("Review ${status.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}")
        }, text = { Text("Confirm that all income and expenses for this month are recorded. If there were no transactions, this confirms no financial activity. Changes to recorded entries will make the month Partial Data again.") },
            confirmButton = { TextButton(onClick = { confirmationSignature = null; onReview(signature) }) { Text("Mark as reviewed") } },
            dismissButton = { TextButton(onClick = { confirmationSignature = null }) { Text("Cancel") } })
    }
}

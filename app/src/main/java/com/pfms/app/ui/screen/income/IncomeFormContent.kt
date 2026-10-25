package com.pfms.app.ui.screen.income

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.IncomeFormState

data class IncomeFormActions(
    val onBack: () -> Unit = {}, val onSave: () -> Unit = {},
    val onAmount: (String) -> Unit = {}, val onSource: (String) -> Unit = {},
    val onCurrency: (String) -> Unit = {}, val onIncrement: (Long) -> Unit = {},
    val onProject: (String) -> Unit = {}, val onStatus: (String) -> Unit = {},
    val onCryptoType: (String) -> Unit = {}, val onAsset: (String) -> Unit = {},
    val onNote: (String) -> Unit = {}, val onToggleNotes: () -> Unit = {},
    val onDatePicker: (Boolean) -> Unit = {}, val onDate: (Long?) -> Unit = {},
    val onInfo: (Boolean) -> Unit = {}, val onManualRate: (String) -> Unit = {},
    val onEnableManualRate: () -> Unit = {}, val onRefreshRate: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeFormContent(state: IncomeFormState, actions: IncomeFormActions = IncomeFormActions(), modifier: Modifier = Modifier) {
    FinanceTheme {
        val colors = MaterialTheme.colorScheme
        BackHandler { if (!state.isSaving) actions.onBack() }
        Scaffold(modifier = modifier.imePadding(), containerColor = colors.background,
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text(state.title, style = MaterialTheme.typography.titleLarge)
                        Text("Record incoming cashflow", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }, navigationIcon = {
                    IconButton(onClick = actions.onBack, enabled = !state.isSaving) {
                        FinanceIcon(FinanceSymbol.BACK, description = "Back")
                    }
                }, actions = {
                    TextButton(onClick = { actions.onInfo(true) }) { Text("Help", style = MaterialTheme.typography.labelMedium) }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
            },
            bottomBar = {
                Surface(color = colors.surface, shadowElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        state.error?.let {
                            Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall, color = colors.error)
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("income_form_actions"), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = actions.onBack, enabled = !state.isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Cancel")
                            }
                            Button(onClick = actions.onSave, enabled = !state.isSaving, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                                else {
                                    Text("✓", Modifier.padding(end = 8.dp))
                                    Text(state.saveLabel)
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .testTag("income_form_sections").padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IncomeFormLabel("INCOME SOURCE")
                        val sourceScroll = rememberLazyListState(initialFirstVisibleItemIndex = state.sourceIndex)
                        LaunchedEffect(state.sourceIndex) { sourceScroll.animateScrollToItem(state.sourceIndex) }
                        LazyRow(Modifier.fillMaxWidth(), state = sourceScroll, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.sources, key = { it.value }) { source ->
                                FilterChip(selected = state.source == source.value, onClick = { actions.onSource(source.value) },
                                    enabled = !state.isSaving, modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text(source.label, style = MaterialTheme.typography.labelLarge) },
                                    leadingIcon = { IncomeRecordIconImage(source.icon, if (state.source == source.value) colors.primary else colors.onSurfaceVariant) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer,
                                        selectedLabelColor = colors.primary, containerColor = colors.surface))
                            }
                        }
                    }
                    IncomeFormAmountCard(state, actions)
                    IncomeFormCard {
                        IncomeFormLabel("DATE RECEIVED")
                        Surface(onClick = { actions.onDatePicker(true) }, enabled = !state.isSaving,
                            shape = RoundedCornerShape(12.dp), color = colors.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IncomeCalendarIcon(tint = colors.primary, modifier = Modifier.size(20.dp))
                                Text(state.dateLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                FinanceIcon(FinanceSymbol.DOWN, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.isFreelance) IncomeFreelanceFields(state, actions)
                    if (state.isCrypto) IncomeCryptoFields(state, actions)
                    IncomeFormCard {
                        Surface(onClick = actions.onToggleNotes, color = colors.surface, enabled = !state.isSaving) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FinanceIcon(FinanceSymbol.EDIT, Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Notes", style = MaterialTheme.typography.titleSmall)
                                    Text("Optional details for this entry", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                FinanceIcon(if (state.notesExpanded) FinanceSymbol.MINUS else FinanceSymbol.PLUS, Modifier.size(18.dp))
                            }
                        }
                        if (state.notesExpanded) IncomeFormTextField(state.note, actions.onNote, "Add a note", "Income note",
                            enabled = !state.isSaving, singleLine = false)
                    }
                }
            }
        }
        if (state.datePickerOpen) {
            val picker = rememberDatePickerState(initialSelectedDateMillis = state.dateMillis)
            DatePickerDialog(onDismissRequest = { actions.onDatePicker(false) },
                confirmButton = { TextButton(onClick = { actions.onDate(picker.selectedDateMillis) }, enabled = picker.selectedDateMillis != null) { Text("Confirm") } },
                dismissButton = { TextButton(onClick = { actions.onDatePicker(false) }) { Text("Cancel") } }) {
                DatePicker(state = picker)
            }
        }
        if (state.infoOpen) AlertDialog(onDismissRequest = { actions.onInfo(false) },
            title = { Text("Recording income") },
            text = { Text("Choose a source and the date the income was received. For freelance work, select Expected until payment arrives. Record crypto gains or losses using a positive amount. USD entries use a dated or manual exchange rate; the saved LKR conversion stays fixed.") },
            confirmButton = { TextButton(onClick = { actions.onInfo(false) }) { Text("Got it") } })
    }
}

@Composable
internal fun IncomeFormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun IncomeFormLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

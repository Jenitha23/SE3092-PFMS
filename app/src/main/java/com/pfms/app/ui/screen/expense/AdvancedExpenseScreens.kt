package com.pfms.app.ui.screen.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.pfms.app.ui.component.EmptyState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.ExpenseRules
import com.pfms.app.domain.usecase.RecurringExpenseCalculator
import com.pfms.app.viewmodel.AdvancedExpenseViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction

@Composable
private fun ClassificationChooser(
    selected: ExpenseClassification,
    onChanged: (ExpenseClassification) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseClassification.entries.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onChanged(value) },
                label = { Text(if (value == ExpenseClassification.COMMITTED) "Committed" else "Discretionary") }
            )
        }
    }
}

// =========================================================================
// FR-16 & FR-18: CATEGORIES SCREEN
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: AdvancedExpenseViewModel, onBack: () -> Unit) {
    ExpenseUiTheme { CategoriesScreenContent(vm, onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesScreenContent(vm: AdvancedExpenseViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val canRetry by vm.canRetry.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Archived
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var classification by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ExpenseClassification.DISCRETIONARY) }
    var editingCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var categoryToArchive by remember { mutableStateOf<ExpenseCategory?>(null) }
    val activeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val activeCategories = categories.filter { !it.archived }.sortedWith(compareBy<ExpenseCategory> { it.custom }.thenBy { it.name })
    val archivedCategories = categories.filter { it.archived }.sortedBy { it.name }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExpenseHeader("Categories", "Give every expense a place.", onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active (${activeCategories.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Archived (${archivedCategories.size})") }
                )
            }

            AnimatedVisibility(visible = message != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (canRetry && !busy) TextButton(onClick = vm::retry) { Text("Retry") }
                        TextButton(onClick = { vm.clearMessage() }) { Text("Dismiss") }
                    }
                }
            }

            if (selectedTab == 0) {
                // Active Categories Tab
                LazyColumn(
                    state = activeListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("YOUR CATEGORIES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Organized spending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Choose a default or make it your own.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ExpenseBadge("${activeCategories.size} active")
                        }
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExpenseSectionTitle(if (editingCategory == null) "Add Custom Category" else "Edit Category: ${editingCategory?.name}", ExpenseSymbol.GRID)

                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { if (it.length <= 50) name = it },
                                    label = { Text("Category Name (1–50 characters)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Default Classification:", style = MaterialTheme.typography.bodySmall)
                                ClassificationChooser(classification) { classification = it }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !busy && name.trim().isNotBlank(),
                                        onClick = {
                                            val current = editingCategory
                                            if (current == null) {
                                                vm.createCategory(name, classification) {
                                                    name = ""
                                                    editingCategory = null
                                                }
                                            } else {
                                                vm.updateCategory(current.copy(name = name.trim(), classification = classification)) {
                                                    name = ""
                                                    editingCategory = null
                                                }
                                            }
                                        }
                                    ) {
                                        Text(if (editingCategory == null) "Add Category" else "Save Changes")
                                    }

                                    if (editingCategory != null) {
                                        OutlinedButton(onClick = {
                                            editingCategory = null
                                            name = ""
                                            classification = ExpenseClassification.DISCRETIONARY
                                        }) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(activeCategories, key = { it.id }) { cat ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryAvatar(cat.name)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ExpenseBadge(if (cat.classification == ExpenseClassification.COMMITTED) "Committed" else "Discretionary",
                                            if (cat.classification == ExpenseClassification.COMMITTED) ExpenseOrange else ExpensePurple)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(if (cat.custom) "Custom category" else "Default · Protected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (cat.custom) {
                                    Row {
                                        IconButton(modifier = Modifier.semantics { contentDescription = "Edit ${cat.name}" }, onClick = {
                                            editingCategory = cat
                                            name = cat.name
                                            classification = cat.classification
                                            scope.launch { activeListState.animateScrollToItem(0) }
                                        }) {
                                            ExpenseIcon(ExpenseSymbol.EDIT, Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(modifier = Modifier.semantics { contentDescription = "Archive ${cat.name}" }, onClick = { categoryToArchive = cat }) {
                                            ExpenseIcon(ExpenseSymbol.ARCHIVE, Modifier.size(19.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Archived Categories Tab
                if (archivedCategories.isEmpty()) {
                    EmptyState(
                        title = "No archived categories",
                        message = "Your active categories are ready to organise expenses.",
                        actionLabel = "View active categories",
                        onAction = { selectedTab = 0 }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(archivedCategories, key = { it.id }) { cat ->
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${cat.classification.name} • Archived",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        enabled = !busy,
                                        onClick = { vm.updateCategory(cat.copy(archived = false)) }
                                    ) {
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    categoryToArchive?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToArchive = null },
            title = { Text("Archive Category?") },
            text = {
                Text("Archiving '${cat.name}' will hide it from new expense forms, but all historical expenses will keep their category name.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToArchive = null
                        vm.updateCategory(cat.copy(archived = true))
                    }
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToArchive = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =========================================================================
// FR-19: RECURRING EXPENSES SCREEN
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(
    vm: AdvancedExpenseViewModel,
    onBack: () -> Unit,
    subscriptionsOnly: Boolean = false
) {
    ExpenseUiTheme { RecurringExpensesScreenContent(vm, onBack, subscriptionsOnly) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurringExpensesScreenContent(vm: AdvancedExpenseViewModel, onBack: () -> Unit, subscriptionsOnly: Boolean) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val totalMonthly by vm.monthlySubscriptionMinor.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val canRetry by vm.canRetry.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<RecurringExpense?>(null) }
    var newTemplateId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var templateToDelete by remember { mutableStateOf<RecurringExpense?>(null) }
    var confirmDueItem by remember { mutableStateOf<RecurringExpense?>(null) }
    var adjustedAmount by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    // Form fields
    var title by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var amountText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var due by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE).toString()) }
    var reminder by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var recurrence by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Recurrence.MONTHLY) }
    var flag by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SubscriptionFlag.NONE) }
    var paymentMethod by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ExpensePaymentMethod.CASH) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }

    val dueInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(dueInteractionSource) {
        dueInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showDueDatePicker = true
            }
        }
    }

    val reminderInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(reminderInteractionSource) {
        reminderInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showReminderDatePicker = true
            }
        }
    }

    val activeCategories = categories.filter { !it.archived }
    val currentSelectedCategory = activeCategories.firstOrNull { it.id == selectedCategoryId }
    val amountMinor = ExpenseRules.parseAmount(amountText)
    val validDueDate = runCatching { LocalDate.parse(due.trim()) }.isSuccess
    val validReminderDate = reminder.isBlank() || runCatching { LocalDate.parse(reminder.trim()) }.isSuccess

    val relevantTemplates = templates.filter { it.subscription == subscriptionsOnly }
    val dueTemplates = relevantTemplates.filter { vm.isDue(it) }
    val reviewTemplates = if (subscriptionsOnly) relevantTemplates.filter { vm.isReviewDue(it) } else emptyList()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExpenseHeader(if (subscriptionsOnly) "Subscriptions" else "Recurring Expenses",
                if (subscriptionsOnly) "Keep track of what renews." else "Your routine, with fewer surprises.", onBack,
                action = {
                    FilledTonalButton(onClick = {
                        editingTemplate = null
                        newTemplateId = UUID.randomUUID().toString()
                        title = ""
                        amountText = ""
                        due = LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE).toString()
                        reminder = ""
                        selectedCategoryId = activeCategories.firstOrNull()?.id.orEmpty()
                        recurrence = Recurrence.MONTHLY
                        flag = SubscriptionFlag.NONE
                        paymentMethod = ExpensePaymentMethod.CASH
                        showForm = !showForm
                    }) {
                        Text(if (showForm) "Close Form" else "+ New")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Error banner
            if (message != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message ?: "", color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            if (canRetry && !busy) TextButton(onClick = vm::retry) { Text("Retry") }
                            TextButton(onClick = { vm.clearMessage() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            // FR-20: Subscriptions Monthly Total Card
            if (!subscriptionsOnly) {
                item {
                    ExpensePanel {
                        ExpenseSectionTitle("Stay one step ahead", ExpenseSymbol.REPEAT)
                        Text("Create a template once. Confirm each payment when it is due.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpenseBadge("${relevantTemplates.count { it.active }} active")
                            ExpenseBadge("${dueTemplates.size} due", ExpenseOrange)
                        }
                    }
                }
            }
            if (subscriptionsOnly) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "TOTAL MONTHLY SUBSCRIPTIONS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                totalMonthly?.let { "LKR " + String.format(Locale.US, "%,.2f", it / 100.0) } ?: "Unavailable",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Active subscriptions, shown as a monthly estimate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // FR-20: Subscriptions Due for Review Prompt (Prominently placed at the top of Subscriptions)
            if (subscriptionsOnly && reviewTemplates.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Subscriptions to Review (${reviewTemplates.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                ExpenseBadge("Review Due", ExpenseOrange)
                            }
                            Text(
                                "These subscriptions have reached their reminder date or are flagged for review. Keep, cancel, or edit them below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            reviewTemplates.forEach { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "LKR ${String.format(Locale.US, "%.2f", item.amountMinor / 100.0)} • ${item.categoryName}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            if (item.reminderDate.isNotBlank()) {
                                                Text("Reminder: ${item.reminderDate}", style = MaterialTheme.typography.labelSmall, color = ExpenseOrange)
                                            }
                                            if (item.flag == SubscriptionFlag.REVIEW) {
                                                Text("Status: Flagged for review", style = MaterialTheme.typography.labelSmall, color = ExpenseOrange)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = {
                                            editingTemplate = item
                                            title = item.title
                                            amountText = String.format(Locale.US, "%.2f", item.amountMinor / 100.0)
                                            due = item.nextDueDate
                                            reminder = item.reminderDate
                                            selectedCategoryId = item.categoryId
                                            recurrence = item.recurrence
                                            flag = item.flag
                                            paymentMethod = item.paymentMethod
                                            showForm = true
                                        }) {
                                            Text("Edit")
                                        }

                                        TextButton(
                                            enabled = !busy,
                                            onClick = { vm.cancelSubscription(item) }
                                        ) {
                                            Text("Cancel Sub")
                                        }

                                        FilledTonalButton(
                                            enabled = !busy,
                                            onClick = { vm.dismissReview(item) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Keep")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // FR-19 Part C: DUE RECURRING EXPENSES PROMPT
            if (dueTemplates.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Due for Confirmation (${dueTemplates.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "These recurring items have reached their scheduled date. Tap to confirm and record them in your expense ledger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )

                            dueTemplates.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "LKR ${String.format(Locale.US, "%.2f", item.amountMinor / 100.0)} • ${item.categoryName}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text("Due: ${item.nextDueDate}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            confirmDueItem = item
                                            adjustedAmount = String.format(Locale.US, "%.2f", item.amountMinor / 100.0)
                                        }
                                    ) {
                                        Text("Confirm")
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Add/Edit Template Form
            if (showForm) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (editingTemplate == null) "New Recurring Template" else "Edit Template",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Template Name / Merchant") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Amount in LKR (e.g. 1500.00)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Category Selector
                            var catExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { catExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Category: ${currentSelectedCategory?.name ?: "Select Category"}")
                                }
                                DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                    activeCategories.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c.name) },
                                            onClick = {
                                                selectedCategoryId = c.id
                                                catExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Payment Method
                            var payExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { payExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Payment: ${paymentMethod.name.replace("_", " ")}")
                                }
                                DropdownMenu(expanded = payExpanded, onDismissRequest = { payExpanded = false }) {
                                    ExpensePaymentMethod.entries.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name.replace("_", " ")) },
                                            onClick = {
                                                paymentMethod = p
                                                payExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Recurrence
                            Text("Recurrence Frequency:", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Recurrence.entries.forEach { r ->
                                    FilterChip(
                                        selected = recurrence == r,
                                        onClick = { recurrence = r },
                                        label = { Text(r.name) }
                                    )
                                }
                            }

                            // Next Due Date with Interactive DatePicker
                            OutlinedTextField(
                                value = due,
                                onValueChange = {},
                                readOnly = true,
                                interactionSource = dueInteractionSource,
                                label = { Text("Next Due Date (YYYY-MM-DD)") },
                                trailingIcon = {
                                    IconButton(onClick = { showDueDatePicker = true }) {
                                        ExpenseIcon(ExpenseSymbol.CALENDAR, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                                    }
                                },
                                isError = !validDueDate,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (subscriptionsOnly) {
                                // Review Reminder Date with Interactive DatePicker
                                OutlinedTextField(
                                    value = reminder,
                                    onValueChange = {},
                                    readOnly = true,
                                    interactionSource = reminderInteractionSource,
                                    label = { Text("Review Reminder Date (YYYY-MM-DD, optional)") },
                                    placeholder = { Text("Tap calendar to select date") },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (reminder.isNotBlank()) {
                                                IconButton(onClick = { reminder = "" }) {
                                                    ExpenseIcon(ExpenseSymbol.CLOSE, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            IconButton(onClick = { showReminderDatePicker = true }) {
                                                ExpenseIcon(ExpenseSymbol.CALENDAR, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    isError = !validReminderDate,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Subscription Review Flag:", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SubscriptionFlag.entries.forEach { sf ->
                                        FilterChip(
                                            selected = flag == sf,
                                            onClick = { flag = sf },
                                            label = { Text(sf.name) }
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    enabled = !busy && title.trim().isNotBlank() && amountMinor != null && currentSelectedCategory != null && validDueDate && validReminderDate,
                                    onClick = {
                                        val cat = currentSelectedCategory ?: return@Button
                                        val amt = amountMinor ?: return@Button
                                        val template = RecurringExpense(
                                            id = editingTemplate?.id ?: newTemplateId,
                                            title = title.trim(),
                                            categoryId = cat.id,
                                            categoryName = cat.name,
                                            classification = cat.classification,
                                            amountMinor = amt,
                                            paymentMethod = paymentMethod,
                                            recurrence = recurrence,
                                            nextDueDate = due.trim(),
                                            active = editingTemplate?.active ?: true,
                                            subscription = subscriptionsOnly,
                                            flag = flag,
                                            reminderDate = reminder.trim()
                                        )
                                        vm.saveTemplate(template) {
                                            showForm = false
                                            editingTemplate = null
                                        }
                                    }
                                ) {
                                    Text(if (editingTemplate == null) "Save Template" else "Update Template")
                                }

                                OutlinedButton(onClick = {
                                    showForm = false
                                    editingTemplate = null
                                }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            // Template list
            if (relevantTemplates.isEmpty() && !showForm) {
                item {
                    EmptyState(
                        title = if (subscriptionsOnly) "No subscriptions added yet" else "No recurring expenses yet",
                        message = "Add a template to track upcoming payments and reminders.",
                        actionLabel = if (subscriptionsOnly) "Add Subscription" else "Add Recurring Expense",
                        onAction = { editingTemplate = null; showForm = true }
                    )
                }
            } else {
                items(relevantTemplates, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CategoryAvatar(item.categoryName)
                                Spacer(Modifier.width(10.dp))
                                Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "LKR ${String.format(Locale.US, "%.2f", item.amountMinor / 100.0)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                "${item.categoryName} • ${item.recurrence.name} • ${item.paymentMethod.name.replace("_", " ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (subscriptionsOnly && item.reminderDate.isNotBlank()) {
                                Text("Review reminder: ${item.reminderDate}", style = MaterialTheme.typography.bodySmall)
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ExpenseBadge(if (item.active) "Active" else "Paused / Cancelled")
                                if (subscriptionsOnly && item.flag != SubscriptionFlag.NONE) {
                                    ExpenseBadge("Flag: ${item.flag.name}", ExpenseOrange)
                                }
                                if (subscriptionsOnly && vm.isReviewDue(item)) {
                                    ExpenseBadge("Review Due", ExpenseOrange)
                                }
                                ExpenseBadge("Next: ${item.nextDueDate}")
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    editingTemplate = item
                                    title = item.title
                                    amountText = String.format(Locale.US, "%.2f", item.amountMinor / 100.0)
                                    due = item.nextDueDate
                                    reminder = item.reminderDate
                                    selectedCategoryId = item.categoryId
                                    recurrence = item.recurrence
                                    flag = item.flag
                                    paymentMethod = item.paymentMethod
                                    showForm = true
                                }) {
                                    Text("Edit")
                                }

                                TextButton(
                                    enabled = !busy,
                                    onClick = { vm.toggleActive(item) }
                                ) {
                                    Text(if (item.active) "Pause" else "Resume")
                                }

                                if (subscriptionsOnly && item.active) {
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { vm.cancelSubscription(item) }
                                    ) {
                                        Text("Cancel Sub")
                                    }
                                }

                                TextButton(
                                    enabled = !busy,
                                    onClick = { templateToDelete = item }
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for One-Tap Due Confirmation
    confirmDueItem?.let { template ->
        AlertDialog(
            onDismissRequest = { confirmDueItem = null },
            title = { Text("Confirm Recurring Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.pfms.app.ui.component.ErrorBanner(message)
                    Text("Confirm recording of '${template.title}' into your expense records?")
                    Text("Category: ${template.categoryName}")
                    Text("Due Date: ${template.nextDueDate}")
                    OutlinedTextField(
                        value = adjustedAmount,
                        onValueChange = { adjustedAmount = it },
                        label = { Text("Amount (LKR)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && ExpenseRules.parseAmount(adjustedAmount) != null,
                    onClick = {
                        val parsed = ExpenseRules.parseAmount(adjustedAmount) ?: template.amountMinor
                        val updated = template.copy(amountMinor = parsed)
                        vm.confirm(updated) { confirmDueItem = null }
                    }
                ) {
                    Text(if (busy) "Saving…" else if (canRetry && message != null) "Retry" else "Confirm & Record")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDueItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Template Dialog
    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Delete Template?") },
            text = {
                Text("Are you sure you want to delete '${template.title}'? This will not delete previously recorded expenses.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = template.id
                        templateToDelete = null
                        vm.deleteTemplate(id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDueDatePicker) {
        val initialDate = runCatching { LocalDate.parse(due.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let {
                            due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        }
                        showDueDatePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(picker)
        }
    }

    if (showReminderDatePicker) {
        val initialDate = runCatching {
            if (reminder.isNotBlank()) LocalDate.parse(reminder.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            else LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        DatePickerDialog(
            onDismissRequest = { showReminderDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let {
                            reminder = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        }
                        showReminderDatePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(picker)
        }
    }
}


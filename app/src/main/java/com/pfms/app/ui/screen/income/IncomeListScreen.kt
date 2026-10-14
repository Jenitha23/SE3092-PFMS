package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.model.Income
import com.pfms.app.ui.theme.DarkNavyText
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.ui.theme.SecondaryText
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeListScreen(
    incomes: List<Income>,
    onAddIncome: () -> Unit,
    onEditIncome: (String) -> Unit,
    onDeleteIncome: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRecurringSalary: () -> Unit = {},
    // Member 2: Realized Net Income summary
    totalRealizedIncomeMinor: Long = 0L,
    monthlyRealizedIncomeMinor: Long = 0L,
    // Member 2: Freelance summary
    totalPendingFreelanceMinor: Long = 0L,
    pendingFreelanceCount: Int = 0,
    // Member 2: Crypto summary
    cryptoGainsMinor: Long = 0L,
    cryptoLossesMinor: Long = 0L,
    netCryptoMinor: Long = 0L,
    ytdNetCryptoMinor: Long = 0L,
    // Member 2: Mark freelance as received
    onMarkFreelanceReceived: (String) -> Unit = {}
) {
    var incomeToDelete by remember {
        mutableStateOf<Income?>(null)
    }

    // Palette tokens
    val cardBackground = Color.White
    val borderLight = Color(0xFFE9EDF2)
    val textPrimary = DarkNavyText
    val textSecondary = SecondaryText
    val brandEmerald = EmeraldPrimary

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFFF7F9FC),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Income Management",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "Back",
                            fontWeight = FontWeight.Bold,
                            color = brandEmerald,
                            fontSize = 15.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7F9FC)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddIncome,
                text = {
                    Text(
                        text = "+ Add Income",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                },
                icon = {},
                containerColor = brandEmerald,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
        ) {

            // ═════════════════════════════════════════════════════════
            // 1. TOTAL REALIZED INCOME CARD (FR-08 & FR-09)
            // ═════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    border = BorderStroke(1.dp, borderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL REALIZED INCOME",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = textSecondary
                                )
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFD1FAE5)
                            ) {
                                Text(
                                    text = "Actual Net",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF065F46)
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = formatLkrAmount(totalRealizedIncomeMinor),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = brandEmerald
                            )
                        )

                        Text(
                            text = "This month's realized net: ${formatLkrAmount(monthlyRealizedIncomeMinor)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold, color = textPrimary
                            )
                        )

                        Text(
                            text = "Excludes pending freelance invoices • Deducts crypto losses",
                            style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = borderLight
                        )

                        // Breakdown lines
                        if (cryptoLossesMinor > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Crypto Losses Deducted",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB91C1C))
                                )
                                Text(
                                    text = "-${formatLkrAmount(cryptoLossesMinor)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB91C1C)
                                    )
                                )
                            }
                        }

                        if (pendingFreelanceCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Outstanding Invoices (Excluded)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB45309))
                                )
                                Text(
                                    text = formatLkrAmount(totalPendingFreelanceMinor),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════
            // 2. OUTSTANDING FREELANCE INVOICES CARD (FR-08)
            // ═════════════════════════════════════════════════════════
            if (pendingFreelanceCount > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Outstanding Freelance Invoices",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "$pendingFreelanceCount Pending",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E)
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = formatLkrAmount(totalPendingFreelanceMinor),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            )

                            Text(
                                text = "These payments are marked Expected and excluded from actual income until marked Received.",
                                style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                            )
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════
            // 3. CRYPTOCURRENCY P&L CARD (FR-09)
            // ═════════════════════════════════════════════════════════
            if (cryptoGainsMinor > 0 || cryptoLossesMinor > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        border = BorderStroke(1.dp, borderLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cryptocurrency Income & P&L",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF3E8FF)
                                ) {
                                    Text(
                                        text = "Crypto Portfolio",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF7E22CE)
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Gains", style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary))
                                Text(
                                    text = "+${formatLkrAmount(cryptoGainsMinor)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = brandEmerald
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Losses (Deducted)", style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary))
                                Text(
                                    text = "-${formatLkrAmount(cryptoLossesMinor)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB91C1C)
                                    )
                                )
                            }

                            HorizontalDivider(color = borderLight)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Net Crypto Income",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = if (netCryptoMinor >= 0) "+${formatLkrAmount(netCryptoMinor)}"
                                    else "-${formatLkrAmount(-netCryptoMinor)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (netCryptoMinor >= 0) brandEmerald else Color(0xFFB91C1C)
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Year-to-Date (YTD) P&L",
                                    style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                                )
                                Text(
                                    text = if (ytdNetCryptoMinor >= 0) "+${formatLkrAmount(ytdNetCryptoMinor)}"
                                    else "-${formatLkrAmount(-ytdNetCryptoMinor)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (ytdNetCryptoMinor >= 0) brandEmerald else Color(0xFFB91C1C)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════
            // 4. RECURRING SALARY CARD
            // ═════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    border = BorderStroke(1.dp, borderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Recurring Salary Template",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        )

                        Text(
                            text = "Set your expected monthly salary and verify payments received each month.",
                            style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                        )

                        OutlinedButton(
                            onClick = onRecurringSalary,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, borderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Manage Salary Template",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                            )
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════
            // 5. TRANSACTION HISTORY HEADING
            // ═════════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction History",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )
                    Text(
                        text = "${incomes.size} records",
                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                    )
                }
            }

            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            when {
                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = brandEmerald)
                        }
                    }
                }

                incomes.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "No Income Records Yet",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add salary, freelance projects, or crypto transactions.",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onAddIncome,
                                    colors = ButtonDefaults.buttonColors(containerColor = brandEmerald),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Add First Income")
                                }
                            }
                        }
                    }
                }

                else -> {
                    items(
                        items = incomes.sortedWith(
                            compareByDescending<Income> { it.receivedDate }
                                .thenByDescending { it.createdAt }
                        ),
                        key = { it.id }
                    ) { income ->
                        IncomeRecordCard(
                            income = income,
                            onEdit = { onEditIncome(income.id) },
                            onDelete = { incomeToDelete = income },
                            onMarkReceived = { onMarkFreelanceReceived(income.id) }
                        )
                    }
                }
            }
        }
    }

    incomeToDelete?.let { income ->
        AlertDialog(
            onDismissRequest = { incomeToDelete = null },
            title = { Text("Delete Income Record?") },
            text = { Text("Are you sure you want to delete this income record? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteIncome(income.id)
                        incomeToDelete = null
                    }
                ) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { incomeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun IncomeRecordCard(
    income: Income,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkReceived: () -> Unit = {}
) {
    val isFreelanceExpected = income.source == "Freelance" && income.paymentStatus == "Expected"
    val isCryptoLoss = income.source == "Crypto" && income.cryptoType == "Loss"

    val textPrimary = DarkNavyText
    val textSecondary = SecondaryText
    val brandEmerald = EmeraldPrimary
    val borderLight = Color(0xFFE9EDF2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            if (isFreelanceExpected) Color(0xFFFDE68A)
            else if (isCryptoLoss) Color(0xFFFECACA)
            else borderLight
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Category & Status Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = income.source,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        )

                        // Freelance badge
                        if (income.source == "Freelance") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isFreelanceExpected) Color(0xFFFEF3C7) else Color(0xFFD1FAE5)
                            ) {
                                Text(
                                    text = if (isFreelanceExpected) "Pending Invoice" else "Received",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFreelanceExpected) Color(0xFF92400E) else Color(0xFF065F46)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Crypto badge
                        if (income.source == "Crypto" && income.cryptoType != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (income.cryptoType == "Loss") Color(0xFFFEE2E2) else Color(0xFFD1FAE5)
                            ) {
                                Text(
                                    text = if (income.cryptoType == "Loss") "Loss" else "Gain",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (income.cryptoType == "Loss") Color(0xFF991B1B) else Color(0xFF065F46)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Freelance project name
                    if (income.source == "Freelance" && !income.projectName.isNullOrBlank()) {
                        Text(
                            text = "Project: ${income.projectName}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                        )
                    }

                    // Crypto asset
                    if (income.source == "Crypto" && !income.cryptoAsset.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = income.cryptoAsset,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Date: ${income.receivedDate}",
                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                    )
                }

                // Amount Column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatIncomeAmount(income),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isCryptoLoss) Color(0xFFB91C1C)
                            else if (isFreelanceExpected) Color(0xFFB45309)
                            else brandEmerald
                        )
                    )

                    // Show converted LKR for USD
                    if (income.currency == "USD" && income.amountLkrMinor != null) {
                        Text(
                            text = "≈ ${formatLkrAmount(income.amountLkrMinor)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                        )
                    }

                    if (isFreelanceExpected) {
                        Text(
                            text = "Excluded from actual",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFB45309)
                            )
                        )
                    }
                }
            }

            // Exchange rate info
            if (income.exchangeRate != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rate: 1 USD = ${income.exchangeRate} LKR" +
                            if (income.exchangeRateDate != null) " (${income.exchangeRateDate})" else "",
                    style = MaterialTheme.typography.labelSmall.copy(color = textSecondary)
                )
            }

            // Note
            if (income.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = income.note,
                    style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = borderLight)

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFreelanceExpected) {
                    Button(
                        onClick = onMarkReceived,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFEF3C7),
                            contentColor = Color(0xFF92400E)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Mark Received",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row {
                    TextButton(onClick = onEdit) {
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary)
                        )
                    }
                    TextButton(onClick = onDelete) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }
        }
    }
}

private fun formatIncomeAmount(income: Income): String {
    val amount = BigDecimal.valueOf(income.amountMinor, 2)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val prefix = if (income.source == "Crypto" && income.cryptoType == "Loss") "-" else ""
    return "$prefix${income.currency} ${formatter.format(amount)}"
}

private fun formatLkrAmount(amountMinor: Long): String {
    val amount = BigDecimal.valueOf(amountMinor, 2)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "LKR ${formatter.format(amount)}"
}

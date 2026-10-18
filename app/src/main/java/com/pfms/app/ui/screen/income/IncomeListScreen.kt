package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.model.Income
import com.pfms.app.ui.component.InsightsNavIcon
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
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
    // Realized Net Income summary
    totalRealizedIncomeMinor: Long = 0L,
    monthlyRealizedIncomeMinor: Long = 0L,
    // Freelance summary
    totalPendingFreelanceMinor: Long = 0L,
    pendingFreelanceCount: Int = 0,
    // Crypto summary
    cryptoGainsMinor: Long = 0L,
    cryptoLossesMinor: Long = 0L,
    netCryptoMinor: Long = 0L,
    ytdNetCryptoMinor: Long = 0L,
    // Actions
    onMarkFreelanceReceived: (String) -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToExpenses: (() -> Unit)? = null,
    onNavigateToGoals: (() -> Unit)? = null
) {
    var incomeToDelete by remember { mutableStateOf<Income?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }

    // Palette Tokens matching the Reference Design
    val brandEmerald = Color(0xFF007A5E)
    val brandEmeraldLight = Color(0xFFF0FDF4)
    val brandEmeraldBorder = Color(0xFFDCFCE7)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)
    val borderLight = Color(0xFFE2E8F0)
    val bgScreen = Color(0xFFF8FAFC)

    // Calculate currency totals for the Total Income Card
    val lkrOnlyTotalMinor = incomes
        .filter { it.currency == "LKR" && it.source != "Crypto" }
        .sumOf { it.amountMinor }

    val usdOnlyInLkrMinor = incomes
        .filter { it.currency == "USD" }
        .sumOf { it.amountLkrMinor ?: 0L }

    val usdOnlyUsdMinor = incomes
        .filter { it.currency == "USD" }
        .sumOf { it.amountMinor }

    val filterChips = listOf("All", "Salary", "Freelance", "AdSense", "Crypto")

    // Filter incomes list based on selected tab
    val filteredIncomes = remember(incomes, selectedFilter) {
        when (selectedFilter) {
            "Salary" -> incomes.filter { it.source.equals("Salary", ignoreCase = true) }
            "Freelance" -> incomes.filter { it.source.equals("Freelance", ignoreCase = true) }
            "AdSense" -> incomes.filter { it.source.contains("AdSense", ignoreCase = true) }
            "Crypto" -> incomes.filter { it.source.equals("Crypto", ignoreCase = true) }
            else -> incomes
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = bgScreen,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        IncomeArrowBackIcon(tint = textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onRecurringSalary) {
                        IncomeCalendarIcon(modifier = Modifier.size(20.dp), tint = textSecondary)
                    }
                    IconButton(onClick = onNavigateToAnalytics) {
                        InsightsNavIcon(modifier = Modifier.size(20.dp), tint = brandEmerald)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgScreen
                )
            )
        },
        bottomBar = {
            com.pfms.app.ui.component.PfmsBottomNavBar(
                selectedTab = com.pfms.app.ui.component.PfmsNavTab.INCOME,
                onHomeClick = { onNavigateToHome?.invoke() ?: onBack() },
                onIncomeClick = { /* Already on Income */ },
                onExpensesClick = { onNavigateToExpenses?.invoke() },
                onGoalsClick = onNavigateToGoals,
                onInsightsClick = onNavigateToAnalytics
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)
        ) {

            // ═════════════════════════════════════════════════════════
            // 1. SCREEN HEADER (Income + Add Income button)
            // ═════════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                fontSize = 30.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Track all your income sources and stay on top of your earnings.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onAddIncome,
                        colors = ButtonDefaults.buttonColors(containerColor = brandEmerald),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IncomePlusIcon(modifier = Modifier.size(13.dp), tint = Color.White)
                            Text(
                                text = "Add Income",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════
            // 2. TOTAL INCOME (THIS MONTH) OVERVIEW CARD
            // ═════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = brandEmeraldLight),
                    border = BorderStroke(1.dp, brandEmeraldBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card Top Row: Label and Trend badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "Total Income (This Month)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155),
                                    fontSize = 14.sp
                                )
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    IncomeTrendUpIcon(modifier = Modifier.size(12.dp), tint = brandEmerald)
                                    Text(
                                        text = "+12%",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = brandEmerald,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                                Text(
                                    text = "vs. last month",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        // Big Bold Amount
                        val displayTotalMinor = if (monthlyRealizedIncomeMinor > 0) {
                            monthlyRealizedIncomeMinor
                        } else if (totalRealizedIncomeMinor > 0) {
                            totalRealizedIncomeMinor
                        } else {
                            lkrOnlyTotalMinor + usdOnlyInLkrMinor + netCryptoMinor
                        }

                        Text(
                            text = formatLkrAmount(displayTotalMinor),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = brandEmerald,
                                fontSize = 30.sp
                            )
                        )

                        HorizontalDivider(color = brandEmeraldBorder, thickness = 1.dp)

                        // 3 Currency / Asset Breakdown Columns
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Column 1: LKR
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = "LKR",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = formatLkrAmount(lkrOnlyTotalMinor),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            // Column 2: USD
                            Column(modifier = Modifier.weight(1.2f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3B82F6))
                                    )
                                    Text(
                                        text = "USD",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = formatLkrAmount(usdOnlyInLkrMinor),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                                if (usdOnlyUsdMinor > 0) {
                                    Text(
                                        text = "($${formatDecimalAmount(usdOnlyUsdMinor)})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF0284C7),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            // Column 3: USDT / ETH Crypto
                            Column(modifier = Modifier.weight(1.1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF8B5CF6))
                                    )
                                    Text(
                                        text = "USDT/ETH",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                    )
                                    IncomeInfoCircleIcon(
                                        modifier = Modifier.size(12.dp),
                                        tint = Color(0xFF8B5CF6)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = formatLkrAmount(if (netCryptoMinor >= 0) netCryptoMinor else 0L),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Outstanding or loss indicator if any
                        if (pendingFreelanceCount > 0 || cryptoLossesMinor > 0) {
                            HorizontalDivider(color = brandEmeraldBorder, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pendingFreelanceCount > 0) {
                                    Text(
                                        text = "$pendingFreelanceCount invoice pending (${formatLkrAmount(totalPendingFreelanceMinor)})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFB45309),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                                if (cryptoLossesMinor > 0) {
                                    Text(
                                        text = "Crypto loss: -${formatLkrAmount(cryptoLossesMinor)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════
            // 3. SOURCE FILTER CHIPS ROW
            // ═════════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterChips.forEach { chip ->
                        val isSelected = selectedFilter == chip
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) brandEmerald else Color.White,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) brandEmerald else borderLight
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedFilter = chip }
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    fontSize = 13.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Error display
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ═════════════════════════════════════════════════════════
            // 4. INCOME LIST / ITEMS
            // ═════════════════════════════════════════════════════════
            when {
                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = brandEmerald)
                        }
                    }
                }

                filteredIncomes.isEmpty() -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, borderLight),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (selectedFilter == "All") "No Income Records Yet" else "No $selectedFilter Records",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = "Add salary, freelance projects, ad revenue, or crypto to track your earnings.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = onAddIncome,
                                    colors = ButtonDefaults.buttonColors(containerColor = brandEmerald),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Add Income")
                                }
                            }
                        }
                    }
                }

                else -> {
                    items(
                        items = filteredIncomes.sortedWith(
                            compareByDescending<Income> { it.receivedDate }
                                .thenByDescending { it.createdAt }
                        ),
                        key = { it.id }
                    ) { income ->
                        IncomeCardItem(
                            income = income,
                            onEdit = { onEditIncome(income.id) },
                            onDelete = { incomeToDelete = income },
                            onMarkReceived = { onMarkFreelanceReceived(income.id) }
                        )
                    }
                }
            }

            // ═════════════════════════════════════════════════════════
            // 5. BOTTOM DISCLAIMER / INFO NOTE
            // ═════════════════════════════════════════════════════════
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, borderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IncomeInfoCircleIcon(
                            modifier = Modifier.size(16.dp),
                            tint = textSecondary
                        )
                        Text(
                            text = "Income amounts are converted to LKR using the exchange rate at the time of entry.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    incomeToDelete?.let { income ->
        AlertDialog(
            onDismissRequest = { incomeToDelete = null },
            title = {
                Text(
                    text = "Delete Income Record?",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this ${income.source} income record? This action cannot be undone.",
                    color = textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteIncome(income.id)
                        incomeToDelete = null
                    }
                ) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { incomeToDelete = null }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

/**
 * Individual Income Card matching the visual reference design.
 */
@Composable
private fun IncomeCardItem(
    income: Income,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkReceived: () -> Unit
) {
    val isFreelanceExpected = income.source == "Freelance" && income.paymentStatus == "Expected"
    val isCryptoLoss = income.source == "Crypto" && income.cryptoType == "Loss"

    val brandEmerald = Color(0xFF007A5E)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)
    val textMuted = Color(0xFF94A3B8)
    val borderLight = Color(0xFFE2E8F0)

    var showMenu by remember { mutableStateOf(false) }

    // Icon container tint & icon tint based on source
    val (iconBgColor, iconTint) = when {
        income.source.equals("Salary", ignoreCase = true) -> Pair(Color(0xFFE6F4EA), Color(0xFF007A5E))
        income.source.equals("Freelance", ignoreCase = true) -> Pair(Color(0xFFE0F2FE), Color(0xFF0284C7))
        income.source.contains("AdSense", ignoreCase = true) -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
        income.source.equals("Crypto", ignoreCase = true) -> Pair(Color(0xFFF3E8FF), Color(0xFF7E22CE))
        else -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 1. Left Icon in Circular Container (48x48)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                IncomeSourceVector(
                    source = income.source,
                    modifier = Modifier.size(24.dp),
                    customTint = iconTint
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 2. Middle Details (Title, Date, Note)
            Column(modifier = Modifier.weight(1f)) {
                // Source Title
                val displayTitle = when {
                    income.source.equals("Freelance", ignoreCase = true) && !income.projectName.isNullOrBlank() ->
                        income.projectName
                    income.source.equals("Freelance", ignoreCase = true) ->
                        "Freelance Project"
                    income.source.contains("AdSense", ignoreCase = true) ->
                        "AdSense"
                    income.source.equals("Crypto", ignoreCase = true) ->
                        "Crypto Trading"
                    else ->
                        income.source
                }

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Date
                Text(
                    text = formatDisplayDate(income.receivedDate),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                )

                // Note or description
                val noteText = when {
                    income.note.isNotBlank() -> income.note
                    income.source == "Salary" -> "Monthly salary"
                    income.source == "Crypto" -> "${income.cryptoAsset ?: "USDT / ETH"} (${if (isCryptoLoss) "net loss" else "net profit"})"
                    income.source.contains("AdSense") -> "Blog revenue"
                    else -> ""
                }

                if (noteText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = noteText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textMuted,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Right Column (Amount, Converted LKR, Status Badge, 3-dots Menu)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Amount
                val amountColor = if (isCryptoLoss) Color(0xFFDC2626) else brandEmerald
                Text(
                    text = formatIncomeDisplayAmount(income),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        fontSize = 15.sp
                    )
                )

                // Subtext for USD converted to LKR
                if (income.currency == "USD" && income.amountLkrMinor != null) {
                    Text(
                        text = "(${formatLkrAmount(income.amountLkrMinor)})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                // Status Badge Pill
                val (badgeBg, badgeTextColor, badgeLabel) = when {
                    isFreelanceExpected -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Pending")
                    income.source == "Freelance" -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Received")
                    income.source == "Crypto" && income.cryptoType == "Loss" -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Loss")
                    income.source == "Crypto" -> Triple(Color(0xFFF3E8FF), Color(0xFF7E22CE), "Estimated")
                    else -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Confirmed")
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // 4. Three-dots Menu
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    IncomeMoreVertIcon(modifier = Modifier.size(16.dp), tint = textMuted)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isFreelanceExpected) {
                        DropdownMenuItem(
                            text = { Text("Mark Received", fontWeight = FontWeight.SemiBold, color = brandEmerald) },
                            onClick = {
                                showMenu = false
                                onMarkReceived()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Helper to format "2025-09-25" -> "25 Sep 2025"
 */
private fun formatDisplayDate(dateStr: String): String {
    return runCatching {
        val parsed = LocalDate.parse(dateStr)
        val month = parsed.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "${parsed.dayOfMonth} $month ${parsed.year}"
    }.getOrDefault(dateStr)
}

/**
 * Format Income Amount with proper Currency symbol.
 */
private fun formatIncomeDisplayAmount(income: Income): String {
    val amount = BigDecimal.valueOf(income.amountMinor, 2)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (income.amountMinor % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    val prefix = if (income.source == "Crypto" && income.cryptoType == "Loss") "- " else ""
    return "$prefix${income.currency} ${formatter.format(amount)}"
}

/**
 * Format LKR Amount (e.g. "LKR 175,000" or "LKR 142,000.50").
 */
private fun formatLkrAmount(amountMinor: Long): String {
    val amount = BigDecimal.valueOf(amountMinor, 2)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (amountMinor % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return "LKR ${formatter.format(amount)}"
}

/**
 * Format Decimal Amount (e.g. "73.40").
 */
private fun formatDecimalAmount(amountMinor: Long): String {
    val amount = BigDecimal.valueOf(amountMinor, 2)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return formatter.format(amount)
}

package com.pfms.app.ui.screen.income

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.model.Income
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

// Data returned by the Add/Edit Income form.
data class IncomeFormInput(
    val amount: String,
    val source: String,
    val currency: String,
    val receivedDate: String,
    val note: String,
    val projectName: String? = null,
    val paymentStatus: String = "Received",
    val cryptoType: String? = null,
    val cryptoAsset: String? = null
)

// Supported income sources
private val incomeCategoryList = listOf(
    "Salary",
    "Freelance",
    "Google AdSense",
    "Crypto",
    "Other"
)

// Common cryptocurrency assets
private val quickCryptoAssets = listOf(
    "BTC", "ETH", "USDT", "SOL", "BNB", "XRP", "ADA"
)

// Common frequencies
private val frequencyList = listOf(
    "Monthly",
    "One-time",
    "Bi-weekly",
    "Weekly",
    "Annually"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeFormScreen(
    onBack: () -> Unit,
    onSave: (IncomeFormInput) -> Unit,
    modifier: Modifier = Modifier,
    initialIncome: Income? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    exchangeRate: BigDecimal? = null,
    exchangeRateDate: String? = null,
    isRateFetching: Boolean = false,
    rateFetchError: String? = null,
    isManualRateMode: Boolean = false,
    onFetchExchangeRate: (String) -> Unit = {},
    onSetManualRate: (String, String) -> Boolean = { _, _ -> false },
    onEnableManualRate: () -> Unit = {},
    onClearExchangeRate: () -> Unit = {},
    canRetry: Boolean = false
) {
    val isEditMode = initialIncome != null

    // Form states
    var source by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.source ?: "Salary")
    }

    var amount by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.amountMinor?.let {
                BigDecimal.valueOf(it, 2).toPlainString()
            } ?: ""
        )
    }

    var currency by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.currency ?: "LKR")
    }

    var receivedDate by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.receivedDate
                ?: LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
        )
    }

    var frequency by rememberSaveable { mutableStateOf("Monthly") }

    var paymentStatus by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.paymentStatus ?: "Received")
    }

    var note by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.note ?: "")
    }

    var projectName by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.projectName ?: "")
    }

    var cryptoType by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.cryptoType ?: "Gain")
    }

    var cryptoAsset by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.cryptoAsset ?: "BTC")
    }

    // Expandable accordion state (for extra details like notes, freelance client, crypto details)
    var isDetailsExpanded by rememberSaveable {
        mutableStateOf(source == "Freelance" || source == "Crypto" || initialIncome?.note?.isNotBlank() == true)
    }

    // Dropdown visibility states
    var showSourceDropdown by remember { mutableStateOf(false) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showFrequencyDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var manualRateInput by rememberSaveable { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }

    // Palette tokens matching the reference design
    val brandEmerald = Color(0xFF007A5E)
    val brandEmeraldLight = Color(0xFFF0FDF4)
    val brandEmeraldBorder = Color(0xFFDCFCE7)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)
    val borderLight = Color(0xFFE2E8F0)
    val bgScreen = Color(0xFFF8FAFC)

    // ════════════════════════════════════════════════════════════
    // AUTOMATIC LIVE EXCHANGE RATE FETCHING
    // ════════════════════════════════════════════════════════════
    // When currency is USD, automatically fetch the exchange rate
    // immediately whenever the currency is set or the date changes!
    LaunchedEffect(currency, receivedDate) {
        if (currency == "USD") {
            if (initialIncome != null && initialIncome.currency == "USD" && initialIncome.receivedDate == receivedDate && exchangeRate != null) {
                // Already loaded existing rate for edit mode
            } else {
                onFetchExchangeRate(receivedDate)
            }
        }
    }

    // Sync manualRateInput when exchangeRate is updated
    LaunchedEffect(exchangeRate) {
        if (exchangeRate != null) {
            manualRateInput = exchangeRate.toPlainString()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = bgScreen,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Income" else "Add Income",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 20.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        IncomeArrowBackIcon(tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgScreen
                )
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ════════════════════════════════════════════════════════════
            // 1. SOURCE SELECTOR
            // ════════════════════════════════════════════════════════════
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Source",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 14.sp
                    )
                )

                // Category Card / Selector
                val (sourceIconBg, sourceIconTint) = when {
                    source.equals("Salary", ignoreCase = true) -> Pair(Color(0xFFE6F4EA), Color(0xFF007A5E))
                    source.equals("Freelance", ignoreCase = true) -> Pair(Color(0xFFE0F2FE), Color(0xFF0284C7))
                    source.contains("AdSense", ignoreCase = true) -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
                    source.equals("Crypto", ignoreCase = true) -> Pair(Color(0xFFF3E8FF), Color(0xFF7E22CE))
                    else -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, borderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showSourceDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(sourceIconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IncomeSourceVector(
                                        source = source,
                                        modifier = Modifier.size(20.dp),
                                        customTint = sourceIconTint
                                    )
                                }

                                Text(
                                    text = if (source.contains("AdSense")) "AdSense" else source,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    )
                                )
                            }

                            IncomeChevronDownIcon(
                                modifier = Modifier.size(16.dp),
                                tint = textSecondary
                            )
                        }
                    }

                    // Source Options Dropdown
                    DropdownMenu(
                        expanded = showSourceDropdown,
                        onDismissRequest = { showSourceDropdown = false },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        incomeCategoryList.forEach { cat ->
                            val isSelected = source == cat
                            val (catBg, catTint) = when {
                                cat.equals("Salary", ignoreCase = true) -> Pair(Color(0xFFE6F4EA), Color(0xFF007A5E))
                                cat.equals("Freelance", ignoreCase = true) -> Pair(Color(0xFFE0F2FE), Color(0xFF0284C7))
                                cat.contains("AdSense", ignoreCase = true) -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
                                cat.equals("Crypto", ignoreCase = true) -> Pair(Color(0xFFF3E8FF), Color(0xFF7E22CE))
                                else -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
                            }

                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(catBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IncomeSourceVector(
                                            source = cat,
                                            modifier = Modifier.size(18.dp),
                                            customTint = catTint
                                        )
                                    }
                                },
                                text = {
                                    Text(
                                        text = if (cat.contains("AdSense")) "AdSense" else cat,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) brandEmerald else textPrimary
                                    )
                                },
                                onClick = {
                                    source = cat
                                    showSourceDropdown = false
                                    if (cat == "Freelance" || cat == "Crypto") {
                                        isDetailsExpanded = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 2. ROW: AMOUNT & CURRENCY
            // ════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Amount Input
                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Amount",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 14.sp
                        )
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            amountError = null
                        },
                        placeholder = { Text("0.00", color = Color(0xFF94A3B8)) },
                        prefix = {
                            Text(
                                text = if (currency == "USD") "$ " else "Rs. ",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = brandEmerald
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = amountError != null,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = brandEmerald,
                            unfocusedBorderColor = borderLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Currency Dropdown
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Currency",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 14.sp
                        )
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, borderLight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showCurrencyDropdown = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currency,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )
                                IncomeChevronDownIcon(
                                    modifier = Modifier.size(16.dp),
                                    tint = textSecondary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCurrencyDropdown,
                            onDismissRequest = { showCurrencyDropdown = false },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            listOf("LKR", "USD").forEach { cur ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cur,
                                            fontWeight = if (currency == cur) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currency == cur) brandEmerald else textPrimary
                                        )
                                    },
                                    onClick = {
                                        if (currency != cur) {
                                            currency = cur
                                            onClearExchangeRate()
                                            if (cur == "USD") {
                                                onFetchExchangeRate(receivedDate)
                                            }
                                        }
                                        showCurrencyDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (amountError != null) {
                Text(
                    text = amountError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ════════════════════════════════════════════════════════════
            // AUTOMATIC LIVE CURRENCY CONVERSION CARD (When Currency = USD)
            // ════════════════════════════════════════════════════════════
            if (currency == "USD") {
                val effectiveRate = exchangeRate ?: manualRateInput.trim().toBigDecimalOrNull()
                val parsedAmount = amount.trim().toBigDecimalOrNull()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = brandEmeraldLight),
                    border = BorderStroke(1.dp, brandEmeraldBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card Header with Live Status & Refresh Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Live Pulsing Status Dot
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isRateFetching) Color(0xFFF59E0B)
                                            else if (effectiveRate != null) Color(0xFF10B981)
                                            else Color(0xFFEF4444)
                                        )
                                )

                                Column {
                                    Text(
                                        text = if (isRateFetching) "Fetching Live Exchange Rate..."
                                        else if (effectiveRate != null) "Exchange Rate (USD → LKR)"
                                        else "Exchange Rate Required",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 13.5.sp
                                        )
                                    )

                                    if (exchangeRateDate != null && !isRateFetching) {
                                        Text(
                                            text = "Exchange rate date: $exchangeRateDate",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = textSecondary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }

                            // Refresh / Re-fetch Button
                            IconButton(
                                onClick = { onFetchExchangeRate(receivedDate) },
                                enabled = !isRateFetching,
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (isRateFetching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = brandEmerald
                                    )
                                } else {
                                    IncomeRefreshIcon(modifier = Modifier.size(16.dp), tint = brandEmerald)
                                }
                            }
                        }

                        // Rate Input Field with clean suffix
                        OutlinedTextField(
                            value = if (isRateFetching && exchangeRate == null) "Fetching..." else manualRateInput,
                            onValueChange = {
                                manualRateInput = it
                                onSetManualRate(it, receivedDate)
                            },
                            enabled = !isRateFetching,
                            label = { Text("Rate (1 USD = ? LKR)") },
                            placeholder = { Text("e.g. 308.50") },
                            trailingIcon = {
                                Text(
                                    text = "LKR per 1 USD",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = textSecondary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = brandEmerald,
                                unfocusedBorderColor = borderLight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Real-Time Converted Amount Banner
                        if (effectiveRate != null && parsedAmount != null && parsedAmount > BigDecimal.ZERO) {
                            val converted = parsedAmount.multiply(effectiveRate)
                            val lkrFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
                                minimumFractionDigits = 2
                                maximumFractionDigits = 2
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, brandEmeraldBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Converted Value:",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Text(
                                        text = "LKR ${lkrFormatter.format(converted)}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = brandEmerald
                                        )
                                    )
                                }
                            }
                        }

                        // Rate fetch error fallback message
                        if (rateFetchError != null) {
                            Text(
                                text = rateFetchError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 3. ROW: DATE RECEIVED & FREQUENCY
            // ════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date Received
                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (source == "Freelance" && paymentStatus == "Expected") "Invoice Date" else "Date Received",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 14.sp
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, borderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showDatePicker = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IncomeCalendarIcon(
                                modifier = Modifier.size(18.dp),
                                tint = textSecondary
                            )
                            Text(
                                text = formatDisplayDate(receivedDate),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                            )
                        }
                    }
                }

                // Frequency Dropdown
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Frequency",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 14.sp
                        )
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, borderLight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showFrequencyDropdown = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = frequency,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary
                                    )
                                )
                                IncomeChevronDownIcon(
                                    modifier = Modifier.size(16.dp),
                                    tint = textSecondary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showFrequencyDropdown,
                            onDismissRequest = { showFrequencyDropdown = false },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            frequencyList.forEach { freq ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = freq,
                                            fontWeight = if (frequency == freq) FontWeight.Bold else FontWeight.Normal,
                                            color = if (frequency == freq) brandEmerald else textPrimary
                                        )
                                    },
                                    onClick = {
                                        frequency = freq
                                        showFrequencyDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 4. STATUS TOGGLE (Confirmed vs Estimated)
            // ════════════════════════════════════════════════════════════
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 14.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Confirmed Button
                    val isConfirmed = paymentStatus == "Received"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isConfirmed) brandEmerald else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isConfirmed) brandEmerald else borderLight
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { paymentStatus = "Received" }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IncomeCheckmarkIcon(
                                modifier = Modifier.size(16.dp),
                                tint = if (isConfirmed) Color.White else textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Confirmed",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConfirmed) Color.White else textPrimary
                                )
                            )
                        }
                    }

                    // Estimated Button
                    val isEstimated = paymentStatus == "Expected"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isEstimated) brandEmerald else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isEstimated) brandEmerald else borderLight
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { paymentStatus = "Expected" }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IncomeClockIcon(
                                modifier = Modifier.size(16.dp),
                                tint = if (isEstimated) Color.White else textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Estimated",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isEstimated) Color.White else textPrimary
                                )
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 5. ADDITIONAL DETAILS ACCORDION
            // ════════════════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Accordion Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDetailsExpanded = !isDetailsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Additional Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 15.sp
                            )
                        )
                        if (isDetailsExpanded) {
                            IncomeChevronUpIcon(modifier = Modifier.size(16.dp), tint = textSecondary)
                        } else {
                            IncomeChevronDownIcon(modifier = Modifier.size(16.dp), tint = textSecondary)
                        }
                    }

                    AnimatedVisibility(
                        visible = isDetailsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {

                            // Project / Client (required for Freelance, optional otherwise)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (source == "Freelance") "Project / Client *" else "Project / Client",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary
                                    )
                                )

                                OutlinedTextField(
                                    value = projectName,
                                    onValueChange = { projectName = it },
                                    placeholder = { Text("e.g. ABC Industries", color = Color(0xFF94A3B8)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = brandEmerald,
                                        unfocusedBorderColor = borderLight
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Crypto P&L specifics
                            if (source == "Crypto") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Crypto Result",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = textSecondary
                                        )
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val isGain = cryptoType == "Gain"
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isGain) Color(0xFFD1FAE5) else Color(0xFFF8FAFC),
                                            border = BorderStroke(1.dp, if (isGain) brandEmerald else borderLight),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { cryptoType = "Gain" }
                                        ) {
                                            Text(
                                                text = "+ Profit (Gain)",
                                                fontWeight = FontWeight.Bold,
                                                color = if (isGain) Color(0xFF065F46) else textPrimary,
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        val isLoss = cryptoType == "Loss"
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isLoss) Color(0xFFFEE2E2) else Color(0xFFF8FAFC),
                                            border = BorderStroke(1.dp, if (isLoss) Color(0xFFEF4444) else borderLight),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { cryptoType = "Loss" }
                                        ) {
                                            Text(
                                                text = "- Loss",
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLoss) Color(0xFFB91C1C) else textPrimary,
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    // Quick crypto tokens
                                    Text(
                                        text = "Asset Symbol",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = textSecondary
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        quickCryptoAssets.forEach { asset ->
                                            val isAssetSelected = cryptoAsset.equals(asset, ignoreCase = true)
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isAssetSelected) textPrimary else Color(0xFFF1F5F9),
                                                modifier = Modifier.clickable { cryptoAsset = asset }
                                            ) {
                                                Text(
                                                    text = asset,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isAssetSelected) Color.White else textPrimary
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Description (optional)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Description (optional)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary
                                    )
                                )

                                OutlinedTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    placeholder = { Text("Add a note about this income...", color = Color(0xFF94A3B8)) },
                                    minLines = 3,
                                    maxLines = 5,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = brandEmerald,
                                        unfocusedBorderColor = borderLight
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Global error message if any
            com.pfms.app.ui.component.ErrorBanner(errorMessage)

            Spacer(modifier = Modifier.height(10.dp))

            // ════════════════════════════════════════════════════════════
            // 6. ACTION BUTTONS: SAVE INCOME & CANCEL
            // ════════════════════════════════════════════════════════════
            Button(
                onClick = {
                    val value = amount.trim()
                    val validAmount = value.matches(Regex("""\d+(\.\d{1,2})?""")) &&
                            value.toBigDecimalOrNull()?.let {
                                it > BigDecimal.ZERO && it <= BigDecimal("92233720368547758.07")
                            } == true

                    if (!validAmount) {
                        amountError = "Enter a valid positive amount."
                        return@Button
                    }

                    if (currency == "USD") {
                        if (exchangeRate == null && manualRateInput.isNotBlank()) {
                            val rateOk = onSetManualRate(manualRateInput, receivedDate)
                            if (!rateOk) {
                                amountError = "Enter a valid exchange rate for USD."
                                return@Button
                            }
                        } else if (exchangeRate == null || isRateFetching) {
                            amountError = "Please wait for live exchange rate or enter it manually."
                            return@Button
                        }
                    }

                    if (source == "Freelance" && projectName.isBlank()) {
                        amountError = "Project / client name is required for freelance income."
                        return@Button
                    }

                    onSave(
                        IncomeFormInput(
                            amount = value,
                            source = source,
                            currency = currency,
                            receivedDate = receivedDate,
                            note = note.trim(),
                            projectName = if (source == "Freelance") projectName.trim() else null,
                            paymentStatus = paymentStatus,
                            cryptoType = if (source == "Crypto") cryptoType else null,
                            cryptoAsset = if (source == "Crypto") cryptoAsset.trim().ifBlank { null } else null
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = brandEmerald),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (canRetry && errorMessage != null) "Retry" else if (isEditMode) "Save Changes" else "Save Income",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, brandEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isSaving
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = brandEmerald,
                        fontSize = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val parsedDate = runCatching { LocalDate.parse(receivedDate) }
            .getOrElse { LocalDate.now(ZoneId.of("Asia/Colombo")) }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                parsedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull() ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedLocalDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            if (receivedDate != selectedLocalDate.toString()) {
                                receivedDate = selectedLocalDate.toString()
                                if (currency == "USD") onClearExchangeRate()
                            }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = "Confirm",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = brandEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = brandEmerald,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = brandEmerald,
                    todayContentColor = brandEmerald
                )
            )
        }
    }
}

/**
 * Format "2025-09-25" -> "25 Sep 2025"
 */
private fun formatDisplayDate(dateStr: String): String {
    return runCatching {
        val parsed = LocalDate.parse(dateStr)
        val month = parsed.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "${parsed.dayOfMonth} $month ${parsed.year}"
    }.getOrDefault(dateStr)
}

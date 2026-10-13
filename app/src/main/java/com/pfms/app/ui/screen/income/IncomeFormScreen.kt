package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.model.Income
import com.pfms.app.ui.theme.DarkNavyText
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.ui.theme.SecondaryText
import com.pfms.app.data.exchange.ExchangeRateResult
import com.pfms.app.data.exchange.ExchangeRateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val exchangeRate: String? = null,
    val exchangeRateDate: String? = null,
    val amountLkrMinor: Long? = null,
    val projectName: String? = null,
    val paymentStatus: String = "Received",
    val cryptoType: String? = null,
    val cryptoAsset: String? = null
)

// Supported income sources (no emojis)
private val incomeCategoryList = listOf(
    "Salary",
    "Freelance",
    "Crypto",
    "Google AdSense",
    "Other"
)

// Common cryptocurrency assets
private val quickCryptoAssets = listOf(
    "BTC", "ETH", "USDT", "SOL", "BNB", "XRP", "ADA"
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
    onSetManualRate: (String) -> Boolean = { false },
    onClearExchangeRate: () -> Unit = {}
) {
    val context = LocalContext.current
    val isEditMode = initialIncome != null

    // 1. Income source
    var source by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.source ?: "Salary")
    }

    // 2. Income amount
    var amount by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.amountMinor?.let {
                BigDecimal.valueOf(it, 2).toPlainString()
            } ?: ""
        )
    }

    // 3. Currency
    var currency by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.currency ?: "LKR")
    }

    // 4. Received / Invoice date
    var receivedDate by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.receivedDate
                ?: LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
        )
    }

    // 5. Optional note
    var note by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.note ?: "")
    }

    // Member 2: Freelance fields
    var projectName by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.projectName ?: "")
    }

    var paymentStatus by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.paymentStatus ?: "Received")
    }

    // Member 2: Crypto fields
    var cryptoType by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.cryptoType ?: "Gain")
    }

    var cryptoAsset by rememberSaveable(initialIncome?.id) {
        mutableStateOf(initialIncome?.cryptoAsset ?: "BTC")
    }

    // Member 2: Date picker state
    var showDatePicker by remember { mutableStateOf(false) }

    // Member 2: Internal fallback exchange-rate state (used when not driven by external ViewModel)
    var localExchangeRate by rememberSaveable { mutableStateOf<String?>(initialIncome?.exchangeRate) }
    var localExchangeRateDate by rememberSaveable { mutableStateOf<String?>(initialIncome?.exchangeRateDate) }
    var localIsRateFetching by remember { mutableStateOf(false) }
    var localRateFetchError by remember { mutableStateOf<String?>(null) }
    var localIsManualMode by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val exchangeRateService = remember { ExchangeRateService() }

    val activeExchangeRate = exchangeRate ?: localExchangeRate?.let { runCatching { BigDecimal(it) }.getOrNull() }
    val activeExchangeRateDate = exchangeRateDate ?: localExchangeRateDate
    val activeIsRateFetching = isRateFetching || localIsRateFetching
    val activeRateFetchError = rateFetchError ?: localRateFetchError
    val activeIsManualMode = isManualRateMode || localIsManualMode

    var manualRateInput by rememberSaveable { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }

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
                        text = if (isEditMode) "Edit Income Record" else "Add Income Record",
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
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ════════════════════════════════════════════════════════════
            // 1. CATEGORY SELECTOR (Clean Modern Pills, No Emojis)
            // ════════════════════════════════════════════════════════════
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Income Category",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    incomeCategoryList.forEach { cat ->
                        val isSelected = source == cat
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isSelected) brandEmerald else Color.White,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) brandEmerald else borderLight
                            ),
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { source = cat }
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else textPrimary
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 2. FREELANCE PROJECT DETAILS (FR-08)
            // ════════════════════════════════════════════════════════════
            if (source == "Freelance") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    border = BorderStroke(1.dp, borderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Freelance Project Details",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = "Client billing & invoice status",
                                    style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEBF3FE)
                            ) {
                                Text(
                                    text = "Client Project",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Project / Client Name
                        OutlinedTextField(
                            value = projectName,
                            onValueChange = { projectName = it },
                            label = { Text("Project / Client Name") },
                            placeholder = { Text("e.g. Acme Corp Web App") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Payment Status Toggle
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Payment Status",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Expected Option
                                val isExpected = paymentStatus == "Expected"
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isExpected) Color(0xFFFEF3C7) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isExpected) Color(0xFFF59E0B) else borderLight
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { paymentStatus = "Expected" }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Pending Invoice",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isExpected) Color(0xFFB45309) else textPrimary
                                            )
                                        )
                                        Text(
                                            text = "Awaiting payment",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isExpected) Color(0xFF92400E) else textSecondary
                                            )
                                        )
                                    }
                                }

                                // Received Option
                                val isReceived = paymentStatus == "Received"
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isReceived) Color(0xFFD1FAE5) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isReceived) brandEmerald else borderLight
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { paymentStatus = "Received" }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Paid & Received",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isReceived) Color(0xFF065F46) else textPrimary
                                            )
                                        )
                                        Text(
                                            text = "Funds received",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isReceived) Color(0xFF047857) else textSecondary
                                            )
                                        )
                                    }
                                }
                            }

                            // Dynamic explanatory banner for Freelance
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (paymentStatus == "Expected") Color(0xFFFEF3C7).copy(alpha = 0.5f)
                                else Color(0xFFD1FAE5).copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (paymentStatus == "Expected")
                                        "Pending payments are tracked in Outstanding Invoices and excluded from your actual realized income until received."
                                    else
                                        "Received payments are immediately included in your realized income total.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (paymentStatus == "Expected") Color(0xFF92400E) else Color(0xFF065F46)
                                    ),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 3. CRYPTO P&L DETAILS (FR-09)
            // ════════════════════════════════════════════════════════════
            if (source == "Crypto") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    border = BorderStroke(1.dp, borderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cryptocurrency P&L",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = "Track realized trading profit or loss",
                                    style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF3E8FF)
                            ) {
                                Text(
                                    text = "Portfolio Trade",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7E22CE)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Gain vs Loss Segmented Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Transaction Result",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Profit Option
                                val isGain = cryptoType == "Gain"
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isGain) Color(0xFFD1FAE5) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isGain) brandEmerald else borderLight
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { cryptoType = "Gain" }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "+ Profit (Gain)",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isGain) Color(0xFF065F46) else textPrimary
                                            )
                                        )
                                        Text(
                                            text = "Adds to income",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isGain) Color(0xFF047857) else textSecondary
                                            )
                                        )
                                    }
                                }

                                // Loss Option
                                val isLoss = cryptoType == "Loss"
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isLoss) Color(0xFFFEE2E2) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isLoss) Color(0xFFEF4444) else borderLight
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { cryptoType = "Loss" }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "- Loss",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLoss) Color(0xFFB91C1C) else textPrimary
                                            )
                                        )
                                        Text(
                                            text = "Deducted from income",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isLoss) Color(0xFF991B1B) else textSecondary
                                            )
                                        )
                                    }
                                }
                            }

                            // Dynamic explanatory banner for Crypto
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (cryptoType == "Loss") Color(0xFFFEE2E2).copy(alpha = 0.5f)
                                else Color(0xFFD1FAE5).copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (cryptoType == "Loss")
                                        "Crypto losses are automatically deducted from your monthly net income totals."
                                    else
                                        "Crypto profits are added to your monthly net income and portfolio summary.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (cryptoType == "Loss") Color(0xFF991B1B) else Color(0xFF065F46)
                                    ),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Crypto Asset Chips
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Cryptocurrency Asset (Optional)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
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
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isAssetSelected) textPrimary else Color(0xFFF1F5F9),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { cryptoAsset = asset }
                                    ) {
                                        Text(
                                            text = asset,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAssetSelected) Color.White else textPrimary
                                            ),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = cryptoAsset,
                                onValueChange = { cryptoAsset = it },
                                label = { Text("Asset Symbol") },
                                placeholder = { Text("e.g. BTC, ETH, USDT") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 4. AMOUNT & CURRENCY CARD
            // ════════════════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = BorderStroke(1.dp, borderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Amount & Currency",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )

                    // Currency Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("LKR", "USD").forEach { cur ->
                            val isCurSelected = currency == cur
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurSelected) brandEmerald else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurSelected) brandEmerald else borderLight
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        currency = cur
                                        if (cur == "LKR") {
                                            onClearExchangeRate()
                                            localExchangeRate = null
                                            localIsManualMode = false
                                            localRateFetchError = null
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = cur,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurSelected) Color.White else textPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Amount Label
                    val amountLabel = when {
                        source == "Freelance" && paymentStatus == "Expected" -> "Expected Invoice Amount"
                        source == "Freelance" -> "Payment Amount Received"
                        source == "Crypto" && cryptoType == "Loss" -> "Crypto Loss Amount (Deducted)"
                        source == "Crypto" -> "Crypto Gain Amount"
                        else -> "Income Amount"
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            amountError = null
                        },
                        label = { Text(amountLabel) },
                        placeholder = { Text("0.00") },
                        prefix = {
                            Text(
                                text = if (currency == "USD") "$ " else "Rs. ",
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = amountError != null,
                        supportingText = {
                            amountError?.let { Text(it) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // 5. USD TO LKR CONVERSION (FR-07)
            // ════════════════════════════════════════════════════════════
            if (currency == "USD") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    border = BorderStroke(1.dp, borderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "USD to LKR Conversion",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = "Live and historical exchange rates",
                                    style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEBF3FE)
                            ) {
                                Text(
                                    text = "Forex",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        val isHistorical = runCatching {
                            LocalDate.parse(receivedDate) < LocalDate.now(ZoneId.of("Asia/Colombo"))
                        }.getOrDefault(false)

                        Button(
                            onClick = {
                                onFetchExchangeRate(receivedDate)
                                // Self-contained rate fetch fallback when no external ViewModel is actively controlling rate state
                                localIsRateFetching = true
                                localRateFetchError = null
                                coroutineScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            val entryDate = LocalDate.parse(receivedDate)
                                            val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
                                            if (entryDate < today) {
                                                exchangeRateService.fetchHistoricalRate(receivedDate)
                                            } else {
                                                exchangeRateService.fetchLatestRate()
                                            }
                                        } catch (e: Exception) {
                                            exchangeRateService.fetchLatestRate()
                                        }
                                    }
                                    localIsRateFetching = false
                                    when (result) {
                                        is ExchangeRateResult.Success -> {
                                            localExchangeRate = result.rate.toPlainString()
                                            localExchangeRateDate = result.rateDate
                                            localRateFetchError = null
                                            localIsManualMode = false
                                        }
                                        is ExchangeRateResult.Failure -> {
                                            localRateFetchError = result.message
                                            localIsManualMode = true
                                        }
                                    }
                                }
                            },
                            enabled = !activeIsRateFetching,
                            colors = ButtonDefaults.buttonColors(containerColor = brandEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (activeIsRateFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Retrieving rate...")
                            } else {
                                Text(
                                    text = if (isHistorical) "Fetch Historical Rate ($receivedDate)"
                                    else "Fetch Current Rate",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Display Rate & Converted Preview
                        if (activeExchangeRate != null) {
                            val rateFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
                                minimumFractionDigits = 2
                                maximumFractionDigits = 4
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, borderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "1 USD = ${rateFormatter.format(activeExchangeRate)} LKR",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                    )
                                    Text(
                                        text = if (activeIsManualMode) "Manual rate applied"
                                        else "Rate date: ${activeExchangeRateDate ?: "Latest"}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = textSecondary)
                                    )

                                    val parsedAmount = amount.trim().toBigDecimalOrNull()
                                    if (parsedAmount != null && parsedAmount > BigDecimal.ZERO) {
                                        val converted = parsedAmount.multiply(activeExchangeRate)
                                        val lkrFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
                                            minimumFractionDigits = 2
                                            maximumFractionDigits = 2
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = borderLight
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Converted Value:",
                                                style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary)
                                            )
                                            Text(
                                                text = "LKR ${lkrFormatter.format(converted)}",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = brandEmerald
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (activeRateFetchError != null) {
                            Text(
                                text = activeRateFetchError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Manual fallback entry
                        if (activeIsManualMode || activeRateFetchError != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Manual Rate Entry (1 USD = ? LKR)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    )
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualRateInput,
                                        onValueChange = { manualRateInput = it },
                                        label = { Text("Rate") },
                                        placeholder = { Text("e.g. 305.50") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Button(
                                        onClick = {
                                            val handled = onSetManualRate(manualRateInput)
                                            if (!handled) {
                                                val parsed = manualRateInput.trim().toBigDecimalOrNull()
                                                if (parsed != null && parsed > BigDecimal.ZERO) {
                                                    localExchangeRate = parsed.toPlainString()
                                                    localExchangeRateDate = "Manual"
                                                    localRateFetchError = null
                                                    localIsManualMode = true
                                                } else {
                                                    localRateFetchError = "Please enter a valid rate (e.g. 305.50)"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = brandEmerald),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Apply")
                                    }
                                }
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    localIsManualMode = true
                                    localExchangeRate = null
                                    onClearExchangeRate()
                                }
                            ) {
                                Text(
                                    text = "Enter rate manually instead",
                                    style = MaterialTheme.typography.bodySmall.copy(color = brandEmerald)
                                )
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 6. DATE & NOTES CARD
            // ════════════════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                border = BorderStroke(1.dp, borderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Date & Notes",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )

                    // Date Picker — clean clickable row
                    val dateLabel = if (source == "Freelance" && paymentStatus == "Expected") "Invoice Date" else "Date Received"
                    val parsedDate = runCatching { LocalDate.parse(receivedDate) }
                        .getOrElse { LocalDate.now(ZoneId.of("Asia/Colombo")) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = SecondaryText,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFE9EDF2)),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = receivedDate,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = brandEmerald
                                    )
                                )
                            }
                        }

                        if (showDatePicker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = runCatching {
                                    parsedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                                }.getOrNull() ?: System.currentTimeMillis()
                            )

                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            datePickerState.selectedDateMillis?.let { millis ->
                                                val selectedLocalDate = Instant.ofEpochMilli(millis)
                                                    .atZone(ZoneOffset.UTC)
                                                    .toLocalDate()
                                                receivedDate = selectedLocalDate.toString()
                                                if (currency == "USD") {
                                                    onClearExchangeRate()
                                                    localExchangeRate = null
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
                                        Text(
                                            text = "Cancel",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = textSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
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

                    // Optional Notes
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("Add transaction details or reference...") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Global error message
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ════════════════════════════════════════════════════════════
            // 7. SAVE & CANCEL BUTTONS
            // ════════════════════════════════════════════════════════════
            Button(
                onClick = {
                    val value = amount.trim()
                    val validAmount = value.matches(Regex("""\d+(\.\d{1,2})?""")) &&
                            value.toBigDecimalOrNull()?.let {
                                it > BigDecimal.ZERO && it <= BigDecimal("92233720368547758.07")
                            } == true

                    if (!validAmount) {
                        amountError = "Enter a valid positive amount with up to 2 decimal places."
                    } else {
                        val convertedLkrMinor = if (currency == "USD" && activeExchangeRate != null) {
                            com.pfms.app.domain.usecase.CurrencyConverter.convertUsdToLkrMinor(
                                amountUsdMinor = (value.toBigDecimal().multiply(BigDecimal(100))).toLong(),
                                exchangeRate = activeExchangeRate
                            )
                        } else null

                        onSave(
                            IncomeFormInput(
                                amount = value,
                                source = source,
                                currency = currency,
                                receivedDate = receivedDate,
                                note = note.trim(),
                                exchangeRate = if (currency == "USD") activeExchangeRate?.toPlainString() else null,
                                exchangeRateDate = if (currency == "USD") activeExchangeRateDate else null,
                                amountLkrMinor = convertedLkrMinor,
                                projectName = if (source == "Freelance") projectName.trim() else null,
                                paymentStatus = if (source == "Freelance") paymentStatus else "Received",
                                cryptoType = if (source == "Crypto") cryptoType else null,
                                cryptoAsset = if (source == "Crypto") cryptoAsset.trim().ifBlank { null } else null
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = brandEmerald),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isEditMode) "Update Income Record" else "Save Income Record",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, borderLight),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodyLarge.copy(color = textSecondary)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

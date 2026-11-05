
package com.pfms.app.ui.screen.crypto

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pfms.app.domain.model.CryptoSide
import com.pfms.app.domain.model.CryptoTrade
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.CryptoTradingViewModel
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoTradingScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onManualGainLoss: () -> Unit,
    viewModel: CryptoTradingViewModel = hiltViewModel()
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()

    FinanceTheme {
        val palette = MaterialTheme.colorScheme

        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Cryptocurrency",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text(
                                text = "Track your crypto trades",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("‹ Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.background
                    )
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ========================================
                // BUY / SELL FORM
                // ========================================

                SectionCard {

                    Text(
                        text = "BUY / SELL · AUTOMATIC CALCULATION",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.primary
                    )

                    // BUY / SELL selection
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            CryptoSide.BUY,
                            CryptoSide.SELL
                        ).forEach { side ->

                            FilterChip(
                                selected = ui.form.side == side,
                                onClick = {
                                    viewModel.edit {
                                        it.copy(side = side)
                                    }
                                },
                                enabled = !ui.saving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                label = {
                                    Text(
                                        if (side == CryptoSide.BUY)
                                            "↓ Buy"
                                        else
                                            "↑ Sell"
                                    )
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    // Cryptocurrency asset selection
                    Text(
                        text = "Asset",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        listOf(
                            "BTC",
                            "ETH",
                            "USDT"
                        ).forEach { asset ->

                            FilterChip(
                                selected = ui.form.asset == asset,
                                onClick = {
                                    viewModel.edit {
                                        it.copy(asset = asset)
                                    }
                                },
                                enabled = !ui.saving,
                                label = {
                                    Text(asset)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    FinanceInput(
                        label = "Asset symbol (or enter another)",
                        value = ui.form.asset,
                        onChange = { asset ->
                            viewModel.edit { form ->
                                form.copy(
                                    asset = asset.uppercase()
                                )
                            }
                        }
                    )

                    // Quantity
                    FinanceInput(
                        label = "Quantity (e.g. 0.0025)",
                        value = ui.form.quantity,
                        onChange = { quantity ->
                            viewModel.edit { form ->
                                form.copy(quantity = quantity)
                            }
                        },
                        numeric = true
                    )

                    // Currency selection
                    Text(
                        text = "Transaction currency",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("LKR", "USD").forEach { unit ->

                            FilterChip(
                                selected = ui.form.currency == unit,
                                onClick = {
                                    viewModel.edit {
                                        it.copy(currency = unit)
                                    }
                                },
                                enabled = !ui.saving,
                                label = {
                                    Text(unit)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // Total trade value
                    FinanceInput(
                        label = "Total ${
                            if (ui.form.side == CryptoSide.BUY)
                                "purchase cost"
                            else
                                "sale proceeds"
                        } (${ui.form.currency})",
                        value = ui.form.gross,
                        onChange = { amount ->
                            viewModel.edit { form ->
                                form.copy(gross = amount)
                            }
                        },
                        numeric = true
                    )

                    // Trading fee
                    FinanceInput(
                        label = "Trading fee (${ui.form.currency})",
                        value = ui.form.fee,
                        onChange = { fee ->
                            viewModel.edit { form ->
                                form.copy(fee = fee)
                            }
                        },
                        numeric = true
                    )

                    // USD to LKR conversion
                    if (ui.form.currency == "USD") {

                        FinanceInput(
                            label = "Verified USD → LKR rate for trade date",
                            value = ui.form.exchangeRate,
                            onChange = { rate ->
                                viewModel.edit { form ->
                                    form.copy(exchangeRate = rate)
                                }
                            },
                            numeric = true
                        )

                        Text(
                            text = "Enter a verified historical rate. " +
                                    "PFMS stores the rate with the trade. " +
                                    "This screen does not fetch rates automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurfaceVariant
                        )
                    }

                    // Transaction date
                    FinanceInput(
                        label = "Date (YYYY-MM-DD)",
                        value = ui.form.date,
                        onChange = { date ->
                            viewModel.edit { form ->
                                form.copy(date = date)
                            }
                        }
                    )

                    // Optional note
                    FinanceInput(
                        label = "Note (optional)",
                        value = ui.form.note,
                        onChange = { note ->
                            viewModel.edit { form ->
                                form.copy(note = note)
                            }
                        }
                    )
                }

                // ========================================
                // PURCHASE / SALE RESULT
                // ========================================

                SectionCard {

                    if (ui.form.side == CryptoSide.BUY) {

                        Text(
                            text = "Your purchase",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Buying crypto adds to your recorded " +
                                    "holdings; it does not increase income.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurfaceVariant
                        )

                    } else {

                        Text(
                            text = "Estimated sale result",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Available ${ui.form.asset.uppercase()}: " +
                                    viewModel.available(),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        val preview = viewModel.preview()

                        if (preview != null) {

                            Text(
                                text = "FIFO purchase cost: " +
                                        "${money(preview.allocatedCostMinor)} LKR"
                            )

                            Text(
                                text = "Net proceeds: " +
                                        "${money(preview.netProceedsMinor)} LKR"
                            )

                            val isGain =
                                preview.signedRealizedMinor >= 0

                            Text(
                                text = "Realized ${
                                    if (isGain) "gain" else "loss"
                                }: ${money(preview.signedRealizedMinor)} LKR",
                                color = if (isGain)
                                    palette.primary
                                else
                                    palette.error,
                                style = MaterialTheme.typography.titleMedium
                            )

                        } else {

                            Text(
                                text = "Enter valid quantities and amounts " +
                                        "to preview profit or loss. " +
                                        "Previous purchase lots are required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onSurfaceVariant
                            )
                        }
                    }

                    // Error message
                    ui.error?.let { error ->

                        Text(
                            text = error,
                            color = palette.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Success message
                    if (ui.saved) {

                        Text(
                            text = "Trade saved successfully. " +
                                    "A completed sale automatically updates Crypto income.",
                            color = palette.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Save button
                    Button(
                        onClick = viewModel::save,
                        enabled = !ui.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {

                        if (ui.saving) {

                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = palette.onPrimary
                            )

                        } else {

                            Text(
                                text = if (ui.form.side == CryptoSide.BUY)
                                    "Save Purchase"
                                else
                                    "Save Sale & Calculate Profit"
                            )
                        }
                    }
                }

                // ========================================
                // CRYPTO TRADE HISTORY
                // ========================================

                SectionCard {

                    Text(
                        text = "Recent crypto trades",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (ui.loading) {

                        CircularProgressIndicator()

                    } else if (ui.trades.isEmpty()) {

                        Text(
                            text = "No trades yet. Record a purchase " +
                                    "to begin tracking holdings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurfaceVariant
                        )

                    } else {

                        ui.trades
                            .sortedWith(
                                compareByDescending<CryptoTrade> {
                                    it.date
                                }.thenByDescending {
                                    it.createdAt
                                }
                            )
                            .take(12)
                            .forEach { trade ->

                                HorizontalDivider(
                                    color = palette.outlineVariant
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {

                                        Text(
                                            text = "${trade.side.name} ${trade.asset}",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Text(
                                            text = "${trade.quantity} • ${trade.date}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.onSurfaceVariant
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {

                                        Text(
                                            text = "${money(trade.grossLkrMinor)} LKR",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        trade.realizedLkrMinor?.let { pnl ->

                                            Text(
                                                text = "P/L ${money(pnl)} LKR",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (pnl >= 0)
                                                    palette.primary
                                                else
                                                    palette.error
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }

                // Information about trade editing
                Text(
                    text = "Trades must be entered chronologically in this " +
                            "version. Existing trades cannot be edited or " +
                            "deleted until FIFO recalculation is implemented.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

// ========================================
// REUSABLE SECTION CARD
// ========================================

@Composable
private fun SectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

// ========================================
// REUSABLE FINANCE INPUT
// ========================================

@Composable
private fun FinanceInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Text(label)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric)
                KeyboardType.Decimal
            else
                KeyboardType.Text
        ),
        shape = RoundedCornerShape(13.dp)
    )
}

// ========================================
// MONEY FORMATTING
// ========================================

private fun money(
    minor: Long
): String {
    return BigDecimal.valueOf(minor, 2)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()
}

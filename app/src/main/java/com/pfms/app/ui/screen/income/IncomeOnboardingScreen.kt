package com.pfms.app.ui.screen.income

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.R
import com.pfms.app.ui.component.FinanceBrand
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.IncomeOnboardingState

/** First-use financial setup. All persistence is delegated to the ViewModel via callbacks. */
@Composable
fun IncomeOnboardingScreen(
    state: IncomeOnboardingState,
    onAddIncome: (String) -> Unit,
    onAddExpense: (String) -> Unit,
    onSaveSalary: (String) -> Unit,
    onSkip: (String) -> Unit
) = FinanceTheme {
    val colors = MaterialTheme.colorScheme
    var estimate by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mint hero matches the PFMS Login/Sign Up color palette.
            Box(
                modifier = Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFFF1FCF6), Color(0xFFE2F8F0), colors.background))
                )
            ) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { FinanceBrand() }
                        Image(
                            painter = painterResource(R.drawable.onboarding_quick_setup_art),
                            contentDescription = null,
                            modifier = Modifier.size(108.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Start tracking your finances",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Record your first transaction and see the dashboard in under 2 minutes.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Optional estimate (not a salary payout or transaction)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface,
                    shadowElevation = 3.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE6F8EF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚙", fontSize = 23.sp, color = colors.primary)
                            }
                            Column {
                                Text("Quick setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text("Enter your monthly income (optional).", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        OutlinedTextField(
                            value = estimate,
                            onValueChange = { new ->
                                if (new.length <= 16 && new.all { it.isDigit() || it == '.' }) estimate = new
                            },
                            label = { Text("My Income") },
                            prefix = { Text("LKR  ", color = colors.onSurfaceVariant) },
                            placeholder = { Text("e.g. 30000") },
                            singleLine = true,
                            enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outlineVariant,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        Text(
                            "Leave it empty if you want to add it later in Income Analytics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        state.error?.let { ErrorBanner(it) }
                    }
                }

                Text(
                    "What would you like to do first?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SetupActionCard(
                        title = "Add My First Income",
                        subtext = null,
                        icon = "↓",
                        iconBackground = Color(0xFFD5F5E6),
                        iconForeground = Color(0xFF007E55),
                        background = Color(0xFFE6F8EF),
                        enabled = !state.saving,
                        onClick = { onAddIncome(estimate) }
                    )
                    SetupActionCard(
                        title = "Add My First Expense",
                        subtext = null,
                        icon = "↑",
                        iconBackground = Color(0xFFFBE9E8),
                        iconForeground = Color(0xFFBB4444),
                        background = colors.surface,
                        enabled = !state.saving,
                        onClick = { onAddExpense(estimate) }
                    )
                    SetupActionCard(
                        title = "Save My Salary",
                        subtext = "Set your usual salary and confirm each payout.",
                        icon = "₨",
                        iconBackground = Color(0xFFE6F7F0),
                        iconForeground = colors.primary,
                        background = colors.surface,
                        enabled = !state.saving,
                        onClick = { onSaveSalary(estimate) }
                    )
                }
                HorizontalDivider(color = colors.outlineVariant)
                TextButton(
                    onClick = { onSkip(estimate) },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Skip for Now → Dashboard", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                }
                Text(
                    "You can add income, expenses, and salary details anytime from the dashboard.",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (state.saving) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Saving setup…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupActionCard(
    title: String,
    subtext: String?,
    icon: String,
    iconBackground: Color,
    iconForeground: Color,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = iconForeground)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
                if (subtext != null) {
                    Text(subtext, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Text("›", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
        }
    }
}

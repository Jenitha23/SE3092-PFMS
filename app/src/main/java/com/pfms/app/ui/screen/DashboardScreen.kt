package com.pfms.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.model.SessionState
import com.pfms.app.ui.component.ArrowDownVector
import com.pfms.app.ui.component.ArrowUpVector
import com.pfms.app.ui.component.BellVectorIcon
import com.pfms.app.ui.component.CalendarVectorIcon
import com.pfms.app.ui.component.DiningVectorIcon
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.ExpensesNavIcon
import com.pfms.app.ui.component.GoalVectorIcon
import com.pfms.app.ui.component.GoalsNavIcon
import com.pfms.app.ui.component.HomeNavIcon
import com.pfms.app.ui.component.IncomeNavIcon
import com.pfms.app.ui.component.InsightsNavIcon
import com.pfms.app.ui.component.LaptopVectorIcon
import com.pfms.app.ui.component.SavingsVector
import com.pfms.app.ui.component.SpendingDonutChart
import com.pfms.app.ui.component.UserVectorIcon
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.viewmodel.SessionViewModel
import com.pfms.app.viewmodel.SettingsViewModel

/**
 * Modern Fintech Dashboard matching Reference UI 2 exactly:
 * - Emerald top header with user greeting, display name + wave, date, bell, and avatar.
 * - Floating Monthly Overview card (Income ↑, Expenses ↓, Savings).
 * - Spending Breakdown card with two-tone donut chart (Committed 45%, Discretionary 55%).
 * - Savings Goal card with target laptop item and 23% progress bar.
 * - Recent Activity card with Salary (+ LKR 130,000) and Dining (- LKR 2,500).
 * - Pinned 5-tab bottom navigation (Home, Income, Expenses, Goals, Insights).
 * - All authentication, session state, settings navigation, and logout dialogs strictly preserved.
 */
@Composable
fun DashboardScreen(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val logoutResult by sessionViewModel.logoutResult.collectAsStateWithLifecycle()

    val authUser = (session as? SessionState.Authenticated)?.user
    val rawName = profile?.displayName?.ifBlank { null }
        ?: authUser?.displayName?.ifBlank { null }
        ?: "User"
    val displayName = rawName.substringBefore("@")
    val email = authUser?.email
        ?: profile?.email
        ?: ""

    val logoutError = (logoutResult as? LogoutResult.Failure)?.message

    Scaffold(
        bottomBar = {
            // ── 5-Tab Bottom Navigation Bar matching Reference UI 2 ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home Tab (Active)
                    DashboardBottomTab(
                        icon = { HomeNavIcon(tint = EmeraldPrimary, filled = true) },
                        label = "Home",
                        isActive = true,
                        onClick = { /* Active destination */ }
                    )

                    // Income Tab
                    DashboardBottomTab(
                        icon = { IncomeNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Income",
                        isActive = false,
                        onClick = null
                    )

                    // Expenses Tab
                    DashboardBottomTab(
                        icon = { ExpensesNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Expenses",
                        isActive = false,
                        onClick = null
                    )

                    // Goals Tab
                    DashboardBottomTab(
                        icon = { GoalsNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Goals",
                        isActive = false,
                        onClick = null
                    )

                    // Insights Tab
                    DashboardBottomTab(
                        icon = { InsightsNavIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        label = "Insights",
                        isActive = false,
                        onClick = null
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top Emerald Header + Floating Monthly Overview Card ───
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Background Emerald Header matching Reference UI 2
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    color = EmeraldPrimary,
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "Good morning,",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFD0EDE5)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$displayName 👋",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tue, 24 Sep 2025",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA7DFD0)
                                )
                            }

                            // Right action buttons: Notification Bell & Profile Avatar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Bell button
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        BellVectorIcon(tint = Color.White)
                                    }
                                }

                                // Profile Avatar button (navigates to Settings)
                                Surface(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { onNavigateToSettings() },
                                    shape = CircleShape,
                                    color = Color.White
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        UserVectorIcon(
                                            modifier = Modifier.size(20.dp),
                                            tint = EmeraldPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Card 1: Monthly Overview (Floating / Overlapping Header) ──
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 115.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Card Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Overview",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                CalendarVectorIcon(
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "September 2025",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 3 Metrics Columns
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Column 1: Income
                            Column(horizontalAlignment = Alignment.Start) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = EmeraldPrimary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        ArrowUpVector(tint = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "LKR 175,000",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Column 2: Expenses
                            Column(horizontalAlignment = Alignment.Start) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFFFECEF)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        ArrowDownVector(tint = Color(0xFFE84C6A))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Expenses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "LKR 82,500",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFFE84C6A)
                                )
                            }

                            // Column 3: Savings
                            Column(horizontalAlignment = Alignment.Start) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFF3E8FF)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SavingsVector(tint = Color(0xFF7A4DD8))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Savings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "LKR 92,500",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF7A4DD8)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Main Content Area ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                // Logout Error Banner (if any)
                ErrorBanner(message = logoutError)
                if (logoutError != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ── Card 2: Spending Breakdown ─────────────────────────
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Spending Breakdown",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "View details →",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = EmeraldPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Donut Chart & Legend Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut Chart
                            SpendingDonutChart(
                                committedColor = EmeraldPrimary,
                                discretionaryColor = Color(0xFF7A4DD8)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "LKR",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "82,500",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Legend Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Item 1: Committed
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(EmeraldPrimary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Committed",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "LKR 37,125",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "45%",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Item 2: Discretionary
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(Color(0xFF7A4DD8), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Discretionary",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "LKR 45,375",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "55%",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Card 3: Savings Goal ────────────────────────────────
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = EmeraldPrimary.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        GoalVectorIcon(tint = EmeraldPrimary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Savings Goal",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "View goal →",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = EmeraldPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Item row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    LaptopVectorIcon(tint = Color(0xFF334155))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "MacBook Pro M4",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "LKR 490,000",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Bar & Percentage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFFE2E8F0))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.23f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(EmeraldPrimary)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "23%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subtext: saved vs remaining
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LKR 112,000 saved",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "LKR 378,000 remaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Card 4: Recent Activity ────────────────────────────
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Activity",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "View all →",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = EmeraldPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Transaction 1: Salary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                color = Color(0xFFE8F6F2)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    ArrowDownVector(
                                        modifier = Modifier.size(16.dp),
                                        tint = EmeraldPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Salary",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "22 Sep 2025 • Confirmed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "+ LKR 130,000",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = EmeraldPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Transaction 2: Dining
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                color = Color(0xFFFFECEF)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    DiningVectorIcon(tint = Color(0xFFE84C6A))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dining",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "21 Sep 2025 • Card",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "- LKR 2,500",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFE84C6A)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // ── Unsynchronized Data Warning Dialog ─────────────────────────────
    if (logoutResult is LogoutResult.UnsynchronizedDataWarning) {
        AlertDialog(
            onDismissRequest = { sessionViewModel.consumeLogoutResult() },
            icon = { Text("⚠", style = MaterialTheme.typography.headlineSmall) },
            title = { Text("Unsynchronized Data") },
            text = {
                Text(
                    "You have offline financial entries that have not synced with the cloud yet. " +
                    "Logging out will discard unsaved local changes. Do you still want to log out?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sessionViewModel.logout(force = true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Log Out Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionViewModel.consumeLogoutResult() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Bottom navigation tab item matching Reference UI 2.
 */
@Composable
private fun DashboardBottomTab(
    icon: @Composable () -> Unit,
    label: String,
    isActive: Boolean,
    onClick: (() -> Unit)?
) {
    val activeColor = EmeraldPrimary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val color = if (isActive) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = onClick != null,
                onClick = { onClick?.invoke() }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            ),
            color = color
        )
    }
}

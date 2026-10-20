package com.pfms.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * In-app privacy information (FR-06).
 * Review the wording against your final deployed service and legal policy before release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp, vertical = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "How PFMS handles your information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            PolicySection(
                "Account and profile",
                "PFMS uses Firebase Authentication for email/password and Google sign-in. " +
                        "Your account identifier and profile preferences support access to your own records."
            )
            PolicySection(
                "Financial records",
                "Income, expenses, savings goals, categories and recurring items you enter " +
                        "are stored under your account in Cloud Firestore."
            )
            PolicySection(
                "On-device storage",
                "PFMS caches financial information in an on-device Room database and stores " +
                        "some preferences locally. Changes made offline may be queued for later sync. " +
                        "Avoid sharing an unlocked device with others."
            )
            PolicySection(
                "Other services",
                "Firebase provides authentication and cloud storage. When you request currency " +
                        "conversion, PFMS contacts external exchange-rate services. Google authentication " +
                        "uses Google's account sign-in services."
            )
            PolicySection(
                "Your choices",
                "You can edit records, update account settings and request account deletion from " +
                        "Settings. Deletion and local cleanup depend on successfully completing the " +
                        "app's deletion process; verify it completes before assuming data is erased."
            )
            PolicySection(
                "Security",
                "PFMS uses Firebase authentication and Firestore access rules to restrict access " +
                        "to account data. No app can guarantee absolute security."
            )
            Text(
                "This is an in-app privacy summary. Before publishing PFMS, your team should " +
                        "review the final policy and include accurate contact, retention, and " +
                        "data-deletion details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

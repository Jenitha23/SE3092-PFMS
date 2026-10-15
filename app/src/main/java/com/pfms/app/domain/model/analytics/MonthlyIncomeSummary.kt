package com.pfms.app.domain.model.analytics

/**
 * Breakdown of realized income for a specific calendar month.
 *
 * Rules:
 * - [salaryMinor]: Realized salary income.
 * - [freelanceMinor]: Only includes invoices with paymentStatus == "Received" (excludes Expected).
 * - [adsenseMinor]: Realized Google AdSense income.
 * - [cryptoNetMinor]: Net crypto realized income (Gains - Losses). Can be negative.
 * - [otherMinor]: All other realized income sources.
 * - [totalRealizedMinor]: Sum of all realized categories: salary + freelance + adsense + cryptoNet + other.
 */
data class MonthlyIncomeSummary(
    val yearMonth: String,
    val totalRealizedMinor: Long,
    val salaryMinor: Long,
    val freelanceMinor: Long,
    val adsenseMinor: Long,
    val cryptoNetMinor: Long,
    val otherMinor: Long
)

package com.pfms.app.domain.model.analytics

/**
 * Breakdown of income into Regular (fixed predictable income, e.g. Salary)
 * and Variable (fluctuating income, e.g. Freelance, AdSense, Crypto, Other).
 */
data class RegularVariableIncome(
    val regularMinor: Long,
    val variableMinor: Long,
    val totalMinor: Long,
    val regularPercentage: Double,
    val variablePercentage: Double
)

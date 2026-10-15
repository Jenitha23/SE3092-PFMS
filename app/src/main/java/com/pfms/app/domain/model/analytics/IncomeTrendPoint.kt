package com.pfms.app.domain.model.analytics

/**
 * A single data point representing total realized income for a calendar month.
 * Ordered chronologically for charting and trend analysis.
 */
data class IncomeTrendPoint(
    val yearMonth: String,
    val amountMinor: Long
)

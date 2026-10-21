package com.pfms.app.domain.usecase

/** Shared guards for ratios and visual progress (FR-34). Signed financial ratios remain signed. */
object SafeCalculations {
    fun ratio(numerator: Double, denominator: Double): Double {
        if (!numerator.isFinite() || !denominator.isFinite() || denominator <= 0.0) return 0.0
        return (numerator / denominator).takeIf { it.isFinite() } ?: 0.0
    }

    fun percentage(part: Long, total: Long): Double =
        (ratio(part.toDouble(), total.toDouble()) * 100.0).takeIf { it.isFinite() } ?: 0.0

    fun progressFraction(percent: Double): Float = ratio(percent, 100.0).coerceIn(0.0, 1.0).toFloat()

    fun finiteOrZero(value: Double): Double = value.takeIf { it.isFinite() } ?: 0.0
}

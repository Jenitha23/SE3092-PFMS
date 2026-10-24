package com.pfms.app.data.exchange

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Device-wide public USD/LKR reference rates. No private user transaction data is cached here. */
@Singleton
class ExchangeRateCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("pfms_exchange_rates_v1", Context.MODE_PRIVATE)

    fun put(date: String, rate: BigDecimal) {
        if (runCatching { LocalDate.parse(date) }.isFailure || rate <= BigDecimal.ZERO) return
        val lastDate = preferences.getString("usd_lkr_latest_date", null)
        val editor = preferences.edit().putString("usd_lkr_$date", rate.toPlainString())
        if (lastDate == null || date >= lastDate) {
            editor.putString("usd_lkr_latest_date", date)
        }
        editor.apply()
    }

    fun forDate(date: String): BigDecimal? = preferences.getString("usd_lkr_$date", null)
        ?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

    /** Only return a dated latest quote; caller decides whether age is acceptable. */
    fun latest(): Pair<String, BigDecimal>? {
        val date = preferences.getString("usd_lkr_latest_date", null) ?: return null
        val rate = forDate(date) ?: return null
        return date to rate
    }
}

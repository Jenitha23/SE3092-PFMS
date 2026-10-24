
package com.pfms.app.data.exchange

import com.google.gson.Gson
import com.pfms.app.data.model.ExchangeRateResponse
import com.pfms.app.data.model.OpenErApiResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of an exchange-rate lookup.
 *
 * [rate] is the BigDecimal exchange rate (e.g. 305.42 for 1 USD = 305.42 LKR).
 * [rateDate] is the date the rate applies to (ISO-8601 string from the API).
 */
sealed class ExchangeRateResult {
    data class Success(
        val rate: BigDecimal,
        val rateDate: String,
        val isCached: Boolean = false
    ) : ExchangeRateResult()

    data class Failure(
        val message: String
    ) : ExchangeRateResult()
}

/**
 * Fetches USD → LKR exchange rates with real-time and historical support.
 *
 * - Primary live source: open.er-api.com (100% free, no API key, reliable LKR support)
 * - Secondary source: cdn.jsdelivr.net currency-api (supports dates + LKR)
 *
 * If the network call fails for any reason the caller can fall back
 * to manual exchange-rate entry (handled in the ViewModel / UI layer).
 */
@Singleton
class ExchangeRateService @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Hilt member injection keeps the existing zero-argument constructor/test doubles compatible.
    @Inject lateinit var cache: ExchangeRateCache

    private fun remember(result: ExchangeRateResult): ExchangeRateResult {
        if (result is ExchangeRateResult.Success && !result.isCached && ::cache.isInitialized) {
            cache.put(result.rateDate, result.rate)
        }
        return result
    }

    private fun cachedLatest(): ExchangeRateResult? {
        if (!::cache.isInitialized) return null
        val (date, rate) = cache.latest() ?: return null
        val age = runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(date), LocalDate.now(java.time.ZoneId.of("Asia/Colombo")))
        }.getOrNull() ?: return null
        // An old quote becomes misleading. Ask for a manual rate instead.
        if (age < 0 || age > 14) return null
        return ExchangeRateResult.Success(rate, date, isCached = true)
    }

    private fun cachedHistorical(date: String): ExchangeRateResult? {
        if (!::cache.isInitialized) return null
        return cache.forDate(date)?.let { ExchangeRateResult.Success(it, date, isCached = true) }
    }

    /**
     * Fetch the latest USD → LKR exchange rate.
     */
    fun fetchLatestRate(): ExchangeRateResult {
        // Try open.er-api.com first (real-time USD base)
        val primaryResult = fetchFromOpenErApi("https://open.er-api.com/v6/latest/USD")
        if (primaryResult is ExchangeRateResult.Success) {
            return remember(primaryResult)
        }

        // Fallback: jsdelivr latest
        val fallbackResult = fetchFromJsDelivr("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json")
        if (fallbackResult is ExchangeRateResult.Success) {
            return remember(fallbackResult)
        }

        return cachedLatest() ?: primaryResult
    }

    /**
     * Fetch a historical USD → LKR exchange rate for the given date.
     *
     * @param date ISO-8601 date string, e.g. "2026-03-01".
     */
    fun fetchHistoricalRate(date: String): ExchangeRateResult {
        // Try historical CDN
        if (runCatching { LocalDate.parse(date) }.isFailure) {
            return ExchangeRateResult.Failure("Invalid historical date.")
        }
        val cdnUrl = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$date/v1/currencies/usd.json"
        val cdnResult = fetchFromJsDelivr(cdnUrl)
        if (cdnResult is ExchangeRateResult.Success) {
            return remember(cdnResult)
        }
        cachedHistorical(date)?.let { return it }

        // Never silently fall back to today's rate for a historical entry.
        return ExchangeRateResult.Failure(
            "Historical rate for $date is unavailable. Please enter the rate manually."
        )
    }

    private fun fetchFromOpenErApi(url: String): ExchangeRateResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return ExchangeRateResult.Failure("The exchange rate service is unavailable. Try again or enter a rate manually.")
            }

            val body = response.body?.string()
                ?: return ExchangeRateResult.Failure("Unable to get an exchange rate. Try again or enter it manually.")

            val parsed = gson.fromJson(body, OpenErApiResponse::class.java)
            if (parsed.result != "success" || parsed.base_code != "USD") {
                return ExchangeRateResult.Failure("Unexpected exchange-rate response.")
            }
            val lkr = parsed.rates["LKR"]
                ?: return ExchangeRateResult.Failure("This exchange rate is unavailable. Enter it manually.")

            ExchangeRateResult.Success(
                rate = BigDecimal.valueOf(lkr),
                rateDate = runCatching {
                    ZonedDateTime.parse(parsed.time_last_update_utc, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toLocalDate().toString()
                }.getOrElse { "" }
            )
        } catch (e: IOException) {
            ExchangeRateResult.Failure(
                "Network error: Unable to reach exchange rate service. Check your internet connection."
            )
        } catch (e: Exception) {
            ExchangeRateResult.Failure("Unable to get an exchange rate. Try again or enter it manually.")
        }
    }

    private fun fetchFromJsDelivr(url: String): ExchangeRateResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return ExchangeRateResult.Failure("The exchange rate service is unavailable. Try again or enter a rate manually.")
            }

            val body = response.body?.string()
                ?: return ExchangeRateResult.Failure("Unable to get an exchange rate. Try again or enter it manually.")

            val parsed = gson.fromJson(body, ExchangeRateResponse::class.java)
            val usdMap = parsed.usd
            val lkr = usdMap?.get("lkr")
                ?: return ExchangeRateResult.Failure("This exchange rate is unavailable. Enter it manually.")

            ExchangeRateResult.Success(
                rate = BigDecimal.valueOf(lkr),
                rateDate = parsed.date
            )
        } catch (e: Exception) {
            ExchangeRateResult.Failure("Unable to get an exchange rate. Try again or enter it manually.")
        }
    }
}


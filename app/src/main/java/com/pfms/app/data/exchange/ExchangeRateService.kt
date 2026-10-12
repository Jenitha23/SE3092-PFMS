
package com.pfms.app.data.exchange

import com.google.gson.Gson
import com.pfms.app.data.model.ExchangeRateResponse
import com.pfms.app.data.model.OpenErApiResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
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
        val rateDate: String
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

    /**
     * Fetch the latest USD → LKR exchange rate.
     */
    fun fetchLatestRate(): ExchangeRateResult {
        // Try open.er-api.com first (real-time USD base)
        val primaryResult = fetchFromOpenErApi("https://open.er-api.com/v6/latest/USD")
        if (primaryResult is ExchangeRateResult.Success) {
            return primaryResult
        }

        // Fallback: jsdelivr latest
        val fallbackResult = fetchFromJsDelivr("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json")
        if (fallbackResult is ExchangeRateResult.Success) {
            return fallbackResult
        }

        return primaryResult
    }

    /**
     * Fetch a historical USD → LKR exchange rate for the given date.
     *
     * @param date ISO-8601 date string, e.g. "2026-03-01".
     */
    fun fetchHistoricalRate(date: String): ExchangeRateResult {
        // Try historical CDN
        val cdnUrl = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$date/v1/currencies/usd.json"
        val cdnResult = fetchFromJsDelivr(cdnUrl)
        if (cdnResult is ExchangeRateResult.Success) {
            return cdnResult
        }

        // Fall back to latest rate
        return fetchLatestRate()
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
                return ExchangeRateResult.Failure("API returned HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: return ExchangeRateResult.Failure("Empty response from exchange rate API.")

            val parsed = gson.fromJson(body, OpenErApiResponse::class.java)
            val lkr = parsed.rates["LKR"]
                ?: return ExchangeRateResult.Failure("LKR rate not found in API response.")

            ExchangeRateResult.Success(
                rate = BigDecimal.valueOf(lkr),
                rateDate = parsed.time_last_update_utc?.take(16) ?: ""
            )
        } catch (e: IOException) {
            ExchangeRateResult.Failure(
                "Network error: Unable to reach exchange rate service. Check your internet connection."
            )
        } catch (e: Exception) {
            ExchangeRateResult.Failure("Error fetching exchange rate: ${e.message}")
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
                return ExchangeRateResult.Failure("API returned HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: return ExchangeRateResult.Failure("Empty response from exchange rate API.")

            val parsed = gson.fromJson(body, ExchangeRateResponse::class.java)
            val usdMap = parsed.usd
            val lkr = usdMap?.get("lkr")
                ?: return ExchangeRateResult.Failure("LKR rate not found in fallback API.")

            ExchangeRateResult.Success(
                rate = BigDecimal.valueOf(lkr),
                rateDate = parsed.date
            )
        } catch (e: Exception) {
            ExchangeRateResult.Failure("Error: ${e.message}")
        }
    }
}


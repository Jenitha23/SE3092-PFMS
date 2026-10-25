package com.pfms.app.data.exchange

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Exercises production service/cache logic with intercepted HTTP and a preferences test double. */
class ExchangeRateOfflineServiceTest {
    private val values = mutableMapOf<String, String>()
    private val preferences = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val context = mock<Context>()
    private val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
    private var online = true
    private var rate = "305.42"
    private var responseDate = today.toString()
    private var primaryValid = true
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        if (!online) throw IOException("Offline")
        val request = chain.request()
        val body = if (request.url.host == "open.er-api.com") {
            val date = LocalDate.parse(responseDate).atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
            """{"result":"success","base_code":"USD","time_last_update_utc":"$date","rates":{"LKR":${if (primaryValid) rate else "0"}}}"""
        } else """{"date":"$responseDate","usd":{"lkr":$rate}}"""
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    @Before fun setup() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(preferences)
        whenever(preferences.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument(0)] ?: it.getArgument<String?>(1) }
        whenever(preferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenAnswer { values[it.getArgument(0)] = it.getArgument(1); editor }
    }
    private fun service() = ExchangeRateService(client).also { it.cache = ExchangeRateCache(context) }

    @Test fun onlineQuoteIsDatedAndCachedThenOfflineQuoteIsMarkedCached() {
        val fetched = service().fetchLatestRate() as ExchangeRateResult.Success
        assertEquals(BigDecimal("305.42"), fetched.rate)
        assertEquals(today.toString(), fetched.rateDate)
        assertFalse(fetched.isCached)
        online = false
        val cached = service().fetchLatestRate() as ExchangeRateResult.Success
        assertEquals(fetched.rate, cached.rate); assertEquals(fetched.rateDate, cached.rateDate)
        assertTrue(cached.isCached)
    }

    @Test fun latestCacheIncludesDayFourteenButRejectsExpiredAndFutureQuotes() {
        online = false
        val cache = ExchangeRateCache(context)
        cache.put(today.minusDays(14).toString(), BigDecimal("300.25"))
        assertTrue((service().fetchLatestRate() as ExchangeRateResult.Success).isCached)
        values.clear(); cache.put(today.minusDays(15).toString(), BigDecimal("300.25"))
        assertTrue(service().fetchLatestRate() is ExchangeRateResult.Failure)
        values.clear(); cache.put(today.plusDays(1).toString(), BigDecimal("300.25"))
        assertTrue(service().fetchLatestRate() is ExchangeRateResult.Failure)
    }

    @Test fun missingCacheRequiresManualFallback() {
        online = false
        assertTrue(service().fetchLatestRate() is ExchangeRateResult.Failure)
    }

    @Test fun historicalCacheMustMatchExactlyEvenWhenLatestIsFresh() {
        val date = today.minusDays(90).toString()
        val cache = ExchangeRateCache(context)
        cache.put(date, BigDecimal("301.12")); cache.put(today.toString(), BigDecimal("305.42"))
        online = false
        val historical = service().fetchHistoricalRate(date) as ExchangeRateResult.Success
        assertEquals(date, historical.rateDate); assertTrue(historical.isCached)
        assertEquals(BigDecimal("301.12"), historical.rate)
        assertTrue(service().fetchHistoricalRate(today.minusDays(91).toString()) is ExchangeRateResult.Failure)
        assertTrue(service().fetchHistoricalRate("invalid") is ExchangeRateResult.Failure)
    }

    @Test fun wrongHistoricalNetworkDateDoesNotPolluteCacheOrReplaceExactHistoricalQuote() {
        val date = today.minusDays(30).toString()
        ExchangeRateCache(context).put(date, BigDecimal("299.50"))
        val result = service().fetchHistoricalRate(date) as ExchangeRateResult.Success
        assertTrue(result.isCached); assertEquals(date, result.rateDate)
        assertNull(ExchangeRateCache(context).forDate(today.toString()))
        assertTrue(service().fetchHistoricalRate(today.minusDays(31).toString()) is ExchangeRateResult.Failure)
    }

    @Test fun reconnectFetchesAndStoresANewQuoteAcrossServiceRecreation() {
        service().fetchLatestRate()
        online = false
        assertEquals(BigDecimal("305.42"), (service().fetchLatestRate() as ExchangeRateResult.Success).rate)
        online = true; rate = "310.75"
        val refreshed = service().fetchLatestRate() as ExchangeRateResult.Success
        assertFalse(refreshed.isCached); assertEquals(BigDecimal("310.75"), refreshed.rate)
        online = false
        assertEquals(refreshed.rate, (service().fetchLatestRate() as ExchangeRateResult.Success).rate)
    }

    @Test fun invalidQuotesCannotBeCachedAndFallbackIsStillAvailable() {
        primaryValid = false
        assertEquals(BigDecimal("305.42"), (service().fetchLatestRate() as ExchangeRateResult.Success).rate)
        values.clear(); rate = "-1"
        assertTrue(service().fetchLatestRate() is ExchangeRateResult.Failure)
        assertNull(ExchangeRateCache(context).latest())
        ExchangeRateCache(context).put("invalid", BigDecimal("300"))
        ExchangeRateCache(context).put(today.toString(), BigDecimal.ZERO)
        assertNull(ExchangeRateCache(context).latest())
    }
}

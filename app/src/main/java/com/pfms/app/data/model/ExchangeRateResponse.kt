
package com.pfms.app.data.model

/**
  * Fallback exchange-rate API models.
  */
data class ExchangeRateResponse(
    val date: String = "",
    val usd: Map<String, Double>? = null
)


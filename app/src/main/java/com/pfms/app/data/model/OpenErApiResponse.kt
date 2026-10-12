package com.pfms.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Models the free open.er-api.com response:
 * {
 *   "result": "success",
 *   "time_last_update_utc": "Mon, 12 Oct 2026 00:02:32 +0000",
 *   "base_code": "USD",
 *   "rates": { "LKR": 330.54, ... }
 * }
 */
data class OpenErApiResponse(
    val result: String = "",
    val time_last_update_utc: String? = null,
    val base_code: String = "USD",
    val rates: Map<String, Double> = emptyMap()
)

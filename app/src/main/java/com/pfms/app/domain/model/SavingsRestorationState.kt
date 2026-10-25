package com.pfms.app.domain.model

/** Remote restoration status; Room remains the source of savings balances and ledger data. */
data class SavingsRestorationState(
    val isRefreshing: Boolean = false,
    val error: Throwable? = null
)

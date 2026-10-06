package com.pfms.app.domain.model

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val baseCurrency: String = "LKR",
    val defaultPaymentMethod: String? = null
)

package com.pfms.app.data.model

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val baseCurrency: String = "LKR",
    val defaultPaymentMethod: String? = null,
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)
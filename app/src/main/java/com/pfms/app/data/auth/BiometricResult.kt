package com.pfms.app.data.auth

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object UseFallback : BiometricResult
    data class Error(val message: String) : BiometricResult
}
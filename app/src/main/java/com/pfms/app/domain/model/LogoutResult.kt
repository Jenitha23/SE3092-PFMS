package com.pfms.app.domain.model

sealed class LogoutResult {

    data object Success : LogoutResult()

    data object UnsynchronizedDataWarning : LogoutResult()

    data class Failure(
        val message: String
    ) : LogoutResult()
}
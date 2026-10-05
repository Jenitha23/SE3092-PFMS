package com.pfms.app.domain.model

class AuthException(
    override val message: String
) : Exception(message)
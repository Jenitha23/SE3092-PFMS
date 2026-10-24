package com.pfms.app.viewmodel

sealed interface AuthAction {
    data class EmailChanged(val value: String) : AuthAction
    data class NameChanged(val value: String) : AuthAction
    data class PasswordChanged(val value: String) : AuthAction
    data class RememberMeChanged(val value: Boolean) : AuthAction
    data class TermsAcceptedChanged(val value: Boolean) : AuthAction
    data object TogglePasswordVisibility : AuthAction
    data object Login : AuthAction
    data object Register : AuthAction
    data object ShowTerms : AuthAction
    data object DismissTerms : AuthAction
}

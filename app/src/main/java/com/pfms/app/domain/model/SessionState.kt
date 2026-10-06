package com.pfms.app.domain.model

/** What the navigation graph needs to pick its start destination (FR-02 session persistence). */
sealed interface SessionState {
    /** Firebase has not reported the persisted session yet. Show a splash, not the login screen. */
    data object Loading : SessionState
    data object Unauthenticated : SessionState
    data class Authenticated(val user: AuthUser) : SessionState
}

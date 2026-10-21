package com.pfms.app.data.error

import android.database.sqlite.SQLiteFullException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pfms.app.domain.model.AuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

enum class ErrorAction { LOAD, SAVE, DELETE }

data class OperationError(val message: String, val canRetry: Boolean)

/** Only approved text crosses the presentation boundary; exception messages are never displayed. */
object OperationErrors {
    fun present(error: Throwable?, action: ErrorAction = ErrorAction.SAVE): OperationError {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        val causes = generateSequence(error) { it.cause }.take(12).toList()
        val firestore = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        val code = firestore?.code?.name
        return when {
            error is AuthException -> OperationError(error.message, false)
            causes.any { it is IllegalStateException && it.message == "An active savings goal already exists" } ->
                OperationError("You already have an active savings goal. Edit or archive it before creating another.", false)
            causes.any { it is IllegalStateException && it.message in setOf("Authentication required", "Authentication required: no active user session", "No authenticated user session", "User must be authenticated") } ->
                OperationError("Please sign in again to continue.", false)
            code == "UNAUTHENTICATED" ->
                OperationError("Please sign in again to continue.", false)
            code == "PERMISSION_DENIED" || causes.any { it is SecurityException } ->
                OperationError("You don't have access to this data. Please check that you're signed in to the right account.", false)
            code == "NOT_FOUND" || causes.any { it is NoSuchElementException } ->
                OperationError("This record is no longer available. Return to the list and choose another record.", false)
            causes.any { it is SQLiteFullException } ->
                OperationError("Your device storage is full. Free some space before saving again.", false)
            code in setOf("INVALID_ARGUMENT", "FAILED_PRECONDITION") ||
                causes.any { it is IllegalArgumentException || it is ArithmeticException } ->
                OperationError("These changes couldn't be saved. Check the entered details before trying again.", false)
            causes.any { it is TimeoutCancellationException } ->
                OperationError("The request timed out. Please check your internet connection and try again.", true)
            causes.any { it is IOException || it is FirebaseNetworkException } ||
                code in setOf("UNAVAILABLE", "DEADLINE_EXCEEDED") ->
                OperationError("Please check your internet connection and try again.", true)
            else -> OperationError(when (action) {
                ErrorAction.LOAD -> "Unable to load your data. Please try again."
                ErrorAction.SAVE -> "Something went wrong while saving. Please try again."
                ErrorAction.DELETE -> "Unable to delete this record. Please try again."
            }, true)
        }
    }
}

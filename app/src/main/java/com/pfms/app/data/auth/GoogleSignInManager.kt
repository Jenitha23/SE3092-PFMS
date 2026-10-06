package com.pfms.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pfms.app.domain.model.AuthErrorType
import com.pfms.app.domain.model.AuthException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class GoogleSignInManager @Inject constructor(
    private val credentialManager: CredentialManager
) {

    /**
     * @param context must be an Activity context, Credential Manager shows its bottom sheet from it.
     * The context is only used during this call and is never stored.
     */
    suspend fun getGoogleIdToken(context: Context): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(com.pfms.app.R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credential = credentialManager.getCredential(context, request).credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.failure(failure("Google sign-in failed. Please try again."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Result.failure(failure("Google sign-in was cancelled."))
        } catch (e: NoCredentialException) {
            Result.failure(failure("No Google account was found on this device."))
        } catch (e: Exception) {
            Result.failure(failure("Google sign-in failed. Please try again."))
        }
    }

    private fun failure(message: String) = AuthException(message, AuthErrorType.UNKNOWN)
}

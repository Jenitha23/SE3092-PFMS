package com.pfms.app.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
            // "Sign in with Google" button flow: shows the account picker AND lets the user
            // add an account, unlike GetGoogleIdOption which fails when none exists.
            val signInOption = GetSignInWithGoogleOption.Builder(
                context.getString(com.pfms.app.R.string.default_web_client_id)
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
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
            Result.failure(
                failure("No Google account was found on this device. Add one in Settings > Passwords & accounts, then try again.")
            )
        } catch (e: Exception) {
            // Check Logcat (tag GoogleSignIn): wrong SHA-1 or web client ID shows up here.
            Log.e("GoogleSignIn", "Credential Manager failed", e)
            Result.failure(failure("Google sign-in failed. Please try again."))
        }
    }

    /** Clear the provider's active credential session after the user explicitly logs out.
     * This does not delete the Google account from the device; it allows the next sign-in to
     * show the account chooser again instead of reusing the previous credential state.
     */
    suspend fun clearCredentialState() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Firebase sign-out has already succeeded; credential-state cleanup is best-effort.
            Log.w("GoogleSignIn", "Could not clear Credential Manager state", e)
        }
    }

    private fun failure(message: String) = AuthException(message, AuthErrorType.UNKNOWN)
}

package com.pfms.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject

class GoogleSignInManager @Inject constructor(
    private val credentialManager: CredentialManager
) {

    suspend fun getGoogleIdToken(
        context: Context
    ): Result<String> {

        return try {

            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(
                    context.getString(
                        com.pfms.app.R.string.default_web_client_id
                    )
                )
                .setFilterByAuthorizedAccounts(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context,
                request
            )

            val credential = result.credential

            val googleIdTokenCredential =
                GoogleIdTokenCredential.createFrom(credential.data)

            Result.success(
                googleIdTokenCredential.idToken
            )

        } catch (exception: Exception) {

            Result.failure(exception)
        }
    }
}
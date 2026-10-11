package com.pfms.app.data.repository

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.pfms.app.data.firebase.BULK_WRITE_TIMEOUT_MS
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.firebase.UserProfileInitializer
import com.pfms.app.data.firebase.awaitOnline
import com.pfms.app.data.firebase.toAuthException
import com.pfms.app.domain.model.AuthErrorType
import com.pfms.app.domain.model.AuthException
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.PaymentMethods
import com.pfms.app.domain.model.ReauthCredential
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.validation.AuthValidator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val profileInitializer: UserProfileInitializer
) : AuthRepository {

    // ------------------------------------------------------------------ session (FR-02)

    /** Firebase persists the session on disk, so this emits the stored user after an app restart. */
    private val firebaseUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * True while a Google login/sign-up is still being verified (new vs existing account, profile
     * setup). Firebase reports the user as signed in the moment the credential is accepted; the
     * session must not expose that user until the checks pass, otherwise the dashboard would flash
     * and then disappear when we reject or sign out the account.
     */
    private val googleCheckInProgress = MutableStateFlow(false)

    override val currentUser: Flow<AuthUser?> =
        combine(firebaseUser, googleCheckInProgress) { user, checking -> if (checking) null else user }
            .distinctUntilChanged()

    // ------------------------------------------------------------------ FR-01 register

    override suspend fun register(
        displayName: String,
        email: String,
        password: String
    ): Result<AuthUser> {
        val name = displayName.trim()
        val mail = email.trim()

        AuthValidator.validateRegistration(name, mail, password).first?.let {
            return Result.failure(AuthException(it, AuthErrorType.VALIDATION))
        }

        return authCall {
            val firebaseUser = firebaseAuth.createUserWithEmailAndPassword(mail, password)
                .await().user
                ?: throw AuthException(
                    "Your account could not be created. Please try again.",
                    AuthErrorType.UNKNOWN
                )

            firebaseUser.updateProfile(userProfileChangeRequest { this.displayName = name }).await()

            initialiseProfileOrSignOut(firebaseUser, name)

            firebaseUser.toAuthUser().copy(displayName = name)
        }
    }

    // ------------------------------------------------------------------ FR-02 login / reset

    override suspend fun login(email: String, password: String): Result<AuthUser> {
        val mail = email.trim()

        AuthValidator.validateLogin(mail, password).first?.let {
            return Result.failure(AuthException(it, AuthErrorType.VALIDATION))
        }

        return authCall {
            val firebaseUser = firebaseAuth.signInWithEmailAndPassword(mail, password)
                .await().user
                ?: throw AuthException("Invalid email or password.", AuthErrorType.INVALID_CREDENTIALS)

            // Repairs accounts whose registration was interrupted. Never blocks a valid login.
            bestEffortProfileRepair(firebaseUser)

            firebaseUser.toAuthUser()
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val mail = email.trim()

        AuthValidator.validateEmail(mail)?.let {
            return Result.failure(AuthException(it, AuthErrorType.VALIDATION))
        }

        return authCall {
            try {
                firebaseAuth.sendPasswordResetEmail(mail).await()
            } catch (e: FirebaseAuthInvalidUserException) {
                // Swallow on purpose: telling the caller "no such user" would reveal which
                // emails are registered (FR-02).
            }
            Unit
        }
    }

    // ------------------------------------------------------------------ FR-03 Google

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> =
        googleAuth(idToken, GoogleIntent.LOGIN)

    override suspend fun signUpWithGoogle(idToken: String): Result<AuthUser> =
        googleAuth(idToken, GoogleIntent.SIGN_UP)

    private enum class GoogleIntent { LOGIN, SIGN_UP }

    /**
     * Firebase silently creates an account for any unknown Google user, so "new vs existing" is
     * decided here from [com.google.firebase.auth.AdditionalUserInfo.isNewUser]:
     *  - LOGIN + brand-new account  -> delete the account we just created, report "not found".
     *  - SIGN_UP + finished profile -> sign out, report "already registered".
     * An Auth account whose profile was never completed (interrupted earlier setup) is finished
     * rather than rejected, so the user is never locked out of their own Google account.
     */
    private suspend fun googleAuth(idToken: String, intent: GoogleIntent): Result<AuthUser> =
        authCall(invalidCredentialsMessage = GOOGLE_FAILED_MESSAGE) {
            googleCheckInProgress.value = true
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user
                    ?: throw AuthException(GOOGLE_FAILED_MESSAGE, AuthErrorType.INVALID_CREDENTIALS)
                val isNewAccount = authResult.additionalUserInfo?.isNewUser == true

                when (intent) {
                    GoogleIntent.LOGIN -> if (isNewAccount) {
                        discardJustCreatedAccount(firebaseUser)
                        throw AuthException(ACCOUNT_NOT_FOUND_MESSAGE, AuthErrorType.ACCOUNT_NOT_FOUND)
                    }
                    GoogleIntent.SIGN_UP -> if (!isNewAccount && hasCompletedProfile(firebaseUser.uid)) {
                        firebaseAuth.signOut()
                        throw AuthException(ACCOUNT_EXISTS_MESSAGE, AuthErrorType.ACCOUNT_EXISTS)
                    }
                }

                initialiseProfileOrSignOut(
                    firebaseUser,
                    firebaseUser.displayName?.takeIf { it.isNotBlank() }
                        ?: firebaseUser.email?.substringBefore('@').orEmpty()
                )

                firebaseUser.toAuthUser()
            } finally {
                googleCheckInProgress.value = false
            }
        }

    // ------------------------------------------------------------------ FR-06 settings

    override suspend fun updateDisplayName(displayName: String): Result<Unit> {
        val name = displayName.trim()

        AuthValidator.validateDisplayName(name)?.let {
            return Result.failure(AuthException(it, AuthErrorType.VALIDATION))
        }

        return authCall {
            val user = requireUser()
            // Ensure older/partially-created accounts have a profile document before editing it.
            profileInitializer.ensureInitialised(user.uid, user.displayName.orEmpty(), user.email.orEmpty())

            // Merge avoids failing with NOT_FOUND if a profile document was missing.
            profileDocument(user.uid).set(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to name,
                    "email" to user.email.orEmpty(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).awaitOnline(SETTINGS_WRITE_TIMEOUT_MS)

            // Update Firebase Auth only after Firestore confirms the profile write, so the UI
            // does not report a failed save after changing just one copy of the name.
            user.updateProfile(userProfileChangeRequest { this.displayName = name }).await()
        }
    }

    override suspend fun updateDefaultPaymentMethod(paymentMethod: String?): Result<Unit> {
        if (paymentMethod != null && paymentMethod !in PaymentMethods.all) {
            return Result.failure(
                AuthException("Please choose a valid payment method.", AuthErrorType.VALIDATION)
            )
        }

        return authCall {
            val user = requireUser()
            profileInitializer.ensureInitialised(user.uid, user.displayName.orEmpty(), user.email.orEmpty())
            profileDocument(user.uid).set(
                mapOf(
                    "uid" to user.uid,
                    "email" to user.email.orEmpty(),
                    "defaultPaymentMethod" to paymentMethod,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).awaitOnline(SETTINGS_WRITE_TIMEOUT_MS)
        }
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> {
        AuthValidator.validateNewPassword(newPassword)?.let {
            return Result.failure(AuthException(it, AuthErrorType.VALIDATION))
        }

        return authCall {
            val user = requireUser()
            if (user.providerData.none { it.providerId == AuthUser.PASSWORD_PROVIDER }) {
                throw AuthException(
                    "This account signs in with Google and has no password to change.",
                    AuthErrorType.VALIDATION
                )
            }
            user.updatePassword(newPassword).await()
            Unit
        }
    }

    override suspend fun reauthenticate(credential: ReauthCredential): Result<Unit> =
        authCall(invalidCredentialsMessage = "Incorrect password or Google account.") {
            val user = requireUser()
            val authCredential = when (credential) {
                is ReauthCredential.Password -> {
                    val email = user.email
                        ?: throw AuthException(
                            "This account has no email address.",
                            AuthErrorType.NOT_AUTHENTICATED
                        )
                    EmailAuthProvider.getCredential(email, credential.value)
                }
                is ReauthCredential.Google -> GoogleAuthProvider.getCredential(credential.idToken, null)
            }
            user.reauthenticate(authCredential).await()
            Unit
        }

    /**
     * Deletes every Firestore document under users/{uid}, then the profile, then the Auth account.
     *
     * Safety: the login must be recent BEFORE we touch any data. Otherwise Auth would refuse the
     * final delete after the data is already gone, leaving a live account with nothing in it.
     * Deletion runs in chunks because a Firestore batch is limited to 500 operations.
     */
    override suspend fun deleteAccount(): Result<Unit> = authCall {
        val user = requireUser()

        val tokenResult = user.getIdToken(true).await()
        val loginAgeSeconds = System.currentTimeMillis() / 1000 - tokenResult.authTimestamp
        if (loginAgeSeconds > RECENT_LOGIN_WINDOW_SECONDS) {
            throw AuthException(
                "For your security, please confirm your identity and try again.",
                AuthErrorType.REQUIRES_RECENT_LOGIN
            )
        }

        val userDoc = profileDocument(user.uid)
        FirestoreCollections.USER_SUBCOLLECTIONS.forEach { name ->
            deleteCollection(userDoc.collection(name))
        }
        userDoc.delete().awaitOnline(BULK_WRITE_TIMEOUT_MS)

        user.delete().await()
        Unit
    }

    // ------------------------------------------------------------------ FR-05 logout

    /** Only ends the session. Unsynced-data warning and cache clearing live in LogoutUseCase. */
    override suspend fun logout(): Result<Unit> = authCall {
        firebaseAuth.signOut()
    }

    // ------------------------------------------------------------------ helpers

    private fun requireUser(): FirebaseUser = firebaseAuth.currentUser
        ?: throw AuthException("You are not signed in.", AuthErrorType.NOT_AUTHENTICATED)

    private fun profileDocument(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid)

    /**
     * Used by register and Google sign-in. If the profile cannot be created we sign out, so the
     * app never lands on the dashboard with an account that has no profile or categories. The
     * next login retries the setup (see [bestEffortProfileRepair]).
     */
    private suspend fun initialiseProfileOrSignOut(user: FirebaseUser, displayName: String) {
        try {
            profileInitializer.ensureInitialised(user.uid, displayName, user.email.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Real cause (PERMISSION_DENIED = Firestore rules, NOT_FOUND = database not created...).
            Log.e("ProfileSetup", "Profile initialisation failed; signing out", e)
            firebaseAuth.signOut()
            throw AuthException(
                "Your account was created but setup could not finish. Please log in to complete it.",
                AuthErrorType.PROFILE_SETUP_FAILED
            )
        }
    }

    private suspend fun hasCompletedProfile(uid: String): Boolean = try {
        profileInitializer.profileExists(uid)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("GoogleAuth", "Could not check whether the profile exists; signing out", e)
        firebaseAuth.signOut()
        throw AuthException(
            "We could not verify your account. Please check your connection and try again.",
            AuthErrorType.PROFILE_SETUP_FAILED
        )
    }

    /** Removes the Auth account that signInWithCredential just auto-created for a LOGIN attempt. */
    private suspend fun discardJustCreatedAccount(user: FirebaseUser) {
        try {
            user.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GoogleAuth", "Could not delete the auto-created account; signing out", e)
            firebaseAuth.signOut()
        }
    }

    private suspend fun bestEffortProfileRepair(user: FirebaseUser) {
        try {
            profileInitializer.ensureInitialised(
                user.uid,
                user.displayName.orEmpty(),
                user.email.orEmpty()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-fatal: the user is authenticated. Setup is retried on the next login.
        }
    }

    private suspend fun deleteCollection(collection: CollectionReference) {
        while (true) {
            // SERVER source: fails fast when offline instead of "deleting" from a stale cache.
            val snapshot = collection.limit(DELETE_BATCH_SIZE).get(Source.SERVER).await()
            if (snapshot.isEmpty) return

            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().awaitOnline(BULK_WRITE_TIMEOUT_MS)
        }
    }

    /**
     * Wraps a block into a Result with plain-language errors. CancellationException is rethrown
     * so coroutine cancellation (e.g. leaving the screen) is never mistaken for a failure.
     */
    private inline fun <T> authCall(
        invalidCredentialsMessage: String? = null,
        block: () -> T
    ): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("AuthRepo", "Auth call failed", e)
        val mapped = e.toAuthException()
        Result.failure(
            if (invalidCredentialsMessage != null && mapped.type == AuthErrorType.INVALID_CREDENTIALS) {
                AuthException(invalidCredentialsMessage, AuthErrorType.INVALID_CREDENTIALS)
            } else {
                mapped
            }
        )
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName,
        email = email,
        isEmailVerified = isEmailVerified,
        providerIds = providerData.map { it.providerId }.filter { it != FIREBASE_PROVIDER }
    )

    private companion object {
        const val GOOGLE_FAILED_MESSAGE = "Google sign-in failed. Please try again."
        const val ACCOUNT_NOT_FOUND_MESSAGE =
            "No account found for this Google account. Please sign up first."
        const val ACCOUNT_EXISTS_MESSAGE =
            "This Google account is already registered. Please log in instead."
        const val FIREBASE_PROVIDER = "firebase"
        const val DELETE_BATCH_SIZE = 400L
        const val SETTINGS_WRITE_TIMEOUT_MS = 30_000L
        const val RECENT_LOGIN_WINDOW_SECONDS = 240L // Firebase's window is about 5 minutes
    }
}

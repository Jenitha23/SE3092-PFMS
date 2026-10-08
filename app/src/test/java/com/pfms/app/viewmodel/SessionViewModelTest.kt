package com.pfms.app.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.pfms.app.data.auth.BiometricAuthManager
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.auth.BiometricResult
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.domain.model.AuthErrorType
import com.pfms.app.domain.model.AuthException
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.usecase.LogoutUseCase
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var biometricPreferences: BiometricPreferences
    private lateinit var googleSignInManager: GoogleSignInManager

    private lateinit var viewModel: SessionViewModel

    private val currentUserFlow = MutableStateFlow<AuthUser?>(null)
    private val biometricEnabledFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        authRepository = mock()
        logoutUseCase = mock()
        biometricAuthManager = mock()
        biometricPreferences = mock()
        googleSignInManager = mock()

        whenever(authRepository.currentUser).thenReturn(currentUserFlow)
        whenever(biometricPreferences.biometricEnabled).thenReturn(biometricEnabledFlow)

        viewModel = SessionViewModel(
            authRepository,
            logoutUseCase,
            biometricAuthManager,
            biometricPreferences,
            googleSignInManager
        )
    }

    // ------------------------------------------------------------------ Biometric Unlock

    @Test
    fun `unlockWithBiometric success updates lock state to unlocked`() = runTest {
        val activity: FragmentActivity = mock()
        
        // Mock the callback logic for BiometricAuthManager
        val captor = argumentCaptor<(BiometricResult) -> Unit>()
        
        viewModel.unlockWithBiometric(activity)
        verify(biometricAuthManager).authenticate(any(), captor.capture())
        
        // Simulate a successful fingerprint scan
        captor.firstValue.invoke(BiometricResult.Success)

        val state = viewModel.lockUiState.value
        assertEquals(false, state.showFallback)
    }

    @Test
    fun `unlockWithBiometric UseFallback shows fallback UI`() = runTest {
        val activity: FragmentActivity = mock()
        val captor = argumentCaptor<(BiometricResult) -> Unit>()
        
        viewModel.unlockWithBiometric(activity)
        verify(biometricAuthManager).authenticate(any(), captor.capture())
        
        // Simulate user clicking "Use Password" on the biometric prompt
        captor.firstValue.invoke(BiometricResult.UseFallback)

        val state = viewModel.lockUiState.value
        assertTrue(state.showFallback)
    }

    // ------------------------------------------------------------------ Fallback Unlock (Password / Google)

    @Test
    fun `unlockWithPassword failure updates errorMessage`() = runTest {
        val error = AuthException("Incorrect password.", AuthErrorType.INVALID_CREDENTIALS)
        whenever(authRepository.reauthenticate(any())).thenReturn(Result.failure(error))

        viewModel.unlockWithPassword("wrong-password")

        val state = viewModel.lockUiState.value
        assertEquals("Incorrect password.", state.message)
    }

    @Test
    fun `unlockWithGoogle failure updates errorMessage`() = runTest {
        val context: Context = mock()
        val error = AuthException("Google sign-in failed.")
        whenever(googleSignInManager.getGoogleIdToken(any())).thenReturn(Result.failure(error))

        viewModel.unlockWithGoogle(context)

        val state = viewModel.lockUiState.value
        assertEquals("Google sign-in failed.", state.message)
    }
}

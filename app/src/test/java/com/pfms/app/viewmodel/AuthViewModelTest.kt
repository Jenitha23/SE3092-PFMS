package com.pfms.app.viewmodel

import android.content.Context
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.domain.model.AuthErrorType
import com.pfms.app.domain.model.AuthException
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var googleSignInManager: GoogleSignInManager
    private lateinit var viewModel: AuthViewModel
    private lateinit var mockContext: Context

    private val validUser = AuthUser("uid-1", "Kasun Silva", "kasun@example.com", true)

    @Before
    fun setUp() {
        authRepository = mock()
        googleSignInManager = mock()
        mockContext = mock()
        viewModel = AuthViewModel(authRepository, googleSignInManager)
    }

    // ------------------------------------------------------------------ Register Flow

    @Test
    fun `register with invalid data updates field errors and does not call repository`() = runTest {
        viewModel.register(" ", "invalid-email", "short")

        val state = viewModel.uiState.value
        assertTrue(state.fieldErrors.hasErrors)
        assertEquals("Please enter your name.", state.fieldErrors.displayName)
        
        verify(authRepository, never()).register(any(), any(), any())
    }

    @Test
    fun `successful register updates uiState correctly`() = runTest {
        whenever(authRepository.register(any(), any(), any())).thenReturn(Result.success(validUser))

        viewModel.register("Kasun Silva", "kasun@example.com", "password123")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.fieldErrors.hasErrors)
        assertNull(state.errorMessage)
    }

    // ------------------------------------------------------------------ Login Flow

    @Test
    fun `login with invalid email updates field errors and does not call repository`() = runTest {
        viewModel.login("invalid", "password123")

        val state = viewModel.uiState.value
        assertTrue(state.fieldErrors.hasErrors)
        assertEquals("Please enter a valid email address.", state.fieldErrors.email)

        verify(authRepository, never()).login(any(), any())
    }

    @Test
    fun `failed login updates errorMessage`() = runTest {
        val error = AuthException("Invalid email or password.", AuthErrorType.INVALID_CREDENTIALS)
        whenever(authRepository.login(any(), any())).thenReturn(Result.failure(error))

        viewModel.login("kasun@example.com", "password123")

        val state = viewModel.uiState.value
        assertEquals("Invalid email or password.", state.errorMessage)
    }

    // ------------------------------------------------------------------ Forgot Password Flow

    @Test
    fun `sendPasswordReset with valid email updates infoMessage`() = runTest {
        whenever(authRepository.sendPasswordResetEmail(any())).thenReturn(Result.success(Unit))

        viewModel.sendPasswordReset("kasun@example.com")

        val state = viewModel.uiState.value
        assertEquals("If an account exists for that email, a password reset link has been sent.", state.infoMessage)
    }

    // ------------------------------------------------------------------ Google Sign-in Flow

    @Test
    fun `signInWithGoogle success calls repository and updates state`() = runTest {
        whenever(googleSignInManager.getGoogleIdToken(any())).thenReturn(Result.success("fake-id-token"))
        whenever(authRepository.signInWithGoogle("fake-id-token")).thenReturn(Result.success(validUser))

        viewModel.signInWithGoogle(mockContext)

        verify(authRepository).signInWithGoogle("fake-id-token")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `signInWithGoogle failure updates errorMessage`() = runTest {
        val error = AuthException("Google sign-in failed. Please try again.")
        whenever(googleSignInManager.getGoogleIdToken(any())).thenReturn(Result.failure(error))

        viewModel.signInWithGoogle(mockContext)

        verify(authRepository, never()).signInWithGoogle(any())
        assertEquals("Google sign-in failed. Please try again.", viewModel.uiState.value.errorMessage)
    }
}

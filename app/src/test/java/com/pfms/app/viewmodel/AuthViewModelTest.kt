package com.pfms.app.viewmodel

import android.content.Context
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.domain.model.AuthErrorType
import com.pfms.app.domain.model.AuthException
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.RememberedLoginRepository
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
    private lateinit var rememberedLoginRepository: RememberedLoginRepository
    private lateinit var mockContext: Context

    private val validUser = AuthUser("uid-1", "Kasun Silva", "kasun@example.com", true)

    @Before
    fun setUp() = runTest {
        authRepository = mock()
        googleSignInManager = mock()
        mockContext = mock()
        rememberedLoginRepository = mock()
        whenever(rememberedLoginRepository.getEmail()).thenReturn(null)
        viewModel = AuthViewModel(authRepository, googleSignInManager, rememberedLoginRepository)
    }

    // ------------------------------------------------------------------ Register Flow

    @Test
    fun `validation retains form values so the user can correct them`() = runTest {
        viewModel.onEmailChanged("invalid")
        viewModel.onPasswordChanged("secret123")
        viewModel.submitLogin()

        assertEquals("invalid", viewModel.uiState.value.email)
        assertEquals("secret123", viewModel.uiState.value.password)
        assertTrue(viewModel.uiState.value.fieldErrors.hasErrors)
        viewModel.onEmailChanged("kasun@example.com")
        assertFalse(viewModel.uiState.value.fieldErrors.hasErrors)
    }

    @Test
    fun `both signup methods require agreement before contacting providers`() = runTest {
        viewModel.onDisplayNameChanged("Kasun Silva")
        viewModel.onEmailChanged("kasun@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.submitRegistration()
        viewModel.submitGoogleRegistration(mockContext)

        assertEquals("Please agree before creating an account.", viewModel.uiState.value.termsError)
        verify(authRepository, never()).register(any(), any(), any())
        verify(googleSignInManager, never()).getGoogleIdToken(any())
    }

    @Test
    fun `agreed registration submits trimmed values and clears password on success`() = runTest {
        whenever(authRepository.register(any(), any(), any())).thenReturn(Result.success(validUser))
        viewModel.onDisplayNameChanged(" Kasun Silva ")
        viewModel.onEmailChanged(" kasun@example.com ")
        viewModel.onPasswordChanged("password123")
        viewModel.onTermsAcceptedChanged(true)
        viewModel.submitRegistration()

        verify(authRepository).register("Kasun Silva", "kasun@example.com", "password123")
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `remember me stores only the email after successful login`() = runTest {
        whenever(authRepository.login(any(), any())).thenReturn(Result.success(validUser))
        viewModel.onEmailChanged(" kasun@example.com ")
        viewModel.onPasswordChanged("password123")
        viewModel.onRememberMeChanged(true)
        viewModel.submitLogin()

        verify(rememberedLoginRepository).setEmail("kasun@example.com")
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `failed login retains password and does not remember unverified input`() = runTest {
        whenever(authRepository.login(any(), any())).thenReturn(Result.failure(AuthException("Try again")))
        viewModel.onEmailChanged("kasun@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.onRememberMeChanged(true)
        viewModel.submitLogin()

        assertEquals("password123", viewModel.uiState.value.password)
        verify(rememberedLoginRepository, never()).setEmail(any())
    }

    @Test
    fun `remembered email is restored and unchecking removes it`() = runTest {
        whenever(rememberedLoginRepository.getEmail()).thenReturn("kasun@example.com")
        val restored = AuthViewModel(authRepository, googleSignInManager, rememberedLoginRepository)
        assertEquals("kasun@example.com", restored.uiState.value.email)
        assertTrue(restored.uiState.value.rememberMe)
        restored.onRememberMeChanged(false)
        verify(rememberedLoginRepository).setEmail(null)
    }

    @Test
    fun `preference write failure does not prevent a successful login`() = runTest {
        whenever(authRepository.login(any(), any())).thenReturn(Result.success(validUser))
        whenever(rememberedLoginRepository.setEmail(any())).thenAnswer { throw java.io.IOException("disk unavailable") }
        viewModel.onEmailChanged("kasun@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.onRememberMeChanged(true)
        viewModel.submitLogin()

        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("", viewModel.uiState.value.password)
    }

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

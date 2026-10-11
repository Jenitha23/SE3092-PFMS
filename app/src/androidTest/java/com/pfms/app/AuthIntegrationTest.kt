package com.pfms.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import com.pfms.app.ui.screen.LoginScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

/**
 * Instrumented UI Test for the Authentication Flow.
 * This test runs on a real device or emulator and verifies that the 
 * frontend (UI) correctly triggers the backend validation logic.
 */
@HiltAndroidTest
class AuthIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginWithInvalidEmail_showsBackendValidationMessage() {
        // 1. Find the Email input field by its label and type an invalid email
        composeTestRule.onNodeWithText("Email address").performTextReplacement("invalid-email")
        
        // 2. Find the Password input field and type a password
        composeTestRule.onNodeWithText("Password").performTextReplacement("password123")
        
        // 3. Trigger the Done action on the keyboard (this calls login and hides the keyboard reliably)
        composeTestRule.onNodeWithText("Password").performImeAction()

        // 4. Verify that the backend validation logic was triggered and 
        // the error message correctly made it back to the UI!
        composeTestRule.onNodeWithText("Please enter a valid email address.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun loginWithEmptyPassword_showsBackendValidationMessage() {
        composeTestRule.onNodeWithText("Email address").performTextReplacement("kasun@gmail.com")
        
        // Leave password empty
        
        // Click the "Log In" button (index 1 is the button, index 0 is the tab)
        composeTestRule.onAllNodesWithText("Log In")[1].performClick()

        // Assert the backend sent back the password missing error
        composeTestRule.onNodeWithText("Please enter your password.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun registerWithEmptyName_showsBackendValidationMessage() {
        // 1. Navigate to the Register Screen by clicking the Sign Up tab (index 0)
        composeTestRule.onAllNodesWithText("Sign Up")[0].performClick()

        // 2. We are now on RegisterScreen. Fill out email and password but leave Name empty.
        composeTestRule.onNodeWithText("Email address").performTextReplacement("kasun@example.com")
        composeTestRule.onNodeWithText("Password").performTextReplacement("password123")

        // 3. Click the Sign Up button (index 1 is the button)
        composeTestRule.onAllNodesWithText("Sign Up")[1].performClick()

        // 4. Verify the backend name validation error appears
        composeTestRule.onNodeWithText("Please enter your name.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun forgotPasswordWithInvalidEmail_showsBackendValidationMessage() {
        // 1. Navigate to the Forgot Password screen
        composeTestRule.onNodeWithText("Forgot password?").performClick()

        // 2. Type an invalid email
        composeTestRule.onNodeWithText("Email address").performTextReplacement("bad-email")

        // 3. Click the Reset button
        composeTestRule.onNode(
            androidx.compose.ui.test.hasText("Send Reset Link") and 
            androidx.compose.ui.test.hasClickAction()
        ).performClick()

        // 4. Verify the backend email validation error appears
        composeTestRule.onNodeWithText("Please enter a valid email address.", useUnmergedTree = true).assertExists()
    }
}

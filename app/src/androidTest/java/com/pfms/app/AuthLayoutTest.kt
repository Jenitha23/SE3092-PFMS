package com.pfms.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.screen.AuthScreenContent
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.AuthUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun signupFitsPhoneIncludingGoogleAndLoginLink() {
        compose.setContent {
            PFMSTheme(darkTheme = false) {
                Box(Modifier.requiredSize(390.dp, 800.dp).testTag("viewport")) {
                    AuthScreenContent(AuthUiState(), true, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("FULL NAME").assertIsDisplayed()
        compose.onNodeWithText("Sign up with Google").assertIsDisplayed()
        val footer = compose.onNodeWithTag("auth_footer").assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("viewport").getUnclippedBoundsInRoot()
        assertTrue("The entire login footer should fit without scrolling", footer.bottom <= viewport.bottom)
    }

    @Test
    fun smallPhoneWithLargeTextKeepsBottomActionsReachable() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.3f)) {
                PFMSTheme(darkTheme = false) {
                    Box(Modifier.requiredSize(320.dp, 640.dp).testTag("viewport")) {
                        AuthScreenContent(AuthUiState(), true, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("FULL NAME").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sign up with Google").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_footer").performScrollTo().assertIsDisplayed()
    }
}

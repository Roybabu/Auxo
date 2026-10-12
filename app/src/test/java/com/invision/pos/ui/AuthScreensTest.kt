package com.invision.pos.ui

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.auth.AuthState
import com.invision.pos.core.auth.SetupError
import com.invision.pos.core.auth.SetupField
import com.invision.pos.ui.screens.auth.AdminSetupContent
import com.invision.pos.ui.screens.auth.LoginContent
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.AdminSetupUiState
import com.invision.pos.viewmodel.LoginMessage
import com.invision.pos.viewmodel.LoginUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SAMPLE_PW = "Green-Leaf-42"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class AuthScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun gate(state: AuthState) {
        composeRule.setContent {
            InvisionTheme {
                AuthGate(
                    state = state,
                    setup = { Text("SETUP SCREEN", modifier = Modifier.testTag("fake_setup")) },
                    login = { Text("LOGIN SCREEN", modifier = Modifier.testTag("fake_login")) },
                    app = { Text("THE APP", modifier = Modifier.testTag("fake_app")) },
                )
            }
        }
    }

    // The gate

    @Test
    fun firstLaunch_showsAdminSetup_only() {
        gate(AuthState.NeedsSetup)
        composeRule.onNodeWithTag("fake_setup").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_login").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_app").assertDoesNotExist()
    }

    @Test
    fun laterLaunches_showLogin_neverSetup() {
        gate(AuthState.SignedOut)
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_setup").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_app").assertDoesNotExist()
    }

    @Test
    fun signedIn_showsTheApp() {
        gate(AuthState.SignedIn("Roy"))
        composeRule.onNodeWithTag("fake_app").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_login").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_setup").assertDoesNotExist()
    }

    @Test
    fun anUnreadableAccount_showsAnExplanation_notSetup() {
        gate(AuthState.Unreadable)
        composeRule.onNodeWithTag("auth_unreadable").assertIsDisplayed()
        composeRule.onNodeWithText("Administrator account unreadable").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_setup").assertDoesNotExist()
        composeRule.onNodeWithTag("setup_submit").assertDoesNotExist()
    }

    // Setup screen

    private fun setup(state: AdminSetupUiState = AdminSetupUiState(), onSubmit: () -> Unit = {}) {
        composeRule.setContent {
            InvisionTheme {
                AdminSetupContent(state, onNameChange = {}, onIdentifierChange = {}, onPasswordChange = {}, onConfirmChange = {}, onSubmit = onSubmit)
            }
        }
    }

    @Test
    fun onAPhone_theFormUsesTheFullWidth_belowTheBrand() {
        setup()
        val left = composeRule.onNodeWithTag("setup_name").getUnclippedBoundsInRoot().left
        assertTrue("form starts at ${left.value}dp", left.value < 40f)
        composeRule.onNodeWithText("InVision").assertIsDisplayed()
    }

    @Test
    fun setupScreen_hasTheFourFields_andTheInVisionLogo() {
        setup()
        composeRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_name").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_identifier").assertExists()
        composeRule.onNodeWithTag("setup_password").assertExists()
        composeRule.onNodeWithTag("setup_confirm").assertExists()
        composeRule.onNodeWithContentDescription("InVision").assertExists()
        composeRule.onNodeWithTag("setup_submit").performScrollTo().assertIsEnabled()
    }

    @Test
    fun setupScreen_showsFieldErrors_includingAMismatch() {
        setup(
            AdminSetupUiState(
                submitted = true,
                errors = mapOf(
                    SetupField.NAME to SetupError.NAME_REQUIRED,
                    SetupField.IDENTIFIER to SetupError.IDENTIFIER_INVALID,
                    SetupField.PASSWORD to SetupError.PASSWORD_TOO_SHORT,
                    SetupField.CONFIRM to SetupError.CONFIRM_MISMATCH,
                ),
            ),
        )
        composeRule.onNodeWithText("Enter your name.").assertExists()
        composeRule.onNodeWithText("Use at least 8 characters.").assertExists()
        composeRule.onNodeWithText("The passwords do not match.").assertExists()
        composeRule.onNodeWithText("Use 3 to 32 letters, numbers, dots, dashes or underscores, or a valid email address.").assertExists()
    }

    @Test
    fun setupScreen_disablesTheButton_whileSaving_soItCannotBeSubmittedTwice() {
        var submits = 0
        setup(AdminSetupUiState(isSubmitting = true), onSubmit = { submits++ })
        composeRule.onNodeWithTag("setup_submit").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Creating…").assertExists()
        composeRule.onNodeWithTag("setup_name").assertIsNotEnabled()
        assertEquals(0, submits)
    }

    @Test
    fun setupScreen_submitsWhenTheButtonIsTapped() {
        var submits = 0
        setup(onSubmit = { submits++ })
        composeRule.onNodeWithTag("setup_submit").performScrollTo().performClick()
        assertEquals(1, submits)
    }

    @Test
    fun setupScreen_hidesPasswords_untilToggled() {
        composeRule.setContent {
            InvisionTheme {
                AdminSetupContent(
                    AdminSetupUiState("", "", SAMPLE_PW, SAMPLE_PW),
                    onNameChange = {}, onIdentifierChange = {}, onPasswordChange = {}, onConfirmChange = {}, onSubmit = {},
                )
            }
        }
        // The field is marked as a password (so its text is masked on screen and kept out of autofill/accessibility readouts).
        composeRule.onNodeWithTag("setup_password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNodeWithTag("setup_confirm").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNodeWithContentDescription("Show password").performScrollTo()
        composeRule.onNodeWithContentDescription("Show password").performClick()
        composeRule.onNodeWithTag("setup_password").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
        composeRule.onNodeWithTag("setup_confirm").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
        composeRule.onNodeWithContentDescription("Hide password").assertExists()
    }

    @Test
    fun setupAndLogin_offerNoForgotPassword_orOnlineSignIn() {
        setup()
        for (text in listOf("Forgot", "forgot", "Reset password", "Sign in with", "Google", "server", "Server", "Cloud")) {
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
    }

    // Login screen

    private fun login(state: LoginUiState = LoginUiState(), onSignIn: () -> Unit = {}) {
        composeRule.setContent {
            InvisionTheme { LoginContent(state, onIdentifierChange = {}, onPasswordChange = {}, onSignIn = onSignIn) }
        }
    }

    @Test
    fun loginScreen_hasNoSetupOrForgotPasswordOptions() {
        login()
        composeRule.onNodeWithTag("login").assertIsDisplayed()
        composeRule.onNodeWithTag("login_identifier").assertExists()
        composeRule.onNodeWithTag("login_password").assertExists()
        composeRule.onNodeWithTag("setup_name").assertDoesNotExist()
        composeRule.onNodeWithTag("setup_submit").assertDoesNotExist()
        for (text in listOf("Forgot", "Create administrator", "Register", "Sign up", "Reset", "Cloud", "server")) {
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun loginButton_needsBothFields() {
        login(LoginUiState(identifier = "roy", password = ""))
        composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
    }

    @Test
    fun loginButton_isEnabledWithBothFields_andSignsIn() {
        var signIns = 0
        login(LoginUiState(identifier = "roy", password = "x"), onSignIn = { signIns++ })
        composeRule.onNodeWithTag("login_submit").assertIsEnabled().performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun loginMessages_areShown() {
        login(LoginUiState(identifier = "roy", message = LoginMessage.Rejected))
        composeRule.onNodeWithText("Incorrect username or password.").assertIsDisplayed()
    }

    @Test
    fun lockoutMessage_countsSeconds() {
        login(LoginUiState(identifier = "roy", message = LoginMessage.LockedOut(30)))
        composeRule.onNodeWithText("Too many attempts. Try again in 30 seconds.").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1024dp-h768dp-mdpi")
class AuthScreensTabletTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onATablet_setupAndLogin_putTheBrandBesideTheForm() {
        composeRule.setContent {
            InvisionTheme {
                AdminSetupContent(AdminSetupUiState(), {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        // The wide layout shows the app name as text next to the form; the narrow one has only the logo.
        composeRule.onNodeWithText("InVision").assertIsDisplayed()
        composeRule.onNodeWithText("Billing and inventory for your shop").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_name").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_submit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onATablet_login_isUsable() {
        composeRule.setContent {
            InvisionTheme { LoginContent(LoginUiState(identifier = "roy", password = "x"), {}, {}, {}) }
        }
        composeRule.onNodeWithText("InVision").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun onATablet_theFormSitsInTheRightHalf() {
        composeRule.setContent { InvisionTheme { AdminSetupContent(AdminSetupUiState(), {}, {}, {}, {}, {}) } }
        val left = composeRule.onNodeWithTag("setup_name").getUnclippedBoundsInRoot().left
        assertTrue("form starts at ${left.value}dp", left.value >= 500f)
    }
}

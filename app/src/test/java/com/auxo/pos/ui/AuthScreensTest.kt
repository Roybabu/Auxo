package com.auxo.pos.ui

import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.ui.screens.auth.AdminSetupContent
import com.auxo.pos.ui.screens.auth.LoginContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.AdminSetupUiState
import com.auxo.pos.viewmodel.LoginMessage
import com.auxo.pos.viewmodel.LoginUiState
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

    private fun gate(state: AuthState, savedSessionName: String? = null) {
        composeRule.setContent {
            AuxoTheme {
                AuthGate(
                    state = state,
                    savedSessionName = savedSessionName,
                    resume = { name, usePassword -> Text("RESUME $name", modifier = Modifier.testTag("fake_resume").clickable { usePassword() }) },
                    setup = { back -> Text("SETUP SCREEN", modifier = Modifier.testTag("fake_setup").clickable { back() }) },
                    login = { open -> Text("LOGIN SCREEN", modifier = Modifier.testTag("fake_login").clickable { open() }) },
                    businessExists = { back -> Text("EXISTS", modifier = Modifier.testTag("fake_exists").clickable { back() }) },
                    app = { Text("THE APP", modifier = Modifier.testTag("fake_app")) },
                )
            }
        }
    }

    // The gate

    @Test
    fun firstLaunch_showsLogin_andSetupOnlyAfterTheLinkIsTapped() {
        gate(AuthState.NeedsSetup)
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_setup").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_app").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_login").performClick()
        composeRule.onNodeWithTag("fake_setup").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_setup").performClick()
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
    }

    @Test
    fun aSavedSession_opensTheFingerprintScreen_first_andThePasswordIsOneTapAway() {
        gate(AuthState.SignedOut, savedSessionName = "Roy")
        composeRule.onNodeWithTag("fake_resume").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_login").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_resume").performClick()
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_resume").assertDoesNotExist()
    }

    @Test
    fun aSavedSession_isIgnored_beforeAnyBusinessExists_andWhenNothingWasSaved() {
        gate(AuthState.NeedsSetup, savedSessionName = "Roy")
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_resume").assertDoesNotExist()
    }

    @Test
    fun withABusinessAlready_theLink_explains_andNeverOpensSetup() {
        gate(AuthState.SignedOut)
        composeRule.onNodeWithTag("fake_login").performClick()
        composeRule.onNodeWithTag("fake_exists").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_setup").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_exists").performClick()
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
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
            AuxoTheme {
                AdminSetupContent(state, onNameChange = {}, onIdentifierChange = {}, onPasswordChange = {}, onConfirmChange = {}, onSubmit = onSubmit)
            }
        }
    }

    @Test
    fun onAPhone_theFormUsesTheFullWidth_belowTheBrand() {
        setup()
        val left = composeRule.onNodeWithTag("setup_name").getUnclippedBoundsInRoot().left
        assertTrue("form starts at ${left.value}dp", left.value < 40f)
        composeRule.onNodeWithContentDescription("Auxo").assertIsDisplayed()
    }

    @Test
    fun setupScreen_hasTheFourFields_andTheAuxoLogo() {
        setup()
        composeRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_name").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_identifier").assertExists()
        composeRule.onNodeWithTag("setup_password").assertExists()
        composeRule.onNodeWithTag("setup_confirm").assertExists()
        composeRule.onNodeWithContentDescription("Auxo").assertExists()
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
            AuxoTheme {
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
        // The fields stay marked as passwords (keyboard type), so only the on-screen text changes; the toggle flips its label.
        composeRule.onNodeWithTag("setup_password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNodeWithContentDescription("Hide password").assertExists()
    }

    @Test
    fun setupScreen_asksForTheBusinessName_andShowsItsError() {
        setup(AdminSetupUiState(submitted = true, errors = mapOf(SetupField.BUSINESS to SetupError.BUSINESS_REQUIRED)))
        composeRule.onNodeWithTag("setup_business").assertExists()
        composeRule.onNodeWithText("Enter your business name.").assertIsDisplayed()
        composeRule.onNodeWithText("Create & Get Started").assertExists()
    }

    @Test
    fun strengthMeter_isHidden_untilAPasswordIsTyped() {
        setup(AdminSetupUiState())
        composeRule.onNodeWithTag("strength_meter").assertDoesNotExist()
    }

    @Test
    fun strengthMeter_namesItsLevel() {
        composeRule.setContent { AuxoTheme { com.auxo.pos.ui.components.StrengthMeter(filled = 3, label = "Good") } }
        composeRule.onNodeWithTag("strength_meter").assertIsDisplayed()
        composeRule.onNodeWithText("Good").assertIsDisplayed()
    }

    @Test
    fun usernameIdeas_areShown_andTappingOneReportsIt() {
        var picked: String? = null
        composeRule.setContent {
            AuxoTheme {
                AdminSetupContent(AdminSetupUiState(name = "Roy Babu"), {}, {}, {}, {}, {}, onSuggestionPick = { picked = it })
            }
        }
        composeRule.onNodeWithTag("suggestion_0").performScrollTo().performClick()
        assertEquals("roy.babu", picked)
    }

    @Test
    fun setup_offersNoOnlineSignIn() {
        setup()
        for (text in listOf("Sign in with", "Google", "server", "Server", "Cloud")) {
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
    }

    // Login screen

    private fun login(state: LoginUiState = LoginUiState(), onSignIn: () -> Unit = {}) {
        composeRule.setContent {
            AuxoTheme { LoginContent(state, onIdentifierChange = {}, onPasswordChange = {}, onSignIn = onSignIn) }
        }
    }

    @Test
    fun loginScreen_hasNoSetupOptions_andOnlyOffersForgotPasswordWhenAskedTo() {
        login()
        composeRule.onNodeWithTag("login").assertIsDisplayed()
        composeRule.onNodeWithTag("login_identifier").assertExists()
        composeRule.onNodeWithTag("login_password").assertExists()
        composeRule.onNodeWithTag("setup_name").assertDoesNotExist()
        composeRule.onNodeWithTag("setup_submit").assertDoesNotExist()
        for (text in listOf("Forgot", "Create administrator", "Register", "Sign up", "Cloud", "server")) {
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun loginScreen_showsTheSetUpBusinessDivider() {
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(), {}, {}, {}, onSetUpBusiness = {}) }
        }
        composeRule.onNodeWithText("Set up your business").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loginScreen_alwaysOffersSetUpYourBusiness() {
        var taps = 0
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(), {}, {}, {}, onSetUpBusiness = { taps++ }) }
        }
        composeRule.onNodeWithTag("login_setup_business").assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun loginScreen_saysWhenThereIsNoAccountOnTheDevice() {
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(message = com.auxo.pos.viewmodel.LoginMessage.NoAccount), {}, {}, {}, onSetUpBusiness = {}) }
        }
        composeRule.onNodeWithTag("login_error").assertIsDisplayed()
        composeRule.onNodeWithText("No account was found", substring = true).assertIsDisplayed()
    }

    @Test
    fun resumeScreen_offersTheFingerprint_andThePassword() {
        var fingerprints = 0
        var passwords = 0
        composeRule.setContent {
            AuxoTheme {
                com.auxo.pos.ui.screens.auth.ResumeContent(
                    com.auxo.pos.viewmodel.ResumeUiState("Roy"),
                    onUseFingerprint = { fingerprints++ },
                    onUsePassword = { passwords++ },
                )
            }
        }
        composeRule.onNodeWithText("Welcome back, Roy").assertIsDisplayed()
        composeRule.onNodeWithTag("resume_idle").assertIsDisplayed()
        composeRule.onNodeWithTag("resume_fingerprint").performClick()
        composeRule.onNodeWithTag("resume_password").performClick()
        assertEquals(1, fingerprints)
        assertEquals(1, passwords)
    }

    @Test
    fun resumeScreen_saysSoWhenAFingerIsNotRecognised() {
        composeRule.setContent {
            AuxoTheme { com.auxo.pos.ui.screens.auth.ResumeContent(com.auxo.pos.viewmodel.ResumeUiState("Roy", failed = true), {}, {}) }
        }
        composeRule.onNodeWithTag("resume_failed").assertIsDisplayed()
        composeRule.onNodeWithText("Fingerprint not recognised. Try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun businessExistsScreen_offersOnlyAWayBack() {
        var backs = 0
        composeRule.setContent { AuxoTheme { com.auxo.pos.ui.screens.auth.BusinessExistsScreen { backs++ } } }
        composeRule.onNodeWithTag("business_exists").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_submit").assertDoesNotExist()
        composeRule.onNodeWithTag("business_exists_back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun loginScreen_explainsMissingFields_insteadOfDisablingTheButton() {
        var signIns = 0
        login(LoginUiState(identifier = "", password = "", identifierMissing = true, passwordMissing = true), onSignIn = { signIns++ })
        composeRule.onNodeWithTag("login_submit").assertIsEnabled()
        composeRule.onNodeWithText("Enter your username or email.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter your password.").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun loginScreen_hasNoOfflineModeOrServerOptions() {
        login()
        for (text in listOf("Offline", "offline", "Online", "online", "Server", "server", "Cloud", "cloud", "Connect", "Business", "OTP", "Register", "Google")) {
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
        composeRule.onNodeWithContentDescription("Auxo").assertExists()
        composeRule.onNodeWithContentDescription("Auxo").assertIsDisplayed()
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
            AuxoTheme {
                AdminSetupContent(AdminSetupUiState(), {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        // Both layouts show the approved logo (symbol, wordmark and tagline in one image), described as "Auxo".
        composeRule.onNodeWithContentDescription("Auxo").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_name").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_submit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onATablet_login_isUsable() {
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(identifier = "roy", password = "x"), {}, {}, {}) }
        }
        composeRule.onNodeWithContentDescription("Auxo").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun onATablet_theFormSitsInTheRightHalf() {
        composeRule.setContent { AuxoTheme { AdminSetupContent(AdminSetupUiState(), {}, {}, {}, {}, {}) } }
        val left = composeRule.onNodeWithTag("setup_name").getUnclippedBoundsInRoot().left
        assertTrue("form starts at ${left.value}dp", left.value >= 500f)
    }
}

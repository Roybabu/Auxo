package com.auxo.pos.ui

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.RecoveryPrompt
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.ui.screens.auth.LoginContent
import com.auxo.pos.ui.screens.auth.PasswordResetContent
import com.auxo.pos.ui.screens.auth.RecoveryCodeBody
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.LoginUiState
import com.auxo.pos.viewmodel.PasswordResetUiState
import com.auxo.pos.viewmodel.RecoveryCodeUiState
import com.auxo.pos.viewmodel.RecoveryMessage
import com.auxo.pos.viewmodel.RecoveryStep
import com.auxo.pos.viewmodel.ResetMessage
import com.auxo.pos.viewmodel.ResetStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val CODE = "ABCDE-FGHJK-LMNPQ-RSTUV"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class RecoveryScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The code card

    @Test
    fun theCode_staysOnOneLine_evenInANarrowCard() {
        composeRule.setContent {
            AuxoTheme {
                androidx.compose.foundation.layout.Box(Modifier.width(260.dp)) {
                    com.auxo.pos.ui.screens.auth.RecoveryCodeCard("SEL9U-GD96A-LWYSH-M7VLV")
                }
            }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithTag("recovery_code").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertEquals("SEL9U-GD96A-LWYSH-M7VLV", layouts.single().layoutInput.text.text)
    }

    // The gate

    private fun gate(state: AuthState, prompt: RecoveryPrompt) {
        composeRule.setContent {
            AuxoTheme {
                AuthGate(
                    state = state,
                    setup = { Text("SETUP", modifier = Modifier.testTag("fake_setup")) },
                    login = { Text("LOGIN", modifier = Modifier.testTag("fake_login")) },
                    recoveryPrompt = prompt,
                    recovery = { canSkip -> Text(if (canSkip) "OFFER" else "REQUIRED", modifier = Modifier.testTag("fake_recovery")) },
                    app = { Text("THE APP", modifier = Modifier.testTag("fake_app")) },
                )
            }
        }
    }

    @Test
    fun afterSetup_theCodeScreenComesBeforeTheApp_andCannotBeSkipped() {
        gate(AuthState.SignedIn("Roy"), RecoveryPrompt.Required)
        composeRule.onNodeWithText("REQUIRED").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_app").assertDoesNotExist()
    }

    @Test
    fun anExistingInstall_isOfferedTheCode_withAWayToSkip() {
        gate(AuthState.SignedIn("Roy"), RecoveryPrompt.Offered)
        composeRule.onNodeWithText("OFFER").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_app").assertDoesNotExist()
    }

    @Test
    fun withNothingToAsk_theAppOpensAtOnce() {
        gate(AuthState.SignedIn("Roy"), RecoveryPrompt.None)
        composeRule.onNodeWithTag("fake_app").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_recovery").assertDoesNotExist()
    }

    @Test
    fun theCodePrompt_isNeverShownToSomeoneSignedOut() {
        gate(AuthState.SignedOut, RecoveryPrompt.Required)
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_recovery").assertDoesNotExist()
    }

    // Creating the code

    private class Taps {
        var start = 0
        var written = 0
        var confirm = 0
        var showAgain = 0
        var skip = 0
        var done = 0
        var typed = ""
        var password = ""
    }

    private fun body(state: RecoveryCodeUiState, taps: Taps = Taps(), canSkip: Boolean = false, hasDone: Boolean = false) {
        composeRule.setContent {
            AuxoTheme {
                RecoveryCodeBody(
                    state = state,
                    onPasswordChange = { taps.password += it },
                    onStart = { taps.start++ },
                    onWrittenDown = { taps.written++ },
                    onTypedChange = { taps.typed += it },
                    onConfirm = { taps.confirm++ },
                    onShowAgain = { taps.showAgain++ },
                    onSkip = if (canSkip) ({ taps.skip++ }) else null,
                    onDone = if (hasDone) ({ taps.done++ }) else null,
                )
            }
        }
    }

    @Test
    fun theIntro_explainsTheCode_andAsksForThePasswordWhenNeeded() {
        val taps = Taps()
        body(RecoveryCodeUiState(needsPassword = true), taps, canSkip = true)
        composeRule.onNodeWithTag("recovery_password").assertIsDisplayed()
        composeRule.onNodeWithTag("recovery_password").performTextInput("abc")
        assertEquals("abc", taps.password)
        composeRule.onNodeWithTag("recovery_start").performClick()
        composeRule.onNodeWithTag("recovery_skip").performClick()
        assertEquals(1, taps.start)
        assertEquals(1, taps.skip)
        composeRule.onNodeWithTag("recovery_code").assertDoesNotExist()
    }

    @Test
    fun rightAfterSetup_thereIsNoPasswordField_andNoSkip() {
        body(RecoveryCodeUiState(needsPassword = false))
        composeRule.onNodeWithTag("recovery_start").assertIsDisplayed()
        composeRule.onNodeWithTag("recovery_password").assertDoesNotExist()
        composeRule.onNodeWithTag("recovery_skip").assertDoesNotExist()
    }

    @Test
    fun anIntroError_isShown() {
        body(RecoveryCodeUiState(message = RecoveryMessage.WrongPassword))
        composeRule.onNodeWithTag("recovery_error").assertIsDisplayed()
        composeRule.onNodeWithText("That password is not right.").assertIsDisplayed()
    }

    @Test
    fun theCode_isShownInGroups_withAPromptToWriteItDown() {
        val taps = Taps()
        body(RecoveryCodeUiState(step = RecoveryStep.SHOW, code = CODE), taps)
        composeRule.onNodeWithTag("recovery_code").assertIsDisplayed()
        composeRule.onNodeWithText(CODE).assertIsDisplayed()
        composeRule.onNodeWithText("Write this code down").assertIsDisplayed()
        composeRule.onNodeWithTag("recovery_written").performClick()
        assertEquals(1, taps.written)
    }

    @Test
    fun theConfirmStep_doesNotShowTheCode_andTakesWhatIsTyped() {
        val taps = Taps()
        body(RecoveryCodeUiState(step = RecoveryStep.CONFIRM, code = CODE, typed = ""), taps)
        composeRule.onNodeWithTag("recovery_code").assertDoesNotExist()
        composeRule.onNodeWithTag("recovery_typed").performTextInput("ABC")
        composeRule.onNodeWithTag("recovery_confirm").performClick()
        composeRule.onNodeWithTag("recovery_show_again").performClick()
        assertEquals("ABC", taps.typed)
        assertEquals(1, taps.confirm)
        assertEquals(1, taps.showAgain)
    }

    @Test
    fun aMismatch_isExplained() {
        body(RecoveryCodeUiState(step = RecoveryStep.CONFIRM, message = RecoveryMessage.Mismatch))
        composeRule.onNodeWithText("That is not the code that was shown. Check your copy and try again.").assertIsDisplayed()
    }

    @Test
    fun onceSaved_thePersonCanGoBack() {
        val taps = Taps()
        body(RecoveryCodeUiState(step = RecoveryStep.SAVED), taps, hasDone = true)
        composeRule.onNodeWithTag("recovery_saved").assertIsDisplayed()
        composeRule.onNodeWithTag("recovery_code").assertDoesNotExist()
        composeRule.onNodeWithTag("recovery_done").performClick()
        assertEquals(1, taps.done)
    }

    // Forgot password

    private class ResetTaps {
        var submit = 0
        var written = 0
        var confirmNew = 0
        var back = 0
        var code = ""
        var password = ""
        var confirm = ""
    }

    private fun reset(state: PasswordResetUiState, taps: ResetTaps = ResetTaps()) {
        composeRule.setContent {
            AuxoTheme {
                PasswordResetContent(
                    state = state,
                    onCodeChange = { taps.code += it },
                    onPasswordChange = { taps.password += it },
                    onConfirmChange = { taps.confirm += it },
                    onSubmit = { taps.submit++ },
                    onWrittenDown = { taps.written++ },
                    onTypedChange = {},
                    onConfirmNewCode = { taps.confirmNew++ },
                    onShowAgain = {},
                    onBack = { taps.back++ },
                )
            }
        }
    }

    @Test
    fun theResetForm_takesTheCodeAndANewPassword() {
        val taps = ResetTaps()
        reset(PasswordResetUiState(), taps)
        composeRule.onNodeWithTag("reset_code").performTextInput("ABC")
        composeRule.onNodeWithTag("reset_password").performTextInput("pw")
        composeRule.onNodeWithTag("reset_confirm").performTextInput("pw")
        composeRule.onNodeWithTag("reset_submit").performScrollTo().performClick()
        composeRule.onNodeWithTag("reset_back").performScrollTo().performClick()
        assertEquals("ABC", taps.code)
        assertEquals("pw", taps.password)
        assertEquals("pw", taps.confirm)
        assertEquals(1, taps.submit)
        assertEquals(1, taps.back)
    }

    @Test
    fun resetErrors_areShown() {
        reset(PasswordResetUiState(errors = mapOf(SetupField.PASSWORD to SetupError.PASSWORD_TOO_SHORT), message = ResetMessage.WrongCode))
        composeRule.onNodeWithText("Use at least 8 characters.").assertIsDisplayed()
        composeRule.onNodeWithTag("reset_error").assertIsDisplayed()
        composeRule.onNodeWithText("That is not the recovery code.").assertIsDisplayed()
    }

    @Test
    fun noCodeEver_isExplained() {
        reset(PasswordResetUiState(message = ResetMessage.NoCode))
        composeRule.onNodeWithTag("reset_error").assertIsDisplayed()
    }

    @Test
    fun afterTheReset_theNewCodeIsShown_beforeAnythingElse() {
        val taps = ResetTaps()
        reset(PasswordResetUiState(step = ResetStep.SHOW, newCode = CODE), taps)
        composeRule.onNodeWithText(CODE).assertIsDisplayed()
        composeRule.onNodeWithTag("reset_submit").assertDoesNotExist()
        composeRule.onNodeWithTag("recovery_written").performClick()
        assertEquals(1, taps.written)
    }

    @Test
    fun theNewCodeConfirmation_doesNotShowTheCode() {
        val taps = ResetTaps()
        reset(PasswordResetUiState(step = ResetStep.CONFIRM, newCode = CODE, message = ResetMessage.Mismatch), taps)
        composeRule.onNodeWithTag("recovery_code").assertDoesNotExist()
        composeRule.onNodeWithText("That is not the code that was shown. Check your copy and try again.").assertIsDisplayed()
        composeRule.onNodeWithTag("recovery_confirm").performClick()
        assertEquals(1, taps.confirmNew)
    }

    @Test
    fun whenFinished_thePersonGoesBackToSignIn() {
        val taps = ResetTaps()
        reset(PasswordResetUiState(step = ResetStep.DONE), taps)
        composeRule.onNodeWithTag("reset_done_title").assertIsDisplayed()
        composeRule.onNodeWithTag("reset_done").performClick()
        assertEquals(1, taps.back)
    }

    // The sign-in screen

    @Test
    fun theLoginScreen_offersForgotPassword_whenItCanHandleIt() {
        var forgot = 0
        composeRule.setContent {
            AuxoTheme { LoginContent(LoginUiState(), {}, {}, {}, onForgotPassword = { forgot++ }) }
        }
        composeRule.onNodeWithTag("login_forgot").performScrollTo().performClick()
        assertEquals(1, forgot)
    }

    @Test
    fun theLoginScreen_hasNoForgotLink_whenNothingHandlesIt() {
        composeRule.setContent { AuxoTheme { LoginContent(LoginUiState(), {}, {}, {}) } }
        composeRule.onNodeWithTag("login_forgot").assertDoesNotExist()
    }
}

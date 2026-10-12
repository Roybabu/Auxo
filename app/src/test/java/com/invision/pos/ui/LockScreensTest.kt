package com.invision.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.auth.AuthState
import com.invision.pos.core.auth.IdleTimeout
import com.invision.pos.core.auth.LockInfo
import com.invision.pos.core.auth.PinError
import com.invision.pos.core.auth.QuickUnlockStatus
import com.invision.pos.ui.lock.LockContent
import com.invision.pos.ui.screens.more.QuickUnlockContent
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.LockMessage
import com.invision.pos.viewmodel.LockUiState
import com.invision.pos.viewmodel.QuickUnlockForm
import com.invision.pos.viewmodel.QuickUnlockMessage
import com.invision.pos.viewmodel.QuickUnlockUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class LockScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The gate

    @Test
    fun locking_hidesTheApp_butKeepsItsStateForWhenThePersonComesBack() {
        val lock = mutableStateOf(LockInfo())
        composeRule.setContent {
            InvisionTheme {
                AuthGate(
                    state = AuthState.SignedIn("Roy"),
                    lock = lock.value,
                    lockScreen = { Text("LOCK SCREEN", modifier = Modifier.testTag("fake_lock")) },
                    app = {
                        var taps by remember { mutableIntStateOf(0) }
                        Column {
                            Text("taps $taps", modifier = Modifier.testTag("taps"))
                            Text("add", modifier = Modifier.testTag("add").clickable { taps++ })
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("add").performClick()
        composeRule.onNodeWithTag("add").performClick()
        composeRule.onNodeWithText("taps 2").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_lock").assertDoesNotExist()

        lock.value = LockInfo(locked = true, userName = "Roy")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fake_lock").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_overlay").assertExists()
        composeRule.onNodeWithTag("taps").assertDoesNotExist() // hidden from screen readers too
        composeRule.onNodeWithTag("add").assertDoesNotExist()

        lock.value = LockInfo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fake_lock").assertDoesNotExist()
        composeRule.onNodeWithText("taps 2").assertIsDisplayed() // the unfinished work is still there
    }

    @Test
    fun theLockScreen_isNeverShownToSomeoneSignedOut() {
        composeRule.setContent {
            InvisionTheme {
                AuthGate(
                    state = AuthState.SignedOut,
                    login = { Text("LOGIN", modifier = Modifier.testTag("fake_login")) },
                    lock = LockInfo(locked = true),
                    lockScreen = { Text("LOCK", modifier = Modifier.testTag("fake_lock")) },
                    app = { Text("APP", modifier = Modifier.testTag("fake_app")) },
                )
            }
        }
        composeRule.onNodeWithTag("fake_login").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_lock").assertDoesNotExist()
        composeRule.onNodeWithTag("fake_app").assertDoesNotExist()
    }

    // The lock screen

    private class Taps {
        var pinUnlock = 0
        var passwordUnlock = 0
        var fingerprint = 0
        var signOut = 0
        var pin = ""
        var password = ""
    }

    private fun lockScreen(info: LockInfo, state: LockUiState = LockUiState(), fingerprintOffered: Boolean = false, taps: Taps = Taps()) {
        composeRule.setContent {
            InvisionTheme {
                LockContent(
                    info = info,
                    state = state,
                    fingerprintOffered = fingerprintOffered,
                    onPinChange = { taps.pin += it },
                    onUnlockWithPin = { taps.pinUnlock++ },
                    onPasswordChange = { taps.password += it },
                    onUnlockWithPassword = { taps.passwordUnlock++ },
                    onUseFingerprint = { taps.fingerprint++ },
                    onSignOut = { taps.signOut++ },
                )
            }
        }
    }

    @Test
    fun withAPin_theLockScreenAsksForIt_andAlsoOffersThePassword() {
        val taps = Taps()
        lockScreen(LockInfo(locked = true, userName = "Asha", hasPin = true), LockUiState(pin = "48"), taps = taps)
        composeRule.onNodeWithTag("lock_user").assertIsDisplayed()
        composeRule.onNodeWithText("Signed in as Asha. Unlock to carry on where you left off.").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_pin").performTextInput("2")
        composeRule.onNodeWithTag("lock_pin_unlock").performClick()
        composeRule.onNodeWithTag("lock_password").performTextInput("pw")
        composeRule.onNodeWithTag("lock_sign_out").performScrollTo().performClick()
        assertEquals("482", taps.pin) // what the field now holds: the 48 already typed, then 2
        assertEquals(1, taps.pinUnlock)
        assertEquals("pw", taps.password)
        assertEquals(1, taps.signOut)
    }

    @Test
    fun withoutAPin_onlyThePasswordIsOffered() {
        lockScreen(LockInfo(locked = true, userName = "Asha"))
        composeRule.onNodeWithTag("lock_pin").assertDoesNotExist()
        composeRule.onNodeWithTag("lock_fingerprint").assertDoesNotExist()
        composeRule.onNodeWithTag("lock_password").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_sign_out").assertExists()
    }

    @Test
    fun theUnlockButtons_waitForSomethingToBeTyped() {
        lockScreen(LockInfo(locked = true, hasPin = true), LockUiState())
        composeRule.onNodeWithTag("lock_pin_unlock").assertIsNotEnabled()
        composeRule.onNodeWithTag("lock_password_unlock").assertIsNotEnabled()
    }

    @Test
    fun theUnlockButtons_workOnceSomethingIsTyped() {
        val taps = Taps()
        lockScreen(LockInfo(locked = true, hasPin = true), LockUiState(pin = "4829", password = "x"), taps = taps)
        composeRule.onNodeWithTag("lock_pin_unlock").assertIsEnabled()
        composeRule.onNodeWithTag("lock_password_unlock").performScrollTo().performClick()
        assertEquals(1, taps.passwordUnlock)
    }

    @Test
    fun theFingerprintButton_showsWhenOffered() {
        val taps = Taps()
        lockScreen(LockInfo(locked = true, fingerprint = true), fingerprintOffered = true, taps = taps)
        composeRule.onNodeWithTag("lock_fingerprint").performClick()
        assertEquals(1, taps.fingerprint)
    }

    @Test
    fun afterTooManyWrongPins_onlyThePasswordRemains_withAnExplanation() {
        lockScreen(LockInfo(locked = true, hasPin = true, fingerprint = true, quickUnlockOff = true), fingerprintOffered = false)
        composeRule.onNodeWithTag("lock_quick_off").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_pin").assertDoesNotExist()
        composeRule.onNodeWithTag("lock_fingerprint").assertDoesNotExist()
        composeRule.onNodeWithTag("lock_password").assertIsDisplayed()
    }

    @Test
    fun lockMessages_areShown() {
        lockScreen(LockInfo(locked = true, hasPin = true), LockUiState(message = LockMessage.WrongPin(3)))
        composeRule.onNodeWithText("Wrong PIN. 3 tries left.").assertIsDisplayed()
    }

    @Test
    fun theLastTry_isSaidPlainly() {
        lockScreen(LockInfo(locked = true, hasPin = true), LockUiState(message = LockMessage.WrongPin(1)))
        composeRule.onNodeWithText("Wrong PIN. 1 try left.").assertIsDisplayed()
    }

    @Test
    fun aWrongLockPassword_isReported() {
        lockScreen(LockInfo(locked = true), LockUiState(message = LockMessage.WrongPassword))
        composeRule.onNodeWithText("That password is not right.").assertIsDisplayed()
    }

    // The Quick unlock page

    private class PageTaps {
        var openPin = 0
        var openFingerprint = 0
        var close = 0
        var savePin = 0
        var removePin = 0
        var fingerprintOn = 0
        var fingerprintOff = 0
        var lockNow = 0
        var idle: IdleTimeout? = null
        var password = ""
        var pin = ""
        var confirm = ""
    }

    private fun page(state: QuickUnlockUiState, fingerprintAvailable: Boolean = true, taps: PageTaps = PageTaps()) {
        composeRule.setContent {
            InvisionTheme {
                QuickUnlockContent(
                    state = state,
                    fingerprintAvailable = fingerprintAvailable,
                    onBack = {},
                    onOpenPinForm = { taps.openPin++ },
                    onOpenFingerprintForm = { taps.openFingerprint++ },
                    onCloseForm = { taps.close++ },
                    onPasswordChange = { taps.password += it },
                    onPinChange = { taps.pin += it },
                    onConfirmPinChange = { taps.confirm += it },
                    onSavePin = { taps.savePin++ },
                    onRemovePin = { taps.removePin++ },
                    onConfirmFingerprintOn = { taps.fingerprintOn++ },
                    onTurnFingerprintOff = { taps.fingerprintOff++ },
                    onChooseIdleTime = { taps.idle = it },
                    onLockNow = { taps.lockNow++ },
                )
            }
        }
    }

    @Test
    fun thePage_showsWhetherAPinIsSet_andOffersToSetOrChangeIt() {
        val taps = PageTaps()
        page(QuickUnlockUiState(), taps = taps)
        composeRule.onNodeWithText("No PIN set").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_pin_remove").assertDoesNotExist()
        composeRule.onNodeWithTag("quick_pin_set").performClick()
        assertEquals(1, taps.openPin)
    }

    @Test
    fun withAPin_itCanBeChangedOrRemoved() {
        val taps = PageTaps()
        page(QuickUnlockUiState(status = QuickUnlockStatus(hasPin = true, fingerprint = false)), taps = taps)
        composeRule.onNodeWithText("A PIN is set").assertIsDisplayed()
        composeRule.onNodeWithText("Change PIN").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_pin_remove").performClick()
        assertEquals(1, taps.removePin)
    }

    @Test
    fun thePinForm_asksForThePassword_andTheTwoPins() {
        val taps = PageTaps()
        page(QuickUnlockUiState(form = QuickUnlockForm.PIN), taps = taps)
        composeRule.onNodeWithTag("quick_password").performTextInput("pw")
        composeRule.onNodeWithTag("quick_pin").performTextInput("4829")
        composeRule.onNodeWithTag("quick_confirm_pin").performTextInput("4829")
        composeRule.onNodeWithTag("quick_pin_save").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick_cancel").performScrollTo().performClick()
        assertEquals("pw", taps.password)
        assertEquals("4829", taps.pin)
        assertEquals("4829", taps.confirm)
        assertEquals(1, taps.savePin)
        assertEquals(1, taps.close)
    }

    @Test
    fun pinProblems_areExplained() {
        page(QuickUnlockUiState(form = QuickUnlockForm.PIN, message = QuickUnlockMessage.PinProblem(PinError.TOO_SIMPLE)))
        composeRule.onNodeWithText("That PIN is too easy to guess. Avoid repeats and runs such as 1111 or 1234.").assertIsDisplayed()
    }

    @Test
    fun aWrongPassword_isReportedOnTheForm() {
        page(QuickUnlockUiState(form = QuickUnlockForm.PIN, message = QuickUnlockMessage.WrongPassword))
        composeRule.onNodeWithTag("quick_error").assertIsDisplayed()
        composeRule.onNodeWithText("That password is not right.").assertIsDisplayed()
    }

    @Test
    fun theFingerprintSwitch_opensAPasswordForm_whenTurnedOn() {
        val taps = PageTaps()
        page(QuickUnlockUiState(), taps = taps)
        composeRule.onNodeWithTag("quick_fingerprint_switch").performClick()
        assertEquals(1, taps.openFingerprint)
        assertEquals(0, taps.fingerprintOff)
    }

    @Test
    fun theFingerprintSwitch_turnsItOffAtOnce() {
        val taps = PageTaps()
        page(QuickUnlockUiState(status = QuickUnlockStatus(hasPin = false, fingerprint = true)), taps = taps)
        composeRule.onNodeWithTag("quick_fingerprint_switch").performClick()
        assertEquals(1, taps.fingerprintOff)
    }

    @Test
    fun withoutASensor_itCannotBeTurnedOn() {
        page(QuickUnlockUiState(), fingerprintAvailable = false)
        composeRule.onNodeWithTag("quick_fingerprint_switch").assertIsNotEnabled()
        composeRule.onNodeWithText("This phone has no fingerprint sensor set up.").assertIsDisplayed()
    }

    @Test
    fun withoutASensor_itCanStillBeTurnedOff() {
        page(QuickUnlockUiState(status = QuickUnlockStatus(hasPin = false, fingerprint = true)), fingerprintAvailable = false)
        composeRule.onNodeWithTag("quick_fingerprint_switch").assertIsEnabled()
    }

    @Test
    fun theFingerprintForm_asksForThePassword() {
        val taps = PageTaps()
        page(QuickUnlockUiState(form = QuickUnlockForm.FINGERPRINT), taps = taps)
        composeRule.onNodeWithTag("quick_password").performTextInput("pw")
        composeRule.onNodeWithTag("quick_fingerprint_confirm").performScrollTo().performClick()
        assertEquals("pw", taps.password)
        assertEquals(1, taps.fingerprintOn)
    }

    @Test
    fun lockNow_isOnThePage() {
        val taps = PageTaps()
        page(QuickUnlockUiState(), taps = taps)
        composeRule.onNodeWithTag("quick_lock_now").performClick()
        assertEquals(1, taps.lockNow)
    }

    @Test
    fun theIdleTime_isOnlyShownToTheSuperAdmin() {
        page(QuickUnlockUiState(canChooseIdleTime = false))
        composeRule.onNodeWithTag("quick_idle_MINUTES_5").assertDoesNotExist()
        composeRule.onNodeWithText("Lock automatically").assertDoesNotExist()
    }

    @Test
    fun theSuperAdmin_picksTheIdleTime() {
        val taps = PageTaps()
        page(QuickUnlockUiState(canChooseIdleTime = true, idleTimeout = IdleTimeout.MINUTES_2), taps = taps)
        composeRule.onNodeWithText("Lock automatically").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("After 1 minute").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Never").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("quick_idle_MINUTES_10").performScrollTo().performClick()
        assertEquals(IdleTimeout.MINUTES_10, taps.idle)
    }
}

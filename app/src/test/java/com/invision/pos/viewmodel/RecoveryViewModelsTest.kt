package com.invision.pos.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.auth.FileAccountStore
import com.invision.pos.core.auth.LocalAuthRepository
import com.invision.pos.core.auth.PasswordHasher
import com.invision.pos.core.auth.RecoveryCodes
import com.invision.pos.core.auth.RecoveryPrompt
import com.invision.pos.core.auth.RecoveryStatus
import com.invision.pos.core.auth.SetupError
import com.invision.pos.core.auth.SetupField
import com.invision.pos.core.auth.SignInResult
import com.invision.pos.testutil.MainDispatcherRule
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryViewModelsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var auth: LocalAuthRepository

    private val owner = "roy.babu"
    private val ownerPw = "Green-Leaf-42"
    private val newPw = "Blue-River-77"

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "recovery-vm-${UUID.randomUUID()}").apply { mkdirs() }
        auth = LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000))
    }

    private fun <T> waitUntil(block: suspend () -> T): T = runBlocking { withTimeout(15_000) { block() } }

    private fun setUp() = runBlocking { auth.createAdministrator("Roy Babu", owner, ownerPw, ownerPw) }

    private fun RecoveryCodeViewModel.settle() = waitUntil { uiState.first { !it.busy } }

    // Creating the code

    @Test
    fun rightAfterSetup_noPasswordIsAsked_andTheFlowGoesIntroShowConfirmSaved() {
        setUp()
        val vm = RecoveryCodeViewModel(auth)
        assertFalse(vm.uiState.value.needsPassword)
        assertEquals(RecoveryStep.INTRO, vm.uiState.value.step)

        vm.start()
        vm.settle()
        assertEquals(RecoveryStep.SHOW, vm.uiState.value.step)
        val shown = vm.uiState.value.code
        assertTrue(RecoveryCodes.isWellFormed(shown))
        assertTrue(shown.contains('-'))

        vm.onWrittenDown()
        assertEquals(RecoveryStep.CONFIRM, vm.uiState.value.step)
        vm.onTypedChange(shown.lowercase())
        vm.confirm()
        vm.settle()

        assertEquals(RecoveryStep.SAVED, vm.uiState.value.step)
        assertEquals("", vm.uiState.value.code) // the code is not kept once saved
        assertEquals("", vm.uiState.value.typed)
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
    }

    @Test
    fun aWrongCopy_isRejected_andNothingIsSaved() {
        setUp()
        val vm = RecoveryCodeViewModel(auth)
        vm.start()
        vm.settle()
        vm.onWrittenDown()
        vm.onTypedChange("AAAAA-AAAAA-AAAAA-AAAAA")
        vm.confirm()
        vm.settle()
        assertEquals(RecoveryStep.CONFIRM, vm.uiState.value.step)
        assertEquals(RecoveryMessage.Mismatch, vm.uiState.value.message)
        assertEquals(RecoveryPrompt.Required, auth.recoveryPrompt.value)
        assertEquals(RecoveryStatus(null), runBlocking { auth.recoveryStatus() })

        // Typing again clears the message, and "show the code again" goes back to the code.
        vm.onTypedChange("A")
        assertEquals(null, vm.uiState.value.message)
        vm.showCodeAgain()
        assertEquals(RecoveryStep.SHOW, vm.uiState.value.step)
    }

    @Test
    fun anExistingInstall_isAskedForThePassword_andMayPutItOff() {
        setUp()
        auth = LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000))
        runBlocking { auth.signIn(owner, ownerPw) }
        assertEquals(RecoveryPrompt.Offered, auth.recoveryPrompt.value)

        val vm = RecoveryCodeViewModel(auth)
        assertTrue(vm.uiState.value.needsPassword)
        vm.start()
        vm.settle()
        assertEquals(RecoveryStep.INTRO, vm.uiState.value.step)
        assertEquals(RecoveryMessage.WrongPassword, vm.uiState.value.message)

        vm.onPasswordChange(ownerPw)
        assertEquals(null, vm.uiState.value.message)
        vm.start()
        vm.settle()
        assertEquals(RecoveryStep.SHOW, vm.uiState.value.step)
        assertEquals("", vm.uiState.value.password) // the password is not kept

        RecoveryCodeViewModel(auth).skip()
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
    }

    @Test
    fun repeatedWrongPasswords_showTheWait() {
        setUp()
        auth = LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000))
        runBlocking { auth.signIn(owner, ownerPw) }
        val vm = RecoveryCodeViewModel(auth)
        repeat(5) {
            vm.onPasswordChange("wrong")
            vm.start()
            vm.settle()
        }
        vm.onPasswordChange(ownerPw)
        vm.start()
        vm.settle()
        assertTrue(vm.uiState.value.message is RecoveryMessage.LockedOut)
    }

    @Test
    fun aSecondTap_whileWorking_isIgnored() {
        setUp()
        val vm = RecoveryCodeViewModel(auth)
        vm.start()
        vm.start()
        vm.settle()
        assertEquals(RecoveryStep.SHOW, vm.uiState.value.step)
    }

    // Forgot password

    private fun forgotten(): String {
        setUp()
        val code = runBlocking {
            val shown = (auth.startRecoveryCode(null) as com.invision.pos.core.auth.RecoveryStartResult.Ready).code
            auth.confirmRecoveryCode(shown)
            shown
        }
        auth.signOut()
        return code
    }

    private fun PasswordResetViewModel.settle() = waitUntil { uiState.first { !it.busy } }

    private fun PasswordResetViewModel.fill(code: String, pw: String = newPw, confirm: String = pw) {
        onCodeChange(code)
        onPasswordChange(pw)
        onConfirmChange(confirm)
    }

    @Test
    fun theResetFlow_changesThePassword_showsANewCode_andFinishesOnlyWhenItIsTypedBack() {
        val code = forgotten()
        val vm = PasswordResetViewModel(auth)
        vm.fill(code)
        vm.submit()
        vm.settle()

        var state = vm.uiState.value
        assertEquals(ResetStep.SHOW, state.step)
        assertNotEquals(RecoveryCodes.normalize(code), RecoveryCodes.normalize(state.newCode))
        // Nothing typed is kept once the password has changed.
        assertEquals("", state.code)
        assertEquals("", state.password)
        assertEquals("", state.confirm)

        val newCode = state.newCode
        vm.onWrittenDown()
        vm.onTypedChange("AAAAA-AAAAA-AAAAA-AAAAA")
        vm.confirmNewCode()
        assertEquals(ResetStep.CONFIRM, vm.uiState.value.step)
        assertEquals(ResetMessage.Mismatch, vm.uiState.value.message)

        vm.onTypedChange(newCode.lowercase())
        vm.confirmNewCode()
        state = vm.uiState.value
        assertEquals(ResetStep.DONE, state.step)
        assertEquals("", state.newCode)

        assertEquals(SignInResult.Success, runBlocking { auth.signIn(owner, newPw) })
    }

    @Test
    fun aWrongCode_isReported_andTheFormStaysOpen() {
        forgotten()
        val vm = PasswordResetViewModel(auth)
        vm.fill(RecoveryCodes.generate())
        vm.submit()
        vm.settle()
        assertEquals(ResetStep.FORM, vm.uiState.value.step)
        assertEquals(ResetMessage.WrongCode, vm.uiState.value.message)
    }

    @Test
    fun aPasswordProblem_isShownOnItsField() {
        val code = forgotten()
        val vm = PasswordResetViewModel(auth)
        vm.fill(code, pw = "short1", confirm = "short1")
        vm.submit()
        vm.settle()
        assertEquals(SetupError.PASSWORD_TOO_SHORT, vm.uiState.value.errors[SetupField.PASSWORD])
        vm.onPasswordChange("Blue-River-77")
        assertEquals(null, vm.uiState.value.errors[SetupField.PASSWORD])
        assertEquals(ResetStep.FORM, vm.uiState.value.step)
    }

    @Test
    fun withoutACode_theScreenSaysThereIsNoWayBack() {
        setUp()
        auth.signOut()
        val vm = PasswordResetViewModel(auth)
        vm.fill(RecoveryCodes.generate())
        vm.submit()
        vm.settle()
        assertEquals(ResetMessage.NoCode, vm.uiState.value.message)
    }
}

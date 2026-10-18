package com.auxo.pos.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.FileAccountStore
import com.auxo.pos.core.auth.IdleTimeout
import com.auxo.pos.core.auth.InMemoryLockSettings
import com.auxo.pos.core.auth.LocalAuthRepository
import com.auxo.pos.core.auth.PasswordHasher
import com.auxo.pos.core.auth.PinError
import com.auxo.pos.core.auth.QuickUnlockStatus
import com.auxo.pos.core.auth.SignInResult
import com.auxo.pos.core.auth.UserChangeResult
import com.auxo.pos.testutil.MainDispatcherRule
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickUnlockViewModelsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var auth: LocalAuthRepository

    private val ownerPw = "Green-Leaf-42"

    @Before
    fun freshDevice() {
        val dir = File(context.cacheDir, "quick-vm-${UUID.randomUUID()}").apply { mkdirs() }
        auth = LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000), lockSettings = InMemoryLockSettings())
        runBlocking { auth.createAdministrator("Sample User", "sample.user", ownerPw, ownerPw) }
    }

    private fun <T> waitUntil(block: suspend () -> T): T = runBlocking { withTimeout(15_000) { block() } }

    private fun QuickUnlockViewModel.settle() = waitUntil { uiState.first { !it.busy } }

    private fun LockViewModel.settle() = waitUntil { uiState.first { !it.busy } }

    private fun QuickUnlockViewModel.setPin(pin: String, password: String = ownerPw, confirm: String = pin) {
        openPinForm()
        onPasswordChange(password)
        onPinChange(pin)
        onConfirmPinChange(confirm)
        savePin()
        settle()
    }

    // The Quick unlock page

    @Test
    fun aPin_isSetWithThePassword_andTheFormCloses() {
        val vm = QuickUnlockViewModel(auth)
        vm.setPin("4829")
        assertEquals(QuickUnlockForm.NONE, vm.uiState.value.form)
        waitUntil { vm.uiState.first { it.status.hasPin } }
        assertEquals("", vm.uiState.value.pin) // nothing typed is kept
        assertEquals("", vm.uiState.value.password)
    }

    @Test
    fun pinProblems_areCaughtBeforeAnythingIsSaved() {
        val vm = QuickUnlockViewModel(auth)
        vm.setPin("1234")
        assertEquals(QuickUnlockMessage.PinProblem(PinError.TOO_SIMPLE), vm.uiState.value.message)
        vm.setPin("12")
        assertEquals(QuickUnlockMessage.PinProblem(PinError.WRONG_LENGTH), vm.uiState.value.message)
        vm.setPin("4829", confirm = "4830")
        assertEquals(QuickUnlockMessage.PinMismatch, vm.uiState.value.message)
        assertEquals(QuickUnlockForm.PIN, vm.uiState.value.form)
        assertEquals(QuickUnlockStatus(false, false), runBlocking { auth.quickUnlockStatus() })
    }

    @Test
    fun theTypedPin_keepsOnlyDigits_upToSix() {
        val vm = QuickUnlockViewModel(auth)
        vm.onPinChange("12a3-45 6789")
        assertEquals("123456", vm.uiState.value.pin)
    }

    @Test
    fun aWrongPassword_keepsTheFormOpen_andClearsThePassword() {
        val vm = QuickUnlockViewModel(auth)
        vm.setPin("4829", password = "wrong")
        assertEquals(QuickUnlockMessage.WrongPassword, vm.uiState.value.message)
        assertEquals(QuickUnlockForm.PIN, vm.uiState.value.form)
        assertEquals("", vm.uiState.value.password)
    }

    @Test
    fun aPin_canBeRemoved() {
        val vm = QuickUnlockViewModel(auth)
        vm.setPin("4829")
        vm.removePin()
        waitUntil { vm.uiState.first { !it.status.hasPin } }
        assertEquals(QuickUnlockStatus(false, false), runBlocking { auth.quickUnlockStatus() })
    }

    @Test
    fun fingerprint_isTurnedOnWithThePassword_andOffAtOnce() {
        val vm = QuickUnlockViewModel(auth)
        vm.openFingerprintForm()
        vm.onPasswordChange("wrong")
        vm.confirmFingerprintOn()
        vm.settle()
        assertEquals(QuickUnlockMessage.WrongPassword, vm.uiState.value.message)

        vm.onPasswordChange(ownerPw)
        vm.confirmFingerprintOn()
        vm.settle()
        waitUntil { vm.uiState.first { it.status.fingerprint } }
        assertEquals(QuickUnlockForm.NONE, vm.uiState.value.form)

        vm.turnFingerprintOff()
        waitUntil { vm.uiState.first { !it.status.fingerprint } }
    }

    @Test
    fun theSuperAdmin_picksTheIdleTime_andThePageFollowsIt() {
        val vm = QuickUnlockViewModel(auth)
        assertTrue(vm.uiState.value.canChooseIdleTime)
        vm.chooseIdleTime(IdleTimeout.MINUTES_10)
        waitUntil { vm.uiState.first { it.idleTimeout == IdleTimeout.MINUTES_10 } }
        assertEquals(IdleTimeout.MINUTES_10, auth.idleTimeout.value)
    }

    @Test
    fun anEmployee_cannotChooseTheIdleTime() {
        runBlocking {
            assertEquals(UserChangeResult.Done, auth.createEmployee("Test Staff", "test.staff", "Counter-Desk-17", "Counter-Desk-17"))
            auth.signOut()
            assertEquals(SignInResult.Success, auth.signIn("test.staff", "Counter-Desk-17"))
        }
        val vm = QuickUnlockViewModel(auth)
        assertFalse(vm.uiState.value.canChooseIdleTime)
        vm.chooseIdleTime(IdleTimeout.MINUTES_1)
        assertEquals(IdleTimeout.OFF, auth.idleTimeout.value)
    }

    @Test
    fun lockNow_locksTheApp() {
        QuickUnlockViewModel(auth).lockNow()
        assertTrue(auth.lock.value.locked)
    }

    // The lock screen

    private fun lockedWithPin(): LockViewModel {
        QuickUnlockViewModel(auth).setPin("4829")
        auth.lockNow()
        return LockViewModel(auth)
    }

    @Test
    fun theRightPin_unlocks() {
        val vm = lockedWithPin()
        vm.onPinChange("4829")
        vm.unlockWithPin()
        vm.settle()
        assertFalse(auth.lock.value.locked)
        assertEquals("", vm.uiState.value.pin)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun aWrongPin_countsDown_thenSwitchesQuickUnlockOff() {
        val vm = lockedWithPin()
        for (left in listOf(4, 3, 2, 1)) {
            vm.onPinChange("0000")
            vm.unlockWithPin()
            vm.settle()
            assertEquals(LockMessage.WrongPin(left), vm.uiState.value.message)
            assertEquals("", vm.uiState.value.pin)
        }
        vm.onPinChange("0000")
        vm.unlockWithPin()
        vm.settle()
        assertEquals(LockMessage.PinOff, vm.uiState.value.message)
        assertTrue(vm.lock.value.quickUnlockOff)
        assertTrue(auth.lock.value.locked)
    }

    @Test
    fun thePassword_unlocksTheSamePerson() {
        val vm = lockedWithPin()
        vm.onPasswordChange("wrong")
        vm.unlockWithPassword()
        vm.settle()
        assertEquals(LockMessage.WrongPassword, vm.uiState.value.message)
        vm.onPasswordChange(ownerPw)
        vm.unlockWithPassword()
        vm.settle()
        assertFalse(auth.lock.value.locked)
        assertEquals(AuthState.SignedIn("Sample User"), auth.state.value)
    }

    @Test
    fun aFingerprintThatIsNotTurnedOn_doesNotUnlock() {
        auth.lockNow()
        val vm = LockViewModel(auth)
        vm.fingerprintAccepted()
        assertTrue(auth.lock.value.locked)
        assertEquals(LockMessage.Unavailable, vm.uiState.value.message)
    }

    @Test
    fun aFingerprintTurnedOn_unlocks() {
        runBlocking { auth.setFingerprint(true, ownerPw) }
        auth.lockNow()
        LockViewModel(auth).fingerprintAccepted()
        assertFalse(auth.lock.value.locked)
    }

    @Test
    fun signingOutFromTheLock_letsSomeoneElseSignIn() {
        auth.lockNow()
        LockViewModel(auth).signOut()
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertFalse(auth.lock.value.locked)
    }

    @Test
    fun emptyEntries_areIgnored() {
        val vm = lockedWithPin()
        vm.unlockWithPin()
        vm.unlockWithPassword()
        assertNull(vm.uiState.value.message)
        assertTrue(auth.lock.value.locked)
    }
}

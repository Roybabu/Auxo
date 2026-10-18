package com.auxo.pos.core.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickUnlockRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val clock = MutableClock()
    private val hasher = PasswordHasher(iterations = 1_000)
    private val events = mutableListOf<AccountEvent>()
    private lateinit var settings: InMemoryLockSettings

    private val owner = "sample.user"
    private val ownerPw = "Green-Leaf-42"
    private val staff = "test.staff"
    private val staffPw = "Counter-Desk-17"

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "quick-${UUID.randomUUID()}").apply { mkdirs() }
        events.clear()
        settings = InMemoryLockSettings()
    }

    private fun launch() = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default, { events += it }, settings)

    private val accountFile get() = File(dir, "accounts.json")

    private suspend fun LocalAuthRepository.ownerSignedIn() {
        createAdministrator("Sample User", owner, ownerPw, ownerPw)
    }

    private suspend fun LocalAuthRepository.withStaff() {
        ownerSignedIn()
        assertEquals(UserChangeResult.Done, createEmployee("Test Staff", staff, staffPw, staffPw))
    }

    private suspend fun LocalAuthRepository.staffSignedIn() {
        signOut()
        assertEquals(SignInResult.Success, signIn(staff, staffPw))
    }

    // Setting a PIN

    @Test
    fun aPin_isSavedAsAHashOnTheOwnersAccount() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        assertEquals(QuickUnlockStatus(false, false), auth.quickUnlockStatus())
        assertEquals(QuickUnlockChange.Done, auth.setPin(ownerPw, "4829"))
        assertEquals(QuickUnlockStatus(true, false), auth.quickUnlockStatus())
        val text = accountFile.readText()
        assertFalse(text.contains("4829"))
        val saved = JSONObject(text).getJSONArray("accounts").getJSONObject(0).getJSONObject("pin")
        assertEquals(setOf("salt", "hash", "iterations"), saved.keys().asSequence().toSet())
    }

    @Test
    fun settingAPin_asksForThePassword_andChecksThePin() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        assertEquals(QuickUnlockChange.WrongPassword, auth.setPin("wrong", "4829"))
        assertEquals(QuickUnlockChange.InvalidPin(PinError.TOO_SIMPLE), auth.setPin(ownerPw, "1234"))
        assertEquals(QuickUnlockChange.InvalidPin(PinError.WRONG_LENGTH), auth.setPin(ownerPw, "12"))
        assertEquals(QuickUnlockStatus(false, false), auth.quickUnlockStatus())
    }

    @Test
    fun wrongPasswordsWhenSettingAPin_shareTheSignInWait() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        repeat(5) { assertEquals(QuickUnlockChange.WrongPassword, auth.setPin("wrong", "4829")) }
        assertTrue(auth.setPin(ownerPw, "4829") is QuickUnlockChange.LockedOut)
        clock.now += 31_000
        assertEquals(QuickUnlockChange.Done, auth.setPin(ownerPw, "4829"))
    }

    @Test
    fun aPin_belongsToOnePerson() = runBlocking {
        val auth = launch()
        auth.withStaff()
        auth.setPin(ownerPw, "4829")
        auth.staffSignedIn()
        assertEquals(QuickUnlockStatus(false, false), auth.quickUnlockStatus())
        assertEquals(QuickUnlockChange.Done, auth.setPin(staffPw, "7302"))
        auth.lockNow()
        assertTrue(auth.lock.value.hasPin)
        // The owner's PIN does not open the employee's lock.
        assertEquals(UnlockResult.WrongPin(4), auth.unlockWithPin("4829"))
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPin("7302"))
    }

    @Test
    fun aPin_canBeReplacedAndRemoved() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setPin(ownerPw, "4829")
        auth.setPin(ownerPw, "7302")
        auth.lockNow()
        assertEquals(UnlockResult.WrongPin(4), auth.unlockWithPin("4829"))
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPin("7302"))
        assertEquals(QuickUnlockChange.Done, auth.removePin())
        assertEquals(QuickUnlockStatus(false, false), auth.quickUnlockStatus())
        assertFalse(accountFile.readText().contains("\"pin\""))
        auth.lockNow()
        assertFalse(auth.lock.value.hasPin)
        assertEquals(UnlockResult.Unavailable, auth.unlockWithPin("7302"))
    }

    @Test
    fun nobodySignedIn_canSetNothing() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.signOut()
        assertEquals(QuickUnlockChange.Denied, auth.setPin(ownerPw, "4829"))
        assertEquals(QuickUnlockChange.Denied, auth.removePin())
        assertEquals(QuickUnlockChange.Denied, auth.setFingerprint(true, ownerPw))
        assertNull(auth.quickUnlockStatus())
    }

    @Test
    fun whileLocked_nothingCanBeChanged() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.lockNow()
        assertEquals(QuickUnlockChange.Denied, auth.setPin(ownerPw, "4829"))
        assertEquals(UserChangeResult.Denied, auth.setIdleTimeout(IdleTimeout.MINUTES_5))
        assertEquals(UserChangeResult.Denied, auth.createEmployee("X Y", "x.y.z", staffPw, staffPw))
        assertNull(auth.listUsers())
    }

    // Fingerprint

    @Test
    fun fingerprint_isOnlyTurnedOnWithThePassword() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        assertEquals(QuickUnlockChange.WrongPassword, auth.setFingerprint(true, null))
        assertEquals(QuickUnlockChange.WrongPassword, auth.setFingerprint(true, "wrong"))
        assertEquals(QuickUnlockChange.Done, auth.setFingerprint(true, ownerPw))
        assertEquals(QuickUnlockStatus(false, true), auth.quickUnlockStatus())
        assertEquals(QuickUnlockChange.Done, auth.setFingerprint(false, null))
        assertEquals(QuickUnlockStatus(false, false), auth.quickUnlockStatus())
    }

    @Test
    fun aFingerprintCheck_unlocksOnlyIfThePersonTurnedItOn() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.lockNow()
        assertFalse(auth.unlockWithFingerprint())
        assertTrue(auth.lock.value.locked)

        assertEquals(UnlockResult.Unlocked, auth.unlockWithPassword(ownerPw))
        auth.setFingerprint(true, ownerPw)
        assertFalse(auth.unlockWithFingerprint()) // not locked
        auth.lockNow()
        assertTrue(auth.lock.value.fingerprintAvailable)
        assertTrue(auth.unlockWithFingerprint())
        assertFalse(auth.lock.value.locked)
    }

    // Locking and unlocking

    @Test
    fun lockingKeepsTheSamePersonSignedIn() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        val before = auth.state.value
        assertTrue(auth.lockNow())
        assertTrue(auth.lock.value.locked)
        assertEquals("Sample User", auth.lock.value.userName)
        assertEquals(before, auth.state.value)
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPassword(ownerPw))
        assertEquals(before, auth.state.value)
        assertFalse(auth.lock.value.locked)
    }

    @Test
    fun nobodySignedIn_cannotLock() = runBlocking {
        val auth = launch()
        assertFalse(auth.lockNow())
        auth.ownerSignedIn()
        auth.signOut()
        assertFalse(auth.lockNow())
        assertFalse(auth.lock.value.locked)
    }

    @Test
    fun theRightPin_unlocks_andForgetsTheEarlierMistakes() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setPin(ownerPw, "4829")
        auth.lockNow()
        assertEquals(UnlockResult.WrongPin(4), auth.unlockWithPin("0000"))
        assertEquals(UnlockResult.WrongPin(3), auth.unlockWithPin("1111"))
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPin("4829"))
        auth.lockNow()
        assertEquals(UnlockResult.WrongPin(4), auth.unlockWithPin("0000"))
    }

    @Test
    fun fiveWrongPins_switchQuickUnlockOff_untilThePasswordIsUsed() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setPin(ownerPw, "4829")
        auth.setFingerprint(true, ownerPw)
        auth.lockNow()
        repeat(4) { assertTrue(auth.unlockWithPin("0000") is UnlockResult.WrongPin) }
        assertEquals(UnlockResult.PinLockedOut, auth.unlockWithPin("0000"))
        assertTrue(auth.lock.value.quickUnlockOff)
        assertFalse(auth.lock.value.pinAvailable || auth.lock.value.fingerprintAvailable)

        // Even the right PIN and the fingerprint are refused now.
        assertEquals(UnlockResult.Unavailable, auth.unlockWithPin("4829"))
        assertFalse(auth.unlockWithFingerprint())
        assertTrue(auth.lock.value.locked)
        assertTrue(events.any { it.kind == AccountEventKind.PIN_LOCKED_OUT })

        assertEquals(UnlockResult.Unlocked, auth.unlockWithPassword(ownerPw))
        auth.lockNow()
        assertTrue(auth.lock.value.pinAvailable) // a fresh lock offers the PIN again
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPin("4829"))
    }

    @Test
    fun theWrongPinCount_isNotKeptAcrossAppRestarts_becauseTheAppAlwaysStartsSignedOut() = runBlocking {
        val first = launch()
        first.ownerSignedIn()
        first.setPin(ownerPw, "4829")
        first.lockNow()
        val second = launch()
        assertEquals(AuthState.SignedOut, second.state.value)
        assertFalse(second.lock.value.locked)
        assertEquals(UnlockResult.Unavailable, second.unlockWithPin("4829"))
    }

    @Test
    fun theLockedPassword_mustBeThePersonsOwn() = runBlocking {
        val auth = launch()
        auth.withStaff()
        auth.staffSignedIn()
        auth.lockNow()
        assertEquals(UnlockResult.WrongPassword, auth.unlockWithPassword(ownerPw)) // the Super Admin's password
        assertTrue(auth.lock.value.locked)
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPassword(staffPw))
        assertEquals("Test Staff", (auth.state.value as AuthState.SignedIn).name)
    }

    @Test
    fun wrongPasswordsOnTheLockScreen_runIntoTheSameWait() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.lockNow()
        repeat(5) { assertEquals(UnlockResult.WrongPassword, auth.unlockWithPassword("wrong")) }
        assertTrue(auth.unlockWithPassword(ownerPw) is UnlockResult.LockedOut)
        clock.now += 31_000
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPassword(ownerPw))
    }

    @Test
    fun anotherPerson_signsInFresh_afterSigningOutFromTheLock() = runBlocking {
        val auth = launch()
        auth.withStaff()
        auth.setPin(ownerPw, "4829")
        auth.lockNow()
        auth.signOut() // "Sign in as someone else"
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertFalse(auth.lock.value.locked)
        assertEquals(SignInResult.Success, auth.signIn(staff, staffPw))
        assertFalse(auth.lock.value.locked)
        assertEquals(UnlockResult.Unavailable, auth.unlockWithPin("4829"))
    }

    @Test
    fun aLockedPerson_whoseAccountWasTurnedOff_cannotUnlock() = runBlocking {
        val auth = launch()
        auth.withStaff()
        auth.staffSignedIn()
        auth.setPin(staffPw, "7302")
        auth.lockNow()
        // The Super Admin turns the account off (from another session of the same file).
        val other = launch()
        other.signIn(owner, ownerPw)
        val id = other.listUsers()!!.first { it.role == Role.EMPLOYEE }.id
        other.setEmployeeActive(id, false)
        assertEquals(UnlockResult.Unavailable, auth.unlockWithPin("7302"))
        assertEquals(UnlockResult.Unavailable, auth.unlockWithPassword(staffPw))
        auth.revalidate()
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertFalse(auth.lock.value.locked)
    }

    // Password changes close the shortcut

    @Test
    fun resettingAnEmployeesPassword_removesTheirPinAndFingerprint() = runBlocking {
        val auth = launch()
        auth.withStaff()
        auth.staffSignedIn()
        auth.setPin(staffPw, "7302")
        auth.setFingerprint(true, staffPw)
        auth.signOut()
        auth.signIn(owner, ownerPw)
        val id = auth.listUsers()!!.first { it.role == Role.EMPLOYEE }.id
        assertEquals(UserChangeResult.Done, auth.resetEmployeePassword(id, "Fresh-Pass-31", "Fresh-Pass-31"))
        auth.signOut()
        auth.signIn(staff, "Fresh-Pass-31")
        assertEquals(QuickUnlockStatus(false, false), auth.quickUnlockStatus())
    }

    @Test
    fun resettingWithTheRecoveryCode_removesTheSuperAdminsPinAndFingerprint() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        val code = (auth.startRecoveryCode(null) as RecoveryStartResult.Ready).code
        auth.confirmRecoveryCode(code)
        auth.setPin(ownerPw, "4829")
        auth.setFingerprint(true, ownerPw)
        auth.signOut()
        assertTrue(auth.resetPasswordWithRecoveryCode(code, "Blue-River-77", "Blue-River-77") is RecoveryResetResult.Reset)
        auth.signIn(owner, "Blue-River-77")
        assertEquals(QuickUnlockStatus(false, false), auth.quickUnlockStatus())
    }

    // Idle time

    @Test
    fun theIdleTime_isOffUntilTheSuperAdminChoosesOne() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        assertEquals(IdleTimeout.OFF, auth.idleTimeout.value)
        clock.now += 24 * 3_600_000L
        auth.checkIdle()
        assertFalse(auth.lock.value.locked)
    }

    @Test
    fun onlyTheSuperAdmin_choosesTheIdleTime() = runBlocking {
        val auth = launch()
        auth.withStaff()
        assertEquals(UserChangeResult.Done, auth.setIdleTimeout(IdleTimeout.MINUTES_5))
        assertEquals(IdleTimeout.MINUTES_5, auth.idleTimeout.value)
        auth.staffSignedIn()
        assertEquals(UserChangeResult.Denied, auth.setIdleTimeout(IdleTimeout.OFF))
        assertEquals(IdleTimeout.MINUTES_5, auth.idleTimeout.value)
        auth.signOut()
        assertEquals(UserChangeResult.Denied, auth.setIdleTimeout(IdleTimeout.OFF))
    }

    @Test
    fun anIdleApp_locksAfterTheChosenTime_andActivityRestartsIt() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setIdleTimeout(IdleTimeout.MINUTES_2)

        clock.now += 119_000
        auth.checkIdle()
        assertFalse(auth.lock.value.locked)

        auth.noteActivity() // touch restarts the count
        clock.now += 119_000
        auth.checkIdle()
        assertFalse(auth.lock.value.locked)

        clock.now += 2_000
        auth.checkIdle()
        assertTrue(auth.lock.value.locked)
        assertEquals(AuthState.SignedIn("Sample User"), auth.state.value)
    }

    @Test
    fun touchesWhileLocked_doNotKeepTheLockFromHolding_andUnlockingStartsAFreshCount() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setIdleTimeout(IdleTimeout.MINUTES_1)
        clock.now += 61_000
        auth.checkIdle()
        assertTrue(auth.lock.value.locked)

        clock.now += 600_000
        auth.noteActivity()
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPassword(ownerPw))
        auth.checkIdle()
        assertFalse(auth.lock.value.locked) // the count began at the unlock, not before
        clock.now += 61_000
        auth.checkIdle()
        assertTrue(auth.lock.value.locked)
    }

    @Test
    fun comingBackToTheApp_afterALongTime_locksIt() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setIdleTimeout(IdleTimeout.MINUTES_10)
        clock.now += 3 * 3_600_000L
        auth.checkIdle()
        assertTrue(auth.lock.value.locked)
    }

    @Test
    fun theIdleTime_followsTheSettingsStore_andSurvivesANewLaunch() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setIdleTimeout(IdleTimeout.MINUTES_30)
        assertEquals(IdleTimeout.MINUTES_30, launch().idleTimeout.value)
    }

    @Test
    fun nobodySignedIn_isNeverLockedByIdleTime() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setIdleTimeout(IdleTimeout.MINUTES_1)
        auth.signOut()
        clock.now += 3_600_000L
        auth.checkIdle()
        assertFalse(auth.lock.value.locked)
    }

    // Activity log

    @Test
    fun theseChanges_areLogged_withoutAnySecret() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setPin(ownerPw, "4829")
        auth.setFingerprint(true, ownerPw)
        auth.setFingerprint(false, null)
        auth.removePin()
        auth.setIdleTimeout(IdleTimeout.MINUTES_5)
        val kinds = events.map { it.kind }
        assertEquals(
            listOf(
                AccountEventKind.PIN_SET,
                AccountEventKind.FINGERPRINT_TURNED_ON,
                AccountEventKind.FINGERPRINT_TURNED_OFF,
                AccountEventKind.PIN_REMOVED,
                AccountEventKind.AUTO_LOCK_CHANGED,
            ),
            kinds,
        )
        assertEquals(IdleTimeout.MINUTES_5, events.last().idleTimeout)
        assertTrue(events.none { it.toString().contains("4829") })
    }

    @Test
    fun aWrongPassword_orUnchangedChoice_isNotAnEvent() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setPin("wrong", "4829")
        auth.removePin() // nothing to remove
        auth.setFingerprint(false, null) // already off
        auth.setIdleTimeout(IdleTimeout.OFF) // already off
        assertTrue(events.isEmpty())
    }

    // The saved registry

    @Test
    fun aRegistryFromBeforeQuickUnlock_stillOpens() = runBlocking {
        launch().ownerSignedIn()
        val account = JSONObject(accountFile.readText()).getJSONArray("accounts").getJSONObject(0)
        assertFalse(account.has("pin"))
        assertFalse(account.has("fingerprint"))
        assertEquals(SignInResult.Success, launch().signIn(owner, ownerPw))
    }

    @Test
    fun aPinSurvivesOtherAccountChanges() = runBlocking {
        val auth = launch()
        auth.withStaff()
        auth.setPin(ownerPw, "4829")
        val id = auth.listUsers()!!.first { it.role == Role.EMPLOYEE }.id
        auth.setEmployeeActive(id, false)
        auth.setEmployeeAccess(id, emptySet())
        auth.lockNow()
        assertEquals(UnlockResult.Unlocked, auth.unlockWithPin("4829"))
    }

    @Test
    fun anEmptyPinProof_meansTheFileWasEdited() = runBlocking {
        val auth = launch()
        auth.ownerSignedIn()
        auth.setPin(ownerPw, "4829")
        val json = JSONObject(accountFile.readText())
        json.getJSONArray("accounts").getJSONObject(0).getJSONObject("pin").put("hash", "")
        accountFile.writeText(json.toString())
        assertEquals(AuthState.Unreadable, launch().state.value)
    }
}

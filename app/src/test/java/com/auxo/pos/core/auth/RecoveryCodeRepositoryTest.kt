// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryCodeRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val clock = MutableClock()
    private val hasher = PasswordHasher(iterations = 1_000)
    private val events = mutableListOf<AccountEvent>()

    private val owner = "sample.user"
    private val ownerPw = "Green-Leaf-42"
    private val newPw = "Blue-River-77"

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "recovery-${UUID.randomUUID()}").apply { mkdirs() }
        events.clear()
    }

    private fun launch() = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default, { events += it })

    private val accountFile get() = File(dir, "accounts.json")

    private suspend fun LocalAuthRepository.setUp() = createAdministrator("Sample User", owner, ownerPw, ownerPw)

    /** Sets up the Super Admin and goes through the whole code flow. Returns the code that was shown. */
    private suspend fun LocalAuthRepository.setUpWithCode(): String {
        setUp()
        val code = (startRecoveryCode(null) as RecoveryStartResult.Ready).code
        assertEquals(RecoveryConfirmResult.Saved, confirmRecoveryCode(code))
        return code
    }

    private suspend fun LocalAuthRepository.addEmployee() {
        assertEquals(UserChangeResult.Done, createEmployee("Test Staff", "test.staff", "Counter-Desk-17", "Counter-Desk-17"))
    }

    // Setting up the first code

    @Test
    fun firstSetup_requiresACode_andAsksForNoPassword() = runBlocking {
        val auth = launch()
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
        auth.setUp()
        assertEquals(RecoveryPrompt.Required, auth.recoveryPrompt.value)
        assertTrue(auth.startRecoveryCode(null) is RecoveryStartResult.Ready)
    }

    @Test
    fun theCode_isNotSavedUntilItIsConfirmed() = runBlocking {
        val auth = launch()
        auth.setUp()
        val code = (auth.startRecoveryCode(null) as RecoveryStartResult.Ready).code
        assertFalse(JSONObject(accountFile.readText()).getJSONArray("accounts").getJSONObject(0).has("recovery"))

        assertEquals(RecoveryConfirmResult.Mismatch, auth.confirmRecoveryCode("AAAAA-AAAAA-AAAAA-AAAAA"))
        assertEquals(RecoveryConfirmResult.Mismatch, auth.confirmRecoveryCode(""))
        assertFalse(accountFile.readText().contains("\"recovery\""))
        assertEquals(RecoveryPrompt.Required, auth.recoveryPrompt.value)
        assertEquals(RecoveryStatus(null), auth.recoveryStatus())

        assertEquals(RecoveryConfirmResult.Saved, auth.confirmRecoveryCode(code.lowercase().replace("-", " ")))
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
        assertEquals(RecoveryStatus(clock.now), auth.recoveryStatus())
    }

    @Test
    fun theSavedFile_holdsOnlyAHash_neverTheCode() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        val text = accountFile.readText()
        assertFalse(text.contains(code))
        assertFalse(text.contains(RecoveryCodes.normalize(code)))
        val saved = JSONObject(text).getJSONArray("accounts").getJSONObject(0).getJSONObject("recovery")
        assertEquals(setOf("salt", "hash", "iterations", "createdAt"), saved.keys().asSequence().toSet())
    }

    @Test
    fun confirmingWithNothingStarted_saysSo() = runBlocking {
        val auth = launch()
        auth.setUp()
        assertEquals(RecoveryConfirmResult.NothingPending, auth.confirmRecoveryCode("AAAAA-AAAAA-AAAAA-AAAAA"))
    }

    @Test
    fun aCodeNeverConfirmed_isLostWhenTheAppIsClosed() = runBlocking {
        val first = launch()
        first.setUp()
        val shown = (first.startRecoveryCode(null) as RecoveryStartResult.Ready).code

        val second = launch()
        assertEquals(SignInResult.Success, second.signIn(owner, ownerPw))
        assertEquals(RecoveryPrompt.Offered, second.recoveryPrompt.value)
        assertEquals(RecoveryConfirmResult.NothingPending, second.confirmRecoveryCode(shown))
        assertEquals(RecoveryResetResult.NoCode, run { second.signOut(); second.resetPasswordWithRecoveryCode(shown, newPw, newPw) })
    }

    // Existing installs

    @Test
    fun anInstallWithoutACode_isOfferedOneAfterSignIn_andMayLeaveItForLater() = runBlocking {
        launch().setUp()
        val auth = launch()
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
        assertEquals(SignInResult.Success, auth.signIn(owner, ownerPw))
        assertEquals(RecoveryPrompt.Offered, auth.recoveryPrompt.value)
        auth.dismissRecoveryOffer()
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)

        // It comes back at the next sign-in until a code exists.
        auth.signOut()
        assertEquals(SignInResult.Success, auth.signIn(owner, ownerPw))
        assertEquals(RecoveryPrompt.Offered, auth.recoveryPrompt.value)
    }

    @Test
    fun theFirstSetupPrompt_cannotBeDismissed() = runBlocking {
        val auth = launch()
        auth.setUp()
        auth.dismissRecoveryOffer()
        assertEquals(RecoveryPrompt.Required, auth.recoveryPrompt.value)
    }

    @Test
    fun onceACodeExists_noOfferIsMade() = runBlocking {
        launch().setUpWithCode()
        val auth = launch()
        assertEquals(SignInResult.Success, auth.signIn(owner, ownerPw))
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
    }

    @Test
    fun anOfferedCode_needsThePasswordAgain() = runBlocking {
        launch().setUp()
        val auth = launch()
        auth.signIn(owner, ownerPw)
        assertEquals(RecoveryStartResult.WrongPassword, auth.startRecoveryCode(null))
        assertEquals(RecoveryStartResult.WrongPassword, auth.startRecoveryCode("not the password"))
        val code = (auth.startRecoveryCode(ownerPw) as RecoveryStartResult.Ready).code
        assertEquals(RecoveryConfirmResult.Saved, auth.confirmRecoveryCode(code))
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
    }

    @Test
    fun replacingACode_needsThePassword_andKeepsTheOldCodeUntilConfirmed() = runBlocking {
        val auth = launch()
        val old = auth.setUpWithCode()
        assertEquals(RecoveryStartResult.WrongPassword, auth.startRecoveryCode("wrong"))
        val replacement = (auth.startRecoveryCode(ownerPw) as RecoveryStartResult.Ready).code
        assertNotEquals(old, replacement)

        // Not confirmed yet: the old code still works.
        auth.signOut()
        assertTrue(auth.resetPasswordWithRecoveryCode(old, newPw, newPw) is RecoveryResetResult.Reset)
    }

    @Test
    fun confirmingAReplacement_retiresTheOldCode() = runBlocking {
        val auth = launch()
        val old = auth.setUpWithCode()
        val replacement = (auth.startRecoveryCode(ownerPw) as RecoveryStartResult.Ready).code
        assertEquals(RecoveryConfirmResult.Saved, auth.confirmRecoveryCode(replacement))
        auth.signOut()
        assertEquals(RecoveryResetResult.WrongCode, auth.resetPasswordWithRecoveryCode(old, newPw, newPw))
        assertTrue(auth.resetPasswordWithRecoveryCode(replacement, newPw, newPw) is RecoveryResetResult.Reset)
    }

    @Test
    fun wrongPasswordsWhenAskingForACode_runIntoTheSameWaitAsSignIn() = runBlocking {
        launch().setUp()
        val auth = launch()
        auth.signIn(owner, ownerPw)
        repeat(5) { assertEquals(RecoveryStartResult.WrongPassword, auth.startRecoveryCode("wrong")) }
        assertTrue(auth.startRecoveryCode(ownerPw) is RecoveryStartResult.LockedOut)
        clock.now += 31_000
        assertTrue(auth.startRecoveryCode(ownerPw) is RecoveryStartResult.Ready)
    }

    @Test
    fun anEmployee_cannotMakeOrSeeACode() = runBlocking {
        val auth = launch()
        auth.setUpWithCode()
        auth.addEmployee()
        auth.signOut()
        assertEquals(SignInResult.Success, auth.signIn("test.staff", "Counter-Desk-17"))
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
        assertEquals(RecoveryStartResult.Denied, auth.startRecoveryCode("Counter-Desk-17"))
        assertEquals(RecoveryConfirmResult.Denied, auth.confirmRecoveryCode("AAAAA-AAAAA-AAAAA-AAAAA"))
        assertNull(auth.recoveryStatus())
    }

    @Test
    fun nobodySignedIn_cannotMakeACode() = runBlocking {
        val auth = launch()
        auth.setUp()
        auth.signOut()
        assertEquals(RecoveryStartResult.Denied, auth.startRecoveryCode(ownerPw))
        assertNull(auth.recoveryStatus())
    }

    @Test
    fun signingOut_dropsAnUnconfirmedCode() = runBlocking {
        val auth = launch()
        auth.setUp()
        val code = (auth.startRecoveryCode(null) as RecoveryStartResult.Ready).code
        auth.signOut()
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value)
        auth.signIn(owner, ownerPw)
        assertEquals(RecoveryConfirmResult.NothingPending, auth.confirmRecoveryCode(code))
    }

    // Resetting a forgotten password

    @Test
    fun theCode_resetsThePassword_andIssuesANewOne() = runBlocking {
        val auth = launch()
        val first = auth.setUpWithCode()
        auth.signOut()

        val result = auth.resetPasswordWithRecoveryCode(first.lowercase().replace('-', ' '), newPw, newPw) as RecoveryResetResult.Reset
        assertNotEquals(first, result.newCode)
        assertTrue(RecoveryCodes.isWellFormed(result.newCode))
        assertEquals(AuthState.SignedOut, auth.state.value)

        assertEquals(SignInResult.Rejected, auth.signIn(owner, ownerPw))
        assertEquals(SignInResult.Success, auth.signIn(owner, newPw))
        assertEquals(RecoveryPrompt.None, auth.recoveryPrompt.value) // the new code is already saved
    }

    @Test
    fun theUsedCode_stopsWorking_andTheNewOneWorks() = runBlocking {
        val auth = launch()
        val first = auth.setUpWithCode()
        auth.signOut()
        val second = (auth.resetPasswordWithRecoveryCode(first, newPw, newPw) as RecoveryResetResult.Reset).newCode

        assertEquals(RecoveryResetResult.WrongCode, auth.resetPasswordWithRecoveryCode(first, "Third-Pass-99", "Third-Pass-99"))
        assertEquals(SignInResult.Success, auth.signIn(owner, newPw))
        auth.signOut()

        val third = (auth.resetPasswordWithRecoveryCode(second, "Third-Pass-99", "Third-Pass-99") as RecoveryResetResult.Reset).newCode
        assertNotEquals(second, third)
        assertEquals(SignInResult.Success, launch().signIn(owner, "Third-Pass-99"))
    }

    @Test
    fun theUsernameAndNameAreUntouched() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        auth.signOut()
        auth.resetPasswordWithRecoveryCode(code, newPw, newPw)
        val account = (FileAccountStore(dir).read() as StoredAccounts.Present).superAdmin!!
        assertEquals(owner, account.identifier)
        assertEquals("Sample User", account.name)
        assertEquals(Role.SUPER_ADMIN, account.role)
        assertEquals(1L, account.id)
    }

    @Test
    fun resettingTheSuperAdmin_doesNotTouchEmployees() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        auth.addEmployee()
        auth.signOut()
        auth.resetPasswordWithRecoveryCode(code, newPw, newPw)
        assertEquals(SignInResult.Success, auth.signIn("test.staff", "Counter-Desk-17"))
    }

    @Test
    fun aWeakOrMismatchedNewPassword_isRefused_withoutUsingTheCode() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        auth.signOut()
        val short = auth.resetPasswordWithRecoveryCode(code, "abc", "abc")
        assertTrue(short is RecoveryResetResult.Invalid)
        assertTrue(SetupField.PASSWORD in (short as RecoveryResetResult.Invalid).errors)
        val mismatch = auth.resetPasswordWithRecoveryCode(code, newPw, "Different-Pass-1") as RecoveryResetResult.Invalid
        assertTrue(SetupField.CONFIRM in mismatch.errors)
        // The old password still works and the code was not spent.
        assertEquals(SignInResult.Success, auth.signIn(owner, ownerPw))
        auth.signOut()
        repeat(10) { auth.resetPasswordWithRecoveryCode(code, "abc", "abc") }
        assertTrue(auth.resetPasswordWithRecoveryCode(code, newPw, newPw) is RecoveryResetResult.Reset)
    }

    @Test
    fun wrongCodes_areSlowedDown() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        auth.signOut()
        repeat(5) { assertEquals(RecoveryResetResult.WrongCode, auth.resetPasswordWithRecoveryCode(RecoveryCodes.generate(), newPw, newPw)) }
        // Locked, even for the right code.
        val locked = auth.resetPasswordWithRecoveryCode(code, newPw, newPw)
        assertTrue(locked is RecoveryResetResult.LockedOut)
        assertEquals(30, (locked as RecoveryResetResult.LockedOut).seconds)

        clock.now += 31_000
        assertTrue(auth.resetPasswordWithRecoveryCode(code, newPw, newPw) is RecoveryResetResult.Reset)
    }

    @Test
    fun theWait_growsWithEachFurtherWrongCode() = runBlocking {
        val auth = launch()
        auth.setUpWithCode()
        auth.signOut()
        repeat(5) { auth.resetPasswordWithRecoveryCode(RecoveryCodes.generate(), newPw, newPw) }
        clock.now += 31_000
        assertEquals(RecoveryResetResult.WrongCode, auth.resetPasswordWithRecoveryCode(RecoveryCodes.generate(), newPw, newPw))
        val locked = auth.resetPasswordWithRecoveryCode(RecoveryCodes.generate(), newPw, newPw) as RecoveryResetResult.LockedOut
        assertEquals(60, locked.seconds)
    }

    @Test
    fun badCodeText_isJustAWrongCode() = runBlocking {
        val auth = launch()
        auth.setUpWithCode()
        auth.signOut()
        assertEquals(RecoveryResetResult.WrongCode, auth.resetPasswordWithRecoveryCode("", newPw, newPw))
        assertEquals(RecoveryResetResult.WrongCode, auth.resetPasswordWithRecoveryCode("12345", newPw, newPw))
    }

    @Test
    fun withoutACode_thereIsNoWayBack() = runBlocking {
        launch().setUp()
        val auth = launch()
        assertEquals(RecoveryResetResult.NoCode, auth.resetPasswordWithRecoveryCode(RecoveryCodes.generate(), newPw, newPw))
        assertEquals(SignInResult.Success, auth.signIn(owner, ownerPw)) // password unchanged
    }

    @Test
    fun theResetIsOnlyForSomeoneWhoIsSignedOut() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        assertEquals(RecoveryResetResult.Unavailable, auth.resetPasswordWithRecoveryCode(code, newPw, newPw))
        assertEquals(RecoveryResetResult.Unavailable, launch().let { fresh -> fresh.signIn(owner, ownerPw); fresh.resetPasswordWithRecoveryCode(code, newPw, newPw) })
    }

    @Test
    fun theResetClearsTheWaitAfterWrongPasswords() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        auth.signOut()
        repeat(5) { auth.signIn(owner, "wrong") }
        assertTrue(auth.signIn(owner, ownerPw) is SignInResult.LockedOut)
        auth.resetPasswordWithRecoveryCode(code, newPw, newPw)
        assertEquals(SignInResult.Success, auth.signIn(owner, newPw))
    }

    @Test
    fun setupOnANewDevice_hasNothingToResetWith() = runBlocking {
        val auth = launch()
        assertEquals(RecoveryResetResult.Unavailable, auth.resetPasswordWithRecoveryCode(RecoveryCodes.generate(), newPw, newPw))
        assertEquals(AuthState.NeedsSetup, auth.state.value)
    }

    // Activity log

    @Test
    fun savingACode_andResettingWithOne_areReportedWithoutAnySecret() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        auth.signOut()
        val reset = auth.resetPasswordWithRecoveryCode(code, newPw, newPw) as RecoveryResetResult.Reset
        val kinds = events.map { it.kind }
        assertEquals(listOf(AccountEventKind.RECOVERY_CODE_SET, AccountEventKind.SIGNED_OUT, AccountEventKind.PASSWORD_RESET_WITH_CODE), kinds)
        assertEquals("Sample User", events.last().actor.name)
        assertTrue(events.none { it.toString().contains(RecoveryCodes.normalize(code)) || it.toString().contains(RecoveryCodes.normalize(reset.newCode)) })
    }

    @Test
    fun aWrongCode_isNotAnEvent() = runBlocking {
        val auth = launch()
        auth.setUpWithCode()
        auth.signOut()
        events.clear()
        auth.resetPasswordWithRecoveryCode(RecoveryCodes.generate(), newPw, newPw)
        assertTrue(events.isEmpty())
    }

    // The saved registry

    @Test
    fun aRegistryFromBeforeRecoveryCodes_stillOpens() = runBlocking {
        launch().setUp()
        val json = JSONObject(accountFile.readText())
        assertFalse(json.getJSONArray("accounts").getJSONObject(0).has("recovery"))
        assertEquals(SignInResult.Success, launch().signIn(owner, ownerPw))
    }

    @Test
    fun aRecoveryProofOnAnEmployee_meansTheFileWasEdited() = runBlocking {
        val auth = launch()
        auth.setUpWithCode()
        auth.addEmployee()
        val json = JSONObject(accountFile.readText())
        val accounts = json.getJSONArray("accounts")
        accounts.getJSONObject(1).put("recovery", accounts.getJSONObject(0).getJSONObject("recovery"))
        accountFile.writeText(json.toString())
        assertEquals(AuthState.Unreadable, launch().state.value)
    }

    @Test
    fun anEmptyRecoveryProof_meansTheFileWasEdited() = runBlocking {
        launch().setUpWithCode()
        val json = JSONObject(accountFile.readText())
        json.getJSONArray("accounts").getJSONObject(0).getJSONObject("recovery").put("hash", "")
        accountFile.writeText(json.toString())
        assertEquals(AuthState.Unreadable, launch().state.value)
    }

    @Test
    fun otherAccountChanges_keepTheRecoveryProof() = runBlocking {
        val auth = launch()
        val code = auth.setUpWithCode()
        auth.addEmployee()
        val employeeId = (auth.listUsers()!!.first { it.role == Role.EMPLOYEE }).id
        assertEquals(UserChangeResult.Done, auth.setEmployeeActive(employeeId, false))
        assertEquals(UserChangeResult.Done, auth.resetEmployeePassword(employeeId, "Another-Pass-5", "Another-Pass-5"))
        auth.signOut()
        assertTrue(launch().resetPasswordWithRecoveryCode(code, newPw, newPw) is RecoveryResetResult.Reset)
    }
}

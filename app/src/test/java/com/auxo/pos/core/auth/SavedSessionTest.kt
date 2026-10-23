// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Remembers one saved session in memory, the way the phone's key store would; the fingerprint part is not simulated. */
class FakeSavedSessionVault(private val canProtect: Boolean = true) : SavedSessionVault {
    var userId: Long? = null
    var name: String? = null
    var saves = 0
    var clears = 0

    override fun savedName(): String? = name
    override fun save(userId: Long, name: String): Boolean {
        saves++
        if (!canProtect) return false
        this.userId = userId
        this.name = name
        return true
    }

    override fun clear() {
        clears++
        userId = null
        name = null
    }

    override fun unlockCipher(): Cipher? = null
    override fun open(cipher: Cipher): Long? = userId
}

@RunWith(AndroidJUnit4::class)
class SavedSessionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val hasher = PasswordHasher(iterations = 1_000)
    private val vault = FakeSavedSessionVault()
    private val pw = "Green-Leaf-42"

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "saved-${UUID.randomUUID()}").apply { mkdirs() }
    }

    private fun launch(v: SavedSessionVault = vault) =
        LocalAuthRepository(FileAccountStore(dir), hasher, MutableClock(), Dispatchers.Default, sessions = v)

    private suspend fun LocalAuthRepository.ownerWithFingerprint() {
        createAdministrator("Sample User", "sample.user", pw, pw)
        assertEquals(QuickUnlockChange.Done, setFingerprint(true, pw))
    }

    @Test
    fun turningTheFingerprintOn_savesTheSession_andTurningItOffForgetsIt() = runBlocking {
        val auth = launch()
        auth.ownerWithFingerprint()
        assertEquals("Sample User", vault.name)
        assertEquals(1L, vault.userId)
        assertEquals(QuickUnlockChange.Done, auth.setFingerprint(false, null))
        assertNull(vault.name)
    }

    @Test
    fun signingOut_forgetsTheSavedSession() = runBlocking {
        val auth = launch()
        auth.ownerWithFingerprint()
        auth.signOut()
        assertNull(vault.name)
        assertNull(launch().savedSessionName())
    }

    @Test
    fun afterARestart_theSavedNameIsOffered_onlyWhileSignedOut() = runBlocking {
        launch().ownerWithFingerprint()
        val restarted = launch()
        assertEquals(AuthState.SignedOut, restarted.state.value)
        assertEquals("Sample User", restarted.savedSessionName())
        assertEquals(SignInResult.Success, restarted.signIn("sample.user", pw))
        assertNull(restarted.savedSessionName())
    }

    @Test
    fun resumingASavedSession_signsThePersonBackIn_withoutAskingForAPassword() = runBlocking {
        launch().ownerWithFingerprint()
        val restarted = launch()
        assertTrue(restarted.resumeSession(1L))
        val state = restarted.state.value as AuthState.SignedIn
        assertEquals("Sample User", state.name)
        assertEquals(RecoveryPrompt.None, restarted.recoveryPrompt.value)
        assertFalse(restarted.lock.value.locked)
    }

    @Test
    fun resumingIsRefused_whenTheFingerprintWasTurnedOff_orTheNumberIsUnknown() = runBlocking {
        val first = launch()
        first.ownerWithFingerprint()
        first.setFingerprint(false, null)
        val restarted = launch()
        assertFalse(restarted.resumeSession(1L))
        assertEquals(AuthState.SignedOut, restarted.state.value)
        assertFalse(launch().resumeSession(99L))
    }

    @Test
    fun resumingIsRefused_whenSomeoneIsAlreadySignedIn() = runBlocking {
        val auth = launch()
        auth.ownerWithFingerprint()
        assertFalse(auth.resumeSession(1L))
    }

    @Test
    fun aSignInWithoutTheFingerprint_savesNothing() = runBlocking {
        val auth = launch()
        auth.createAdministrator("Sample User", "sample.user", pw, pw)
        auth.signOut()
        assertEquals(SignInResult.Success, auth.signIn("sample.user", pw))
        assertEquals(0, vault.saves)
        assertNull(auth.savedSessionName())
    }

    @Test
    fun aPhoneThatCannotProtectTheSession_stillSignsIn_andSavesNothing() = runBlocking {
        val weak = FakeSavedSessionVault(canProtect = false)
        val auth = launch(weak)
        auth.createAdministrator("Sample User", "sample.user", pw, pw)
        assertEquals(QuickUnlockChange.Done, auth.setFingerprint(true, pw))
        assertNull(weak.name)
        auth.signOut()
        assertEquals(SignInResult.Success, auth.signIn("sample.user", pw))
    }
}

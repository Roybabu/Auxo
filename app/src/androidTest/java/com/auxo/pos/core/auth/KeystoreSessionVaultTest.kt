// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the emulator. It has no fingerprint, so only saving and forgetting are checked here: opening a saved session
 * needs a real finger and is tried by hand on a phone.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSessionVaultTest {

    private val vault = KeystoreSessionVault(ApplicationProvider.getApplicationContext())

    @Before
    @After
    fun forget() = vault.clear()

    @Test
    fun savingEitherKeepsAProtectedSession_orKeepsNothingAtAll() {
        val saved = vault.save(7L, "Sample User")
        // A phone with no screen lock or fingerprint cannot protect it, and then nothing may be left behind.
        assertEquals(saved, vault.savedName() == "Sample User")
        if (!saved) assertNull(vault.savedName())
    }

    @Test
    fun forgetting_removesTheSavedSessionAndItsKey() {
        vault.save(7L, "Sample User")
        vault.clear()
        assertNull(vault.savedName())
        assertNull(vault.unlockCipher())
    }

    @Test
    fun theSavedFileNeverContainsTheNumberInTheClear() {
        if (!vault.save(424242L, "Sample User")) return
        val file = java.io.File(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir,
            "saved_session.vault",
        )
        assertFalse(file.readText().contains("424242"))
    }
}

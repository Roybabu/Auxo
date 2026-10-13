package com.auxo.pos.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.FakeSavedSessionVault
import com.auxo.pos.core.auth.FileAccountStore
import com.auxo.pos.core.auth.LocalAuthRepository
import com.auxo.pos.core.auth.MutableClock
import com.auxo.pos.core.auth.PasswordHasher
import com.auxo.pos.core.auth.QuickUnlockChange
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

@RunWith(AndroidJUnit4::class)
class ResumeViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val vault = FakeSavedSessionVault()
    private val pw = "Green-Leaf-42"
    private val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "resume-${UUID.randomUUID()}").apply { mkdirs() }
    }

    private fun launch() = LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000), MutableClock(), Dispatchers.Default, sessions = vault)

    private fun restartedWithSavedOwner(): LocalAuthRepository {
        runBlocking {
            val first = launch()
            first.createAdministrator("Roy Babu", "roy.babu", pw, pw)
            assertEquals(QuickUnlockChange.Done, first.setFingerprint(true, pw))
        }
        return launch()
    }

    @Test
    fun aPassedFingerprint_opensTheSavedSession() {
        val auth = restartedWithSavedOwner()
        val model = ResumeViewModel(auth, vault, "Roy Babu")
        model.fingerprintAccepted(cipher)
        assertTrue(auth.state.value is AuthState.SignedIn)
        assertFalse(model.uiState.value.unavailable)
    }

    @Test
    fun aSavedNumberThatNoLongerMatchesAnAccount_fallsBackToThePassword_andIsForgotten() {
        val auth = restartedWithSavedOwner()
        vault.userId = 99L
        val model = ResumeViewModel(auth, vault, "Roy Babu")
        model.fingerprintAccepted(cipher)
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertTrue(model.uiState.value.unavailable)
        assertNull(vault.name)
    }

    @Test
    fun aKeyThatCannotBeUsed_fallsBackToThePassword() {
        val model = ResumeViewModel(restartedWithSavedOwner(), vault, "Roy Babu")
        assertNull(model.cipher())
        assertTrue(model.uiState.value.unavailable)
    }

    @Test
    fun aFingerThatIsNotRecognised_isReported_withoutSigningAnyoneIn() {
        val auth = restartedWithSavedOwner()
        val model = ResumeViewModel(auth, vault, "Roy Babu")
        model.fingerprintRejected()
        assertTrue(model.uiState.value.failed)
        assertEquals(AuthState.SignedOut, auth.state.value)
    }
}
